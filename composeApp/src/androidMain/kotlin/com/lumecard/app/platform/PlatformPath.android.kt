package com.lumecard.app.platform

import android.net.Uri
import com.lumecard.shared.database.AndroidContextHolder
import java.io.File

/** True for the URI forms the save/open pickers hand back; everything else stays a plain path. */
private fun isUriPath(path: String): Boolean {
    val lower = path.lowercase()
    return lower.startsWith("content://") || lower.startsWith("file://")
}

actual fun platformGetFileName(path: String): String = File(path).name

actual fun platformJoinPath(base: String, vararg parts: String): String =
    File(base, parts.joinToString(File.separator)).absolutePath

actual fun platformNormalizePath(path: String): String = File(path).absolutePath

actual fun platformPathExists(path: String): Boolean = File(path).exists()

actual fun platformIsFile(path: String): Boolean {
    if (!isUriPath(path)) return File(path).isFile
    return try {
        val uri = Uri.parse(path)
        if (uri.scheme == "file") {
            File(uri.path ?: return false).isFile
        } else {
            AndroidContextHolder.context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }
    } catch (_: Exception) {
        false
    }
}

actual fun platformListFiles(dir: String): List<String> =
    File(dir).listFiles()?.map { it.absolutePath }.orEmpty()

actual fun platformGetFileNameWithoutExtension(path: String): String =
    File(path).nameWithoutExtension

actual fun platformGetUserHome(): String? =
    try { AndroidContextHolder.context.filesDir.absolutePath } catch (_: Exception) { null }
        ?: System.getenv("HOME") ?: System.getProperty("user.home")

actual fun platformGetSystemProperty(key: String): String? = System.getProperty(key)

actual fun platformReadFileBytes(path: String): ByteArray? = try {
    File(path).readBytes()
} catch (_: Exception) { null }

actual fun platformWriteFileBytes(path: String, bytes: ByteArray) {
    File(path).writeBytes(bytes)
}

actual fun platformDeleteFile(path: String): Boolean {
    if (!isUriPath(path)) return File(path).delete()
    return try {
        val uri = Uri.parse(path)
        if (uri.scheme == "file") {
            File(uri.path ?: return false).delete()
        } else {
            // Some providers report 0 rows deleted even on success, so only an exception means
            // the delete did not go through.
            AndroidContextHolder.context.contentResolver.delete(uri, null, null)
            true
        }
    } catch (_: Exception) {
        false
    }
}

actual fun platformMkdirs(path: String): Boolean = File(path).mkdirs()

actual fun platformFileExists(path: String): Boolean = File(path).exists()

actual fun platformListFileNames(dir: String): List<String> =
    File(dir).listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()

actual fun platformReadFileText(path: String): String? = try {
    File(path).readText()
} catch (_: Exception) { null }

actual fun platformWriteFileText(path: String, text: String): Boolean {
    if (!isUriPath(path)) {
        return try {
            File(path).writeText(text)
            true
        } catch (_: Exception) {
            false
        }
    }
    return try {
        val uri = Uri.parse(path)
        if (uri.scheme == "file") {
            val filePath = uri.path ?: return false
            File(filePath).writeText(text)
            true
        } else {
            // "wt" truncates instead of appending, which is what a save-to-URI needs. A null stream
            // means the provider refused the document — that is a failure, not a silent no-op.
            val stream = AndroidContextHolder.context.contentResolver.openOutputStream(uri, "wt")
                ?: return false
            stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            true
        }
    } catch (_: Exception) {
        false
    }
}

actual fun platformGetParentDir(path: String): String = File(path).parent ?: ""
