package com.lumecard.shared.data.ai

import com.lumecard.shared.data.AiClient
import com.lumecard.shared.data.AiConfig

class AiClientAdapter(
    private val client: AiClient,
) : ProviderAdapter {
    override val providerId: String = "default"

    override suspend fun sendRequest(
        config: AiConfig,
        systemPrompt: String,
        userMessage: String,
    ): Result<String> {
        return client.sendChatCompletion(config, systemPrompt, userMessage)
    }

    override suspend fun testConnection(config: AiConfig): Result<String> {
        return client.testConnection(config)
    }
}
