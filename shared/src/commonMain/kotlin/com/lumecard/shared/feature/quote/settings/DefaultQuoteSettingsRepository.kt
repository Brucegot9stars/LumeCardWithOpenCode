package com.lumecard.shared.feature.quote.settings

import com.lumecard.shared.repository.SettingsRepository

class DefaultQuoteSettingsRepository(
    private val settings: SettingsRepository,
) : QuoteSettingsRepository {

    override suspend fun getScreenSaverEnabled(): Boolean =
        settings.getBoolean(KEY_SCREEN_SAVER_ENABLED, true)

    override suspend fun setScreenSaverEnabled(enabled: Boolean) =
        settings.set(KEY_SCREEN_SAVER_ENABLED, enabled.toString())

    override suspend fun getScreenSaverIdleMinutes(): Int =
        settings.getInt(KEY_SCREEN_SAVER_IDLE_MINUTES, 3).coerceIn(1, 60)

    override suspend fun setScreenSaverIdleMinutes(minutes: Int) =
        settings.set(KEY_SCREEN_SAVER_IDLE_MINUTES, minutes.coerceIn(1, 60).toString())

    override suspend fun getScreenSaverRotationSeconds(): Int =
        settings.getInt(KEY_SCREEN_SAVER_ROTATION_SECONDS, 3).coerceIn(1, 30)

    override suspend fun setScreenSaverRotationSeconds(seconds: Int) =
        settings.set(KEY_SCREEN_SAVER_ROTATION_SECONDS, seconds.coerceIn(1, 30).toString())

    override suspend fun getIdlePauseEnabled(): Boolean =
        settings.getBoolean(KEY_IDLE_PAUSE_ENABLED, true)

    override suspend fun setIdlePauseEnabled(enabled: Boolean) =
        settings.set(KEY_IDLE_PAUSE_ENABLED, enabled.toString())

    override suspend fun getIdlePauseThresholdSeconds(): Int =
        settings.getInt(KEY_IDLE_PAUSE_THRESHOLD, 30).coerceIn(10, 600)

    override suspend fun setIdlePauseThresholdSeconds(seconds: Int) =
        settings.set(KEY_IDLE_PAUSE_THRESHOLD, seconds.coerceIn(10, 600).toString())

    companion object {
        private const val KEY_SCREEN_SAVER_ENABLED = "screen_saver_enabled"
        private const val KEY_SCREEN_SAVER_IDLE_MINUTES = "screen_saver_idle_minutes"
        private const val KEY_SCREEN_SAVER_ROTATION_SECONDS = "screen_saver_rotation_seconds"
        private const val KEY_IDLE_PAUSE_ENABLED = "idle_pause_enabled"
        private const val KEY_IDLE_PAUSE_THRESHOLD = "idle_pause_threshold"
    }
}
