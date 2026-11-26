package com.mine.offlinetranslate

import com.mine.offlinetranslate.mt.TranslationTextChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationTextChunkerTest {
    @Test
    fun longChineseTextIsSplitWithoutLosingContent() {
        val text = buildString {
            repeat(20) { index ->
                append("这是第${index + 1}句用于验证长文本翻译不会被截断。\n")
            }
        }

        val chunks = TranslationTextChunker.split(text, 35)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 35 })
        assertEquals(
            text.filterNot { it.isWhitespace() },
            chunks.joinToString("").filterNot { it.isWhitespace() },
        )
    }

    @Test
    fun shortTextStaysInOneChunk() {
        assertEquals(
            listOf("今天天气很好。"),
            TranslationTextChunker.split("今天天气很好。", 140),
        )
    }
}
