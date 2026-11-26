#include <jni.h>

#include <android/log.h>

#include <algorithm>
#include <cstdint>
#include <memory>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "onnxruntime_c_api.h"

#define NLLB_TAG "NllbOrt"
#define NLLB_LOGI(...) __android_log_print(ANDROID_LOG_INFO, NLLB_TAG, __VA_ARGS__)
#define NLLB_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, NLLB_TAG, __VA_ARGS__)

extern "C" OrtStatus* OrtSessionOptionsAppendExecutionProvider_CPU(
        OrtSessionOptions* options,
        int use_arena);

namespace {

constexpr int64_t kEosToken = 2;

std::vector<std::string> sessionInputNames(
        const OrtApi *api,
        OrtSession *session,
        OrtAllocator *allocator,
        size_t from = 0) {
    size_t count = 0;
    OrtStatus *status = api->SessionGetInputCount(session, &count);
    if (status != nullptr) {
        std::string message = api->GetErrorMessage(status);
        api->ReleaseStatus(status);
        throw std::runtime_error("SessionGetInputCount: " + message);
    }
    std::vector<std::string> names;
    for (size_t index = from; index < count; ++index) {
        char *name = nullptr;
        status = api->SessionGetInputName(session, index, allocator, &name);
        if (status != nullptr) {
            std::string message = api->GetErrorMessage(status);
            api->ReleaseStatus(status);
            throw std::runtime_error("SessionGetInputName: " + message);
        }
        names.emplace_back(name);
        allocator->Free(allocator, name);
    }
    return names;
}

std::vector<std::string> sessionOutputNames(
        const OrtApi *api,
        OrtSession *session,
        OrtAllocator *allocator) {
    size_t count = 0;
    OrtStatus *status = api->SessionGetOutputCount(session, &count);
    if (status != nullptr) {
        std::string message = api->GetErrorMessage(status);
        api->ReleaseStatus(status);
        throw std::runtime_error("SessionGetOutputCount: " + message);
    }
    std::vector<std::string> names;
    names.reserve(count);
    for (size_t index = 0; index < count; ++index) {
        char *name = nullptr;
        status = api->SessionGetOutputName(session, index, allocator, &name);
        if (status != nullptr) {
            std::string message = api->GetErrorMessage(status);
            api->ReleaseStatus(status);
            throw std::runtime_error("SessionGetOutputName: " + message);
        }
        names.emplace_back(name);
        allocator->Free(allocator, name);
    }
    return names;
}

std::string pastNameForOutput(const std::string &outputName) {
    static const std::string prefix = "present.";
    if (outputName.rfind(prefix, 0) != 0) return {};
    return "past_key_values." + outputName.substr(prefix.size());
}

class NllbOrtEngine {
public:
    NllbOrtEngine(
            const std::string &encoderPath,
            const std::string &decoderPath,
            const std::string &decoderPastPath) {
        api_ = OrtGetApiBase()->GetApi(ORT_API_VERSION);
        if (api_ == nullptr) throw std::runtime_error("OrtGetApiBase returned null");

        check(api_->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "nllb", &env_),
              "CreateEnv");
        check(api_->CreateSessionOptions(&options_), "CreateSessionOptions");
        check(api_->SetSessionGraphOptimizationLevel(options_, ORT_ENABLE_ALL),
              "SetSessionGraphOptimizationLevel");
        check(api_->SetIntraOpNumThreads(options_, 1), "SetIntraOpNumThreads");
        check(api_->SetInterOpNumThreads(options_, 1), "SetInterOpNumThreads");
        check(api_->DisableMemPattern(options_), "DisableMemPattern");
        check(OrtSessionOptionsAppendExecutionProvider_CPU(options_, 0),
              "AppendExecutionProvider_CPU");
        check(api_->CreateCpuMemoryInfo(
                      OrtArenaAllocator, OrtMemTypeDefault, &memoryInfo_),
              "CreateCpuMemoryInfo");

        check(api_->CreateSession(env_, encoderPath.c_str(), options_, &encoder_),
              "Create encoder session");
        check(api_->CreateSession(env_, decoderPath.c_str(), options_, &decoder_),
              "Create decoder session");
        check(api_->CreateSession(
                      env_, decoderPastPath.c_str(), options_, &decoderPast_),
              "Create decoder-with-past session");

