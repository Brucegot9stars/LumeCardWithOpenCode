package com.lumecard.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual suspend fun pickSaveFile(suggestedName: String, mimeType: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            // Derive the filter and the enforced extension from the suggested name, not from a
            // hardcoded .json: archive exports use .lumecard and must not become *.lumecard.json.
            val expectedExtension = suggestedName.substringAfterLast('.', "").lowercase()
                .ifBlank { "json" }
            val chooser = JFileChooser().apply {
                dialogTitle = "Save File"
                selectedFile = File(suggestedName)
                fileFilter = FileNameExtensionFilter(
                    expectedExtension.uppercase() + " Files",
                    expectedExtension,
                )
            }
            val result = chooser.showSaveDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                val chosen = chooser.selectedFile.absolutePath
                // Replace a known export extension instead of appending to it: a user typing
                // `notes.json` must not end up with `notes.json.lumecard` (or `notes.json.json`
                // after the plain-JSON fallback).
                val withoutKnownSuffix = when {
                    chosen.endsWith(".lumecard", ignoreCase = true) ->
                        chosen.dropLast(".lumecard".length)
                    chosen.endsWith(".json", ignoreCase = true) -> chosen.dropLast(".json".length)
                    else -> chosen
                }
                if (withoutKnownSuffix.endsWith(".$expectedExtension", ignoreCase = true)) {
                    chosen
                } else {
                    "$withoutKnownSuffix.$expectedExtension"
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

actual suspend fun pickOpenFile(mimeType: String, initialDirectory: String?): String? {
    return withContext(Dispatchers.IO) {
        try {
            val chooser = JFileChooser().apply {
                dialogTitle = "Open File"
                fileFilter = FileNameExtensionFilter(
                    when {
                        mimeType.startsWith("image/") -> "Image Files"
                        mimeType == "text/plain" || mimeType.startsWith("text/") -> "Text Files"
                        mimeType.contains("font") -> "Font Files"
                        mimeType == "application/json" -> "JSON Files"
                        // Archive exports are zip containers; plain .json exports are equally
                        // valid input (the caller sniffs the header). Mirrors Android, where
                        // application/zip is widened to application/* so both can be picked.
                        mimeType == "application/zip" -> "LumeCard Exports"
                        else -> "All Files"
                    },
                    *when {
                        mimeType == "text/plain" || mimeType.startsWith("text/") -> arrayOf("txt", "md")
                        mimeType == "application/json" -> arrayOf("json")
                        mimeType == "application/zip" -> arrayOf("lumecard", "zip", "json")
                        mimeType.startsWith("image/") -> arrayOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")
                        mimeType.contains("font") -> arrayOf("ttf", "otf", "ttc")
                        else -> emptyArray()
                    }
                )
                isAcceptAllFileFilterUsed = true
                if (initialDirectory != null) {
                    val dir = File(initialDirectory)
                    if (dir.isDirectory) currentDirectory = dir
                }
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
        val file = File(path)
        val name = file.name.lowercase()
        val binaryExtensions = setOf("docx", "doc", "pdf", "xls", "xlsx", "ppt", "pptx", "zip", "rar", "7z", "exe", "dll", "so", "dylib", "bin", "dat")
        if (binaryExtensions.any { name.endsWith(it) }) return null
        if (file.length() > 64L * 1024 * 1024) return null
        file.readText()
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

actual fun fileParentDirectory(filePath: String): String? {
    return try {
        val file = File(filePath)
        file.parentFile?.absolutePath
    } catch (_: Exception) { null }
}
