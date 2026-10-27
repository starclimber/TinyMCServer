package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import dev.tinymcserver.app.core.frp.FrpFormat
import dev.tinymcserver.app.core.frp.FrpManager
import dev.tinymcserver.app.core.frp.FrpState
import dev.tinymcserver.app.core.frp.FrpStore
import dev.tinymcserver.app.core.frp.FrpTunnel
import dev.tinymcserver.app.core.i18n.t

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrpScreen(nav: NavController) {
    val ctx = LocalContext.current
    var tunnels by remember { mutableStateOf(FrpStore.load(ctx)) }
    var pendingDelete by remember { mutableStateOf<FrpTunnel?>(null) }

    // 每次回到本页（编辑保存后）都重读
    val entry = nav.currentBackStackEntry
    androidx.compose.runtime.LaunchedEffect(entry) {
        tunnels = FrpStore.load(ctx)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("frp 内网穿透")) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("返回"))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate("frpEdit") }) {
                Icon(Icons.Filled.Add, contentDescription = t("新建隧道"))
            }
        },
    ) { pad ->
        if (tunnels.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t("还没有 frp 隧道"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t("点右下角 + 添加，把手机服务端映射到公网"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tunnels, key = { it.id }) { tun ->
                    FrpCard(
                        tunnel = tun,
                        onOpenConsole = { nav.navigate("frpConsole/${tun.id}") },
                        onEdit = { nav.navigate("frpEdit/${tun.id}") },
                        onDelete = { pendingDelete = tun },
                    )
                }
            }
        }
    }

    pendingDelete?.let { tun ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(t("删除隧道")) },
            text = { Text(t("确定删除「%s」？", tun.name.ifBlank { tun.serverAddr })) },
            confirmButton = {
                TextButton(onClick = {
                    FrpManager.stop(tun.id)
                    tunnels = FrpStore.remove(ctx, tun.id)
                    pendingDelete = null
                }) { Text(t("删除")) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(t("取消")) } },
        )
    }
}

@Composable
private fun FrpCard(
    tunnel: FrpTunnel,
    onOpenConsole: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val rt by FrpManager.runtime(tunnel.id).collectAsState()
    val (label, color) = when (rt.state) {
        FrpState.RUNNING -> t("运行中") to Color(0xFF66BB6A)
        FrpState.STARTING -> t("连接中") to Color(0xFFFFA726)
        FrpState.ERROR -> t("错误") to Color(0xFFEF5350)
        FrpState.STOPPED -> t("已停止") to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        Modifier.fillMaxWidth().clickable { onOpenConsole() },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tunnel.name.ifBlank { t("未命名隧道") },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${tunnel.serverAddr}:${tunnel.serverPort} → ${tunnel.localIp}:${tunnel.localPort}" +
                            (if (tunnel.remotePort > 0) " :${tunnel.remotePort}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(onClick = {}, label = { Text(label, color = color) })
            }
            Spacer(Modifier.height(4.dp))
            Text(
                t("格式 %s · %s", formatLabel(tunnel.format), if (tunnel.autoStart) t("关联启动") else t("手动启动")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rt.message.isNotBlank() && rt.state == FrpState.ERROR) {
                Text(
                    rt.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF5350),
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onOpenConsole) {
                    Icon(Icons.Filled.Terminal, contentDescription = t("控制台"))
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, t("编辑")) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, t("删除")) }
            }
        }
    }
}

private fun formatLabel(f: FrpFormat): String = when (f) {
    FrpFormat.GUI -> t("图形")
    FrpFormat.TOML -> "TOML"
    FrpFormat.INI -> "INI"
}
