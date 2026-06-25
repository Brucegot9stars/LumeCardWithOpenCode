package com.lumecard.app.ui.util

import androidx.compose.ui.graphics.Color

internal fun parseHtmlColor(value: String): Color? {
    val v = value.trim()
    val rgb = Regex("""rgba?\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*([\d.]+))?\s*\)""").find(v)
    if (rgb != null) {
        val r = rgb.groupValues[1].toIntOrNull() ?: return null
        val g = rgb.groupValues[2].toIntOrNull() ?: return null
        val b = rgb.groupValues[3].toIntOrNull() ?: return null
        val a = rgb.groupValues[4].toFloatOrNull() ?: 1f
        return Color(r / 255f, g / 255f, b / 255f, a.coerceIn(0f, 1f))
    }
    if (v.startsWith("#")) {
        val hex = v.removePrefix("#")
        val n = hex.toLongOrNull(16) ?: return null
        return when (hex.length) { 6 -> Color(0xFF000000 or n); 8 -> Color(n); else -> null }
    }
    return when (v) {
        "red" -> Color.Red; "blue" -> Color.Blue; "green" -> Color(0xFF2E7D32); "black" -> Color.Black
        "white" -> Color.White; "gray", "grey" -> Color.Gray; "purple" -> Color(0xFF7B1FA2)
        "orange" -> Color(0xFFF57C00); "teal" -> Color(0xFF00796B); "brown" -> Color(0xFF5D4037)
        else -> null
    }
}
