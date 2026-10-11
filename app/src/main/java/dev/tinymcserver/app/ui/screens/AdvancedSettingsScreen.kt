package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.i18n.t
import dev.tinymcserver.app.core.storage.Settings
import dev.tinymcserver.app.ui.AppViewModel
import dev.tinymcserver.app.ui.components.FieldCard
import dev.tinymcserver.app.ui.components.LabeledField
import dev.tinymcserver.app.ui.components.SectionTitle

/** 高级：下载源与镜像、RCON —— 低频但技术性较强，从主设置页挪进来。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    Settings.attach(ctx)
    var rcon by remember { mutableStateOf(Settings.getRconPassword(ctx)) }
    var mirror by remember { mutableStateOf(Settings.mirrorBase) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("高级")) },
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
            SectionTitle(t("下载源与镜像"))
            FieldCard {
                Text(
                    t("默认源：PaperMC / Purpur / Folia / Mojang 官方。可填写镜像前缀。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LabeledField(t("镜像前缀"), mirror, { mirror = it })
                Button(
                    onClick = { Settings.mirrorBase = mirror.trim(); vm.toast(t("已保存镜像设置")) },
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text(t("保存")) }
            }

            SectionTitle("RCON")
            FieldCard {
                Text(
                    t("RCON 密码使用 Android Keystore 加密存储。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LabeledField(t("RCON 密码"), rcon, { rcon = it })
                Button(
                    onClick = { Settings.setRconPassword(ctx, rcon); vm.toast(t("已加密保存")) },
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text(t("保存密码")) }
            }

            Text(" ", Modifier.padding(12.dp))
        }
    }
}
