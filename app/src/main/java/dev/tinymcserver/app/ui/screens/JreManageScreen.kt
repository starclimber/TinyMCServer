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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.tinymcserver.app.core.i18n.t
import dev.tinymcserver.app.core.runtime.JreManager
import dev.tinymcserver.app.ui.AppViewModel
import dev.tinymcserver.app.ui.components.FieldCard
import dev.tinymcserver.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

/** JRE 管理：内置运行时的安装 / 重装。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JreManageScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshFlag by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t("JRE 管理")) },
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
            SectionTitle(t("内置运行时"))
            FieldCard {
                Text(
                    t("内置 JRE 打包在 APK 中，首次使用解压到私有目录。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val bundled = vm.bundledJres()
                if (bundled.isEmpty()) {
                    Text(t("未检测到内置 JRE 归档。"), color = MaterialTheme.colorScheme.error)
                }
                bundled.forEach { major ->
                    val installed = vm.jreInstalled(major)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("JRE $major", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (installed)
                                    t("已安装 · %s · ", vm.jreVersionLabel(major)) +
                                        "${"%.0f".format(vm.jreSize(major) / 1048576.0)} MB"
                                else t("未解压"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (installed) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    JreManager.uninstall(ctx, major)
                                    refreshFlag++
                                }
                            }) { Text(t("重装")) }
                        } else {
                            Button(onClick = { vm.installJre(major) { refreshFlag++ } }) {
                                Text(t("解压安装"))
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { vm.installJre(21) { refreshFlag++ } },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) { Text(t("一键安装 JRE 21（推荐）")) }
            }

            Text(
                t("提示：运行 1.20.5+ 的服务端需要 JRE 21；1.17 ~ 1.20.4 对应 JRE 17。"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(" ", Modifier.padding(12.dp))
        }
    }
}
