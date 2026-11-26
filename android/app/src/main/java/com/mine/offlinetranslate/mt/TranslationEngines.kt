package com.mine.offlinetranslate.mt

import android.util.Log
import android.content.Context
import com.mine.offlinetranslate.core.ModelCatalog
import com.mine.offlinetranslate.core.ModelKind
import com.mine.offlinetranslate.core.MtMode
import com.mine.offlinetranslate.core.ProviderConfig
import kotlinx.coroutines.CancellationException

class FallbackTranslator(
    private val primary: TranslationEngine,
    private val fallback: TranslationEngine,
) : TranslationEngine {
    override val id: String = "${primary.id}+${fallback.id}"

    override suspend fun prepare(source: String, target: String): Result<Unit> {
        val first = primary.prepare(source, target)
        if (first.isSuccess) return first
        return fallback.prepare(source, target)
    }

    override suspend fun translate(text: String, source: String, target: String): String {
        return try {
            primary.translate(text, source, target)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Log.w("FallbackTranslator", "primary ${primary.id} failed: ${error.message}")
            fallback.translate(text, source, target)
        }
    }

    override fun close() {
        primary.close()
        fallback.close()
    }
}

object TranslationEngines {
    fun create(
        config: ProviderConfig,
        context: Context,
        localModelPath: String?,
        localPackId: String? = null,
    ): TranslationEngine = when (config.mt) {
        // ML Kit is offline after one download, but that download needs Google.
        // When an OpenAI-compatible endpoint is configured, use it as a fallback
        // so emulators and GMS-free phones still translate.
        MtMode.MLKIT -> if (config.cloud.baseUrl.isNotBlank() && config.cloud.apiKey.isNotBlank()) {
            FallbackTranslator(
                primary = FastTranslator(),
                fallback = CloudTranslator(config.cloud),
            )
        } else {
            FastTranslator()
        }
        MtMode.CLOUD -> FallbackTranslator(
            primary = CloudTranslator(config.cloud),
            fallback = FastTranslator(),
        )
        MtMode.LOCAL_LLM -> {
            val localPack = localPackId?.let(ModelCatalog::byId)
            when {
                localPack?.kind == ModelKind.MT -> {
                    NllbTranslator(context, localModelPath, localPack)
                }
                localModelPath?.endsWith(".task", ignoreCase = true) == true ||
                    localModelPath?.endsWith(".litertlm", ignoreCase = true) == true -> {
                    GemmaTranslator(context, localModelPath)
                }
                else -> LocalLlmTranslator(context, localModelPath)
            }
        }
    }
}
