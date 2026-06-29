package com.lumecard.app.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumecard.app.i18n.AppLocale
import com.lumecard.shared.domain.scheduler.ReviewMode

class SettingsStateHolder {
    var isDarkMode by mutableStateOf(false)
    var reviewMode by mutableStateOf(ReviewMode.FSRS)
    var answerDisplayMode by mutableStateOf(AnswerDisplayMode.FLIP)
    var dailyGoal by mutableStateOf(20)
    var newCardsPerDay by mutableStateOf(20)
    var notificationsEnabled by mutableStateOf(true)
    var autoSyncEnabled by mutableStateOf(false)
    var autoSyncIntervalMinutes by mutableStateOf(30)
    var language by mutableStateOf(AppLocale.SYSTEM)
    var defaultFontFamily by mutableStateOf("")
    var fontScale by mutableStateOf(1.0f)
    var isDirty by mutableStateOf(false)
    var isSaving by mutableStateOf(false)

    fun markDirty() {
        isDirty = true
    }

    fun markClean() {
        isDirty = false
    }
}
