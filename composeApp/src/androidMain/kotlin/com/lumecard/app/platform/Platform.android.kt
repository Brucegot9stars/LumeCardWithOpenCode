package com.lumecard.app.platform

import android.content.ClipboardManager
import android.content.Context
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

actual fun readClipboardImageAndSave(mediaDir: String): String? {
    return try {
        val context = AndroidContextHolder.context ?: return null
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return null
        val desc = clip.description ?: return null
        val hasImage = (0 until desc.mimeTypeCount).any { i -> desc.getMimeType(i)?.startsWith("image/") == true }
        if (!hasImage) return null
        val item = clip.getItemAt(0) ?: return null
        val bytes = item.uri?.let { uri -> context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: return null
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
        val fileName = "$hash.png"
        val dir = File(mediaDir)
        if (!dir.exists()) dir.mkdirs()
        File(dir, fileName).outputStream().use { it.write(bytes) }
        fileName
    } catch (_: Exception) { null }
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
