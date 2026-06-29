package com.lumecard.shared.data.ai

import com.lumecard.shared.data.AiConfig

interface ProviderAdapter {
    val providerId: String

    suspend fun sendRequest(
        config: AiConfig,
        systemPrompt: String,
        userMessage: String,
    ): Result<String>

    suspend fun testConnection(config: AiConfig): Result<String>

    suspend fun streamRequest(
        config: AiConfig,
        systemPrompt: String,
        userMessage: String,
        onChunk: (String) -> Unit,
    ): Result<String> {
        throw UnsupportedOperationException("Streaming not supported for $providerId")
    }
}
