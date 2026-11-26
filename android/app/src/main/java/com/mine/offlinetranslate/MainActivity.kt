package com.mine.offlinetranslate

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mine.offlinetranslate.core.CloudConfig
import com.mine.offlinetranslate.core.DiagLog
import com.mine.offlinetranslate.core.ModelCatalog
import com.mine.offlinetranslate.core.ModelPack
import com.mine.offlinetranslate.core.OnnxRuntimeLoader
import com.mine.offlinetranslate.core.MtMode
import com.mine.offlinetranslate.core.ProviderConfig
import com.mine.offlinetranslate.ui.AboutScreen
import com.mine.offlinetranslate.ui.LiveScreen
import com.mine.offlinetranslate.ui.OfflineTranslateTheme
import com.mine.offlinetranslate.ui.PhotoTranslateScreen
import com.mine.offlinetranslate.ui.ProfileScreen
import com.mine.offlinetranslate.ui.ProviderSettingsDialog
import com.mine.offlinetranslate.ui.RecordsScreen
import com.mine.offlinetranslate.ui.TextTranslateScreen
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        OnnxRuntimeLoader.ensureLoaded()
        val requestedAutoRun = intent?.getBooleanExtra("autorun_test", false) ?: false
        if (requestedAutoRun && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        super.onCreate(savedInstanceState)
        val autoConfig = parseAutoConfig(intent)
        val autoRunTest = requestedAutoRun
        val autoDownload = intent?.getBooleanExtra("download_model", false) ?: false
        val autoDownloadPack = intent?.getStringExtra("download_pack")
        val autoSpeaker = intent?.getBooleanExtra("speaker", false) ?: false
        val autoSource = intent?.getStringExtra("source")
        val autoTarget = intent?.getStringExtra("target")
        val autoTestWav = intent?.getStringExtra("test_wav")
        val autoTranslateText = intent?.getStringExtra("direct_text")
        val autoPhotoSource = intent?.getStringExtra("photo_test_source")
        val autoPhotoTranslation = intent?.getStringExtra("photo_test_translation")
        val autoPhotoClear = intent?.getBooleanExtra("photo_test_clear", false) ?: false
        val autoPanel = intent?.getStringExtra("panel")
        setContent {
            OfflineTranslateTheme {
                AppContent(
                    autoRunTest = autoRunTest,
                    autoDownload = autoDownload,
                    autoDownloadPack = autoDownloadPack,
                    autoSpeaker = autoSpeaker,
                    autoConfig = autoConfig,
                    autoSource = autoSource,
                    autoTarget = autoTarget,
                    autoTestWav = autoTestWav,
                    autoTranslateText = autoTranslateText,
                    autoPhotoSource = autoPhotoSource,
                    autoPhotoTranslation = autoPhotoTranslation,
                    autoPhotoClear = autoPhotoClear,
                    autoPanel = autoPanel,
                )
            }
        }
    }

    private fun parseAutoConfig(intent: android.content.Intent?): ProviderConfig? {
        val raw = intent?.getStringExtra("mt") ?: return null
        val mode = runCatching { MtMode.valueOf(raw.uppercase()) }.getOrNull() ?: return null
        val defaults = CloudConfig()
        return ProviderConfig(
            mt = mode,
            llmPackId = intent.getStringExtra("llm_pack") ?: ProviderConfig().llmPackId,
            cloud = CloudConfig(
                baseUrl = intent.getStringExtra("cloud_base") ?: defaults.baseUrl,
                apiKey = intent.getStringExtra("cloud_key") ?: "",
                chatModel = intent.getStringExtra("cloud_model") ?: defaults.chatModel,
                asrModel = intent.getStringExtra("cloud_asr_model") ?: defaults.asrModel,
            ),
        )
    }
}

