package com.lumecard.shared.data.ai

import com.lumecard.shared.data.AiConfig
import com.lumecard.shared.data.ai.stream.AiStreamParser

interface ProviderAdapter {
    val providerId: String

    suspend fun sendRequest(
        config: AiConfig,
        systemPrompt: String,
        userMessage: String,
        onProgress: ((received: Long, total: Long?) -> Unit)? = null,
    ): Result<String>

    suspend fun sendRequestStreaming(
        config: AiConfig,
        systemPrompt: String,
        userMessage: String,
        parser: AiStreamParser,
        onEvent: (List<com.lumecard.shared.data.ai.stream.ParseResult>) -> Unit,
        onProgress: ((received: Long, total: Long?) -> Unit)? = null,
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
