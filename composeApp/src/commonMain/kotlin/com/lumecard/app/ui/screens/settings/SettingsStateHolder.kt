package com.lumecard.app.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumecard.shared.domain.scheduler.ReviewMode

class SettingsStateHolder {
    var isDarkMode by mutableStateOf(false)
    var reviewMode by mutableStateOf(ReviewMode.FSRS)
    var answerDisplayMode by mutableStateOf(AnswerDisplayMode.FLIP)
    var dailyGoal by mutableStateOf(20)
    var newCardsPerDay by mutableStateOf(20)
    var notificationsEnabled by mutableStateOf(true)
    var webdavUrl by mutableStateOf("")
    var webdavUser by mutableStateOf("")
    var webdavPass by mutableStateOf("")
    var isDirty by mutableStateOf(false)
    var isSaving by mutableStateOf(false)

    fun markDirty() {
        isDirty = true
    }

    fun markClean() {
        isDirty = false
    }
}
