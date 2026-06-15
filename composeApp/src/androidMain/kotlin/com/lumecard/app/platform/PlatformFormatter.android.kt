package com.lumecard.app.platform

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

private fun locale(): Locale {
    val tag = detectSystemLocaleTag()
    return try {
        Locale.forLanguageTag(tag)
    } catch (_: Exception) {
        Locale.getDefault()
    }
}

actual fun formatDate(year: Int, month: Int, day: Int): String {
    val cal = java.util.Calendar.getInstance()
    cal.set(year, month - 1, day)
    return DateFormat.getDateInstance(DateFormat.MEDIUM, locale()).format(cal.time)
}

actual fun formatTime(hour: Int, minute: Int): String {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
    cal.set(java.util.Calendar.MINUTE, minute)
    return DateFormat.getTimeInstance(DateFormat.SHORT, locale()).format(cal.time)
}

actual fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}min" else "${m}min"
}

actual fun formatNumber(value: Number): String {
    return NumberFormat.getNumberInstance(locale()).format(value)
}

actual fun formatPercentage(value: Double): String {
    return NumberFormat.getPercentInstance(locale()).format(value / 100.0)
}
