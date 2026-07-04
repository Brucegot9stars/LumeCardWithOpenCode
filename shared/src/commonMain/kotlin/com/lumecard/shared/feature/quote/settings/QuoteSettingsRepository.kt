package com.lumecard.shared.feature.quote.settings

import kotlinx.coroutines.flow.StateFlow

interface QuoteSettingsRepository {
    suspend fun getScreenSaverEnabled(): Boolean
    suspend fun setScreenSaverEnabled(enabled: Boolean)

    suspend fun getScreenSaverIdleMinutes(): Int
    suspend fun setScreenSaverIdleMinutes(minutes: Int)

    suspend fun getScreenSaverRotationSeconds(): Int
    suspend fun setScreenSaverRotationSeconds(seconds: Int)

    suspend fun getIdlePauseEnabled(): Boolean
    suspend fun setIdlePauseEnabled(enabled: Boolean)

    suspend fun getIdlePauseThresholdSeconds(): Int
    suspend fun setIdlePauseThresholdSeconds(seconds: Int)
}
