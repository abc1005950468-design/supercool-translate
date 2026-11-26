package com.mine.offlinetranslate.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.mine.offlinetranslate.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mine.offlinetranslate.DownloadState
import com.mine.offlinetranslate.LiveUiState
import com.mine.offlinetranslate.TranscriptLine
import com.mine.offlinetranslate.core.ModelCatalog
import com.mine.offlinetranslate.core.ModelPack
import com.mine.offlinetranslate.core.MtMode
import com.mine.offlinetranslate.core.Languages

private val SOURCE_LANGUAGES = Languages.all.map { it.code to it.label }

private val TARGET_LANGUAGES = Languages.all.map { it.code to it.label }

@Composable
fun LiveScreen(
    state: LiveUiState,
    snackbarHostState: SnackbarHostState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDownload: (ModelPack) -> Unit,
    onClear: () -> Unit,
    onSwap: () -> Unit,
    onSource: (String) -> Unit,
    onTarget: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onMode: (String) -> Unit,
    onTtsToggle: () -> Unit,
    onSummarize: () -> Unit,
    onDismissSummary: () -> Unit,
    onFontSize: (Int) -> Unit,
    onTextMode: (String) -> Unit,
    onPanelMode: (String) -> Unit,
    onPanelLanguage: (String, String) -> Unit,
    onTranscriptOnly: (Boolean) -> Unit,
    onTtsVolume: (Float) -> Unit,
    onTtsVoice: (String) -> Unit,
) {
    val pack = ModelCatalog.asr(state.sourceLang)
    val download = state.downloads[pack.id]
    var menuOpen by remember { mutableStateOf(false) }
    var ttsOpen by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            ControlBar(
                state = state,
                onStart = onStart,
                onStop = onStop,
                onPause = onPause,
                onResume = onResume,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Header(
                state = state,
                onClear = onClear,
                onOpenSettings = onOpenSettings,
                onMore = { menuOpen = true },
            )
            if (!state.hasGms && state.providerConfig.mt == MtMode.MLKIT) {
                Text(
                    text = "本机无 Google 服务：默认翻译不可用，请在设置里改用离线模型或云端 API。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            LanguageRow(
                state = state,
                onSwap = onSwap,
                onSource = onSource,
                onTarget = onTarget,
            )
            if (!Languages.asrSupported(state.sourceLang)) {
                Text(
                    text = "该语言暂无本地识别模型：可先用文本翻译，或等待 Whisper 多语模型接入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            QuickRow(
                state = state,
                onTtsOpen = { ttsOpen = true },
                onSummarize = onSummarize,
            )
            if (!state.modelReady || download?.active == true) {
                ModelCard(
                    pack = pack,
                    download = download,
                    onDownload = { onDownload(pack) },
                )
            }
            Transcript(
                state = state,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (menuOpen) {
        PanelMenuDialog(
            state = state,
            onDismiss = { menuOpen = false },
            onFontSize = onFontSize,
            onSwap = onSwap,
            onPanelMode = onPanelMode,
            onTextMode = onTextMode,
            onTranscriptOnly = onTranscriptOnly,
        )
    }

    if (ttsOpen) {
        TtsDialog(
            state = state,
            onDismiss = { ttsOpen = false },
            onTtsToggle = onTtsToggle,
            onTtsVoice = onTtsVoice,
            onTtsVolume = onTtsVolume,
        )
    }

    state.summary?.let { summary ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = onDismissSummary,
            title = { Text("AI 纪要") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(summary))
                }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = onDismissSummary) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun Header(
    state: LiveUiState,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
    onMore: () -> Unit,
) {
    val status = when {
        state.testMode -> "测试音频运行中 · ${label(state.sourceLang)} → ${label(state.targetLang)}"
        state.running -> "正在同传 · ${label(state.sourceLang)} → ${label(state.targetLang)}"
        state.modelLoading -> "正在加载模型…"
        else -> engineSummary(state)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "超酷翻译",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "清空")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "设置")
            }
            IconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "面板菜单")
            }
        }
        Text(
            text = "使用离线模型时识别时间会较长，请耐心等待",
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 58.dp, bottom = 10.dp),
        )
    }
}

