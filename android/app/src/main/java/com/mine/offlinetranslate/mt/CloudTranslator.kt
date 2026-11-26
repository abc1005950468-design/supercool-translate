package com.mine.offlinetranslate.mt

import android.util.Log
import com.mine.offlinetranslate.core.CloudConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Any OpenAI-compatible chat endpoint: DeepSeek, DashScope compatible mode,
 * vLLM, Ollama, llama.cpp server, or a later self-hosted server.
 */
class CloudTranslator(private val config: CloudConfig) : TranslationEngine {
    override val id: String = "cloud"

    private val cache = LinkedHashMap<String, String>(32, 0.75f, true)

    override suspend fun prepare(source: String, target: String): Result<Unit> =
        runCatching {
            require(config.baseUrl.isNotBlank()) { "云端地址为空" }
            Unit
        }

    override suspend fun translate(text: String, source: String, target: String): String =
        withContext(Dispatchers.IO) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return@withContext ""
            val key = "$source|$target|$trimmed"
            synchronized(cache) {
                cache[key]?.let { return@withContext it }
            }

            val content = TranslationTextChunker.split(trimmed, MAX_CHUNK_CHARS)
                .map { chunk -> requestTranslation(chunk, source, target) }
                .joinToString("\n")
                .trim()
            synchronized(cache) {
                cache[key] = content
                while (cache.size > 200) {
                    val eldest = cache.keys.firstOrNull() ?: break
                    cache.remove(eldest)
                }
            }
            content
        }

    override fun close() {
        synchronized(cache) { cache.clear() }
    }

    private fun endpoint(): String {
        val base = config.baseUrl.trim().trimEnd('/')
        return when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }

    private fun requestTranslation(text: String, source: String, target: String): String {
        val payload = JSONObject()
            .put("model", config.chatModel)
            .put("temperature", 0.1)
            .put("max_tokens", MAX_OUTPUT_TOKENS)
            .put("reasoning_effort", "none")
            .put("stream", false)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", systemPrompt(source, target))
                    )
                    .put(JSONObject().put("role", "user").put("content", text)),
            )

        val connection = (URL(endpoint()).openConnection() as HttpURLConnection).apply {
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
        Log.i(TAG, "POST ${endpoint()} model=${config.chatModel} bytes=${bytes.size}")

        try {
            connection.outputStream.use { output ->
                output.write(bytes)
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            Log.i(TAG, "HTTP $code body=${body.take(240)}")
            if (code !in 200..299) {
                throw IOException("云端返回 HTTP $code：${body.take(240)}")
            }
            val content = JSONObject(body)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content", "")
                .trim()
            if (content.isEmpty()) throw IOException("云端返回空译文")
            return content
        } finally {
            connection.disconnect()
        }
    }

    private fun systemPrompt(source: String, target: String): String =
        buildString {
            append(
                "You are a professional simultaneous interpreter. Translate the user's text " +
                    "from ${languageName(source)} to ${languageName(target)}. Preserve names, " +
                    "numbers, and terminology. Output only the translation, without quotes or explanation."
            )
            if (config.keywords.isNotBlank()) {
                append(" Glossary (use these translations when relevant): ")
                append(config.keywords.take(600))
                append('.')
            }
            if (config.context.isNotBlank()) {
                append(" Context: ")
                append(config.context.take(600))
                append('.')
            }
        }

    private fun languageName(code: String): String = when (code) {
        "ru" -> "Russian"
        "zh" -> "Simplified Chinese"
        "en" -> "English"
        "ja" -> "Japanese"
        "ko" -> "Korean"
        "fr" -> "French"
        "de" -> "German"
        "es" -> "Spanish"
        "it" -> "Italian"
        "pt" -> "Portuguese"
        else -> code
    }

    companion object {
        private const val TAG = "CloudTranslator"
        private const val MAX_CHUNK_CHARS = 600
        private const val MAX_OUTPUT_TOKENS = 1600
    }
}
