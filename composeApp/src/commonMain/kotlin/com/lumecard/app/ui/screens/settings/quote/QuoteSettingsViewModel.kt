package com.lumecard.app.ui.screens.settings.quote

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.lumecard.shared.data.SplashQuoteData
import com.lumecard.shared.data.SplashQuoteDirection
import com.lumecard.shared.data.SplashQuoteManager
import com.lumecard.shared.data.SplashQuoteSettings
import com.lumecard.shared.data.SplashQuoteStrategy
import com.lumecard.shared.feature.quote.config.QuoteAnimationStyle
import com.lumecard.shared.feature.quote.facade.QuoteFeature
import com.lumecard.shared.feature.quote.facade.ScreenSaverSettings
import com.lumecard.shared.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UnifiedQuoteSettings(
    val enabled: Boolean = true,
    val durationSeconds: Int = 3,
    val strategy: SplashQuoteStrategy = SplashQuoteStrategy.RANDOM,
    val showAuthor: Boolean = true,
    val direction: SplashQuoteDirection = SplashQuoteDirection.HORIZONTAL,
    val font: String = "",
    val fontSize: Float = 0f,
    val backgroundPath: String = "",
    val screenSaverEnabled: Boolean = true,
    val screenSaverIdleMinutes: Int = 3,
    val screenSaverRotationSeconds: Int = 3,
    val enableAnimation: Boolean = true,
    val animationStyle: QuoteAnimationStyle = QuoteAnimationStyle.FADE_IN,
)

