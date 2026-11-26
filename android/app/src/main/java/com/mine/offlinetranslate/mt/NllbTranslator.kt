package com.mine.offlinetranslate.mt

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.mine.offlinetranslate.core.DiagLog
import com.mine.offlinetranslate.core.ModelCatalog
import com.mine.offlinetranslate.core.ModelPack
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Dedicated many-to-many NLLB translator running fully on CPU. */
class NllbTranslator(
    context: Context,
    private val modelDir: String?,
    private val pack: ModelPack,
) : TranslationEngine {
    override val id: String = pack.id
    private val appContext = context.applicationContext

    override suspend fun prepare(source: String, target: String): Result<Unit> = runCatching {
        NllbRuntime.prepare(appContext, modelDir, pack)
    }

    override suspend fun translate(text: String, source: String, target: String): String {
        val started = System.currentTimeMillis()
        val result = NllbRuntime.translate(appContext, modelDir, pack, text, source, target)
        val elapsed = System.currentTimeMillis() - started
        DiagLog.log(
            "nllb translate pack=${pack.id} ${elapsed}ms src=$source tgt=$target " +
                "len=${text.length} out=$result",
        )
        Log.i(TAG, "translation: $result")
        return result
    }

    // The runtime is shared by every translator instance in this process.
    // ViewModel replacement must not unload sessions still used by another call.
    override fun close() = Unit

    private companion object {
        const val TAG = "NllbTranslator"
    }
}

internal object NllbRuntime {
    private val lock = Mutex()
    private var loadedKey: String? = null
    private var engine: NllbEngine? = null

    suspend fun prepare(context: Context, modelDir: String?, pack: ModelPack) {
        lock.withLock { ensureLoadedLocked(context, modelDir, pack) }
    }

    suspend fun translate(
        context: Context,
        modelDir: String?,
        pack: ModelPack,
        text: String,
        source: String,
        target: String,
    ): String = lock.withLock {
        ensureLoadedLocked(context, modelDir, pack)
        withContext(Dispatchers.Default) {
            checkNotNull(engine).translate(text, source, target)
        }
    }

    suspend fun release() {
        lock.withLock {
            val current = engine
            engine = null
            loadedKey = null
            if (current != null) {
                withContext(Dispatchers.IO + NonCancellable) {
                    runCatching { current.close() }
                }
                DiagLog.log("nllb model released")
            }
        }
    }

    private suspend fun ensureLoadedLocked(context: Context, modelDir: String?, pack: ModelPack) {
        val dir = modelDir
        if (dir == null) {
            error("请先在设置 → 翻译引擎 → 离线模型中下载 ${pack.label}")
        }
        val key = "${pack.id}|$dir"
        if (loadedKey == key && engine != null) return
        val files = pack.files
        if (files.any { File(dir, it.name).length() != it.bytes }) {
            error("${pack.label} 模型文件不完整，请在设置中重新下载")
        }
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        val requiredMb = if (pack.id == ModelCatalog.NLLB_13B.id) 5_200L else 2_300L
        if (memory.availMem < requiredMb * 1024 * 1024) {
            val available = memory.availMem / (1024 * 1024)
            DiagLog.log("nllb load refused pack=${pack.id} avail=${available}MB")
            error("可用内存不足（${available}MB），${pack.label} 需要约 ${requiredMb / 1024} GB 可用内存")
        }
        engine?.let { runCatching { it.close() } }
        engine = null
        loadedKey = null
        var created: NllbEngine? = null
        try {
            withContext(Dispatchers.IO + NonCancellable) {
                DiagLog.log(
                    "nllb load start pack=${pack.id} " +
                        "avail=${memory.availMem / (1024 * 1024)}MB",
                )
                created = NllbEngine(dir)
            }
        } finally {
            // Publish completed sessions even if the caller's coroutine was
            // cancelled after the NonCancellable native initialization.
            val ready = created
            if (ready != null) {
                engine = ready
                loadedKey = key
                DiagLog.log("nllb model ready pack=${pack.id}")
                DiagLog.memory(context, "nllb-loaded-${pack.id}")
            }
        }
    }
}
