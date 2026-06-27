package com.lumecard.app.font

import android.graphics.FontListParser

actual fun detectSystemFonts(): List<FontSpec> {
    val result = mutableListOf<FontSpec>()
    result.add(FontSpec("sys_default", "System Default", "sans-serif", FontSource.SYSTEM))
    result.add(FontSpec("sys_serif", "Serif", "serif", FontSource.SYSTEM))
    result.add(FontSpec("sys_monospace", "Monospace", "monospace", FontSource.SYSTEM))
    return result
}
