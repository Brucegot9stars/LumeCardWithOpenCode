package com.lumecard.app.font

import android.util.Log
import androidx.compose.ui.text.font.FontFamily
import java.io.File

actual fun resolveFontFamily(familyName: String): FontFamily = when {
    familyName.contains("serif", ignoreCase = true) && !familyName.contains("sans", ignoreCase = true) -> FontFamily.Serif
    familyName.contains("monospace", ignoreCase = true) || familyName.contains("mono", ignoreCase = true) -> FontFamily.Monospace
    familyName.contains("cursive", ignoreCase = true) -> FontFamily.Cursive
    else -> FontFamily.SansSerif
}

actual fun detectSystemFonts(): List<FontSpec> {
    val result = mutableListOf<FontSpec>()
    result.add(FontSpec("sys_default", "System Default", "sans-serif", FontSource.SYSTEM))
    result.add(FontSpec("sys_serif", "Serif", "serif", FontSource.SYSTEM))
    result.add(FontSpec("sys_monospace", "Monospace", "monospace", FontSource.SYSTEM))
    return result
}

actual fun registerFontFile(filePath: String): Boolean {
    return try {
        val file = File(filePath)
        if (!file.exists()) return false
        android.graphics.Typeface.createFromFile(file)
        true
    } catch (e: Exception) {
        Log.e("FontPlatform", "Failed to register font: $filePath", e)
        false
    }
}
