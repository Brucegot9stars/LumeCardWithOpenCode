package com.lumecard.app.platform

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import java.io.File

actual fun platformLoadImage(path: String): Painter? {
    return try {
        val bitmap = BitmapFactory.decodeFile(path)
        if (bitmap != null) {
            BitmapPainter(bitmap.asImageBitmap())
        } else null
    } catch (_: Exception) { null }
}
