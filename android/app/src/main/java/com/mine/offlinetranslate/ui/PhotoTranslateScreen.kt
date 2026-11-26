package com.mine.offlinetranslate.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mine.offlinetranslate.LiveUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PhotoTranslateScreen(
    state: LiveUiState,
    onStartPhoto: (Uri) -> Unit,
    onRecognize: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val task = state.photoTask
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val latestBitmap by rememberUpdatedState(bitmap)

    LaunchedEffect(task.imagePath) {
        val loaded = if (task.imagePath.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(task.imagePath) }
        }
        if (bitmap !== loaded) {
            bitmap?.takeIf { !it.isRecycled }?.recycle()
            bitmap = loaded
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            latestBitmap?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) onStartPhoto(uri)
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onStartPhoto(uri)
    }

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
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("拍照翻译", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "内置中俄 OCR · 自动识别并互译 · 当前任务自动保留",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (task.id.isNotBlank()) {
                TextButton(onClick = onClear) { Text("清空") }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            val image = bitmap
            if (image == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = null,
                        modifier = Modifier.height(48.dp),
                    )
                    Text("拍摄或选择包含中文、俄语文字的照片")
                }
            } else {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "待识别图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        val directory = File(context.cacheDir, "ocr-images").apply { mkdirs() }
                        val file = File.createTempFile("photo_", ".jpg", directory)
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                    }.onSuccess {
                        cameraUri = it
                        cameraLauncher.launch(it)
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                Text("拍照", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                Text("相册", modifier = Modifier.padding(start = 6.dp))
            }
        }

        Button(
            onClick = onRecognize,
            enabled = task.id.isNotBlank() && bitmap != null && !task.busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            if (task.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(
                text = if (task.busy) task.status else "识别并翻译",
                modifier = Modifier.padding(start = if (task.busy) 8.dp else 0.dp),
            )
        }

        Text(
            text = "识别与翻译成功后会写入记录。开始下一张图片会替换当前任务，历史记录仍会保留。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        task.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (task.sourceText.isNotBlank()) {
            ResultCard(
                title = "OCR 原文 · ${if (task.sourceLang == "ru") "俄语" else "中文"}",
                text = task.sourceText,
                onCopy = { clipboard.setText(AnnotatedString(task.sourceText)) },
            )
        }
        if (task.translatedText.isNotBlank()) {
            ResultCard(
                title = "译文",
                text = task.translatedText,
                highlighted = true,
                onCopy = { clipboard.setText(AnnotatedString(task.translatedText)) },
            )
        }
        Text(
            text = task.status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun ResultCard(
    title: String,
    text: String,
    highlighted: Boolean = false,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onCopy) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "复制")
                }
            }
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
