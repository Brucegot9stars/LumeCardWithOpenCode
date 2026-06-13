package com.lumecard.app.ui.screens.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.domain.scheduler.ReviewMode
import com.lumecard.shared.repository.SettingsRepository
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    val state: SettingsStateHolder
) : ScreenModel {

    fun loadSettings() {
        screenModelScope.launch {
            state.isDarkMode = settingsRepository.getBoolean("isDarkMode", false)
            val modeStr = settingsRepository.get("reviewMode") ?: ReviewMode.FSRS.name
            state.reviewMode = try { ReviewMode.valueOf(modeStr) } catch (_: Exception) { ReviewMode.FSRS }
            state.dailyGoal = settingsRepository.getInt("dailyGoal", 20)
            state.newCardsPerDay = settingsRepository.getInt("newCardsPerDay", 20)
            state.notificationsEnabled = settingsRepository.getBoolean("notificationsEnabled", true)
            state.webdavUrl = settingsRepository.get("webdavUrl") ?: ""
            state.webdavUser = settingsRepository.get("webdavUser") ?: ""
            state.webdavPass = settingsRepository.get("webdavPass") ?: ""
        }
    }

    fun saveSettings() {
        screenModelScope.launch {
            state.isSaving = true
            settingsRepository.set("isDarkMode", state.isDarkMode.toString())
            settingsRepository.set("reviewMode", state.reviewMode.name)
            settingsRepository.set("dailyGoal", state.dailyGoal.toString())
            settingsRepository.set("newCardsPerDay", state.newCardsPerDay.toString())
            settingsRepository.set("notificationsEnabled", state.notificationsEnabled.toString())
            settingsRepository.set("webdavUrl", state.webdavUrl)
            settingsRepository.set("webdavUser", state.webdavUser)
            settingsRepository.set("webdavPass", state.webdavPass)
            state.markClean()
            state.isSaving = false
        }
    }

    fun setReviewMode(mode: ReviewMode) {
        state.reviewMode = mode
        state.markDirty()
    }

    fun setDarkMode(enabled: Boolean) {
        state.isDarkMode = enabled
        state.markDirty()
    }

    fun setDailyGoal(goal: Int) {
        state.dailyGoal = goal.coerceIn(1, 999)
        state.markDirty()
    }

    fun setNewCardsPerDay(count: Int) {
        state.newCardsPerDay = count.coerceIn(1, 999)
        state.markDirty()
    }

    fun setNotifications(enabled: Boolean) {
        state.notificationsEnabled = enabled
        state.markDirty()
    }

    fun setWebdavUrl(url: String) {
        state.webdavUrl = url
        state.markDirty()
    }

    fun setWebdavUser(user: String) {
        state.webdavUser = user
        state.markDirty()
    }

    fun setWebdavPass(pass: String) {
        state.webdavPass = pass
        state.markDirty()
    }
}
