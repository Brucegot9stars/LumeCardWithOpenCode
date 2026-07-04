package com.lumecard.shared.feature.quote.manager

import com.lumecard.shared.feature.quote.config.QuoteDisplayConfig
import com.lumecard.shared.feature.quote.config.QuoteDisplayMode
import com.lumecard.shared.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class QuoteDisplayController(
    private val quoteManager: QuoteManager,
    private val overrideManager: QuoteOverrideManager,
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _currentQuote = MutableStateFlow<SplashQuoteData?>(null)
    val currentQuote: StateFlow<SplashQuoteData?> = _currentQuote.asStateFlow()

    private val _currentConfig = MutableStateFlow(QuoteDisplayConfig.STARTUP_DEFAULT)
    val currentConfig: StateFlow<QuoteDisplayConfig> = _currentConfig.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)

    private var rotationJob: Job? = null

    suspend fun startDisplay(
        config: QuoteDisplayConfig,
        settings: SplashQuoteSettings,
    ) {
        _currentConfig.value = config
        val allQuotes = quoteManager.getAllQuotes()
        if (allQuotes.isEmpty()) return

        val strategy = settings.strategy
        val (quote, index) = quoteManager.getQuoteForDisplay(strategy, 0, allQuotes)
        _currentQuote.value = quote
        _currentIndex.value = index

        if (config.autoRotate) {
            startRotation(allQuotes, strategy, config.rotationIntervalMs)
        }
    }

    fun nextQuote(quotes: List<SplashQuoteData>, strategy: SplashQuoteStrategy) {
        scope.launch {
            if (quotes.isEmpty()) return@launch
            val (quote, index) = quoteManager.getQuoteForDisplay(strategy, _currentIndex.value, quotes)
            _currentQuote.value = quote
            _currentIndex.value = index
        }
    }

    fun updateConfig(config: QuoteDisplayConfig) {
        _currentConfig.value = config
    }

    fun setCurrentQuote(quote: SplashQuoteData?) {
        _currentQuote.value = quote
    }

    private fun startRotation(
        quotes: List<SplashQuoteData>,
        strategy: SplashQuoteStrategy,
        intervalMs: Long,
    ) {
        rotationJob?.cancel()
        rotationJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                nextQuote(quotes, strategy)
            }
        }
    }

    fun stopDisplay() {
        rotationJob?.cancel()
    }

    fun dispose() {
        stopDisplay()
        scope.cancel()
    }
}
