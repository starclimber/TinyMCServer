package dev.tinymcserver.app.core.trigger

/** 表单字段：kind = text / number / who / choice / longtext */
data class FieldSpec(
    val key: String,
    val label: String,
    val kind: String = "text",
    val def: String = "",
    val choices: List<String> = emptyList(),
)

data class ActionSpec(
    val type: String,
    val label: String,
    val fields: List<FieldSpec> = emptyList(),
)

data class EventSpec(
    val type: String,
    val label: String,
    val fields: List<FieldSpec> = emptyList(),
    /** 是否支持玩家三档范围 */
    val playerScope: Boolean = false,
)

data class ConditionSpec(
    val type: String,
    val label: String,
    val fields: List<FieldSpec> = emptyList(),
)

/**
 * 事件 / 条件 / 动作的元数据 + 命令拼装。
 * UI 只依赖这张表渲染表单，引擎只依赖 [buildCommands]。
 */
object TriggerSpecs {

    // ---------------- 事件 ----------------
    val events: List<EventSpec> = listOf(
        EventSpec("player_join", "玩家进入", playerScope = true),
        EventSpec("player_leave", "玩家退出", playerScope = true),
        EventSpec(
            "player_count", "人数变化",
            listOf(
                FieldSpec("op", "条件", "choice", ">=", listOf(">=", "<=", "=")),
                FieldSpec("count", "人数", "number", "5"),
            ),
        ),
        EventSpec(
            "chat", "玩家聊天", playerScope = true,
            fields = listOf(FieldSpec("keyword", "包含关键词（留空=任意）", "text", "")),
        ),
        EventSpec("death", "玩家死亡", playerScope = true),
        EventSpec(
            "advancement", "玩家获得成就", playerScope = true,
            fields = listOf(FieldSpec("keyword", "成就名包含（留空=任意）", "text", "")),
        ),
        EventSpec("server_ready", "服务器就绪"),
        EventSpec(
            "timer", "定时",
            listOf(
                FieldSpec("mode", "方式", "choice", "interval", listOf("interval", "daily")),
                FieldSpec("interval_sec", "每多少秒", "number", "1800"),
                FieldSpec("daily_at", "每天几点（HH:mm）", "text", "20:00"),
            ),
        ),
    )

    fun event(type: String): EventSpec = events.firstOrNull { it.type == type } ?: events.first()

    // ---------------- 条件 ----------------
    val conditions: List<ConditionSpec> = listOf(
        ConditionSpec(
            "name_match", "玩家名匹配",
            listOf(
                FieldSpec("op", "方式", "choice", "=", listOf("=", "包含", "前缀")),
                FieldSpec("value", "内容", "text", ""),
            ),
        ),
        ConditionSpec("whitelist", "在白名单", listOf(FieldSpec("expect", "期望", "choice", "是", listOf("是", "否")))),
        ConditionSpec("op", "是 OP", listOf(FieldSpec("expect", "期望", "choice", "是", listOf("是", "否")))),
        ConditionSpec("banned", "被封禁", listOf(FieldSpec("expect", "期望", "choice", "否", listOf("是", "否")))),
        ConditionSpec(
            "online", "在线人数",
            listOf(
                FieldSpec("op", "方式", "choice", ">=", listOf(">=", "<=", "=")),
                FieldSpec("value", "人数", "number", "1"),
            ),
        ),
        ConditionSpec(
            "time_range", "时间范围",
            listOf(FieldSpec("from", "从（HH:mm）", "text", "00:00"), FieldSpec("to", "到（HH:mm）", "text", "23:59")),
        ),
        ConditionSpec("weekday", "星期", listOf(FieldSpec("days", "周几（如 1,2,3）", "text", "1,2,3,4,5,6,7"))),
        ConditionSpec("random", "随机概率", listOf(FieldSpec("percent", "百分比", "number", "50"))),
        ConditionSpec("since_last", "距上次触发", listOf(FieldSpec("seconds", "至少多少秒", "number", "60"))),
        ConditionSpec(
            "server_state", "服务器状态",
            listOf(FieldSpec("expect", "期望", "choice", "运行中", listOf("运行中", "已停止"))),
        ),
    )

