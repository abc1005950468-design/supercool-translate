package com.mine.offlinetranslate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog

private const val KEYWORD_LIMIT = 2000
private const val CONTEXT_LIMIT = 800

private const val TAB_ALL = 0
private const val TAB_MANUAL = 1
private const val TAB_AUTO = 2

private fun mergeKeywords(manual: String, auto: String): String =
    (manual + "," + auto)
        .split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(", ")

@Composable
fun AiAssistantDialog(
    initialKeywords: String,
    initialAutoKeywords: String,
    initialContext: String,
    initialPersonalization: String,
    initialKnowledgeBase: Boolean,
    initialKeywordLearning: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        keywords: String,
        autoKeywords: String,
        context: String,
        personalization: String,
        knowledgeBase: Boolean,
        keywordLearning: Boolean,
    ) -> Unit,
) {
    var manual by remember { mutableStateOf(initialKeywords) }
    var auto by remember { mutableStateOf(initialAutoKeywords) }
    var context by remember { mutableStateOf(initialContext) }
    var personalization by remember { mutableStateOf(initialPersonalization) }
    var knowledgeBase by remember { mutableStateOf(initialKnowledgeBase) }
    var keywordLearning by remember { mutableStateOf(initialKeywordLearning) }
    var tab by remember { mutableStateOf(TAB_ALL) }
    var plusHint by remember { mutableStateOf<String?>(null) }

    val merged = mergeKeywords(manual, auto)
    val displayText = when (tab) {
        TAB_MANUAL -> manual
        TAB_AUTO -> auto
        else -> merged
    }
    val keywordCount = when (tab) {
        TAB_MANUAL -> manual.length
        TAB_AUTO -> auto.length
        else -> merged.length
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "AI 助手",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }
                Text(
                    text = "关键词与上下文会随翻译请求发送给所选引擎，用于提升术语和场景准确度。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 知识库
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("知识库", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (knowledgeBase) "已启用" else "未启用",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = knowledgeBase, onCheckedChange = { knowledgeBase = it })
                    }

                    HorizontalDivider()

                    // 关键词
                    Text(
                        text = "关键词",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = tab == TAB_ALL,
                            onClick = { tab = TAB_ALL; plusHint = null },
                            label = { Text("全部") },
                        )
                        FilterChip(
                            selected = tab == TAB_MANUAL,
                            onClick = { tab = TAB_MANUAL; plusHint = null },
                            label = { Text("手动添加") },
                        )
                        FilterChip(
                            selected = tab == TAB_AUTO,
                            onClick = { tab = TAB_AUTO; plusHint = null },
                            label = { Text("自动添加") },
                        )
                    }

                    OutlinedTextField(
                        value = displayText,
                        onValueChange = {
                            if (tab == TAB_MANUAL) {
                                manual = it.take(KEYWORD_LIMIT)
                            }
                        },
                        readOnly = tab != TAB_MANUAL,
                        label = {
                            Text(
                                when (tab) {
                                    TAB_MANUAL -> "手动关键词（逗号或换行分隔）"
                                    TAB_AUTO -> "自动关键词（由 AI 学习生成）"
                                    else -> "全部关键词（手动 + 自动 预览）"
                                }
                            )
                        },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$keywordCount/$KEYWORD_LIMIT",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                when (tab) {
                                    TAB_MANUAL -> manual = (manual + "\n").take(KEYWORD_LIMIT)
                                    TAB_AUTO -> plusHint = "由 AI 学习生成"
                                    else -> {
                                        tab = TAB_MANUAL
                                        manual = (manual + "\n").take(KEYWORD_LIMIT)
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "添加")
                        }
                    }
                    if (plusHint != null) {
                        Text(
                            text = plusHint!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI 关键词学习", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "开启后 AI 会从对话中学习关键词，写入“自动添加”。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = keywordLearning, onCheckedChange = { keywordLearning = it })
                    }

                    HorizontalDivider()

                    // 上下文
                    Text(
                        text = "上下文",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = context,
                        onValueChange = { context = it.take(CONTEXT_LIMIT) },
                        label = { Text("上下文（职业、场景、会议主题）") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "${context.length}/$CONTEXT_LIMIT",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    HorizontalDivider()

                    // 个性化
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "个性化（由 AI 生成）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { personalization = "" }) { Text("清空") }
                    }
                    OutlinedTextField(
                        value = personalization,
                        onValueChange = { personalization = it },
                        label = { Text("个性化摘要") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "仅保存在本机，可随时清空。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                                manual.trim(),
                                auto.trim(),
                                context.trim(),
                                personalization.trim(),
                                knowledgeBase,
                                keywordLearning,
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
