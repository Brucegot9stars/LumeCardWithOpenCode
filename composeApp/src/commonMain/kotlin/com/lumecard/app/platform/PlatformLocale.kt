package com.lumecard.app.platform

import com.lumecard.app.i18n.AppLocale

expect fun applyAppLocale(locale: AppLocale)

expect fun detectSystemLocaleTag(): String
