package dev.tinymcserver.app.core.trigger

import android.content.Context
import dev.tinymcserver.app.core.i18n.t
import dev.tinymcserver.app.core.player.PlayerManager
import dev.tinymcserver.app.core.server.LogParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 触发器引擎：日志行 → 事件 → 条件 → 动作（串行队列）。
 *
 * 三条硬约束：
 *  1. 同一实例所有动作串行执行（Mutex），不会同时往服务端灌命令；
 *  2. 状态型事件（人数变化）只在「不满足 → 满足」的边沿触发一次；
 *  3. 动作后监听 800ms 回显，出现错误行即判失败，走「失败后」分支。
 */
object TriggerEngine {

    class Session(
        val ctx: Context,
        val instanceId: String,
        val scope: CoroutineScope,
        val send: suspend (String) -> Unit,
        val online: () -> List<String>,
        val maxPlayers: () -> Int,
        val running: () -> Boolean,
        /** 往控制台追加一行（用于让用户看到触发器真的动作了） */
        val notice: (String) -> Unit = {},
    )

    private val sessions = ConcurrentHashMap<String, Session>()
    private val caches = ConcurrentHashMap<String, List<Trigger>>()
    private val recent = ConcurrentHashMap<String, ArrayDeque<Pair<Long, String>>>()
    private val lastFired = ConcurrentHashMap<String, Long>()
    private val firedOnce = ConcurrentHashMap<String, MutableSet<String>>()
    private val counters = ConcurrentHashMap<String, Int>()
    private val userVars = ConcurrentHashMap<String, MutableMap<String, String>>()
    private val edges = ConcurrentHashMap<String, Boolean>()
    private val mutexes = ConcurrentHashMap<String, Mutex>()
    private val timerJobs = ConcurrentHashMap<String, Job>()

    private val errorRe = Regex(
        "Unknown command|Incorrect argument|Expected |That player does not exist|" +
            "No player was found|Player not found|无法解析|未知的命令"
    )

    // ---------------- 生命周期 ----------------

    fun register(session: Session) {
        sessions[session.instanceId] = session
        onServerStart(session.instanceId)
        val list = TriggerStore.load(session.ctx, session.instanceId)
        caches[session.instanceId] = list
        val on = list.count { it.enabled }
        log(session, t("引擎已就绪：%s 条触发器（启用 %s）", list.size, on))
        timerJobs.remove(session.instanceId)?.cancel()
        timerJobs[session.instanceId] = session.scope.launch {
            while (isActive) {
                delay(1000)
                runCatching { tick(session.instanceId) }
            }
        }
    }

    fun unregister(instanceId: String) {
        timerJobs.remove(instanceId)?.cancel()
        sessions.remove(instanceId)
        caches.remove(instanceId)
        recent.remove(instanceId)
        firedOnce.remove(instanceId)
        counters.keys.filter { it.startsWith("$instanceId:") }.forEach { counters.remove(it) }
        userVars.remove(instanceId)
        mutexes.remove(instanceId)
        edges.keys.filter { it.startsWith("$instanceId:") }.forEach { edges.remove(it) }
    }

    fun reload(ctx: Context, instanceId: String) {
        val list = TriggerStore.load(ctx, instanceId)
        caches[instanceId] = list
        sessions[instanceId]?.let { log(it, t("触发器已更新：%s 条（启用 %s）", list.size, list.count { tr -> tr.enabled })) }
    }

    fun onServerStart(instanceId: String) {
        firedOnce[instanceId] = ConcurrentHashMap.newKeySet<String>()
        edges.keys.filter { it.startsWith("$instanceId:") }.forEach { edges.remove(it) }
        counters.remove(instanceId)
    }

    // ---------------- 日志入口 ----------------

