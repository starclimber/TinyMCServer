package dev.tinymcserver.app.core.player

import android.content.Context
import dev.tinymcserver.app.core.storage.Paths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 玩家名单读写。增删优先走控制台命令（由服务端解析 UUID），
 * 这里负责读取各 JSON 名单用于展示。
 */
object PlayerManager {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Player(val name: String, val uuid: String = "")

    private fun file(ctx: Context, id: String, name: String) =
        File(Paths.instanceDir(ctx, id), name)

    private fun readNames(f: File, nameKey: String): List<Player> {
        if (!f.exists()) return emptyList()
        return try {
            val arr = json.parseToJsonElement(f.readText()).jsonArray
            arr.mapNotNull { el ->
                val o = el.jsonObject
                val n = o[nameKey]?.jsonPrimitive?.content
                if (n.isNullOrBlank()) null
                else Player(n, o["uuid"]?.jsonPrimitive?.content ?: "")
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun whitelist(ctx: Context, id: String) = readNames(file(ctx, id, "whitelist.json"), "name")
    fun ops(ctx: Context, id: String) = readNames(file(ctx, id, "ops.json"), "name")
    fun banned(ctx: Context, id: String) =
        readNames(file(ctx, id, "banned-players.json"), "name").ifEmpty {
            readNames(file(ctx, id, "banned-ips.json"), "ip")
        }

    /**
     * 玩家列表：以 usercache.json（服务端记录的所有登录过的玩家）为底，
     * 再并入各名单里出现过的名字，保证只加进白名单、从未上线的玩家也能看到。
     */
    fun allPlayers(ctx: Context, id: String): List<Player> {
        val merged = LinkedHashMap<String, Player>()
        fun addAll(list: List<Player>) = list.forEach { merged.putIfAbsent(it.name, it) }
        addAll(readNames(file(ctx, id, "usercache.json"), "name"))
        addAll(whitelist(ctx, id))
        addAll(ops(ctx, id))
        addAll(banned(ctx, id))
        return merged.values.sortedBy { it.name.lowercase() }
    }

    /** 生成对应的控制台命令 */
    fun commandForWhitelistAdd(name: String) = "whitelist add $name"
    fun commandForWhitelistRemove(name: String) = "whitelist remove $name"
    fun commandForOp(name: String) = "op $name"
    fun commandForDeop(name: String) = "deop $name"
    fun commandForBan(name: String) = "ban $name"
    fun commandForPardon(name: String) = "pardon $name"
    fun commandForKick(name: String) = "kick $name"
}