        check(api_->GetAllocatorWithDefaultOptions(&allocator_),
              "GetAllocatorWithDefaultOptions");
        initialOutputNames_ = sessionOutputNames(api_, decoder_, allocator_);
        pastInputNames_ = sessionInputNames(api_, decoderPast_, allocator_, 2);
        pastOutputNames_ = sessionOutputNames(api_, decoderPast_, allocator_);
        for (const auto &name : pastInputNames_) {
            if (name.find(".encoder.") == std::string::npos) {
                requiredDecoderPastNames_.push_back(name);
            }
        }
        NLLB_LOGI(
                "sessions ready initialOutputs=%zu pastInputs=%zu pastOutputs=%zu",
                initialOutputNames_.size(),
                pastInputNames_.size(),
                pastOutputNames_.size());
    }

    ~NllbOrtEngine() {
        releasePast(decoderPastValues_);
        releasePast(encoderPastValues_);
        if (decoderPast_ != nullptr) api_->ReleaseSession(decoderPast_);
        if (decoder_ != nullptr) api_->ReleaseSession(decoder_);
        if (encoder_ != nullptr) api_->ReleaseSession(encoder_);
        if (memoryInfo_ != nullptr) api_->ReleaseMemoryInfo(memoryInfo_);
        if (options_ != nullptr) api_->ReleaseSessionOptions(options_);
        if (env_ != nullptr) api_->ReleaseEnv(env_);
    }

    std::vector<int64_t> translate(
            const std::vector<int64_t> &inputIds,
            int64_t forcedFirstToken,
            int maxTokens) {
        // Caches belong to one sentence only. Release the previous sentence
        // before collectInitialPast overwrites the same keys.
        releasePast(decoderPastValues_);
        releasePast(encoderPastValues_);
        std::vector<int64_t> attentionMask(inputIds.size(), 1);
        OrtValue *idsTensor = createInt64Tensor(inputIds);
        OrtValue *maskTensor = createInt64Tensor(attentionMask);
        OrtValue *encoderHidden = nullptr;
        OrtValue *eosTensor = nullptr;
        try {
            const char *encoderInputNames[] = {"input_ids", "attention_mask"};
            const OrtValue *encoderInputs[] = {idsTensor, maskTensor};
            const char *encoderOutputNames[] = {"last_hidden_state"};
            check(api_->Run(
                          encoder_,
                          nullptr,
                          encoderInputNames,
                          encoderInputs,
                          2,
                          encoderOutputNames,
                          1,
                          &encoderHidden),
                  "Run encoder");

            std::vector<int64_t> eosData = {kEosToken};
            eosTensor = createInt64Tensor(eosData);
            const char *decoderInputNames[] = {
                    "encoder_attention_mask",
                    "input_ids",
                    "encoder_hidden_states",
            };
            const OrtValue *decoderInputs[] = {
                    maskTensor,
                    eosTensor,
                    encoderHidden,
            };
            std::vector<const char *> decoderOutputNamePtrs;
            std::vector<OrtValue *> decoderOutputs(initialOutputNames_.size());
            decoderOutputNamePtrs.reserve(initialOutputNames_.size());
            for (const auto &name : initialOutputNames_) {
                decoderOutputNamePtrs.push_back(name.c_str());
            }
            check(api_->Run(
                          decoder_,
                          nullptr,
                          decoderInputNames,
                          decoderInputs,
                          3,
                          decoderOutputNamePtrs.data(),
                          decoderOutputNamePtrs.size(),
                          decoderOutputs.data()),
                  "Run decoder");
            api_->ReleaseValue(eosTensor);
            eosTensor = nullptr;

            collectInitialPast(decoderOutputs);
            const int64_t nextInput = forcedFirstToken;
            std::vector<int64_t> generated;
            generated.reserve(static_cast<size_t>(maxTokens));
            int64_t currentInput = nextInput;

            for (int step = 0; step < maxTokens; ++step) {
                currentInput = runPastStep(
                        attentionMask,
                        currentInput,
                        generated);
                if (currentInput == kEosToken) break;
            }

            api_->ReleaseValue(encoderHidden);
            encoderHidden = nullptr;
            api_->ReleaseValue(idsTensor);
            idsTensor = nullptr;
            api_->ReleaseValue(maskTensor);
            maskTensor = nullptr;
            return generated;
        } catch (...) {
            if (encoderHidden != nullptr) api_->ReleaseValue(encoderHidden);
            if (idsTensor != nullptr) api_->ReleaseValue(idsTensor);
            if (maskTensor != nullptr) api_->ReleaseValue(maskTensor);
            if (eosTensor != nullptr) api_->ReleaseValue(eosTensor);
            releasePast(decoderPastValues_);
            releasePast(encoderPastValues_);
            throw;
        }
    }

private:
    const OrtApi *api_ = nullptr;
    OrtEnv *env_ = nullptr;
    OrtSessionOptions *options_ = nullptr;
    OrtMemoryInfo *memoryInfo_ = nullptr;
    OrtAllocator *allocator_ = nullptr;
    OrtSession *encoder_ = nullptr;
    OrtSession *decoder_ = nullptr;
    OrtSession *decoderPast_ = nullptr;
    std::vector<std::string> initialOutputNames_;
    std::vector<std::string> pastInputNames_;
    std::vector<std::string> pastOutputNames_;
    std::vector<std::string> requiredDecoderPastNames_;
    std::unordered_map<std::string, OrtValue *> encoderPastValues_;
    std::unordered_map<std::string, OrtValue *> decoderPastValues_;