    fun onLine(instanceId: String, line: String) {
        val s = sessions[instanceId] ?: return
        record(instanceId, line)
        val join = LogParser.join(line)
        val leave = LogParser.leave(line)
        val ready = LogParser.isReady(line)
        val chat = chatOf(line)
        val death = deathOf(line)
        val adv = advancementOf(line)

        val list = (caches[instanceId] ?: TriggerStore.load(s.ctx, instanceId)).filter { it.enabled }
        val hits = ArrayList<Pair<Trigger, String>>()
        list.forEach { tr ->
            val e = tr.event
            val hit: String? = when (e.type) {
                "player_join" -> join?.takeIf { scopeMatch(e, it) }
                "player_leave" -> leave?.takeIf { scopeMatch(e, it) }
                "chat" -> chat?.takeIf { (name, msg) ->
                    scopeMatch(e, name) &&
                        (e.extra["keyword"].isNullOrBlank() || msg.contains(e.extra["keyword"].orEmpty(), true))
                }?.first
                "death" -> death?.takeIf { scopeMatch(e, it) }
                "advancement" -> adv?.takeIf { (name, title) ->
                    scopeMatch(e, name) &&
                        (e.extra["keyword"].isNullOrBlank() || title.contains(e.extra["keyword"].orEmpty(), true))
                }?.first
                "server_ready" -> if (ready) "" else null
                else -> null
            }
            if (hit != null) hits += tr to hit
        }
        // 诊断：只要有可识别的事件行，就把「命中了哪几条」写进触发器日志，
        // 便于区分「事件没解析到」和「解析到了但没匹配上」。
        val evtDesc = when {
            join != null -> "player_join($join)"
            leave != null -> "player_leave($leave)"
            chat != null -> "chat(${chat.first})"
            death != null -> "death($death)"
            adv != null -> "advancement(${adv.first})"
            ready -> "server_ready"
            else -> ""
        }
        if (evtDesc.isNotBlank()) {
            log(s, t("事件 %s → 命中 %s 条（启用 %s 条）", evtDesc, hits.size, list.size))
        }
        hits.forEach { (tr, who) -> s.scope.launch { runCatching { fire(tr, s, who) } } }
    }

    private fun tick(instanceId: String) {
        val s = sessions[instanceId] ?: return
        val list = caches[instanceId] ?: return
        val size = s.online().size
        list.filter { it.enabled }.forEach { tr ->
            when (tr.event.type) {
                "player_count" -> {
                    val op = tr.event.extra["op"] ?: ">="
                    val n = tr.event.extra["count"]?.toIntOrNull() ?: 1
                    val now = when (op) {
                        ">=" -> size >= n
                        "<=" -> size <= n
                        else -> size == n
                    }
                    val key = "$instanceId:${tr.id}"
                    val before = edges[key] ?: false
                    edges[key] = now
                    // 只在「不满足 → 满足」的边沿触发
                    if (now && !before) {
                        s.scope.launch { runCatching { fire(tr, s, "") } }
                    }
                }
                "timer" -> {
                    val key = "$instanceId:${tr.id}:timer"
                    val mode = tr.event.extra["mode"] ?: "interval"
                    val nowMs = System.currentTimeMillis()
                    val last = lastFired[tr.id] ?: 0L
                    val due = if (mode == "daily") {
                        val at = tr.event.extra["daily_at"] ?: "20:00"
                        val hm = at.split(":")
                        val h = hm.getOrNull(0)?.toIntOrNull() ?: 20
                        val m = hm.getOrNull(1)?.toIntOrNull() ?: 0
                        val nowT = LocalTime.now()
                        nowT.hour == h && nowT.minute == m && (nowMs - last) > 60_000
                    } else {
                        val sec = tr.event.extra["interval_sec"]?.toLongOrNull() ?: 1800L
                        nowMs - last >= sec * 1000
                    }
                    if (due) {
                        lastFired[tr.id] = nowMs
                        s.scope.launch { runCatching { fire(tr, s, "") } }
                    }
                    edges[key] = true
                }
            }
        }
    }

    // ---------------- 触发 ----------------

    private suspend fun fire(tr: Trigger, s: Session, player: String) {
        val now = System.currentTimeMillis()
        if (tr.cooldownMs > 0 && now - (lastFired[tr.id] ?: 0L) < tr.cooldownMs) {
            log(s, t("「%s」冷却中，跳过", tr.name))
            return
        }
        if (tr.oncePerSession && firedOnce.getOrPut(s.instanceId) { ConcurrentHashMap.newKeySet<String>() }.contains(tr.id)) {
            log(s, t("「%s」本局已触发过，跳过", tr.name))
            return
        }

        val vars = buildVars(s, tr, player)
        val ok = evalGroup(tr, s, vars)
        lastFired[tr.id] = now
        val counter = (counters[tr.id] ?: 0) + 1
        counters[tr.id] = counter
        vars["count"] = counter.toString()

        if (!ok) {
            log(s, t("「%s」条件不成立", tr.name))
            if (tr.elseActions.isNotEmpty()) {
                log(s, t("「%s」→ 执行否则分支", tr.name))
                runActions(tr, s, tr.elseActions, vars, 0)
            }
            return
        }
        if (tr.oncePerSession) {
            firedOnce.getOrPut(s.instanceId) { ConcurrentHashMap.newKeySet<String>() }.add(tr.id)
        }
        if (tr.thenActions.isEmpty()) {
            log(s, t("「%s」已触发，但「就」里没有动作", tr.name))
            return
        }
        log(s, t("「%s」已触发（玩家=%s，在线=%s）", tr.name, player.ifBlank { "-" }, vars["online"] ?: "0"))
        runActions(tr, s, tr.thenActions, vars, 0)
    }

