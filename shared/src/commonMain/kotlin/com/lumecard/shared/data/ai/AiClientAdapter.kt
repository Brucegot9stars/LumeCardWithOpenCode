package com.lumecard.shared.data.ai

import com.lumecard.shared.data.AiClient
import com.lumecard.shared.data.AiConfig
import com.lumecard.shared.data.ai.stream.AiStreamParser
import com.lumecard.shared.data.ai.stream.ParseResult

class AiClientAdapter(
    private val client: AiClient,
) : ProviderAdapter {
    override val providerId: String = "default"

    override suspend fun sendRequest(
        config: AiConfig,
        systemPrompt: String,
        userMessage: String,
        onProgress: ((received: Long, total: Long?) -> Unit)?,
    ): Result<String> {
        return client.sendChatCompletion(config, systemPrompt, userMessage, onProgress)
    }

    override suspend fun sendRequestStreaming(
        config: AiConfig,
        systemPrompt: String,
        userMessage: String,
        parser: AiStreamParser,
        onEvent: (List<ParseResult>) -> Unit,
        onProgress: ((received: Long, total: Long?) -> Unit)?,
    ): Result<String> {
        return client.sendChatCompletionStreaming(config, systemPrompt, userMessage, parser, onEvent, onProgress)
    }

    override suspend fun testConnection(config: AiConfig): Result<String> {
        return client.testConnection(config)
    }
}
