package com.lumecard.app.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.lumecard.shared.database.AndroidContextHolder
import java.io.File

actual suspend fun pickSaveFile(suggestedName: String, mimeType: String): String? {
    return try {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloads, suggestedName)
        file.parentFile?.mkdirs()
        file.absolutePath
    } catch (e: Exception) {
        try {
            val context = AndroidContextHolder.context ?: return null
            val file = File(context.filesDir, suggestedName)
            file.absolutePath
        } catch (e2: Exception) {
            null
        }
    }
}

actual suspend fun pickOpenFile(mimeType: String): String? {
    return try {
        val context = AndroidContextHolder.context ?: return null
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloads, "lumecard_import.json")
        if (file.exists()) file.absolutePath else null
    } catch (e: Exception) {
        null
    }
}

actual fun readFileContent(path: String): String? {
    return try {
        File(path).readText()
    } catch (e: Exception) {
        null
    }
}

actual fun writeFileContent(path: String, content: String): Boolean {
    return try {
        File(path).parentFile?.mkdirs()
        File(path).writeText(content)
        true
    } catch (e: Exception) {
        false
    }
}
