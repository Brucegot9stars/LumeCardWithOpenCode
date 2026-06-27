package com.lumecard.app.font

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class FontSource { SYSTEM, BUNDLED, USER_IMPORTED }

data class FontSpec(
    val id: String,
    val displayName: String,
    val family: String,
    val source: FontSource,
    val weight: FontWeight = FontWeight.Normal,
    val style: FontStyle = FontStyle.Normal,
    val filePath: String? = null,
)

@Serializable
data class PersistedUserFont(
    val id: String,
    val displayName: String,
    val family: String,
    val filePath: String,
)

private val fontJson = Json { ignoreUnknownKeys = true }
private const val USER_FONTS_SETTINGS_KEY = "user_fonts"

object FontRegistry {
    private val _fonts = mutableListOf<FontSpec>()
    private val _userFontPaths = mutableSetOf<String>()
    private val _fontFamilyCache = mutableMapOf<String, FontFamily>()

    val default: FontSpec get() = _fonts.firstOrNull() ?: FontSpec("default", "Default", "", FontSource.SYSTEM)
    val fonts: List<FontSpec> get() = _fonts.toList()
    val userFontPaths: Set<String> get() = _userFontPaths.toSet()

    fun register(spec: FontSpec) {
        _fonts.removeAll { it.id == spec.id }
        _fonts.add(spec)
        _fontFamilyCache.remove(spec.id)
        if (spec.filePath != null) _userFontPaths.add(spec.filePath)
    }

    fun registerAll(specs: List<FontSpec>) {
        specs.forEach { register(it) }
    }

    fun remove(id: String) {
        val spec = _fonts.find { it.id == id } ?: return
        _fonts.remove(spec)
        _fontFamilyCache.remove(id)
        spec.filePath?.let { _userFontPaths.remove(it) }
    }

    @OptIn(ExperimentalTextApi::class)
    fun getFontFamily(spec: FontSpec): FontFamily {
        return if (spec.family.isBlank()) FontFamily.Default
        else _fontFamilyCache.getOrPut(spec.id) { FontFamily(spec.family) }
    }

    @OptIn(ExperimentalTextApi::class)
    fun resolveFontFamily(name: String): FontFamily {
        if (name.isBlank()) return FontFamily.Default
        val spec = _fonts.find { it.id == name || it.family == name }
        return if (spec != null) getFontFamily(spec) else FontFamily(name)
    }

    fun clear() {
        _fonts.clear()
        _userFontPaths.clear()
        _fontFamilyCache.clear()
    }

    fun findByFamily(family: String): FontSpec? = _fonts.find { it.family == family }

    fun saveUserFonts(repository: com.lumecard.shared.repository.SettingsRepository) {
        val persisted = _fonts.filter { it.source == FontSource.USER_IMPORTED && it.filePath != null }.map {
            PersistedUserFont(it.id, it.displayName, it.family, it.filePath!!)
        }
        kotlinx.coroutines.runBlocking {
            repository.set(USER_FONTS_SETTINGS_KEY, fontJson.encodeToString(persisted))
        }
    }

    fun loadUserFonts(repository: com.lumecard.shared.repository.SettingsRepository) {
        val raw = kotlinx.coroutines.runBlocking { repository.get(USER_FONTS_SETTINGS_KEY) } ?: return
        try {
            val persisted = fontJson.decodeFromString<List<PersistedUserFont>>(raw)
            persisted.forEach { p ->
                register(FontSpec(p.id, p.displayName, p.family, FontSource.USER_IMPORTED, filePath = p.filePath))
            }
        } catch (_: Exception) { }
    }

    fun importFont(filePath: String, displayName: String): FontSpec {
        val id = "user_${displayName.lowercase().replace(" ", "_")}"
        val spec = FontSpec(id, displayName, displayName, FontSource.USER_IMPORTED, filePath = filePath)
        register(spec)
        return spec
    }
}
