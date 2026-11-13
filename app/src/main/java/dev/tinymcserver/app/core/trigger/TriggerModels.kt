package dev.tinymcserver.app.core.trigger

import android.content.Context
import dev.tinymcserver.app.core.storage.Paths
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** 触发事件（当…） */
@Serializable
data class TrEvent(
    /** player_join / player_leave / player_count / chat / death / advancement / server_ready / timer */
    val type: String = "player_join",
    /** all / exact / contains */
    val scope: String = "all",
    val value: String = "",
    /** 各事件的附加参数：count_op+count、interval_sec、keyword… */
    val extra: Map<String, String> = emptyMap(),
)

/** 条件组：空表示总是成立 */
@Serializable
data class TrConditionGroup(
    val matchAll: Boolean = true,
    val items: List<TrCondition> = emptyList(),
)

@Serializable
data class TrCondition(
    /** name_match / whitelist / op / banned / online / time_range / weekday / random / since_last / server_state */
    val type: String = "name_match",
    val params: Map<String, String> = emptyMap(),
)

/** 一个动作，可挂「成功后 / 失败后」子动作 */
@Serializable
data class TrAction(
    val type: String = "command",
    val params: Map<String, String> = emptyMap(),
    val onSuccess: List<TrAction> = emptyList(),
    val onFailure: List<TrAction> = emptyList(),
)

@Serializable
data class Trigger(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val event: TrEvent = TrEvent(),
    val conditions: TrConditionGroup = TrConditionGroup(),
    val thenActions: List<TrAction> = emptyList(),
    val elseActions: List<TrAction> = emptyList(),
    val cooldownMs: Long = 0,
    val oncePerSession: Boolean = false,
)

/** 触发器持久化：每个实例一个 triggers.json */
object TriggerStore {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(ctx: Context, instanceId: String) =
        File(Paths.instanceDir(ctx, instanceId), "triggers.json")

    fun load(ctx: Context, instanceId: String): List<Trigger> = runCatching {
        val f = file(ctx, instanceId)
        if (!f.exists()) emptyList() else json.decodeFromString<List<Trigger>>(f.readText())
    }.getOrDefault(emptyList())

    fun save(ctx: Context, instanceId: String, list: List<Trigger>) {
        runCatching {
            val f = file(ctx, instanceId)
            f.parentFile?.mkdirs()
            f.writeText(json.encodeToString(list))
        }
    }

    fun upsert(ctx: Context, instanceId: String, trigger: Trigger): List<Trigger> {
        val list = load(ctx, instanceId).toMutableList()
        val i = list.indexOfFirst { it.id == trigger.id }
        if (i >= 0) list[i] = trigger else list.add(trigger)
        save(ctx, instanceId, list)
        return list
    }

    fun remove(ctx: Context, instanceId: String, id: String): List<Trigger> {
        val list = load(ctx, instanceId).filterNot { it.id == id }
        save(ctx, instanceId, list)
        return list
    }

    fun find(ctx: Context, instanceId: String, id: String): Trigger? =
        load(ctx, instanceId).firstOrNull { it.id == id }
}

/**
 * 触发器运行日志（本地文本，追加写，最多保留 500 行）。
 * 每条记录形如：`13:52:07 | VIP 入场 | 成功 | 42ms` 或 `13:52:07 |   └ particle → Unknown command`
 */
object TriggerLogStore {
    private const val MAX_LINES = 500

    private fun file(ctx: Context, instanceId: String) =
        File(Paths.instanceDir(ctx, instanceId), "trigger-logs.txt")

    fun append(ctx: Context, instanceId: String, line: String) {
        runCatching {
            val f = file(ctx, instanceId)
            val old = if (f.exists()) f.readLines() else emptyList()
            val merged = (old + line).takeLast(MAX_LINES)
            f.writeText(merged.joinToString("\n"))
        }
    }

    fun read(ctx: Context, instanceId: String): List<String> = runCatching {
        val f = file(ctx, instanceId)
        if (!f.exists()) emptyList() else f.readLines()
    }.getOrDefault(emptyList())

    fun clear(ctx: Context, instanceId: String) {
        runCatching { file(ctx, instanceId).writeText("") }
    }
}
