package com.lumecard.shared.data

import com.lumecard.shared.repository.SettingsRepository
import com.lumecard.shared.util.loadTextResource
import kotlinx.serialization.json.Json
import kotlin.random.Random

class SplashQuoteManager(
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        private const val QUOTES_RESOURCE = "/config/quotes.json"
        private const val KEY_ENABLED = "splash_quote_enabled"
        private const val KEY_DIRECTION = "splash_quote_direction"
        private const val KEY_FONT = "splash_quote_font"
        private const val KEY_FONT_SIZE = "splash_quote_font_size"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var cachedQuotes: List<SplashQuoteData>? = null

    suspend fun isEnabled(): Boolean = settingsRepository.getBoolean(KEY_ENABLED, true)

    suspend fun setEnabled(enabled: Boolean) = settingsRepository.set(KEY_ENABLED, enabled.toString())

    suspend fun getDirection(): SplashQuoteDirection {
        val raw = settingsRepository.get(KEY_DIRECTION) ?: return SplashQuoteDirection.HORIZONTAL
        return try { SplashQuoteDirection.valueOf(raw) } catch (_: Exception) { SplashQuoteDirection.HORIZONTAL }
    }

    suspend fun setDirection(direction: SplashQuoteDirection) = settingsRepository.set(KEY_DIRECTION, direction.name)

    suspend fun getFont(): String = settingsRepository.get(KEY_FONT) ?: ""

    suspend fun setFont(font: String) = settingsRepository.set(KEY_FONT, font)

    suspend fun getFontSize(): Float = settingsRepository.get(KEY_FONT_SIZE)?.toFloatOrNull() ?: 0f

    suspend fun setFontSize(size: Float) = settingsRepository.set(KEY_FONT_SIZE, size.toString())

    fun getRandomQuote(): SplashQuoteData? {
        val quotes = loadQuotes() ?: return null
        if (quotes.isEmpty()) return null
        return quotes[Random.nextInt(quotes.size)]
    }

    private fun loadQuotes(): List<SplashQuoteData>? {
        if (cachedQuotes != null) return cachedQuotes
        val raw = loadTextResource(QUOTES_RESOURCE) ?: return null
        return try {
            val collection = json.decodeFromString<SplashQuotesCollection>(raw)
            collection.quotes.also { cachedQuotes = it }
        } catch (_: Exception) {
            null
        }
    }
}
