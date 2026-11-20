package com.lumecard.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

actual suspend fun writeZipArchive(
    outputPath: String,
    entries: List<Pair<String, String>>,
): List<String>? = withContext(Dispatchers.IO) {
    val written = mutableListOf<String>()
    try {
        val target = File(outputPath)
        target.parentFile?.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { zos ->
            for ((archivePath, sourceFile) in entries) {
                val source = File(sourceFile)
                if (!source.isFile) continue
                // Fixed timestamp keeps archives reproducible: the same content yields the same bytes.
                zos.putNextEntry(ZipEntry(archivePath).apply { time = 0L })
                source.inputStream().buffered().use { it.copyTo(zos) }
                zos.closeEntry()
                written.add(archivePath)
            }
        }
    } catch (_: Exception) {
        // A partial archive is not usable: report failure instead of a misleading count.
        return@withContext null
    }
    written
}

actual suspend fun listZipArchive(archivePath: String): List<String>? = withContext(Dispatchers.IO) {
    try {
        val file = File(archivePath)
        if (!file.isFile) null else ZipFile(file).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }.map { it.name }.toList()
        }
    } catch (_: Exception) {
        null
    }
}

actual suspend fun readZipArchiveEntry(
    archivePath: String,
    entryPath: String,
    maxBytes: Long,
): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val file = File(archivePath)
        if (!file.isFile) null else ZipFile(file).use { zip ->
            val entry = zip.getEntry(entryPath) ?: return@use null
            if (entry.isDirectory) return@use null
            if (entry.size > maxBytes) return@use null
            BufferedInputStream(zip.getInputStream(entry)).use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    // Entry.size may be -1 (unknown) or lie; enforce the cap while streaming too.
                    if (total > maxBytes) return@use null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        }
    } catch (_: Exception) {
        null
    }
}

actual suspend fun readZipArchiveEntries(
    archivePath: String,
    entryPaths: Set<String>,
    maxBytes: Long,
): Map<String, ByteArray>? = withContext(Dispatchers.IO) {
    try {
        val file = File(archivePath)
        if (!file.isFile) return@withContext null
        ZipFile(file).use { zip ->
            val result = mutableMapOf<String, ByteArray>()
            for (path in entryPaths) {
                val entry = zip.getEntry(path) ?: continue
                if (entry.isDirectory || entry.size > maxBytes) continue
                BufferedInputStream(zip.getInputStream(entry)).use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var overflow = false
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) {
                            overflow = true
                            break
                        }
                        out.write(buffer, 0, read)
                    }
                    if (!overflow) result[path] = out.toByteArray()
                }
            }
            result
        }
    } catch (_: Exception) {
        null
    }
}

actual suspend fun readFileHeader(filePath: String, byteCount: Int): ByteArray? = withContext(Dispatchers.IO) {
    try {
        val file = File(filePath)
        if (!file.isFile || byteCount <= 0) {
            null
        } else {
            file.inputStream().use { input ->
                val buffer = ByteArray(byteCount)
                var total = 0
                while (total < byteCount) {
                    val read = input.read(buffer, total, byteCount - total)
                    if (read < 0) break
                    total += read
                }
                if (total == 0) null else buffer.copyOf(total)
            }
        }
    } catch (_: Exception) {
        null
    }
}
