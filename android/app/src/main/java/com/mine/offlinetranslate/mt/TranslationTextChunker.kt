package com.mine.offlinetranslate.mt

import kotlin.math.min

internal object TranslationTextChunker {
    private val sentenceEnd = Regex("(?<=[。！？!?.;；\\n])")

    fun split(text: String, maxChars: Int): List<String> {
        require(maxChars > 0)
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.length <= maxChars) return listOf(trimmed)

        val chunks = ArrayList<String>()
        val current = StringBuilder()
        sentenceEnd.split(trimmed)
            .filter { it.isNotBlank() }
            .flatMap { breakLongUnit(it.trim(), maxChars) }
            .forEach { unit ->
                val needed = current.length + unit.length + if (current.isEmpty()) 0 else 1
                if (current.isNotEmpty() && needed > maxChars) {
                    chunks += current.toString().trim()
                    current.clear()
                }
                if (current.isNotEmpty()) current.append('\n')
                current.append(unit)
            }
        if (current.isNotEmpty()) chunks += current.toString().trim()
        return chunks.filter { it.isNotEmpty() }
    }

    private fun breakLongUnit(unit: String, maxChars: Int): List<String> {
        if (unit.length <= maxChars) return listOf(unit)
        val parts = ArrayList<String>()
        var start = 0
        while (start < unit.length) {
            while (start < unit.length && unit[start].isWhitespace()) start++
            if (start >= unit.length) break
            var end = min(start + maxChars, unit.length)
            if (end < unit.length) {
                val space = unit.lastIndexOf(' ', end)
                if (space > start) end = space + 1
            }
            parts += unit.substring(start, end).trim()
            start = end
        }
        return parts
    }
}
