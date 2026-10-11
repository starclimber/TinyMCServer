package dev.tinymcserver.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.i18n.t
import dev.tinymcserver.app.core.update.ReleaseNote
import dev.tinymcserver.app.core.update.UpdateChecker
import dev.tinymcserver.app.core.update.UpdateResult
import dev.tinymcserver.app.ui.components.FieldCard
import dev.tinymcserver.app.ui.components.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface UpdateUiState {
    data object Checking : UpdateUiState
    data class Done(val result: UpdateResult) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion = remember { UpdateChecker.currentVersion(ctx) }
    var state by remember { mutableStateOf<UpdateUiState>(UpdateUiState.Checking) }
    var nonce by remember { mutableStateOf(0) }

    LaunchedEffect(nonce) {
        state = UpdateUiState.Checking
        state = runCatching {
            withContext(Dispatchers.IO) { UpdateChecker.check(currentVersion) }
        }.fold(
            onSuccess = { UpdateUiState.Done(it) },
            onFailure = { UpdateUiState.Failed(it.message ?: "") },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("检查更新")) },
                navigationIcon = {
                    IconButton(onClick = { if (nav.previousBackStackEntry != null) nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = t("返回"))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            SectionTitle(t("版本"))
            FieldCard {
                Text(
                    t("当前版本 %s", currentVersion),
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            when (val s = state) {
                is UpdateUiState.Checking -> {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(t("检查中…"))
                    }
                }

                is UpdateUiState.Failed -> {
                    SectionTitle(t("检查失败"))
                    FieldCard {
                        Text(
                            t("检查更新失败：%s", s.message),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { nonce++ },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(t("重试")) }
                    }
                }

                is UpdateUiState.Done -> {
                    val r = s.result
                    if (!r.hasUpdate) {
                        SectionTitle(t("状态"))
                        FieldCard {
                            Text(
                                t("已是最新版本（%s）", r.currentVersion),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            OutlinedButton(
                                onClick = { nonce++ },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) { Text(t("重新检查")) }
                        }
                    } else {
                        SectionTitle(t("发现新版本"))
                        FieldCard {
                            Text(
                                t("当前版本 %s → 最新版本 %s", r.currentVersion, r.latestVersion),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Button(
                                onClick = {
                                    val url = r.apkUrl ?: r.pageUrl
                                    runCatching {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) { Text(t("前往下载")) }
                            Text(
                                if (r.apkUrl != null) t("将打开 APK 下载链接；下载后覆盖安装即可。")
                                else t("将打开 Release 页面；下载 APK 后覆盖安装即可。"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }

                        SectionTitle(t("更新日志"))
                        r.releases.forEach { note -> ReleaseCard(note) }

                        OutlinedButton(
                            onClick = { nonce++ },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text(t("重新检查")) }
                    }
                }
            }

            Text(" ", Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun ReleaseCard(note: ReleaseNote) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${note.tag}   ${note.title}".trim(),
                style = MaterialTheme.typography.titleSmall,
            )
            if (note.publishedAt.isNotBlank()) {
                Text(
                    note.publishedAt.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (note.body.isNotBlank()) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(
                    // Release 正文是 Markdown，这里做纯文本展示：去掉标题标记，保留内容
                    note.body.replace("\r\n", "\n").lines()
                        .joinToString("\n") { line ->
                            line.removePrefix("### ").removePrefix("## ").removePrefix("# ")
                        },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Default,
                    fontSize = 12.sp,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}
