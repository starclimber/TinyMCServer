package dev.tinymcserver.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * 服务器图标（server-icon.png）。
 *
 * Minecraft 的约定：服务端根目录下的 `server-icon.png`，必须是 **64×64 的 PNG**，
 * 玩家客户端在服务器列表里显示的就是它。这里统一做「居中裁成正方形 → 缩到 64×64 → 存 PNG」，
 * 用户随便选一张图就行，不用自己裁剪。
 */
object ServerIcon {

    const val FILE_NAME = "server-icon.png"
    const val SIDE = 64

    fun file(instanceDir: File): File = File(instanceDir, FILE_NAME)

    fun exists(instanceDir: File): Boolean = file(instanceDir).isFile

    /** 从相册/文件选择器的 uri 导入，写到 [target]（一般就是 server-icon.png）。 */
    fun importTo(ctx: Context, uri: Uri, target: File): Boolean {
        return try {
            val src = decodeSampled(ctx, uri) ?: return false
            val side = minOf(src.width, src.height)
            val square = if (src.width == side && src.height == side) {
                src
            } else {
                Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            }
            val scaled = if (square.width == SIDE) square else Bitmap.createScaledBitmap(square, SIDE, SIDE, true)
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (square !== src) square.recycle()
            if (scaled !== square) scaled.recycle()
            if (src !== square) src.recycle()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 把已经处理好的 64×64 PNG 复制到实例目录 */
    fun install(source: File, instanceDir: File): Boolean = try {
        instanceDir.mkdirs()
        source.copyTo(file(instanceDir), overwrite = true)
        true
    } catch (e: Exception) {
        false
    }

    fun remove(instanceDir: File): Boolean = try {
        val f = file(instanceDir)
        !f.exists() || f.delete()
    } catch (e: Exception) {
        false
    }

    /** 大图不做全尺寸解码，先算采样率，避免几千万像素的原图把内存打满 */
    private fun decodeSampled(ctx: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        var longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sample > 512) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}
