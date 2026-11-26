package com.mine.offlinetranslate.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ProviderStore internal constructor(
    private val prefs: SharedPreferences,
    private val encryptedPrefs: SharedPreferences?,
) {
    private constructor(context: Context, encryptedPrefs: SharedPreferences?) : this(
        encryptedPrefs ?: context.getSharedPreferences("provider_config_fallback", Context.MODE_PRIVATE),
        encryptedPrefs,
    )

    constructor(context: Context) : this(context, openEncryptedPrefs(context))

    private companion object {
        const val CLOUD_KEY = "cloud_key"

        fun openEncryptedPrefs(context: Context): SharedPreferences? = runCatching {
            val key = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "provider_config",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrNull()
    }

    fun load(): ProviderConfig = ProviderConfig(
        asr = runCatching { AsrMode.valueOf(prefs.getString("asr", null) ?: "LOCAL") }
            .getOrDefault(AsrMode.LOCAL),
        mt = runCatching { MtMode.valueOf(prefs.getString("mt", null) ?: "LOCAL_LLM") }
            .getOrDefault(MtMode.LOCAL_LLM),
        asrPackId = prefs.getString("asr_pack", "ru-small") ?: "ru-small",
        llmPackId = validLocalPack(prefs.getString("llm_pack", "nllb-200-distilled-600m")),
        lidPackId = validLidPack(prefs.getString("lid_pack", ModelCatalog.LID_BASE.id)),
        zhSentenceRefine = prefs.getBoolean("zh_sentence_refine", true),
        ruSentenceRefine = prefs.getBoolean("ru_sentence_refine", true),
        ruRefinerId = validRuRefiner(
            prefs.getString("ru_refiner_pack", ModelCatalog.RU_GIGAAM.id),
        ),
        languageRoute = validLanguageRoute(prefs.getString("language_route", "auto")),
        cloud = CloudConfig(
            baseUrl = prefs.getString("cloud_base", "https://api.deepseek.com/v1")
                ?: "https://api.deepseek.com/v1",
            apiKey = encryptedPrefs?.getString(CLOUD_KEY, "") ?: "",
            chatModel = prefs.getString("cloud_chat_model", "deepseek-chat") ?: "deepseek-chat",
            asrModel = prefs.getString("cloud_asr_model", "whisper-1") ?: "whisper-1",
        ),
    )

    fun save(config: ProviderConfig) {
        prefs.edit()
            .putString("asr", config.asr.name)
            .putString("mt", config.mt.name)
            .putString("asr_pack", config.asrPackId)
            .putString("llm_pack", config.llmPackId)
            .putString("lid_pack", config.lidPackId)
            .putBoolean("zh_sentence_refine", config.zhSentenceRefine)
            .putBoolean("ru_sentence_refine", config.ruSentenceRefine)
            .putString("ru_refiner_pack", config.ruRefinerId)
            .putString("language_route", config.languageRoute)
            .putString("cloud_base", config.cloud.baseUrl)
            .putString("cloud_chat_model", config.cloud.chatModel)
            .putString("cloud_asr_model", config.cloud.asrModel)
            .apply()
    }

    fun saveApiKey(value: String): Boolean {
        val key = value.trim()
        if (key.isEmpty()) return false
        return runCatching {
            encryptedPrefs?.edit()?.putString(CLOUD_KEY, key)?.commit() == true
        }.getOrDefault(false)
    }

    fun clearApiKey(): Boolean = runCatching {
        encryptedPrefs?.edit()?.remove(CLOUD_KEY)?.commit() == true
    }.getOrDefault(false)

    private fun validLocalPack(id: String?): String {
        val pack = id?.let(ModelCatalog::byId)
        return if (pack?.kind == ModelKind.LLM || pack?.kind == ModelKind.MT) {
            pack.id
        } else {
            ModelCatalog.NLLB_600M.id
        }
    }

    private fun validLidPack(id: String?): String {
        val pack = id?.let(ModelCatalog::byId)
        return if (pack?.kind == ModelKind.LID) pack.id else ModelCatalog.LID_BASE.id
    }

    private fun validRuRefiner(id: String?): String {
        val pack = id?.let(ModelCatalog::byId)
        return if (
            pack?.kind == ModelKind.ASR &&
            (pack.id == ModelCatalog.RU_GIGAAM.id || pack.id == ModelCatalog.RU_WHISPER.id)
        ) {
            pack.id
        } else {
            ModelCatalog.RU_GIGAAM.id
        }
    }

    private fun validLanguageRoute(route: String?): String = when (route) {
        "auto", "zh", "ru" -> route
        else -> "auto"
    }
}
