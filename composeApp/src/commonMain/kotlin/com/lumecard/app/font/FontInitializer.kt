package com.lumecard.app.font

object FontInitializer {
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        initialized = true
        FontRegistry.registerAll(builtinFonts)
        FontRegistry.registerAll(detectSystemFonts())
    }

    private val builtinFonts = listOf(
        FontSpec("default", "Default", "", FontSource.SYSTEM),
        FontSpec("serif", "Serif", "serif", FontSource.SYSTEM),
        FontSpec("sans-serif", "Sans Serif", "sans-serif", FontSource.SYSTEM),
        FontSpec("monospace", "Monospace", "monospace", FontSource.SYSTEM),
        FontSpec("cursive", "Cursive", "cursive", FontSource.SYSTEM),
    )
}
