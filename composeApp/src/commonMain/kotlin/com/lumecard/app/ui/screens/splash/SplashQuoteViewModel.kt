package com.lumecard.app.ui.screens.splash

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.data.SplashQuoteData
import com.lumecard.shared.data.SplashQuoteDirection
import com.lumecard.shared.data.SplashQuoteManager
import com.lumecard.shared.data.SplashQuoteSettings
import com.lumecard.shared.data.SplashQuoteStrategy
import com.lumecard.shared.data.SplashQuotesCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SplashQuoteViewModel(
    private val manager: SplashQuoteManager,
) : ScreenModel {

    private val _settings = MutableStateFlow(SplashQuoteSettings())
    val settings: StateFlow<SplashQuoteSettings> = _settings.asStateFlow()

    private val _userQuotes = MutableStateFlow<List<SplashQuoteData>>(emptyList())
    val userQuotes: StateFlow<List<SplashQuoteData>> = _userQuotes.asStateFlow()

    private val _defaultQuotes = MutableStateFlow<List<SplashQuoteData>>(emptyList())
    val defaultQuotes: StateFlow<List<SplashQuoteData>> = _defaultQuotes.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun load() {
        screenModelScope.launch {
            _settings.value = manager.loadSettings()
            _userQuotes.value = manager.getUserQuotes()
            _defaultQuotes.value = manager.getDefaultQuotes()
        }
    }

    fun save() {
        screenModelScope.launch {
            _isSaving.value = true
            manager.saveSettings(_settings.value)
            _isSaving.value = false
        }
    }

    fun setEnabled(v: Boolean) { _settings.value = _settings.value.copy(enabled = v) }
    fun setDuration(v: Int) { _settings.value = _settings.value.copy(durationSeconds = v.coerceIn(1, 30)) }
    fun setDirection(v: SplashQuoteDirection) { _settings.value = _settings.value.copy(direction = v) }
    fun setFont(v: String) { _settings.value = _settings.value.copy(font = v) }
    fun setFontSize(v: Float) { _settings.value = _settings.value.copy(fontSize = v) }
    fun setBackgroundPath(v: String) { _settings.value = _settings.value.copy(backgroundPath = v) }
    fun setStrategy(v: SplashQuoteStrategy) { _settings.value = _settings.value.copy(strategy = v) }
    fun setShowAuthor(v: Boolean) { _settings.value = _settings.value.copy(showAuthor = v) }

    // ── Quote CRUD ──────────────────────────────────────

    fun addQuote(quote: SplashQuoteData) {
        screenModelScope.launch {
            manager.addQuote(quote)
            _userQuotes.value = manager.getUserQuotes()
        }
    }

    fun updateQuote(index: Int, quote: SplashQuoteData) {
        screenModelScope.launch {
            manager.updateQuote(index, quote)
            _userQuotes.value = manager.getUserQuotes()
        }
    }

    suspend fun saveQuote(quoteIndex: Int, quote: SplashQuoteData) {
        if (quoteIndex < 0) {
            manager.addQuote(quote)
        } else {
            manager.updateQuote(quoteIndex, quote)
        }
        _userQuotes.value = manager.getUserQuotes()
    }

    fun deleteQuote(index: Int) {
        screenModelScope.launch {
            manager.deleteQuote(index)
            _userQuotes.value = manager.getUserQuotes()
        }
    }

    fun importQuotes(collection: SplashQuotesCollection, mode: SplashQuoteManager.ImportMode) {
        screenModelScope.launch {
            manager.importQuotes(collection, mode)
            _userQuotes.value = manager.getUserQuotes()
        }
    }

    fun exportQuotes(callback: (SplashQuotesCollection) -> Unit) {
        screenModelScope.launch {
            callback(manager.exportQuotes())
        }
    }

    fun getQuoteForPreview(): SplashQuoteData? {
        val s = _settings.value
        val quotes = run {
            val uq = _userQuotes.value
            if (uq.isNotEmpty()) uq else {
                kotlinx.coroutines.runBlocking { manager.getAllQuotes() }
            }
        }
        val (quote, _) = manager.getQuoteForDisplay(s.strategy, s.sequenceIndex, quotes)
        return quote
    }

    // ── Built-in quote hiding ─────────────────────────────

    fun hideDefaultQuote(index: Int) {
        screenModelScope.launch {
            manager.hideDefaultQuote(index)
            _defaultQuotes.value = manager.getDefaultQuotes()
        }
    }

    fun restoreAllDefaultQuotes() {
        screenModelScope.launch {
            manager.restoreAllDefaultQuotes()
            _defaultQuotes.value = manager.getDefaultQuotes()
        }
    }
}