    private suspend fun runActions(
        tr: Trigger,
        s: Session,
        actions: List<TrAction>,
        vars: MutableMap<String, String>,
        depth: Int,
    ) {
        if (depth > 4) return
        val mutex = mutexes.getOrPut(s.instanceId) { Mutex() }
        mutex.withLock {
            for (a in actions) {
                val started = System.currentTimeMillis()
                val ok = runAction(tr, s, a, vars, depth)
                val ms = System.currentTimeMillis() - started
                if (!ok && a.onFailure.isNotEmpty()) {
                    log(s, "${tr.name} | 动作失败：${TriggerSpecs.actionLabel(a.type)} (${ms}ms)")
                    runActions(tr, s, a.onFailure, vars, depth + 1)
                } else if (ok && a.onSuccess.isNotEmpty()) {
                    runActions(tr, s, a.onSuccess, vars, depth + 1)
                }
                if (a.type == "stop") return@withLock
            }
        }
    }

    private suspend fun runAction(
        tr: Trigger,
        s: Session,
        a: TrAction,
        vars: MutableMap<String, String>,
        depth: Int,
    ): Boolean {
        val filled = inlineVars(a.params, vars)
        return when (a.type) {
            "delay" -> {
                delay(filled["ms"]?.toLongOrNull()?.coerceIn(0, 600_000) ?: 500)
                true
            }
            "setvar" -> {
                userVars.getOrPut(s.instanceId) { ConcurrentHashMap() }[filled["name"].orEmpty()] = filled["value"].orEmpty()
                true
            }
            "addvar" -> {
                val m = userVars.getOrPut(s.instanceId) { ConcurrentHashMap() }
                val k = filled["name"].orEmpty().ifBlank { "x" }
                val delta = filled["delta"]?.toIntOrNull() ?: 1
                m[k] = ((m[k]?.toIntOrNull() ?: 0) + delta).toString()
                true
            }
            "log" -> {
                log(s, "${tr.name} | ${filled["text"].orEmpty()}")
                true
            }
            "jump_else" -> {
                if (tr.elseActions.isNotEmpty()) runActions(tr, s, tr.elseActions, vars, depth + 1)
                true
            }
            "stop" -> true
            else -> {
                val cmds = TriggerSpecs.buildCommands(a.copy(params = filled), filled)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (cmds.isEmpty()) {
                    log(s, t("「%s」的命令为空，已跳过", tr.name))
                    return false
                }
                var ok = true
                for (c in cmds) {
                    val start = System.currentTimeMillis()
                    log(s, t("「%s」发送命令：%s", tr.name, c))
                    // 同步在控制台留一行，用户能直接看到触发器确实动作了
                    s.notice(t("[触发器] %s：%s", tr.name, c))
                    s.send(c)
                    delay(800)
                    val err = recent[s.instanceId]
                        ?.filter { it.first >= start }
                        ?.firstOrNull { errorRe.containsMatchIn(it.second) }
                    if (err != null) {
                        ok = false
                        log(s, t("「%s」命令失败：%s → %s", tr.name, c, err.second.take(120)))
                        break
                    }
                }
                if (ok) log(s, t("「%s」命令已执行完成", tr.name))
                ok
            }
        }
    }

    // ---------------- 条件 ----------------

    private fun evalGroup(tr: Trigger, s: Session, vars: Map<String, String>): Boolean {
        val items = tr.conditions.items
        if (items.isEmpty()) return true
        return if (tr.conditions.matchAll) items.all { evalOne(tr, s, it, vars) }
        else items.any { evalOne(tr, s, it, vars) }
    }

