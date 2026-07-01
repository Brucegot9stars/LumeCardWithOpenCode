package com.lumecard.shared.util

import com.lumecard.shared.database.AndroidContextHolder
import java.io.File

/**
 * On Android, KMP commonMain resources are bundled into the APK's assets.
 * This implementation extracts them to [context.filesDir] on first access
 * so the file lives at a real filesystem path matching the resource path,
 * then reads from there. Subsequent reads use the filesystem copy.
 */
actual fun loadTextResource(path: String): String? {
    val relativePath = path.removePrefix("/")
    val ctx = AndroidContextHolder.context
    return try {
        val file = File(ctx.filesDir, relativePath)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            ctx.assets.open(relativePath).use { src ->
                file.outputStream().use { dst ->
                    src.copyTo(dst)
                }
            }
        }
        file.readText().trim()
    } catch (_: Exception) {
        try {
            val stream = object {}.javaClass.getResourceAsStream(path)
            stream?.bufferedReader()?.readText()?.trim()
        } catch (_: Exception) {
            null
        }
    }
}
