package com.mine.offlinetranslate.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mine.offlinetranslate.R

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

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
            Column {
                Text("关于我们", style = MaterialTheme.typography.headlineSmall)
                if (version.isNotBlank()) {
                    Text(
                        "超酷翻译 $version",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Image(
            painter = painterResource(R.drawable.app_logo_full),
            contentDescription = "超酷翻译",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 18.dp)
                .size(240.dp),
        )
        Text(
            text = "超库8331 中俄物流",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp),
        )
        Text(
            text = "专注中俄物流专线",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 18.dp),
        )

        Text(
            text = aboutText,
            style = MaterialTheme.typography.bodyLarge,
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "商务联系与交流",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("微信：18911370011", style = MaterialTheme.typography.bodyLarge)
                Text("邮箱：1005950468@qq.com", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "交流群：添加微信并注明“超酷翻译”，我们会邀请你进群。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString("18911370011"))
                        Toast.makeText(context, "微信号已复制", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Text("复制微信号", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_SENDTO,
                                    Uri.parse("mailto:1005950468@qq.com"),
                                )
                            )
                        }.onFailure {
                            Toast.makeText(context, "没有可用的邮件应用", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Email, contentDescription = null)
                    Text("发送邮件", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        Text(
            text = "本软件由超库8331团队独立开发。使用云端 API 时，仅将识别或输入的文字发送给所选择的模型服务商。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 18.dp, bottom = 28.dp),
        )
    }
}

private val aboutText = """
超酷翻译由超库8331团队独立开发。我们的本职工作是专注中俄物流专线，长期服务中俄之间的运输与沟通。

在实际业务中，我们深切体会到俄罗斯许多地区网络不稳定，甚至经常处于完全没有网络的环境，普通在线翻译工具往往无法使用。为了解决团队内部真实存在的沟通问题，我们开发了超酷翻译，并坚持把离线语音识别、离线 OCR 拍照翻译、文本翻译和本地模型放在优先位置。

软件最初只供内部使用，经过实际业务反复打磨后，现在愿意分享给更多有需要的人。我们不是一家单纯的软件公司，而是从真实跨境业务中走出来的使用者，因此功能设计更关注菜单、路牌、商品说明和物流沟通等实际场景。

如果你在使用中遇到问题、发现错误、有功能建议，或者希望商务合作、加入交流群，欢迎通过下方方式联系我们。我们会认真听取每一条反馈，并持续更新改进。
""".trimIndent()
