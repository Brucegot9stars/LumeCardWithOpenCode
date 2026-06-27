package com.lumecard.app.font

import java.awt.GraphicsEnvironment

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
