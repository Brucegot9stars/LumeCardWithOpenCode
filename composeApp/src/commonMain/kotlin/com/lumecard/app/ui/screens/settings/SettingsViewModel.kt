package com.lumecard.app.ui.screens.settings

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.app.i18n.AppLocale
import com.lumecard.app.i18n.I18nManager
import com.lumecard.shared.domain.scheduler.ReviewMode
import com.lumecard.shared.repository.SettingsRepository
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    val state: SettingsStateHolder
) : ScreenModel, KoinComponent {

    private val i18nManager: I18nManager by inject()

    fun loadSettings() {
        screenModelScope.launch {
            state.isDarkMode = settingsRepository.getBoolean("isDarkMode", false)
            val modeStr = settingsRepository.get("reviewMode") ?: ReviewMode.FSRS.name
            state.reviewMode = try { ReviewMode.valueOf(modeStr) } catch (_: Exception) { ReviewMode.FSRS }
            val displayStr = settingsRepository.get("answerDisplayMode") ?: AnswerDisplayMode.FLIP.name
            state.answerDisplayMode = try { AnswerDisplayMode.valueOf(displayStr) } catch (_: Exception) { AnswerDisplayMode.FLIP }
            state.dailyGoal = settingsRepository.getInt("dailyGoal", 20)
            state.newCardsPerDay = settingsRepository.getInt("newCardsPerDay", 20)
            state.notificationsEnabled = settingsRepository.getBoolean("notificationsEnabled", true)
            state.autoSyncEnabled = settingsRepository.getBoolean("auto_sync_enabled", false)
            state.autoSyncIntervalMinutes = settingsRepository.getInt("auto_sync_interval_minutes", 30)
            val langStr = settingsRepository.get("language") ?: AppLocale.SYSTEM.name
            state.language = try { AppLocale.valueOf(langStr) } catch (_: Exception) { AppLocale.SYSTEM }
        }
    }

    fun saveSettings() {
        screenModelScope.launch {
            state.isSaving = true
            settingsRepository.set("isDarkMode", state.isDarkMode.toString())
            settingsRepository.set("reviewMode", state.reviewMode.name)
            settingsRepository.set("answerDisplayMode", state.answerDisplayMode.name)
            settingsRepository.set("dailyGoal", state.dailyGoal.toString())
            settingsRepository.set("newCardsPerDay", state.newCardsPerDay.toString())
            settingsRepository.set("notificationsEnabled", state.notificationsEnabled.toString())
            settingsRepository.set("auto_sync_enabled", state.autoSyncEnabled.toString())
            settingsRepository.set("auto_sync_interval_minutes", state.autoSyncIntervalMinutes.toString())
            settingsRepository.set("language", state.language.name)
            state.markClean()
            state.isSaving = false
        }
    }

    fun setAnswerDisplayMode(mode: AnswerDisplayMode) {
        state.answerDisplayMode = mode
        state.markDirty()
    }

    fun setReviewMode(mode: ReviewMode) {
        state.reviewMode = mode
        state.markDirty()
    }

    fun setDarkMode(enabled: Boolean) {
        state.isDarkMode = enabled
        state.markDirty()
    }

    fun setLanguage(locale: AppLocale) {
        state.language = locale
        i18nManager.setLocale(locale)
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

    fun setAutoSyncEnabled(enabled: Boolean) {
        state.autoSyncEnabled = enabled
        state.markDirty()
    }

    fun setAutoSyncInterval(minutes: Int) {
        state.autoSyncIntervalMinutes = minutes.coerceIn(5, 1440)
        state.markDirty()
    }
}

