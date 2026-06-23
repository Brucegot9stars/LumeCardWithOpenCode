package com.lumecard.app.platform

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipOutputStream

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
