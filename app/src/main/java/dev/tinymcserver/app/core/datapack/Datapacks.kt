package dev.tinymcserver.app.core.datapack

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.tinymcserver.app.core.i18n.t
import dev.tinymcserver.app.core.net.Http
import dev.tinymcserver.app.core.plugin.PluginSearch
import dev.tinymcserver.app.core.storage.Paths
import dev.tinymcserver.app.core.storage.ServerProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

/**
 * 数据包（datapack）：放在 `<实例目录>/<level-name>/datapacks/` 下。
 *
 * 与插件不同，数据包是「世界级」的：跟着关卡目录走，换 level-name 就等于换了一套。
 * 支持 .zip 打包的数据包，也支持单个 .mcfunction 直接丢进去。
 */
object Datapacks {

    /** 当前世界的目录名，取自 server.properties 的 level-name，缺省 world */
    fun levelName(ctx: Context, instanceId: String): String {
        val props = File(Paths.instanceDir(ctx, instanceId), "server.properties")
        return runCatching {
            if (props.isFile) ServerProperties.parse(props.readText())["level-name"] else null
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "world"
    }

    /** `<实例目录>/<level-name>/datapacks`，不存在时创建 */
    fun dir(ctx: Context, instanceId: String): File =
        File(File(Paths.instanceDir(ctx, instanceId), levelName(ctx, instanceId)), "datapacks")
            .apply { mkdirs() }

    /** 已安装的数据包（.zip / .mcfunction） */
    fun list(ctx: Context, instanceId: String): List<File> = dir(ctx, instanceId).listFiles()
        ?.filter { it.isFile && isDatapackName(it.name) }
        ?.sortedBy { it.name.lowercase() }
        ?: emptyList()

    fun isDatapackName(name: String): Boolean =
        name.endsWith(".zip", true) || name.endsWith(".mcfunction", true)

    /**
     * 把系统文件选择器选中的文件复制进 datapacks 目录。
     * 后缀不是 .zip / .mcfunction 的一律补 .zip（数据包最常见的就是 zip）。
     *
     * @return 实际落地的文件名
     */
    fun importTo(ctx: Context, instanceId: String, uri: Uri): String {
        val dir = dir(ctx, instanceId)
        var name = displayName(ctx, uri)?.takeIf { it.isNotBlank() } ?: "datapack.zip"
        if (!isDatapackName(name)) name += ".zip"

        val target = uniqueFile(dir, name)
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            FileOutputStream(target).use { outs -> ins.copyTo(outs) }
        } ?: throw RuntimeException(t("无法读取所选文件"))

        if (target.length() == 0L) {
            target.delete()
            throw RuntimeException(t("文件为空"))
        }
        return target.name
    }

    /** 从直链下载数据包 */
    suspend fun download(ctx: Context, instanceId: String, link: String): String =
        withContext(Dispatchers.IO) {
            val dir = dir(ctx, instanceId)
            var name = link.substringAfterLast('/').substringBefore('?')
            if (name.isBlank()) name = "datapack"
            if (!isDatapackName(name)) name += ".zip"
            val dest = uniqueFile(dir, name)
            Http.download(link, dest)
            dest.name
        }

    fun delete(ctx: Context, instanceId: String, name: String): Boolean =
        File(dir(ctx, instanceId), name).let { if (it.isFile) it.delete() else false }

    private fun displayName(ctx: Context, uri: Uri): String? {
        runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    /** 同名时加 -1 / -2 …，避免覆盖已安装的数据包 */
    private fun uniqueFile(dir: File, name: String): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        var f = File(dir, "$base-$i$ext")
        while (f.exists()) {
            i++
            f = File(dir, "$base-$i$ext")
        }
        return f
    }
}

/**
 * Modrinth 数据包搜索。
 *
 * 数据包在 Modrinth 里是 project_type=datapack，与插件不同——它不绑服务端类型，
 * 只跟 MC 版本走，所以筛选条件里不需要 loader。
 */
object DatapackSearch {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private const val API = "https://api.modrinth.com/v2"

    suspend fun search(query: String, mcVersion: String): List<PluginSearch.Hit> =
        withContext(Dispatchers.IO) {
            val facets = buildString {
                append("[")
                append("[\"project_type:datapack\"]")
                if (mcVersion.isNotBlank()) append(",[\"versions:$mcVersion\"]")
                append("]")
            }
            val url = "$API/search?query=${enc(query)}&limit=30&index=downloads&facets=${enc(facets)}"
            val root = json.parseToJsonElement(Http.get(url)).jsonObject
            root["hits"]?.jsonArray?.mapNotNull { el ->
                val o = el.jsonObject
                val id = o["project_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                PluginSearch.Hit(
                    projectId = id,
                    title = o["title"]?.jsonPrimitive?.content ?: id,
                    description = o["description"]?.jsonPrimitive?.content ?: "",
                    downloads = o["downloads"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                    iconUrl = o["icon_url"]?.jsonPrimitive?.content,
                    slug = o["slug"]?.jsonPrimitive?.content ?: id,
                )
            } ?: emptyList()
        }

    /** 取与 MC 版本兼容的最新文件；先按 datapack loader 过滤，取不到再放宽 */
    suspend fun pickDownload(projectId: String, mcVersion: String): PluginSearch.Download? =
        withContext(Dispatchers.IO) {
            val gv = enc("[\"$mcVersion\"]")
            val withLoader = "$API/project/$projectId/version?game_versions=$gv&loaders=" +
                enc("[\"datapack\"]")
            val withoutLoader = "$API/project/$projectId/version?game_versions=$gv"
            val arr = listOf(withLoader, withoutLoader).firstNotNullOfOrNull { u ->
                runCatching { json.parseToJsonElement(Http.get(u)).jsonArray }
                    .getOrNull()?.takeIf { it.isNotEmpty() }
            } ?: return@withContext null

            val first = arr.firstOrNull()?.jsonObject ?: return@withContext null
            val objs = first["files"]?.jsonArray?.map { it.jsonObject } ?: return@withContext null
            val f = objs.firstOrNull { it["primary"]?.jsonPrimitive?.content?.toBoolean() == true }
                ?: objs.firstOrNull { o ->
                    val n = o["filename"]?.jsonPrimitive?.content ?: ""
                    !n.lowercase().contains("sources")
                }
                ?: objs.firstOrNull()
                ?: return@withContext null

            val dl = f["url"]?.jsonPrimitive?.content ?: return@withContext null
            val fn = f["filename"]?.jsonPrimitive?.content ?: "datapack.zip"
            val sz = f["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            val hashes = f["hashes"]?.jsonObject
            PluginSearch.Download(
                url = dl,
                filename = fn,
                size = sz,
                sha512 = hashes?.get("sha512")?.jsonPrimitive?.content.orEmpty(),
                sha1 = hashes?.get("sha1")?.jsonPrimitive?.content.orEmpty(),
            )
        }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
