package com.mine.offlinetranslate.data

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var sourceLang: String
        get() = prefs.getString("source_lang", "ru") ?: "ru"
        set(value) = prefs.edit().putString("source_lang", value).apply()

    var targetLang: String
        get() = prefs.getString("target_lang", "zh") ?: "zh"
        set(value) = prefs.edit().putString("target_lang", value).apply()

    var ttsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", false)
        set(value) = prefs.edit().putBoolean("tts_enabled", value).apply()

    var showSource: Boolean
        get() = prefs.getBoolean("show_source", true)
        set(value) = prefs.edit().putBoolean("show_source", value).apply()

    var speakerDiarization: Boolean
        get() = prefs.getBoolean("speaker_diarization", false)
        set(value) = prefs.edit().putBoolean("speaker_diarization", value).apply()

    var transcriptOnly: Boolean
        get() = prefs.getBoolean("transcript_only", false)
        set(value) = prefs.edit().putBoolean("transcript_only", value).apply()

    var fontSize: Int
        get() = prefs.getInt("font_size", 16)
        set(value) = prefs.edit().putInt("font_size", value).apply()

    var aiKeywords: String
        get() = prefs.getString("ai_keywords", "") ?: ""
        set(value) = prefs.edit().putString("ai_keywords", value).apply()

    var aiContext: String
        get() = prefs.getString("ai_context", "") ?: ""
        set(value) = prefs.edit().putString("ai_context", value).apply()

    var knowledgeBaseEnabled: Boolean
        get() = prefs.getBoolean("knowledge_base_enabled", false)
        set(value) = prefs.edit().putBoolean("knowledge_base_enabled", value).apply()

    var aiKeywordLearning: Boolean
        get() = prefs.getBoolean("ai_keyword_learning", true)
        set(value) = prefs.edit().putBoolean("ai_keyword_learning", value).apply()

    var aiAutoKeywords: String
        get() = prefs.getString("ai_auto_keywords", "") ?: ""
        set(value) = prefs.edit().putString("ai_auto_keywords", value).apply()

    var aiPersonalization: String
        get() = prefs.getString("ai_personalization", "") ?: ""
        set(value) = prefs.edit().putString("ai_personalization", value).apply()

    var totalSeconds: Long
        get() = prefs.getLong("total_seconds", 0)
        set(value) = prefs.edit().putLong("total_seconds", value).apply()

    var sessionCount: Int
        get() = prefs.getInt("session_count", 0)
        set(value) = prefs.edit().putInt("session_count", value).apply()

    var textMode: String
        get() = prefs.getString("text_mode", "mixed") ?: "mixed"
        set(value) = prefs.edit().putString("text_mode", value).apply()

    var panelMode: String
        get() = prefs.getString("panel_mode", "single") ?: "single"
        set(value) = prefs.edit().putString("panel_mode", value).apply()

    var panelALang: String
        get() = prefs.getString("panel_a_lang", "ru") ?: "ru"
        set(value) = prefs.edit().putString("panel_a_lang", value).apply()

    var panelBLang: String
        get() = prefs.getString("panel_b_lang", "zh") ?: "zh"
        set(value) = prefs.edit().putString("panel_b_lang", value).apply()

    var ttsVolume: Float
        get() = prefs.getFloat("tts_volume", 1f)
        set(value) = prefs.edit().putFloat("tts_volume", value).apply()

    var ttsVoice: String
        get() = prefs.getString("tts_voice", "") ?: ""
        set(value) = prefs.edit().putString("tts_voice", value).apply()
}