    fun condition(type: String): ConditionSpec =
        conditions.firstOrNull { it.type == type } ?: conditions.first()

    // ---------------- 动作 ----------------
    private val whoField = FieldSpec("who", "目标", "who", "{player}")

    val actions: List<ActionSpec> = listOf(
        ActionSpec("command", "执行命令", listOf(FieldSpec("cmd", "命令（不带 /）", "text", ""))),
        ActionSpec("say", "广播消息", listOf(FieldSpec("text", "内容", "text", ""))),
        ActionSpec("tell", "私聊玩家", listOf(whoField, FieldSpec("text", "内容", "text", ""))),
        ActionSpec("raw", "原始 JSON 消息", listOf(whoField, FieldSpec("json", "JSON", "longtext", "{\"text\":\"你好\"}"))),
        ActionSpec(
            "title", "主标题",
            listOf(
                whoField,
                FieldSpec("text", "内容", "text", ""),
                FieldSpec("fade_in", "淡入(ms)", "number", "200"),
                FieldSpec("stay", "停留(ms)", "number", "1500"),
                FieldSpec("fade_out", "淡出(ms)", "number", "500"),
            ),
        ),
        ActionSpec("subtitle", "副标题", listOf(whoField, FieldSpec("text", "内容", "text", ""))),
        ActionSpec("actionbar", "物品栏提示", listOf(whoField, FieldSpec("text", "内容", "text", ""))),
        ActionSpec("clear_title", "清除标题", listOf(whoField)),
        ActionSpec(
            "sound", "播放音效",
            listOf(
                whoField,
                FieldSpec("id", "音效 ID", "text", "minecraft:entity.firework_rocket.launch"),
                FieldSpec("volume", "音量", "number", "1"),
                FieldSpec("pitch", "音调", "number", "1"),
            ),
        ),
        ActionSpec(
            "particle", "粒子",
            listOf(
                whoField,
                FieldSpec("id", "粒子 ID", "text", "minecraft:firework"),
                FieldSpec("count", "数量", "number", "50"),
                FieldSpec("spread", "扩散", "number", "0.5"),
                FieldSpec("speed", "速度", "number", "0.1"),
            ),
        ),
        ActionSpec(
            "give", "给予物品",
            listOf(whoField, FieldSpec("item", "物品 ID", "text", "minecraft:bread"), FieldSpec("count", "数量", "number", "1")),
        ),
        ActionSpec(
            "tp", "传送到坐标",
            listOf(
                whoField,
                FieldSpec("x", "X", "text", "0"), FieldSpec("y", "Y", "text", "64"), FieldSpec("z", "Z", "text", "0"),
            ),
        ),
        ActionSpec("tp_player", "传送到玩家", listOf(whoField, FieldSpec("target", "目标玩家", "text", ""))),
        ActionSpec(
            "gamemode", "设置游戏模式",
            listOf(whoField, FieldSpec("mode", "模式", "choice", "survival", listOf("survival", "creative", "adventure", "spectator"))),
        ),
        ActionSpec(
            "effect", "药水效果",
            listOf(
                whoField,
                FieldSpec("id", "效果 ID", "text", "minecraft:speed"),
                FieldSpec("seconds", "时长(秒)", "number", "10"),
                FieldSpec("amp", "等级", "number", "0"),
            ),
        ),
        ActionSpec("heal", "回满血", listOf(whoField)),
        ActionSpec("feed", "回满饱食度", listOf(whoField)),
        ActionSpec("kick", "踢出", listOf(whoField, FieldSpec("reason", "原因", "text", ""))),
        ActionSpec("time", "设置时间", listOf(FieldSpec("value", "时间", "choice", "day", listOf("day", "noon", "night", "midnight")))),
        ActionSpec("weather", "设置天气", listOf(FieldSpec("value", "天气", "choice", "clear", listOf("clear", "rain", "thunder")))),
        ActionSpec("delay", "延迟", listOf(FieldSpec("ms", "毫秒", "number", "500"))),
        ActionSpec("setvar", "设置变量", listOf(FieldSpec("name", "变量名", "text", "x"), FieldSpec("value", "值", "text", ""))),
        ActionSpec("addvar", "变量加减", listOf(FieldSpec("name", "变量名", "text", "x"), FieldSpec("delta", "增量", "number", "1"))),
        ActionSpec("log", "写日志", listOf(FieldSpec("text", "内容", "text", ""))),
        ActionSpec("stop", "停止后续"),
        ActionSpec("jump_else", "跳到否则"),
    )

