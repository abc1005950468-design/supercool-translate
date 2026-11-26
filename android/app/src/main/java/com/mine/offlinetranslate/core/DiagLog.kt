package com.mine.offlinetranslate.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lightweight on-device diagnostics that the user can share from the app. */
object DiagLog {
    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private var file: File? = null
    private var directory: File? = null
    private var installed = false
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val stampFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    fun init(context: Context) {
        if (installed) return
        installed = true
        val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
        directory = dir
        dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("diag-") && it.extension == "txt" }
            ?.forEach { it.delete() }
        file = null
        val memory = memoryInfo(context)
        log(
            "device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} " +
                "sdk=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.firstOrNull()}"
        )
        log(
            "ram total=${memory.totalMem / MB}MB available=${memory.availMem / MB}MB " +
                "low=${memory.lowMemory}"
        )
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            log("FATAL ${thread.name}: ${error.stackTraceToString()}")
            runCatching { previous?.uncaughtException(thread, error) }
        }
    }

    fun log(message: String) {
        val line = "[${timeFormat.format(Date())}] $message"
        Log.i("Diag", line)
        synchronized(lock) {
            lines.addLast(line)
            while (lines.size > 3000) lines.removeFirst()
        }
    }

    fun memory(context: Context, tag: String) {
        val info = memoryInfo(context)
        log("mem[$tag] avail=${info.availMem / MB}MB low=${info.lowMemory}")
    }

    fun export(): File? = synchronized(lock) {
        val dir = directory ?: return null
        val target = file ?: File(dir, "diag-${stampFormat.format(Date())}.txt").also {
            file = it
        }
        runCatching {
            target.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        }.getOrNull()
        target.takeIf { it.isFile }
    }

    private fun memoryInfo(context: Context): ActivityManager.MemoryInfo {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
    }

    private const val MB = 1024L * 1024L
}
