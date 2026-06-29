package com.lumecard.shared.data.ai

import com.lumecard.shared.data.AiConfig
import com.lumecard.shared.data.AiConfigManager

class AiFallbackManager(
    private val configManager: AiConfigManager,
    private val adapter: ProviderAdapter,
) {
    suspend fun sendWithFallback(
        config: AiConfig,
        systemPrompt: String,
        userMessage: String,
    ): Result<String> {
        val result = adapter.sendRequest(config, systemPrompt, userMessage)

        if (result.isSuccess) return result

        val shouldFallback = isFallbackError(result.exceptionOrNull())
        if (!shouldFallback) return result

        val fallbackConfig = findFallbackConfig(config)
        if (fallbackConfig == null || fallbackConfig.id == config.id) return result

        return adapter.sendRequest(fallbackConfig, systemPrompt, userMessage)
    }

    private fun isFallbackError(error: Throwable?): Boolean {
        if (error == null) return false
        val msg = error.message ?: ""
        return msg.contains("429") ||
            msg.contains("Timeout") ||
            msg.contains("Connection refused") ||
            msg.contains("Server error (5") ||
            msg.contains("rate limit", ignoreCase = true) ||
            msg.contains("503") ||
            msg.contains("502") ||
            msg.contains("500")
    }

    private suspend fun findFallbackConfig(primary: AiConfig): AiConfig? {
        if (primary.fallbackConfigId != null) {
            val fc = configManager.getById(primary.fallbackConfigId)
            if (fc != null) return fc
        }

        val allConfigs = configManager.getAll()
        return allConfigs.firstOrNull { it.id != primary.id }
    }
}
