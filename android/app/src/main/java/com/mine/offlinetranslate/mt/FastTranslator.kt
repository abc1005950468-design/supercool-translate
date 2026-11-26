package com.mine.offlinetranslate.mt

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.io.IOException

/**
 * ML Kit on-device translation. The first use of a language pair downloads its
 * model; after that it works offline.
 */
class FastTranslator : TranslationEngine {
    override val id: String = "mlkit"

    private val clients = HashMap<String, Translator>()
    private val cache = LinkedHashMap<String, String>(64, 0.75f, true)
    private val lock = Mutex()

    private fun tag(code: String): String =
        TranslateLanguage.fromLanguageTag(code) ?: code

    private fun clientFor(source: String, target: String): Translator {
        val key = "$source>$target"
        return clients.getOrPut(key) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build()
            )
        }
    }

    override suspend fun prepare(sourceCode: String, targetCode: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = tag(sourceCode)
                val target = tag(targetCode)
                if (source == target) return@runCatching
                lock.withLock {
                    ensureModel(clientFor(source, target))
                }
                Unit
            }
        }

    override suspend fun translate(text: String, sourceCode: String, targetCode: String): String =
        withContext(Dispatchers.IO) {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return@withContext ""
            val source = tag(sourceCode)
            val target = tag(targetCode)
            if (source == target) return@withContext trimmed

            val key = "$source|$target|$trimmed"
            synchronized(cache) {
                cache[key]?.let { return@withContext it }
            }

            val result = lock.withLock {
                val client = clientFor(source, target)
                ensureModel(client)
                try {
                    Tasks.await(client.translate(trimmed), 12, TimeUnit.SECONDS)
                } catch (t: Throwable) {
                    throw IOException("离线翻译超时", t)
                }
            }
            synchronized(cache) {
                cache[key] = result
                while (cache.size > 400) {
                    val eldest = cache.keys.firstOrNull() ?: break
                    cache.remove(eldest)
                }
            }
            result
        }

    override fun close() {
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
        synchronized(cache) { cache.clear() }
    }

    /**
     * ML Kit downloads its language pack from Google on first use. Without Google
     * connectivity (emulator, GMS-free phones) that never completes, so fail in
     * seconds with an actionable message instead of hanging for a minute.
     */
    private suspend fun ensureModel(client: Translator) {
        try {
            Tasks.await(client.downloadModelIfNeeded(), 8, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            throw IOException("本机语言包不可用（需要 Google 服务）", t)
        }
    }
}
