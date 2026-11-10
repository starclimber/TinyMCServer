package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.i18n.t
import dev.tinymcserver.app.core.model.InstanceState
import dev.tinymcserver.app.core.player.PlayerManager
import dev.tinymcserver.app.core.server.ServerManager
import dev.tinymcserver.app.ui.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 玩家管理：只显示**当前在线**的玩家（日志里 joined / left 实时维护）。
 * 每个玩家一张卡片，右侧是三个真正的按钮（白名单 / OP / 封禁）。
 * 没启动服务端或没有玩家时，提示文案在页面正中。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerManageScreen(vm: AppViewModel, nav: NavController, id: String) {
    val ctx = LocalContext.current
    val inst = vm.instances.collectAsState().value.firstOrNull { it.id == id }
    if (inst == null) {
        Text(t("实例不存在"), Modifier.padding(16.dp))
        return
    }
    val controller = remember(id) { ServerManager.controller(ctx, inst) }
    val runtime = controller.state.collectAsState().value
    val scope = rememberCoroutineScope()

    var tick by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }

    val running = runtime.state == InstanceState.RUNNING ||
        runtime.state == InstanceState.STARTING
    val online = runtime.onlinePlayers
    val filtered = online.filter {
        applied.isBlank() || it.contains(applied, ignoreCase = true)
    }

    // 名单状态从文件读取，点按钮后稍等再重读一次
    val white = remember(tick) { PlayerManager.whitelist(ctx, id).map { it.name }.toSet() }
    val ops = remember(tick) { PlayerManager.ops(ctx, id).map { it.name }.toSet() }
    val banned = remember(tick) { PlayerManager.banned(ctx, id).map { it.name }.toSet() }
    val refreshSoon: () -> Unit = { scope.launch { delay(400); tick++ } }

    val hint = when {
        !running -> t("请启动服务器后再试")
        filtered.isEmpty() -> t("当前暂无玩家")
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("玩家管理")) },
                navigationIcon = {
                    IconButton(onClick = { if (nav.previousBackStackEntry != null) nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = t("返回"))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isBlank()) applied = ""
                },
                label = { Text(t("搜索玩家")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { applied = query.trim() }),
                trailingIcon = {
                    IconButton(onClick = { applied = query.trim() }) {
                        Icon(Icons.Filled.Search, contentDescription = t("搜索玩家"))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (hint != null) {
                Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    items(filtered, key = { it }) { p ->
                        Card(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    p,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                PlayerAction(t("白名单"), p in white) {
                                    controller.sendCommand(
                                        if (p in white) PlayerManager.commandForWhitelistRemove(p)
                                        else PlayerManager.commandForWhitelistAdd(p)
                                    )
                                    refreshSoon()
                                }
                                Spacer(Modifier.width(6.dp))
                                PlayerAction("OP", p in ops) {
                                    controller.sendCommand(
                                        if (p in ops) PlayerManager.commandForDeop(p)
                                        else PlayerManager.commandForOp(p)
                                    )
                                    refreshSoon()
                                }
                                Spacer(Modifier.width(6.dp))
                                PlayerAction(t("封禁"), p in banned) {
                                    controller.sendCommand(
                                        if (p in banned) PlayerManager.commandForPardon(p)
                                        else PlayerManager.commandForBan(p)
                                    )
                                    refreshSoon()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 行内按钮：已在名单里用实心按钮，不在用描边按钮，一眼能看出是按钮 */
@Composable
private fun PlayerAction(text: String, active: Boolean, onClick: () -> Unit) {
    val contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    val label: @Composable () -> Unit = {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
    if (active) {
        Button(
            onClick = onClick,
            contentPadding = contentPadding,
            modifier = Modifier.height(36.dp),
        ) { label() }
    } else {
        OutlinedButton(
            onClick = onClick,
            contentPadding = contentPadding,
            modifier = Modifier.height(36.dp),
        ) { label() }
    }
}