    fun action(type: String): ActionSpec = actions.firstOrNull { it.type == type } ?: actions.first()

    fun actionLabel(type: String): String = action(type).label

    /** 纯本地动作：不发命令，因此总是成功 */
    fun isLocal(type: String): Boolean =
        type in setOf("delay", "setvar", "addvar", "log", "stop", "jump_else")

    // ---------------- 命令拼装 ----------------
    private fun quote(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun jsonText(s: String): String = "{\"text\":\"${quote(s)}\"}"

    /**
     * 把一个动作展开成若干条服务端命令。
     * [vars] 里的值已完成变量替换；[who] 已解析成 @a / 玩家名。
     */
    fun buildCommands(action: TrAction, vars: Map<String, String>): List<String> {
        fun raw(k: String): String = action.params[k] ?: ""
        val who = raw("who").ifBlank() { "{player}" }
            .let { if (it == "{player}") vars["player"].orEmpty() else it }
            .ifBlank { "@a" }
        return when (action.type) {
            "command" -> listOf(raw("cmd"))
            "say" -> listOf("say ${raw("text")}")
            "tell" -> listOf("tellraw $who ${jsonText(raw("text"))}")
            "raw" -> listOf("tellraw $who ${raw("json")}")
            "title" -> {
                val fi = raw("fade_in").ifBlank { "200" }
                val st = raw("stay").ifBlank { "1500" }
                val fo = raw("fade_out").ifBlank { "500" }
                listOf(
                    "title $who times $fi $st $fo",
                    "title $who title ${jsonText(raw("text"))}",
                )
            }
            "subtitle" -> listOf("title $who subtitle ${jsonText(raw("text"))}")
            "actionbar" -> listOf("title $who actionbar ${jsonText(raw("text"))}")
            "clear_title" -> listOf(
                "title $who times 0 0 0",
                "title $who title {\"text\":\"\"}",
                "title $who subtitle {\"text\":\"\"}",
            )
            "sound" -> listOf(
                "execute as $who at @s run playsound ${raw("id").ifBlank { "minecraft:ui.button.click" }} master @s ~ ~ ~ " +
                    "${raw("volume").ifBlank { "1" }} ${raw("pitch").ifBlank { "1" }}"
            )
            "particle" -> listOf(
                "execute at $who run particle ${raw("id").ifBlank { "minecraft:firework" }} ~ ~1 ~ " +
                    "${raw("spread").ifBlank { "0.5" }} ${raw("spread").ifBlank { "0.5" }} ${raw("spread").ifBlank { "0.5" }} " +
                    "${raw("speed").ifBlank { "0.1" }} ${raw("count").ifBlank { "50" }}"
            )
            "give" -> listOf("give $who ${raw("item").ifBlank { "minecraft:bread" }} ${raw("count").ifBlank { "1" }}")
            "tp" -> listOf(
                "tp $who ${raw("x").ifBlank { "0" }} ${raw("y").ifBlank { "64" }} ${raw("z").ifBlank { "0" }}"
            )
            "tp_player" -> listOf("tp $who ${raw("target")}")
            "gamemode" -> listOf("gamemode ${raw("mode").ifBlank { "survival" }} $who")
            "effect" -> listOf(
                "effect give $who ${raw("id").ifBlank { "minecraft:speed" }} " +
                    "${raw("seconds").ifBlank { "10" }} ${raw("amp").ifBlank { "0" }}"
            )
            "heal" -> listOf("effect give $who minecraft:instant_health 1 10 true")
            "feed" -> listOf("effect give $who minecraft:saturation 1 10 true")
            "kick" -> listOf("kick $who ${raw("reason")}")
            "time" -> listOf("time set ${raw("value").ifBlank { "day" }}")
            "weather" -> listOf("weather ${raw("value").ifBlank { "clear" }}")
            else -> emptyList()
        }
    }
}
