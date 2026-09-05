package com.lumecard.app.platform

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

actual fun platformLoadImage(path: String): Painter? {
    return try {
        val bytes = java.io.File(path).readBytes()
        val bitmap = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        BitmapPainter(bitmap)
    } catch (_: Exception) { null }
}
