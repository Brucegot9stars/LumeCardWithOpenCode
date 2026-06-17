package com.lumecard.app.platform

import com.lumecard.shared.database.AndroidContextHolder
import java.io.File

actual suspend fun pickSaveFile(suggestedName: String, mimeType: String): String? {
    return try {
        val context = AndroidContextHolder.context ?: return null
        val cacheDir = context.cacheDir
        val file = File(cacheDir, suggestedName)
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

actual suspend fun pickOpenFile(mimeType: String): String? {
    return try {
        val context = AndroidContextHolder.context ?: return null
        val cacheDir = context.cacheDir
        val file = File(cacheDir, "import.json")
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
        File(path).writeText(content)
        true
    } catch (e: Exception) {
        false
    }
}
