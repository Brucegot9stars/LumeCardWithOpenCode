package com.lumecard.app.font

expect fun detectSystemFonts(): List<FontSpec>

expect fun registerFontFile(filePath: String): Boolean
