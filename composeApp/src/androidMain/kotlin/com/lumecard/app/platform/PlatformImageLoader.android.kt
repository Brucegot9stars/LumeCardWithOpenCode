package com.lumecard.app.platform

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.lumecard.shared.database.AndroidContextHolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlin.math.max

private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
private const val MAX_DECODE_EDGE = 2048

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

actual fun platformDecodeImage(bytes: ByteArray): ImageBitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            val edge = max(bounds.outWidth, bounds.outHeight)
            while (edge / sample > MAX_DECODE_EDGE) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        }
    } catch (_: Exception) { null }
}

actual fun platformReadImageSourceBytes(source: String): ByteArray? = try {
    val uri = Uri.parse(source)
    when {
        uri.scheme == "content" || uri.scheme == "file" ->
            AndroidContextHolder.context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        else -> File(source).takeIf { it.isFile }?.readBytes()
    }
} catch (_: Exception) { null }

/**
 * Downscale to [maxEdge] and re-encode in the original format so transparency survives.
 * Uses BitmapFactory inSampleSize for the heavy reduction, then scales exactly so the result is not
 * bound to power-of-two factors. Returns null when no work is needed or decoding fails.
 */
actual fun platformCompressImage(bytes: ByteArray, maxEdge: Int, sourceExtension: String): ByteArray? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val edge = max(bounds.outWidth, bounds.outHeight)
        if (maxEdge <= 0 || bounds.outWidth <= 0 || bounds.outHeight <= 0 || edge <= maxEdge) {
            null
        } else {
            var sample = 1
            while (edge / sample > maxEdge) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
            ) ?: return null

            val currentEdge = max(decoded.width, decoded.height)
            val scaled = if (currentEdge <= maxEdge) {
                decoded
            } else {
                val targetWidth = (decoded.width.toLong() * maxEdge / currentEdge).toInt().coerceAtLeast(1)
                val targetHeight = (decoded.height.toLong() * maxEdge / currentEdge).toInt().coerceAtLeast(1)
                android.graphics.Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
                    .also { if (it !== decoded) decoded.recycle() }
            }

            val keepAlpha = sourceExtension.lowercase() in setOf("png", "gif", "webp", "bmp")
            val format = if (keepAlpha) {
                android.graphics.Bitmap.CompressFormat.PNG
            } else {
                android.graphics.Bitmap.CompressFormat.JPEG
            }
            val quality = if (keepAlpha) 100 else 85
            val output = ByteArrayOutputStream()
            val ok = scaled.compress(format, quality, output)
            if (scaled !== decoded) scaled.recycle()
            if (ok) output.toByteArray() else null
        }
    } catch (_: Throwable) {
        null
    }
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
            } else if (connection.contentLength > MAX_IMAGE_BYTES) {
                null
            } else {
                if (!connection.url.protocol.equals("https", ignoreCase = true)) return null
                connection.inputStream?.use { input ->
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var total = 0
                    var oversized = false
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        total += read
                        if (total > MAX_IMAGE_BYTES) { oversized = true; break }
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
