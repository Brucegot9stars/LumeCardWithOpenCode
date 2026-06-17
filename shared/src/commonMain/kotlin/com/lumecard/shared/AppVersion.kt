package com.lumecard.shared

object AppVersion {
    private val props = run {
        val p = java.util.Properties()
        try {
            val stream = object {}.javaClass.getResourceAsStream("/version.properties")
            if (stream != null) {
                p.load(stream)
            } else {
                p.setProperty("APP_VERSION_NAME", "0.0.1")
                p.setProperty("APP_VERSION_CODE", "1")
                p.setProperty("EXPORT_VERSION", "1.0.0")
                p.setProperty("SCHEMA_VERSION", "1")
            }
        } catch (_: Exception) {
            p.setProperty("APP_VERSION_NAME", "0.0.1")
            p.setProperty("APP_VERSION_CODE", "1")
            p.setProperty("EXPORT_VERSION", "1.0.0")
            p.setProperty("SCHEMA_VERSION", "1")
        }
        p
    }

    val VERSION_NAME: String get() = props.getProperty("APP_VERSION_NAME", "0.0.1")
    val VERSION_CODE: Int get() = props.getProperty("APP_VERSION_CODE", "1").toIntOrNull() ?: 1
    val EXPORT_VERSION: String get() = props.getProperty("EXPORT_VERSION", "1.0.0")
    val SCHEMA_VERSION: Int get() = props.getProperty("SCHEMA_VERSION", "1").toIntOrNull() ?: 1
}
