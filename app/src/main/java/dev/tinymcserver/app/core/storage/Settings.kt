package dev.tinymcserver.app.core.storage

import android.content.Context
import dev.tinymcserver.app.core.util.CryptoUtil

/** 全局设置（SharedPreferences） */
object Settings {

    private const val PREF = "tinymc_settings"
    private fun sp(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // 下载镜像（可为空，表示使用默认官方源）
    var mirrorBase: String
        get() = ctxHolder?.let { sp(it).getString("mirror_base", "") } ?: ""
        set(v) { ctxHolder?.let { sp(it).edit().putString("mirror_base", v).apply() } }

    // RCON 密码：Keystore 加密后存储
    fun setRconPassword(ctx: Context, plain: String) {
        val enc = if (plain.isBlank()) "" else CryptoUtil.encrypt(plain)
        sp(ctx).edit().putString("rcon_pwd", enc).apply()
    }

    fun getRconPassword(ctx: Context): String {
        val enc = sp(ctx).getString("rcon_pwd", "") ?: ""
        if (enc.isBlank()) return ""
        return CryptoUtil.decrypt(enc) ?: ""
    }

    // 是否从系统「最近任务」列表中隐藏本应用
    var hideFromRecents: Boolean
        get() = ctxHolder?.let { sp(it).getBoolean("hide_from_recents", false) } ?: false
        set(v) { ctxHolder?.let { sp(it).edit().putBoolean("hide_from_recents", v).apply() } }

    // 分享 / 复制日志时自动脱敏（默认开）：抹掉玩家 IP、UUID 中段与玩家名
    var sanitizeSharedLogs: Boolean
        get() = ctxHolder?.let { sp(it).getBoolean("sanitize_shared_logs", true) } ?: true
        set(v) { ctxHolder?.let { sp(it).edit().putBoolean("sanitize_shared_logs", v).apply() } }

    // 启动时自动检查新版本（默认开）
    var autoCheckUpdate: Boolean
        get() = ctxHolder?.let { sp(it).getBoolean("auto_check_update", true) } ?: true
        set(v) { ctxHolder?.let { sp(it).edit().putBoolean("auto_check_update", v).apply() } }

    // 更新检查的 ETag 缓存（避免重复拉取整份 Release 列表）
    fun updateCacheEtag(ctx: Context): String =
        sp(ctx).getString("update_etag", "") ?: ""

    fun updateCacheBody(ctx: Context): String =
        sp(ctx).getString("update_body", "") ?: ""

    fun saveUpdateCache(ctx: Context, etag: String?, body: String) {
        sp(ctx).edit()
            .putString("update_etag", etag ?: "")
            .putString("update_body", body)
            .apply()
    }

    private var ctxHolder: Context? = null
    fun attach(ctx: Context) { ctxHolder = ctx.applicationContext }
}
