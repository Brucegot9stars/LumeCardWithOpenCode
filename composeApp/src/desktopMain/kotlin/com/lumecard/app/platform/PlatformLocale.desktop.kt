package com.lumecard.app.platform

import com.lumecard.app.i18n.AppLocale
import java.util.Locale

actual fun applyAppLocale(locale: AppLocale) {
    // No per-app language API on Desktop
    Locale.setDefault(Locale.forLanguageTag(locale.code))
}

actual fun detectSystemLocaleTag(): String {
    return Locale.getDefault().toLanguageTag()
}
