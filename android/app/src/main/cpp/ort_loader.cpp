#include <jni.h>

#include <android/log.h>
#include <dlfcn.h>

#define ORT_TAG "OrtLoader"
#define ORT_LOGI(...) __android_log_print(ANDROID_LOG_INFO, ORT_TAG, __VA_ARGS__)
#define ORT_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, ORT_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mine_offlinetranslate_core_OnnxRuntimeLoader_nativeLoadGlobal(
        JNIEnv *,
        jobject) {
    void *handle = dlopen("libonnxruntime.so", RTLD_NOW | RTLD_GLOBAL);
    if (handle == nullptr) {
        const char *error = dlerror();
        ORT_LOGE("dlopen libonnxruntime.so failed: %s", error == nullptr ? "unknown" : error);
        return JNI_FALSE;
    }
    ORT_LOGI("libonnxruntime.so loaded with RTLD_GLOBAL");
    return JNI_TRUE;
}
