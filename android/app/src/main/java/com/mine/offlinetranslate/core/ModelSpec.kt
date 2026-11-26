package com.mine.offlinetranslate.core

enum class ModelKind { ASR, LLM, MT, TTS, SPEAKER, LID }

data class ModelFile(
    val name: String,
    val bytes: Long,
    val sources: List<String> = emptyList(),
)

data class ModelPack(
    val id: String,
    val kind: ModelKind,
    val label: String,
    val description: String,
    val repo: String,
    val files: List<ModelFile>,
    val license: String,
    val minRamGb: Int = 0,
    val modelScopeRepo: String? = null,
) {
    val totalBytes: Long get() = files.sumOf { it.bytes }

    /** Primary + fallback sources; a failed source rolls over to the next. */
    fun urls(name: String): List<String> = buildList {
        files.firstOrNull { it.name == name }?.sources?.let { sources ->
            if (sources.isNotEmpty()) {
                addAll(sources)
                return@buildList
            }
        }
        add("https://hf-mirror.com/$repo/resolve/main/$name")
        add("https://huggingface.co/$repo/resolve/main/$name")
        modelScopeRepo?.let {
            add("https://modelscope.cn/models/$it/resolve/master/$name")
        }
    }
}

/**
 * One source of truth for local downloads. The PC harness reads the same
 * catalog so desktop tests and the APK agree.
 */
object ModelCatalog {
    val RU_SMALL = ModelPack(
        id = "ru-small",
        kind = ModelKind.ASR,
        label = "俄语·快速",
        description = "流式识别，约 28 MB，首字 20–160 ms",
        repo = "csukuangfj/sherpa-onnx-streaming-zipformer-small-ru-vosk-int8-2025-08-16",
        files = listOf(
            ModelFile("encoder.int8.onnx", 26_214_060L),
            ModelFile("decoder.onnx", 2_093_080L),
            ModelFile("joiner.int8.onnx", 259_417L),
            ModelFile("tokens.txt", 6_388L),
        ),
        license = "Apache-2.0",
        minRamGb = 4,
    )

    val ZH_EN = ModelPack(
        id = "zh-en",
        kind = ModelKind.ASR,
        label = "中英·快速",
        description = "中英双语流式识别，约 189 MB",
        repo = "csukuangfj/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20",
        files = listOf(
            ModelFile("encoder-epoch-99-avg-1.int8.onnx", 181_895_032L),
            ModelFile("decoder-epoch-99-avg-1.int8.onnx", 13_091_040L),
            ModelFile("joiner-epoch-99-avg-1.int8.onnx", 3_228_404L),
            ModelFile("tokens.txt", 56_317L),
        ),
        license = "Apache-2.0",
        minRamGb = 6,
    )

    val ZH_PARA = ModelPack(
        id = "zh-paraformer-int8",
        kind = ModelKind.ASR,
        label = "中文·高精度",
        description = "Paraformer 句末校正，int8 约 243 MB，推荐与 NLLB 一起下载",
        repo = "csukuangfj/sherpa-onnx-paraformer-zh-2023-09-14",
        files = listOf(
            ModelFile(
                name = "model.int8.onnx",
                bytes = 243_371_218L,
                sources = listOf(
                    "https://gh-proxy.com/https://github.com/120660021/sherpa-asr-models/releases/download/paraformer-zh-v1/model.int8.onnx",
                    "https://ghproxy.net/https://github.com/120660021/sherpa-asr-models/releases/download/paraformer-zh-v1/model.int8.onnx",
                    "https://github.com/120660021/sherpa-asr-models/releases/download/paraformer-zh-v1/model.int8.onnx",
                ),
            ),
            ModelFile(
                name = "tokens.txt",
                bytes = 75_756L,
                sources = listOf(
                    "https://hf-mirror.com/csukuangfj/sherpa-onnx-paraformer-zh-2023-09-14/resolve/main/tokens.txt",
                ),
            ),
        ),
        license = "Apache-2.0",
        minRamGb = 4,
    )

    val RU_WHISPER = ModelPack(
        id = "ru-whisper-small-int8",
        kind = ModelKind.ASR,
        label = "俄语·高精度（Whisper Small）",
        description = "俄语句末重识别，int8 约 358 MB，用于减少流式小模型误识别",
        repo = "csukuangfj/sherpa-onnx-whisper-small",
        files = listOf(
            ModelFile("small-encoder.int8.onnx", 112_442_483L),
            ModelFile("small-decoder.int8.onnx", 262_226_114L),
            ModelFile("small-tokens.txt", 816_730L),
        ),
        license = "MIT",
        minRamGb = 6,
    )

