package dev.tinymcserver.app.core.util

import java.io.File
import java.security.MessageDigest

/**
 * 文件哈希计算与完整性校验。
 *
 * 下载的服务端 jar / 插件 jar 都在落盘后与上游公布的摘要比对：
 * 不一致就删除并报错，避免半截或被篡改的文件被当成可用资源。
 */
object HashUtil {

    /** 支持的算法名（就是 [MessageDigest] 的标准名） */
    const val SHA256 = "SHA-256"
    const val SHA1 = "SHA-1"
    const val SHA512 = "SHA-512"
    const val MD5 = "MD5"

    /** 流式计算文件摘要（支持大文件，128KB 缓冲，不会一次读进内存）。 */
    fun hash(file: File, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        file.inputStream().use { ins ->
            val buf = ByteArray(128 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File): String = hash(file, SHA256)
    fun sha1(file: File): String = hash(file, SHA1)
    fun sha512(file: File): String = hash(file, SHA512)
    fun md5(file: File): String = hash(file, MD5)

    /**
     * 把 [expected] 与文件实际摘要比对。
     * @return null 表示上游没给摘要（无法校验）；true/false 为比对结果。
     */
    fun verify(file: File, expected: String?, algorithm: String): Boolean? {
        val exp = expected?.trim().orEmpty()
        if (exp.isBlank()) return null
        val actual = hash(file, algorithm)
        return actual.equals(exp, ignoreCase = true)
    }
}
