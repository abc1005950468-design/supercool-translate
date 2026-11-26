package com.mine.offlinetranslate.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

class TtsSpeaker(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    private var pending: Pair<String, String>? = null

    @Volatile
    private var volume: Float = 1f

    @Volatile
    private var voiceName: String = ""

    fun init() {
        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setSpeechRate(1.0f)
                applyVoice()
                pending?.let { (text, lang) -> speakNow(text, lang) }
            }
            pending = null
        }
    }

    fun availableVoices(): List<String> {
        val engine = tts ?: return emptyList()
        if (!ready) return emptyList()
        return engine.voices?.mapNotNull { it.name }?.sorted() ?: emptyList()
    }

    fun setVoice(name: String) {
        voiceName = name
        applyVoice()
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
    }

    private fun applyVoice() {
        val engine = tts ?: return
        if (!ready) return
        if (voiceName.isBlank()) return
        val match: Voice? = engine.voices?.firstOrNull { it.name == voiceName }
        if (match != null) engine.setVoice(match)
    }

    fun speak(text: String, lang: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (!ready) {
            pending = clean to lang
            return
        }
        speakNow(clean, lang)
    }

    private fun speakNow(text: String, lang: String) {
        val engine = tts ?: return
        engine.language = localeOf(lang)
        applyVoice()
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)
        }
        engine.speak(text.take(400), TextToSpeech.QUEUE_ADD, params, "tts-${System.nanoTime()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun localeOf(tag: String): Locale = when (tag) {
        "zh" -> Locale.SIMPLIFIED_CHINESE
        "en" -> Locale.US
        "ru" -> Locale("ru", "RU")
        "ja" -> Locale.JAPAN
        "ko" -> Locale.KOREA
        else -> Locale.forLanguageTag(tag)
    }
}
