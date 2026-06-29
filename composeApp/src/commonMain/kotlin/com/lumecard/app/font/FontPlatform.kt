package com.lumecard.app.font

import androidx.compose.ui.text.font.FontFamily

expect fun detectSystemFonts(): List<FontSpec>

expect fun registerFontFile(filePath: String): Boolean

expect fun resolveFontFamily(familyName: String): FontFamily

expect fun readFontFamilyName(filePath: String): String?

expect fun createFileFontFamily(filePath: String): FontFamily?
