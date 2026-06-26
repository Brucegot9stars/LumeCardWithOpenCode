package com.lumecard.app.platform

import android.content.ClipboardManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.lumecard.shared.model.Rating
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import android.content.Context
import android.net.Uri
import com.lumecard.shared.database.AndroidContextHolder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipOutputStream

actual fun isDesktopPlatform(): Boolean = false

actual fun scanMediaDirectory(basePath: String): List<MediaFileEntry> {
    val dir = File(basePath)
    if (!dir.exists() || !dir.isDirectory) return emptyList()

    return dir.walkTopDown().filter { it.isFile }.map { file ->
        val relativePath = file.toPath().normalize().toString()
            .removePrefix(dir.toPath().normalize().toString() + File.separator)
            .replace("\\", "/")
        MediaFileEntry(
            relativePath = relativePath,
            size = file.length(),
            hash = file.sha1(),
            mtime = file.lastModified()
        )
    }.toList()
}

actual fun scanMediaDirectoryRaw(basePath: String): List<RawFileEntry> {
    val dir = File(basePath)
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    return dir.walkTopDown().filter { it.isFile }.map { file ->
        val relativePath = file.toPath().normalize().toString()
            .removePrefix(dir.toPath().normalize().toString() + File.separator)
            .replace("\\", "/")
        RawFileEntry(relativePath, file.length(), file.lastModified())
    }.toList()
}

actual fun hashFileSha1(absPath: String): String {
    return File(absPath).sha1()
}

actual fun createZipPackage(outputPath: String, entries: List<ZipEntry>) {
    ZipOutputStream(FileOutputStream(outputPath)).use { zos ->
        for (entry in entries) {
            val source = File(entry.sourceFile)
            if (!source.exists()) continue
            zos.putNextEntry(java.util.zip.ZipEntry(entry.path))
            source.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }
}

actual fun getMediaBasePath(): String {
    return "${AndroidContextHolder.context.filesDir.absolutePath}/media"
}

actual fun pasteClipboardMedia(mediaDir: String): List<String> {
    val refs = mutableListOf<String>()
    try {
        val context = AndroidContextHolder.context
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return refs
        val desc = clip.description ?: return refs
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i) ?: continue
            val mime = if (i < desc.mimeTypeCount) desc.getMimeType(i) else null
            val bytes = item.uri?.let { uri -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                ?: continue
            val ext = when {
                mime?.startsWith("image/") == true -> "png"
                mime?.startsWith("audio/") == true -> "mp3"
                mime?.startsWith("video/") == true -> "mp4"
                else -> "bin"
            }
            val digest = MessageDigest.getInstance("SHA-1")
            val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
            val fileName = "$hash.$ext"
            val dir = File(mediaDir)
            if (!dir.exists()) dir.mkdirs()
            File(dir, fileName).outputStream().use { it.write(bytes) }
            val ref = when {
                mime?.startsWith("image/") == true -> "![image]($fileName)"
                mime?.startsWith("audio/") == true -> "[sound:$fileName]"
                mime?.startsWith("video/") == true -> "[sound:$fileName]"
                else -> "[$fileName]($fileName)"
            }
            refs.add(ref)
        }
    } catch (_: Exception) { }
    return refs
}

actual fun saveMediaFile(mediaDir: String, sourcePath: String): String? {
    return try {
        val context = AndroidContextHolder.context
        val uri = Uri.parse(sourcePath)
        val bytes = if (uri.scheme == "content" || uri.scheme == "file") {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } else {
            File(sourcePath).takeIf { it.isFile }?.readBytes()
        } ?: return null
        val ext = sourcePath.substringAfterLast('.', "").lowercase().takeIf { it.isNotBlank() } ?: "bin"
        val mimeGroup = mimeGroupForAndroid(ext) ?: "other"
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
        val fileName = "$hash.$ext"
        val dir = File(mediaDir)
        if (!dir.exists()) dir.mkdirs()
        File(dir, fileName).outputStream().use { it.write(bytes) }
        markdownRefAndroid(mimeGroup, fileName)
    } catch (_: Exception) { null }
}

private fun mimeGroupForAndroid(ext: String): String? = when (ext) {
    "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg" -> "image"
    "mp3", "wav", "ogg", "m4a", "wma", "flac", "aac" -> "audio"
    "mp4", "mov", "avi", "mkv", "wmv", "webm" -> "video"
    else -> null
}

