package com.lumecard.app.platform

expect suspend fun pickSaveFile(
    suggestedName: String,
    mimeType: String = "*/*"
): String?

expect suspend fun pickOpenFile(
    mimeType: String = "*/*"
): String?

expect fun readFileContent(path: String): String?

expect fun writeFileContent(path: String, content: String): Boolean

/** Open a file picker for images, audio, and video files. Returns absolute path (Desktop) or content URI (Android). */
expect suspend fun pickMediaFile(): String?
