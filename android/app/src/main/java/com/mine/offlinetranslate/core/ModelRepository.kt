package com.mine.offlinetranslate.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class ModelRepository(private val context: Context) {

    fun dir(pack: ModelPack): File =
        File(context.filesDir, "models/${pack.id}").apply { mkdirs() }

    fun isReady(pack: ModelPack): Boolean =
        pack.files.all { File(dir(pack), it.name).length() == it.bytes }

    /** True when this build ships the pack inside the APK. */
    fun hasBundled(pack: ModelPack): Boolean = runCatching {
        context.assets.open("models/${pack.id}/${pack.files.first().name}").close()
        true
    }.getOrDefault(false)

    /**
     * Installs a bundled pack from assets on first launch so the phone works
     * without downloading anything.
     */
    suspend fun ensureBundled(
        pack: ModelPack,
        onProgress: (Float, String) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (isReady(pack)) return@runCatching
            var done = 0L
            pack.files.forEach { file ->
                coroutineContext.ensureActive()
                val target = File(dir(pack), file.name)
                if (target.length() == file.bytes) {
                    done += file.bytes
                    return@forEach
                }
                val part = File(dir(pack), file.name + ".part")
                context.assets.open("models/${pack.id}/${file.name}").use { input ->
                    FileOutputStream(part, false).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(
                                (done.toDouble() / pack.totalBytes).toFloat().coerceIn(0f, 1f),
                                "安装内置模型 ${file.name}",
                            )
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }
                if (part.length() != file.bytes) {
                    part.delete()
                    throw IOException("内置模型不完整：${file.name}")
                }
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) throw IOException("无法写入 ${file.name}")
            }
            if (!isReady(pack)) throw IOException("内置模型校验失败")
        }
    }

    fun installedBytes(pack: ModelPack): Long = pack.files.sumOf { file ->
        val exact = File(dir(pack), file.name)
        val part = File(dir(pack), file.name + ".part")
        when {
            exact.length() == file.bytes -> file.bytes
            exact.exists() -> 0L
            else -> part.length().coerceAtMost(file.bytes)
        }
    }

    suspend fun download(
        pack: ModelPack,
        onProgress: (Float, String) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val required = pack.totalBytes + 200L * 1024 * 1024
            if (context.filesDir.usableSpace < required) {
                DiagLog.log("download refused ${pack.id}: storage ${mb(context.filesDir.usableSpace)}MB < ${mb(required)}MB")
                throw IOException("存储空间不足：需要约 ${mb(required)} MB 可用空间")
            }
            DiagLog.log("download start ${pack.id} total=${mb(pack.totalBytes)}MB")
            var doneBefore = 0L
            pack.files.forEach { file ->
                coroutineContext.ensureActive()
                val target = File(dir(pack), file.name)
                if (target.length() == file.bytes) {
                    doneBefore += file.bytes
                    onProgress(doneBefore.toFloat() / pack.totalBytes, "已就绪 ${file.name}")
                    return@forEach
                }
                if (target.exists()) target.delete()

                val part = File(dir(pack), file.name + ".part")

                val sources = pack.urls(file.name)
                var lastError: Exception? = null
                var saved = false
                for ((index, source) in sources.withIndex()) {
                    coroutineContext.ensureActive()
                    val host = runCatching { URL(source).host }.getOrDefault("下载源")
                    DiagLog.log("download source $host (${index + 1}/${sources.size}) file=${file.name}")
                    onProgress(
                        doneBefore.toFloat() / pack.totalBytes,
                        "连接 $host（${index + 1}/${sources.size}）",
                    )
                    try {
                        downloadFile(
                            source,
                            part,
                            file.bytes,
                            pack.totalBytes,
                            doneBefore,
                            onProgress,
                        )
                        if (part.length() != file.bytes) throw IOException("文件大小不符")
                        if (target.exists()) target.delete()
                        if (!part.renameTo(target)) throw IOException("无法保存 ${file.name}")
                        saved = true
                        break
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (e: Exception) {
                        lastError = e
                        DiagLog.log(
                            "download source failed $host: ${e.message}; " +
                                "kept ${part.length()} bytes for resume",
                        )
                        onProgress(doneBefore.toFloat() / pack.totalBytes, "源不可用：$host")
                    }
                }
                if (!saved) {
                    throw IOException("所有下载源失败（${lastError?.message ?: "未知错误"}）")
                }
                doneBefore += file.bytes
                DiagLog.log("download done ${file.name}")
                onProgress(doneBefore.toFloat() / pack.totalBytes, "已完成 ${file.name}")
            }
            if (!isReady(pack)) throw IOException("模型校验失败")
        }
    }

    private suspend fun downloadFile(
        source: String,
        part: File,
        fileBytes: Long,
        packBytes: Long,
        doneBefore: Long,
        onProgress: (Float, String) -> Unit,
    ) {
        var existing = if (part.exists()) part.length() else 0L
        if (existing > fileBytes) {
            part.delete()
            existing = 0L
        }
        var current = source
        var hops = 0
        while (true) {
            // Follow redirects manually so the Range header survives the CDN hop.
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "OfflineTranslate/1.0")
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            val code = try {
                connection.responseCode
            } catch (t: Throwable) {
                connection.disconnect()
                throw t
            }
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank() || hops >= 6) throw IOException("重定向失败")
                current = runCatching {
                    java.net.URI(current).resolve(location).toString()
                }.getOrDefault(location)
                hops++
                continue
            }
            if (code !in 200..299) {
                connection.disconnect()
                throw IOException("HTTP $code")
            }
            val append = code == HttpURLConnection.HTTP_PARTIAL && existing > 0
            if (append) {
                val contentRange = connection.getHeaderField("Content-Range")
                if (contentRange?.startsWith("bytes $existing-") != true) {
                    connection.disconnect()
                    part.delete()
                    throw IOException("服务器断点位置不匹配：${contentRange ?: "缺失"}")
                }
            }
            if (!append) {
                part.delete()
                existing = 0L
            }
            var received = existing
            var lastTick = 0L
            try {
                connection.inputStream.use { input ->
                    FileOutputStream(part, append).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            received += read
                            val now = System.currentTimeMillis()
                            if (now - lastTick > 160) {
                                lastTick = now
                                val total = doneBefore + received
                                onProgress(
                                    (total.toDouble() / packBytes).toFloat().coerceIn(0f, 1f),
                                    "已下载 ${mb(total)}/${mb(packBytes)} MB",
                                )
                            }
                        }
                        output.flush()
                        output.fd.sync()
                    }
                }
            } finally {
                connection.disconnect()
            }
            return
        }
    }

    fun importFile(pack: ModelPack, uri: Uri): String? {
        val name = displayName(uri) ?: return null
        val known = pack.files.firstOrNull { it.name == name } ?: return null
        val part = File(dir(pack), name + ".part")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(part, false).use { output -> input.copyTo(output) }
        } ?: return null
        if (part.length() != known.bytes) {
            part.delete()
            return null
        }
        val target = File(dir(pack), name)
        if (target.exists()) target.delete()
        return if (part.renameTo(target)) name else null
    }

    fun delete(pack: ModelPack) {
        dir(pack).listFiles()?.forEach { it.delete() }
    }

    /** Remove model caches for packs that were withdrawn from the download catalog. */
    fun deleteLegacyQwenModels(): Long {
        val root = File(context.filesDir, "models")
        var freed = 0L
        root.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("qwen", ignoreCase = true) }
            ?.forEach { directory ->
                directory.walkBottomUp().forEach { file ->
                    if (file.isFile) freed += file.length()
                    file.delete()
                }
            }
        return freed
    }

    private fun displayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
                return null
            }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun mb(bytes: Long): String = "%.1f".format(bytes / 1024.0 / 1024.0)
}
