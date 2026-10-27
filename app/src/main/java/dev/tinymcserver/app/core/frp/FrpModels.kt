package dev.tinymcserver.app.core.frp

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** 配置来源：图形表单 / 直接写 TOML / 直接写 INI */
enum class FrpFormat { GUI, TOML, INI }

/** 一条 frp 隧道配置。一个隧道对应一张卡片。 */
@Serializable
data class FrpTunnel(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val format: FrpFormat = FrpFormat.GUI,
    val enabled: Boolean = true,
    /** 关联启动：该隧道绑定的服务器实例启动后自动拉起 frp */
    val autoStart: Boolean = true,
    /** 绑定的服务器实例 id；空表示手动启动、不绑定 */
    val instanceId: String = "",
    val serverAddr: String = "",
    val serverPort: Int = 7000,
    val authToken: String = "",
    /** 本地回环地址，固定 127.0.0.1，不支持改 */
    val localIp: String = "127.0.0.1",
    val localPort: Int = 25565,
    val remotePort: Int = 0,
    val protocol: String = "tcp",
    /** TOML / INI 模式下的原始配置文本 */
    val rawConfig: String = "",
)

/** 隧道持久化（JSON 文件） */
object FrpStore {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(ctx: Context) = File(ctx.filesDir, "frp/tunnels.json")

    fun load(ctx: Context): List<FrpTunnel> = runCatching {
        val f = file(ctx)
        if (!f.exists()) emptyList()
        else json.decodeFromString<List<FrpTunnel>>(f.readText())
    }.getOrDefault(emptyList())

    fun save(ctx: Context, list: List<FrpTunnel>) {
        runCatching {
            val f = file(ctx)
            f.parentFile?.mkdirs()
            f.writeText(json.encodeToString(list))
        }
    }

    fun upsert(ctx: Context, tunnel: FrpTunnel): List<FrpTunnel> {
        val list = load(ctx).toMutableList()
        val i = list.indexOfFirst { it.id == tunnel.id }
        if (i >= 0) list[i] = tunnel else list.add(tunnel)
        save(ctx, list)
        return list
    }

    fun remove(ctx: Context, id: String): List<FrpTunnel> {
        val list = load(ctx).filterNot { it.id == id }
        save(ctx, list)
        return list
    }
}
