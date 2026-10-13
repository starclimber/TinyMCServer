package dev.tinymcserver.app.core.update

import dev.tinymcserver.app.core.net.Http
import dev.tinymcserver.app.core.storage.Settings
import org.json.JSONArray
import org.json.JSONObject

/** 单个 Release 的更新说明 */
data class ReleaseNote(
    /** 去掉前缀 v 的版本号，如 "1.2.1" */
    val version: String,
    /** 原始 tag，如 "v1.2.1" */
    val tag: String,
    /** Release 标题 */
    val title: String,
    /** 更新说明（Markdown 原文） */
    val body: String,
    /** 发布时间（ISO8601） */
    val publishedAt: String,
)

/** 一次更新检查的结果 */
data class UpdateResult(
    val currentVersion: String,
    /** 最新版本（无更新时等于当前版本） */
    val latestVersion: String,
    val hasUpdate: Boolean,
    /** 最新版的 APK 直链（该 Release 挂了 .apk 资源时才有） */
    val apkUrl: String?,
    /** Release 页面地址 */
    val pageUrl: String,
    /** 比当前版本新的所有 Release，从新到旧 */
    val releases: List<ReleaseNote>,
)

/**
 * 应用内更新检查：查询 GitHub Release 列表，与当前版本比较，并收集更新日志。
 *
 * 只用公开 API（未认证，每小时 60 次限额，本场景绰绰有余）。
 */
object UpdateChecker {

    /**
     * 更新源，按顺序尝试，第一个成功的即采用：
     *   1. HTTPS 代理（自建，最快）
     *   2. HTTP 代理（同上，HTTPS 证书未就绪时的回退）
     *   3. GitHub 官方 API（最终回退）
     *
     * 代理是「透明」的 —— 返回的 JSON 结构与 GitHub 完全一致，因此解析逻辑共用。
     */
    private val SOURCES = listOf(
        "https://tinymcserver-releases.quest.l.cd/api",
        "http://tinymcserver-releases.quest.l.cd/api",
        "https://api.github.com/repos/starclimber/TinyMCServer/releases?per_page=20",
    )

    private const val API_RELEASES =
        "https://api.github.com/repos/starclimber/TinyMCServer/releases?per_page=20"

    private const val REPO = "https://github.com/starclimber/TinyMCServer"

    /** 读取已安装应用的 versionName。 */
    fun currentVersion(ctx: android.content.Context): String =
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "0"

    /**
     * 检查更新。
     *
     * @return 始终返回结果对象；[UpdateResult.hasUpdate] 为 false 表示已是最新。
     * @throws Exception 网络失败 / 解析失败 —— 由调用方决定如何提示。
     */
    fun check(currentVersion: String, ctx: android.content.Context? = null): UpdateResult {
        val arr = JSONArray(fetchReleaseList(ctx))

        val newer = mutableListOf<ReleaseNote>()
        var latestVersion = currentVersion
        var apkUrl: String? = null
        var pageUrl = "$REPO/releases"

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("draft", false)) continue
            if (o.optBoolean("prerelease", false)) continue

            val tag = o.optString("tag_name").trim()
            if (tag.isEmpty()) continue
            val ver = tag.trimStart('v', 'V')
            if (!isNewer(ver, currentVersion)) continue

            newer += ReleaseNote(
                version = ver,
                tag = tag,
                title = o.optString("name").trim().ifBlank { tag },
                body = o.optString("body").trim(),
                publishedAt = o.optString("published_at").trim(),
            )

            if (isNewer(ver, latestVersion)) {
                latestVersion = ver
                pageUrl = o.optString("html_url").trim().ifBlank { pageUrl }
                apkUrl = pickApk(o)
            }
        }

        // GitHub 已按时间倒序返回，这里再按版本号兜底排一次（新 → 旧）
        val sorted = newer.sortedWith { a, b -> compareVersions(b.version, a.version) }

        return UpdateResult(
            currentVersion = currentVersion,
            latestVersion = if (sorted.isEmpty()) currentVersion else sorted.first().version,
            hasUpdate = sorted.isNotEmpty(),
            apkUrl = apkUrl,
            pageUrl = pageUrl,
            releases = sorted,
        )
    }

    /**
     * 依次尝试各个更新源，返回 Release 列表 JSON 文本。
     *
     * 带 ETag 条件请求：若内容没变，服务端返回 304（0 字节），直接复用上次缓存。
     */
    private fun fetchReleaseList(ctx: android.content.Context?): String {
        var last: Throwable? = null
        val cachedBody = ctx?.let { Settings.updateCacheBody(it) }.orEmpty()
        val cachedEtag = ctx?.let { Settings.updateCacheEtag(it) }.orEmpty()

        for (url in SOURCES) {
            try {
                val (body, etag) = Http.getConditional(url, cachedEtag.ifBlank { null })
                if (body != null) {
                    ctx?.let { Settings.saveUpdateCache(it, etag, body) }
                    return body
                }
                // 304：内容未变，用缓存
                if (cachedBody.isNotBlank()) return cachedBody
                // 有 ETag 但没有缓存（异常情况）：无条件再取一次
                val (b2, e2) = Http.getConditional(url, null)
                if (b2 != null) {
                    ctx?.let { Settings.saveUpdateCache(it, e2, b2) }
                    return b2
                }
            } catch (e: Throwable) {
                last = e
            }
        }
        throw last ?: RuntimeException("no update source available")
    }

    /** latest 是否比 current 新（逐段比较点分数字，容忍 "1.2" 与 "1.2.0" 这类差异）。 */
    fun isNewer(latest: String, current: String): Boolean = compareVersions(latest, current) > 0

    private fun compareVersions(a: String, b: String): Int {
        val x = parse(a)
        val y = parse(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p.compareTo(q)
        }
        return 0
    }

    private fun parse(v: String): List<Int> =
        v.trim()
            .trimStart('v', 'V')
            .split('.', '-', '+', ' ')
            .map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    private fun pickApk(o: JSONObject): String? {
        val arr = o.optJSONArray("assets") ?: return null
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                return a.optString("browser_download_url").ifBlank { null }
            }
        }
        return null
    }
}
