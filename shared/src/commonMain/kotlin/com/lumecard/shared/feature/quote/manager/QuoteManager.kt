package com.lumecard.shared.feature.quote.manager

import com.lumecard.shared.data.*
import kotlinx.coroutines.flow.StateFlow

class QuoteManager(
    private val splashQuoteManager: SplashQuoteManager,
) {
    suspend fun getAllQuotes(): List<SplashQuoteData> = splashQuoteManager.getAllQuotes()

    suspend fun getUserQuotes(): List<SplashQuoteData> = splashQuoteManager.getUserQuotes()

    suspend fun getDefaultQuotes(): List<SplashQuoteData> = splashQuoteManager.getDefaultQuotes()

    suspend fun getQuoteForDisplay(
        strategy: SplashQuoteStrategy,
        currentIndex: Int,
        quotes: List<SplashQuoteData>,
    ): Pair<SplashQuoteData?, Int> = splashQuoteManager.getQuoteForDisplay(strategy, currentIndex, quotes)

    suspend fun addQuote(quote: SplashQuoteData) = splashQuoteManager.addQuote(quote)

    suspend fun updateQuote(index: Int, quote: SplashQuoteData) = splashQuoteManager.updateQuote(index, quote)

    suspend fun deleteQuote(index: Int) = splashQuoteManager.deleteQuote(index)

    suspend fun importQuotes(collection: SplashQuotesCollection, mode: SplashQuoteManager.ImportMode) =
        splashQuoteManager.importQuotes(collection, mode)

    suspend fun exportQuotes(): SplashQuotesCollection = splashQuoteManager.exportQuotes()

    suspend fun loadSettings(): SplashQuoteSettings = splashQuoteManager.loadSettings()

    suspend fun saveSettings(s: SplashQuoteSettings) = splashQuoteManager.saveSettings(s)

    suspend fun getGlobalBackgroundPath(): String = splashQuoteManager.getBackgroundPath()

    suspend fun setGlobalBackgroundPath(path: String) = splashQuoteManager.setBackgroundPath(path)
}
