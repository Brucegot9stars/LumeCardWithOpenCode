package com.lumecard.app.platform

import android.os.Environment
import android.os.ParcelFileDescriptor
import com.lumecard.shared.database.AndroidContextHolder
import java.io.File

private var pendingSaveUri: android.net.Uri? = null

actual suspend fun pickSaveFile(suggestedName: String, mimeType: String): String? {
    return try {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloads == null || !downloads.exists()) {
            downloads?.mkdirs()
        }
        val file = File(downloads, suggestedName)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        file.absolutePath
    } catch (e: Exception) {
        try {
            val context = AndroidContextHolder.context ?: return null
            val file = File(context.getExternalFilesDir(null), suggestedName)
            file.parentFile?.mkdirs()
            if (!file.exists()) file.createNewFile()
            file.absolutePath
        } catch (e2: Exception) {
            null
        }
    }
}

actual suspend fun pickOpenFile(mimeType: String): String? {
    return try {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloads, "lumecard_export.json")
        if (file.exists()) file.absolutePath else null
    } catch (e: Exception) {
        null
    }
}

actual fun readFileContent(path: String): String? {
    return try {
        val file = File(path)
        if (!file.exists()) return null
        file.readText(Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}

actual fun writeFileContent(path: String, content: String): Boolean {
    return try {
        val file = File(path)
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        file.writeText(content, Charsets.UTF_8)
        file.exists() && file.length() > 0
    } catch (e: SecurityException) {
        false
    } catch (e: java.io.IOException) {
        false
    } catch (e: Exception) {
        false
    }
}
