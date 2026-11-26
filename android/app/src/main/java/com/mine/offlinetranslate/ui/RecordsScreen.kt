package com.mine.offlinetranslate.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mine.offlinetranslate.data.RecordSession
import com.mine.offlinetranslate.data.RecordKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordsScreen(
    records: List<RecordSession>,
    folders: List<String>,
    onStar: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onExport: (String) -> Unit,
    onBatchStar: (Set<String>, Boolean) -> Unit,
    onBatchDelete: (Set<String>) -> Unit,
    onBatchMove: (Set<String>, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var renameTarget by remember { mutableStateOf<RecordSession?>(null) }
    var deleteTarget by remember { mutableStateOf<RecordSession?>(null) }
    var detailTarget by remember { mutableStateOf<RecordSession?>(null) }
    var moveTarget by remember { mutableStateOf<Set<String>?>(null) }
    var confirmBatchDelete by remember { mutableStateOf(false) }

    val filtered = records.filter { record ->
        (folderFilter == null || record.folder == folderFilter) &&
            (
                query.isBlank() ||
                    record.title.contains(query, ignoreCase = true) ||
                    record.lines.any {
                        it.source.contains(query, ignoreCase = true) ||
                            it.translation?.contains(query, ignoreCase = true) == true
                    }
                )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "翻译记录",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                selectionMode = !selectionMode
                if (!selectionMode) selected = emptySet()
            }) {
                Text(if (selectionMode) "取消" else "选择")
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            item {
                FilterChip(
                    selected = folderFilter == null,
                    onClick = { folderFilter = null },
                    label = { Text("全部") },
                )
            }
            item {
                FilterChip(
                    selected = folderFilter == "",
                    onClick = { folderFilter = "" },
                    label = { Text("未分类") },
                )
            }
            items(folders) { folder ->
                FilterChip(
                    selected = folderFilter == folder,
                    onClick = { folderFilter = folder },
                    label = { Text(folder) },
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索记录") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filtered, key = { it.id }) { record ->
                RecordCard(
                    record = record,
                    selectionMode = selectionMode,
                    selected = record.id in selected,
                    onToggle = {
                        selected = if (record.id in selected) {
                            selected - record.id
                        } else {
                            selected + record.id
                        }
                    },
                    onOpen = { detailTarget = record },
                    onStar = { onStar(record.id) },
                    onRename = { renameTarget = record },
                    onMove = { moveTarget = setOf(record.id) },
                    onExport = { onExport(record.id) },
                    onDelete = { deleteTarget = record },
                )
            }
            if (filtered.isEmpty()) {
                item("empty") {
                    Text(
                        text = if (records.isEmpty()) {
                            "还没有记录\n开始一次同传或拍照翻译后会自动保存"
                        } else {
                            "没有匹配的记录"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 40.dp),
                    )
                }
            }
        }
        if (selectionMode && selected.isNotEmpty()) {
            Surface(tonalElevation = 3.dp, shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "已选 ${selected.size} 项",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    TextButton(onClick = { onBatchStar(selected, true) }) { Text("星标") }
                    TextButton(onClick = { onBatchStar(selected, false) }) { Text("取消星标") }
                    TextButton(onClick = { moveTarget = selected }) { Text("移动") }
                    TextButton(onClick = { confirmBatchDelete = true }) { Text("删除") }
                }
            }
        }
    }

    renameTarget?.let { record ->
        var title by remember(record.id) { mutableStateOf(record.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名记录") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text("名称") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(record.id, title)
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }

    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除记录") },
            text = { Text("确定删除「${record.title}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(record.id)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmBatchDelete = false },
            title = { Text("批量删除") },
            text = { Text("确定删除选中的 ${selected.size} 条记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onBatchDelete(selected)
                    selected = emptySet()
                    selectionMode = false
                    confirmBatchDelete = false
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatchDelete = false }) { Text("取消") }
            },
        )
    }

    moveTarget?.let { ids ->
        MoveFolderDialog(
            folders = folders,
            onDismiss = { moveTarget = null },
            onMove = { folder ->
                onBatchMove(ids, folder)
                moveTarget = null
                selected = emptySet()
                selectionMode = false
            },
        )
    }

    detailTarget?.let { record ->
        RecordDetailDialog(
            record = record,
            onDismiss = { detailTarget = null },
            onExport = { onExport(record.id) },
        )
    }
}

@Composable
private fun RecordCard(
    record: RecordSession,
    selectionMode: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onStar: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = if (selectionMode) onToggle else onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(formatTime(record.startedAt))
                        append(" · ").append(
                            if (record.kind == RecordKind.PHOTO) "拍照翻译" else "同传"
                        )
                        append(" · ").append(record.lines.size).append(" 段")
                        append(" · ").append(record.sourceLang).append(" → ").append(record.targetLang)
                        if (record.folder.isNotBlank()) append(" · ").append(record.folder)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                record.lines.firstOrNull()?.source?.let { first ->
                    Text(
                        text = first,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (!selectionMode) {
                IconButton(onClick = onStar) {
                    Icon(
                        imageVector = if (record.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = "星标",
                        tint = if (record.starred) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = { menu = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("移动到文件夹") },
                            onClick = { menu = false; onMove() },
                        )
                        DropdownMenuItem(
                            text = { Text("导出 / 分享") },
                            onClick = { menu = false; onExport() },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveFolderDialog(
    folders: List<String>,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
) {
    var newFolder by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到文件夹") },
        text = {
            Column {
                TextButton(onClick = { onMove("") }) { Text("未分类") }
                folders.forEach { folder ->
                    TextButton(onClick = { onMove(folder) }) { Text(folder) }
                }
                OutlinedTextField(
                    value = newFolder,
                    onValueChange = { newFolder = it },
                    label = { Text("新建文件夹") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (newFolder.isNotBlank()) onMove(newFolder.trim()) },
                enabled = newFolder.isNotBlank(),
            ) { Text("移动") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun RecordDetailDialog(
    record: RecordSession,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatTime(record.startedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(record.lines) { line ->
                        Column {
                            line.speaker?.let { speaker ->
                                Text(
                                    text = speaker,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(line.source, style = MaterialTheme.typography.bodyLarge)
                            line.translation?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onExport) { Text("导出 / 分享") }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
