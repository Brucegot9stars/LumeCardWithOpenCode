package com.lumecard.shared.data

import com.lumecard.shared.data.ai.stream.AiStreamParser
import com.lumecard.shared.data.ai.stream.ParseResult
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*

class AiClient(private val client: HttpClient) {

    suspend fun testConnection(config: AiConfig): Result<String> {
        return try {
            val protocol = AiProtocols.findById(config.protocol)
                ?: return Result.failure(AiException("Unknown protocol: ${config.protocol}"))

            val baseUrl = config.baseUrl.trimEnd('/')
            if (baseUrl.isBlank()) return Result.failure(AiException("Base URL is empty"))
            if (config.apiKey.isBlank()) return Result.failure(AiException("API Key is empty"))
            if (config.model.isBlank()) return Result.failure(AiException("Model is empty"))

            val body = protocol.buildTestRequestBody(config)
            val response = client.post("$baseUrl${protocol.endpoint(config)}") {
                protocol.headers(config).forEach { (k, v) -> header(k, v) }
                setBody(body)
            }

            val responseBody = response.bodyAsText()

            return if (response.status.isSuccess()) {
                protocol.parseTestResponse(responseBody)
            } else {
                val errMsg = protocol.extractError(responseBody, response.status.value)
                when (response.status.value) {
                    401 -> Result.failure(AiException("Authentication failed (401)\n$errMsg"))
                    403 -> Result.failure(AiException("Forbidden (403)\n$errMsg"))
                    404 -> Result.failure(AiException("Endpoint or model not found (404)\n$errMsg"))
                    429 -> Result.failure(AiException("Rate limit exceeded (429)\n$errMsg"))
                    in 500..599 -> Result.failure(AiException("Server error (${response.status.value})\n$errMsg"))
                    else -> Result.failure(AiException("HTTP ${response.status.value}\n$errMsg"))
                }
            }
        } catch (e: AiException) {
            Result.failure(e)
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            val kind = when {
                msg.contains("timeout", ignoreCase = true) -> "Timeout"
                msg.contains("refused", ignoreCase = true) -> "Connection refused"
                msg.contains("resolve", ignoreCase = true) || msg.contains("host", ignoreCase = true) -> "DNS resolution failed"
                msg.contains("ssl", ignoreCase = true) || msg.contains("certificate", ignoreCase = true) -> "SSL error"
                else -> "Connection failed"
            }
            Result.failure(AiException("$kind: ${e.message ?: "unknown error"}"))
        }
    }

    suspend fun sendChatCompletion(
        config: AiConfig,
        systemPrompt: String,
        userMessage: String,
        onProgress: ((received: Long, total: Long?) -> Unit)? = null,
    ): Result<String> {
        return try {
            val protocol = AiProtocols.findById(config.protocol)
                ?: return Result.failure(AiException("Unknown protocol: ${config.protocol}"))

            val baseUrl = config.baseUrl.trimEnd('/')
            if (baseUrl.isBlank()) return Result.failure(AiException("Base URL is empty"))
            if (config.apiKey.isBlank()) return Result.failure(AiException("API Key is empty"))
            if (config.model.isBlank()) return Result.failure(AiException("Model is empty"))

            val body = protocol.buildChatRequest(config, systemPrompt, userMessage)
            val response = client.post("$baseUrl${protocol.endpoint(config)}") {
                protocol.headers(config).forEach { (k, v) -> header(k, v) }
                setBody(body)
            }

            val contentLength = response.contentLength()

            val responseBody = if (onProgress != null) {
                buildString {
                    val channel = response.bodyAsChannel()
                    var received = 0L
                    val buffer = ByteArray(4096)
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer)
                        if (read > 0) {
                            append(buffer.decodeToString(0, read))
                            received += read
                            onProgress(received, contentLength)
                        }
                    }
                }
            } else {
                response.bodyAsText()
            }

