package com.lumecard.app.font

import android.os.Build
import android.util.Log
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

actual fun resolveFontFamily(familyName: String): FontFamily = when {
    familyName.contains("serif", ignoreCase = true) && !familyName.contains("sans", ignoreCase = true) -> FontFamily.Serif
    familyName.contains("monospace", ignoreCase = true) || familyName.contains("mono", ignoreCase = true) -> FontFamily.Monospace
    familyName.contains("cursive", ignoreCase = true) -> FontFamily.Cursive
    else -> FontFamily.SansSerif
}

private val fontProbeCache = mutableSetOf<String>()

private fun fontFamilyExists(name: String): Boolean {
    if (name in fontProbeCache) return true
    return try {
        val tf = android.graphics.Typeface.create(name, android.graphics.Typeface.NORMAL)
        if (tf != null && !tf.equals(android.graphics.Typeface.DEFAULT)) {
            fontProbeCache.add(name)
            true
        } else false
    } catch (_: Exception) { false }
}

private val isChineseLocale: Boolean by lazy {
    val locale = Locale.getDefault()
    locale.language == "zh"
}

private fun cnLabel(en: String, zh: String): String = if (isChineseLocale) zh else en

actual fun detectSystemFonts(): List<FontSpec> {
    val result = mutableListOf<FontSpec>()
    val seen = mutableSetOf<String>()

    val knownFamilies = listOf(
        "sans-serif" to cnLabel("System Default", "系统默认"),
        "sans-serif-light" to cnLabel("Light", "细体"),
        "sans-serif-thin" to cnLabel("Thin", "极细"),
        "sans-serif-medium" to cnLabel("Medium", "中等"),
        "sans-serif-black" to cnLabel("Black", "粗体"),
        "sans-serif-condensed" to cnLabel("Condensed", "窄体"),
        "sans-serif-condensed-light" to cnLabel("Condensed Light", "窄细体"),
        "serif" to cnLabel("Serif", "衬线体"),
        "serif-thin" to cnLabel("Serif Thin", "衬线极细"),
        "serif-light" to cnLabel("Serif Light", "衬线细体"),
        "serif-medium" to cnLabel("Serif Medium", "衬线中等"),
        "serif-black" to cnLabel("Serif Black", "衬线粗体"),
        "serif-condensed" to cnLabel("Serif Condensed", "衬线窄体"),
        "monospace" to cnLabel("Monospace", "等宽体"),
        "cursive" to cnLabel("Cursive", "手写体"),
    )

    for ((family, displayName) in knownFamilies) {
        if (fontFamilyExists(family)) {
            val id = "sys_${family.lowercase(Locale.US).replace(" ", "_").replace("-", "_")}"
            result.add(FontSpec(id, displayName, family, FontSource.SYSTEM))
            seen.add(family)
        }
    }

    val vendorFonts = listOf(
        "sans-serif-smallcaps" to cnLabel("Small Caps", "小型大写"),
        "sans-serif-thin-condensed" to cnLabel("Thin Condensed", "极细窄体"),
        "sans-serif-light-condensed" to cnLabel("Light Condensed", "细窄体"),
        "sans-serif-medium-condensed" to cnLabel("Medium Condensed", "中等窄体"),
        "sans-serif-black-condensed" to cnLabel("Black Condensed", "粗窄体"),
        "sans-serif-black-italic" to cnLabel("Black Italic", "粗斜体"),
        "sans-serif-light-italic" to cnLabel("Light Italic", "细斜体"),
        "sans-serif-thin-italic" to cnLabel("Thin Italic", "极细斜体"),
        "sans-serif-medium-italic" to cnLabel("Medium Italic", "中等斜体"),
    )
    for ((family, displayName) in vendorFonts) {
        if (family !in seen && fontFamilyExists(family)) {
            val id = "sys_${family.lowercase(Locale.US).replace(" ", "_").replace("-", "_")}"
            result.add(FontSpec(id, displayName, family, FontSource.SYSTEM))
            seen.add(family)
        }
    }

    val cjkFonts = listOf(
        "noto-sans-cjk-sc" to cnLabel("Noto Sans CJK SC", "Noto 无衬线体"),
        "noto-serif-cjk-sc" to cnLabel("Noto Serif CJK SC", "Noto 衬线体"),
        "noto-sans-sc" to cnLabel("Noto Sans SC", "Noto 无衬线体 SC"),
        "noto-serif-sc" to cnLabel("Noto Serif SC", "Noto 衬线体 SC"),
        "source-han-sans-sc" to cnLabel("Source Han Sans SC", "思源黑体"),
        "source-han-serif-sc" to cnLabel("Source Han Serif SC", "思源宋体"),
    )
    for ((family, displayName) in cjkFonts) {
        if (family !in seen && fontFamilyExists(family)) {
            val id = "sys_${family.lowercase(Locale.US).replace(" ", "_").replace("-", "_")}"
            result.add(FontSpec(id, displayName, family, FontSource.SYSTEM))
            seen.add(family)
        }
    }

    val oemFonts = listOf(
        "miui" to cnLabel("MIUI", "MIUI"),
        "milankafont" to cnLabel("MILAN", "米兰"),
        "hwu-chinese" to cnLabel("Huawei Chinese", "华为字体"),
        "hwu-text" to cnLabel("Huawei Text", "华为正文"),
        "opposans" to cnLabel("Oppo Sans", "OPPO Sans"),
        "vivo-sans" to cnLabel("vivo Sans", "vivo Sans"),
        "honor-sans" to cnLabel("Honor Sans", "荣耀 Sans"),
        "coloros-sans" to cnLabel("ColorOS Sans", "ColorOS Sans"),
    )
    for ((family, displayName) in oemFonts) {
        if (family !in seen && fontFamilyExists(family)) {
            val id = "sys_${family.lowercase(Locale.US).replace(" ", "_").replace("-", "_")}"
            result.add(FontSpec(id, displayName, family, FontSource.SYSTEM))
            seen.add(family)
        }
    }

    tryDetectCustomFonts(result, seen)

    return result
}

