package com.mine.offlinetranslate.asr

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File

/** Offline Whisper recognizer used to re-decode complete Russian utterances. */
class WhisperAsr(
    private val modelDir: File,
    private val language: String,
) {
    private var recognizer: OfflineRecognizer? = null
    private val lock = Any()

    fun load(): Result<Unit> = synchronized(lock) {
        runCatching {
            if (recognizer != null) return@runCatching
            val encoder = File(modelDir, "small-encoder.int8.onnx")
            val decoder = File(modelDir, "small-decoder.int8.onnx")
            val tokens = File(modelDir, "small-tokens.txt")
            require(encoder.isFile && encoder.length() > 0) { "Whisper encoder 缺失" }
            require(decoder.isFile && decoder.length() > 0) { "Whisper decoder 缺失" }
            require(tokens.isFile && tokens.length() > 0) { "Whisper tokens 缺失" }
            recognizer = OfflineRecognizer(
                assetManager = null,
                config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(
                        sampleRate = SAMPLE_RATE,
                        featureDim = 80,
                        dither = 0f,
                    ),
                    modelConfig = OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = encoder.absolutePath,
                            decoder = decoder.absolutePath,
                            language = language,
                            task = "transcribe",
                        ),
                        tokens = tokens.absolutePath,
                        numThreads = 4,
                        debug = false,
                        provider = "cpu",
                        modelType = "whisper",
                    ),
                ),
            )
            Log.i(TAG, "Whisper loaded from $modelDir language=$language")
        }.onFailure {
            Log.e(TAG, "Whisper load failed language=$language", it)
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
        const val TAG = "WhisperAsr"
        const val SAMPLE_RATE = 16000
    }
}
