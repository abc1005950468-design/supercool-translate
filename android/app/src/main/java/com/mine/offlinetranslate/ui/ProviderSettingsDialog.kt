package com.mine.offlinetranslate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mine.offlinetranslate.DownloadState
import com.mine.offlinetranslate.core.AsrMode
import com.mine.offlinetranslate.core.CloudConfig
import com.mine.offlinetranslate.core.ModelCatalog
import com.mine.offlinetranslate.core.ModelKind
import com.mine.offlinetranslate.core.ModelPack
import com.mine.offlinetranslate.core.MtMode
import com.mine.offlinetranslate.core.ProviderConfig

private enum class SettingsPage { MAIN, MT, ASR, LID, MODELS }

@Composable
fun ProviderSettingsDialog(
    initial: ProviderConfig,
    downloads: Map<String, DownloadState>,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig) -> Unit,
    onCloudChange: (CloudConfig, Boolean) -> Boolean,
    onDownload: (ModelPack) -> Unit,
    onDownloadRecommended: () -> Unit,
    onImport: (ModelPack) -> Unit,
    onDelete: (ModelPack) -> Unit,
    onCancel: (ModelPack) -> Unit,
) {
    var page by remember { mutableStateOf(SettingsPage.MAIN) }
    var asrMode by remember { mutableStateOf(initial.asr) }
    var asrPackId by remember { mutableStateOf(initial.asrPackId) }
    var mtMode by remember { mutableStateOf(initial.mt) }
    var llmPackId by remember { mutableStateOf(initial.llmPackId) }
    var lidPackId by remember { mutableStateOf(initial.lidPackId) }
    var zhSentenceRefine by remember { mutableStateOf(initial.zhSentenceRefine) }
    var ruSentenceRefine by remember { mutableStateOf(initial.ruSentenceRefine) }
    var ruRefinerId by remember { mutableStateOf(initial.ruRefinerId) }
    var languageRoute by remember { mutableStateOf(initial.languageRoute) }
    var baseUrl by remember { mutableStateOf(initial.cloud.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.cloud.apiKey) }
    var chatModel by remember { mutableStateOf(initial.cloud.chatModel) }

    var apiKeyError by remember { mutableStateOf(false) }

    fun saveCloudDraft(
        nextBaseUrl: String = baseUrl,
        nextApiKey: String = "",
        nextChatModel: String = chatModel,
        clearApiKey: Boolean = false,
    ): Boolean = onCloudChange(
        initial.cloud.copy(
            baseUrl = nextBaseUrl.trim(),
            apiKey = nextApiKey.trim(),
            chatModel = nextChatModel.trim(),
        ),
        clearApiKey,
    )

    val mtPacks = ModelCatalog.all.filter { it.kind == ModelKind.LLM || it.kind == ModelKind.MT }
    val asrPacks = ModelCatalog.all.filter { it.kind == ModelKind.ASR }
    val lidPack = ModelCatalog.byId(lidPackId)
        ?: ModelCatalog.LID_BASE.takeIf { downloads[it.id]?.ready == true }
        ?: ModelCatalog.LID_TINY

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (page != SettingsPage.MAIN) {
                        TextButton(onClick = { page = SettingsPage.MAIN }) { Text("← 返回") }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (page) {
                                SettingsPage.MAIN -> "引擎与模型"
                                SettingsPage.MT -> "翻译引擎"
                                SettingsPage.ASR -> "语音识别"
                                SettingsPage.LID -> "语种识别"
                                SettingsPage.MODELS -> "模型管理"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (page == SettingsPage.MAIN) {
                            Text(
                                text = "每类引擎独立设置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (page == SettingsPage.MAIN) {
                        TextButton(onClick = onDismiss) { Text("关闭") }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (page) {
                        SettingsPage.MAIN -> {
                            item {
                                Button(
                                    onClick = onDownloadRecommended,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("下载推荐中俄高精度组合")
                                }
                                Text(
                                    "NLLB 翻译 + 中文校正 + 俄语校正 + 高精度语种识别，约 2 GB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item {
                                NavRow("翻译引擎", mtSummary(mtMode, llmPackId)) { page = SettingsPage.MT }
                            }
                            item {
                                NavRow(
                                    "语音识别",
                                    if (asrMode == AsrMode.LOCAL) "本机流式" else "云端识别（未接入）",
                                ) { page = SettingsPage.ASR }
                            }
                            item {
                                NavRow("语种识别", "自动 · ${lidPack.label}") { page = SettingsPage.LID }
                            }
                            item {
                                NavRow("模型管理", "下载 / 删除全部模型") { page = SettingsPage.MODELS }
                            }
                        }

                        SettingsPage.MT -> {
                            item {
                                ChoiceRow("离线模型（手机内运行）", mtMode == MtMode.LOCAL_LLM) {
                                    mtMode = MtMode.LOCAL_LLM
                                }
                            }
                            if (mtMode == MtMode.LOCAL_LLM) {
                                item {
                                    Text("选择要使用的离线翻译模型", style = MaterialTheme.typography.labelLarge)
                                }
                                items(mtPacks, key = { "mt-${it.id}" }) { pack ->
                                    ModelChoiceRow(
                                        pack = pack,
                                        state = downloads[pack.id],
                                        selected = llmPackId == pack.id,
                                        onSelect = {
                                            mtMode = MtMode.LOCAL_LLM
                                            llmPackId = pack.id
                                        },
                                        onDownload = { onDownload(pack) },
                                        onImport = { onImport(pack) },
                                        onDelete = { onDelete(pack) },
                                        onCancel = { onCancel(pack) },
                                    )
                                }
                                val selected = ModelCatalog.byId(llmPackId)
                                if (selected != null && downloads[selected.id]?.ready != true) {
                                    item {
                                        Text(
                                            "当前选中的模型尚未下载，翻译会失败。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                            item { HorizontalDivider() }
                            item {
                                ChoiceRow("云端 API（OpenAI 兼容）", mtMode == MtMode.CLOUD) {
                                    mtMode = MtMode.CLOUD
                                }
                            }
                            if (mtMode == MtMode.CLOUD) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = baseUrl,
                                            onValueChange = {
                                                baseUrl = it
                                                saveCloudDraft(nextBaseUrl = it)
                                            },
                                            label = { Text("Base URL") },
                                            supportingText = { Text("如 https://api.deepseek.com/v1") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        OutlinedTextField(
                                            value = apiKey,
                                            onValueChange = {
                                                apiKey = it
                                                apiKeyError = it.isNotBlank() && !saveCloudDraft(nextApiKey = it)
                                            },
                                            label = { Text("API Key") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            isError = apiKeyError,
                                            singleLine = true,
                                            supportingText = {
                                                Text(
                                                    if (apiKeyError) "无法写入本机加密存储"
                                                    else "输入后自动加密保存，点按下方按钮可清除",
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        TextButton(
                                            onClick = {
                                                apiKeyError = !saveCloudDraft(clearApiKey = true)
                                                if (!apiKeyError) apiKey = ""
                                            },
                                        ) { Text("清除 API Key") }
                                        OutlinedTextField(
                                            value = chatModel,
                                            onValueChange = {
                                                chatModel = it
                                                saveCloudDraft(nextChatModel = it)
                                            },
                                            label = { Text("翻译模型名") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                            item { HorizontalDivider() }
                            item {
                                ChoiceRow("系统 ML Kit（需 Google 服务）", mtMode == MtMode.MLKIT) {
                                    mtMode = MtMode.MLKIT
                                }
                            }
                            item {
                                Text(
                                    "ML Kit 依赖 Google Play 服务；大陆无 GMS 手机请使用离线模型或云端 API。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        SettingsPage.ASR -> {
                            item {
                                ChoiceRow("本机流式识别", asrMode == AsrMode.LOCAL) {
                                    asrMode = AsrMode.LOCAL
                                }
                            }
                            item {
                                ChoiceRow("云端识别", asrMode == AsrMode.CLOUD) {
                                    asrMode = AsrMode.CLOUD
                                }
                            }
                            item {
                                Text(
                                    "云端识别尚未接入，选择后仍会提示；首发请使用本机流式。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item { HorizontalDivider() }
                            item {
                                Text(
                                    "实时字幕模型（低延迟，系统按语种自动加载）",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            items(
                                asrPacks.filter {
                                    it.id == ModelCatalog.RU_SMALL.id ||
                                        it.id == ModelCatalog.ZH_SMALL.id ||
                                        it.id == ModelCatalog.ZH_EN.id
                                },
                                key = { "streaming-${it.id}" },
                            ) { pack ->
                                ChoiceRow(
                                    label = "${pack.label} · ${pack.description}",
                                    selected = asrPackId == pack.id,
                                ) { asrPackId = pack.id }
                                PackStatusRow(pack, downloads[pack.id], onDownload, onImport, onDelete, onCancel)
                            }
                            item {
                                Text(
                                    "句末高精度识别（完整句子重新识别，准确率优先）",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                            item {
                                CheckboxRow(
                                    label = "中文：使用 Paraformer 高精度识别",
                                    checked = zhSentenceRefine,
                                ) { zhSentenceRefine = it }
                            }
                            item {
                                PackStatusRow(
                                    ModelCatalog.ZH_PARA,
                                    downloads[ModelCatalog.ZH_PARA.id],
                                    onDownload,
                                    onImport,
                                    onDelete,
                                    onCancel,
                                )
                            }
                            item {
                                CheckboxRow(
                                    label = "俄语：启用高精度句末识别",
                                    checked = ruSentenceRefine,
                                ) { ruSentenceRefine = it }
                            }
                            items(
                                listOf(ModelCatalog.RU_GIGAAM, ModelCatalog.RU_WHISPER),
                                key = { "ru-refiner-${it.id}" },
                            ) { pack ->
                                ChoiceRow(
                                    label = pack.label,
                                    selected = ruRefinerId == pack.id,
                                ) { ruRefinerId = pack.id }
                                PackStatusRow(
                                    pack,
                                    downloads[pack.id],
                                    onDownload,
                                    onImport,
                                    onDelete,
                                    onCancel,
                                )
                            }
                        }

                        SettingsPage.LID -> {
                            item {
                                Text(
                                    "识别语言模式",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            item {
                                ChoiceRow(
                                    label = "自动中俄（多阶段语种确认）",
                                    selected = languageRoute == "auto",
                                ) { languageRoute = "auto" }
                            }
                            item {
                                ChoiceRow(
                                    label = "固定中文（只加载中文识别）",
                                    selected = languageRoute == "zh",
                                ) { languageRoute = "zh" }
                            }
                            item {
                                ChoiceRow(
                                    label = "固定俄语（只加载俄语识别）",
                                    selected = languageRoute == "ru",
                                ) { languageRoute = "ru" }
                            }
                            item { HorizontalDivider() }
                            item {
                                Text(
                                    "语种识别自动进行，用于判断一句该走中文→俄语还是俄语→中文。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item {
                                Text(
                                    "选择语种识别模型",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            items(
                                listOf(ModelCatalog.LID_BASE, ModelCatalog.LID_TINY),
                                key = { "lid-${it.id}" },
                            ) { pack ->
                                ChoiceRow(
                                    label = "${pack.label} · ${pack.description}",
                                    selected = lidPackId == pack.id,
                                ) { lidPackId = pack.id }
                                PackStatusRow(pack, downloads[pack.id], onDownload, onImport, onDelete, onCancel)
                            }
                        }

                        SettingsPage.MODELS -> {
                            listOf(
                                ModelKind.ASR,
                                ModelKind.MT,
                                ModelKind.LID,
                                ModelKind.SPEAKER,
                                ModelKind.LLM,
                                ModelKind.TTS,
                            ).forEach { kind ->
                                val packs = ModelCatalog.all.filter { it.kind == kind }
                                if (packs.isNotEmpty()) {
                                    item { ModelSectionHeader(kind) }
                                    items(packs, key = { "all-${it.id}" }) { pack ->
                                        PackStatusRow(pack, downloads[pack.id], onDownload, onImport, onDelete, onCancel)
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = {
                            onSave(
                                initial.copy(
                                    asr = asrMode,
                                    asrPackId = asrPackId,
                                    mt = mtMode,
                                    llmPackId = llmPackId,
                                    lidPackId = lidPackId,
                                    zhSentenceRefine = zhSentenceRefine,
                                    ruSentenceRefine = ruSentenceRefine,
                                    ruRefinerId = ruRefinerId,
                                    languageRoute = languageRoute,
                                    cloud = initial.cloud.copy(
                                        baseUrl = baseUrl.trim(),
                                        apiKey = "",
                                        chatModel = chatModel.trim(),
                                    ),
                                )
                            )
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

private fun mtSummary(mode: MtMode, llmPackId: String): String = when (mode) {
    MtMode.LOCAL_LLM -> "离线 · ${ModelCatalog.byId(llmPackId)?.label ?: llmPackId}"
    MtMode.CLOUD -> "云端 API"
    MtMode.MLKIT -> "ML Kit（需 Google）"
}

private fun kindLabel(kind: ModelKind): String = when (kind) {
    ModelKind.ASR -> "语音识别"
    ModelKind.LLM -> "离线翻译大模型"
    ModelKind.MT -> "专用翻译模型"
    ModelKind.LID -> "语种识别"
    ModelKind.SPEAKER -> "说话人声纹"
    ModelKind.TTS -> "语音合成"
}

@Composable
private fun ModelSectionHeader(kind: ModelKind) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 6.dp),
    ) {
        Text(
            text = kindLabel(kind),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = kindDescription(kind),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
    HorizontalDivider()
}

private fun kindDescription(kind: ModelKind): String = when (kind) {
    ModelKind.ASR -> "流式字幕与句末高精度重识别，直接影响语音转文字准确率"
    ModelKind.LLM -> "通用离线语言模型，实验功能"
    ModelKind.MT -> "把识别后的文字翻译成目标语言"
    ModelKind.LID -> "先判断说的是中文还是俄语，再选择正确的语音识别器"
    ModelKind.SPEAKER -> "区分不同发言人，不负责语言判断和文字识别"
    ModelKind.TTS -> "把翻译结果读出来"
}

@Composable
private fun NavRow(title: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModelChoiceRow(
    pack: ModelPack,
    state: DownloadState?,
    selected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(modifier = Modifier.weight(1f)) {
                Text(pack.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${pack.totalBytes / 1024 / 1024} MB · ${pack.license} · ${statusText(state)}",
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
                else -> {
                    TextButton(onClick = onImport) { Text("导入") }
                    TextButton(onClick = onDownload) { Text("下载") }
                }
            }
        }
        if (state?.active == true) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PackStatusRow(
    pack: ModelPack,
    state: DownloadState?,
    onDownload: (ModelPack) -> Unit,
    onImport: (ModelPack) -> Unit,
    onDelete: (ModelPack) -> Unit,
    onCancel: (ModelPack) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(pack.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${pack.totalBytes / 1024 / 1024} MB · ${statusText(state)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            state?.active == true -> {
                Text("${(state.progress * 100).toInt()}%")
                TextButton(onClick = { onCancel(pack) }) { Text("取消") }
            }
            state?.ready == true -> TextButton(onClick = { onDelete(pack) }) { Text("删除") }
            else -> {
                TextButton(onClick = { onImport(pack) }) { Text("导入") }
                TextButton(onClick = { onDownload(pack) }) { Text("下载") }
            }
        }
    }
}

private fun statusText(state: DownloadState?): String = when {
    state?.active == true -> "下载中"
    state?.ready == true -> "已就绪"
    else -> "未下载"
}
