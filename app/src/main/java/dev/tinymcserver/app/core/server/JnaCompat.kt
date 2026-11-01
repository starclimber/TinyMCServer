package dev.tinymcserver.app.core.server

import android.content.res.AssetManager
import java.io.File

/**
 * 让服务端自带的 JNA 在 Android（bionic）上能加载。
 *
 * 背景：Paper 系服务端用 oshi 采集硬件信息（SystemReport），oshi 依赖 JNA；
 * 而 JNA 官方 jar 里带的是 **glibc** 版 libjnidispatch.so，dlopen 时会去找 libc.so.6，
 * 在 Android 上必然失败，于是控制台出现
 * `Did not JNA classes...` / `dlopen failed: library "libc.so.6" not found` 这类红字。
 * oshi 自己会降级（不影响启动），但日志很难看，也给排查真实问题添乱。
 *
 * 做法：把 JNA 官方发布的 **Android(aar)** 构建里的 libjnidispatch.so（只依赖
 * libc.so/libdl.so/libm.so，符号标签是 bionic 的 LIBC）解到实例目录，
 * 再用 `-Djna.boot.library.path` 让 JNA 优先加载它（该属性在 Native.loadNative 里第一步生效）。
 *
 * 版本兼容：JNA 的校验规则是「主版本相同 + 原生库次版本 >= jar 次版本」
 * （见 com.sun.jna.Native.isCompatibleVersion），所以内置一份较新的 5.x 原生库
 * 即可满足当前所有 5.x 的 jna.jar。
 */
object JnaCompat {

    private const val ASSET_PATH = "jna/libjnidispatch.so"

    /** 内置原生库的 JNA 版本（来自 net.java.dev.jna:jna:5.19.1 的 aar） */
    private const val BUILTIN_MAJOR = 5
    private const val BUILTIN_MINOR = 19

    private val jnaJarRe = Regex("""^jna-(\d+)\.(\d+)\.(\d+)(?:[.-][A-Za-z0-9]+)*\.jar$""")

    /**
     * 准备好本实例的 JNA 原生库目录。
     *
     * @return 原生库所在目录（可直接用作 `-Djna.boot.library.path`），null 表示本次不启用
     *         （服务端用的是不兼容的 JNA 版本，此时保持原样更安全）。
     */
    fun prepare(assets: AssetManager, instanceDir: File): File? {
        val detected = detectServerJna(File(instanceDir, "libraries"))
        if (detected != null) {
            val (major, minor) = detected
            // JNA 6.x 或比内置更新的次版本：不能拿 5.x 原生库顶替，否则会抛版本不兼容错误
            if (major != BUILTIN_MAJOR || minor > BUILTIN_MINOR) return null
        }
        return try {
            val dir = File(instanceDir, "jna")
            val lib = File(dir, "libjnidispatch.so")
            val bytes = assets.open(ASSET_PATH).use { it.readBytes() }
            dir.mkdirs()
            // 按大小比对：App 升级后自动替换旧文件，避免一直用旧的原生库
            if (!lib.isFile || lib.length() != bytes.size.toLong()) {
                lib.writeBytes(bytes)
                lib.setReadable(true, true)
                lib.setExecutable(true, true)
            }
            dir
        } catch (e: Exception) {
            null
        }
    }

    /** 找到服务端实际使用的 jna.jar 版本；找不到返回 null（说明它自带/没有 JNA）。 */
    private fun detectServerJna(libraries: File): Pair<Int, Int>? {
        if (!libraries.isDirectory) return null
        // Paper/Paperclip 的标准布局：libraries/net/java/dev/jna/jna/<version>/jna-<version>.jar
        val standard = File(libraries, "net/java/dev/jna/jna")
        var best: Pair<Int, Int>? = null
        val roots = if (standard.isDirectory) listOf(standard) else listOf(libraries)
        for (root in roots) {
            if (!root.isDirectory) continue
            root.walkTopDown().maxDepth(10).forEach { f ->
                if (!f.isFile) return@forEach
                val m = jnaJarRe.matchEntire(f.name) ?: return@forEach
                val major = m.groupValues[1].toIntOrNull() ?: return@forEach
                val minor = m.groupValues[2].toIntOrNull() ?: return@forEach
                val cur = best
                if (cur == null || minor > cur.second) best = major to minor
            }
        }
        return best
    }
}