@Composable
private fun AppContent(
    autoRunTest: Boolean,
    autoDownload: Boolean,
    autoDownloadPack: String?,
    autoSpeaker: Boolean,
    autoConfig: ProviderConfig?,
    autoSource: String?,
    autoTarget: String?,
    autoTestWav: String?,
    autoTranslateText: String?,
    autoPhotoSource: String?,
    autoPhotoTranslation: String?,
    autoPhotoClear: Boolean,
    autoPanel: String?,
) {
    val viewModel: LiveViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rootView = LocalView.current
    LaunchedEffect(state.running, state.modelLoading) {
        rootView.keepScreenOn = state.running || state.modelLoading
    }
    val context = LocalContext.current
    BackHandler(enabled = state.downloads.values.any { it.active }) {
        (context as? Activity)?.moveTaskToBack(true)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var settingsOpen by remember { mutableStateOf(false) }
    var importingPack by remember { mutableStateOf<ModelPack?>(null) }
    var pendingExportFile by remember { mutableStateOf<File?>(null) }
    var confirmDiagExport by remember { mutableStateOf(false) }
    var textTranslateOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        autoPanel?.let { viewModel.setPanelMode(it) }
        if (autoSource != null && autoTarget != null) {
            viewModel.switchLanguages(autoSource, autoTarget)
        }
        autoConfig?.let { viewModel.updateProviderConfig(it) }
        if (autoSpeaker) {
            viewModel.setSpeakerDiarization(true)
        }
        if (autoDownload) {
            viewModel.downloadPack(ModelCatalog.RU_SMALL)
        }
        autoDownloadPack?.let { packId ->
            ModelCatalog.byId(packId)?.let(viewModel::downloadPack)
        }
        if (!autoTranslateText.isNullOrBlank()) {
            viewModel.translateText(autoTranslateText) { result ->
                DiagLog.log("direct translate result=$result")
                runCatching {
                    File(context.filesDir, "last-direct-translation.txt").writeText(result)
                }
            }
        }
        if (!autoPhotoSource.isNullOrBlank() && !autoPhotoTranslation.isNullOrBlank()) {
            viewModel.seedPhotoTaskForTest(autoPhotoSource, autoPhotoTranslation)
        }
        if (autoPhotoClear) viewModel.clearPhotoTask()
        if (autoRunTest) {
            viewModel.startWithAssetWav(autoTestWav ?: "test_ru.wav")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.start()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("需要麦克风权限才能开始同传")
            }
        }
    }
    val modelImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = importingPack
        importingPack = null
        if (uri != null && target != null) {
            viewModel.importPack(target, uri)
        }
    }

    BackHandler(enabled = textTranslateOpen) {
        textTranslateOpen = false
    }
    BackHandler(enabled = tab == 1) {
        tab = 0
    }
    BackHandler(enabled = aboutOpen) {
        aboutOpen = false
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (!textTranslateOpen && !aboutOpen) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Rounded.Mic, contentDescription = null) },
                        label = { Text("同传") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
                        label = { Text("拍照翻译") },
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                        label = { Text("记录") },
                    )
                    NavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        label = { Text("我的") },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                textTranslateOpen -> TextTranslateScreen(
                    state = state,
                    onTranslate = { text, callback -> viewModel.translateText(text, callback) },
                    onBack = { textTranslateOpen = false },
                )
                aboutOpen -> AboutScreen(onBack = { aboutOpen = false })
                tab == 1 -> PhotoTranslateScreen(
                    state = state,
                    onStartPhoto = viewModel::startPhotoTask,
                    onRecognize = viewModel::recognizePhotoTask,
                    onClear = viewModel::clearPhotoTask,
                    onBack = { tab = 0 },
                )
                tab == 0 -> LiveScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onStart = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            viewModel.start()
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECORD_AUDIO,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            )
                        }
                    },
                    onStop = viewModel::stop,
                    onPause = viewModel::pause,
                    onResume = viewModel::resume,
                    onDownload = viewModel::downloadPack,
                    onClear = viewModel::clearTranscript,
                    onSwap = viewModel::swapLanguages,
                    onSource = { viewModel.switchLanguages(it, state.targetLang) },
                    onTarget = { viewModel.switchLanguages(state.sourceLang, it) },
                    onOpenSettings = { settingsOpen = true },
                    onMode = viewModel::setMode,
                    onTtsToggle = { viewModel.setTtsEnabled(!state.ttsEnabled) },
                    onSummarize = viewModel::summarize,
                    onDismissSummary = viewModel::dismissSummary,
                    onFontSize = viewModel::setFontSize,
                    onTextMode = viewModel::setTextMode,
                    onPanelMode = viewModel::setPanelMode,
                    onPanelLanguage = viewModel::setPanelLanguage,
                    onTranscriptOnly = viewModel::setTranscriptOnly,
                    onTtsVolume = viewModel::setTtsVolume,
                    onTtsVoice = viewModel::setTtsVoice,
                )
                tab == 2 -> RecordsScreen(
                    records = state.records,
                    folders = state.folders,
                    onStar = viewModel::toggleStar,
                    onDelete = viewModel::deleteRecord,
                    onRename = viewModel::renameRecord,
                    onExport = { id ->
                        viewModel.exportRecord(id)?.let { pendingExportFile = it }
                    },
                    onBatchStar = viewModel::batchStar,
                    onBatchDelete = viewModel::batchDelete,
                    onBatchMove = viewModel::batchMove,
                )
                else -> ProfileScreen(
                    state = state,
                    onDownload = viewModel::downloadPack,
                    onDeleteModel = viewModel::deletePack,
                    onCancelModel = viewModel::cancelDownload,
                    onExportDiag = { confirmDiagExport = true },
                    onTts = viewModel::setTtsEnabled,
                    onShowSource = viewModel::setShowSource,
                    onSpeakerDiarization = viewModel::setSpeakerDiarization,
                    onFontSize = viewModel::setFontSize,
                    onAiKeywords = viewModel::setAiKeywords,
                    onAiContext = viewModel::setAiContext,
                    onKnowledgeBase = viewModel::setKnowledgeBaseEnabled,
                    onKeywordLearning = viewModel::setAiKeywordLearning,
                    onAutoKeywords = viewModel::setAiAutoKeywords,
                    onPersonalization = viewModel::setAiPersonalization,
                    onOpenProviders = { settingsOpen = true },
                    onOpenTextTranslate = {
                        textTranslateOpen = true
                    },
                    onOpenPhotoTranslate = {
                        textTranslateOpen = false
                        aboutOpen = false
                        tab = 1
                    },
                    onOpenAbout = {
                        textTranslateOpen = false
                        aboutOpen = true
                    },
                )
            }
        }
    }

    if (settingsOpen) {
        ProviderSettingsDialog(
            initial = state.providerConfig,
            downloads = state.downloads,
            onDismiss = { settingsOpen = false },
            onSave = {
                viewModel.updateProviderConfig(it)
                settingsOpen = false
            },
            onCloudChange = viewModel::updateCloudSettings,
            onDownload = viewModel::downloadPack,
            onDownloadRecommended = viewModel::downloadRecommendedModels,
            onImport = { pack ->
                importingPack = pack
                modelImportLauncher.launch(arrayOf("*/*"))
            },
            onDelete = viewModel::deletePack,
            onCancel = viewModel::cancelDownload,
        )
    }

    if (confirmDiagExport) {
        AlertDialog(
            onDismissRequest = { confirmDiagExport = false },
            title = { Text("导出诊断日志？") },
            text = { Text("只有确认后才会生成本次诊断文件；默认不会自动保存日志。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiagExport = false
                        DiagLog.export()?.let { pendingExportFile = it }
                    },
                ) { Text("导出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiagExport = false }) { Text("取消") }
            },
        )
    }

    pendingExportFile?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingExportFile = null },
            title = { Text("文件已保存") },
            text = {
                Text("${file.name}\n已保存到 App 导出目录。\n如需发送给其他应用，再点“分享文件”。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingExportFile = null
                        shareFile(context, file)
                    },
                ) { Text("分享文件") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExportFile = null }) { Text("关闭") }
            },
        )
    }
}

private fun shareFile(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "分享记录").apply {
            putExtra(
                Intent.EXTRA_EXCLUDE_COMPONENTS,
                arrayOf(ComponentName(context, MainActivity::class.java)),
            )
        }
        context.startActivity(chooser)
    }
}
