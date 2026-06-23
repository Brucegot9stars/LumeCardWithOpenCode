package com.lumecard.app.platform

import android.content.ClipboardManager
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
        val context = AndroidContextHolder.context ?: return refs
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
        val context = AndroidContextHolder.context ?: return null
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
