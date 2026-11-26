package com.mine.offlinetranslate.mt

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.mine.offlinetranslate.core.DiagLog
import com.mine.offlinetranslate.core.Languages
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Gemma through Google's standalone LiteRT-LM runtime. This path does not use
 * Google Play services and is separate from the llama.cpp GGUF engine.
 */
class GemmaTranslator(
    context: Context,
    private val modelPath: String?,
) : TranslationEngine {
    override val id: String = "gemma-litert"

    private val appContext = context.applicationContext
    private val loaded = AtomicBoolean(false)
    private val lock = Mutex()

    @Volatile
    private var engine: Engine? = null

    override suspend fun prepare(source: String, target: String): Result<Unit> = runCatching {
        lock.withLock { ensureLoadedLocked() }
        Unit
    }

    override suspend fun translate(text: String, source: String, target: String): String =
        lock.withLock {
            ensureLoadedLocked()
            val started = System.currentTimeMillis()
            val translateGemma = isTranslateGemma()
            val conversation = engine()
                .createConversation(
                    ConversationConfig(
                        samplerConfig = SamplerConfig(
                            topK = 1,
                            topP = 1.0,
                            temperature = 0.1,
                        ),
                    ),
                )
            try {
                val raw = withContext(Dispatchers.IO) {
                    conversation.sendMessage(
                        if (translateGemma) {
                            translateGemmaPrompt(text, source, target)
                        } else {
                            translationPrompt(text, source, target)
                        },
                        maxOutputToken = if (translateGemma) 384 else 128,
                    ).toString()
                }
                val result = cleanTranslation(raw)
                val elapsed = System.currentTimeMillis() - started
                DiagLog.log("gemma translate ${elapsed}ms src=$source tgt=$target len=${text.length} out=$result")
                Log.i(TAG, "translation: $result")
                result
            } finally {
                runCatching { conversation.close() }
            }
        }

    private suspend fun ensureLoadedLocked() {
        if (loaded.get()) return
        val path = modelPath
        if (path == null || !File(path).isFile || File(path).length() < 100_000_000L) {
            error("请先在设置 → 翻译引擎 → 离线模型中下载 Gemma 3 1B")
        }
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        if (memory.availMem < 1_900L * 1024 * 1024) {
            val available = memory.availMem / (1024 * 1024)
            DiagLog.log("gemma load refused: avail=${available}MB")
            error("可用内存不足（${available}MB），Gemma 3 1B 需要约 2 GB 连续内存")
        }

        withContext(Dispatchers.IO + NonCancellable) {
            Log.i(TAG, "loading model: $path")
            DiagLog.log("gemma load start ${File(path).length() / (1024 * 1024)}MB avail=${memory.availMem / (1024 * 1024)}MB")
        val created = if (File(path).name.contains("translategemma", ignoreCase = true)) {
            // The community TranslateGemma bundle keeps float32 embedding
            // weights and emits padding on WebGPU/OpenCL. Its upstream test
            // matrix recommends CPU for this artifact.
            createEngine(path, Backend.CPU(), "CPU")
        } else {
            try {
                createEngine(path, Backend.GPU(), "GPU")
            } catch (gpuError: Throwable) {
                DiagLog.log("gemma GPU init failed: ${gpuError.stackTraceToString()}")
                Log.w(TAG, "GPU init failed; retrying CPU", gpuError)
                createEngine(path, Backend.CPU(), "CPU")
            }
        }
            engine = created
        }
        loaded.set(true)
        DiagLog.log("gemma model ready")
        DiagLog.memory(appContext, "gemma-loaded")
    }

    private fun createEngine(path: String, backend: Backend, label: String): Engine {
        val candidate = Engine(
            EngineConfig(
                modelPath = path,
                backend = backend,
                maxNumTokens = 1024,
            ),
        )
        return try {
            candidate.initialize()
            Log.i(TAG, "model initialized with $label")
            DiagLog.log("gemma backend=$label")
            candidate
        } catch (error: Throwable) {
            runCatching { candidate.close() }
            throw error
        }
    }

    private fun engine(): Engine =
        checkNotNull(engine) { "Gemma engine is not initialized" }

    private fun isTranslateGemma(): Boolean =
        modelPath?.contains("translategemma", ignoreCase = true) == true

    override fun close() {
        if (loaded.compareAndSet(true, false)) {
            engine?.let { runCatching { it.close() } }
            engine = null
        }
    }

    private companion object {
        const val TAG = "GemmaTranslator"

        fun translationPrompt(text: String, source: String, target: String): String =
            buildString {
                append("把下面的")
                append(Languages.label(source))
                append("翻译成")
                append(Languages.label(target))
                append("。只输出")
                append(Languages.label(target))
                append("译文；严禁复制原文；不要解释、标签或引号。\n")
                append(text)
            }

        fun translateGemmaPrompt(text: String, source: String, target: String): String {
            val escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            return "<src>$source</src><dst>$target</dst><text>$escaped</text>"
        }

        fun cleanTranslation(raw: String): String {
            var text = raw
                .replace("<start_of_turn>model", "")
                .replace("<end_of_turn>", "")
                .replace("<eos>", "")
                .trim()
            val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            if (firstLine.isNotEmpty()) text = firstLine
            text = text
                .removePrefix("Translation:")
                .removePrefix("译文：")
                .removePrefix("翻译：")
                .trim()
            if (text.length >= 2 && text.first() == '"' && text.last() == '"') {
                text = text.substring(1, text.length - 1).trim()
            }
            return text
        }
    }
}
