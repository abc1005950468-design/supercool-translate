package com.mine.offlinetranslate

import com.mine.offlinetranslate.ocr.PhotoOcrLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoOcrLanguageTest {
    @Test
    fun detectsChineseAndRussianText() {
        assertEquals("zh", PhotoOcrLanguage.detect("今天天气很好"))
        assertEquals("ru", PhotoOcrLanguage.detect("Сегодня хорошая погода"))
    }

    @Test
    fun usesTheDominantScriptForMixedText() {
        assertEquals("ru", PhotoOcrLanguage.detect("Привет 世界"))
        assertEquals("zh", PhotoOcrLanguage.detect("你好世界，мир"))
    }

    @Test
    fun russianTranslatesToChineseAndAllOthersToRussian() {
        assertEquals("zh", PhotoOcrLanguage.targetFor("ru"))
        assertEquals("ru", PhotoOcrLanguage.targetFor("zh"))
    }

    @Test
    fun matchingScriptOutranksForeignNoiseAtEqualConfidence() {
        val chinese = PhotoOcrLanguage.score("今天天气很好", 70, "chi_sim")
        val noisyRussian = PhotoOcrLanguage.score("ан  中  и", 70, "chi_sim")
        assertEquals(true, chinese > noisyRussian)
    }
}