    val RU_GIGAAM = ModelPack(
        id = "ru-gigaam-v3-int8",
        kind = ModelKind.ASR,
        label = "俄语·高精度（GigaAM v3）",
        description = "俄语专用 CTC 模型，int8 约 304 MB，俄语识别优先推荐",
        repo = "Smirnov75/GigaAM-v3-sherpa-onnx",
        files = listOf(
            ModelFile("gigaam_v3_ctc_int8.onnx", 319_183_165L),
            ModelFile("gigaam_v3_ctc_tokens.txt", 195L),
        ),
        license = "MIT",
        minRamGb = 6,
    )

    /** Small Chinese-only streaming model (14M params) used by the default bundle. */
    val ZH_SMALL = ModelPack(
        id = "zh-small",
        kind = ModelKind.ASR,
        label = "中文·轻量",
        description = "中文流式识别，int8 约 24 MB，默认内置",
        repo = "csukuangfj/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23",
        files = listOf(
            ModelFile("encoder-epoch-99-avg-1.int8.onnx", 21_621_684L),
            ModelFile("decoder-epoch-99-avg-1.int8.onnx", 1_888_682L),
            ModelFile("joiner-epoch-99-avg-1.int8.onnx", 1_795_562L),
            ModelFile("tokens.txt", 48_697L),
        ),
        license = "Apache-2.0",
        minRamGb = 4,
    )

    /** Small English-only streaming model (20M params). */
    val EN_SMALL = ModelPack(
        id = "en-small",
        kind = ModelKind.ASR,
        label = "英语·轻量",
        description = "英语流式识别，int8 约 42 MB，默认内置",
        repo = "csukuangfj/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17",
        files = listOf(
            ModelFile("encoder-epoch-99-avg-1.int8.onnx", 42_845_182L),
            ModelFile("decoder-epoch-99-avg-1.int8.onnx", 539_499L),
            ModelFile("joiner-epoch-99-avg-1.int8.onnx", 259_572L),
            ModelFile("tokens.txt", 5_048L),
        ),
        license = "Apache-2.0",
        minRamGb = 4,
    )

    val GEMMA3_1B_LITERTLM = ModelPack(
        id = "gemma3-1b-it-litertlm-q4",
        kind = ModelKind.LLM,
        label = "Gemma 3 1B · GPU 实验",
        description = "约 584 MB；约 1 秒/句，但当前会复述原文，仅用于诊断",
        repo = "litert-community/Gemma3-1B-IT",
        files = listOf(
            ModelFile(
                name = "gemma3-1b-it-int4.litertlm",
                bytes = 584_417_280L,
                sources = listOf(
                    "https://modelscope.cn/models/litert-community/Gemma3-1B-IT/resolve/master/gemma3-1b-it-int4.litertlm",
                ),
            ),
        ),
        license = "Gemma Terms",
        minRamGb = 6,
    )

    val TRANSLATEGEMMA_4B = ModelPack(
        id = "translategemma-4b-it-int4",
        kind = ModelKind.LLM,
        label = "TranslateGemma 4B · Pro 精译",
        description = "55 语专用翻译，约 2.0 GB；CPU 运行，质量优先，手动选择",
        repo = "barakplasma/translategemma-4b-it-android-task-quantized",
        files = listOf(
            ModelFile(
                name = "translategemma-4b-it-int4-generic.litertlm",
                bytes = 2_011_201_536L,
                sources = listOf(
                    "https://hf-mirror.com/barakplasma/translategemma-4b-it-android-task-quantized/resolve/main/artifacts/int4-generic/translategemma-4b-it-int4-generic.litertlm",
                ),
            ),
        ),
        license = "Gemma Terms",
        minRamGb = 8,
    )

    val DEEPSEEK_R1_15B = ModelPack(
        id = "deepseek-r1-distill-1.5b-q4km",
        kind = ModelKind.LLM,
        label = "DeepSeek-R1 蒸馏 1.5B",
        description = "推理型蒸馏模型，Q4_K_M 约 1.0 GB；翻译需关闭思考",
        repo = "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
        files = listOf(
            ModelFile("DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf", 1_117_320_800L),
        ),
        license = "MIT",
        minRamGb = 8,
    )

