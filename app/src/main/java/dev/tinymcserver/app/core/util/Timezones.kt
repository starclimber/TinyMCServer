package dev.tinymcserver.app.core.util

import java.io.File
import java.util.TimeZone

/**
 * 子进程时区处理。
 *
 * Android 上没有 `/etc/localtime`，服务端 JRE 与 frpc 都取不到系统时区，于是全部退化为 UTC，
 * 日志时间戳比用户本地时间差好几个小时（中国差 8 小时，欧洲差 1~2 小时）。
 *
 * 这里一律按**设备当前时区**来定，不写死任何偏移：
 *  - JVM 侧用 `-Duser.timezone=<时区 id>`，JRE 自带 tzdb，任何时区（含夏令时）都正确；
 *  - Go 程序（frpc）只能靠 `TZ` 环境变量，优先指向系统 zoneinfo 里的真实时区文件，
 *    取不到时退化为 POSIX 固定偏移（对无夏令时的时区完全正确，有夏令时的时区最多差 1 小时，
 *    且不会像现在这样差整整 8 小时）。
 */
object Timezones {

    /** 设备当前时区 id，例如 `Asia/Shanghai`、`Europe/Berlin`、`America/New_York` */
    fun deviceId(): String = TimeZone.getDefault().id

    /**
     * 给 frpc（Go）用的 `TZ` 值。
     * Go 支持 `TZ=:<绝对路径>` 直接读 TZif 文件，这样夏令时也能正确处理。
     */
    fun tzEnvValue(): String {
        val id = deviceId()
        val zi = File("/system/usr/share/zoneinfo/$id")
        // 注意必须是绝对路径：Go 会先按位置名去它自己的 zoneinfo 列表里找，
        // Android 的 /system/usr/share/zoneinfo 不在那个列表里，只有绝对路径才会被直接读取。
        if (zi.isFile) return ":${zi.absolutePath}"
        // 系统 zoneinfo 不可用（部分定制 ROM）：退化成 POSIX 固定偏移
        return posixFixed(TimeZone.getDefault())
    }

    /** 生成 POSIX TZ 字符串，如 UTC+8 → `TZX-8`，UTC-5 → `TZX+5` */
    private fun posixFixed(tz: TimeZone): String {
        val offsetMinutes = tz.getOffset(System.currentTimeMillis()) / 60_000
        val sign = if (offsetMinutes > 0) "-" else "+"
        val abs = if (offsetMinutes < 0) -offsetMinutes else offsetMinutes
        val hh = abs / 60
        val mm = abs % 60
        return buildString {
            append("TZX")
            append(sign)
            append(hh)
            if (mm != 0) {
                append(':')
                if (mm < 10) append('0')
                append(mm)
            }
        }
    }
}
