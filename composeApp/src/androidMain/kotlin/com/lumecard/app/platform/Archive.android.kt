package com.lumecard.app.platform

import android.net.Uri
import com.lumecard.shared.database.AndroidContextHolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** True when [path] is a `content://` URI from the system document picker rather than a file path. */
private fun isContentUri(path: String): Boolean =
    path.startsWith("content://", ignoreCase = true) || path.startsWith("file://", ignoreCase = true)

private fun openArchiveInput(path: String): InputStream? = try {
    if (isContentUri(path)) {
        AndroidContextHolder.context.contentResolver.openInputStream(Uri.parse(path))
    } else {
        val file = File(path)
        if (file.isFile) file.inputStream() else null
    }
} catch (_: Exception) {
    null
}

/**
 * ZipInputStream is the only option for `content://` archives (no random access), so the whole
 * entry list is produced in one pass over the stream.
 */
actual suspend fun writeZipArchive(
    outputPath: String,
    entries: List<Pair<String, String>>,
): List<String>? = withContext(Dispatchers.IO) {
    val written = mutableListOf<String>()
    try {
        val sink: java.io.OutputStream? = if (isContentUri(outputPath)) {
            // "wt" truncates an existing document instead of appending to it.
            AndroidContextHolder.context.contentResolver.openOutputStream(Uri.parse(outputPath), "wt")
        } else {
            val target = File(outputPath)
            target.parentFile?.mkdirs()
            FileOutputStream(target)
        }
        // A null sink means the document could not be opened, which is a hard failure rather than
        // an empty-but-successful write.
        if (sink == null) return@withContext null
        sink.buffered().use { output ->
            ZipOutputStream(output).use { zos ->
                for ((archivePath, sourceFile) in entries) {
                    val source = try {
                        File(sourceFile).takeIf { it.isFile }?.inputStream()
                    } catch (_: Exception) {
                        null
                    } ?: continue
                    source.use { input ->
                        zos.putNextEntry(ZipEntry(archivePath).apply { time = 0L })
                        input.copyTo(zos)
                        zos.closeEntry()
                        written.add(archivePath)
                    }
                }
            }
        }
    } catch (_: Exception) {
        // A failed write leaves a partial archive, which is not usable; report null so the caller
        // can delete it and fall back to a plain JSON export.
        return@withContext null
    }
    written
}

actual suspend fun listZipArchive(archivePath: String): List<String>? = withContext(Dispatchers.IO) {
    val input = openArchiveInput(archivePath) ?: return@withContext null
    try {
        val names = mutableListOf<String>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) names.add(entry.name)
                // Drain the entry instead of jumping straight to closeEntry: some providers hand
                // back a stream that will not advance to the next header otherwise.
                drainEntry(zip)
                zip.closeEntry()
            }
        }
        names
    } catch (_: Exception) {
        null
    }
}

/** Consume the current entry's payload so the stream is positioned on the next header. */
private fun drainEntry(zip: ZipInputStream) {
    val buffer = ByteArray(8 * 1024)
    while (zip.read(buffer) >= 0) {
        // Discard: the caller either re-reads the entry or does not need its bytes.
    }
}

actual suspend fun readZipArchiveEntry(
    archivePath: String,
    entryPath: String,
    maxBytes: Long,
): ByteArray? = withContext(Dispatchers.IO) {
    val input = openArchiveInput(archivePath) ?: return@withContext null
    try {
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name == entryPath) {
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) return@withContext null
                        out.write(buffer, 0, read)
                    }
                    zip.closeEntry()
                    return@withContext out.toByteArray()
                }
                zip.closeEntry()
            }
            null
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
    if (entryPaths.isEmpty()) return@withContext emptyMap()
    val input = openArchiveInput(archivePath) ?: return@withContext null
    try {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name in entryPaths && entry.name !in result) {
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var overflow = false
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) {
                            // Oversized entry: skip it but keep scanning for the others.
                            overflow = true
                            break
                        }
                        out.write(buffer, 0, read)
                    }
                    if (overflow) {
                        // Oversized entry: discard the rest but keep scanning for the others.
                        drainEntry(zip)
                    } else {
                        result[entry.name] = out.toByteArray()
                    }
                    zip.closeEntry()
                } else {
                    drainEntry(zip)
                    zip.closeEntry()
                }
            }
        }
        result
    } catch (_: Exception) {
        null
    }
}

actual suspend fun readFileHeader(filePath: String, byteCount: Int): ByteArray? = withContext(Dispatchers.IO) {
    if (byteCount <= 0) return@withContext null
    val input = openArchiveInput(filePath) ?: return@withContext null
    try {
        input.use { stream ->
            val buffer = ByteArray(byteCount)
            var total = 0
            while (total < byteCount) {
                val read = stream.read(buffer, total, byteCount - total)
                if (read < 0) break
                total += read
            }
            if (total == 0) null else buffer.copyOf(total)
        }
    } catch (_: Exception) {
        null
    }
}
