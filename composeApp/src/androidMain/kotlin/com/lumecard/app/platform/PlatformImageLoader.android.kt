package com.lumecard.app.platform

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.lumecard.shared.database.AndroidContextHolder
import java.io.File

actual fun platformLoadImage(path: String): Painter? {
    return try {
        val uri = Uri.parse(path)
        val bitmap = if (uri.scheme == "content" || uri.scheme == "file") {
            val context = AndroidContextHolder.context
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } else {
            BitmapFactory.decodeFile(path)
        }
        if (bitmap != null) {
            BitmapPainter(bitmap.asImageBitmap())
        } else null
    } catch (_: Exception) { null }
}
