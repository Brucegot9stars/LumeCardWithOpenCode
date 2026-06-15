package com.lumecard.app.platform

import android.content.Context
import android.os.Build
import android.app.LocaleManager
import com.lumecard.app.i18n.AppLocale
import com.lumecard.shared.database.AndroidContextHolder

actual fun applyAppLocale(locale: AppLocale) {
    if (locale == AppLocale.SYSTEM) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val context = AndroidContextHolder.context
        val localeManager = context.getSystemService(Context.LOCALE_SERVICE) as LocaleManager
        localeManager.applicationLocales = android.os.LocaleList(
            java.util.Locale.forLanguageTag(locale.code)
        )
    }
}

actual fun detectSystemLocaleTag(): String {
    val context = AndroidContextHolder.context
    val config = context.resources.configuration
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        config.locales[0].toLanguageTag()
    } else {
        @Suppress("DEPRECATION")
        config.locale.toLanguageTag()
    }
}