            return if (response.status.isSuccess()) {
                protocol.parseChatResponse(responseBody)
            } else {
                val errMsg = protocol.extractError(responseBody, response.status.value)
                when (response.status.value) {
                    401 -> Result.failure(AiException("Authentication failed (401)\n$errMsg"))
                    403 -> Result.failure(AiException("Forbidden (403)\n$errMsg"))
                    404 -> Result.failure(AiException("Endpoint or model not found (404)\n$errMsg"))
                    429 -> Result.failure(AiException("Rate limit exceeded (429)\n$errMsg"))
                    in 500..599 -> Result.failure(AiException("Server error (${response.status.value})\n$errMsg"))
                    else -> Result.failure(AiException("HTTP ${response.status.value}\n$errMsg"))
                }
            }
        } catch (e: AiException) {
            Result.failure(e)
        } catch (e: UnsupportedOperationException) {
            Result.failure(AiException("Protocol does not support card generation: ${e.message}"))
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            val kind = when {
                msg.contains("timeout", ignoreCase = true) -> "Timeout"
                msg.contains("refused", ignoreCase = true) -> "Connection refused"
                msg.contains("resolve", ignoreCase = true) || msg.contains("host", ignoreCase = true) -> "DNS resolution failed"
                msg.contains("ssl", ignoreCase = true) || msg.contains("certificate", ignoreCase = true) -> "SSL error"
                else -> "Connection failed"
            }
            Result.failure(AiException("$kind: ${e.message ?: "unknown error"}"))
        }
    }

    suspend fun sendChatCompletionStreaming(
        config: AiConfig,
        systemPrompt: String,
        userMessage: String,
        parser: AiStreamParser,
        onEvent: (List<ParseResult>) -> Unit,
        onProgress: ((received: Long, total: Long?) -> Unit)? = null,
    ): Result<String> {
        return try {
            val protocol = AiProtocols.findById(config.protocol)
                ?: return Result.failure(AiException("Unknown protocol: ${config.protocol}"))

            val baseUrl = config.baseUrl.trimEnd('/')
            if (baseUrl.isBlank()) return Result.failure(AiException("Base URL is empty"))
            if (config.apiKey.isBlank()) return Result.failure(AiException("API Key is empty"))
            if (config.model.isBlank()) return Result.failure(AiException("Model is empty"))

            val body = protocol.buildChatRequest(config, systemPrompt, userMessage)
            val response = client.post("$baseUrl${protocol.endpoint(config)}") {
                protocol.headers(config).forEach { (k, v) -> header(k, v) }
                setBody(body)
            }

            val contentLength = response.contentLength()
            val responseBody = buildString {
                val channel = response.bodyAsChannel()
                var received = 0L
                val buffer = ByteArray(8192)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer)
                    if (read > 0) {
                        val text = buffer.decodeToString(0, read)
                        append(text)
                        received += read
                        onProgress?.invoke(received, contentLength)
                        val results = parser.feed(text)
                        if (results.isNotEmpty()) {
                            onEvent(results)
                        }
                    }
                }
            }

            return if (response.status.isSuccess()) {
                Result.success(responseBody)
            } else {
                val errMsg = protocol.extractError(responseBody, response.status.value)
                when (response.status.value) {
                    401 -> Result.failure(AiException("Authentication failed (401)\n$errMsg"))
                    403 -> Result.failure(AiException("Forbidden (403)\n$errMsg"))
                    404 -> Result.failure(AiException("Endpoint or model not found (404)\n$errMsg"))
                    429 -> Result.failure(AiException("Rate limit exceeded (429)\n$errMsg"))
                    in 500..599 -> Result.failure(AiException("Server error (${response.status.value})\n$errMsg"))
                    else -> Result.failure(AiException("HTTP ${response.status.value}\n$errMsg"))
                }
            }
        } catch (e: AiException) {
            Result.failure(e)
        } catch (e: UnsupportedOperationException) {
            Result.failure(AiException("Protocol does not support card generation: ${e.message}"))
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            val kind = when {
                msg.contains("timeout", ignoreCase = true) -> "Timeout"
                msg.contains("refused", ignoreCase = true) -> "Connection refused"
                msg.contains("resolve", ignoreCase = true) || msg.contains("host", ignoreCase = true) -> "DNS resolution failed"
                msg.contains("ssl", ignoreCase = true) || msg.contains("certificate", ignoreCase = true) -> "SSL error"
                else -> "Connection failed"
            }
            Result.failure(AiException("$kind: ${e.message ?: "unknown error"}"))
        }
    }
}
