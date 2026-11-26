package com.mine.offlinetranslate

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mine.offlinetranslate.asr.StreamingAsr
import com.mine.offlinetranslate.asr.ParaformerAsr
import com.mine.offlinetranslate.asr.WhisperAsr
import com.mine.offlinetranslate.asr.GigaAmAsr
import com.mine.offlinetranslate.asr.SpeakerTagger
import com.mine.offlinetranslate.asr.LanguageId
import com.mine.offlinetranslate.audio.AudioCapture
import com.mine.offlinetranslate.audio.WavReader
import com.mine.offlinetranslate.core.ModelCatalog
import com.mine.offlinetranslate.core.ModelKind
import com.mine.offlinetranslate.core.ModelPack
import com.mine.offlinetranslate.core.ModelRepository
import com.mine.offlinetranslate.core.Languages
import com.mine.offlinetranslate.core.AsrMode
import com.mine.offlinetranslate.core.MtMode
import com.mine.offlinetranslate.core.ProviderConfig
import com.mine.offlinetranslate.core.ProviderStore
import com.mine.offlinetranslate.core.Gms
import com.mine.offlinetranslate.core.DiagLog
import com.mine.offlinetranslate.data.AppSettings
import com.mine.offlinetranslate.data.PhotoTask
import com.mine.offlinetranslate.data.PhotoTaskStore
import com.mine.offlinetranslate.data.RecordLine
import com.mine.offlinetranslate.data.RecordKind
import com.mine.offlinetranslate.data.RecordSession
import com.mine.offlinetranslate.data.RecordStore
import com.mine.offlinetranslate.mt.TranslationEngine
import com.mine.offlinetranslate.mt.TranslationEngines
import com.mine.offlinetranslate.mt.TranslationTextChunker
import com.mine.offlinetranslate.mt.NllbRuntime
import com.mine.offlinetranslate.mt.SummaryService
import com.mine.offlinetranslate.ocr.PhotoOcrEngine
import com.mine.offlinetranslate.ocr.PhotoOcrLanguage
import com.mine.offlinetranslate.ocr.decodePhoto
import com.mine.offlinetranslate.service.MicService
import com.mine.offlinetranslate.service.DownloadService
import com.mine.offlinetranslate.tts.TtsSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.io.File

data class TranscriptLine(
    val id: Long,
    val source: String,
    val translation: String? = null,
    val speaker: String? = null,
    val panel: String = "A",
    val srcLang: String = "",
    val tgtLang: String = "",
)

data class DownloadState(
    val progress: Float = 0f,
    val detail: String = "",
    val active: Boolean = false,
    val ready: Boolean = false,
)

data class LiveUiState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val modelReady: Boolean = false,
    val modelLoading: Boolean = false,
    val downloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadDetail: String = "",
    val sourceLang: String = "ru",
    val targetLang: String = "zh",
    val lines: List<TranscriptLine> = emptyList(),
    val partial: String = "",
    val partialTranslation: String = "",
    val partialPanel: String = "A",
    val transcriptOnly: Boolean = false,
    val hasGms: Boolean = true,
    val level: Float = 0f,
    val pendingTranslations: Int = 0,
    val error: String? = null,
    val providerConfig: ProviderConfig = ProviderConfig(),
    val downloads: Map<String, DownloadState> = emptyMap(),
    val testMode: Boolean = false,
    val records: List<RecordSession> = emptyList(),
    val folders: List<String> = emptyList(),
    val ttsEnabled: Boolean = false,
    val showSource: Boolean = true,
    val fontSize: Int = 16,
    val totalSeconds: Long = 0,
    val sessionCount: Int = 0,
    val aiKeywords: String = "",
    val aiContext: String = "",
    val mode: String = "single",
    val summary: String? = null,
    val summarizing: Boolean = false,
    val speakerDiarization: Boolean = false,
    val textMode: String = "mixed",
    val panelMode: String = "single",
    val panelALang: String = "ru",
    val panelBLang: String = "zh",
    val ttsVolume: Float = 1f,
    val ttsVoice: String = "",
    val ttsVoices: List<String> = emptyList(),
    val knowledgeBaseEnabled: Boolean = false,
    val aiKeywordLearning: Boolean = true,
    val aiAutoKeywords: String = "",
    val aiPersonalization: String = "",
    val photoTask: PhotoTask = PhotoTask(),
)

class LiveViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ModelRepository(application)
    private val providerStore = ProviderStore(application)
    private var providerConfig = providerStore.load()
    private var translator: TranslationEngine =
        TranslationEngines.create(providerConfig, application, null)
    private val recordStore = RecordStore(application)
    private val photoTaskStore = PhotoTaskStore(application)
    private val settings = AppSettings(application)
    private val speaker = TtsSpeaker(application)
    private val summaryService = SummaryService()
    private val capture = AudioCapture()
    private val photoOcr = PhotoOcrEngine(application)
    private val asrLock = Any()
    private val loadLock = Any()

    private var asrA: StreamingAsr? = null
    private var asrB: StreamingAsr? = null
    private var asrGeneration = 0

    private val lidLock = Any()
    private var languageId: LanguageId? = null
    private var lastLidLang: String? = null
    @Volatile
    private var preparingModels = false
    private val lidSamples = ArrayList<Float>()
    private val maxLidSamples = 16_000 * 12

    /** ASR pack for panel A. In single mode it follows the global source language. */
    private val pack: ModelPack
        get() = streamingPackFor(asrLangFor("A"))

    private fun streamingPackFor(language: String): ModelPack {
        val selected = ModelCatalog.byId(providerConfig.asrPackId)
        val streaming = selected?.takeIf {
            it.id == ModelCatalog.RU_SMALL.id ||
                it.id == ModelCatalog.ZH_SMALL.id ||
                it.id == ModelCatalog.EN_SMALL.id ||
                it.id == ModelCatalog.ZH_EN.id
        }
        return streaming?.takeIf { packSupportsLang(it, language) }
            ?: ModelCatalog.asr(language)
    }

    private fun asrLangFor(panel: String): String {
        val s = _state.value
        return if (s.panelMode == "dual") {
            if (panel == "A") s.panelALang else s.panelBLang
        } else {
            s.sourceLang
        }
    }

    /** Source/target pair a transcript panel translates between. */
    private fun pairFor(panel: String): Pair<String, String> {
        val s = _state.value
        return if (s.panelMode == "dual") {
            if (panel == "A") s.panelALang to s.panelBLang else s.panelBLang to s.panelALang
        } else {
            s.sourceLang to s.targetLang
        }
    }
    private var sequence = 0L
    private var partialJob: Job? = null
    private val translationJobs = java.util.concurrent.ConcurrentHashMap.newKeySet<Job>()
    private val downloadJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private var sessionId: String? = null
    private var sessionStartedAt = 0L
    private var photoJob: Job? = null
    private var idleReleaseJob: Job? = null
    private var speakerTagger: SpeakerTagger? = null
    private var zhHighAsr: ParaformerAsr? = null
    private val zhHighAsrLock = Any()
    private var ruHighAsr: WhisperAsr? = null
    private val ruHighAsrLock = Any()
    private var ruGigaAsr: GigaAmAsr? = null
    @Volatile
    private var utteranceLang: String? = null
    private val lidVoteLock = Any()
    private val lidVotes = ArrayList<String>()
    private var lidProbeInFlight = false
    private var nextLidProbeSamples = LID_PROBE_FIRST_SAMPLES
    private val lineSamples = ArrayList<Float>()
    private val maxLineSamples = 16_000 * 30
    private data class PendingUtterance(
        val text: String,
        val audio: FloatArray,
        val panel: String,
        val source: String,
        val target: String,
    )
    private val utteranceQueue = Channel<PendingUtterance>(Channel.UNLIMITED)

    private val _state = MutableStateFlow(LiveUiState(providerConfig = providerConfig))
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    init {
        DiagLog.init(application)
        DiagLog.log("app start mt=${providerConfig.mt} llm=${providerConfig.llmPackId}")
        val legacyFreed = repository.deleteLegacyQwenModels()
        if (legacyFreed > 0) {
            DiagLog.log("removed legacy Qwen models ${legacyFreed / (1024 * 1024)}MB")
        }
        speaker.init()
        speaker.setVolume(settings.ttsVolume)
        speaker.setVoice(settings.ttsVoice)
        val restoredPhotoTask = photoTaskStore.load()?.let { task ->
            if (task.busy) {
                task.copy(busy = false, status = "上次任务已中断，可重新识别")
                    .also(photoTaskStore::save)
            } else {
                task
            }
        } ?: PhotoTask()
        _state.update { current ->
            current.copy(
                sourceLang = when (providerConfig.languageRoute) {
                    "zh" -> "zh"
                    "ru" -> "ru"
                    else -> settings.sourceLang
                },
                targetLang = when (providerConfig.languageRoute) {
                    "zh" -> "ru"
                    "ru" -> "zh"
                    else -> settings.targetLang
                },
                ttsEnabled = settings.ttsEnabled,
                showSource = settings.showSource,
                fontSize = settings.fontSize,
                totalSeconds = settings.totalSeconds,
                sessionCount = settings.sessionCount,
                aiKeywords = settings.aiKeywords,
                aiContext = settings.aiContext,
                speakerDiarization = settings.speakerDiarization,
                transcriptOnly = settings.transcriptOnly,
                hasGms = Gms.available(application),
                textMode = settings.textMode,
                // V2: one timeline, but two recognisers stay loaded for bilingual auto-routing.
                panelMode = if (providerConfig.languageRoute == "auto") "dual" else "single",
                panelALang = settings.panelALang,
                panelBLang = settings.panelBLang,
                ttsVolume = settings.ttsVolume,
                ttsVoice = settings.ttsVoice,
                knowledgeBaseEnabled = settings.knowledgeBaseEnabled,
                aiKeywordLearning = settings.aiKeywordLearning,
                aiAutoKeywords = settings.aiAutoKeywords,
                aiPersonalization = settings.aiPersonalization,
                photoTask = restoredPhotoTask,
                records = recordStore.list(),
                folders = recordStore.folders(),
                downloads = ModelCatalog.all.associate { model ->
                    model.id to DownloadState(ready = repository.isReady(model))
                }
            )
        }
        runCatching { translator.close() }
        translator = newTranslator()
        viewModelScope.launch {
            installBundledModels()
            refreshModel()
        }
        viewModelScope.launch {
            for (pending in utteranceQueue) {
                val refined = withContext(Dispatchers.Default) {
                    refineTranscript(pending.text, pending.source, pending.audio)
                }
                emitUtterance(
                    refined,
                    pending.audio,
                    pending.panel,
                    pending.source,
                    pending.target,
                )
            }
        }
        viewModelScope.launch {
            var attempts = 0
            while (attempts < 25 && speaker.availableVoices().isEmpty()) {
                delay(200)
                attempts++
            }
            _state.update { it.copy(ttsVoices = speaker.availableVoices()) }
        }
    }

    fun refreshModel() {
        val activePack = streamingPackFor(_state.value.sourceLang)
        providerStore.save(providerConfig)
        val ready = repository.isReady(activePack)
        _state.update { it.copy(modelReady = ready, providerConfig = providerConfig) }
        if (ready) {
            viewModelScope.launch(Dispatchers.IO) { ensureAsrLoaded() }
        }
    }

    /** First launch on a fresh phone: copy the models shipped inside the APK. */
    private suspend fun installBundledModels() {
        preparingModels = true
        val packs = listOf(
            ModelCatalog.RU_SMALL,
            ModelCatalog.ZH_SMALL,
            ModelCatalog.EN_SMALL,
            ModelCatalog.LID_TINY,
        )
        try {
            for (pack in packs) {
            if (!repository.hasBundled(pack) || repository.isReady(pack)) continue
            _state.update { current ->
                current.copy(
                    downloads = current.downloads + (
                        pack.id to DownloadState(active = true, detail = "正在安装内置模型")
                        ),
                )
            }
            val result = repository.ensureBundled(pack) { progress, detail ->
                _state.update { current ->
                    current.copy(
                        downloads = current.downloads + (
                            pack.id to DownloadState(
                                active = true,
                                progress = progress,
                                detail = detail,
                                ready = false,
                            )
                            ),
                    )
                }
            }
            _state.update { current ->
                current.copy(
                    downloads = current.downloads + (
                        pack.id to DownloadState(
                            active = false,
                            progress = if (result.isSuccess) 1f else 0f,
                            detail = if (result.isSuccess) "内置模型已安装" else "内置模型安装失败",
                            ready = result.isSuccess,
                        )
                        ),
                )
            }
            }
        } finally {
            preparingModels = false
        }
    }

    fun updateProviderConfig(config: ProviderConfig) {
        if (_state.value.running) stop()
        cancelTranslations()
        val previous = providerConfig
        val next = withAi(config)
        providerConfig = next
        providerStore.save(providerConfig)
        val cloudChanged = next.mt != com.mine.offlinetranslate.core.MtMode.LOCAL_LLM &&
            (
                previous.cloud.baseUrl != next.cloud.baseUrl ||
                    previous.cloud.apiKey != next.cloud.apiKey ||
                    previous.cloud.chatModel != next.cloud.chatModel
                )
        val engineChanged =
            previous.mt != next.mt ||
                previous.llmPackId != next.llmPackId ||
                cloudChanged
        DiagLog.log(
            "provider update changed=$engineChanged old=${previous.mt}/${previous.llmPackId} " +
                "new=${next.mt}/${next.llmPackId}",
        )
        if (engineChanged) {
            runCatching { translator.close() }
            translator = newTranslator()
        }
        if (previous.lidPackId != next.lidPackId) {
            synchronized(lidLock) {
                runCatching { languageId?.release() }
                languageId = null
            }
        }
        if (previous.zhSentenceRefine != next.zhSentenceRefine) {
            if (!next.zhSentenceRefine) {
                synchronized(zhHighAsrLock) {
                    zhHighAsr?.release()
                    zhHighAsr = null
                }
            }
        }
        if (
            previous.ruSentenceRefine != next.ruSentenceRefine ||
            previous.ruRefinerId != next.ruRefinerId
        ) {
            synchronized(ruHighAsrLock) {
                ruHighAsr?.release()
                ruHighAsr = null
                ruGigaAsr?.release()
                ruGigaAsr = null
            }
        }
        if (previous.languageRoute != next.languageRoute) {
            applyLanguageRoute(next.languageRoute)
        }
        Log.i("LiveViewModel", "provider mt=${config.mt} asr=${config.asr} base=${config.cloud.baseUrl}")
        _state.update {
            it.copy(
                providerConfig = providerConfig,
                error = null,
                partial = "",
                partialTranslation = "",
            )
        }
    }

    fun updateCloudSettings(cloud: com.mine.offlinetranslate.core.CloudConfig) {
        providerConfig = providerConfig.copy(cloud = cloud)
        providerStore.save(providerConfig)
        _state.update { it.copy(providerConfig = providerConfig) }
    }

    fun downloadModel() {
        downloadPack(pack)
    }

    fun downloadRecommendedModels() {
        downloadPack(ModelCatalog.NLLB_600M)
        downloadPack(ModelCatalog.ZH_PARA)
        downloadPack(ModelCatalog.RU_GIGAAM)
        downloadPack(ModelCatalog.LID_BASE)
        downloadPack(ModelCatalog.SPEAKER_EMBEDDING)
    }

    fun downloadPack(model: ModelPack) {
        if (downloadJobs.containsKey(model.id)) return
        DownloadService.start(getApplication())
        val job = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
            _state.update { current ->
                current.copy(
                    downloads = current.downloads + (
                        model.id to DownloadState(active = true, detail = "准备下载 ${model.label}")
                        ),
                )
            }
            val result = repository.download(model) { progress, detail ->
                _state.update { current ->
                    current.copy(
                        downloads = current.downloads + (
                            model.id to DownloadState(
                                active = true,
                                progress = progress,
                                detail = detail,
                            )
                            ),
                    )
                }
            }
            result.onSuccess {
                _state.update { current ->
                    current.copy(
                        modelReady = if (model.id == pack.id) true else current.modelReady,
                        downloads = current.downloads + (
                            model.id to DownloadState(ready = true, progress = 1f, detail = "已下载")
                            ),
                    )
                }
                when (model.id) {
                    ModelCatalog.LID_BASE.id -> {
                        synchronized(lidLock) {
                            runCatching { languageId?.release() }
                            languageId = null
                        }
                    }
                }
                if (model.kind == ModelKind.ASR && model.id == pack.id) {
                    withContext(Dispatchers.IO) { ensureAsrLoaded() }
                } else {
                    refreshTranslatorIfSelected(model)
                }
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        downloads = current.downloads + (
                            model.id to DownloadState(detail = "下载失败，可重试（续传）")
                            ),
                        error = "模型下载失败：${error.message}",
                    )
                }
            }
            } finally {
                downloadJobs.remove(model.id)
                if (downloadJobs.isEmpty()) {
                    runCatching { DownloadService.stop(getApplication()) }
                }
            }
        }
        downloadJobs[model.id] = job
        job.start()
    }

    /** Imports a model the user already downloaded on another device or computer. */
    fun importPack(model: ModelPack, uri: Uri) {
        if (downloadJobs.containsKey(model.id)) return
        DownloadService.start(getApplication())
        val job = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                _state.update { current ->
                    current.copy(
                        downloads = current.downloads + (
                            model.id to DownloadState(active = true, detail = "正在导入 ${model.label}")
                            ),
                    )
                }
                val imported = withContext(Dispatchers.IO) { repository.importFile(model, uri) }
                if (imported == null) {
                    _state.update { current ->
                        current.copy(
                            downloads = current.downloads + (
                                model.id to DownloadState(detail = "导入失败")
                                ),
                            error = "导入失败：文件名或大小与 ${model.label} 不匹配",
                        )
                    }
                } else {
                    _state.update { current ->
                        current.copy(
                            downloads = current.downloads + (
                                model.id to DownloadState(
                                    progress = 1f,
                                    detail = "已导入 $imported",
                                    ready = true,
                                )
                                ),
                        )
                    }
                    when (model.id) {
                        ModelCatalog.LID_BASE.id -> {
                            synchronized(lidLock) {
                                runCatching { languageId?.release() }
                                languageId = null
                            }
                        }
                    }
                    refreshTranslatorIfSelected(model)
                }
            } catch (error: Exception) {
                _state.update { current ->
                    current.copy(
                        downloads = current.downloads + (
                            model.id to DownloadState(detail = "导入失败")
                            ),
                        error = "模型导入失败：${error.message}",
                    )
                }
            } finally {
                downloadJobs.remove(model.id)
                if (downloadJobs.isEmpty()) {
                    runCatching { DownloadService.stop(getApplication()) }
                }
            }
        }
        downloadJobs[model.id] = job
        job.start()
    }

    /** Cancels an in-flight download; the .part file is kept for later resume. */
    fun cancelDownload(model: ModelPack) {
        downloadJobs.remove(model.id)?.cancel()
        _state.update { current ->
            current.copy(
                downloads = current.downloads + (
                    model.id to DownloadState(detail = "已取消，可继续下载")
                    ),
            )
        }
        if (downloadJobs.isEmpty()) {
            runCatching { DownloadService.stop(getApplication()) }
        }
    }

    fun deletePack(model: ModelPack) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(model)
            _state.update { current ->
                current.copy(
                    downloads = current.downloads + (model.id to DownloadState()),
                    modelReady = if (model.id == pack.id) false else current.modelReady,
                )
            }
        }
    }

    fun start() {
        if (_state.value.running || _state.value.modelLoading) return
        cancelIdleRelease()
        if (!Languages.asrSupported(_state.value.sourceLang)) {
            _state.update { it.copy(error = "该语言暂无本地识别模型，请使用文本翻译或切换源语言") }
            return
        }
        if (providerConfig.asr == AsrMode.CLOUD) {
            _state.update { it.copy(error = "云端识别通道将在 M2 接入；当前请使用本地模型") }
            return
        }
        viewModelScope.launch {
            if (!repository.isReady(pack)) {
                _state.update {
                    it.copy(
                        error = if (preparingModels) {
                            "正在准备离线模型，请稍候…"
                        } else {
                            "请先下载 ${pack.label} 模型"
                        },
                    )
                }
                return@launch
            }
            _state.update { it.copy(modelLoading = true, error = null) }
            try {
                val (loaded, bIssue) = withContext(Dispatchers.IO) {
                    var started = System.currentTimeMillis()
                    DiagLog.log("ModelLoad ASR-A start")
                    val okA = ensureAsrLoaded()
                    DiagLog.log("ModelLoad ASR-A done ok=$okA ${System.currentTimeMillis() - started}ms")
                    var issue: String? = null
                    if (okA && _state.value.panelMode == "dual") {
                        started = System.currentTimeMillis()
                        DiagLog.log("ModelLoad ASR-B start")
                        issue = ensureAsrBLoaded()
                        DiagLog.log("ModelLoad ASR-B done issue=$issue ${System.currentTimeMillis() - started}ms")
                        started = System.currentTimeMillis()
                        DiagLog.log("ModelLoad LID start")
                        ensureLanguageIdLoaded()
                        DiagLog.log("ModelLoad LID done ${System.currentTimeMillis() - started}ms")
                    }
                    DiagLog.memory(getApplication(), "asr-loaded")
                    okA to issue
                }
                if (!loaded) {
                    _state.update { it.copy(error = it.error ?: "语音模型加载失败") }
                    return@launch
                }
                if (!withContext(Dispatchers.IO) { ensureSpeakerTagger() }) {
                    return@launch
                }
                // The offline LLM loads lazily on the first translated sentence,
                // so starting the microphone never blocks on a 1 GB model.
                startCapture()
                bIssue?.let { message ->
                    _state.update { it.copy(error = "B 面板：$message") }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                DiagLog.log("ModelLoad start failed: ${t.stackTraceToString()}")
                _state.update { it.copy(error = "启动失败：${t.message ?: t.javaClass.simpleName}") }
            } finally {
                _state.update { it.copy(modelLoading = false) }
            }
        }
    }

    /** Deterministic end-to-end path used by the emulator/CI test. */
    fun startWithAssetWav(assetName: String) {
        if (_state.value.running || _state.value.modelLoading) return
        cancelIdleRelease()
        if (!Languages.asrSupported(_state.value.sourceLang)) {
            _state.update { it.copy(error = "该语言暂无本地识别模型") }
            return
        }
        viewModelScope.launch {
            if (!repository.isReady(pack)) {
                _state.update { it.copy(error = "请先下载 ${pack.label} 模型") }
                return@launch
            }
            _state.update { it.copy(modelLoading = true, error = null) }
            var bIssue: String? = null
            val loaded = withContext(Dispatchers.IO) {
                val okA = ensureAsrLoaded()
                if (okA && _state.value.panelMode == "dual") {
                    bIssue = ensureAsrBLoaded()
                    ensureLanguageIdLoaded()
                }
                okA
            }
            _state.update { it.copy(modelLoading = false) }
            if (!loaded) {
                _state.update { it.copy(error = it.error ?: "语音模型加载失败") }
                return@launch
            }
            if (!withContext(Dispatchers.IO) { ensureSpeakerTagger() }) {
                return@launch
            }
            val wav = withContext(Dispatchers.IO) {
                WavReader.readAsset(getApplication<Application>().assets, assetName)
            }
            if (wav == null) {
                _state.update { it.copy(error = "测试音频读取失败") }
                return@launch
            }
            synchronized(asrLock) {
                asrA?.reset()
                asrB?.reset()
            }
            resetUtteranceRouting()
            sessionId = UUID.randomUUID().toString()
            sessionStartedAt = System.currentTimeMillis()
            sequence = 0L
            _state.update {
                it.copy(
                    running = true,
                    testMode = true,
                    lines = emptyList(),
                    partial = "",
                    partialTranslation = "",
                    partialPanel = "A",
                    error = null,
                )
            }
            var offset = 0
            while (offset < wav.samples.size && _state.value.running) {
                val end = minOf(offset + 1600, wav.samples.size)
                handleChunk(wav.samples.copyOfRange(offset, end), 0.35f)
                offset = end
                delay(100)
            }
            if (_state.value.running) stop()
            bIssue?.let { message ->
                _state.update { it.copy(error = "B 面板：$message") }
            }
        }
    }

    private fun startCapture() {
        startCaptureInternal(newSession = true)
    }

    private fun startCaptureInternal(newSession: Boolean) {
        val context = getApplication<Application>()
        if (newSession) {
            sessionId = UUID.randomUUID().toString()
            sessionStartedAt = System.currentTimeMillis()
            sequence = 0L
            speakerTagger?.reset()
            synchronized(lineSamples) { lineSamples.clear() }
        }
        MicService.start(context)
        val ok = capture.start(object : AudioCapture.Listener {
            override fun onChunk(samples: FloatArray, level: Float) {
                handleChunk(samples, level)
            }

            override fun onError(message: String) {
                _state.update { it.copy(error = message, running = false, paused = false) }
                MicService.stop(context)
            }
        })
        if (!ok) {
            MicService.stop(context)
            return
        }
        synchronized(asrLock) {
            asrA?.reset()
            asrB?.reset()
        }
        resetUtteranceRouting()
        _state.update {
            it.copy(
                running = true,
                paused = false,
                testMode = false,
                lines = if (newSession) emptyList() else it.lines,
                partial = "",
                partialTranslation = "",
                partialPanel = "A",
                error = null,
            )
        }
    }

    fun pause() {
        if (!_state.value.running) return
        capture.stop()
        val (finalA, finalB) = finishEngines()
        finalizeBest(finalA, finalB, drainLineSamples(), drainLidSamples())
        resetUtteranceRouting()
        clearPartial()
        _state.update { it.copy(running = false, paused = true, level = 0f) }
        speaker.stop()
        MicService.stop(getApplication())
    }

    fun resume() {
        if (!_state.value.paused) return
        viewModelScope.launch {
            if (withContext(Dispatchers.IO) { ensureSpeakerTagger() }) {
                startCaptureInternal(newSession = false)
            }
        }
    }

    private fun handleChunk(samples: FloatArray, level: Float) {
        val dual = _state.value.panelMode == "dual"
        val textA: String
        val textB: String
        val endpoint: Boolean
        synchronized(asrLock) {
            val engineA = asrA ?: return
            textA = engineA.accept(samples)
            val engineB = if (dual) asrB else null
            textB = when {
                !dual -> ""
                engineB != null && engineB !== engineA -> engineB.accept(samples)
                sameAsrPack() -> textA
                else -> ""
            }
            endpoint = when {
                engineB == null || engineB === engineA -> engineA.isEndpoint()
                else -> {
                    // Only the engine that actually owns this sentence may end it;
                    // the other one frequently hallucinates short noise segments.
                    val routed = utteranceLang?.let { panelForLang(it, textA, textB) }
                        ?: pickPanel(textA, textB)
                    if (routed == "B") engineB.isEndpoint() else engineA.isEndpoint()
                }
            }
        }
        _state.update { it.copy(level = level) }
        synchronized(lineSamples) {
            val room = maxLineSamples - lineSamples.size
            if (room > 0) {
                val count = minOf(room, samples.size)
                for (i in 0 until count) lineSamples.add(samples[i])
            }
        }
        var lidProbe: FloatArray? = null
        if (dual) {
            synchronized(lidSamples) {
                val room = maxLidSamples - lidSamples.size
                if (room > 0) {
                    val count = minOf(room, samples.size)
                    for (i in 0 until count) lidSamples.add(samples[i])
                }
            }
            val lidCount = synchronized(lidSamples) { lidSamples.size }
            synchronized(lidVoteLock) {
                if (
                    utteranceLang == null &&
                    !lidProbeInFlight &&
                    lidCount >= nextLidProbeSamples &&
                    nextLidProbeSamples <= LID_PROBE_LAST_SAMPLES
                ) {
                    lidProbeInFlight = true
                    lidProbe = synchronized(lidSamples) { lidSamples.toFloatArray() }
                }
            }
        }
        lidProbe?.let { probe ->
            viewModelScope.launch(Dispatchers.Default) {
                val detected = detectLanguage(probe)
                synchronized(lidVoteLock) {
                    lidProbeInFlight = false
                    if (detected != null && utteranceLang == null) {
                        lidVotes += detected
                        DiagLog.log(
                            "lid vote=${lidVotes.joinToString(",")} " +
                                "samples=${probe.size}",
                        )
                        if (
                            lidVotes.size >= 2 &&
                            lidVotes.takeLast(2).distinct().size == 1
                        ) {
                            utteranceLang = detected
                            DiagLog.log("lid confirmed=$detected")
                        }
                    }
                    if (utteranceLang == null) {
                        nextLidProbeSamples = minOf(
                            LID_PROBE_LAST_SAMPLES + 1,
                            nextLidProbeSamples + LID_PROBE_STEP_SAMPLES,
                        )
                    }
                }
            }
        }

        if (endpoint) {
            if (dual) {
                val evA = languageEvidence(textA, _state.value.panelALang)
                val evB = languageEvidence(textB, _state.value.panelBLang)
                Log.d(
                    "DualAsr",
                    "A=[${textA.trim()}](${"%.2f".format(evA)}) " +
                        "B=[${textB.trim()}](${"%.2f".format(evB)})",
                )
            }
            synchronized(asrLock) {
                asrA?.reset()
                asrB?.reset()
            }
            finalizeBest(textA, textB, drainLineSamples(), drainLidSamples())
            resetUtteranceRouting()
            return
        }

        val panel = if (dual) {
            utteranceLang?.let { panelForLang(it, textA, textB) }
                ?: run {
                    if (_state.value.partial.isNotEmpty()) clearPartial()
                    return
                }
        } else {
            "A"
        }
        val shown = if (panel == "B") textB else textA
        val text = shown.trim()
        if (text.isEmpty()) return
        if (
            dual &&
            utteranceLang != null &&
            !textMatchesLanguage(text, utteranceLang!!)
        ) {
            if (_state.value.partial.isNotEmpty()) clearPartial()
            return
        }
        if (text.length >= MAX_LIVE_TEXT_CHARS) {
            synchronized(asrLock) {
                asrA?.reset()
                asrB?.reset()
            }
            finalizeBest(textA, textB, drainLineSamples(), drainLidSamples())
            resetUtteranceRouting()
            return
        }
        if (text.isNotEmpty() && (text != _state.value.partial || panel != _state.value.partialPanel)) {
            _state.update {
                it.copy(
                    partial = text,
                    partialPanel = panel,
                    partialTranslation = if (it.partialPanel == panel) it.partialTranslation else "",
                )
            }
            schedulePartialTranslation(text, panel)
        }
    }

    /** Finalizes the routed sentence once so the same phrase never lands in both columns. */
    private fun finalizeBest(
        textA: String,
        textB: String,
        audio: FloatArray,
        lidAudio: FloatArray = FloatArray(0),
    ) {
        val dual = _state.value.panelMode == "dual"
        val lidLang = if (dual) {
            lidAudio.takeIf { it.isNotEmpty() }?.let(::detectLanguage) ?: utteranceLang
        } else {
            null
        }
        if (
            dual &&
            utteranceLang != null &&
            lidLang != null &&
            utteranceLang != lidLang
        ) {
            DiagLog.log("lid final override old=$utteranceLang new=$lidLang")
            clearPartial()
        }
        if (lidLang != null) lastLidLang = lidLang
        var panel = when {
            !dual -> "A"
            lidLang != null -> panelForLang(lidLang, textA, textB)
            else -> pickPanel(textA, textB)
        }
        var text = if (panel == "B") textB else textA
        // Short tails without a fresh LID decision are the main source of
        // cross-language garbage; drop them when they disagree with the language
        // we just confirmed for this conversation.
        if (dual && lidLang == null && text.isNotBlank() && text.length < 12) {
            val dominant = lastLidLang?.let { panelForLang(it, textA, textB) }
            if (dominant != null && dominant != panel) {
                clearPartial()
                return
            }
        }
        if (text.isBlank()) {
            val other = if (panel == "A") "B" else "A"
            val otherText = if (other == "B") textB else textA
            if (otherText.isNotBlank()) {
                panel = other
                text = otherText
            }
        }
        if (text.isBlank()) {
            clearPartial()
            return
        }
        if (dual) {
            Log.d("DualAsr", "route=$panel lid=${lidLang ?: "-"} text=${text.take(40)}")
        }
        val (source, target) = pairFor(panel)
        enqueueUtteranceSegments(text, audio, panel, source, target)
        clearPartial()
    }

    private fun enqueueUtteranceSegments(
        text: String,
        audio: FloatArray,
        panel: String,
        source: String,
        target: String,
    ) {
        val segments = TranslationTextChunker.split(text, MAX_LIVE_TEXT_CHARS)
        if (segments.isEmpty()) return
        segments.forEachIndexed { index, segment ->
            val pending = PendingUtterance(
                text = segment,
                audio = if (index == 0) audio else FloatArray(0),
                panel = panel,
                source = source,
                target = target,
            )
            if (!utteranceQueue.trySend(pending).isSuccess) {
                emitUtterance(segment, pending.audio, panel, source, target)
            }
        }
    }

    private fun emitUtterance(
        text: String,
        audio: FloatArray,
        panel: String,
        source: String,
        target: String,
    ) {
        val id = finalizeLine(text, null, panel, source, target)
        if (audio.isNotEmpty()) {
            val tagger = speakerTagger
            viewModelScope.launch(Dispatchers.Default) {
                val label = tagger?.identify(audio) ?: return@launch
                updateLineSpeaker(id, label)
            }
        }
    }

    private fun drainLineSamples(): FloatArray {
        synchronized(lineSamples) {
            val copy = lineSamples.toFloatArray()
            lineSamples.clear()
            return copy
        }
    }

    private fun drainLidSamples(): FloatArray {
        if (_state.value.panelMode != "dual") {
            synchronized(lidSamples) { lidSamples.clear() }
            return FloatArray(0)
        }
        synchronized(lidSamples) {
            val copy = lidSamples.toFloatArray()
            lidSamples.clear()
            return copy
        }
    }

    private fun resetUtteranceRouting() {
        utteranceLang = null
        synchronized(lidVoteLock) {
            lidVotes.clear()
            lidProbeInFlight = false
            nextLidProbeSamples = LID_PROBE_FIRST_SAMPLES
        }
    }

    private fun detectLanguage(samples: FloatArray): String? {
        val engine = synchronized(lidLock) { languageId }
        if (engine?.isLoaded != true) return null
        return runCatching { engine.detect(samples) }.getOrNull()
    }

    /** Routes an utterance by the language actually heard. */
    private fun panelForLang(lang: String, textA: String, textB: String): String {
        val s = _state.value
        if (lang == s.panelALang) return "A"
        if (lang == s.panelBLang) return "B"
        val packA = streamingPackFor(s.panelALang)
        val packB = streamingPackFor(s.panelBLang)
        val supportsA = packSupportsLang(packA, lang)
        val supportsB = packSupportsLang(packB, lang)
        if (supportsA && !supportsB) return "A"
        if (supportsB && !supportsA) return "B"
        if (sameScriptFamily(lang, s.panelALang) && !sameScriptFamily(lang, s.panelBLang)) return "A"
        if (sameScriptFamily(lang, s.panelBLang) && !sameScriptFamily(lang, s.panelALang)) return "B"
        return pickPanel(textA, textB)
    }

    private fun packSupportsLang(pack: ModelPack, lang: String): Boolean = when (pack.id) {
        ModelCatalog.RU_SMALL.id -> lang == "ru"
        ModelCatalog.ZH_SMALL.id -> lang == "zh" || lang == "yue"
        ModelCatalog.EN_SMALL.id -> lang == "en"
        ModelCatalog.ZH_EN.id -> lang == "zh" || lang == "en" || lang == "yue"
        ModelCatalog.ZH_PARA.id -> lang == "zh" || lang == "yue"
        else -> false
    }

    private fun refineTranscript(
        fallback: String,
        source: String,
        audio: FloatArray,
    ): String {
        if (audio.isEmpty()) return fallback
        return when (source) {
            "zh" -> {
                if (!providerConfig.zhSentenceRefine) return fallback
                if (!repository.isReady(ModelCatalog.ZH_PARA)) return fallback
                val engine = synchronized(zhHighAsrLock) {
                    zhHighAsr ?: ParaformerAsr(repository.dir(ModelCatalog.ZH_PARA)).also {
                        if (it.load().isSuccess) {
                            zhHighAsr = it
                            DiagLog.log("zh paraformer ready")
                            it
                        } else {
                            return fallback
                        }
                    }
                }
                runCatching { engine.transcribe(audio) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: fallback
            }
            "ru" -> {
                if (!providerConfig.ruSentenceRefine) return fallback
                if (providerConfig.ruRefinerId == ModelCatalog.RU_GIGAAM.id) {
                    if (!repository.isReady(ModelCatalog.RU_GIGAAM)) return fallback
                    val engine = synchronized(ruHighAsrLock) {
                        ruGigaAsr ?: GigaAmAsr(repository.dir(ModelCatalog.RU_GIGAAM)).also {
                            if (it.load().isSuccess) {
                                ruGigaAsr = it
                                DiagLog.log("ru gigaam ready")
                                it
                            } else {
                                return fallback
                            }
                        }
                    }
                    return runCatching { engine.transcribe(audio) }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: fallback
                }
                if (!repository.isReady(ModelCatalog.RU_WHISPER)) return fallback
                val engine = synchronized(ruHighAsrLock) {
                    ruHighAsr ?: WhisperAsr(
                        repository.dir(ModelCatalog.RU_WHISPER),
                        "ru",
                    ).also {
                        if (it.load().isSuccess) {
                            ruHighAsr = it
                            DiagLog.log("ru whisper ready")
                            it
                        } else {
                            return fallback
                        }
                    }
                }
                runCatching { engine.transcribe(audio) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: fallback
            }
            else -> fallback
        }
    }

    private fun sameScriptFamily(a: String, b: String): Boolean =
        expectedScript(a) == expectedScript(b)

    /** Flushes both engines and returns their final texts; the second reuses the first when shared. */
    private fun finishEngines(): Pair<String, String> {
        val dual = _state.value.panelMode == "dual"
        synchronized(asrLock) {
            val a = asrA?.finish()?.trim() ?: ""
            val b = if (dual) {
                val engineB = asrB
                when {
                    engineB != null && engineB !== asrA -> engineB.finish().trim()
                    sameAsrPack() -> a
                    else -> ""
                }
            } else {
                ""
            }
            asrA?.reset()
            asrB?.reset()
            return a to b
        }
    }

    private fun scriptOf(ch: Char): String? = when {
        ch in '\u0400'..'\u04FF' -> SCRIPT_CYRILLIC
        ch in '\u4E00'..'\u9FFF' || ch in '\u3400'..'\u4DBF' ||
            ch in '\u3040'..'\u30FF' || ch in '\uAC00'..'\uD7AF' -> SCRIPT_CJK
        ch in 'A'..'Z' || ch in 'a'..'z' || ch in '\u00C0'..'\u024F' -> SCRIPT_LATIN
        else -> null
    }

    private fun expectedScript(lang: String): String = when (lang) {
        "ru", "uk", "bg", "sr", "mk", "be", "kk", "mn" -> SCRIPT_CYRILLIC
        "zh", "yue", "ja", "ko" -> SCRIPT_CJK
        else -> SCRIPT_LATIN
    }

    private fun textMatchesLanguage(text: String, lang: String): Boolean {
        val expected = expectedScript(lang)
        return text.any { scriptOf(it) == expected }
    }

    /** True when both panels resolve to the same recognition pack and share one engine. */
    private fun sameAsrPack(): Boolean =
        streamingPackFor(asrLangFor("A")).id == streamingPackFor(asrLangFor("B")).id

    /**
     * Cheap language evidence for one candidate text: script purity plus a small
     * stop-word prior. A wrong-language engine still emits text in its own script,
     * so script alone cannot decide; common short words tip the balance.
     */
    private fun languageEvidence(text: String, lang: String): Double {
        val clean = text.trim()
        if (clean.isEmpty()) return 0.0
        var best = 0.0
        for (script in supportedScripts(lang)) {
            val purity = scriptPurity(clean, script)
            if (purity == 0.0) continue
            var score = purity * 0.7
            if (script == SCRIPT_CJK && clean.count { scriptOf(it) == SCRIPT_CJK } >= 2) {
                score += 0.35
            }
            if (score > best) best = score
        }
        if (best == 0.0) return 0.0

        val words = clean.split(WORD_SPLIT).filter { it.isNotBlank() }
        val stops = stopwordsFor(lang)
        if (words.isNotEmpty() && stops.isNotEmpty()) {
            val rate = words.count { normalizeWord(it) in stops }.toDouble() / words.size
            best += rate * 0.6
        }

        // Alphabetic output without vowels is usually another language's garbage.
        if (expectedScript(lang) != SCRIPT_CJK) {
            val letters = clean.count { scriptOf(it) != null }
            val vowels = clean.count { it.lowercaseChar() in VOWELS }
            if (letters >= 2 && vowels.toDouble() / letters < 0.12) best *= 0.4
        }
        return best
    }

    /** Scripts a panel's engine can legitimately produce (the zh-en pack also emits Latin). */
    private fun supportedScripts(lang: String): List<String> = when (expectedScript(lang)) {
        SCRIPT_CJK -> listOf(SCRIPT_CJK, SCRIPT_LATIN)
        else -> listOf(expectedScript(lang))
    }

    private fun scriptPurity(text: String, script: String): Double {
        if (script == SCRIPT_CJK) {
            val cjk = text.count { scriptOf(it) == SCRIPT_CJK }
            if (cjk == 0) return 0.0
            val words = WORD_PATTERN.findAll(text).count()
            return cjk.toDouble() / (cjk + words)
        }
        var letters = 0
        var hits = 0
        for (ch in text) {
            val kind = scriptOf(ch) ?: continue
            letters++
            if (kind == script) hits++
        }
        return if (letters == 0) 0.0 else hits.toDouble() / letters
    }

    private fun normalizeWord(raw: String): String =
        raw.lowercase().trim { !it.isLetter() }

    private fun stopwordsFor(lang: String): Set<String> = when (lang) {
        "ru", "uk", "bg", "be", "kk" -> RU_STOPWORDS
        "en" -> EN_STOPWORDS
        else -> emptySet()
    }

    private fun pickPanel(textA: String, textB: String): String {
        val s = _state.value
        val evA = languageEvidence(textA, s.panelALang)
        val evB = languageEvidence(textB, s.panelBLang)
        if (evA < 0.2 && evB < 0.2) {
            return if (textB.isNotBlank() && textB.length > textA.length) "B" else "A"
        }
        if (evA > evB + 0.05) return "A"
        if (evB > evA + 0.05) return "B"
        return if (textB.length > textA.length) "B" else "A"
    }

    private fun finalizeLine(
        text: String,
        speakerLabel: String? = null,
        panel: String = "A",
        sourceLang: String = _state.value.sourceLang,
        targetLang: String = _state.value.targetLang,
    ): Long {
        cancelIdleRelease()
        trimMemoryForLanguage(sourceLang)
        val id = ++sequence
        DiagLog.log("line panel=$panel src=$sourceLang tgt=$targetLang len=${text.length} text=${text.take(60)}")
        val onlyTranscript = _state.value.transcriptOnly
        _state.update {
            it.copy(
                lines = it.lines + TranscriptLine(
                    id = id,
                    source = text,
                    translation = if (onlyTranscript) "" else null,
                    speaker = speakerLabel,
                    panel = panel,
                    srcLang = sourceLang,
                    tgtLang = targetLang,
                ),
                partial = "",
                partialTranslation = "",
                pendingTranslations = it.pendingTranslations + if (onlyTranscript) 0 else 1,
            )
        }
        if (onlyTranscript) {
            schedulePersist()
            scheduleIdleRelease()
            return id
        }
        val job = viewModelScope.launch {
            val result = try {
                Result.success(translator.translate(text, sourceLang, targetLang))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Result.failure(error)
            }
            val translated = result.getOrElse { "翻译失败：${it.message}" }
            _state.update { current ->
                current.copy(
                    lines = current.lines.map { line ->
                        if (line.id == id) {
                            line.copy(translation = translated)
                        } else {
                            line
                        }
                    },
                    pendingTranslations = (current.pendingTranslations - 1).coerceAtLeast(0),
                )
            }
            if (result.isSuccess && _state.value.ttsEnabled) {
                speaker.speak(translated, targetLang)
            }
            schedulePersist()
            scheduleIdleRelease()
        }
        translationJobs += job
        job.invokeOnCompletion { translationJobs -= job }
        return id
    }

    private fun schedulePartialTranslation(text: String, panel: String = "A") {
        partialJob?.cancel()
        // Local NLLB and cloud calls cannot be cancelled once native/network work
        // starts. Partial subtitles would queue large requests and spike memory.
        if (providerConfig.mt != MtMode.MLKIT) return
        val clean = text.trim()
        if (clean.length < 2) return
        partialJob = viewModelScope.launch {
            delay(650)
            val (source, target) = pairFor(panel)
            val translated = try {
                translator.translate(clean, source, target)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                return@launch
            }
            if (_state.value.partial.trim() == clean) {
                _state.update { it.copy(partialTranslation = translated) }
            }
        }
    }

    private fun clearPartial() {
        partialJob?.cancel()
        _state.update { it.copy(partial = "", partialTranslation = "", partialPanel = "A") }
    }

    fun stop() {
        if (!_state.value.running && !_state.value.paused) return
        capture.stop()
        if (_state.value.running) {
            val (finalA, finalB) = finishEngines()
            finalizeBest(finalA, finalB, drainLineSamples(), drainLidSamples())
            resetUtteranceRouting()
        }
        synchronized(lineSamples) { lineSamples.clear() }
        clearPartial()
        _state.update {
            it.copy(running = false, paused = false, level = 0f, testMode = false)
        }
        if (sessionStartedAt > 0) {
            val elapsed = ((System.currentTimeMillis() - sessionStartedAt) / 1000).coerceAtLeast(0)
            settings.totalSeconds += elapsed
            settings.sessionCount += 1
            _state.update {
                it.copy(
                    totalSeconds = settings.totalSeconds,
                    sessionCount = settings.sessionCount,
                )
            }
        }
        persistSession()
        speaker.stop()
        MicService.stop(getApplication())
        scheduleIdleRelease()
    }

    fun switchLanguages(source: String, target: String) {
        if (_state.value.running) stop()
        _state.update {
            it.copy(
                sourceLang = source,
                targetLang = target,
                partial = "",
                partialTranslation = "",
            )
        }
        settings.sourceLang = source
        settings.targetLang = target
        releaseEngines()
        refreshModel()
    }

    fun swapLanguages() {
        val current = _state.value
        switchLanguages(current.targetLang, current.sourceLang)
    }

    fun clearTranscript() {
        clearPartial()
        _state.update { it.copy(lines = emptyList(), paused = false) }
        sessionId = null
        speaker.stop()
        speakerTagger?.reset()
        synchronized(lineSamples) { lineSamples.clear() }
        scheduleIdleRelease()
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    fun refreshRecords() {
        _state.update {
            it.copy(
                records = recordStore.list(),
                folders = recordStore.folders(),
            )
        }
    }

    fun toggleStar(id: String) {
        val record = recordStore.get(id) ?: return
        recordStore.setStarred(id, !record.starred)
        refreshRecords()
    }

    fun deleteRecord(id: String) {
        recordStore.delete(id)
        refreshRecords()
    }

    fun renameRecord(id: String, title: String) {
        if (title.isBlank()) return
        recordStore.rename(id, title.trim())
        refreshRecords()
    }

    fun setRecordFolder(id: String, folder: String) {
        recordStore.setFolder(id, folder)
        refreshRecords()
    }

    fun batchStar(ids: Set<String>, starred: Boolean) {
        ids.forEach { recordStore.setStarred(it, starred) }
        refreshRecords()
    }

    fun batchDelete(ids: Set<String>) {
        ids.forEach { recordStore.delete(it) }
        refreshRecords()
    }

    fun batchMove(ids: Set<String>, folder: String) {
        ids.forEach { recordStore.setFolder(it, folder) }
        refreshRecords()
    }

    fun exportRecord(id: String): File? =
        recordStore.get(id)?.let { recordStore.exportMarkdown(it) }

    fun setTtsEnabled(value: Boolean) {
        settings.ttsEnabled = value
        _state.update { it.copy(ttsEnabled = value) }
        if (!value) speaker.stop()
    }

    fun setShowSource(value: Boolean) {
        settings.showSource = value
        _state.update { it.copy(showSource = value) }
    }

    fun setFontSize(value: Int) {
        val size = value.coerceIn(12, 28)
        settings.fontSize = size
        _state.update { it.copy(fontSize = size) }
    }

    fun setTextMode(value: String) {
        val mode = if (value in setOf("original", "translation", "mixed", "split")) value else "mixed"
        settings.textMode = mode
        _state.update { it.copy(textMode = mode) }
    }

    fun setPanelMode(value: String) {
        val panel = if (value == "dual") "dual" else "single"
        if (_state.value.panelMode == panel) return
        if (_state.value.running || _state.value.paused) stop()
        settings.panelMode = panel
        _state.update {
            it.copy(
                panelMode = panel,
                mode = if (panel == "dual") "dual" else if (it.mode == "dual") "single" else it.mode,
                partial = "",
                partialTranslation = "",
                partialPanel = "A",
            )
        }
        releaseEngines()
    }

    private fun applyLanguageRoute(route: String) {
        if (_state.value.running || _state.value.paused) stop()
        val source = if (route == "zh") "zh" else "ru"
        val target = if (source == "zh") "ru" else "zh"
        val panelMode = if (route == "auto") "dual" else "single"
        settings.panelMode = panelMode
        settings.panelALang = source
        settings.panelBLang = target
        settings.sourceLang = source
        settings.targetLang = target
        _state.update {
            it.copy(
                panelMode = panelMode,
                sourceLang = source,
                targetLang = target,
                panelALang = source,
                panelBLang = target,
                partial = "",
                partialTranslation = "",
                partialPanel = "A",
            )
        }
        releaseEngines()
        refreshModel()
    }

    fun setPanelLanguage(panel: String, lang: String) {
        if (_state.value.running) stop()
        if (panel == "B") {
            settings.panelBLang = lang
            _state.update {
                it.copy(
                    panelBLang = lang,
                    partial = "",
                    partialTranslation = "",
                    partialPanel = "A",
                )
            }
        } else {
            settings.panelALang = lang
            _state.update {
                it.copy(
                    panelALang = lang,
                    partial = "",
                    partialTranslation = "",
                    partialPanel = "A",
                )
            }
        }
        releaseEngines()
    }

    fun setTtsVolume(value: Float) {
        val volume = value.coerceIn(0f, 1f)
        settings.ttsVolume = volume
        speaker.setVolume(volume)
        _state.update { it.copy(ttsVolume = volume) }
    }

    fun setTtsVoice(value: String) {
        settings.ttsVoice = value
        speaker.setVoice(value)
        _state.update { it.copy(ttsVoice = value) }
    }

    fun setAiKeywords(value: String) {
        settings.aiKeywords = value
        _state.update { it.copy(aiKeywords = value) }
        if (providerConfig.mt == com.mine.offlinetranslate.core.MtMode.CLOUD) {
            providerConfig = withAi(providerConfig)
            rebuildTranslator()
        }
    }

    fun setAiContext(value: String) {
        settings.aiContext = value
        _state.update { it.copy(aiContext = value) }
        if (providerConfig.mt == com.mine.offlinetranslate.core.MtMode.CLOUD) {
            providerConfig = withAi(providerConfig)
            rebuildTranslator()
        }
    }

    fun setKnowledgeBaseEnabled(value: Boolean) {
        settings.knowledgeBaseEnabled = value
        _state.update { it.copy(knowledgeBaseEnabled = value) }
        if (providerConfig.mt == com.mine.offlinetranslate.core.MtMode.CLOUD) {
            providerConfig = withAi(providerConfig)
            rebuildTranslator()
        }
    }

    fun setAiKeywordLearning(value: Boolean) {
        settings.aiKeywordLearning = value
        _state.update { it.copy(aiKeywordLearning = value) }
    }

    fun setAiAutoKeywords(value: String) {
        val trimmed = value.take(2000)
        settings.aiAutoKeywords = trimmed
        _state.update { it.copy(aiAutoKeywords = trimmed) }
        if (providerConfig.mt == com.mine.offlinetranslate.core.MtMode.CLOUD) {
            providerConfig = withAi(providerConfig)
            rebuildTranslator()
        }
    }

    fun setAiPersonalization(value: String) {
        settings.aiPersonalization = value
        _state.update { it.copy(aiPersonalization = value) }
        if (providerConfig.mt == com.mine.offlinetranslate.core.MtMode.CLOUD) {
            providerConfig = withAi(providerConfig)
            rebuildTranslator()
        }
    }

    fun setSpeakerDiarization(value: Boolean) {
        settings.speakerDiarization = value
        _state.update { it.copy(speakerDiarization = value) }
        if (!value) {
            speakerTagger?.release()
            speakerTagger = null
            synchronized(lineSamples) { lineSamples.clear() }
        } else if (!repository.isReady(ModelCatalog.SPEAKER_EMBEDDING)) {
            _state.update { it.copy(error = "请先在“我的”里下载说话人区分模型") }
        }
    }

    private fun ensureSpeakerTagger(): Boolean {
        if (!settings.speakerDiarization) return true
        val pack = ModelCatalog.SPEAKER_EMBEDDING
        if (!repository.isReady(pack)) {
            _state.update { it.copy(error = "请先在“我的”里下载说话人区分模型") }
            return false
        }
        if (speakerTagger?.isLoaded == true) return true
        speakerTagger?.release()
        speakerTagger = null
        val tagger = SpeakerTagger(repository.dir(pack))
        return if (tagger.load(pack.files.first().name)) {
            speakerTagger = tagger
            true
        } else {
            _state.update { it.copy(error = "说话人模型加载失败") }
            false
        }
    }

    /** Hidden "仅转写" switch: words only, no translation. */
    fun setTranscriptOnly(value: Boolean) {
        settings.transcriptOnly = value
        _state.update { it.copy(transcriptOnly = value) }
    }

    fun setMode(mode: String) {
        when (mode) {
            "voice" -> {
                setPanelMode("single")
                val source = _state.value.sourceLang
                switchLanguages(source, source)
            }
            "single" -> {
                setPanelMode("single")
                val current = _state.value
                if (current.targetLang == current.sourceLang) {
                    switchLanguages(current.sourceLang, if (current.sourceLang == "zh") "en" else "zh")
                }
            }
            "dual" -> {
                setPanelMode("dual")
            }
            else -> Unit
        }
        _state.update { it.copy(mode = mode) }
    }

    fun summarize() {
        val current = _state.value
        if (current.lines.isEmpty()) {
            _state.update { it.copy(error = "暂无可总结内容") }
            return
        }
        if (providerConfig.mt != com.mine.offlinetranslate.core.MtMode.CLOUD) {
            _state.update { it.copy(error = "AI纪要需要云端 API；请先在设置中切换翻译引擎") }
            return
        }
        val transcript = current.lines.joinToString("\n\n") { line ->
            "${line.source}\n${line.translation ?: ""}"
        }
        _state.update { it.copy(summarizing = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                summaryService.summarize(transcript, providerConfig.cloud)
            }
            _state.update {
                it.copy(
                    summarizing = false,
                    summary = result.getOrNull(),
                    error = result.exceptionOrNull()?.let { error ->
                        "纪要失败：${error.message}"
                    },
                )
            }
        }
    }

    fun dismissSummary() {
        _state.update { it.copy(summary = null) }
    }

    fun translateText(text: String, onResult: (String) -> Unit) {
        val current = _state.value
        translateTextPair(text, current.sourceLang, current.targetLang, onResult)
    }

    fun translatePhotoText(
        text: String,
        source: String,
        target: String,
        onResult: (String) -> Unit,
    ) {
        translateTextPair(text, source, target, onResult)
    }

    private fun translateTextPair(
        text: String,
        source: String,
        target: String,
        onResult: (String) -> Unit,
    ) {
        cancelIdleRelease()
        viewModelScope.launch {
            val result = try {
                translator.translate(text, source, target)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                "翻译失败：${error.message}"
            }
            onResult(result)
            scheduleIdleRelease()
        }
    }

    fun startPhotoTask(uri: Uri) {
        cancelIdleRelease()
        photoJob?.cancel()
        val taskId = UUID.randomUUID().toString()
        val initial = PhotoTask(
            id = taskId,
            status = "正在读取图片…",
            busy = true,
            startedAt = System.currentTimeMillis(),
        )
        photoTaskStore.save(initial)
        _state.update { it.copy(photoTask = initial) }
        photoJob = viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    decodePhoto(getApplication(), uri)
                }
                val imagePath = withContext(Dispatchers.IO) {
                    try {
                        photoTaskStore.saveImage(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
                updatePhotoTask(taskId) {
                    it.copy(
                        imagePath = imagePath,
                        status = "图片已就绪，可开始识别",
                        busy = false,
                        error = null,
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                updatePhotoTask(taskId) {
                    it.copy(
                        status = "图片读取失败",
                        busy = false,
                        error = error.message ?: "图片读取失败",
                    )
                }
                scheduleIdleRelease()
            }
        }
    }

    fun recognizePhotoTask() {
        val task = _state.value.photoTask
        if (task.id.isBlank() || task.imagePath.isBlank() || task.busy) return
        cancelIdleRelease()
        photoJob?.cancel()
        val taskId = task.id
        updatePhotoTask(taskId) {
            it.copy(
                sourceText = "",
                translatedText = "",
                sourceLang = "",
                targetLang = "",
                status = "正在识别图片文字…",
                busy = true,
                error = null,
            )
        }
        photoJob = viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(task.imagePath)
                        ?: error("当前任务图片无法读取")
                }
                val text = try {
                    photoOcr.recognize(bitmap)
                } finally {
                    bitmap.recycle()
                }
                if (text.isBlank()) error("没有识别到清晰文字，请更换图片")
                val source = PhotoOcrLanguage.detect(text)
                val target = PhotoOcrLanguage.targetFor(source)
                updatePhotoTask(taskId) {
                    it.copy(
                        sourceText = text,
                        sourceLang = source,
                        targetLang = target,
                        status = "识别完成，共 ${text.length} 字，正在分段翻译…",
                    )
                }
                val translated = translator.translate(text, source, target)
                val completed = _state.value.photoTask.takeIf { it.id == taskId }?.copy(
                    translatedText = translated,
                    status = "处理完成 · 原文 ${text.length} 字 · 译文 ${translated.length} 字",
                    busy = false,
                    error = null,
                    completedAt = System.currentTimeMillis(),
                ) ?: error("拍照任务已变更")
                updatePhotoTask(taskId) { completed }
                savePhotoRecord(completed)
                scheduleIdleRelease()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                updatePhotoTask(taskId) {
                    it.copy(
                        status = "翻译失败",
                        busy = false,
                        error = error.message ?: "翻译失败",
                    )
                }
                scheduleIdleRelease()
            }
        }
    }

    fun clearPhotoTask() {
        photoJob?.cancel()
        photoJob = null
        photoTaskStore.clear()
        _state.update { it.copy(photoTask = PhotoTask()) }
        scheduleIdleRelease()
    }

    fun seedPhotoTaskForTest(sourceText: String, translatedText: String) {
        photoJob?.cancel()
        val timestamp = System.currentTimeMillis()
        val task = PhotoTask(
            id = UUID.randomUUID().toString(),
            sourceText = sourceText,
            translatedText = translatedText,
            sourceLang = PhotoOcrLanguage.detect(sourceText),
            targetLang = PhotoOcrLanguage.targetFor(PhotoOcrLanguage.detect(sourceText)),
            status = "处理完成 · 原文 ${sourceText.length} 字 · 译文 ${translatedText.length} 字",
            busy = false,
            startedAt = timestamp,
            completedAt = timestamp,
        )
        photoTaskStore.save(task)
        _state.update { it.copy(photoTask = task) }
        savePhotoRecord(task)
    }

    private fun updatePhotoTask(taskId: String, transform: (PhotoTask) -> PhotoTask) {
        val current = _state.value.photoTask
        if (current.id != taskId) return
        val updated = transform(current)
        photoTaskStore.save(updated)
        _state.update {
            if (it.photoTask.id == taskId) it.copy(photoTask = updated) else it
        }
    }

    private fun savePhotoRecord(task: PhotoTask) {
        if (task.id.isBlank() || task.sourceText.isBlank() || task.translatedText.isBlank()) return
        val previous = recordStore.get(task.id)
        val timestamp = task.startedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        recordStore.save(
            RecordSession(
                id = task.id,
                title = "拍照翻译 " + SimpleDateFormat(
                    "MM-dd HH:mm",
                    Locale.getDefault(),
                ).format(Date(timestamp)),
                startedAt = timestamp,
                endedAt = task.completedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                sourceLang = task.sourceLang.ifBlank { "ru" },
                targetLang = task.targetLang.ifBlank { "zh" },
                lines = listOf(
                    RecordLine(
                        source = task.sourceText,
                        translation = task.translatedText,
                        panel = "PHOTO",
                    ),
                ),
                starred = previous?.starred ?: false,
                folder = previous?.folder.orEmpty(),
                kind = RecordKind.PHOTO,
            )
        )
        _state.update {
            it.copy(
                records = recordStore.list(),
                folders = recordStore.folders(),
            )
        }
    }

    private fun persistSession() {
        val id = sessionId ?: return
        val current = _state.value
        if (current.lines.isEmpty()) return
        val title = "翻译记录 " + SimpleDateFormat(
            "MM-dd HH:mm",
            Locale.getDefault(),
        ).format(Date(sessionStartedAt))
        val session = RecordSession(
            id = id,
            title = title,
            startedAt = sessionStartedAt,
            endedAt = System.currentTimeMillis(),
            sourceLang = current.sourceLang,
            targetLang = current.targetLang,
            lines = current.lines.map { RecordLine(it.source, it.translation, it.speaker, it.panel) },
            starred = recordStore.get(id)?.starred ?: false,
            folder = recordStore.get(id)?.folder ?: "",
        )
        recordStore.save(session)
        _state.update {
            it.copy(
                records = recordStore.list(),
                folders = recordStore.folders(),
            )
        }
    }

    private var persistJob: Job? = null

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(500)
            persistSession()
        }
    }

    private fun updateLineSpeaker(id: Long, label: String) {
        _state.update { current ->
            current.copy(
                lines = current.lines.map { line ->
                    if (line.id == id) line.copy(speaker = label) else line
                }
            )
        }
        schedulePersist()
    }

    private fun withAi(config: ProviderConfig): ProviderConfig {
        val current = _state.value
        val keywords = (current.aiKeywords + "," + current.aiAutoKeywords)
            .split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")
        val context = buildString {
            append(current.aiContext.trim())
            if (current.knowledgeBaseEnabled && current.aiPersonalization.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(current.aiPersonalization.trim())
            }
        }
        return config.copy(
            cloud = config.cloud.copy(
                keywords = keywords,
                context = context,
            )
        )
    }

    private fun rebuildTranslator() {
        runCatching { translator.close() }
        translator = newTranslator()
    }

    /** A translation model downloaded after startup must replace the stale null-path engine. */
    private fun refreshTranslatorIfSelected(model: ModelPack) {
        if (
            model.kind == ModelKind.MT &&
            model.id == providerConfig.llmPackId &&
            providerConfig.mt == MtMode.LOCAL_LLM
        ) {
            runCatching { translator.close() }
            translator = newTranslator()
        }
    }

    private fun newTranslator(): TranslationEngine {
        val pack = ModelCatalog.byId(providerConfig.llmPackId) ?: ModelCatalog.NLLB_600M
        val modelDir = repository.dir(pack)
        val path = if (repository.isReady(pack)) {
            if (pack.kind == ModelKind.MT) {
                modelDir.absolutePath
            } else {
                File(modelDir, pack.files.first().name).absolutePath
            }
        } else {
            null
        }
        return TranslationEngines.create(
            withAi(providerConfig),
            getApplication(),
            path,
            pack.id,
        )
    }

    private fun ensureAsrLoaded(): Boolean {
        val targetPack: ModelPack
        val generation: Int
        synchronized(asrLock) {
            if (asrA?.isLoaded == true) return true
            targetPack = pack
            generation = asrGeneration
        }
        synchronized(loadLock) {
            synchronized(asrLock) {
                if (asrA?.isLoaded == true) return true
            }
            val engine = StreamingAsr(getApplication(), repository.dir(targetPack))
            val loaded = loadEngine(engine, targetPack)
            if (loaded.isSuccess) {
                var installed = false
                synchronized(asrLock) {
                    if (generation == asrGeneration) {
                        asrA?.release()
                        asrA = engine
                        installed = true
                    }
                }
                if (!installed) {
                    engine.release()
                    _state.update { it.copy(error = "识别配置已变更，请重新开始") }
                    return false
                }
                _state.update { it.copy(modelReady = true) }
                return true
            }
            _state.update {
                it.copy(error = "模型加载失败：${loaded.exceptionOrNull()?.message}")
            }
            return false
        }
    }

    /**
     * Loads the second engine for dual mode. Returns null when ready (or when both
     * panels share one pack and therefore one engine), otherwise a user-facing reason.
     */
    private fun ensureAsrBLoaded(): String? {
        val s = _state.value
        if (s.panelMode != "dual") return null
        val packA = streamingPackFor(s.panelALang)
        val packB = streamingPackFor(s.panelBLang)
        if (packA.id == packB.id) return null
        if (!repository.isReady(packB)) return "请先下载 ${packB.label} 模型"
        val generation: Int
        synchronized(asrLock) {
            if (asrB?.isLoaded == true) return null
            generation = asrGeneration
        }
        synchronized(loadLock) {
            synchronized(asrLock) {
                if (asrB?.isLoaded == true) return null
            }
            val engine = StreamingAsr(getApplication(), repository.dir(packB))
            val loaded = loadEngine(engine, packB)
            if (loaded.isFailure) {
                return "模型加载失败：${loaded.exceptionOrNull()?.message}"
            }
            var installed = false
            synchronized(asrLock) {
                if (generation == asrGeneration) {
                    asrB?.release()
                    asrB = engine
                    installed = true
                }
            }
            if (!installed) {
                engine.release()
            }
        }
        return null
    }

    private fun loadEngine(engine: StreamingAsr, model: ModelPack): Result<Unit> {
        val files = model.files.map { it.name }
        return engine.load(
            encoder = files[0],
            decoder = files[1],
            joiner = files[2],
            tokens = files[3],
        )
    }

    /** Loads the audio-level language detector once; absence only disables LID. */
    private fun ensureLanguageIdLoaded(): Boolean {
        synchronized(lidLock) {
            if (languageId?.isLoaded == true) return true
            val selected = ModelCatalog.byId(providerConfig.lidPackId)
            val pack = selected
                ?.takeIf { it.kind == ModelKind.LID && repository.isReady(it) }
                ?: ModelCatalog.LID_BASE.takeIf { repository.isReady(it) }
                ?: ModelCatalog.LID_TINY
            if (!repository.isReady(pack)) {
                Log.i("LiveViewModel", "LID pack not ready; text routing only")
                return false
            }
            val engine = LanguageId(getApplication(), repository.dir(pack))
            val files = pack.files.map { it.name }
            val loaded = engine.load(files[0], files[1])
            if (loaded.isSuccess) {
                languageId?.release()
                languageId = engine
                return true
            }
            return false
        }
    }

    /** Drops both engines so the next start reloads them for the current configuration. */
    private fun releaseEngines() {
        synchronized(asrLock) {
            asrGeneration++
            val first = asrA
            val second = asrB
            asrA = null
            asrB = null
            first?.release()
            if (second != null && second !== first) second.release()
        }
    }

    private fun cancelIdleRelease() {
        idleReleaseJob?.cancel()
        idleReleaseJob = null
    }

    private fun scheduleIdleRelease(delayMs: Long = 20_000L) {
        cancelIdleRelease()
        idleReleaseJob = viewModelScope.launch {
            delay(delayMs)
            while (
                _state.value.running ||
                _state.value.modelLoading ||
                _state.value.photoTask.busy ||
                translationJobs.isNotEmpty() ||
                partialJob?.isActive == true
            ) {
                delay(2_000)
            }
            releaseIdleModels()
        }
    }

    private suspend fun releaseIdleModels() {
        releaseEngines()
        synchronized(lidLock) {
            runCatching { languageId?.release() }
            languageId = null
        }
        synchronized(zhHighAsrLock) {
            zhHighAsr?.release()
            zhHighAsr = null
        }
        synchronized(ruHighAsrLock) {
            ruHighAsr?.release()
            ruHighAsr = null
            ruGigaAsr?.release()
            ruGigaAsr = null
        }
        if (!_state.value.speakerDiarization) {
            speakerTagger?.release()
            speakerTagger = null
        }
        runCatching { NllbRuntime.release() }
        _state.update { it.copy(modelReady = false) }
        DiagLog.log("idle models released pss=${processPssKb()}KB")
        DiagLog.memory(getApplication(), "models-released")
    }

    private fun trimMemoryForLanguage(source: String) {
        if (processPssKb() < MEMORY_TRIM_PSS_KB) return
        if (source == "zh") {
            synchronized(ruHighAsrLock) {
                ruHighAsr?.release()
                ruHighAsr = null
                ruGigaAsr?.release()
                ruGigaAsr = null
            }
        } else if (source == "ru") {
            synchronized(zhHighAsrLock) {
                zhHighAsr?.release()
                zhHighAsr = null
            }
        }
        DiagLog.log("memory trim source=$source pss=${processPssKb()}KB")
    }

    private fun processPssKb(): Int {
        val manager = getApplication<Application>()
            .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return runCatching {
            manager.getProcessMemoryInfo(intArrayOf(Process.myPid()))
                .firstOrNull()
                ?.totalPss
                ?: 0
        }.getOrDefault(0)
    }

    override fun onCleared() {
        cancelIdleRelease()
        photoJob?.cancel()
        capture.stop()
        MicService.stop(getApplication())
        persistSession()
        cancelTranslations()
        releaseEngines()
        synchronized(lidLock) {
            runCatching { languageId?.release() }
            languageId = null
        }
        synchronized(lidSamples) { lidSamples.clear() }
        runCatching { translator.close() }
        zhHighAsr?.release()
        zhHighAsr = null
        ruHighAsr?.release()
        ruHighAsr = null
        ruGigaAsr?.release()
        ruGigaAsr = null
        speaker.shutdown()
        speakerTagger?.release()
        speakerTagger = null
        super.onCleared()
    }

    private fun cancelTranslations() {
        partialJob?.cancel()
        partialJob = null
        translationJobs.forEach { it.cancel() }
        translationJobs.clear()
    }

    private companion object {
        const val LID_PROBE_FIRST_SAMPLES = 16_000 * 5 / 2
        const val LID_PROBE_STEP_SAMPLES = 16_000 * 3 / 2
        const val LID_PROBE_LAST_SAMPLES = 16_000 * 6
        const val MEMORY_TRIM_PSS_KB = 3_000_000
        const val MAX_LIVE_TEXT_CHARS = 120
        const val SCRIPT_CYRILLIC = "cyrillic"
        const val SCRIPT_CJK = "cjk"
        const val SCRIPT_LATIN = "latin"

        val WORD_PATTERN = Regex("[A-Za-z\\u00C0-\\u024F]+")
        val WORD_SPLIT = Regex("\\s+")
        val VOWELS = "аеёиоуыэюяaeiouy".toSet()

        val RU_STOPWORDS = setOf(
            "и", "в", "во", "не", "что", "он", "на", "я", "с", "со", "как", "а",
            "то", "все", "она", "так", "его", "но", "да", "ты", "к", "у", "же",
            "вы", "за", "бы", "по", "только", "ее", "мне", "было", "вот", "от",
            "меня", "еще", "нет", "о", "из", "ему", "теперь", "когда", "даже",
            "ну", "ли", "если", "уже", "или", "ни", "быть", "был", "него", "до",
            "вас", "уж", "вам", "ведь", "там", "потом", "себя", "ей", "они",
            "тут", "где", "есть", "надо", "для", "мы", "тебя", "их", "чем",
            "была", "сам", "без", "чего", "раз", "тоже", "себе", "под", "будет",
            "тогда", "кто", "этот", "того", "потому", "этого", "какой", "ним",
            "здесь", "этом", "один", "почти", "мой", "тем", "чтобы", "нее",
            "сейчас", "были", "куда", "зачем", "всех", "можно", "при", "об",
            "после", "над", "больше", "тот", "через", "эти", "нас", "про", "них",
        )

        val EN_STOPWORDS = setOf(
            "the", "a", "an", "and", "or", "but", "if", "then", "is", "are",
            "was", "were", "be", "been", "of", "to", "in", "on", "at", "for",
            "with", "that", "this", "it", "i", "you", "he", "she", "we", "they",
            "my", "your", "his", "her", "not", "no", "do", "does", "did", "have",
            "has", "had", "will", "would", "can", "could", "as", "by", "from",
        )
    }
}
