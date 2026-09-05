package com.lumecard.app.platform

expect fun platformGetFileName(path: String): String

expect fun platformJoinPath(base: String, vararg parts: String): String

expect fun platformNormalizePath(path: String): String

expect fun platformPathExists(path: String): Boolean

expect fun platformIsFile(path: String): Boolean

expect fun platformListFiles(dir: String): List<String>

expect fun platformGetFileNameWithoutExtension(path: String): String

expect fun platformGetUserHome(): String?

expect fun platformGetSystemProperty(key: String): String?

expect fun platformReadFileBytes(path: String): ByteArray?

expect fun platformWriteFileBytes(path: String, bytes: ByteArray)

expect fun platformDeleteFile(path: String): Boolean

expect fun platformMkdirs(path: String): Boolean

expect fun platformFileExists(path: String): Boolean

expect fun platformListFileNames(dir: String): List<String>

expect fun platformReadFileText(path: String): String?

expect fun platformWriteFileText(path: String, text: String)

expect fun platformGetParentDir(path: String): String
