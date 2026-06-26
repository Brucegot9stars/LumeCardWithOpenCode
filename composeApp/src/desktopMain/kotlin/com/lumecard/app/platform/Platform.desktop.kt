package com.lumecard.app.platform

import com.lumecard.shared.model.Rating
import java.awt.Toolkit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

actual fun isDesktopPlatform(): Boolean = true

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
    return System.getProperty("lumecard.media.dir") ?: "${System.getProperty("user.home")}/.lumecard/media"
}

actual fun pasteClipboardMedia(mediaDir: String): List<String> {
    val refs = mutableListOf<String>()
    try {
        val toolkit = Toolkit.getDefaultToolkit()
        val clipboard = toolkit.systemClipboard

        // 1. Image flavor — screenshots, copied images from browser etc.
        if (clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
            val image = clipboard.getData(DataFlavor.imageFlavor) as? BufferedImage
            if (image != null) {
                val baos = ByteArrayOutputStream()
                ImageIO.write(image, "png", baos)
                val bytes = baos.toByteArray()
                val hash = sha1Bytes(bytes)
                val fileName = "$hash.png"
                saveFile(mediaDir, fileName, bytes)
                refs.add("![image]($fileName)")
            }
        }

        // 2. File list flavor — files copied from Explorer
        if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
            @Suppress("UNCHECKED_CAST")
            val files = clipboard.getData(DataFlavor.javaFileListFlavor) as? List<File>
            if (files != null) {
                for (file in files) {
                    if (!file.isFile) continue
                    val ref = copyFileToMedia(mediaDir, file)
                    if (ref != null) refs.add(ref)
                }
            }
        }
    } catch (_: Exception) { }
    return refs
}

actual fun saveMediaFile(mediaDir: String, sourcePath: String): String? {
    val file = File(sourcePath)
    if (!file.isFile) return null
    return copyFileToMedia(mediaDir, file)
}

private fun copyFileToMedia(mediaDir: String, file: File): String? {
    val ext = file.extension.lowercase()
    val mimeGroup = mimeGroupForExt(ext) ?: return null
    val targetName = "${file.sha1()}.$ext"
    val target = File(mediaDir, targetName)
    if (target.exists()) return markdownRef(mimeGroup, targetName)
    val dir = File(mediaDir)
    if (!dir.exists()) dir.mkdirs()
    Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    return markdownRef(mimeGroup, targetName)
}

private fun mimeGroupForExt(ext: String): String? = when (ext) {
    "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico" -> "image"
    "mp3", "wav", "ogg", "m4a", "wma", "flac", "aac", "opus" -> "audio"
    "mp4", "mov", "avi", "mkv", "wmv", "webm", "flv", "3gp" -> "video"
    else -> null
}

private fun markdownRef(mimeGroup: String, fileName: String): String = when (mimeGroup) {
    "image" -> "![image]($fileName)"
    "audio" -> "[sound:$fileName]"
    "video" -> "[sound:$fileName]"
    else -> "[$fileName]($fileName)"
}

private fun sha1Bytes(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-1")
    return digest.digest(bytes).joinToString("") { "%02x".format(it) }
}

private fun saveFile(mediaDir: String, fileName: String, bytes: ByteArray) {
    val dir = File(mediaDir)
    if (!dir.exists()) dir.mkdirs()
    File(dir, fileName).outputStream().use { it.write(bytes) }
}

actual fun playRatingSound(rating: Rating) {
    Thread({
        try {
            val sampleRate = 22050f
            val durationMs = 400
            val numSamples = (sampleRate * durationMs / 1000f).toInt()
            val freq = 880.0
            val harmonicFreq = 2640.0
            val samples = ShortArray(numSamples) { i ->
                val t = i / sampleRate
                val envelope = exp(-3.0 * t * sampleRate / numSamples)
                val fundamental = sin(2.0 * PI * freq * t)
                val harmonic = 0.3 * sin(2.0 * PI * harmonicFreq * t)
                (0.6 * Short.MAX_VALUE * envelope * (fundamental + harmonic)).toInt().toShort()
            }
            val format = AudioFormat(sampleRate, 16, 1, true, false)
            val line = AudioSystem.getSourceDataLine(format)
            line.open(format)
            line.start()
            val buffer = ByteArray(numSamples * 2)
            for (i in samples.indices) {
                buffer[i * 2] = (samples[i].toInt() and 0xFF).toByte()
                buffer[i * 2 + 1] = (samples[i].toInt() shr 8).toByte()
            }
            line.write(buffer, 0, buffer.size)
            line.drain()
            line.close()
        } catch (_: Exception) { }
    }, "RatingSound").apply { isDaemon = true }.start()
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
