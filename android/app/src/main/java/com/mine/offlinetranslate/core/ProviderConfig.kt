package com.mine.offlinetranslate.core

enum class AsrMode { LOCAL, CLOUD }

enum class MtMode { MLKIT, CLOUD, LOCAL_LLM }

data class CloudConfig(
    val baseUrl: String = "https://api.deepseek.com/v1",
    val apiKey: String = "",
    val chatModel: String = "deepseek-chat",
    val asrModel: String = "whisper-1",
    val keywords: String = "",
    val context: String = "",
)

data class ProviderConfig(
    val asr: AsrMode = AsrMode.LOCAL,
    val mt: MtMode = MtMode.LOCAL_LLM,
    val asrPackId: String = "ru-small",
    val llmPackId: String = "nllb-200-distilled-600m",
    val lidPackId: String = "lid-whisper-base",
    val zhSentenceRefine: Boolean = true,
    val ruSentenceRefine: Boolean = true,
    val ruRefinerId: String = "ru-gigaam-v3-int8",
    val languageRoute: String = "auto",
    val cloud: CloudConfig = CloudConfig(),
)
