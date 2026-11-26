package com.mine.offlinetranslate.asr

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/**
 * Streaming transducer wrapper. All calls must happen on one thread.
 *
 * Call [finish] before stopping: the streaming model drops the last phrase
 * unless 0.5 s of silence is appended and the stream is flushed.
 */
class StreamingAsr(
    private val context: Context,
    private val modelDir: File,
) {
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null

    var isLoaded: Boolean = false
        private set

    fun load(encoder: String, decoder: String, joiner: String, tokens: String): Result<Unit> =
        runCatching {
            if (isLoaded) return@runCatching
            listOf(encoder, decoder, joiner, tokens).forEach {
                val file = File(modelDir, it)
                require(file.isFile && file.length() > 0) { "模型文件缺失：$it" }
            }

            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80, dither = 0f),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = File(modelDir, encoder).absolutePath,
                        decoder = File(modelDir, decoder).absolutePath,
                        joiner = File(modelDir, joiner).absolutePath,
                    ),
                    tokens = File(modelDir, tokens).absolutePath,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu",
                    modelType = "transducer",
                ),
                endpointConfig = EndpointConfig(
                    // rule2 = trailing silence. Continuous speech often contains
                    // 1.5-3 s thinking/breathing pauses; only close the line after
                    // a clearly longer pause.
                    rule1 = EndpointRule(false, 4.5f, 0f),
                    rule2 = EndpointRule(true, 4.5f, 0f),
                    rule3 = EndpointRule(true, 0f, 60f),
                ),
                enableEndpoint = true,
                // Beam search materially improves Chinese punctuation and
                // homophone handling while remaining fully streaming.
                decodingMethod = "modified_beam_search",
                maxActivePaths = 4,
            )

            // Absolute file paths must be read by the engine itself. Passing an
            // AssetManager makes sherpa try to resolve them as assets and abort.
            val created = OnlineRecognizer(assetManager = null, config = config)
            recognizer = created
            stream = created.createStream()
            isLoaded = true
            Log.i(TAG, "ASR loaded from $modelDir")
        }.onFailure {
            Log.e(TAG, "ASR load failed", it)
            release()
        }

    fun accept(samples: FloatArray): String {
        val engine = recognizer ?: return ""
        val input = stream ?: return ""
        input.acceptWaveform(samples, SAMPLE_RATE)
        while (engine.isReady(input)) {
            engine.decode(input)
        }
        return engine.getResult(input).text.trim()
    }

    fun isEndpoint(): Boolean {
        val engine = recognizer ?: return false
        val input = stream ?: return false
        return engine.isEndpoint(input)
    }

    fun reset() {
        val engine = recognizer ?: return
        val input = stream ?: return
        engine.reset(input)
    }

    /** Append 0.5 s of silence, flush, and return the complete final text. */
    fun finish(): String {
        val engine = recognizer ?: return ""
        val input = stream ?: return ""
        input.acceptWaveform(FloatArray(TAIL_SAMPLES), SAMPLE_RATE)
        while (engine.isReady(input)) {
            engine.decode(input)
        }
        input.inputFinished()
        while (engine.isReady(input)) {
            engine.decode(input)
        }
        return engine.getResult(input).text.trim()
    }

    fun release() {
        try {
            stream?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "stream release", t)
        }
        try {
            recognizer?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "recognizer release", t)
        }
        stream = null
        recognizer = null
        isLoaded = false
    }

    companion object {
        private const val TAG = "StreamingAsr"
        private const val SAMPLE_RATE = 16000
        private const val TAIL_SAMPLES = 8000
    }
}
