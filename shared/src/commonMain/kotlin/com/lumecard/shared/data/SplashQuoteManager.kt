package com.lumecard.shared.data

import com.lumecard.shared.repository.SettingsRepository
import com.lumecard.shared.util.loadTextResource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.absoluteValue
import kotlin.random.Random

class SplashQuoteManager(
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        private const val QUOTES_RESOURCE = "/config/quotes.json"
        private const val KEY_USER_QUOTES = "splash_quote_user_quotes"

        private const val KEY_ENABLED = "splash_quote_enabled"
        private const val KEY_DURATION = "splash_quote_duration"
        private const val KEY_DIRECTION = "splash_quote_direction"
        private const val KEY_FONT = "splash_quote_font"
        private const val KEY_FONT_SIZE = "splash_quote_font_size"
        private const val KEY_BACKGROUND = "splash_quote_background"
        private const val KEY_STRATEGY = "splash_quote_strategy"
        private const val KEY_SEQUENCE_INDEX = "splash_quote_sequence_index"
        private const val KEY_SHOW_AUTHOR = "splash_quote_show_author"
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var cachedDefaultQuotes: List<SplashQuoteData>? = null

    // ── Settings ──────────────────────────────────────────

    suspend fun loadSettings(): SplashQuoteSettings {
        return SplashQuoteSettings(
            enabled = settingsRepository.getBoolean(KEY_ENABLED, true),
            durationSeconds = settingsRepository.getInt(KEY_DURATION, 3).coerceIn(1, 30),
            direction = try { SplashQuoteDirection.valueOf(settingsRepository.get(KEY_DIRECTION) ?: "") } catch (_: Exception) { SplashQuoteDirection.HORIZONTAL },
            font = settingsRepository.get(KEY_FONT) ?: "",
            fontSize = settingsRepository.get(KEY_FONT_SIZE)?.toFloatOrNull() ?: 0f,
            backgroundPath = settingsRepository.get(KEY_BACKGROUND) ?: "",
            strategy = try { SplashQuoteStrategy.valueOf(settingsRepository.get(KEY_STRATEGY) ?: "") } catch (_: Exception) { SplashQuoteStrategy.RANDOM },
            sequenceIndex = settingsRepository.getInt(KEY_SEQUENCE_INDEX, 0),
            showAuthor = settingsRepository.getBoolean(KEY_SHOW_AUTHOR, true),
        )
    }

    suspend fun saveSettings(s: SplashQuoteSettings) {
        settingsRepository.set(KEY_ENABLED, s.enabled.toString())
        settingsRepository.set(KEY_DURATION, s.durationSeconds.toString())
        settingsRepository.set(KEY_DIRECTION, s.direction.name)
        settingsRepository.set(KEY_FONT, s.font)
        settingsRepository.set(KEY_FONT_SIZE, s.fontSize.toString())
        settingsRepository.set(KEY_BACKGROUND, s.backgroundPath)
        settingsRepository.set(KEY_STRATEGY, s.strategy.name)
        settingsRepository.set(KEY_SEQUENCE_INDEX, s.sequenceIndex.toString())
        settingsRepository.set(KEY_SHOW_AUTHOR, s.showAuthor.toString())
    }

    // ── Background ────────────────────────────────────────

    suspend fun getBackgroundPath(): String = settingsRepository.get(KEY_BACKGROUND) ?: ""

    suspend fun setBackgroundPath(path: String) = settingsRepository.set(KEY_BACKGROUND, path)

    // ── Quotes: combined (user + default fallback) ───────

    suspend fun getAllQuotes(): List<SplashQuoteData> {
        val userQuotes = loadUserQuotes()
        if (userQuotes.isNotEmpty()) return userQuotes
        return loadDefaultQuotes()
    }

    fun getQuoteForDisplay(strategy: SplashQuoteStrategy, currentIndex: Int, quotes: List<SplashQuoteData>): Pair<SplashQuoteData?, Int> {
        if (quotes.isEmpty()) return null to currentIndex
        return when (strategy) {
            SplashQuoteStrategy.RANDOM -> {
                quotes[Random.nextInt(quotes.size)] to currentIndex
            }
            SplashQuoteStrategy.SEQUENTIAL -> {
                val idx = if (currentIndex in quotes.indices) currentIndex else 0
                quotes[idx] to ((idx + 1) % quotes.size)
            }
        }
    }

    // ── Default quotes ──────────────────────────────────

    suspend fun getDefaultQuotes(): List<SplashQuoteData> {
        return loadDefaultQuotes()
    }

    // ── Quotes: CRUD on user quotes ──────────────────────

    suspend fun getUserQuotes(): List<SplashQuoteData> {
        return loadUserQuotes()
    }

    suspend fun addQuote(quote: SplashQuoteData) {
        val list = loadUserQuotes().toMutableList()
        list.add(quote)
        saveUserQuotes(list)
    }

    suspend fun updateQuote(index: Int, quote: SplashQuoteData) {
        val list = loadUserQuotes().toMutableList()
        if (index in list.indices) {
            list[index] = quote
            saveUserQuotes(list)
        }
    }

    suspend fun deleteQuote(index: Int) {
        val list = loadUserQuotes().toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            saveUserQuotes(list)
        }
    }

    suspend fun importQuotes(collection: SplashQuotesCollection, mode: ImportMode) {
        when (mode) {
            ImportMode.APPEND -> {
                val list = loadUserQuotes().toMutableList()
                list.addAll(collection.quotes)
                saveUserQuotes(list)
            }
            ImportMode.OVERWRITE -> {
                saveUserQuotes(collection.quotes)
            }
        }
    }

    suspend fun exportQuotes(): SplashQuotesCollection {
        return SplashQuotesCollection(loadUserQuotes())
    }

    enum class ImportMode { APPEND, OVERWRITE }

    // ── Internal ──────────────────────────────────────────

    private suspend fun loadUserQuotes(): List<SplashQuoteData> {
        val raw = settingsRepository.get(KEY_USER_QUOTES) ?: return emptyList()
        return try {
            json.decodeFromString<SplashQuotesCollection>(raw).quotes
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun saveUserQuotes(quotes: List<SplashQuoteData>) {
        val raw = json.encodeToString(SplashQuotesCollection(quotes))
        settingsRepository.set(KEY_USER_QUOTES, raw)
    }

    private fun loadDefaultQuotes(): List<SplashQuoteData> {
        if (cachedDefaultQuotes != null) return cachedDefaultQuotes!!
        val raw = loadTextResource(QUOTES_RESOURCE) ?: return emptyList()
        return try {
            json.decodeFromString<SplashQuotesCollection>(raw).quotes.also { cachedDefaultQuotes = it }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun previewQuote(settings: SplashQuoteSettings): SplashQuoteData? {
        val quotes = getAllQuotes()
        val (quote, _) = getQuoteForDisplay(settings.strategy, settings.sequenceIndex, quotes)
        return quote
    }
}
