package com.lumecard.shared.data

import com.lumecard.shared.repository.SettingsRepository
import com.lumecard.shared.util.loadTextResource

class AiCardPromptManager(
    private val settingsRepository: SettingsRepository,
) {
    companion object {
        private const val PROMPT_RESOURCE = "/config/prompt_ai_card.txt"
        private const val PROMPT_SETTINGS_KEY = "ai_card_prompt"
    }

    suspend fun getActivePrompt(): String {
        val saved = settingsRepository.get(PROMPT_SETTINGS_KEY)
        if (!saved.isNullOrBlank()) return saved
        return loadDefaultPrompt()
    }

    suspend fun savePrompt(prompt: String) {
        settingsRepository.set(PROMPT_SETTINGS_KEY, prompt)
    }

    suspend fun resetToDefault(): String {
        settingsRepository.delete(PROMPT_SETTINGS_KEY)
        return loadDefaultPrompt()
    }

    private fun loadDefaultPrompt(): String {
        return loadTextResource(PROMPT_RESOURCE)
            ?: error("Default prompt resource not found: $PROMPT_RESOURCE")
    }
}
