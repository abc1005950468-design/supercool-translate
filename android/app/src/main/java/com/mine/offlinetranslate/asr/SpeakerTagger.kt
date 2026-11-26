package com.mine.offlinetranslate.asr

import android.util.Log
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File
import kotlin.math.sqrt

/**
 * Online speaker labelling: one embedding per finalized utterance, compared
 * with existing centroids. This is intentionally lightweight; it is not the
 * full offline diarization pipeline.
 */
class SpeakerTagger(private val modelDir: File) {
    private var extractor: SpeakerEmbeddingExtractor? = null
    private val centroids = mutableListOf<FloatArray>()

    var isLoaded: Boolean = false
        private set

    fun load(modelName: String): Boolean = runCatching {
        val model = File(modelDir, modelName)
        require(model.isFile && model.length() > 0) { "说话人模型缺失" }
        val config = SpeakerEmbeddingExtractorConfig(
            model = model.absolutePath,
            numThreads = 1,
            debug = false,
            provider = "cpu",
        )
        extractor = SpeakerEmbeddingExtractor(null, config)
        isLoaded = true
        Log.i(TAG, "speaker model loaded: ${model.name}")
    }.isSuccess

    fun identify(samples: FloatArray): String? {
        Log.i(TAG, "identify called samples=${samples.size}")
        if (samples.size < SAMPLE_RATE / 2) return null
        val engine = extractor ?: return null
        val stream = engine.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            stream.inputFinished()
            val raw = engine.compute(stream)
            Log.i(TAG, "identify samples=${samples.size} embedding=${raw.size}")
            if (raw.isEmpty()) return null
            var norm = 0.0
            raw.forEach { norm += it.toDouble() * it }
            norm = sqrt(norm)
            if (norm < 1e-6) return null
            val vector = FloatArray(raw.size) { (raw[it] / norm).toFloat() }

            var best = -1f
            var bestIndex = -1
            centroids.forEachIndexed { index, centroid ->
                var dot = 0f
                for (i in vector.indices) dot += vector[i] * centroid[i]
                if (dot > best) {
                    best = dot
                    bestIndex = index
                }
            }
            if (bestIndex >= 0 && best >= THRESHOLD) {
                "发言人 ${bestIndex + 1}"
            } else if (centroids.size >= MAX_SPEAKERS && bestIndex >= 0) {
                "发言人 ${bestIndex + 1}"
            } else {
                centroids.add(vector)
                "发言人 ${centroids.size}"
            }
        } catch (t: Throwable) {
            Log.w(TAG, "identify failed", t)
            null
        } finally {
            runCatching { stream.release() }
        }
    }

    fun reset() {
        centroids.clear()
    }

    fun release() {
        runCatching { extractor?.release() }
        extractor = null
        isLoaded = false
        centroids.clear()
    }

    companion object {
        private const val TAG = "SpeakerTagger"
        private const val SAMPLE_RATE = 16000
        private const val THRESHOLD = 0.65f
        private const val MAX_SPEAKERS = 4
    }
}
