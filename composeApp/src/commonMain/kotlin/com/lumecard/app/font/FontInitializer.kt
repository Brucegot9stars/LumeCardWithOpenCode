package com.lumecard.app.font

import com.lumecard.shared.repository.SettingsRepository

object FontInitializer {
    private var initialized = false
    private var repository: SettingsRepository? = null

    fun ensureInitialized(repo: SettingsRepository? = null) {
        if (initialized) return
        initialized = true
        repository = repo
        FontRegistry.registerAll(builtinFonts)
        FontRegistry.registerAll(detectSystemFonts())
        if (repo != null) {
            FontRegistry.loadUserFonts(repo)
            val defaultId = kotlinx.coroutines.runBlocking { repo.get("defaultFontFamily") } ?: ""
            FontRegistry.defaultFontId = defaultId
        }
    }
    fun saveUserFonts() { repository?.let { FontRegistry.saveUserFonts(it) } }

    private val builtinFonts = listOf(
        FontSpec("default", "Default", "", FontSource.SYSTEM),
        FontSpec("serif", "Serif", "serif", FontSource.SYSTEM),
        FontSpec("sans-serif", "Sans Serif", "sans-serif", FontSource.SYSTEM),
        FontSpec("monospace", "Monospace", "monospace", FontSource.SYSTEM),
        FontSpec("cursive", "Cursive", "cursive", FontSource.SYSTEM),
    )
}
