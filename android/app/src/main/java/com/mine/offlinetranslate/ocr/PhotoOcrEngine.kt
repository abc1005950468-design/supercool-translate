package com.mine.offlinetranslate.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object PhotoOcrLanguage {
    fun detect(text: String): String {
        var han = 0
        var cyrillic = 0
        text.forEach { char ->
            val code = char.code
            when {
                code in 0x3400..0x9FFF || code in 0xF900..0xFAFF -> han++
                Character.UnicodeBlock.of(char) == Character.UnicodeBlock.CYRILLIC -> cyrillic++
            }
        }
        return when {
            cyrillic > 0 && cyrillic >= han -> "ru"
            han > 0 -> "zh"
            else -> "zh"
        }
    }

    fun targetFor(source: String): String = if (source == "ru") "zh" else "ru"

    fun score(text: String, confidence: Int, language: String): Double {
        val letters = text.count { it.isLetter() }.coerceAtLeast(1)
        val matched = text.count { char ->
            when (language) {
                "chi_sim" -> char.code in 0x3400..0x9FFF || char.code in 0xF900..0xFAFF
                "rus" -> Character.UnicodeBlock.of(char) == Character.UnicodeBlock.CYRILLIC
                else -> false
            }
        }
        val matchRatio = matched.toDouble() / letters
        return confidence.coerceAtLeast(0) + matchRatio * 50.0 - (1.0 - matchRatio) * 30.0
    }
}

class PhotoOcrEngine(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val engines = mutableMapOf<String, TessBaseAPI>()

    suspend fun recognize(bitmap: Bitmap): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val input = resizeForOcr(bitmap)
            try {
                OCR_LANGUAGES.map { language ->
                    val api = engines.getOrPut(language) { createEngine(language) }
                    api.setImage(input)
                    val text = api.utF8Text?.trim().orEmpty()
                    val confidence = api.meanConfidence()
                    api.clear()
                    Triple(language, text, PhotoOcrLanguage.score(text, confidence, language))
                }.maxBy { it.third }.second
            } finally {
                if (input !== bitmap && !input.isRecycled) input.recycle()
            }
        }
    }

    private fun createEngine(language: String): TessBaseAPI {
        ensureLanguageData()
        val root = File(appContext.filesDir, OCR_DIR)
        val api = TessBaseAPI()
        val initialized = api.init(
            root.absolutePath + File.separator,
            language,
            TessBaseAPI.OEM_LSTM_ONLY,
        )
        check(initialized) { "内置 OCR 模型初始化失败：$language" }
        api.pageSegMode = if (language == "chi_sim") {
            TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
        } else {
            TessBaseAPI.PageSegMode.PSM_AUTO
        }
        api.setVariable("user_defined_dpi", "300")
        api.setVariable("preserve_interword_spaces", "1")
        return api
    }

    private fun ensureLanguageData() {
        val root = File(appContext.filesDir, OCR_DIR)
        val tessdata = File(root, "tessdata")
        val marker = File(root, "asset-version")
        if (marker.takeIf { it.isFile }?.readText() == OCR_ASSET_VERSION) return

        check(tessdata.exists() || tessdata.mkdirs()) { "无法创建 OCR 模型目录" }
        OCR_FILES.forEach { name ->
            val target = File(tessdata, name)
            val temporary = File(tessdata, "$name.part")
            appContext.assets.open("tessdata/$name").use { input ->
                FileOutputStream(temporary).use { output -> input.copyTo(output) }
            }
            check(temporary.length() > 0L) { "内置 OCR 模型为空：$name" }
            if (target.exists()) check(target.delete()) { "无法更新 OCR 模型：$name" }
            check(temporary.renameTo(target)) { "无法安装 OCR 模型：$name" }
        }
        marker.writeText(OCR_ASSET_VERSION)
    }

    private fun resizeForOcr(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        val scale = min(1f, OCR_MAX_SIDE.toFloat() / longest.coerceAtLeast(1))
        if (scale >= 0.999f) return bitmap
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private companion object {
        const val OCR_DIR = "tesseract"
        val OCR_LANGUAGES = listOf("chi_sim", "rus")
        const val OCR_ASSET_VERSION = "tesseract-5.5.1-chi_sim-best-rus-fast-3"
        const val OCR_MAX_SIDE = 2000
        val OCR_FILES = listOf("chi_sim.traineddata", "rus.traineddata")
    }
}

suspend fun decodePhoto(context: Context, uri: Uri, maxSide: Int = 2000): Bitmap =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "无法读取图片" }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片格式不受支持" }

        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxSide * 2) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri).use { input ->
            checkNotNull(input) { "无法读取图片" }
            checkNotNull(BitmapFactory.decodeStream(input, null, options)) { "图片解码失败" }
        }
        val oriented = applyExifRotation(resolver.openInputStream(uri), decoded)
        val longest = max(oriented.width, oriented.height)
        if (longest <= maxSide) {
            oriented
        } else {
            val scale = maxSide.toFloat() / longest
            val resized = Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).toInt().coerceAtLeast(1),
                (oriented.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (resized !== oriented) oriented.recycle()
            resized
        }
    }

private fun applyExifRotation(input: java.io.InputStream?, bitmap: Bitmap): Bitmap {
    val orientation = input?.use {
        runCatching {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}
