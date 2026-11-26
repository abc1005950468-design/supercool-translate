package com.mine.offlinetranslate.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordLine(
    val source: String,
    val translation: String?,
    val speaker: String? = null,
    val panel: String = "A",
)

enum class RecordKind { SPEECH, PHOTO }

data class RecordSession(
    val id: String,
    val title: String,
    val startedAt: Long,
    val endedAt: Long,
    val sourceLang: String,
    val targetLang: String,
    val lines: List<RecordLine>,
    val starred: Boolean = false,
    val folder: String = "",
    val kind: RecordKind = RecordKind.SPEECH,
)

class RecordStore(private val context: Context) {
    private val dir = File(context.filesDir, "records").apply { mkdirs() }
    private val exportDir = File(context.filesDir, "exports").apply { mkdirs() }

    fun save(session: RecordSession) {
        val root = JSONObject()
            .put("id", session.id)
            .put("title", session.title)
            .put("startedAt", session.startedAt)
            .put("endedAt", session.endedAt)
            .put("sourceLang", session.sourceLang)
            .put("targetLang", session.targetLang)
            .put("starred", session.starred)
            .put("folder", session.folder)
            .put("kind", session.kind.name)
        val lines = JSONArray()
        session.lines.forEach { line ->
            lines.put(
                JSONObject()
                    .put("s", line.source)
                    .put("t", line.translation ?: JSONObject.NULL)
                    .put("sp", line.speaker ?: JSONObject.NULL)
                    .put("p", line.panel)
            )
        }
        root.put("lines", lines)
        val tmp = File(dir, "${session.id}.tmp")
        tmp.writeText(root.toString())
        val target = File(dir, "${session.id}.json")
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }

    fun list(): List<RecordSession> =
        dir.listFiles { file -> file.name.endsWith(".json") }
            ?.mapNotNull { parse(it) }
            ?.sortedByDescending { it.startedAt }
            ?: emptyList()

    fun get(id: String): RecordSession? = parse(File(dir, "$id.json"))

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    fun setStarred(id: String, starred: Boolean) {
        val session = get(id) ?: return
        save(session.copy(starred = starred))
    }

    fun rename(id: String, title: String) {
        val session = get(id) ?: return
        save(session.copy(title = title))
    }

    fun setFolder(id: String, folder: String) {
        val session = get(id) ?: return
        save(session.copy(folder = folder))
    }

    fun folders(): List<String> =
        list()
            .map { it.folder }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    fun exportMarkdown(session: RecordSession): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
            .format(Date(session.startedAt))
        val file = File(exportDir, "record-$stamp.md")
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("# ").append(session.title).append("\n\n")
        sb.append(if (session.kind == RecordKind.PHOTO) "拍照翻译" else "同传记录")
            .append("\n\n")
        sb.append(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(session.startedAt))
        ).append(" · ").append(session.sourceLang).append(" → ")
            .append(session.targetLang).append("\n\n")
        val dualPanel = session.lines.any { it.panel == "B" }
        session.lines.forEachIndexed { index, line ->
            sb.append("**").append(index + 1).append(". ").append(time.format(Date())).append("**\n\n")
            line.speaker?.let { sb.append("*").append(it).append("*") }
            if (dualPanel) sb.append("[").append(line.panel).append("]")
            if (line.speaker != null || dualPanel) sb.append("\n\n")
            sb.append(line.source).append("\n\n")
            line.translation?.takeIf { it.isNotBlank() }?.let {
                sb.append("> ").append(it).append("\n\n")
            }
        }
        file.writeText(sb.toString())
        return file
    }

    private fun parse(file: File): RecordSession? = runCatching {
        if (!file.isFile) return null
        val root = JSONObject(file.readText())
        val lines = mutableListOf<RecordLine>()
        val array = root.optJSONArray("lines") ?: JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            lines += RecordLine(
                source = obj.optString("s"),
                translation = if (obj.isNull("t")) null else obj.optString("t"),
                speaker = if (obj.isNull("sp")) null else obj.optString("sp"),
                panel = obj.optString("p", "A").ifBlank { "A" },
            )
        }
        RecordSession(
            id = root.optString("id", file.nameWithoutExtension),
            title = root.optString("title", "翻译记录"),
            startedAt = root.optLong("startedAt"),
            endedAt = root.optLong("endedAt"),
            sourceLang = root.optString("sourceLang", "ru"),
            targetLang = root.optString("targetLang", "zh"),
            lines = lines,
            starred = root.optBoolean("starred", false),
            folder = root.optString("folder", ""),
            kind = runCatching {
                RecordKind.valueOf(root.optString("kind", RecordKind.SPEECH.name))
            }.getOrDefault(RecordKind.SPEECH),
        )
    }.getOrNull()
}
