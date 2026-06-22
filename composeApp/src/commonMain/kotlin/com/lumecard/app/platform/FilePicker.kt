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