    /** Dedicated 200-language translation model (ONNX encoder-decoder). */
    val NLLB_600M = ModelPack(
        id = "nllb-200-distilled-600m",
        kind = ModelKind.MT,
        label = "NLLB-200 600M · 中俄英专用",
        description = "专用翻译模型，约 1.35 GB；CPU 离线运行，首选中俄互译",
        repo = "Xenova/nllb-200-distilled-600M",
        files = listOf(
            ModelFile(
                name = "encoder_model_quantized.onnx",
                bytes = 419_120_483L,
                sources = listOf(
                    "https://modelscope.cn/models/Xenova/nllb-200-distilled-600M/resolve/master/onnx/encoder_model_quantized.onnx",
                ),
            ),
            ModelFile(
                name = "decoder_model_quantized.onnx",
                bytes = 470_533_055L,
                sources = listOf(
                    "https://modelscope.cn/models/Xenova/nllb-200-distilled-600M/resolve/master/onnx/decoder_model_quantized.onnx",
                ),
            ),
            ModelFile(
                name = "decoder_with_past_model_quantized.onnx",
                bytes = 445_006_987L,
                sources = listOf(
                    "https://modelscope.cn/models/Xenova/nllb-200-distilled-600M/resolve/master/onnx/decoder_with_past_model_quantized.onnx",
                ),
            ),
            ModelFile(
                name = "tokenizer.json",
                bytes = 17_331_224L,
                sources = listOf(
                    "https://modelscope.cn/models/Xenova/nllb-200-distilled-600M/resolve/master/tokenizer.json",
                ),
            ),
        ),
        license = "CC-BY-NC-4.0",
        minRamGb = 8,
    )

    /** Higher-quality NLLB export using the same cached decoder contract. */
    val NLLB_13B = ModelPack(
        id = "nllb-200-distilled-1.3b",
        kind = ModelKind.MT,
        label = "NLLB-200 1.3B · 高质量",
        description = "专用翻译模型，int8 约 2.82 GiB；质量优先，建议 12 GB 内存机型",
        repo = "fine444666/nllb-200-distilled-1.3B-onnx-int8",
        files = listOf(
            ModelFile("encoder_model_quantized.onnx", 772_180_723L),
            ModelFile("decoder_model_quantized.onnx", 1_136_625_871L),
            ModelFile("decoder_with_past_model_quantized.onnx", 1_085_911_453L),
            ModelFile("tokenizer.json", 32_240_136L),
        ),
        license = "CC-BY-NC-4.0",
        minRamGb = 12,
    )

    val SPEAKER_EMBEDDING = ModelPack(
        id = "speaker-embedding",
        kind = ModelKind.SPEAKER,
        label = "说话人声纹·CAM++ 增强",
        description = "3D-Speaker CAM++ 中英增强声纹，约 27 MB，区分不同发言人",
        repo = "csukuangfj/speaker-embedding-models",
        files = listOf(
            ModelFile("3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx", 28_281_164L),
        ),
        license = "Apache-2.0",
        minRamGb = 4,
    )

    val LID_TINY = ModelPack(
        id = "lid-tiny",
        kind = ModelKind.LID,
        label = "语种识别·快速",
        description = "Whisper tiny 音频级语种识别，约 98 MB，双通道自动判语种",
        repo = "csukuangfj/sherpa-onnx-whisper-tiny",
        files = listOf(
            ModelFile("tiny-encoder.int8.onnx", 12_937_772L),
            ModelFile("tiny-decoder.int8.onnx", 89_855_401L),
        ),
        license = "MIT",
        minRamGb = 4,
    )

    val LID_BASE = ModelPack(
        id = "lid-whisper-base",
        kind = ModelKind.LID,
        label = "语种识别·高精度",
        description = "Whisper base 音频级语种识别，int8 约 153 MB，优先用于中俄短句路由",
        repo = "csukuangfj/sherpa-onnx-whisper-base",
        files = listOf(
            ModelFile("base-encoder.int8.onnx", 29_120_534L),
            ModelFile("base-decoder.int8.onnx", 130_672_026L),
        ),
        license = "MIT",
        minRamGb = 4,
    )

    val all = listOf(
        RU_SMALL, ZH_SMALL, EN_SMALL, ZH_EN, ZH_PARA, RU_GIGAAM, RU_WHISPER,
        LID_TINY, LID_BASE, SPEAKER_EMBEDDING,
        NLLB_600M, NLLB_13B,
    )

    fun asr(language: String): ModelPack = when (language) {
        "ru" -> RU_SMALL
        "zh" -> ZH_SMALL
        "en" -> EN_SMALL
        else -> ZH_EN
    }

    fun byId(id: String): ModelPack? = all.firstOrNull { it.id == id }
}
