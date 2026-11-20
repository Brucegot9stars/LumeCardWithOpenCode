package com.lumecard.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.net.URI
import java.net.HttpURLConnection

// Keep desktop in sync with Android's MAX_DECODE_EDGE so a huge image never allocates a full bitmap.
private const val MAX_DECODE_EDGE = 2048

actual fun platformLoadImage(path: String): Painter? {
    return try {
        val bytes = File(path).readBytes()
        decodeImageBitmap(bytes)?.let { BitmapPainter(it) }
    } catch (_: Throwable) { null }
}

actual fun platformDecodeImage(bytes: ByteArray): ImageBitmap? = try {
    decodeImageBitmap(bytes)
} catch (_: Throwable) { null }

/**
 * Decode through Skia Codec with power-of-two downsampling so an oversized source is never
 * decoded at full resolution. This skiko Codec API scales by passing a smaller Bitmap to
 * readPixels (a size different from imageInfo is treated as a scale request).
 */
private fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
    val fullEdge = maxOf(codec.imageInfo.width, codec.imageInfo.height)
    var sample = 1
    while (fullEdge / sample > MAX_DECODE_EDGE) sample *= 2
    val scaledWidth = codec.imageInfo.width / sample
    val scaledHeight = codec.imageInfo.height / sample
    if (scaledWidth <= 0 || scaledHeight <= 0) return null
    val info = ImageInfo(scaledWidth, scaledHeight, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
    val bitmap = Bitmap()
    if (!bitmap.allocPixels(info)) return null
    codec.readPixels(bitmap) // throws if the codec cannot scale; caller catches Throwable
    return SkiaImage.makeFromBitmap(bitmap).toComposeImageBitmap()
}

actual fun platformReadImageSourceBytes(source: String): ByteArray? = try {
    when {
        source.startsWith("content://", ignoreCase = true) -> null // content URIs are Android-only
        source.startsWith("file://", ignoreCase = true) -> File(URI(source)).takeIf { it.isFile }?.readBytes()
        else -> File(source).takeIf { it.isFile }?.readBytes()
    }
} catch (_: Exception) { null }

/**
 * Downscale to [maxEdge] and re-encode in the original format so transparency survives.
 * Returns null when no work is needed or the source cannot be handled, letting the caller keep
 * the untouched bytes.
 */
actual fun platformCompressImage(bytes: ByteArray, maxEdge: Int, sourceExtension: String): ByteArray? = try {
    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
    val fullEdge = maxOf(codec.imageInfo.width, codec.imageInfo.height)
    if (maxEdge <= 0 || fullEdge <= maxEdge) {
        null
    } else {
        val scaledWidth = (codec.imageInfo.width.toLong() * maxEdge / fullEdge).toInt().coerceAtLeast(1)
        val scaledHeight = (codec.imageInfo.height.toLong() * maxEdge / fullEdge).toInt().coerceAtLeast(1)
        val info = ImageInfo(scaledWidth, scaledHeight, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        val bitmap = Bitmap()
        if (!bitmap.allocPixels(info)) null else {
            codec.readPixels(bitmap)
            bitmap.setImmutable()
            val image = SkiaImage.makeFromBitmap(bitmap)
            // Transparency must survive, so PNG-ish sources stay PNG. Everything else is a photo-like
            // format where JPEG at high quality gives a large size win with little visible loss.
            val keepAlpha = sourceExtension.lowercase() in setOf("png", "gif", "webp", "bmp")
            val format = if (keepAlpha) EncodedImageFormat.PNG else EncodedImageFormat.JPEG
            val quality = if (keepAlpha) 100 else 85
            image.encodeToData(format, quality)?.bytes
        }
    }
} catch (_: Throwable) {
    null
}

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
                if (!connection.url.protocol.equals("https", ignoreCase = true)) return null
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
