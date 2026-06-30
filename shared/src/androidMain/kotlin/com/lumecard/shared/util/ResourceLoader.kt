package com.lumecard.shared.util

actual fun loadTextResource(path: String): String? {
    return try {
        val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(path.removePrefix("/"))
            ?: object {}.javaClass.getResourceAsStream(path)
        stream?.bufferedReader()?.readText()?.trim()
    } catch (_: Exception) {
        null
    }
}
