package dev.tinymcserver.app.core.server

import dev.tinymcserver.app.core.model.ServerType
import dev.tinymcserver.app.core.net.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import dev.tinymcserver.app.core.i18n.t

/**
 * 各服务端发行版的版本列表与下载地址解析。
 *  - Paper / Folia : fill.papermc.io v3
 *  - Purpur        : api.purpurmc.org v2
 *  - Vanilla       : Mojang launchermeta
 */
object ServerProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val PAPER_API = "https://fill.papermc.io/v3"
    private const val PURPUR_API = "https://api.purpurmc.org/v2"
    private const val MOJANG_MANIFEST =
        "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"

    @Volatile
    private var vanillaManifest: JsonObject? = null

    /**
     * 版本列表。解析异常 / 空列表一律**抛异常**（而不是返回空列表），
     * 这样界面能给出明确原因和「重试」入口，也不会把空结果缓存下来。
     */
    fun listVersions(type: ServerType): List<String> {
        val raw = when (type) {
            ServerType.VANILLA -> vanillaVersions()
            ServerType.PAPER -> paperVersions("paper")
            ServerType.FOLIA -> paperVersions("folia")
            ServerType.PURPUR -> purpurVersions()
        }
        val stable = raw.filter { VersionUtil.isStable(it) }
        if (stable.isEmpty()) {
            throw RuntimeException(t("%s 没有可用版本，请稍后重试", type.display))
        }
        return stable
    }

    /**
     * 服务端 jar 的下载信息。
     *
     * 不同发行版公布摘要用的算法不一样，统一在 [algorithm] 里说明：
     *  - Paper / Folia：SHA-256
     *  - Vanilla      ：SHA-1
     *  - Purpur       ：MD5
     * 上游没给摘要时 [checksum] 为空，此时只能校验大小。
     */
    data class ServerDownload(
        val url: String,
        val size: Long = 0L,
        val algorithm: String = "",
        val checksum: String = "",
    )

    fun serverDownload(type: ServerType, version: String): ServerDownload = when (type) {
        ServerType.VANILLA -> vanillaDownload(version)
        ServerType.PAPER -> paperDownload("paper", version)
        ServerType.FOLIA -> paperDownload("folia", version)
        ServerType.PURPUR -> purpurDownload(version)
    }

    fun serverJarUrl(type: ServerType, version: String): String = when (type) {
        ServerType.VANILLA -> vanillaJar(version)
        ServerType.PAPER -> paperJar("paper", version)
        ServerType.FOLIA -> paperJar("folia", version)
        ServerType.PURPUR -> "$PURPUR_API/purpur/$version/latest/download"
    }

    // ---------------- Vanilla ----------------
    private fun manifest(): JsonObject {
        vanillaManifest?.let { return it }
        val root = json.parseToJsonElement(Http.get(MOJANG_MANIFEST)).jsonObject
        vanillaManifest = root
        return root
    }

    private fun vanillaVersions(): List<String> {
        val arr = manifest()["versions"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            if (o["type"]?.jsonPrimitive?.content == "release") o["id"]?.jsonPrimitive?.content else null
        }
    }

    private fun vanillaDownload(version: String): ServerDownload {
        val arr = manifest()["versions"]?.jsonArray ?: throw RuntimeException("no manifest")
        val url = arr.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == version }
            ?.jsonObject?.get("url")?.jsonPrimitive?.content
            ?: throw RuntimeException("version $version not found")
        val vjson = json.parseToJsonElement(Http.get(url)).jsonObject
        val server = vjson["downloads"]?.jsonObject?.get("server")?.jsonObject
            ?: throw RuntimeException(t("vanilla %s 无服务端下载", version))
        return ServerDownload(
            url = server["url"]?.jsonPrimitive?.content
                ?: throw RuntimeException(t("vanilla %s 无下载地址", version)),
            size = server["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            algorithm = "SHA-1",
            checksum = server["sha1"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    private fun vanillaJar(version: String): String {
        val arr = manifest()["versions"]?.jsonArray ?: throw RuntimeException("no manifest")
        val url = arr.firstOrNull { it.jsonObject["id"]?.jsonPrimitive?.content == version }
            ?.jsonObject?.get("url")?.jsonPrimitive?.content
            ?: throw RuntimeException("version $version not found")
        val vjson = json.parseToJsonElement(Http.get(url)).jsonObject
        return vjson["downloads"]?.jsonObject?.get("server")?.jsonObject
            ?.get("url")?.jsonPrimitive?.content
            ?: throw RuntimeException(t("vanilla %s 无服务端下载", version))
    }

    // ---------------- Paper / Folia (fill v3) ----------------
    private fun paperVersions(project: String): List<String> {
        val root = json.parseToJsonElement(Http.get("$PAPER_API/projects/$project")).jsonObject
        val versions = root["versions"]?.jsonObject
            ?: throw RuntimeException(t("接口返回异常（%s）：缺少 versions 字段", project))
        val out = ArrayList<String>()
        for ((_, v) in versions) {
            runCatching { v.jsonArray.forEach { out.add(it.jsonPrimitive.content) } }
        }
        if (out.isEmpty()) {
            throw RuntimeException(t("%s 的版本列表为空，接口可能暂时不可用", project))
        }
        return out
    }

    private fun paperDownload(project: String, version: String): ServerDownload {
        val arr = json.parseToJsonElement(
            Http.get("$PAPER_API/projects/$project/versions/$version/builds")
        ).jsonArray
        val latest = arr.maxByOrNull { it.jsonObject["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1 }
            ?: throw RuntimeException(t("%s %s 无构建", project, version))
        val d = latest.jsonObject["downloads"]?.jsonObject?.get("server:default")?.jsonObject
            ?: throw RuntimeException(t("%s %s 无下载地址", project, version))
        return ServerDownload(
            url = d["url"]?.jsonPrimitive?.content
                ?: throw RuntimeException(t("%s %s 无下载地址", project, version)),
            size = d["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            algorithm = "SHA-256",
            // Paper v3 把摘要放在 downloads["server:default"].checksums.sha256
            checksum = d["checksums"]?.jsonObject?.get("sha256")?.jsonPrimitive?.content
                ?: d["sha256"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    private fun paperJar(project: String, version: String): String {
        val arr = json.parseToJsonElement(
            Http.get("$PAPER_API/projects/$project/versions/$version/builds")
        ).jsonArray
        val latest = arr.maxByOrNull { it.jsonObject["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1 }
            ?: throw RuntimeException(t("%s %s 无构建", project, version))
        return latest.jsonObject["downloads"]?.jsonObject?.get("server:default")
            ?.jsonObject?.get("url")?.jsonPrimitive?.content
            ?: throw RuntimeException(t("%s %s 无下载地址", project, version))
    }

    // ---------------- Purpur ----------------

    private fun purpurDownload(version: String): ServerDownload {
        val info = runCatching {
            json.parseToJsonElement(Http.get("$PURPUR_API/purpur/$version")).jsonObject
        }.getOrNull()
        val latestBuild = info?.get("builds")?.jsonObject?.get("latest")?.jsonPrimitive?.content

        var md5 = ""
        if (!latestBuild.isNullOrBlank()) {
            md5 = runCatching {
                val b = json.parseToJsonElement(
                    Http.get("$PURPUR_API/purpur/$version/$latestBuild")
                ).jsonObject
                // v2 接口把 md5 放在顶层；个别版本嵌在 result 里
                b["md5"]?.jsonPrimitive?.content
                    ?: b["result"]?.jsonObject?.get("md5")?.jsonPrimitive?.content
                    ?: ""
            }.getOrDefault("")
        }

        return ServerDownload(
            url = "$PURPUR_API/purpur/$version/latest/download",
            size = 0L,
            algorithm = if (md5.isBlank()) "" else "MD5",
            checksum = md5,
        )
    }
    private fun purpurVersions(): List<String> {
        val root = json.parseToJsonElement("${Http.get("$PURPUR_API/purpur")}").jsonObject
        val arr = root["versions"]?.jsonArray
            ?: throw RuntimeException(t("接口返回异常（%s）：缺少 versions 字段", "purpur"))
        if (arr.isEmpty()) {
            throw RuntimeException(t("%s 的版本列表为空，接口可能暂时不可用", "purpur"))
        }
        return arr.map { it.jsonPrimitive.content }.reversed()
    }
}
