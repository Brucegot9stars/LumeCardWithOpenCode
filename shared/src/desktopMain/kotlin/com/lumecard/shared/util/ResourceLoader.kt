package com.lumecard.shared.util

actual fun loadTextResource(path: String): String? {
    return try {
        val stream = object {}.javaClass.getResourceAsStream(path)
        stream?.bufferedReader()?.readText()?.trim()
    } catch (_: Exception) {
        null
    }
}
