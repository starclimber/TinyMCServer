package dev.tinymcserver.app.core.frp

import android.content.Context
import android.system.Os
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.tukaani.xz.XZInputStream

/** frp 运行状态 */
enum class FrpState { STOPPED, STARTING, RUNNING, ERROR }

data class FrpRuntime(
    val state: FrpState = FrpState.STOPPED,
    val message: String = "",
    val log: List<String> = emptyList(),
)

/**
 * frp 客户端管理：负责解压二进制、生成配置、启停进程。
 * 单例，按隧道 id 管理多个进程。
 */
object FrpManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtimes = ConcurrentHashMap<String, MutableStateFlow<FrpRuntime>>()
    private val processes = ConcurrentHashMap<String, Process>()
    private val jobs = ConcurrentHashMap<String, Job>()
    /** 记录「是用户主动停止」，用于区分正常停止与崩溃 */
    private val userStops = ConcurrentHashMap<String, Boolean>()

    private const val BIN_NAME = "frpc"

    fun runtime(id: String): StateFlow<FrpRuntime> =
        runtimes.getOrPut(id) { MutableStateFlow(FrpRuntime()) }.asStateFlow()

    private fun update(id: String, block: (FrpRuntime) -> FrpRuntime) {
        runtimes.getOrPut(id) { MutableStateFlow(FrpRuntime()) }.update(block)
    }

    /** 去掉 frp 日志里的 ANSI 颜色/光标控制序列 */
    private val ANSI = Regex("""\u001B\[[0-9;?]*[A-Za-z]""")

    private fun appendLog(id: String, rawLine: String) {
        val line = rawLine.replace(ANSI, "").trimEnd()
        if (line.isBlank()) return
        update(id) { it.copy(log = (it.log + line).takeLast(600)) }
    }

    // ---------------- 二进制准备 ----------------

    private fun binDir(ctx: Context) = File(ctx.filesDir, "frp")

    /** 确保 frpc 已解压且可执行，返回路径 */
    fun ensureBinary(ctx: Context): File {
        val dir = binDir(ctx).apply { mkdirs() }
        val bin = File(dir, BIN_NAME)
        val marker = File(dir, ".version")
        val wantVersion = "0.71.1"
        val versionOk = marker.exists() && marker.readText().trim() == wantVersion
        if (bin.exists() && bin.length() > 1_000_000 && versionOk) {
            runCatching { Os.chmod(bin.absolutePath, 493) } // 0755
            return bin
        }
        // 解压 frpc.xz
        ctx.assets.open("frp/frpc.xz").use { input ->
            XZInputStream(input).use { xz ->
                bin.outputStream().use { out -> xz.copyTo(out) }
            }
        }
        runCatching { Os.chmod(bin.absolutePath, 493) }
        marker.writeText(wantVersion)
        return bin
    }

    // ---------------- 配置生成 ----------------

    /** 根据隧道生成 frpc 配置文本（原样输出，不修改用户配置） */
    fun buildConfig(t: FrpTunnel): String = when (t.format) {
        FrpFormat.TOML -> t.rawConfig
        FrpFormat.INI -> t.rawConfig
        FrpFormat.GUI -> buildToml(t)
    }

    /**
     * 校验「只映射本机」：配置里若写了 localIP/local_ip，必须等于 127.0.0.1。
     * 返回 null 表示通过，否则返回错误提示。
     */
    fun validateLoopback(t: FrpTunnel): String? {
        if (t.format == FrpFormat.GUI) {
            return if (t.localIp == "127.0.0.1") null else "本地地址必须是 127.0.0.1"
        }
        val isIni = t.format == FrpFormat.INI
        val key = if (isIni) "local_ip" else "localIP"
        val re = Regex("""^\s*"?${Regex.escape(key)}"?\s*=\s*"?'?([^"'\s#;]*)""")
        t.rawConfig.lines().forEach { line ->
            val tt = line.trimStart()
            if (tt.startsWith("#") || tt.startsWith(";")) return@forEach
            val m = re.find(line) ?: return@forEach
            val v = m.groupValues[1].trim()
            if (v.isNotEmpty() && v != "127.0.0.1" && v != "localhost") {
                return "配置里的 $key 必须是 127.0.0.1（当前为 $v）"
            }
        }
        return null
    }

    /** 图形模式 → TOML */
    private fun buildToml(t: FrpTunnel): String = buildString {
        appendLine("serverAddr = \"${t.serverAddr}\"")
        appendLine("serverPort = ${t.serverPort}")
        if (t.authToken.isNotBlank()) {
            appendLine()
            appendLine("auth.method = \"token\"")
            appendLine("auth.token = \"${t.authToken}\"")
        }
        appendLine()
        appendLine("[[proxies]]")
        appendLine("name = \"${t.name.ifBlank { "mc-" + t.localPort }}\"")
        appendLine("type = \"${t.protocol}\"")
        appendLine("localIP = \"${t.localIp}\"")
        appendLine("localPort = ${t.localPort}")
        appendLine("remotePort = ${t.remotePort}")
    }

    /** 图形模式 → INI（老版本格式，frp 0.52 前） */
    fun buildIni(t: FrpTunnel): String = buildString {
        appendLine("[common]")
        appendLine("server_addr = ${t.serverAddr}")
        appendLine("server_port = ${t.serverPort}")
        if (t.authToken.isNotBlank()) appendLine("token = ${t.authToken}")
        appendLine()
        appendLine("[${t.name.ifBlank { "mc-" + t.localPort }}]")
        appendLine("type = ${t.protocol}")
        appendLine("local_ip = ${t.localIp}")
        appendLine("local_port = ${t.localPort}")
        appendLine("remote_port = ${t.remotePort}")
    }

    // ---------------- 启停 ----------------

    fun start(ctx: Context, tunnel: FrpTunnel) {
        if (processes[tunnel.id]?.isAlive == true) return
        userStops.remove(tunnel.id)
        update(tunnel.id) { FrpRuntime(state = FrpState.STARTING, message = "连接中", log = it.log) }
        jobs[tunnel.id]?.cancel()
        jobs[tunnel.id] = scope.launch {
            var connected = false
            try {
                val bin = ensureBinary(ctx)
                val cfgDir = File(ctx.filesDir, "frp/${tunnel.id}").apply { mkdirs() }
                val isIni = tunnel.format == FrpFormat.INI
                val cfg = File(cfgDir, if (isIni) "frpc.ini" else "frpc.toml")
                cfg.writeText(buildConfig(tunnel))
                appendLog(tunnel.id, "[Tiny] 配置：${cfg.absolutePath}")

                val pb = ProcessBuilder(bin.absolutePath, "-c", cfg.absolutePath)
                pb.directory(cfgDir)
                pb.redirectErrorStream(true)
                pb.environment()["HOME"] = cfgDir.absolutePath
                pb.environment()["TMPDIR"] = ctx.cacheDir.absolutePath

                val p = pb.start()
                processes[tunnel.id] = p
                appendLog(tunnel.id, "[Tiny] frpc 已启动，等待连接…")

                p.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { raw ->
                        appendLog(tunnel.id, raw)
                        val line = raw.replace(ANSI, "")
                        when {
                            line.contains("start proxy success", true) ||
                                line.contains("login to server success", true) -> {
                                connected = true
                                update(tunnel.id) { it.copy(state = FrpState.RUNNING, message = "已连接") }
                            }
                            line.contains("login to server failed", true) ||
                                line.contains("start error", true) -> {
                                update(tunnel.id) {
                                    it.copy(state = FrpState.ERROR, message = cleanMsg(line))
                                }
                            }
                        }
                    }
                }
                p.waitFor()
                processes.remove(tunnel.id)
                val stoppedByUser = userStops.remove(tunnel.id) == true
                update(tunnel.id) {
                    when {
                        // 用户主动停止：无论读线程抛什么都是正常结束
                        stoppedByUser -> it.copy(state = FrpState.STOPPED, message = "已停止")
                        it.state == FrpState.ERROR -> it
                        else -> it.copy(
                            state = FrpState.ERROR,
                            message = "frpc 意外退出（返回码 ${p.exitValue()}）",
                        )
                    }
                }
            } catch (e: Exception) {
                processes.remove(tunnel.id)
                val stoppedByUser = userStops.remove(tunnel.id) == true
                // 主动停止时 destroy() 会打断读线程并抛 IOException，这不是故障
                if (stoppedByUser) {
                    update(tunnel.id) { it.copy(state = FrpState.STOPPED, message = "已停止") }
                    return@launch
                }
                appendLog(tunnel.id, "[Tiny] 启动失败：${e.message}")
                update(tunnel.id) {
                    it.copy(state = FrpState.ERROR, message = if (connected) "连接已断开" else cleanMsg(e.message ?: "启动失败"))
                }
            }
        }
    }

    private fun cleanMsg(s: String): String =
        s.replace(ANSI, "").trim().take(160)

    fun stop(id: String) {
        userStops[id] = true
        jobs[id]?.cancel()
        jobs.remove(id)
        processes.remove(id)?.let { p ->
            runCatching { p.destroy() }
            scope.launch {
                kotlinx.coroutines.delay(1000)
                runCatching { if (p.isAlive) p.destroyForcibly() }
            }
        }
        update(id) { it.copy(state = FrpState.STOPPED, message = "已停止") }
    }

    fun stopAll() {
        processes.keys.toList().forEach { stop(it) }
    }

    fun clearLog(id: String) {
        update(id) { it.copy(log = emptyList()) }
    }

    fun isRunning(id: String): Boolean = processes[id]?.isAlive == true
}
