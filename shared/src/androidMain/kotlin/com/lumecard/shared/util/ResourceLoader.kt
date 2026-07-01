package com.lumecard.shared.util

import com.lumecard.shared.database.AndroidContextHolder

actual fun loadTextResource(path: String): String? {
    val relativePath = path.removePrefix("/")
    return try {
        val stream = AndroidContextHolder.context.assets.open(relativePath)
        stream.bufferedReader().readText().trim()
    } catch (_: Exception) {
        try {
            val stream = object {}.javaClass.getResourceAsStream(path)
            stream?.bufferedReader()?.readText()?.trim()
        } catch (_: Exception) {
            null
        }
    }
}
