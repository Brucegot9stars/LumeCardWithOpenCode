package com.lumecard.shared.feature.quote.facade

import com.lumecard.shared.data.*
import com.lumecard.shared.feature.quote.config.QuoteDisplayConfig
import com.lumecard.shared.feature.quote.config.QuoteDisplayMode
import kotlinx.coroutines.flow.StateFlow

interface QuoteFeature {
    val currentQuote: StateFlow<SplashQuoteData?>
    val currentConfig: StateFlow<QuoteDisplayConfig>
    val allQuotes: StateFlow<List<SplashQuoteData>>
    val userQuotes: StateFlow<List<SplashQuoteData>>
    val defaultQuotes: StateFlow<List<SplashQuoteData>>
    val isIdle: StateFlow<Boolean>
    val studyTimeSeconds: StateFlow<Long>
    val isStudyPaused: StateFlow<Boolean>

    suspend fun load()
    suspend fun refreshQuotes()

    suspend fun startDisplay(mode: QuoteDisplayMode, settings: SplashQuoteSettings)
    suspend fun stopDisplay()
    fun nextQuote()

    suspend fun addQuote(quote: SplashQuoteData)
    suspend fun updateQuote(index: Int, quote: SplashQuoteData)
    suspend fun deleteQuote(index: Int)
    suspend fun importQuotes(collection: SplashQuotesCollection, mode: SplashQuoteManager.ImportMode)
    suspend fun exportQuotes(): SplashQuotesCollection

    suspend fun loadSettings(): SplashQuoteSettings
    suspend fun saveSettings(settings: SplashQuoteSettings)
    suspend fun loadDisplayConfig(mode: QuoteDisplayMode): QuoteDisplayConfig
    fun getOverrideManager(): com.lumecard.shared.feature.quote.manager.QuoteOverrideManager

    fun setIdleThreshold(ms: Long)
    fun reportActivity()
    fun startStudy()
    fun stopStudy()
    fun setIdlePauseEnabled(enabled: Boolean)
    fun setIdlePauseThreshold(ms: Long)

    fun getScreenSaverSettings(): suspend () -> ScreenSaverSettings
    suspend fun loadScreenSaverSettings(): ScreenSaverSettings
    suspend fun saveScreenSaverSettings(s: ScreenSaverSettings)

    suspend fun loadIdlePauseSettings(): IdlePauseSettings
    suspend fun saveIdlePauseSettings(s: IdlePauseSettings)
}

data class ScreenSaverSettings(
    val enabled: Boolean = true,
    val idleMinutes: Int = 3,
    val rotationSeconds: Int = 3,
)

data class IdlePauseSettings(
    val enabled: Boolean = true,
    val thresholdSeconds: Int = 30,
)
