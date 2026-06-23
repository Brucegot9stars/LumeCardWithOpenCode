package com.lumecard.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual suspend fun pickSaveFile(suggestedName: String, mimeType: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val chooser = JFileChooser().apply {
                dialogTitle = "Save File"
                selectedFile = File(suggestedName)
                fileFilter = FileNameExtensionFilter("JSON Files", "json")
            }
            val result = chooser.showSaveDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

actual suspend fun pickOpenFile(mimeType: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val chooser = JFileChooser().apply {
                dialogTitle = "Open File"
                fileFilter = FileNameExtensionFilter("JSON Files", "json")
            }
            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

actual fun readFileContent(path: String): String? {
    return try {
        File(path).readText()
    } catch (e: Exception) {
        null
    }
}

actual suspend fun pickMediaFile(): String? {
    return withContext(Dispatchers.IO) {
        try {
            val chooser = JFileChooser().apply {
                dialogTitle = "Select Media File"
                fileFilter = FileNameExtensionFilter(
                    "Media Files (images, audio, video)",
                    "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico",
                    "mp3", "wav", "ogg", "m4a", "wma", "flac", "aac", "opus",
                    "mp4", "mov", "avi", "mkv", "wmv", "webm", "flv", "3gp"
                )
                isAcceptAllFileFilterUsed = true
            }
            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                chooser.selectedFile.absolutePath
            } else null
        } catch (_: Exception) { null }
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
