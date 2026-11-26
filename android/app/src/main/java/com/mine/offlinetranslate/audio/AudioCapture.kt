package com.mine.offlinetranslate.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import kotlin.math.sqrt

class AudioCapture {

    interface Listener {
        fun onChunk(samples: FloatArray, level: Float)
        fun onError(message: String)
    }

    private var thread: Thread? = null
    private var record: AudioRecord? = null

    @Volatile
    private var running = false

    val isRunning: Boolean get() = running

    @SuppressLint("MissingPermission")
    fun start(listener: Listener): Boolean {
        if (running) return true

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) {
            listener.onError("设备不支持 16 kHz 录音")
            return false
        }

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                maxOf(minBuffer * 2, SAMPLE_RATE / 2),
            )
        } catch (t: Throwable) {
            listener.onError("无法创建录音器：${t.message}")
            return false
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            listener.onError("麦克风被占用或权限不足")
            return false
        }

        record = recorder
        running = true
        try {
            recorder.startRecording()
        } catch (t: Throwable) {
            running = false
            recorder.release()
            record = null
            listener.onError("录音启动失败：${t.message}")
            return false
        }

        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buffer = ShortArray(CHUNK_SAMPLES)
            var peak = 0f
            var sessionMax = 0f
            var chunks = 0
            while (running) {
                val read = try {
                    recorder.read(buffer, 0, buffer.size)
                } catch (t: Throwable) {
                    -1
                }
                if (read > 0) {
                    val samples = FloatArray(read)
                    var sum = 0.0
                    for (i in 0 until read) {
                        val value = buffer[i] / 32768f
                        samples[i] = value
                        sum += value.toDouble() * value
                    }
                    val rms = sqrt(sum / read).toFloat()
                    if (rms > peak) peak = rms
                    if (rms > sessionMax) sessionMax = rms
                    chunks++
                    if (chunks >= 10) {
                        // One line per second: proves whether host audio reaches the guest.
                        android.util.Log.d(
                            "AudioCapture",
                            "rms_peak_1s=%.4f session_max=%.4f".format(peak, sessionMax),
                        )
                        peak = 0f
                        chunks = 0
                    }
                    listener.onChunk(samples, (rms * 9f).coerceIn(0f, 1f))
                } else if (read < 0) {
                    Thread.sleep(20)
                }
            }
        }, "audio-capture").also { it.start() }

        return true
    }

    fun stop() {
        running = false
        try {
            record?.stop()
        } catch (_: Throwable) {
        }
        thread?.join(1000)
        thread = null
        try {
            record?.release()
        } catch (_: Throwable) {
        }
        record = null
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHUNK_SAMPLES = 1600
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
