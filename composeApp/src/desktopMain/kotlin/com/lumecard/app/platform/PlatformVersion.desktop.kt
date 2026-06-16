package com.lumecard.app.platform

actual fun getAppVersion(): String {
    return try {
        val props = java.util.Properties()
        props.load(object {}.javaClass.getResourceAsStream("/version.properties") ?: return "1.2.0")
        props.getProperty("version", "1.2.0")
    } catch (_: Exception) {
        "1.2.0"
    }
}
