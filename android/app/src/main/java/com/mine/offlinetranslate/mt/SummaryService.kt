package com.mine.offlinetranslate.mt

import com.mine.offlinetranslate.core.CloudConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class SummaryService {
    suspend fun summarize(transcript: String, config: CloudConfig): String =
        withContext(Dispatchers.IO) {
            val prompt = """
                你是会议纪要助手。请根据下面的同传记录生成中文会议纪要。
                要求：
                - 先给 3–6 条要点；
                - 再列决定事项；
                - 最后列行动项（负责人未知时写“待定”）；
                - 使用 Markdown，不要编造记录中没有的信息。

                记录：
                $transcript
            """.trimIndent()

            val payload = JSONObject()
                .put("model", config.chatModel)
                .put("temperature", 0.2)
                .put("max_tokens", 900)
                .put("reasoning_effort", "none")
                .put("stream", false)
                .put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "system")
                                .put("content", "你是严谨的会议纪要助手，只输出 Markdown 纪要。")
                        )
                        .put(JSONObject().put("role", "user").put("content", prompt)),
                )

            val connection = (URL(endpoint(config.baseUrl)).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                if (config.apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                }
            }
            val bytes = payload.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            try {
                connection.outputStream.use { it.write(bytes) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code !in 200..299) {
                    throw IOException("HTTP $code：${body.take(240)}")
                }
                JSONObject(body)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .optString("content", "")
                    .trim()
                    .ifEmpty { throw IOException("云端返回空纪要") }
            } finally {
                connection.disconnect()
            }
        }

    private fun endpoint(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }
}
