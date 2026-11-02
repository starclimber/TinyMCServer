package dev.tinymcserver.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.tinymcserver.app.core.util.ServerIcon
import java.io.File

/**
 * 显示实例的服务器图标（server-icon.png）。没有设置时退化为一个占位方块。
 *
 * [preview] 用于「刚选完图、还没保存」的预览。
 */
@Composable
fun InstanceIcon(
    instanceDir: File,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    preview: File? = null,
    refreshKey: Int = 0,
) {
    val source = preview ?: ServerIcon.file(instanceDir)
    val stamp = if (source.isFile) source.lastModified() else -1L
    val bmp = remember(source.absolutePath, stamp, refreshKey) {
        if (source.isFile) runCatching { BitmapFactory.decodeFile(source.absolutePath) }.getOrNull() else null
    }
    val shape = RoundedCornerShape(size / 6)
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = modifier.size(size).clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}
