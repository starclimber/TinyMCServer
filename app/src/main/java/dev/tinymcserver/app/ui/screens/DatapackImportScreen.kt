package dev.tinymcserver.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.datapack.DatapackSearch
import dev.tinymcserver.app.core.datapack.Datapacks
import dev.tinymcserver.app.core.i18n.t
import dev.tinymcserver.app.ui.AppViewModel
import dev.tinymcserver.app.ui.components.FieldCard
import dev.tinymcserver.app.ui.components.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 数据包导入。四个页签与「插件」页一一对应：
 *   Tab1 本地导入 · Tab2 链接下载 · Tab3 在线搜索(Modrinth) · Tab4 已安装
 *
 * 数据包放在 `<实例目录>/<level-name>/datapacks/`，跟世界走；Vanilla 也支持。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatapackImportScreen(vm: AppViewModel, nav: NavController, id: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val inst = vm.instances.collectAsState().value.firstOrNull { it.id == id }
    if (inst == null) {
        Text(t("实例不存在"), Modifier.padding(16.dp))
        return
    }

    var tab by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }

    // —— 本地 / 链接 ——
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    // —— 在线搜索 ——
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<dev.tinymcserver.app.core.plugin.PluginSearch.Hit>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var doneIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val installed = remember(tick, id) { Datapacks.list(ctx, id) }
    val dpDir = remember(tick, id) { Datapacks.dir(ctx, id) }
    val worldName = remember(tick, id) { Datapacks.levelName(ctx, id) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        status = ""
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { Datapacks.importTo(ctx, id, uri) }
            }.onSuccess {
                status = t("已导入：%s", it)
                vm.toast(t("数据包已导入：%s（重启服务端后生效）", it))
            }.onFailure {
                status = t("导入失败：%s", it.message)
            }
            busy = false
            tick++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("数据包")) },
                navigationIcon = {
                    IconButton(onClick = { if (nav.previousBackStackEntry != null) nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = t("返回"))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                listOf(t("本地导入"), t("链接下载"), t("在线搜索"), t("已安装")).forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = {
                            Text(
                                label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                    )
                }
            }

            when (tab) {
                // ---------------- Tab1 本地导入 ----------------
                0 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                ) {
                    SectionTitle(t("从本地文件导入"))
                    FieldCard {
                        Text(
                            t("选择手机里的数据包（.zip，单个 .mcfunction 也可以），会复制到本实例 %s 世界的 datapacks 目录。", worldName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { picker.launch(arrayOf("*/*")) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        ) { Text(t("选择数据包文件")) }
                        if (status.isNotEmpty()) {
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                // ---------------- Tab2 链接下载 ----------------
                1 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                ) {
                    SectionTitle(t("从链接下载"))
                    FieldCard {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(t("数据包直链（.zip）")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                val link = url.trim()
                                if (!link.startsWith("http://") && !link.startsWith("https://")) {
                                    status = t("请输入以 http(s) 开头的链接")
                                    return@Button
                                }
                                busy = true
                                status = t("下载中…")
                                scope.launch {
                                    val res = runCatching {
                                        Datapacks.download(ctx, id, link)
                                    }
                                    busy = false
                                    tick++
                                    res.onSuccess {
                                        status = t("已下载：%s", it)
                                        vm.toast(t("数据包已下载：%s（重启服务端后生效）", it))
                                    }.onFailure {
                                        status = t("下载失败：%s", it.message)
                                    }
                                }
                            },
                            enabled = !busy && url.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        ) { Text(if (busy) t("下载中…") else t("下载")) }
                        if (status.isNotEmpty()) {
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                // ---------------- Tab3 在线搜索 ----------------
                2 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                ) {
                    SectionTitle(t("在线搜索安装"))
                    FieldCard {
                        Text(
                            t("来源 Modrinth · 目标 %s %s · 数据包与 Vanilla / Paper / Purpur / Folia 都通用", inst.config.type.display, inst.config.mcVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = query, onValueChange = { query = it },
                                label = { Text(t("搜索数据包")) }, singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Button(onClick = {
                                if (query.isBlank()) return@Button
                                searching = true
                                scope.launch {
                                    runCatching {
                                        DatapackSearch.search(query, inst.config.mcVersion)
                                    }.onSuccess { results = it; searched = true }
                                        .onFailure { vm.toast(t("搜索失败：%s", it.message)) }
                                    searching = false
                                }
                            }) { Icon(Icons.Filled.Search, contentDescription = t("搜索")) }
                        }
                        if (searching) {
                            Text(
                                t("搜索中…"),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        if (!searching && !searched) {
                            Text(
                                t("输入关键词后点搜索。装好后重启服务端生效。"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    results.forEach { hit ->
                        val isDownloading = downloadingId == hit.projectId
                        val isDone = doneIds.contains(hit.projectId)
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(hit.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        hit.description.take(110),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        t("下载 %s", hit.downloads),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                when {
                                    isDownloading -> CircularProgressIndicator(
                                        Modifier.size(22.dp), strokeWidth = 2.dp,
                                    )
                                    isDone -> Icon(
                                        Icons.Filled.Check,
                                        contentDescription = t("已下载"),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    else -> IconButton(onClick = {
                                        downloadingId = hit.projectId
                                        vm.downloadDatapack(
                                            inst = inst,
                                            projectId = hit.projectId,
                                            title = hit.title,
                                        ) { ok ->
                                            downloadingId = null
                                            if (ok) {
                                                doneIds = doneIds + hit.projectId
                                                tick++
                                            }
                                        }
                                    }) { Icon(Icons.Filled.Download, contentDescription = t("下载")) }
                                }
                            }
                        }
                    }
                }

                // ---------------- Tab4 已安装 ----------------
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                ) {
                    SectionTitle(t("已安装数据包"))
                    FieldCard {
                        if (installed.isEmpty()) {
                            Text(
                                t("（暂无数据包）"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        installed.forEach { f ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        humanSizeDp(f),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = {
                                    runCatching { f.delete() }
                                    tick++
                                    vm.toast(t("已删除 %s", f.name))
                                }) { Text(t("删除")) }
                            }
                        }
                    }
                    Text(
                        t("目录：%s", dpDir.absolutePath),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        t("提示：数据包装好后需要重启服务端才会生效。不要用 /reload —— 它会重新加载数据包，遇到不兼容的包会让服务端直接崩在加载阶段。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

private fun humanSizeDp(f: File): String = when {
    f.length() < 1024 -> "${f.length()} B"
    f.length() < 1024 * 1024 -> "${f.length() / 1024} KB"
    else -> String.format("%.1f MB", f.length() / 1048576.0)
}