private fun tryDetectCustomFonts(result: MutableList<FontSpec>, seen: MutableSet<String>) {
    if (Build.VERSION.SDK_INT < 28) return
    try {
        val fontDir = File("/system/fonts/")
        if (!fontDir.exists() || !fontDir.isDirectory) return
        val families = mutableSetOf<String>()
        fontDir.listFiles()?.forEach { file ->
            val name = file.nameWithoutExtension
            val cleaned = name
                .removeSuffix("-Regular").removeSuffix("-Bold").removeSuffix("-Italic")
                .removeSuffix("-Medium").removeSuffix("-Light").removeSuffix("-Thin")
                .removeSuffix("-Black").removeSuffix("-Condensed")
                .removeSuffix("UI")
            if (cleaned.isNotBlank() && cleaned.length > 2) {
                families.add(cleaned)
            }
        }
        for (family in families) {
            if (family !in seen && fontFamilyExists(family)) {
                val id = "sys_${family.lowercase(Locale.US).replace(" ", "_").replace("-", "_")}"
                result.add(FontSpec(id, family, family, FontSource.SYSTEM))
                seen.add(family)
            }
        }
    } catch (_: Exception) { }
}

actual fun readFontFamilyName(filePath: String): String? {
    return try {
        val file = java.io.File(filePath)
        if (!file.exists()) return null
        val bytes = file.readBytes()
        if (bytes.size < 12) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val numTables = buf.getShort(4).toInt() and 0xFFFF

        var nameOffset = -1
        var nameLen = -1
        for (i in 0 until numTables) {
            val entryPos = 12 + i * 16
            if (entryPos + 16 > bytes.size) break
            val tag = String(bytes, entryPos, 4, Charsets.US_ASCII)
            if (tag != "name") continue
            nameOffset = java.io.DataInputStream(java.io.ByteArrayInputStream(bytes, entryPos + 8, 4)).readInt()
            nameLen = java.io.DataInputStream(java.io.ByteArrayInputStream(bytes, entryPos + 12, 4)).readInt()
            break
        }
        if (nameOffset < 0 || nameOffset + 6 > bytes.size) return null

        val nameBuf = ByteBuffer.wrap(bytes, nameOffset, bytes.size - nameOffset).order(ByteOrder.BIG_ENDIAN)
        // skip format
        nameBuf.getShort()
        val count = nameBuf.getShort().toInt() and 0xFFFF
        val strOffset = nameBuf.getShort().toInt() and 0xFFFF

        var best: String? = null
        var bestScore = -1

        for (i in 0 until count) {
            if (nameBuf.position() + 12 > bytes.size - nameOffset) break
            val platformID = nameBuf.getShort().toInt() and 0xFFFF
            val encodingID = nameBuf.getShort().toInt() and 0xFFFF
            val languageID = nameBuf.getShort().toInt() and 0xFFFF
            val nameID = nameBuf.getShort().toInt() and 0xFFFF
            val len = nameBuf.getShort().toInt() and 0xFFFF
            val off = nameBuf.getShort().toInt() and 0xFFFF

            if (nameID != 1) continue

            val absOff = nameOffset + strOffset + off
            if (absOff + len > bytes.size) continue

            val raw = bytes.copyOfRange(absOff, absOff + len)
            val decoded = when (platformID) {
                1 -> String(raw, Charsets.UTF_8)
                0, 3 -> {
                    val str = String(raw, Charsets.UTF_16BE)
                    str.trim('\u0000')
                }
                else -> continue
            }
            val score = when {
                platformID == 3 && encodingID == 1 && languageID == 0x0409 -> 4
                platformID == 3 && encodingID == 10 -> 3
                platformID == 3 -> 2
                platformID == 1 -> 1
                else -> 0
            }
            if (score > bestScore) {
                best = decoded
                bestScore = score
            }
        }
        best?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        Log.d("FontPlatform", "readFontFamilyName failed for $filePath: ${e.message}")
        null
    }
}

actual fun createFileFontFamily(filePath: String): FontFamily? {
    return try {
        val file = File(filePath)
        FontFamily(Font(file = file))
    } catch (e: Exception) {
        Log.e("FontPlatform", "createFileFontFamily failed for $filePath", e)
        null
    }
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
