package com.lumecard.app.platform

import com.lumecard.app.i18n.AppLocale
import java.util.Locale

actual fun applyAppLocale(locale: AppLocale) {
    try {
        Locale.setDefault(Locale.forLanguageTag(locale.code))
    } catch (_: Exception) { }
}

actual fun detectSystemLocaleTag(): String {
    return Locale.getDefault().toLanguageTag()
}

