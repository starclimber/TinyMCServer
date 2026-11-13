package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.i18n.t
import dev.tinymcserver.app.core.trigger.Trigger
import dev.tinymcserver.app.core.trigger.TriggerEngine
import dev.tinymcserver.app.core.trigger.TriggerSpecs
import dev.tinymcserver.app.core.trigger.TriggerStore

/** 触发器列表（控制台「好玩的」入口进入） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerListScreen(nav: NavController, instanceId: String) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(TriggerStore.load(ctx, instanceId)) }
    var pendingDelete by remember { mutableStateOf<Trigger?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("好玩的")) },
                navigationIcon = {
                    IconButton(onClick = { if (nav.previousBackStackEntry != null) nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = t("返回"))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate("triggerEdit/$instanceId/new") }) {
                Icon(Icons.Filled.Add, contentDescription = t("新建触发器"))
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
        if (list.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t("还没有触发器，点右下角加号新建"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(list, key = { it.id }) { tr ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { nav.navigate("triggerEdit/$instanceId/${tr.id}") }
                                .padding(start = 14.dp, end = 6.dp, top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tr.name.ifBlank { t("未命名触发器") },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    summary(tr),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = tr.enabled,
                                onCheckedChange = { v ->
                                    list = TriggerStore.upsert(ctx, instanceId, tr.copy(enabled = v))
                                    TriggerEngine.reload(ctx, instanceId)
                                },
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        ) {
                            IconButton(onClick = { nav.navigate("triggerLog/$instanceId") }) {
                                Icon(Icons.Filled.Receipt, contentDescription = t("触发器日志"))
                            }
                            IconButton(onClick = { nav.navigate("triggerEdit/$instanceId/${tr.id}") }) {
                                Icon(Icons.Filled.Edit, contentDescription = t("编辑"))
                            }
                            IconButton(onClick = { pendingDelete = tr }) {
                                Icon(Icons.Filled.Delete, contentDescription = t("删除"))
                            }
                        }
                    }
                }
            }
        }
        }
    }

    pendingDelete?.let { tr ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(t("删除触发器")) },
            text = { Text(t("确定删除「%s」吗？", tr.name.ifBlank { t("未命名触发器") })) },
            confirmButton = {
                TextButton(onClick = {
                    list = TriggerStore.remove(ctx, instanceId, tr.id)
                    TriggerEngine.reload(ctx, instanceId)
                    pendingDelete = null
                }) { Text(t("删除")) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(t("取消")) } },
        )
    }
}

private fun summary(tr: Trigger): String {
    val e = tr.event
    val label = t(TriggerSpecs.event(e.type).label)
    val scope = when {
        e.scope == "exact" && e.value.isNotBlank() -> " · ${e.value}"
        e.scope == "contains" && e.value.isNotBlank() -> t(" · 名字包含 %s", e.value)
        else -> ""
    }
    val extra = when (e.type) {
        "player_count" -> t(" · %s%s 人", e.extra["op"] ?: ">=", e.extra["count"] ?: "1")
        "timer" -> if ((e.extra["mode"] ?: "interval") == "daily") {
            t(" · 每天 %s", e.extra["daily_at"] ?: "20:00")
        } else {
            t(" · 每 %s 秒", e.extra["interval_sec"] ?: "1800")
        }
        "chat" -> e.extra["keyword"]?.takeIf { it.isNotBlank() }?.let { t(" · 含「%s」", it) } ?: ""
        "advancement" -> e.extra["keyword"]?.takeIf { it.isNotBlank() }?.let { t(" · 含「%s」", it) } ?: ""
        else -> ""
    }
    return label + scope + extra
}