    void check(OrtStatus *status, const char *operation) {
        if (status == nullptr) return;
        std::string message = api_->GetErrorMessage(status);
        api_->ReleaseStatus(status);
        throw std::runtime_error(std::string(operation) + ": " + message);
    }

    OrtValue *createInt64Tensor(const std::vector<int64_t> &data) {
        const int64_t shape[] = {1, static_cast<int64_t>(data.size())};
        OrtValue *value = nullptr;
        check(api_->CreateTensorWithDataAsOrtValue(
                      memoryInfo_,
                      const_cast<int64_t *>(data.data()),
                      data.size() * sizeof(int64_t),
                      shape,
                      2,
                      ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64,
                      &value),
              "CreateTensorWithDataAsOrtValue");
        return value;
    }

    void collectInitialPast(const std::vector<OrtValue *> &outputs) {
        for (size_t index = 0; index < initialOutputNames_.size(); ++index) {
            const std::string &name = initialOutputNames_[index];
            const std::string pastName = pastNameForOutput(name);
            if (pastName.empty()) {
                if (name != "logits" && outputs[index] != nullptr) {
                    api_->ReleaseValue(outputs[index]);
                }
                continue;
            }
            if (pastName.find(".encoder.") != std::string::npos) {
                encoderPastValues_[pastName] = outputs[index];
            } else {
                decoderPastValues_[pastName] = outputs[index];
            }
        }
        // The forced language token does not depend on logits, but it still
        // needs to be released after the first run.
        for (size_t index = 0; index < initialOutputNames_.size(); ++index) {
            if (initialOutputNames_[index] == "logits" && outputs[index] != nullptr) {
                api_->ReleaseValue(outputs[index]);
            }
        }
    }

    int64_t runPastStep(
            const std::vector<int64_t> &attentionMask,
            int64_t inputToken,
            std::vector<int64_t> &generated) {
        OrtValue *maskTensor = createInt64Tensor(attentionMask);
        std::vector<int64_t> tokenData = {inputToken};
        OrtValue *tokenTensor = createInt64Tensor(tokenData);

        std::vector<const char *> inputNames;
        std::vector<const OrtValue *> inputValues;
        inputNames.push_back("encoder_attention_mask");
        inputValues.push_back(maskTensor);
        inputNames.push_back("input_ids");
        inputValues.push_back(tokenTensor);
        for (const auto &name : pastInputNames_) {
            const OrtValue *pastValue = nullptr;
            const auto encoderValue = encoderPastValues_.find(name);
            if (encoderValue != encoderPastValues_.end()) {
                pastValue = encoderValue->second;
            } else {
                const auto decoderValue = decoderPastValues_.find(name);
                if (decoderValue != decoderPastValues_.end()) {
                    pastValue = decoderValue->second;
                }
            }
            if (pastValue == nullptr) {
                api_->ReleaseValue(tokenTensor);
                api_->ReleaseValue(maskTensor);
                throw std::runtime_error("Missing ONNX cache input: " + name);
            }
            inputNames.push_back(name.c_str());
            inputValues.push_back(pastValue);
        }

        std::vector<const char *> outputNames;
        std::vector<OrtValue *> outputs(pastOutputNames_.size());
        outputNames.reserve(pastOutputNames_.size());
        for (const auto &name : pastOutputNames_) {
            outputNames.push_back(name.c_str());
        }
        check(api_->Run(
                      decoderPast_,
                      nullptr,
                      inputNames.data(),
                      inputValues.data(),
                      inputValues.size(),
                      outputNames.data(),
                      outputNames.size(),
                      outputs.data()),
              "Run decoder-with-past");
        api_->ReleaseValue(tokenTensor);
        api_->ReleaseValue(maskTensor);

        std::unordered_map<std::string, OrtValue *> nextDecoderPast;
        int64_t nextToken = kEosToken;
        for (size_t index = 0; index < pastOutputNames_.size(); ++index) {
            const std::string &name = pastOutputNames_[index];
            if (name == "logits") {
                nextToken = argMax(outputs[index]);
                api_->ReleaseValue(outputs[index]);
                continue;
            }
            const std::string pastName = pastNameForOutput(name);
            if (!pastName.empty() && pastName.find(".encoder.") == std::string::npos) {
                nextDecoderPast[pastName] = outputs[index];
            } else if (outputs[index] != nullptr) {
                api_->ReleaseValue(outputs[index]);
            }
        }
        releasePast(decoderPastValues_);
        decoderPastValues_ = std::move(nextDecoderPast);
        if (nextToken != kEosToken) generated.push_back(nextToken);
        return nextToken;
    }

