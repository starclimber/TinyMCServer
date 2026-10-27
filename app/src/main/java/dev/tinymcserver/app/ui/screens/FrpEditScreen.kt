package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.frp.FrpFormat
import dev.tinymcserver.app.core.frp.FrpManager
import dev.tinymcserver.app.core.frp.FrpStore
import dev.tinymcserver.app.core.frp.FrpTunnel
import dev.tinymcserver.app.core.i18n.t
import dev.tinymcserver.app.ui.components.FieldCard
import dev.tinymcserver.app.ui.components.SettingRow
import dev.tinymcserver.app.core.storage.InstanceStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrpEditScreen(nav: NavController, id: String?) {
    val ctx = LocalContext.current
    val existing = remember(id) {
        FrpStore.load(ctx).firstOrNull { it.id == id } ?: FrpTunnel()
    }
    var s by remember { mutableStateOf(existing) }
    val instances = remember { InstanceStore.load(ctx) }
    var showInstancePicker by remember { mutableStateOf(false) }

    val boundName = instances.firstOrNull { it.id == s.instanceId }?.config?.name
        ?: (if (s.instanceId.isBlank()) t("不绑定") else t("未找到"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (id == null) t("新建隧道") else t("编辑隧道")) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, t("返回"))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = s.name,
                onValueChange = { s = s.copy(name = it) },
                label = { Text(t("隧道名称")) },
                placeholder = { Text(t("例如：家里的 MC 服")) },
                modifier = Modifier.fillMaxWidth(),
            )

            // 绑定服务器实例：与设置里「语言」那一项同样的样式（标题 + 当前值 + 右箭头），点整行弹窗
            FieldCard {
                SettingRow(
                    title = t("绑定服务器实例"),
                    subtitle = boundName,
                    onClick = { showInstancePicker = true },
                )
            }

            Text(t("配置方式"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = s.format == FrpFormat.GUI,
                    onClick = { s = s.copy(format = FrpFormat.GUI) },
                    label = { Text(t("图形界面")) },
                )
                FilterChip(
                    selected = s.format == FrpFormat.TOML,
                    onClick = { s = s.copy(format = FrpFormat.TOML) },
                    label = { Text("TOML") },
                )
                FilterChip(
                    selected = s.format == FrpFormat.INI,
                    onClick = { s = s.copy(format = FrpFormat.INI) },
                    label = { Text("INI") },
                )
            }

            if (s.format == FrpFormat.GUI) {
                OutlinedTextField(
                    value = s.serverAddr,
                    onValueChange = { s = s.copy(serverAddr = it) },
                    label = { Text(t("frp 服务器地址")) },
                    placeholder = { Text("frp.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = s.serverPort.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { s = s.copy(serverPort = it) } },
                        label = { Text(t("服务端口")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = s.remotePort.toString(),
                        onValueChange = { v -> v.toIntOrNull()?.let { s = s.copy(remotePort = it) } },
                        label = { Text(t("远程端口")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = s.authToken,
                    onValueChange = { s = s.copy(authToken = it) },
                    label = { Text(t("认证 Token（可选）")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = s.localPort.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.let { s = s.copy(localPort = it) } },
                    label = { Text(t("本地端口（服务器端口）")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = s.rawConfig,
                    onValueChange = { s = s.copy(rawConfig = it) },
                    label = { Text(if (s.format == FrpFormat.TOML) t("TOML 配置") else t("INI 配置")) },
                    minLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                t("本地地址固定为 127.0.0.1（只映射本机服务）"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val noBinding = s.instanceId.isBlank()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t("服务器启动后自动启动"),
                        color = if (noBinding) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (noBinding) t("需先绑定服务器实例才能关联启动")
                        else t("关联启动：绑定的服务器 RUNNING 时自动拉起 frp"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (noBinding) 0.5f else 1f),
                    )
                }
                Switch(
                    checked = s.autoStart && !noBinding,
                    enabled = !noBinding,
                    onCheckedChange = { s = s.copy(autoStart = it) },
                )
            }

            Spacer(Modifier.height(4.dp))
            var errorMsg by remember { mutableStateOf<String?>(null) }
            Button(
                onClick = {
                    val err = FrpManager.validateLoopback(s)
                    if (err != null) {
                        errorMsg = err
                    } else {
                        // 没绑定实例时，关联启动无意义，保存时一并关掉
                        val toSave = if (s.instanceId.isBlank()) s.copy(autoStart = false) else s
                        FrpStore.upsert(ctx, toSave)
                        nav.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(t("保存")) }

            if (errorMsg != null) {
                Text(
                    errorMsg!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showInstancePicker) {
        AlertDialog(
            onDismissRequest = { showInstancePicker = false },
            title = { Text(t("绑定服务器实例")) },
            text = {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = s.instanceId.isBlank(),
                            onClick = {
                                s = s.copy(instanceId = "")
                                showInstancePicker = false
                            },
                        )
                        Text(t("不绑定"))
                    }
                    instances.forEach { inst ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = s.instanceId == inst.id,
                                onClick = {
                                    s = s.copy(instanceId = inst.id)
                                    showInstancePicker = false
                                },
                            )
                            Column {
                                Text(inst.config.name)
                                Text(
                                    t("端口 %s", inst.config.port),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInstancePicker = false }) { Text(t("关闭")) }
            },
        )
    }
}

