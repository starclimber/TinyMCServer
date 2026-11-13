package dev.tinymcserver.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import dev.tinymcserver.app.ui.components.SectionTitle
import dev.tinymcserver.app.core.trigger.TrCondition
import dev.tinymcserver.app.core.trigger.TrEvent
import dev.tinymcserver.app.core.trigger.Trigger
import dev.tinymcserver.app.core.trigger.TriggerEngine
import dev.tinymcserver.app.core.trigger.TriggerSpecs
import dev.tinymcserver.app.core.trigger.TriggerStore

/** 触发器编辑：当 / 如果 / 就 / 否则 四段式 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerEditScreen(nav: NavController, instanceId: String, triggerId: String?) {
    val ctx = LocalContext.current
    val isNew = triggerId == null || triggerId == "new"
    var tr by remember {
        mutableStateOf(
            if (isNew) Trigger() else (triggerId?.let { TriggerStore.find(ctx, instanceId, it) } ?: Trigger())
        )
    }
    var editingCond by remember { mutableStateOf<Int?>(null) }
    var addingCond by remember { mutableStateOf(false) }

    fun save() {
        val toSave = tr.copy(name = tr.name.ifBlank { t("未命名触发器") })
        TriggerStore.upsert(ctx, instanceId, toSave)
        TriggerEngine.reload(ctx, instanceId)
        if (nav.previousBackStackEntry != null) nav.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) t("新建触发器") else t("编辑触发器")) },
                navigationIcon = {
                    IconButton(onClick = { if (nav.previousBackStackEntry != null) nav.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = t("返回"))
                    }
                },
                actions = { TextButton(onClick = { save() }) { Text(t("保存")) } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            OutlinedTextField(
                value = tr.name,
                onValueChange = { tr = tr.copy(name = it) },
                label = { Text(t("触发器名称")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t("启用"), Modifier.weight(1f))
                    Switch(checked = tr.enabled, onCheckedChange = { tr = tr.copy(enabled = it) })
                }
            }

            // ---------- 当 ----------
            SectionTitle(t("当"))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val spec = TriggerSpecs.event(tr.event.type)
                    SelectField(
                        t("事件"),
                        TriggerSpecs.events.map { it.type },
                        tr.event.type,
                        { type ->
                            val s = TriggerSpecs.event(type)
                            tr = tr.copy(event = TrEvent(type = type, extra = s.fields.associate { it.key to it.def }))
                        },
                    ) { t(TriggerSpecs.event(it).label) }

                    if (spec.playerScope) {
                        SelectField(
                            t("玩家范围"),
                            listOf("all", "exact", "contains"),
                            tr.event.scope,
                            { tr = tr.copy(event = tr.event.copy(scope = it)) },
                        ) {
                            when (it) {
                                "exact" -> t("指定玩家")
                                "contains" -> t("名字包含")
                                else -> t("所有玩家")
                            }
                        }
                        if (tr.event.scope != "all") {
                            OutlinedTextField(
                                value = tr.event.value,
                                onValueChange = { tr = tr.copy(event = tr.event.copy(value = it)) },
                                label = {
                                    Text(if (tr.event.scope == "exact") t("玩家名") else t("名字包含的文字"))
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            )
                        }
                    }

                    if (spec.fields.isNotEmpty()) {
                        FieldsForm(spec.fields, tr.event.extra) { k, v ->
                            tr = tr.copy(event = tr.event.copy(extra = tr.event.extra + (k to v)))
                        }
                    }
                }
            }

            // ---------- 如果 ----------
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t("如果"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (tr.conditions.items.size > 1) {
                    TextButton(onClick = {
                        tr = tr.copy(conditions = tr.conditions.copy(matchAll = !tr.conditions.matchAll))
                    }) { Text(if (tr.conditions.matchAll) t("全部满足") else t("任一满足")) }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    if (tr.conditions.items.isEmpty()) {
                        Text(
                            t("不加条件时，事件一发生就执行「就」里的动作"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    tr.conditions.items.forEachIndexed { i, c ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                t(TriggerSpecs.condition(c.type).label) + summaryOf(c),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { editingCond = i }) { Text(t("编辑")) }
                            TextButton(onClick = {
                                tr = tr.copy(
                                    conditions = tr.conditions.copy(
                                        items = tr.conditions.items.filterIndexed { idx, _ -> idx != i }
                                    )
                                )
                            }) { Text(t("删除")) }
                        }
                    }
                    TextButton(onClick = { addingCond = true }) { Text(t("+ 添加条件")) }
                }
            }

            // ---------- 就 ----------
            SectionTitle(t("就"))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    ActionListEditor(tr.thenActions, allowSub = true) { tr = tr.copy(thenActions = it) }
                }
            }

            // ---------- 否则 ----------
            SectionTitle(t("否则"))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    if (tr.elseActions.isEmpty()) {
                        Text(
                            t("条件不成立时执行；留空则什么也不做"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ActionListEditor(tr.elseActions, allowSub = true) { tr = tr.copy(elseActions = it) }
                }
            }

            // ---------- 其他 ----------
            SectionTitle(t("其他"))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = if (tr.cooldownMs == 0L) "" else (tr.cooldownMs / 1000).toString(),
                        onValueChange = { v ->
                            tr = tr.copy(cooldownMs = (v.toLongOrNull() ?: 0L) * 1000)
                        },
                        label = { Text(t("冷却秒数（0 = 不限）")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t("每次开服只触发一次"), Modifier.weight(1f))
                        Switch(
                            checked = tr.oncePerSession,
                            onCheckedChange = { tr = tr.copy(oncePerSession = it) },
                        )
                    }
                }
            }

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 24.dp),
            ) { Text(t("保存触发器")) }
        }
    }

    if (addingCond) {
        ConditionEditDialog(initial = null, onDismiss = { addingCond = false }) { c ->
            tr = tr.copy(conditions = tr.conditions.copy(items = tr.conditions.items + c))
            addingCond = false
        }
    }
    editingCond?.let { idx ->
        ConditionEditDialog(
            initial = tr.conditions.items[idx],
            onDismiss = { editingCond = null },
        ) { c ->
            tr = tr.copy(
                conditions = tr.conditions.copy(
                    items = tr.conditions.items.toMutableList().also { it[idx] = c }
                )
            )
            editingCond = null
        }
    }
}

private fun summaryOf(c: TrCondition): String {
    if (c.params.isEmpty()) return ""
    return " · " + c.params.entries.joinToString(" ") { (k, v) -> "$k=$v" }
}
