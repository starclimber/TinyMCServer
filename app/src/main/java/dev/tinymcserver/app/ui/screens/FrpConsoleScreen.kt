package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.frp.FrpManager
import dev.tinymcserver.app.core.frp.FrpState
import dev.tinymcserver.app.core.frp.FrpStore
import dev.tinymcserver.app.core.frp.FrpTunnel
import dev.tinymcserver.app.core.i18n.t

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrpConsoleScreen(nav: NavController, id: String) {
    val ctx = LocalContext.current
    var tunnel by remember(id) {
        mutableStateOf(FrpStore.load(ctx).firstOrNull { it.id == id })
    }
    val rt by FrpManager.runtime(id).collectAsState()
    val listState = rememberLazyListState()
    val running = rt.state == FrpState.RUNNING || rt.state == FrpState.STARTING
    var startError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(rt.log.size) {
        if (rt.log.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(rt.log.size - 1) }
        }
    }

    val (label, color) = when (rt.state) {
        FrpState.RUNNING -> t("运行中") to Color(0xFF66BB6A)
        FrpState.STARTING -> t("连接中") to Color(0xFFFFA726)
        FrpState.ERROR -> t("错误") to Color(0xFFEF5350)
        FrpState.STOPPED -> t("已停止") to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tunnel?.name?.ifBlank { t("未命名隧道") } ?: t("frp 控制台"))
                        Text(
                            "${tunnel?.serverAddr ?: ""}:${tunnel?.serverPort ?: 0} → 127.0.0.1:${tunnel?.localPort ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("返回"))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // 控制栏
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = {}, label = { Text(label, color = color) })
                Spacer(Modifier.weight(1f))
                if (running) {
                    OutlinedButton(onClick = { FrpManager.stop(id) }) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Text(t("停止"), modifier = Modifier.padding(start = 6.dp))
                    }
                } else {
                    Button(
                        onClick = {
                            val t2 = tunnel ?: return@Button
                            val err = FrpManager.validateLoopback(t2)
                            if (err != null) {
                                startError = err
                            } else {
                                startError = null
                                FrpManager.start(ctx, t2)
                            }
                        },
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text(t("启动"), modifier = Modifier.padding(start = 6.dp))
                    }
                }
                IconButton(onClick = { FrpManager.clearLog(id) }) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = t("清屏"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { nav.navigate("frpEdit/$id") }) {
                    Icon(Icons.Filled.Edit, contentDescription = t("编辑"))
                }
            }

            if (startError != null) {
                Text(
                    startError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            if (rt.message.isNotBlank() && rt.state == FrpState.ERROR) {
                Text(
                    rt.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF5350),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            // 日志区
            Card(
                Modifier.fillMaxSize().padding(12.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                if (rt.log.isEmpty()) {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            t("暂无日志"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            t("点上面的「启动」连接 frp 服务器"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF121212))
                            .padding(10.dp),
                    ) {
                        items(rt.log) { line ->
                            Text(
                                line,
                                color = if (line.contains("error", true) || line.contains("失败"))
                                    Color(0xFFFF6E6E) else Color(0xFFD0D0D0),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
