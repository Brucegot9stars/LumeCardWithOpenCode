package com.lumecard.shared.data

expect fun platformMkdirsForPath(path: String)

expect fun platformWriteAllBytes(path: String, bytes: ByteArray)

expect fun platformFileExists(path: String): Boolean

expect fun platformDeleteFile(path: String): Boolean
