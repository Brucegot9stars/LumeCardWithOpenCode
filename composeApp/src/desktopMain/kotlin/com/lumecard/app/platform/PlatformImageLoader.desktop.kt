package com.lumecard.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import java.io.File
import java.net.URI
import java.net.HttpURLConnection

actual fun platformLoadImage(path: String): Painter? {
    return try {
        val bytes = java.io.File(path).readBytes()
        val bitmap = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        BitmapPainter(bitmap)
    } catch (_: Exception) { null }
}

actual fun platformDecodeImage(bytes: ByteArray): ImageBitmap? = try {
    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (_: Exception) { null }

actual fun platformReadImageSourceBytes(source: String): ByteArray? = try {
    when {
        source.startsWith("content://", ignoreCase = true) -> null // content URIs are Android-only
        source.startsWith("file://", ignoreCase = true) -> File(URI(source)).takeIf { it.isFile }?.readBytes()
        else -> File(source).takeIf { it.isFile }?.readBytes()
    }
} catch (_: Exception) { null }

actual fun platformFetchImageBytes(url: String): ByteArray? {
    return try {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "image/*,*/*;q=0.8")
        }
        try {
            if (connection.responseCode !in 200..299) {
                null
            } else if (connection.contentLength > 20 * 1024 * 1024) {
                null
            } else {
                connection.inputStream.use { input ->
                    val buffer = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var total = 0
                    var oversized = false
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        total += read
                        if (total > 20 * 1024 * 1024) { oversized = true; break }
                        buffer.write(chunk, 0, read)
                    }
                    if (oversized) null else buffer.toByteArray()
                }
            }
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) { null }
}