    int64_t argMax(OrtValue *logits) {
        OrtTensorTypeAndShapeInfo *shapeInfo = nullptr;
        check(api_->GetTensorTypeAndShape(logits, &shapeInfo),
              "GetTensorTypeAndShape");
        size_t rank = 0;
        check(api_->GetDimensionsCount(shapeInfo, &rank), "GetDimensionsCount");
        if (rank != 3) {
            api_->ReleaseTensorTypeAndShapeInfo(shapeInfo);
            throw std::runtime_error("Unexpected logits rank");
        }
        int64_t dimensions[3] = {0, 0, 0};
        check(api_->GetDimensions(shapeInfo, dimensions, rank), "GetDimensions");
        api_->ReleaseTensorTypeAndShapeInfo(shapeInfo);
        const int64_t sequenceLength = dimensions[1];
        const int64_t vocabularySize = dimensions[2];
        void *raw = nullptr;
        check(api_->GetTensorMutableData(logits, &raw), "GetTensorMutableData");
        const auto *data = static_cast<float *>(raw);
        const int64_t base = (sequenceLength - 1) * vocabularySize;
        int64_t best = 0;
        float bestValue = -INFINITY;
        for (int64_t index = 0; index < vocabularySize; ++index) {
            const float value = data[base + index];
            if (value > bestValue) {
                bestValue = value;
                best = index;
            }
        }
        return best;
    }

    void releasePast(std::unordered_map<std::string, OrtValue *> &values) {
        for (auto &item : values) {
            if (item.second != nullptr) api_->ReleaseValue(item.second);
        }
        values.clear();
    }
};

std::string jstringToString(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void throwJavaException(JNIEnv *env, const std::exception &error) {
    NLLB_LOGE("native failure: %s", error.what());
    jclass clazz = env->FindClass("java/lang/RuntimeException");
    env->ThrowNew(clazz, error.what());
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_mine_offlinetranslate_mt_NllbNative_create(
        JNIEnv *env,
        jobject,
        jstring modelDir) {
    try {
        const std::string directory = jstringToString(env, modelDir);
        auto *engine = new NllbOrtEngine(
                directory + "/encoder_model_quantized.onnx",
                directory + "/decoder_model_quantized.onnx",
                directory + "/decoder_with_past_model_quantized.onnx");
        return reinterpret_cast<jlong>(engine);
    } catch (const std::exception &error) {
        throwJavaException(env, error);
        return 0;
    }
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_mine_offlinetranslate_mt_NllbNative_translate(
        JNIEnv *env,
        jobject,
        jlong handle,
        jlongArray inputIdsArray,
        jlong forcedFirstToken,
        jint maxTokens) {
    try {
        auto *engine = reinterpret_cast<NllbOrtEngine *>(handle);
        if (engine == nullptr) throw std::runtime_error("NLLB engine is closed");
        const jsize inputLength = env->GetArrayLength(inputIdsArray);
        std::vector<jlong> inputBuffer(static_cast<size_t>(inputLength));
        env->GetLongArrayRegion(
                inputIdsArray, 0, inputLength, inputBuffer.data());
        std::vector<int64_t> inputIds(inputBuffer.begin(), inputBuffer.end());
        const int cappedTokens = std::clamp<int>(maxTokens, 1, 512);
        std::vector<int64_t> generated = engine->translate(
                inputIds,
                static_cast<int64_t>(forcedFirstToken),
                cappedTokens);
        jlongArray result = env->NewLongArray(generated.size());
        env->SetLongArrayRegion(
                result,
                0,
                generated.size(),
                reinterpret_cast<const jlong *>(generated.data()));
        return result;
    } catch (const std::exception &error) {
        throwJavaException(env, error);
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_mine_offlinetranslate_mt_NllbNative_close(
        JNIEnv *,
        jobject,
        jlong handle) {
    delete reinterpret_cast<NllbOrtEngine *>(handle);
}
