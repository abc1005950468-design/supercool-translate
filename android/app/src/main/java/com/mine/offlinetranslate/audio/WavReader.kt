package com.mine.offlinetranslate.audio

import android.content.res.AssetManager
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class WavData(val samples: FloatArray, val sampleRate: Int)

/**
 * Minimal RIFF/WAVE reader for the deterministic test clip (PCM16, mono, 16 kHz).
 * Real microphone input never goes through this class.
 */
object WavReader {
    fun readAsset(assets: AssetManager, name: String): WavData? {
        return runCatching {
            assets.open(name).use { raw ->
                val input = DataInputStream(raw.buffered())
                val riff = ByteArray(12)
                input.readFully(riff)
                require(String(riff, 0, 4, Charsets.US_ASCII) == "RIFF")
                require(String(riff, 8, 4, Charsets.US_ASCII) == "WAVE")

                var sampleRate = 0
                var channels = 0
                var bits = 0
                var pcm: ByteArray? = null

                while (true) {
                    val id = ByteArray(4)
                    if (input.read(id) != 4) break
                    val size = Integer.reverseBytes(input.readInt())
                    if (size < 0) break
                    val chunk = String(id, Charsets.US_ASCII)
                    when (chunk) {
                        "fmt " -> {
                            val fmt = ByteArray(size)
                            input.readFully(fmt)
                            val buffer = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                            buffer.short
                            channels = buffer.short.toInt()
                            sampleRate = buffer.int
                            buffer.int
                            buffer.short
                            bits = buffer.short.toInt()
                        }
                        "data" -> {
                            pcm = ByteArray(size)
                            input.readFully(pcm)
                            break
                        }
                        else -> {
                            var left = size.toLong()
                            while (left > 0) {
                                val skipped = input.skip(left)
                                if (skipped <= 0) break
                                left -= skipped
                            }
                        }
                    }
                    if (size % 2 == 1 && chunk != "data") input.skipBytes(1)
                }

                require(channels == 1) { "仅支持单声道测试音频" }
                require(bits == 16) { "仅支持 PCM16 测试音频" }
                require(sampleRate == 16000) { "仅支持 16 kHz 测试音频" }
                val bytes = pcm ?: return null
                val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val samples = FloatArray(shorts.remaining())
                for (i in samples.indices) {
                    samples[i] = shorts.get(i) / 32768f
                }
                WavData(samples, sampleRate)
            }
        }.getOrNull()
    }
}
