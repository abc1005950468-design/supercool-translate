package com.mine.offlinetranslate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.mine.offlinetranslate.LiveUiState

@Composable
fun TextTranslateScreen(
    state: LiveUiState,
    onTranslate: (String, (String) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
            }
            Column {
                Text("文本翻译", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${state.sourceLang} → ${state.targetLang}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("输入文本") },
            minLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
        Button(
            onClick = {
                if (input.isBlank()) return@Button
                loading = true
                onTranslate(input) {
                    output = it
                    loading = false
                }
            },
            enabled = !loading,
            modifier = Modifier.padding(top = 10.dp),
        ) {
            Icon(Icons.Rounded.Send, contentDescription = null)
            Text(if (loading) "翻译中…" else "翻译", modifier = Modifier.padding(start = 6.dp))
        }
        if (output.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("译文", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { clipboard.setText(AnnotatedString(output)) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "复制")
                        }
                    }
                    Text(output, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
