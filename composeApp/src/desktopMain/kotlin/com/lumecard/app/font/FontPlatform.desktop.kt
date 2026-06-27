package com.lumecard.app.font

import java.awt.GraphicsEnvironment
import java.io.File

actual fun detectSystemFonts(): List<FontSpec> {
    return try {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val names = ge.availableFontFamilyNames.distinct().sorted()
        names.map { name ->
            FontSpec(
                id = "sys_${name.lowercase().replace(" ", "_")}",
                displayName = name,
                family = name,
                source = FontSource.SYSTEM,
            )
        }
        } catch (_: Exception) {
        emptyList()
    }
}

actual fun registerFontFile(filePath: String): Boolean {
    return try {
        val file = File(filePath)
        if (!file.exists()) return false
        val font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, file)
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font)
        true
    } catch (_: Exception) {
        try {
            val file = File(filePath)
            val font = java.awt.Font.createFont(java.awt.Font.TYPE1_FONT, file)
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font)
            true
        } catch (_: Exception) { false }
    }
}
