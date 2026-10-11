package dev.tinymcserver.app.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tinymcserver.app.core.i18n.t
import dev.tinymcserver.app.core.update.UpdateResult

/**
 * 发现新版本时的提示对话框：显示版本对照与更新说明，一键直达下载。
 * 「设置 → 检查更新」页里有完整版。
 *
 * @param onDismiss 关闭（"稍后"或点外部）
 * @param onToast 轻提示（例如没有浏览器）
 */
@Composable
fun UpdateDialog(
    result: UpdateResult,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val latestNote = result.releases.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("发现新版本 %s", latestNote?.tag ?: result.latestVersion)) },
        text = {
            Column {
                Text(
                    t("当前版本 %s → 最新版本 %s", result.currentVersion, result.latestVersion),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (result.releases.size > 1) {
                    Text(
                        t("（共 %s 个版本可更新）", result.releases.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val info0 = latestNote
                val notes = info0?.body.orEmpty()
                if (notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        // Release 正文是 Markdown，这里只做纯文本展示，去掉常见的标记噪音
                        notes
                            .replace("\r\n", "\n")
                            .lines()
                            .joinToString("\n") { it.removePrefix("### ").removePrefix("## ").removePrefix("# ") },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Default,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 优先直接给 APK；若该 Release 没挂 APK，则退回 Release 页面
                val url = result.apkUrl ?: result.pageUrl
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }.onFailure { onToast(t("没有可用的浏览器")) }
                onDismiss()
            }) { Text(t("前往下载")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("稍后")) }
        },
    )
}