private fun engineSummary(state: LiveUiState): String {
    val mt = when (state.providerConfig.mt) {
        MtMode.LOCAL_LLM -> "离线 ${ModelCatalog.byId(state.providerConfig.llmPackId)?.label ?: "大模型"}"
        MtMode.CLOUD -> "云端 ${state.providerConfig.cloud.chatModel}"
        MtMode.MLKIT -> "ML Kit"
    }
    return "本机识别 · $mt"
}

@Composable
private fun LanguageRow(
    state: LiveUiState,
    onSwap: () -> Unit,
    onSource: (String) -> Unit,
    onTarget: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LanguageChip(
            value = label(state.sourceLang),
            options = SOURCE_LANGUAGES,
            onSelect = onSource,
        )
        IconButton(onClick = onSwap) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = "交换语言")
        }
        LanguageChip(
            value = label(state.targetLang),
            options = TARGET_LANGUAGES,
            onSelect = onTarget,
        )
    }
}

@Composable
private fun DualLanguageRow(
    state: LiveUiState,
    onPanelLanguage: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "A",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            LanguageChip(
                value = label(state.panelALang),
                options = SOURCE_LANGUAGES,
                onSelect = { onPanelLanguage("A", it) },
            )
            Text(
                text = "→ ${label(state.panelBLang)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "B",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            LanguageChip(
                value = label(state.panelBLang),
                options = SOURCE_LANGUAGES,
                onSelect = { onPanelLanguage("B", it) },
            )
            Text(
                text = "→ ${label(state.panelALang)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeRow(mode: String, onMode: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            "single" to "单通道",
            "dual" to "双通道",
            "voice" to "语音转文字",
            "chat" to "聊天室",
        ).forEach { (key, label) ->
            FilterChip(
                selected = mode == key,
                onClick = { onMode(key) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun QuickRow(
    state: LiveUiState,
    onTtsOpen: () -> Unit,
    onSummarize: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onTtsOpen) {
            Icon(
                imageVector = if (state.ttsEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                contentDescription = "语音播报",
                tint = if (state.ttsEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (state.ttsEnabled) "播报开" else "播报关",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (state.summarizing) {
            Text("纪要生成中…", style = MaterialTheme.typography.labelMedium)
        } else {
            TextButton(onClick = onSummarize) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("AI纪要")
            }
        }
    }
}

@Composable
private fun LanguageChip(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(value) },
            trailingIcon = {
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (code, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(code)
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    pack: ModelPack,
    download: DownloadState?,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "需要下载${pack.label}模型",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${pack.description} · ${pack.totalBytes / 1024 / 1024} MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
            if (download?.active == true) {
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(CircleShape),
                )
                Text(
                    text = download.detail.ifBlank { "下载中…" },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("下载模型")
                }
            }
        }
    }
}

@Composable
private fun Transcript(
    state: LiveUiState,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val extra = if (state.partial.isNotBlank()) 1 else 0
    LaunchedEffect(state.lines.size, state.partial) {
        val count = state.lines.size + extra
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        items(state.lines, key = { it.id }) { line ->
            LineItem(
                line = line,
                fontSize = state.fontSize,
                showSource = state.showSource,
                textMode = state.textMode,
            )
        }
        if (state.partial.isNotBlank()) {
            item("partial") {
                PartialItem(
                    source = state.partial,
                    translation = state.partialTranslation,
                    fontSize = state.fontSize,
                    showSource = state.showSource,
                    textMode = state.textMode,
                )
            }
        }
        if (state.lines.isEmpty() && state.partial.isBlank()) {
            item("empty") {
                Text(
                    text = "点击下方麦克风开始\n音频与译文只留在本机",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp,
                )
            }
        }
    }
}

@Composable
private fun DualTranscript(
    state: LiveUiState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PanelColumn(
            state = state,
            panel = "A",
            modifier = Modifier.weight(1f),
        )
        PanelColumn(
            state = state,
            panel = "B",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PanelColumn(
    state: LiveUiState,
    panel: String,
    modifier: Modifier = Modifier,
) {
    val title = if (panel == "A") {
        "${panel} · ${label(state.panelALang)} → ${label(state.panelBLang)}"
    } else {
        "${panel} · ${label(state.panelBLang)} → ${label(state.panelALang)}"
    }
    val lines = state.lines.filter { it.panel == panel }
    val partial = if (state.partial.isNotBlank() && state.partialPanel == panel) state.partial else ""
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size, partial) {
        val count = lines.size + if (partial.isNotBlank()) 1 else 0
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }
    val fontSize = (state.fontSize - 2).coerceAtLeast(12)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        )
        if (lines.isEmpty() && partial.isBlank()) {
            Text(
                text = "等待 $panel 发言",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(lines, key = { it.id }) { line ->
                    LineItem(
                        line = line,
                        fontSize = fontSize,
                        showSource = state.showSource,
                        textMode = state.textMode,
                    )
                }
                if (partial.isNotBlank()) {
                    item("partial-$panel") {
                        PartialItem(
                            source = partial,
                            translation = state.partialTranslation,
                            fontSize = fontSize,
                            showSource = state.showSource,
                            textMode = state.textMode,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LineItem(
    line: TranscriptLine,
    fontSize: Int,
    showSource: Boolean,
    textMode: String,
) {
    val translation = line.translation
    val small = (fontSize - 2).coerceAtLeast(12)
    Column(modifier = Modifier.fillMaxWidth()) {
        if (line.srcLang.isNotBlank() && line.tgtLang.isNotBlank()) {
            Text(
                text = "[${label(line.srcLang)}→${label(line.tgtLang)}]",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        line.speaker?.let { speaker ->
            Text(
                text = speaker,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        when (textMode) {
            "original" -> {
                Text(
                    text = line.source,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = (fontSize + 8).sp,
                )
            }
            "translation" -> {
                if (translation != null) {
                    Text(
                        text = translation,
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = (fontSize + 8).sp,
                    )
                } else {
                    Text(
                        text = "翻译中…",
                        fontSize = small.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
            }
            "split" -> {
                Text(
                    text = "原文",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = line.source,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = (fontSize + 8).sp,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(
                    text = "译文",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (translation != null) {
                    Text(
                        text = translation,
                        fontSize = small.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = (fontSize + 6).sp,
                    )
                } else {
                    Text(
                        text = "翻译中…",
                        fontSize = small.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
            }
            else -> {
                if (showSource) {
                    Text(
                        text = line.source,
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = (fontSize + 8).sp,
                    )
                }
                if (translation != null && translation != line.source) {
                    Text(
                        text = translation,
                        fontSize = small.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = if (showSource) 4.dp else 0.dp),
                        lineHeight = (fontSize + 6).sp,
                    )
                } else if (translation == null) {
                    Text(
                        text = "翻译中…",
                        fontSize = small.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = if (showSource) 4.dp else 0.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PartialItem(
    source: String,
    translation: String,
    fontSize: Int,
    showSource: Boolean,
    textMode: String,
) {
    val small = (fontSize - 2).coerceAtLeast(12)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            when (textMode) {
                "original" -> {
                    Text(
                        text = source,
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = (fontSize + 8).sp,
                    )
                }
                "translation" -> {
                    if (translation.isNotBlank()) {
                        Text(
                            text = translation,
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (fontSize + 8).sp,
                        )
                    } else {
                        Text(
                            text = "翻译中…",
                            fontSize = small.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
                "split" -> {
                    Text(
                        text = "原文",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                    Text(
                        text = source,
                        fontSize = fontSize.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = (fontSize + 8).sp,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(
                        text = "译文",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (translation.isNotBlank()) {
                        Text(
                            text = translation,
                            fontSize = small.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            lineHeight = (fontSize + 6).sp,
                        )
                    } else {
                        Text(
                            text = "翻译中…",
                            fontSize = small.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
                else -> {
                    if (showSource) {
                        Text(
                            text = source,
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = (fontSize + 8).sp,
                        )
                    }
                    if (translation.isNotBlank() && translation != source) {
                        Text(
                            text = translation,
                            fontSize = small.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = if (showSource) 4.dp else 0.dp),
                            lineHeight = (fontSize + 6).sp,
                        )
                    } else if (translation.isBlank()) {
                        Text(
                            text = "翻译中…",
                            fontSize = small.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = if (showSource) 4.dp else 0.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlBar(
    state: LiveUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                progress = { state.level },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.running) {
                    FilledTonalIconButton(
                        onClick = onPause,
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                    ) {
                        Icon(Icons.Rounded.Pause, contentDescription = "暂停")
                    }
                }
                FilledIconButton(
                    onClick = when {
                        state.paused -> onResume
                        state.running -> onStop
                        else -> onStart
                    },
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    enabled = !state.modelLoading,
                ) {
                    Icon(
                        imageVector = when {
                            state.paused -> Icons.Rounded.PlayArrow
                            state.running -> Icons.Rounded.Stop
                            else -> Icons.Rounded.Mic
                        },
                        contentDescription = when {
                            state.paused -> "继续"
                            state.running -> "停止"
                            else -> "开始"
                        },
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Text(
                text = when {
                    state.paused -> "已暂停 · 点击继续"
                    state.running -> "正在聆听 · 点击停止"
                    state.modelLoading -> "加载模型…"
                    else -> "点击开始"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun label(code: String): String =
    SOURCE_LANGUAGES.firstOrNull { it.first == code }?.second ?: code

@Composable
private fun PanelMenuDialog(
    state: LiveUiState,
    onDismiss: () -> Unit,
    onFontSize: (Int) -> Unit,
    onSwap: () -> Unit,
    onPanelMode: (String) -> Unit,
    onTextMode: (String) -> Unit,
    onTranscriptOnly: (Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("面板菜单") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("字号", style = MaterialTheme.typography.labelLarge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { onFontSize((state.fontSize - 1).coerceAtLeast(12)) },
                        label = { Text("A-") },
                    )
                    Text(
                        text = "${state.fontSize}px",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AssistChip(
                        onClick = { onFontSize((state.fontSize + 1).coerceAtMost(40)) },
                        label = { Text("A+") },
                    )
                }
                TextButton(onClick = onSwap) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("翻转")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("仅转写（不翻译）", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.transcriptOnly,
                        onCheckedChange = onTranscriptOnly,
                    )
                }
                Text("文本", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "original" to "仅原文",
                        "translation" to "仅译文",
                        "mixed" to "混合",
                        "split" to "分区",
                    ).forEach { (key, text) ->
                        FilterChip(
                            selected = state.textMode == key,
                            onClick = { onTextMode(key) },
                            label = { Text(text) },
                        )
                    }
                }
                Text(
                    text = when (state.textMode) {
                        "original" -> "仅原文：只显示识别出的文字，不显示翻译。"
                        "translation" -> "仅译文：只显示翻译结果，隐藏原文。"
                        "split" -> "分区：分别标注“原文”和“译文”，适合长对话记录。"
                        else -> "混合：原文在上、译文在下，默认显示方式。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun TtsDialog(
    state: LiveUiState,
    onDismiss: () -> Unit,
    onTtsToggle: () -> Unit,
    onTtsVoice: (String) -> Unit,
    onTtsVolume: (Float) -> Unit,
) {
    var voicesExpanded by remember { mutableStateOf(false) }
    val voices = state.ttsVoices
    val currentVoice = voices.firstOrNull { it == state.ttsVoice } ?: "系统默认"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("语音播报") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (state.ttsEnabled) "播报中" else "未播报",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.ttsEnabled, onCheckedChange = { onTtsToggle() })
                }
                Text("音色", style = MaterialTheme.typography.labelLarge)
                Box {
                    AssistChip(
                        onClick = { voicesExpanded = true },
                        label = { Text(currentVoice) },
                        trailingIcon = {
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                        },
                    )
                    DropdownMenu(
                        expanded = voicesExpanded,
                        onDismissRequest = { voicesExpanded = false },
                    ) {
                        if (voices.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("系统默认") },
                                onClick = {
                                    voicesExpanded = false
                                    onTtsVoice("")
                                },
                            )
                        } else {
                            voices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice) },
                                    onClick = {
                                        voicesExpanded = false
                                        onTtsVoice(voice)
                                    },
                                )
                            }
                        }
                    }
                }
                Text(
                    text = "音量 ${(state.ttsVolume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = state.ttsVolume.coerceIn(0f, 1f),
                    onValueChange = { onTtsVolume(it) },
                    valueRange = 0f..1f,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}
