package com.lumecard.app.font

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.FileFont
import java.awt.GraphicsEnvironment
import java.io.File

private fun userHome(): String {
    // JVM on Windows may garble non-ASCII usernames when reading env vars.
    // Spawn cmd.exe to get the correct value.
    if (System.getProperty("os.name")?.lowercase()?.contains("windows") == true) {
        try {
            val process = java.lang.Runtime.getRuntime().exec(arrayOf("cmd.exe", "/c", "echo", "%USERPROFILE%"))
            val line = process.inputStream.bufferedReader().readText().trim()
            if (line.isNotBlank() && java.io.File(line).exists()) return line
        } catch (_: Exception) { }
    }
    return System.getenv("USERPROFILE") ?: System.getenv("HOME") ?: System.getProperty("user.home")
}

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
        val bytes = java.io.File(filePath).readBytes()
        val font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, java.io.ByteArrayInputStream(bytes))
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font)
        true
    } catch (_: Exception) {
        try {
            val bytes = java.io.File(filePath).readBytes()
            val font = java.awt.Font.createFont(java.awt.Font.TYPE1_FONT, java.io.ByteArrayInputStream(bytes))
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font)
            true
        } catch (_: Exception) { false }
    }
}

actual fun readFontFamilyName(filePath: String): String? {
    return try {
        val bytes = java.io.File(filePath).readBytes()
        val font = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, java.io.ByteArrayInputStream(bytes))
        font.getFamily()
    } catch (_: Exception) {
        try {
            val bytes = java.io.File(filePath).readBytes()
            val font = java.awt.Font.createFont(java.awt.Font.TYPE1_FONT, java.io.ByteArrayInputStream(bytes))
            font.getFamily()
        } catch (_: Exception) { null }
    }
}

actual fun createFileFontFamily(filePath: String): FontFamily? {
    return try {
        FontFamily(
            FileFont(file = File(filePath), weight = FontWeight.Normal, style = FontStyle.Normal)
        )
    } catch (_: Exception) { null }
}

actual fun getFontStorageDir(): String {
    val dir = File(userHome(), ".lumecard/fonts")
    dir.mkdirs()
    return dir.absolutePath
}

actual fun copyFontToStorage(sourcePath: String, fileName: String): Boolean {
    return try {
        val dir = File(getFontStorageDir())
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, fileName)
        java.nio.file.Files.copy(
            File(sourcePath).toPath(),
            target.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        )
        true
    } catch (_: Exception) { false }
}

actual fun fontFileExists(filePath: String): Boolean = File(filePath).exists()

actual fun deleteFontFile(filePath: String): Boolean {
    return try {
        val path = java.nio.file.Paths.get(filePath)
        val file = path.toFile()
        if (!file.exists()) return true
        file.setWritable(true)
        java.nio.file.Files.deleteIfExists(path)
    } catch (e: java.nio.file.FileSystemException) {
        // File may be temporarily locked (AWT, anti-virus, etc).
        // Retry once after a short delay.
        try {
            Thread.sleep(200)
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(filePath))
        } catch (_: Exception) { false }
    } catch (_: Exception) { false }
}

actual fun getBackgroundStorageDir(): String {
    val dir = File(userHome(), ".lumecard/backgrounds")
    dir.mkdirs()
    return dir.absolutePath
}

actual fun copyBackgroundToStorage(sourcePath: String, fileName: String): String? {
    return try {
        val dir = File(getBackgroundStorageDir())
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, fileName)
        java.nio.file.Files.copy(
            File(sourcePath).toPath(),
            target.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        )
        target.absolutePath
    } catch (_: Exception) { null }
}
