package com.lumecard.shared.feature.quote.facade

import com.lumecard.shared.data.*
import com.lumecard.shared.feature.quote.config.*
import com.lumecard.shared.feature.quote.manager.*
import com.lumecard.shared.feature.quote.timer.StudyTimerManager
import com.lumecard.shared.feature.quote.settings.QuoteSettingsRepository
import kotlinx.coroutines.flow.*

class DefaultQuoteFeature(
    private val quoteManager: QuoteManager,
    private val displayController: QuoteDisplayController,
    private val overrideManager: QuoteOverrideManager,
    private val idleManager: IdleManager,
    private val studyTimerManager: StudyTimerManager,
    private val settingsRepository: QuoteSettingsRepository,
    private val splashQuoteManager: SplashQuoteManager,
) : QuoteFeature {

    override val currentQuote: StateFlow<SplashQuoteData?> = displayController.currentQuote
    override val currentConfig: StateFlow<QuoteDisplayConfig> = displayController.currentConfig
    override val isIdle: StateFlow<Boolean> = idleManager.isIdle
    override val studyTimeSeconds: StateFlow<Long> = studyTimerManager.studyTimeSeconds
    override val isStudyPaused: StateFlow<Boolean> = studyTimerManager.isPaused

    private val _allQuotes = MutableStateFlow<List<SplashQuoteData>>(emptyList())
    override val allQuotes: StateFlow<List<SplashQuoteData>> = _allQuotes.asStateFlow()

    private val _userQuotes = MutableStateFlow<List<SplashQuoteData>>(emptyList())
    override val userQuotes: StateFlow<List<SplashQuoteData>> = _userQuotes.asStateFlow()

    private val _defaultQuotes = MutableStateFlow<List<SplashQuoteData>>(emptyList())
    override val defaultQuotes: StateFlow<List<SplashQuoteData>> = _defaultQuotes.asStateFlow()

    override suspend fun load() {
        _allQuotes.value = quoteManager.getAllQuotes()
        _userQuotes.value = quoteManager.getUserQuotes()
        _defaultQuotes.value = quoteManager.getDefaultQuotes()
    }

    override suspend fun refreshQuotes() {
        _allQuotes.value = quoteManager.getAllQuotes()
        _userQuotes.value = quoteManager.getUserQuotes()
        _defaultQuotes.value = quoteManager.getDefaultQuotes()
    }

    override suspend fun startDisplay(mode: QuoteDisplayMode, settings: SplashQuoteSettings) {
        val config = loadDisplayConfig(mode).copy(
            enableAnimation = settings.enableAnimation,
            animationStyle = settings.animationStyle,
        )
        displayController.startDisplay(config, settings)
    }

    override suspend fun stopDisplay() {
        displayController.stopDisplay()
    }

    override fun nextQuote() {
        val quotes = _allQuotes.value
        if (quotes.isNotEmpty()) {
            displayController.nextQuote(quotes, SplashQuoteStrategy.RANDOM)
        }
    }

    override suspend fun addQuote(quote: SplashQuoteData) {
        quoteManager.addQuote(quote)
        refreshQuotes()
    }

    override suspend fun updateQuote(index: Int, quote: SplashQuoteData) {
        quoteManager.updateQuote(index, quote)
        refreshQuotes()
    }

    override suspend fun deleteQuote(index: Int) {
        quoteManager.deleteQuote(index)
        refreshQuotes()
    }

    override suspend fun importQuotes(collection: SplashQuotesCollection, mode: SplashQuoteManager.ImportMode) {
        quoteManager.importQuotes(collection, mode)
        refreshQuotes()
    }

    override suspend fun exportQuotes(): SplashQuotesCollection = quoteManager.exportQuotes()

    override suspend fun loadSettings(): SplashQuoteSettings = quoteManager.loadSettings()

    override suspend fun saveSettings(settings: SplashQuoteSettings) = quoteManager.saveSettings(settings)

    override suspend fun loadDisplayConfig(mode: QuoteDisplayMode): QuoteDisplayConfig {
        return when (mode) {
            QuoteDisplayMode.STARTUP -> QuoteDisplayConfig.STARTUP_DEFAULT
            QuoteDisplayMode.SCREEN_SAVER -> {
                val ss = loadScreenSaverSettings()
                QuoteDisplayConfig.SCREEN_SAVER_DEFAULT.copy(
                    rotationIntervalMs = ss.rotationSeconds * 1000L,
                )
            }
            QuoteDisplayMode.PREVIEW -> QuoteDisplayConfig.PREVIEW_DEFAULT
            else -> QuoteDisplayConfig.STARTUP_DEFAULT
        }
    }

    override fun getOverrideManager(): QuoteOverrideManager = overrideManager

    override fun setIdleThreshold(ms: Long) = idleManager.setIdleThreshold(ms)

    override fun reportActivity() = idleManager.reportActivity()

    override fun startStudy() = studyTimerManager.startStudy()

    override fun stopStudy() = studyTimerManager.stopStudy()

    override fun setIdlePauseEnabled(enabled: Boolean) = studyTimerManager.setIdlePauseEnabled(enabled)

    override fun setIdlePauseThreshold(ms: Long) = studyTimerManager.setIdlePauseThreshold(ms)

    override fun getScreenSaverSettings(): suspend () -> ScreenSaverSettings = { loadScreenSaverSettings() }

    override suspend fun loadScreenSaverSettings(): ScreenSaverSettings {
        return ScreenSaverSettings(
            enabled = settingsRepository.getScreenSaverEnabled(),
            idleMinutes = settingsRepository.getScreenSaverIdleMinutes(),
            rotationSeconds = settingsRepository.getScreenSaverRotationSeconds(),
        )
    }

    override suspend fun saveScreenSaverSettings(s: ScreenSaverSettings) {
        settingsRepository.setScreenSaverEnabled(s.enabled)
        settingsRepository.setScreenSaverIdleMinutes(s.idleMinutes)
        settingsRepository.setScreenSaverRotationSeconds(s.rotationSeconds)
        idleManager.setIdleThreshold(s.idleMinutes * 60_000L)
    }

    override suspend fun loadIdlePauseSettings(): IdlePauseSettings {
        return IdlePauseSettings(
            enabled = settingsRepository.getIdlePauseEnabled(),
            thresholdSeconds = settingsRepository.getIdlePauseThresholdSeconds(),
        )
    }

    override suspend fun saveIdlePauseSettings(s: IdlePauseSettings) {
        settingsRepository.setIdlePauseEnabled(s.enabled)
        settingsRepository.setIdlePauseThresholdSeconds(s.thresholdSeconds)
        setIdlePauseEnabled(s.enabled)
        setIdlePauseThreshold(s.thresholdSeconds * 1000L)
    }

    companion object {
        const val DEFAULT_SCREEN_SAVER_IDLE_MINUTES = 3
        const val DEFAULT_SCREEN_SAVER_ROTATION_SECONDS = 3
    }
}