    private fun evalOne(tr: Trigger, s: Session, c: TrCondition, vars: Map<String, String>): Boolean {
        val player = vars["player"].orEmpty()
        val p = c.params
        return when (c.type) {
            "name_match" -> {
                val v = p["value"].orEmpty()
                when (p["op"] ?: "=") {
                    "包含" -> player.contains(v, true)
                    "前缀" -> player.startsWith(v, true)
                    else -> player.equals(v, true)
                }
            }
            "whitelist" -> inList(s, player) { PlayerManager.whitelist(s.ctx, s.instanceId).map { it.name } } == (p["expect"] != "否")
            "op" -> inList(s, player) { PlayerManager.ops(s.ctx, s.instanceId).map { it.name } } == (p["expect"] != "否")
            "banned" -> inList(s, player) { PlayerManager.banned(s.ctx, s.instanceId).map { it.name } } == (p["expect"] == "是")
            "online" -> {
                val n = p["value"]?.toIntOrNull() ?: 1
                val size = s.online().size
                when (p["op"] ?: ">=") {
                    ">=" -> size >= n
                    "<=" -> size <= n
                    else -> size == n
                }
            }
            "time_range" -> inTimeRange(p["from"] ?: "00:00", p["to"] ?: "23:59")
            "weekday" -> {
                val days = (p["days"] ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }
                days.isEmpty() || LocalDate.now().dayOfWeek.value in days
            }
            "random" -> Random.nextInt(100) < (p["percent"]?.toIntOrNull() ?: 50)
            "since_last" -> {
                val sec = p["seconds"]?.toLongOrNull() ?: 60
                System.currentTimeMillis() - (lastFired[tr.id] ?: 0L) >= sec * 1000
            }
            "server_state" -> s.running() == (p["expect"] != "已停止")
            else -> true
        }
    }

    private fun inList(s: Session, player: String, loader: () -> List<String>): Boolean =
        player.isNotBlank() && loader().any { it.equals(player, true) }

    private fun inTimeRange(from: String, to: String): Boolean {
        fun m(t: String): Int {
            val parts = t.split(":")
            return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
        }
        val now = LocalTime.now().let { it.hour * 60 + it.minute }
        val a = m(from)
        val b = m(to)
        return if (a <= b) now in a..b else now >= a || now <= b
    }

    // ---------------- 变量 ----------------

    private fun buildVars(s: Session, tr: Trigger, player: String): MutableMap<String, String> {
        val u = userVars.getOrPut(s.instanceId) { ConcurrentHashMap() }
        val now = LocalTime.now()
        val vars = mutableMapOf(
            "player" to player,
            "online" to s.online().size.toString(),
            "max" to s.maxPlayers().toString(),
            "count" to (counters[tr.id] ?: 0).toString(),
            "time" to String.format("%02d:%02d", now.hour, now.minute),
            "date" to LocalDate.now().toString(),
            "trigger" to tr.name,
        )
        u.forEach { (k, v) -> vars["var:$k"] = v }
        return vars
    }

    private fun inlineVars(src: Map<String, String>, vars: Map<String, String>): MutableMap<String, String> {
        val out = mutableMapOf<String, String>()
        src.forEach { (k, v) ->
            var text = v
            vars.forEach { (vk, vv) -> text = text.replace("{$vk}", vv) }
            out[k] = text
        }
        return out
    }

    // ---------------- 日志与缓冲 ----------------

    private fun record(instanceId: String, line: String) {
        val buf = recent.getOrPut(instanceId) { ArrayDeque() }
        buf.addLast(System.currentTimeMillis() to line)
        while (buf.size > 300) buf.removeFirst()
    }

    private fun log(s: Session, text: String) {
        val stamp = LocalTime.now().let { String.format("%02d:%02d:%02d", it.hour, it.minute, it.second) }
        TriggerLogStore.append(s.ctx, s.instanceId, "$stamp | $text")
    }

    // ---------------- 解析辅助 ----------------

    private fun scopeMatch(e: TrEvent, name: String): Boolean = when (e.scope) {
        "exact" -> name.equals(e.value, true)
        "contains" -> e.value.isBlank() || name.contains(e.value, true)
        else -> true
    }

    private val chatRe = Regex("\\]: <([^>]{1,32})> (.*)$")

    private fun chatOf(line: String): Pair<String, String>? {
        val m = chatRe.find(line) ?: return null
        return m.groupValues[1] to m.groupValues[2]
    }

    private val deathRe = Regex("\\]: ([^\\s]{1,32}) (?:was slain by|was shot by|was killed by|drowned|died|" +
        "fell from|fell into|burned to death|withered away|starved to death|hit the ground too hard)")

    private fun deathOf(line: String): String? = deathRe.find(line)?.groupValues?.get(1)

    private val advRe = Regex("\\]: ([^\\s]{1,32}) has (?:made the advancement|reached the goal|completed the challenge) \\[(.+?)]")

    private fun advancementOf(line: String): Pair<String, String>? {
        val m = advRe.find(line) ?: return null
        return m.groupValues[1] to m.groupValues[2]
    }
}