class QuoteSettingsViewModel(
    private val manager: SplashQuoteManager,
    private val quoteFeature: QuoteFeature,
    private val settingsRepository: SettingsRepository,
) : ScreenModel {

    private val _settings = MutableStateFlow(UnifiedQuoteSettings())
    val settings: StateFlow<UnifiedQuoteSettings> = _settings.asStateFlow()

    private val _sections = MutableStateFlow(
        mapOf(
            "splash" to true,
            "screen_saver" to true,
            "management" to true,
            "font_layout" to false,
            "animation" to false,
            "background" to false,
        )
    )
    val sections: StateFlow<Map<String, Boolean>> = _sections.asStateFlow()
    private val savedSections = mutableMapOf<String, Boolean>()

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    fun load() {
        screenModelScope.launch {
            val ss = manager.loadSettings()
            val saver = quoteFeature.loadScreenSaverSettings()
            _settings.value = UnifiedQuoteSettings(
                enabled = ss.enabled,
                durationSeconds = ss.durationSeconds,
                strategy = ss.strategy,
                showAuthor = ss.showAuthor,
                direction = ss.direction,
                font = ss.font,
                fontSize = ss.fontSize,
                backgroundPath = ss.backgroundPath,
                screenSaverEnabled = saver.enabled,
                screenSaverIdleMinutes = saver.idleMinutes,
                screenSaverRotationSeconds = saver.rotationSeconds,
                enableAnimation = ss.enableAnimation,
                animationStyle = ss.animationStyle,
            )
            loadSectionStates()
        }
    }

    fun save() {
        screenModelScope.launch {
            val s = _settings.value
            manager.saveSettings(
                SplashQuoteSettings(
                    enabled = s.enabled,
                    durationSeconds = s.durationSeconds,
                    direction = s.direction,
                    font = s.font,
                    fontSize = s.fontSize,
                    backgroundPath = s.backgroundPath,
                    strategy = s.strategy,
                    showAuthor = s.showAuthor,
                    enableAnimation = s.enableAnimation,
                    animationStyle = s.animationStyle,
                )
            )
            quoteFeature.saveScreenSaverSettings(
                ScreenSaverSettings(
                    enabled = s.screenSaverEnabled,
                    idleMinutes = s.screenSaverIdleMinutes,
                    rotationSeconds = s.screenSaverRotationSeconds,
                )
            )
            _isDirty.value = false
        }
    }

    fun setEnabled(v: Boolean) { _settings.value = _settings.value.copy(enabled = v); markDirty() }
    fun setDuration(v: Int) { _settings.value = _settings.value.copy(durationSeconds = v.coerceIn(1, 30)); markDirty() }
    fun setStrategy(v: SplashQuoteStrategy) { _settings.value = _settings.value.copy(strategy = v); markDirty() }
    fun setShowAuthor(v: Boolean) { _settings.value = _settings.value.copy(showAuthor = v); markDirty() }
    fun setDirection(v: SplashQuoteDirection) { _settings.value = _settings.value.copy(direction = v); markDirty() }
    fun setFont(v: String) { _settings.value = _settings.value.copy(font = v); markDirty() }
    fun setFontSize(v: Float) { _settings.value = _settings.value.copy(fontSize = v.coerceIn(0f, 72f)); markDirty() }
    fun setBackgroundPath(v: String) { _settings.value = _settings.value.copy(backgroundPath = v); markDirty() }
    fun setScreenSaverEnabled(v: Boolean) { _settings.value = _settings.value.copy(screenSaverEnabled = v); markDirty() }
    fun setScreenSaverIdleMinutes(v: Int) { _settings.value = _settings.value.copy(screenSaverIdleMinutes = v.coerceIn(1, 60)); markDirty() }
    fun setScreenSaverRotationSeconds(v: Int) { _settings.value = _settings.value.copy(screenSaverRotationSeconds = v.coerceIn(1, 30)); markDirty() }
    fun setEnableAnimation(v: Boolean) { _settings.value = _settings.value.copy(enableAnimation = v); markDirty() }
    fun setAnimationStyle(v: QuoteAnimationStyle) { _settings.value = _settings.value.copy(animationStyle = v); markDirty() }

    fun toggleSection(id: String) {
        val current = _sections.value.toMutableMap()
        current[id] = !(current[id] ?: true)
        _sections.value = current
        saveSectionState(id, current[id] ?: true)
    }

    fun setSection(id: String, expanded: Boolean) {
        val current = _sections.value.toMutableMap()
        current[id] = expanded
        _sections.value = current
    }

    suspend fun getPreviewQuote(): SplashQuoteData? {
        val s = _settings.value
        val ss = SplashQuoteSettings(
            enabled = s.enabled,
            durationSeconds = s.durationSeconds,
            direction = s.direction,
            font = s.font,
            fontSize = s.fontSize,
            backgroundPath = s.backgroundPath,
            strategy = s.strategy,
            showAuthor = s.showAuthor,
            enableAnimation = s.enableAnimation,
            animationStyle = s.animationStyle,
        )
        return manager.previewQuote(ss)
    }

    private fun markDirty() { _isDirty.value = true }

    private suspend fun loadSectionStates() {
        SECTION_IDS.forEach { id ->
            val saved = settingsRepository.get(SECTION_KEY_PREFIX + id)
            if (saved != null) {
                val expanded = saved.equals("true", ignoreCase = true)
                savedSections[id] = expanded
            } else {
                savedSections[id] = DEFAULT_SECTION_STATES[id] ?: true
            }
        }
        _sections.value = SECTION_IDS.associateWith { savedSections[it] ?: DEFAULT_SECTION_STATES[it] ?: true }
    }

    private fun saveSectionState(id: String, expanded: Boolean) {
        screenModelScope.launch {
            settingsRepository.set(SECTION_KEY_PREFIX + id, expanded.toString())
        }
    }

    companion object {
        private const val SECTION_KEY_PREFIX = "quote_section_expanded_"
        val SECTION_IDS = listOf("splash", "screen_saver", "management", "font_layout", "animation", "background")
        val DEFAULT_SECTION_STATES = mapOf(
            "splash" to true,
            "screen_saver" to true,
            "management" to true,
            "font_layout" to false,
            "animation" to false,
            "background" to false,
        )
    }
}
