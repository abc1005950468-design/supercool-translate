package com.mine.offlinetranslate.mt

interface TranslationEngine {
    val id: String

    suspend fun prepare(source: String, target: String): Result<Unit>

    suspend fun translate(text: String, source: String, target: String): String

    fun close()
}
