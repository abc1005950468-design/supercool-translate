package com.mine.offlinetranslate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mine.offlinetranslate.DownloadState
import com.mine.offlinetranslate.LiveUiState
import com.mine.offlinetranslate.core.ModelCatalog
import com.mine.offlinetranslate.core.ModelPack

@Composable
fun ProfileScreen(
    state: LiveUiState,
    onDownload: (ModelPack) -> Unit,
    onDeleteModel: (ModelPack) -> Unit,
    onCancelModel: (ModelPack) -> Unit,
    onExportDiag: () -> Unit,
    onTts: (Boolean) -> Unit,
    onShowSource: (Boolean) -> Unit,
    onSpeakerDiarization: (Boolean) -> Unit,
    onFontSize: (Int) -> Unit,
    onAiKeywords: (String) -> Unit,
    onAiContext: (String) -> Unit,
    onKnowledgeBase: (Boolean) -> Unit = {},
    onKeywordLearning: (Boolean) -> Unit = {},
    onAutoKeywords: (String) -> Unit = {},
    onPersonalization: (String) -> Unit = {},
    onOpenProviders: () -> Unit,
    onOpenTextTranslate: () -> Unit,
    onOpenPhotoTranslate: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var aiOpen by remember { mutableStateOf(false) }
    val keywordCount = (state.aiKeywords + "," + state.aiAutoKeywords)
        .split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header() }
        item { UsageCard(state) }
        item {
            Column {
                Section("翻译与音频")
                SettingRow("语音播报", "译文完成后自动朗读") {
                    Switch(checked = state.ttsEnabled, onCheckedChange = onTts)
                }
                SettingRow("显示原文", "关闭后只显示译文") {
                    Switch(checked = state.showSource, onCheckedChange = onShowSource)
                }
                SettingRow("说话人区分", "需下载约 27 MB CAM++ 声纹模型") {
                    Switch(
                        checked = state.speakerDiarization,
                        onCheckedChange = onSpeakerDiarization,
                    )
                }
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("字号 ${state.fontSize}sp", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = state.fontSize.toFloat(),
                        onValueChange = { onFontSize(it.toInt()) },
                        valueRange = 12f..28f,
                        steps = 15,
                    )
                }
            }
        }
        item {
            Column {
                Section("AI 助手")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI 助手", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "关键词 $keywordCount 个 · 知识库 ${if (state.knowledgeBaseEnabled) "已启用" else "未启用"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { aiOpen = true }) { Text("设置") }
                    }
                }
                if (aiOpen) {
                    AiAssistantDialog(
                        initialKeywords = state.aiKeywords,
                        initialAutoKeywords = state.aiAutoKeywords,
                        initialContext = state.aiContext,
                        initialPersonalization = state.aiPersonalization,
                        initialKnowledgeBase = state.knowledgeBaseEnabled,
                        initialKeywordLearning = state.aiKeywordLearning,
                        onDismiss = { aiOpen = false },
                        onSave = { keywords, auto, context, personalization, knowledgeBase, learning ->
                            onAiKeywords(keywords)
                            onAutoKeywords(auto)
                            onAiContext(context)
                            onPersonalization(personalization)
                            onKnowledgeBase(knowledgeBase)
                            onKeywordLearning(learning)
                            aiOpen = false
                        },
                    )
                }
            }
        }
        item {
            Column {
                Section("引擎与模型")
                TextButton(onClick = onOpenProviders) {
                    Text("引擎设置（ML Kit / 云端 API / 本地 NLLB）")
                    Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null)
                }
                TextButton(onClick = onOpenTextTranslate) {
                    Text("文本翻译")
                    Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null)
                }
                TextButton(onClick = onOpenPhotoTranslate) {
                    Text("拍照翻译（内置中俄 OCR）")
                    Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
        items(ModelCatalog.all, key = { it.id }) { pack ->
            ModelRow(
                pack = pack,
                state = state.downloads[pack.id],
                onDownload = { onDownload(pack) },
                onDelete = { onDeleteModel(pack) },
                onCancel = { onCancelModel(pack) },
            )
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            SettingRow("导出诊断日志", "定位卡顿/闪退，不含录音内容") {
                TextButton(onClick = onExportDiag) { Text("导出") }
            }
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            SettingRow("关于我们", "超库8331 · 中俄物流专线") {
                TextButton(onClick = onOpenAbout) { Text("查看") }
            }
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text(
                text = "离线同传 · 本机优先\n音频与记录只保存在本机；使用云端引擎时仅发送文本。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun Header() {
    Column(modifier = Modifier.padding(top = 18.dp)) {
        Text(
            text = "我的",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "用量、引擎与本地模型",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UsageCard(state: LiveUiState) {
    val minutes = state.totalSeconds / 60
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("已翻译时长", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = if (minutes >= 60) "${minutes / 60} 小时 ${minutes % 60} 分" else "$minutes 分钟",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("会话数", style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "${state.sessionCount}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        control()
    }
}

@Composable
private fun ModelRow(
    pack: ModelPack,
    state: DownloadState?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(pack.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${pack.kind.name} · ${pack.totalBytes / 1024 / 1024} MB · ${pack.license}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                state?.active == true -> {
                    Text("${(state.progress * 100).toInt()}%")
                    TextButton(onClick = onCancel) { Text("取消") }
                }
                state?.ready == true -> TextButton(onClick = onDelete) { Text("删除") }
                else -> Button(onClick = onDownload) { Text("下载") }
            }
        }
        if (state?.active == true) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
}
