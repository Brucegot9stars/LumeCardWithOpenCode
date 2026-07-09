package com.lumecard.app.font

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.lumecard.shared.data.generateUuid

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
    var defaultFontId by mutableStateOf("")
    private val _fonts = mutableStateListOf<FontSpec>()
    private val _userFontPaths = mutableSetOf<String>()
    private val _fontFamilyCache = mutableMapOf<String, FontFamily>()

    val default: FontSpec get() = _fonts.firstOrNull() ?: FontSpec("default", "Default", "", FontSource.SYSTEM)
    val fonts: List<FontSpec> get() = _fonts.toList()
    val userFontPaths: Set<String> get() = _userFontPaths.toSet()

    fun register(spec: FontSpec) {
        _fonts.removeAll { it.id == spec.id }
        _fonts.add(spec)
        _fontFamilyCache.remove(spec.id)
        if (spec.filePath != null) {
            _userFontPaths.add(spec.filePath)
            registerFontFile(spec.filePath)
        }
    }

    fun registerAll(specs: List<FontSpec>) {
        specs.forEach { register(it) }
    }

    fun remove(id: String) {
        val spec = _fonts.find { it.id == id } ?: return
        _fonts.remove(spec)
        _fontFamilyCache.remove(id)
        spec.filePath?.let {
            _userFontPaths.remove(it)
            val deleted = deleteFontFile(it)
            if (!deleted) {
                println("[FontRegistry] failed to delete file: $it")
            }
        }
    }

    @OptIn(ExperimentalTextApi::class)
    fun getFontFamily(spec: FontSpec): FontFamily {
        if (spec.family.isBlank()) return FontFamily.Default
        _fontFamilyCache[spec.id]?.let { return it }
        val ff = spec.filePath?.let { createFileFontFamily(it) }
            ?: com.lumecard.app.font.resolveFontFamily(spec.family)
        _fontFamilyCache[spec.id] = ff
        return ff
    }

    @OptIn(ExperimentalTextApi::class)
    fun resolveFontFamily(name: String): FontFamily {
        if (name.isBlank()) return FontFamily.Default
        val spec = _fonts.find { it.id == name || it.family == name }
        return if (spec != null) getFontFamily(spec) else com.lumecard.app.font.resolveFontFamily(name)
    }

    fun clear() {
        _fonts.clear()
        _userFontPaths.clear()
        _fontFamilyCache.clear()
    }

    fun findById(id: String): FontSpec? = _fonts.find { it.id == id }

    fun findByFamily(family: String): FontSpec? = _fonts.find { it.family == family }

    fun saveUserFonts(repository: com.lumecard.shared.repository.SettingsRepository) {
        val currentFontDir = getFontStorageDir()
        val persisted = _fonts.filter { it.source == FontSource.USER_IMPORTED && it.filePath != null }.map {
            val fileName = java.io.File(it.filePath!!).name
            PersistedUserFont(it.id, it.displayName, it.family, java.io.File(currentFontDir, fileName).absolutePath)
        }
        kotlinx.coroutines.runBlocking {
            repository.set(USER_FONTS_SETTINGS_KEY, fontJson.encodeToString(persisted))
        }
    }

    fun loadUserFonts(repository: com.lumecard.shared.repository.SettingsRepository) {
        val raw = kotlinx.coroutines.runBlocking { repository.get(USER_FONTS_SETTINGS_KEY) } ?: return
        try {
            val persisted = fontJson.decodeFromString<List<PersistedUserFont>>(raw)
            val currentFontDir = getFontStorageDir()
            persisted.forEach { p ->
                val fileName = java.io.File(p.filePath).name
                val normalizedPath = java.io.File(currentFontDir, fileName).absolutePath
                if (fontFileExists(normalizedPath)) {
                    register(FontSpec(p.id, p.displayName, p.family, FontSource.USER_IMPORTED, filePath = normalizedPath))
                }
            }
        } catch (_: Exception) { }
    }

    fun importFont(filePath: String, displayName: String): FontSpec? {
        val actualFamily = readFontFamilyName(filePath) ?: displayName
        val ext = filePath.substringAfterLast(".", "ttf")
        val fileName = "${generateUuid()}.$ext"
        if (!copyFontToStorage(filePath, fileName)) return null
        val storagePath = java.io.File(getFontStorageDir(), fileName).absolutePath
        if (!registerFontFile(storagePath)) return null
        val id = "user_${(actualFamily).lowercase().replace(" ", "_")}"
        val spec = FontSpec(id, actualFamily, actualFamily, FontSource.USER_IMPORTED, filePath = storagePath)
        register(spec)
        return spec
    }

    fun rebuildFromStorageDir(repository: com.lumecard.shared.repository.SettingsRepository) {
        val storageDir = getFontStorageDir()
        val dir = java.io.File(storageDir)
        if (!dir.exists()) return
        val fontFiles = dir.listFiles()?.filter {
            it.isFile && it.name.endsWith(".ttf", ignoreCase = true) || it.name.endsWith(".otf", ignoreCase = true)
        }.orEmpty()

        val registeredPaths = _fonts.mapNotNull { it.filePath?.let { p -> java.io.File(p).absolutePath } }.toSet()

        for (file in fontFiles) {
            val absPath = file.absolutePath
            if (absPath in registeredPaths) continue
            val name = file.nameWithoutExtension
            registerFontFile(absPath)
            val actualFamily = readFontFamilyName(absPath) ?: name
            val id = "user_${(actualFamily).lowercase().replace(" ", "_")}"
            val spec = FontSpec(id, actualFamily, actualFamily, FontSource.USER_IMPORTED, filePath = absPath)
            register(spec)
        }

        val deadEntries = _fonts.filter {
            it.source == FontSource.USER_IMPORTED && it.filePath != null && !fontFileExists(it.filePath!!)
        }
        for (entry in deadEntries) {
            _fonts.remove(entry)
            _fontFamilyCache.remove(entry.id)
            _userFontPaths.remove(entry.filePath)
        }

        saveUserFonts(repository)
    }
}
