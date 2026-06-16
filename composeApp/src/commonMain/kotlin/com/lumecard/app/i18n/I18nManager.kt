package com.lumecard.app.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumecard.app.platform.applyAppLocale
import com.lumecard.app.platform.detectSystemLocaleTag

enum class AppLocale(val code: String, val displayName: String, val flag: String = "") {
    SYSTEM("system", "跟随系统", ""),
    ZH_CN("zh-CN", "简体中文", "\uD83C\uDDE8\uD83C\uDDF3"),
    ZH_TW("zh-TW", "繁體中文", "\uD83C\uDDF9\uD83C\uDDFC"),
    EN("en", "English", "\uD83C\uDDFA\uD83C\uDDF8"),
    JA("ja", "日本語", "\uD83C\uDDEF\uD83C\uDDF5"),
    ES("es", "Español", "\uD83C\uDDEA\uD83C\uDDF8")
}

class I18nManager {
    var currentLocale by mutableStateOf(AppLocale.SYSTEM)
        private set

    private val localeMap: Map<AppLocale, I18nStrings> = mapOf(
        AppLocale.ZH_CN to ZhCnStrings,
        AppLocale.ZH_TW to ZhTwStrings,
        AppLocale.EN to EnStrings,
        AppLocale.JA to JaStrings,
        AppLocale.ES to EsStrings
    )

    val strings: I18nStrings
        get() {
            val locale = if (currentLocale == AppLocale.SYSTEM) systemLocale else currentLocale
            return localeMap[locale] ?: EnStrings
        }

    private var systemLocale: AppLocale = AppLocale.EN

    init {
        try {
            detectSystemLocale(detectSystemLocaleTag())
        } catch (_: Exception) {
            systemLocale = AppLocale.EN
        }
    }

    /** Returns strings for the detected system locale, regardless of currentLocale. */
    val systemStrings: I18nStrings
        get() = localeMap[systemLocale] ?: EnStrings

    fun setLocale(locale: AppLocale) {
        if (locale == currentLocale) return
        currentLocale = locale
        if (locale != AppLocale.SYSTEM) {
            applyAppLocale(locale)
        }
    }

    fun detectSystemLocale(systemCode: String) {
        systemLocale = when {
            systemCode.startsWith("zh-CN") || systemCode.startsWith("zh-Hans") -> AppLocale.ZH_CN
            systemCode.startsWith("zh-TW") || systemCode.startsWith("zh-Hant") -> AppLocale.ZH_TW
            systemCode.startsWith("ja") -> AppLocale.JA
            systemCode.startsWith("es") -> AppLocale.ES
            else -> AppLocale.EN
        }
    }
}

fun String.i18nFormat(vararg args: Any?): String {
    if (args.isEmpty()) return this
    var result = this
    args.forEachIndexed { index, arg ->
        result = result.replace("{$index}", arg?.toString() ?: "")
    }
    return result
}



