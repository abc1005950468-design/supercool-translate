package com.mine.offlinetranslate.asr

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File

/** Final-sentence Chinese recognizer used to correct the lightweight stream. */
class ParaformerAsr(
    private val modelDir: File,
) {
    private var recognizer: OfflineRecognizer? = null
    private val lock = Any()

    fun load(): Result<Unit> = synchronized(lock) {
        runCatching {
            if (recognizer != null) return@runCatching
            val model = File(modelDir, "model.int8.onnx")
            val tokens = File(modelDir, "tokens.txt")
            require(model.isFile && model.length() > 0) { "高精度中文模型缺失" }
            require(tokens.isFile && tokens.length() > 0) { "高精度中文 tokens 缺失" }
            recognizer = OfflineRecognizer(
                assetManager = null,
                config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(
                        sampleRate = 16000,
                        featureDim = 80,
                        dither = 0f,
                    ),
                    modelConfig = OfflineModelConfig(
                        paraformer = OfflineParaformerModelConfig(
                            model = model.absolutePath,
                        ),
                        tokens = tokens.absolutePath,
                        numThreads = 4,
                        debug = false,
                        provider = "cpu",
                        modelType = "paraformer",
                    ),
                ),
            )
            Log.i(TAG, "Paraformer loaded from $modelDir")
        }.onFailure {
            Log.e(TAG, "Paraformer load failed", it)
            recognizer?.release()
            recognizer = null
        }
    }

    fun transcribe(samples: FloatArray): String = synchronized(lock) {
        val engine = recognizer ?: return ""
        if (samples.isEmpty()) return ""
        val stream = engine.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            engine.decode(stream)
            engine.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    fun release() = synchronized(lock) {
        runCatching { recognizer?.release() }
        recognizer = null
    }

    private companion object {
        const val TAG = "ParaformerAsr"
        const val SAMPLE_RATE = 16000
    }
}
