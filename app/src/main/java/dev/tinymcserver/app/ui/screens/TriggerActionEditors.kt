package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tinymcserver.app.core.i18n.t
import dev.tinymcserver.app.core.trigger.ConditionSpec
import dev.tinymcserver.app.core.trigger.FieldSpec
import dev.tinymcserver.app.core.trigger.TrAction
import dev.tinymcserver.app.core.trigger.TrCondition
import dev.tinymcserver.app.core.trigger.TriggerSpecs

/** 通用下拉选择 */
@Composable
fun SelectField(
    label: String,
    options: List<String>,
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    display: ((String) -> String)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(t(label), style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    display?.invoke(value) ?: value,
                    Modifier.weight(1f),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("▾")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f),
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(display?.invoke(opt) ?: opt) },
                        onClick = { onSelect(opt); expanded = false },
                    )
                }
            }
        }
    }
}

/** 按字段定义渲染表单 */
@Composable
fun FieldsForm(
    fields: List<FieldSpec>,
    values: Map<String, String>,
    onChange: (String, String) -> Unit,
) {
    fields.forEach { f ->
        val v = values[f.key] ?: f.def
        when (f.kind) {
            "choice" -> SelectField(f.label, f.choices, v.ifBlank { f.def }, onSelect = { onChange(f.key, it) })
            "who" -> WhoField(f.label, v.ifBlank { f.def }) { onChange(f.key, it) }
            "longtext" -> OutlinedTextField(
                value = v,
                onValueChange = { onChange(f.key, it) },
                label = { Text(t(f.label)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 96.dp),
            )
            else -> OutlinedTextField(
                value = v,
                onValueChange = { onChange(f.key, it) },
                label = { Text(t(f.label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
    }
}

/** 目标三档：所有玩家 / 触发者 / 指定玩家 */
@Composable
private fun WhoField(label: String, value: String, onChange: (String) -> Unit) {
    // 用本地状态记住用户选的是哪一档：否则「指定玩家」写成空串后会被误判回「触发者」
    var mode by remember {
        mutableStateOf(
            when {
                value == "@a" -> "all"
                value.isBlank() || value == "{player}" -> "self"
                else -> "exact"
            }
        )
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(t(label), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(
                onClick = { mode = "all"; onChange("@a") },
                label = { Text(t("所有玩家")) },
                enabled = mode != "all",
            )
            AssistChip(
                onClick = { mode = "self"; onChange("{player}") },
                label = { Text(t("触发者")) },
                enabled = mode != "self",
            )
            AssistChip(
                onClick = { mode = "exact"; onChange("") },
                label = { Text(t("指定玩家")) },
                enabled = mode != "exact",
            )
        }
        if (mode == "exact") {
            OutlinedTextField(
                value = if (value == "@a" || value == "{player}") "" else value,
                onValueChange = onChange,
                label = { Text(t("玩家名")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

/** 下拉选项用 type → label 的映射 */
@Composable
fun TypePicker(
    label: String,
    types: List<String>,
    value: String,
    onSelect: (String) -> Unit,
    labelOf: (String) -> String,
) = SelectField(label, types, value, onSelect, display = labelOf)

/** 动作列表（可嵌套子分支） */
@Composable
fun ActionListEditor(
    actions: List<TrAction>,
    allowSub: Boolean,
    onChange: (List<TrAction>) -> Unit,
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }

    actions.forEachIndexed { i, a ->
        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${i + 1}. ${TriggerSpecs.actionLabel(a.type)}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                if (allowSub && (a.onSuccess.isNotEmpty() || a.onFailure.isNotEmpty())) {
                    Text(
                        t("成功 %s / 失败 %s", a.onSuccess.size, a.onFailure.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(onClick = { editing = i }) { Text(t("编辑")) }
                IconButton(onClick = { onChange(actions.filterIndexed { idx, _ -> idx != i }) }) {
                    Icon(Icons.Filled.Delete, contentDescription = t("删除"))
                }
            }
        }
    }
    TextButton(onClick = { adding = true }) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(t("添加动作"))
    }

    if (adding) {
        ActionEditDialog(
            initial = null,
            allowSub = allowSub,
            onDismiss = { adding = false },
        ) { created ->
            onChange(actions + created)
            adding = false
        }
    }
    editing?.let { idx ->
        ActionEditDialog(
            initial = actions[idx],
            allowSub = allowSub,
            onDismiss = { editing = null },
        ) { updated ->
            onChange(actions.toMutableList().also { it[idx] = updated })
            editing = null
        }
    }
}

/** 动作编辑：动作本身 / 成功后 / 失败后 三个页签 */
@Composable
fun ActionEditDialog(
    initial: TrAction?,
    allowSub: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (TrAction) -> Unit,
) {
    var action by remember { mutableStateOf(initial ?: TrAction()) }
    var tab by remember { mutableIntStateOf(0) }
    val spec = TriggerSpecs.action(action.type)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) t("添加动作") else t("编辑动作")) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                TypePicker(
                    t("动作类型"),
                    TriggerSpecs.actions.map { it.type },
                    action.type,
                    { action = action.copy(type = it) },
                ) { t(TriggerSpecs.actionLabel(it)) }
                if (allowSub) {
                    TabRow(selectedTabIndex = tab) {
                        listOf(t("动作"), t("成功后"), t("失败后")).forEachIndexed { i, s ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = {
                                Text(if (i == 0) s else "$s (${if (i == 1) action.onSuccess.size else action.onFailure.size})")
                            })
                        }
                    }
                }
                when {
                    !allowSub || tab == 0 -> FieldsForm(spec.fields, action.params) { k, v ->
                        action = action.copy(params = action.params + (k to v))
                    }
                    tab == 1 -> ActionListEditor(action.onSuccess, allowSub = false) {
                        action = action.copy(onSuccess = it)
                    }
                    else -> ActionListEditor(action.onFailure, allowSub = false) {
                        action = action.copy(onFailure = it)
                    }
                }
                if (action.type == "command") {
                    Text(
                        t("可用变量：{player} {online} {max} {count} {time} {date} {var:名字}"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(action) }) { Text(t("确定")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("取消")) } },
    )
}

/** 条件编辑 */
@Composable
fun ConditionEditDialog(
    initial: TrCondition?,
    onDismiss: () -> Unit,
    onConfirm: (TrCondition) -> Unit,
) {
    var cond by remember { mutableStateOf(initial ?: TrCondition()) }
    val spec: ConditionSpec = TriggerSpecs.condition(cond.type)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) t("添加条件") else t("编辑条件")) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                TypePicker(
                    t("条件类型"),
                    TriggerSpecs.conditions.map { it.type },
                    cond.type,
                    { cond = cond.copy(type = it) },
                ) { t(TriggerSpecs.condition(it).label) }
                FieldsForm(spec.fields, cond.params) { k, v ->
                    cond = cond.copy(params = cond.params + (k to v))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(cond) }) { Text(t("确定")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("取消")) } },
    )
}
