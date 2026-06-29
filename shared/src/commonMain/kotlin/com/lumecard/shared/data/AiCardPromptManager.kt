package com.lumecard.shared.data

import com.lumecard.shared.repository.SettingsRepository

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
        return try {
            val stream = object {}.javaClass.getResourceAsStream(PROMPT_RESOURCE)
            stream?.bufferedReader()?.readText()?.trim()
                ?: throw IllegalStateException("Prompt resource not found: $PROMPT_RESOURCE")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load default prompt: ${e.message}")
        }
    }
}
