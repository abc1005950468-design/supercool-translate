package com.mine.offlinetranslate.asr

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentification
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentificationConfig
import com.k2fsa.sherpa.onnx.SpokenLanguageIdentificationWhisperConfig
import java.io.File

/**
 * Audio-level language identification (Whisper tiny).
 *
 * Watches the same microphone signal as the ASR engines and answers "which
 * language was actually spoken", so a sentence is routed to the engine that
 * can recognise it instead of guessing from decoded text.
 */
class LanguageId(
    private val context: Context,
    private val modelDir: File,
) {
    private var engine: SpokenLanguageIdentification? = null

    var isLoaded: Boolean = false
        private set

    fun load(encoder: String, decoder: String): Result<Unit> = runCatching {
        if (isLoaded) return@runCatching
        val encoderFile = File(modelDir, encoder)
        val decoderFile = File(modelDir, decoder)
        require(encoderFile.isFile && encoderFile.length() > 0) { "语种识别 encoder 缺失" }
        require(decoderFile.isFile && decoderFile.length() > 0) { "语种识别 decoder 缺失" }

        val config = SpokenLanguageIdentificationConfig(
            whisper = SpokenLanguageIdentificationWhisperConfig(
                encoder = encoderFile.absolutePath,
                decoder = decoderFile.absolutePath,
                tailPaddings = 100,
            ),
            numThreads = 1,
            debug = false,
            provider = "cpu",
        )
        engine = SpokenLanguageIdentification(assetManager = null, config = config)
        isLoaded = true
        Log.i(TAG, "LID loaded from $modelDir")
    }.onFailure {
        Log.e(TAG, "LID load failed", it)
        release()
    }

    /** Returns a language code such as `ru`, `zh`, `en`; null when unsure. */
    fun detect(samples: FloatArray): String? {
        val model = engine ?: return null
        // Sub-second tails are too short for a reliable language decision; let the
        // text-evidence fallback handle them instead of forcing a wrong engine.
        if (samples.size < SAMPLE_RATE * 2) return null
        val stream = model.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            model.compute(stream).trim().lowercase().substringBefore('-').ifEmpty { null }
        } catch (t: Throwable) {
            Log.w(TAG, "LID detect failed", t)
            null
        } finally {
            runCatching { stream.release() }
        }
    }

    fun release() {
        runCatching { engine?.release() }
        engine = null
        isLoaded = false
    }

    private companion object {
        const val TAG = "LanguageId"
        const val SAMPLE_RATE = 16000
    }
}
