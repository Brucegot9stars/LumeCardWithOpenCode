package com.lumecard.app.font

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import java.awt.GraphicsEnvironment
import java.io.File

actual fun detectSystemFonts(): List<FontSpec> {
    val blocked = setOf(
        "Segoe MDL2 Assets", "Segoe UI Emoji", "Segoe UI Historic", "Segoe UI Symbol",
        "Segoe Fluent Icons", "Segoe Icons", "Webdings", "Wingdings", "Symbol",
        "Marlett", "MS Outlook", "MS Reference Specialty", "MT Extra",
        "Bookshelf Symbol 7", "Monotype Corsiva", "MS Gothic", "MS PGothic",
        "MS UI Gothic", "MS Mincho", "MS PMincho", "Batang", "BatangChe",
        "Dotum", "DotumChe", "Gulim", "GulimChe", "Gungsuh", "GungsuhChe",
        "Angsana New", "AngsanaUPC", "Browallia New", "BrowalliaUPC",
        "Cordia New", "CordiaUPC", "DFKai-SB", "Euphemia",
        "Gautami", "Iskoola Pota", "Kalinga", "Kartika", "Kokila",
        "Latha", "Mangal", "Narkisim", "Nyala",
        "Raavi", "Shonar Bangla", "Shruti", "Tunga", "Urdu Typesetting",
        "Vani", "Vijaya", "Plantagenet Cherokee",
    )
    return try {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        ge.availableFontFamilyNames
            .filter { it !in blocked && !it.contains("HoloLens") && !it.contains("OneNote") }
            .sorted()
            .map { name ->
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

@OptIn(ExperimentalTextApi::class)
actual fun resolveFontFamily(familyName: String): FontFamily = FontFamily(familyName)

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

actual fun readFontFamilyName(filePath: String): String? {
    return try {
        val font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, File(filePath))
        font.getFamily()
    } catch (_: Exception) {
        try {
            val font = java.awt.Font.createFont(java.awt.Font.TYPE1_FONT, File(filePath))
            font.getFamily()
        } catch (_: Exception) { null }
    }
}

@OptIn(ExperimentalTextApi::class)
actual fun createFileFontFamily(filePath: String): FontFamily? {
    val name = readFontFamilyName(filePath) ?: return null
    return FontFamily(name)
}
