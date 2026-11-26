package com.mine.offlinetranslate.mt

import android.content.Context
import android.app.ActivityManager
import android.util.Log
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.mine.offlinetranslate.core.Languages
import com.mine.offlinetranslate.core.DiagLog
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fully on-device translation through llama.cpp (no Google, no network).
 */
class LocalLlmTranslator(
    context: Context,
    private val modelPath: String?,
) : TranslationEngine {
    override val id: String = "local-llm"

    private val engine = AiChat.getInferenceEngine(context)
    private val appContext = context.applicationContext
    private val loaded = AtomicBoolean(false)
    private val lock = Mutex()

    override suspend fun prepare(source: String, target: String): Result<Unit> = runCatching {
        lock.withLock {
            ensureLoadedLocked()
        }
        Unit
    }

    override suspend fun translate(text: String, source: String, target: String): String {
        // llama.cpp contexts are not thread-safe: one generation at a time.
        return lock.withLock {
            ensureLoadedLocked()
            val started = System.currentTimeMillis()
            val prompt = buildString {
                append("把下面这段")
                append(Languages.label(source))
                append("翻译成")
                append(Languages.label(target))
                append("。只输出一行译文，不要加斜杠、注释或第二个答案：\n")
                append(text)
                // Qwen3 soft switch: skip the reasoning block, translate directly.
                append(" /no_think")
            }
            val builder = StringBuilder()
            engine.sendUserPrompt(prompt, 160).collect { token -> builder.append(token) }
            val result = stripReasoning(builder.toString())
            val elapsed = System.currentTimeMillis() - started
            Log.i(TAG, "translation: $result")
            DiagLog.log("llm translate ${elapsed}ms src=$source tgt=$target len=${text.length} out=$result")
            result
        }
    }

    private suspend fun ensureLoadedLocked() {
        if (loaded.get()) return
        val path = modelPath
        if (path == null || !File(path).isFile || File(path).length() < 1_000_000) {
            error("请先在设置 → 翻译引擎 → 离线模型中下载翻译模型")
        }
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        if (memory.availMem < 1_700L * 1024 * 1024) {
            val available = memory.availMem / (1024 * 1024)
            DiagLog.log("llm load refused: avail=${available}MB")
            error("可用内存不足（${available}MB），请关闭其他应用后重试")
        }
        // The engine is a process-wide singleton; a new translator may see
        // it already loaded (ModelReady). Reset first so loadModel is legal.
        if (engine.state.value !is InferenceEngine.State.Initialized) {
            runCatching { engine.cleanUp() }
                .onFailure { Log.w(TAG, "cleanUp before load failed", it) }
        }
        // A screen lock / activity destroy must not abort a 1 GB model load.
        withContext(NonCancellable) {
            Log.i(TAG, "loading model: $path")
            DiagLog.log("llm load start ${File(path).length() / (1024 * 1024)}MB avail=${memory.availMem / (1024 * 1024)}MB")
            try {
                engine.loadModel(path)
            } catch (t: Throwable) {
                DiagLog.log("llm load failed: ${t.stackTraceToString()}")
                Log.e(TAG, "loadModel failed", t)
                throw t
            }
            DiagLog.log("llm loadModel returned")
            runCatching {
                engine.setSystemPrompt(SYSTEM_PROMPT)
            }.onFailure {
                Log.w(TAG, "system prompt failed; continuing without it", it)
                DiagLog.log("llm system prompt failed: ${it.stackTraceToString()}")
            }
        }
        loaded.set(true)
        DiagLog.log("llm model ready")
        DiagLog.memory(appContext, "llm-loaded")
    }

    override fun close() {
        if (loaded.compareAndSet(true, false)) {
            runCatching { engine.cleanUp() }
        }
    }

    private companion object {
        const val TAG = "LocalLlmTranslator"
        const val SYSTEM_PROMPT =
            "你是专业的同声传译译员。只输出译文，不要解释、不要加引号、不要复述原文。"
    }
}

private fun stripReasoning(raw: String): String {
    var text = raw
    val end = text.indexOf("</think>")
    if (end >= 0) text = text.substring(end + "</think>".length)
    text = text
        .replace("<think>", "")
        .replace("</think>", "")
        .replace("<|im_end|>", "")
        .trim()
    // Small models sometimes append a short second guess like " / 不想".
    val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    if (firstLine.isNotEmpty()) {
        val slash = firstLine.indexOf(" / ")
        if (slash > 0 && firstLine.length - slash < 8) {
            return firstLine.substring(0, slash).trim()
        }
        return firstLine
    }
    return text
}
