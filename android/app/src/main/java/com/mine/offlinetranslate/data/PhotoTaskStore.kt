package com.mine.offlinetranslate.data

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class PhotoTask(
    val id: String = "",
    val imagePath: String = "",
    val sourceText: String = "",
    val translatedText: String = "",
    val sourceLang: String = "",
    val targetLang: String = "",
    val status: String = "选择照片后开始识别",
    val busy: Boolean = false,
    val error: String? = null,
    val startedAt: Long = 0L,
    val completedAt: Long = 0L,
)

class PhotoTaskStore(context: Context) {
    private val dir = File(context.filesDir, "photo-task").apply { mkdirs() }
    private val stateFile = File(dir, "current.json")
    private val imageFile = File(dir, "current.jpg")

    fun load(): PhotoTask? = runCatching {
        if (!stateFile.isFile) return null
        val root = JSONObject(stateFile.readText())
        PhotoTask(
            id = root.optString("id"),
            imagePath = root.optString("imagePath").takeIf {
                it.isNotBlank() && File(it).isFile
            }.orEmpty(),
            sourceText = root.optString("sourceText"),
            translatedText = root.optString("translatedText"),
            sourceLang = root.optString("sourceLang"),
            targetLang = root.optString("targetLang"),
            status = root.optString("status", "选择照片后开始识别"),
            busy = root.optBoolean("busy", false),
            error = if (root.isNull("error")) null else root.optString("error"),
            startedAt = root.optLong("startedAt"),
            completedAt = root.optLong("completedAt"),
        )
    }.getOrNull()

    fun save(task: PhotoTask) {
        val root = JSONObject()
            .put("id", task.id)
            .put("imagePath", task.imagePath)
            .put("sourceText", task.sourceText)
            .put("translatedText", task.translatedText)
            .put("sourceLang", task.sourceLang)
            .put("targetLang", task.targetLang)
            .put("status", task.status)
            .put("busy", task.busy)
            .put("error", task.error ?: JSONObject.NULL)
            .put("startedAt", task.startedAt)
            .put("completedAt", task.completedAt)
        val temporary = File(dir, "current.json.tmp")
        temporary.writeText(root.toString())
        if (stateFile.exists()) stateFile.delete()
        temporary.renameTo(stateFile)
    }

    fun saveImage(bitmap: Bitmap): String {
        val temporary = File(dir, "current.jpg.tmp")
        FileOutputStream(temporary).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                "当前任务图片保存失败"
            }
        }
        if (imageFile.exists()) imageFile.delete()
        check(temporary.renameTo(imageFile)) { "当前任务图片写入失败" }
        return imageFile.absolutePath
    }

    fun clear() {
        stateFile.delete()
        imageFile.delete()
        File(dir, "current.json.tmp").delete()
        File(dir, "current.jpg.tmp").delete()
    }
}