private fun generateSweepTone(
    sampleRate: Int, numSamples: Int,
    freqStart: Double, freqEnd: Double,
    decayMs: Double, attackMs: Double,
    harmonicAmplitude: Double = 0.0,
): ShortArray {
    val attackSamples = (sampleRate * attackMs / 1000.0).toInt()
    return ShortArray(numSamples) { i ->
        val t = i.toDouble() / sampleRate; val p = t * sampleRate / numSamples
        val freq = freqStart + (freqEnd - freqStart) * p
        val attack = if (i < attackSamples) i.toFloat() / attackSamples else 1f
        val envelope = attack * exp(-t * 1000.0 / decayMs)
        val amp = 0.6 * Short.MAX_VALUE * envelope
        val fund = sin(2.0 * PI * freq * t)
        val harm = harmonicAmplitude * sin(2.0 * PI * 2.0 * freq * t)
        (amp * (fund + harm)).toInt().toShort()
    }
}

actual fun playRatingSound(rating: Rating) {
    Thread({
        try {
            val sampleRate = 22050
            val (samples, durationMs) = when (rating) {
                Rating.AGAIN -> {
                    val dur = 350; val n = sampleRate * dur / 1000
                    val buf = ShortArray(n)
                    val fStart = 440.0; val fEnd = 220.0
                    for (i in 0 until n) {
                        val t = i.toDouble() / sampleRate; val p = t * sampleRate / n
                        val freq = fStart - (fStart - fEnd) * p
                        val envelope = exp(-2.5 * p)
                        buf[i] = (0.6 * Short.MAX_VALUE * envelope * sin(2.0 * PI * freq * t)).toInt().toShort()
                    }
                    buf to dur
                }
                Rating.HARD -> {
                    val dur = 300; val n = sampleRate * dur / 1000
                    val buf = ShortArray(n)
                    val f1 = 330.0; val f2 = 392.0
                    for (i in 0 until n) {
                        val t = i.toDouble() / sampleRate; val p = t * sampleRate / n
                        val envelope = exp(-3.0 * p)
                        val a1 = sin(2.0 * PI * f1 * t); val a2 = 0.6 * sin(2.0 * PI * f2 * t)
                        buf[i] = (0.5 * Short.MAX_VALUE * envelope * (a1 + a2)).toInt().toShort()
                    }
                    buf to dur
                }
                Rating.GOOD -> {
                    val dur = 250; val n = sampleRate * dur / 1000
                    val buf = ShortArray(n)
                    val freq = 660.0
                    for (i in 0 until n) {
                        val t = i.toDouble() / sampleRate; val p = t * sampleRate / n
                        val envelope = exp(-5.0 * p)
                        buf[i] = (0.6 * Short.MAX_VALUE * envelope * sin(2.0 * PI * freq * t)).toInt().toShort()
                    }
                    buf to dur
                }
                Rating.EASY -> {
                    val dur = 600; val n = sampleRate * dur / 1000
                    val attackSamples = (sampleRate * 0.001).toInt()
                    val partials = arrayOf(
                        doubleArrayOf(2800.0, 1.00, 250.0),
                        doubleArrayOf(4300.0, 0.45, 180.0),
                        doubleArrayOf(5600.0, 0.20, 120.0),
                        doubleArrayOf(7200.0, 0.10, 80.0),
                    )
                    val buf = ShortArray(n) { i ->
                        val t = i.toDouble() / sampleRate
                        val attack = if (i < attackSamples) i.toFloat() / attackSamples else 1f
                        var sample = 0.0
                        for (p in partials) {
                            val env = attack * exp(-t * 1000.0 / p[2])
                            sample += p[1] * sin(2.0 * PI * p[0] * t) * env
                        }
                        (0.45 * Short.MAX_VALUE * sample).toInt().toShort()
                    }
                    buf to dur
                }
            }
            val minBuf = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                minBuf.coerceAtLeast(samples.size * 2),
                AudioTrack.MODE_STATIC, 0
            )
            val buf = ByteArray(samples.size * 2)
            for (i in samples.indices) {
                buf[i * 2] = (samples[i].toInt() and 0xFF).toByte()
                buf[i * 2 + 1] = (samples[i].toInt() shr 8).toByte()
            }
            track.write(buf, 0, buf.size)
            track.play()
            track.release()
        } catch (_: Exception) { }
    }, "RatingSound").apply { isDaemon = true }.start()
}

private fun markdownRefAndroid(mimeGroup: String, fileName: String): String = when (mimeGroup) {
    "image" -> "![image]($fileName)"
    "audio" -> "[sound:$fileName]"
    "video" -> "[sound:$fileName]"
    else -> "[$fileName]($fileName)"
}

private fun File.sha1(): String {
    val digest = MessageDigest.getInstance("SHA-1")
    FileInputStream(this).use { input ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
