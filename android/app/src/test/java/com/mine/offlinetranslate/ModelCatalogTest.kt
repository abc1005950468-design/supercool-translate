package com.mine.offlinetranslate

import com.mine.offlinetranslate.core.ModelCatalog
import com.mine.offlinetranslate.core.ModelKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun idsAreUnique() {
        val ids = ModelCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun russianUsesStreamingPack() {
        assertEquals(ModelCatalog.RU_SMALL, ModelCatalog.asr("ru"))
    }

    @Test
    fun chineseAndEnglishUseBilingualPack() {
        assertEquals(ModelCatalog.ZH_SMALL, ModelCatalog.asr("zh"))
        assertEquals(ModelCatalog.EN_SMALL, ModelCatalog.asr("en"))
    }

    @Test
    fun everyPackHasPositiveSize() {
        ModelCatalog.all.forEach { pack ->
            assertTrue(pack.id, pack.totalBytes > 0)
            assertEquals(pack.id, pack.totalBytes, pack.files.sumOf { it.bytes })
        }
    }

    @Test
    fun qwenModelsAreNotDownloadable() {
        assertTrue(ModelCatalog.all.none { it.id.contains("qwen", ignoreCase = true) })
    }

    @Test
    fun lookupByIdWorks() {
        assertNotNull(ModelCatalog.byId("ru-small"))
        assertEquals(ModelCatalog.NLLB_600M, ModelCatalog.byId("nllb-200-distilled-600m"))
        assertEquals(ModelCatalog.NLLB_13B, ModelCatalog.byId("nllb-200-distilled-1.3b"))
    }

    @Test
    fun nllbUsesSeparateCachedDecodersAndIsUnderTwoGigabytes() {
        val pack = ModelCatalog.NLLB_600M
        assertEquals(1_351_991_749L, pack.totalBytes)
        assertTrue(pack.files.any { it.name == "decoder_model_quantized.onnx" })
        assertTrue(pack.files.any { it.name == "decoder_with_past_model_quantized.onnx" })
        assertTrue(pack.totalBytes < 2L * 1024 * 1024 * 1024)
    }

    @Test
    fun nllb13BIsAnOptionalHighQualityPack() {
        val pack = ModelCatalog.NLLB_13B
        assertEquals(ModelKind.MT, pack.kind)
        assertEquals(3_026_958_183L, pack.totalBytes)
        assertTrue(pack.files.any { it.name == "decoder_with_past_model_quantized.onnx" })
        assertTrue(ModelCatalog.NLLB_600M.totalBytes < pack.totalBytes)
    }

    @Test
    fun highAccuracySpeechAndLanguagePacksAreAvailable() {
        assertEquals(ModelKind.ASR, ModelCatalog.RU_WHISPER.kind)
        assertEquals(ModelKind.ASR, ModelCatalog.RU_GIGAAM.kind)
        assertEquals(ModelKind.LID, ModelCatalog.LID_BASE.kind)
        assertNotNull(ModelCatalog.byId("ru-whisper-small-int8"))
        assertNotNull(ModelCatalog.byId("ru-gigaam-v3-int8"))
        assertNotNull(ModelCatalog.byId("lid-whisper-base"))
        assertTrue(ModelCatalog.LID_TINY.totalBytes < ModelCatalog.LID_BASE.totalBytes)
    }

}
