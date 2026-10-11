package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.MainActivity
import dev.tinymcserver.app.core.i18n.AppLanguage
import dev.tinymcserver.app.core.i18n.I18n
import dev.tinymcserver.app.core.i18n.t
import dev.tinymcserver.app.core.runtime.JreManager
import dev.tinymcserver.app.core.storage.Settings
import dev.tinymcserver.app.core.util.BatteryUtil
import dev.tinymcserver.app.ui.AppViewModel
import dev.tinymcserver.app.ui.components.FieldCard
import dev.tinymcserver.app.ui.components.SectionTitle
import dev.tinymcserver.app.ui.components.SettingRow

/**
 * 设置主页：**分组 + 列表式**。
 *
 * 只放高频项（语言、两个开关、保活状态、关于）；低频与偏技术的项
 * （JRE 管理、下载源与镜像、RCON）收进各自的子页面，避免主页越堆越长。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    Settings.attach(ctx)

    var hideRecents by remember { mutableStateOf(Settings.hideFromRecents) }
    var sanitizeLogs by remember { mutableStateOf(Settings.sanitizeSharedLogs) }
    var autoCheck by remember { mutableStateOf(Settings.autoCheckUpdate) }
    var ignoringBattery by remember { mutableStateOf(BatteryUtil.isIgnoring(ctx)) }
    var showLangPicker by remember { mutableStateOf(false) }

    val appVersion = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }
    val jreSummary = remember {
        val installed = JreManager.BUNDLED.filter { JreManager.isInstalled(ctx, it) }
        if (installed.isEmpty()) t("未解压")
        else t("已安装：%s", installed.joinToString(" / ") { "JRE $it" })
    }

    LaunchedEffect(Unit) { ignoringBattery = BatteryUtil.isIgnoring(ctx) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("设置")) },
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
            I18n.revision // 语言变化时触发重组

            // ---------------- 通用 ----------------
            SectionTitle(t("通用"))
            FieldCard {
                SettingRow(
                    title = t("语言"),
                    subtitle = I18n.current.displayName,
                    onClick = { showLangPicker = true },
                )
                HorizontalDivider()
                SettingRow(
                    title = t("从最近任务中隐藏"),
                    subtitle = t("开启后，本应用不出现在系统「最近任务」列表中。"),
                    trailing = {
                        Switch(
                            checked = hideRecents,
                            onCheckedChange = { v ->
                                hideRecents = v
                                Settings.hideFromRecents = v
                                (ctx as? MainActivity)?.applyRecentsVisibility()
                            },
                        )
                    },
                )
                HorizontalDivider()
                SettingRow(
                    title = t("日志分享脱敏"),
                    subtitle = t("复制日志时自动隐藏玩家 IP、UUID 中段与玩家名。"),
                    trailing = {
                        Switch(
                            checked = sanitizeLogs,
                            onCheckedChange = { v ->
                                sanitizeLogs = v
                                Settings.sanitizeSharedLogs = v
                            },
                        )
                    },
                )
            }

            // ---------------- 服务端 ----------------
            SectionTitle(t("服务端"))
            FieldCard {
                SettingRow(
                    title = t("JRE 管理"),
                    subtitle = jreSummary,
                    onClick = { nav.navigate("settings/jre") },
                )
                HorizontalDivider()
                SettingRow(
                    title = t("高级"),
                    subtitle = t("下载源与镜像、RCON"),
                    onClick = { nav.navigate("settings/advanced") },
                )
            }

            // ---------------- 保活 ----------------
            SectionTitle(t("保活"))
            FieldCard {
                Text(
                    if (ignoringBattery) t("已加入电池优化白名单 ✅")
                    else t("尚未加入电池优化白名单，后台可能被系统杀掉 ❌"),
                    color = if (ignoringBattery) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = {
                        runCatching { ctx.startActivity(BatteryUtil.requestIgnoreIntent(ctx)) }
                            .onFailure {
                                runCatching { ctx.startActivity(BatteryUtil.openSettingsIntent()) }
                            }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) { Text(t("关闭电池优化 / 加入白名单")) }
                Text(
                    t("服务端运行时会启动前台服务，并持有 PARTIAL_WAKE_LOCK 与 WIFI_LOCK。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------------- 关于 ----------------
            SectionTitle(t("关于"))
            FieldCard {
                SettingRow(
                    title = t("检查更新"),
                    subtitle = t("当前版本 %s", appVersion),
                    onClick = { nav.navigate("update") },
                )
                HorizontalDivider()
                SettingRow(
                    title = t("启动时自动检查新版本"),
                    trailing = {
                        Switch(
                            checked = autoCheck,
                            onCheckedChange = { v ->
                                autoCheck = v
                                Settings.autoCheckUpdate = v
                            },
                        )
                    },
                )
                HorizontalDivider()
                SettingRow(
                    title = t("关于本应用"),
                    onClick = { nav.navigate("about") },
                )
            }

            Text(" ", Modifier.padding(12.dp))
        }
    }

    // 语言：列表式 → 点一行弹选择
    if (showLangPicker) {
        AlertDialog(
            onDismissRequest = { showLangPicker = false },
            title = { Text(t("语言")) },
            text = {
                Column {
                    AppLanguage.entries.forEach { lang ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = I18n.current == lang,
                                onClick = {
                                    I18n.setLanguage(ctx, lang)
                                    showLangPicker = false
                                },
                            )
                            Text(lang.displayName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLangPicker = false }) { Text(t("关闭")) }
            },
        )
    }
}
