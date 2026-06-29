package com.lumecard.shared.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AiException(override val message: String) : Exception(message)

interface AiProtocol {
    val id: String
    val displayName: String

    /** Relative URL path for the test endpoint (e.g. "/chat/completions"). */
    fun endpoint(config: AiConfig? = null): String

    /** HTTP headers required by this protocol (auth, content-type, version headers). */
    fun headers(config: AiConfig): Map<String, String>

    /** Build the minimal test request body. */
    fun buildTestRequestBody(config: AiConfig): String

    /** Validate the test response and return success or an error message. */
    fun parseTestResponse(responseBody: String): Result<String>

    /** Extract a human-readable error from a failed response body. */
    fun extractError(responseBody: String, statusCode: Int): String

    /** Build a chat completion request body with system prompt and user message. */
    fun buildChatRequest(config: AiConfig, systemPrompt: String, userMessage: String): String {
        throw UnsupportedOperationException("Protocol ${id} does not support chat completions")
    }

    /** Parse the text content from a chat completion response. */
    fun parseChatResponse(responseBody: String): Result<String> {
        return Result.failure(AiException("Chat completions not supported for protocol $id"))
    }
}

// ─── OpenAI Chat Completions ───────────────────────────────────────────

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class OpenAiChatMessage(val role: String, val content: String)

@Serializable
private data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val max_tokens: Int = 2,
    val temperature: Double = 0.0,
)

@Serializable
private data class OpenAiChatFullRequest(
    val model: String,
    val messages: List<OpenAiChatMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 4096,
)

@Serializable
private data class OpenAiChatChoice(val message: OpenAiChatMessage)

@Serializable
private data class OpenAiChatUsage(val total_tokens: Int)

@Serializable
private data class OpenAiChatResponse(
    val choices: List<OpenAiChatChoice>? = null,
    val usage: OpenAiChatUsage? = null,
    val error: OpenAiError? = null,
)

@Serializable
private data class OpenAiError(val message: String, val type: String? = null, val code: String? = null)

@Serializable
private data class OpenAiErrorBody(val error: OpenAiError)

class OpenAiChatProtocol : AiProtocol {
    override val id: String = "openai_chat"
    override val displayName: String = "OpenAI Chat Completions"

    override fun endpoint(config: AiConfig?): String = "/chat/completions"

    override fun headers(config: AiConfig): Map<String, String> = mapOf(
        "Authorization" to "Bearer ${config.apiKey}",
        "Content-Type" to "application/json",
    )

    override fun buildTestRequestBody(config: AiConfig): String = json.encodeToString(
        OpenAiChatRequest.serializer(), OpenAiChatRequest(
            model = config.model,
            messages = listOf(
                OpenAiChatMessage("user", "Hello"),
            ),
        )
    )

    override fun parseTestResponse(responseBody: String): Result<String> {
        return try {
            val resp = json.decodeFromString(OpenAiChatResponse.serializer(), responseBody)
            if (resp.error != null) {
                return Result.failure(AiException(resp.error.message))
            }
            val choice = resp.choices?.firstOrNull()
                ?: return Result.failure(AiException("No response choices returned"))
            val msg = choice.message.content
            val tokens = resp.usage?.total_tokens ?: 0
            Result.success("OK ($tokens tokens)")
        } catch (e: Exception) {
            Result.failure(AiException("Parse error: ${e.message}"))
        }
    }

    override fun extractError(responseBody: String, statusCode: Int): String {
        return try {
            val err = json.decodeFromString(OpenAiErrorBody.serializer(), responseBody)
            val parts = listOfNotNull(err.error.code, err.error.type, err.error.message)
            parts.joinToString(" — ")
        } catch (_: Exception) {
            try {
                val obj = json.parseToJsonElement(responseBody).jsonObject
                obj["error"]?.jsonObject?.let { e ->
                    listOfNotNull(
                        e["code"]?.jsonPrimitive?.content,
                        e["type"]?.jsonPrimitive?.content,
                        e["message"]?.jsonPrimitive?.content,
                    ).joinToString(" — ")
                } ?: "HTTP $statusCode"
            } catch (_: Exception) {
                "HTTP $statusCode"
            }
        }
    }

    override fun buildChatRequest(config: AiConfig, systemPrompt: String, userMessage: String): String {
        return json.encodeToString(
            OpenAiChatFullRequest.serializer(),
            OpenAiChatFullRequest(
                model = config.model,
                messages = listOf(
                    OpenAiChatMessage("system", systemPrompt),
                    OpenAiChatMessage("user", userMessage),
                ),
                temperature = config.temperature.toDouble(),
                max_tokens = config.maxTokens.coerceIn(256, 8192),
            )
        )
    }

    override fun parseChatResponse(responseBody: String): Result<String> {
        return try {
            val resp = json.decodeFromString(OpenAiChatResponse.serializer(), responseBody)
            if (resp.error != null) {
                return Result.failure(AiException(resp.error.message))
            }
            val content = resp.choices?.firstOrNull()?.message?.content
                ?: return Result.failure(AiException("No response content returned"))
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(AiException("Parse error: ${e.message}"))
        }
    }
}

// ─── OpenAI Responses API ──────────────────────────────────────────────

@Serializable
private data class OpenAiResponseInput(val role: String, val content: String)

@Serializable
private data class OpenAiResponsesRequest(
    val model: String,
    val input: String,
    val max_output_tokens: Int = 2,
    val temperature: Double = 0.0,
)

@Serializable
private data class OpenAiResponseOutputText(val text: String?)

@Serializable
private data class OpenAiResponseContent(val text: String? = null)

@Serializable
private data class OpenAiResponseItem(
    val type: String? = null,
    val content: List<OpenAiResponseContent>? = null,
)

@Serializable
private data class OpenAiResponsesResponse(
    val id: String? = null,
    val output: List<OpenAiResponseItem>? = null,
    val error: OpenAiError? = null,
)

class OpenAiResponsesProtocol : AiProtocol {
    override val id: String = "openai_responses"
    override val displayName: String = "OpenAI Responses API"

    override fun endpoint(config: AiConfig?): String = "/responses"

    override fun headers(config: AiConfig): Map<String, String> = mapOf(
        "Authorization" to "Bearer ${config.apiKey}",
        "Content-Type" to "application/json",
    )

    override fun buildTestRequestBody(config: AiConfig): String = json.encodeToString(
        OpenAiResponsesRequest.serializer(), OpenAiResponsesRequest(
            model = config.model, input = "Hello",
        )
    )

    override fun parseTestResponse(responseBody: String): Result<String> {
        return try {
            val resp = json.decodeFromString(OpenAiResponsesResponse.serializer(), responseBody)
            if (resp.error != null) {
                return Result.failure(AiException(resp.error.message))
            }
            if (resp.id != null && resp.output != null) {
                Result.success("OK")
            } else {
                Result.failure(AiException("Unexpected response format"))
            }
        } catch (e: Exception) {
            Result.failure(AiException("Parse error: ${e.message}"))
        }
    }

    override fun extractError(responseBody: String, statusCode: Int): String {
        return try {
            val obj = json.parseToJsonElement(responseBody).jsonObject
            obj["error"]?.jsonObject?.let { e ->
                listOfNotNull(
                    e["code"]?.jsonPrimitive?.content,
                    e["type"]?.jsonPrimitive?.content,
                    e["message"]?.jsonPrimitive?.content,
                ).joinToString(" — ")
            } ?: "HTTP $statusCode"
        } catch (_: Exception) {
            "HTTP $statusCode"
        }
    }
}

// ─── Anthropic Messages API ────────────────────────────────────────────

@Serializable
private data class AnthropicMessage(val role: String, val content: String)

@Serializable
private data class AnthropicMessagesRequest(
    val model: String,
    val max_tokens: Int = 2,
    val messages: List<AnthropicMessage>,
)

@Serializable
private data class AnthropicMessagesFullRequest(
    val model: String,
    val system: String,
    val messages: List<AnthropicMessage>,
    val max_tokens: Int = 4096,
    val temperature: Double = 0.7,
)

@Serializable
private data class AnthropicContent(val text: String? = null, val type: String? = null)

@Serializable
private data class AnthropicUsage(val input_tokens: Int, val output_tokens: Int)

@Serializable
private data class AnthropicMessagesResponse(
    val id: String? = null,
    val type: String? = null,
    val content: List<AnthropicContent>? = null,
    val usage: AnthropicUsage? = null,
    val error: AnthropicErrorBody? = null,
)

@Serializable
private data class AnthropicError(val message: String, val type: String? = null)

@Serializable
private data class AnthropicErrorBody(val error: AnthropicError? = null)

class AnthropicMessagesProtocol : AiProtocol {
    override val id: String = "anthropic_messages"
    override val displayName: String = "Anthropic Messages API"

    override fun endpoint(config: AiConfig?): String = "/messages"

    override fun headers(config: AiConfig): Map<String, String> = mapOf(
        "x-api-key" to config.apiKey,
        "anthropic-version" to "2023-06-01",
        "Content-Type" to "application/json",
    )

    override fun buildTestRequestBody(config: AiConfig): String = json.encodeToString(
        AnthropicMessagesRequest.serializer(), AnthropicMessagesRequest(
            model = config.model,
            messages = listOf(AnthropicMessage("user", "Hello")),
        )
    )

    override fun parseTestResponse(responseBody: String): Result<String> {
        return try {
            val resp = json.decodeFromString(AnthropicMessagesResponse.serializer(), responseBody)
            if (resp.error != null) {
                return Result.failure(AiException(resp.error.error?.message ?: "API error"))
            }
            if (resp.type == "message" && resp.content != null) {
                val tokens = resp.usage?.let { "${it.input_tokens}+${it.output_tokens}" } ?: "?"
                Result.success("OK ($tokens tokens)")
            } else {
                Result.failure(AiException("Unexpected response type: ${resp.type}"))
            }
        } catch (e: Exception) {
            Result.failure(AiException("Parse error: ${e.message}"))
        }
    }

    override fun extractError(responseBody: String, statusCode: Int): String {
        return try {
            val err = json.decodeFromString(AnthropicErrorBody.serializer(), responseBody)
            val e = err.error
            if (e != null) {
                listOfNotNull(e.type, e.message).joinToString(" — ")
            } else "HTTP $statusCode"
        } catch (_: Exception) {
            "HTTP $statusCode"
        }
    }

    override fun buildChatRequest(config: AiConfig, systemPrompt: String, userMessage: String): String {
        return json.encodeToString(
            AnthropicMessagesFullRequest.serializer(),
            AnthropicMessagesFullRequest(
                model = config.model,
                system = systemPrompt,
                messages = listOf(
                    AnthropicMessage("user", userMessage),
                ),
                max_tokens = config.maxTokens.coerceIn(256, 8192),
                temperature = config.temperature.toDouble(),
            )
        )
    }

    override fun parseChatResponse(responseBody: String): Result<String> {
        return try {
            val resp = json.decodeFromString(AnthropicMessagesResponse.serializer(), responseBody)
            if (resp.error != null) {
                return Result.failure(AiException(resp.error.error?.message ?: "API error"))
            }
            val text = resp.content?.firstOrNull { it.type == "text" }?.text
                ?: return Result.failure(AiException("No text content in response"))
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(AiException("Parse error: ${e.message}"))
        }
    }
}

// ─── Google Generative AI ────────────────────────────────────────────

@Serializable
private data class GoogleGenAiContent(
    val role: String = "user",
    val parts: List<GoogleGenAiPart>,
)

@Serializable
private data class GoogleGenAiPart(val text: String)

@Serializable
private data class GoogleGenAiRequest(
    val contents: List<GoogleGenAiContent>,
    val generationConfig: GoogleGenAiConfig = GoogleGenAiConfig(),
)

@Serializable
private data class GoogleGenAiConfig(
    val maxOutputTokens: Int = 2,
    val temperature: Double = 0.0,
)

@Serializable
private data class GoogleGenAiFullRequest(
    val contents: List<GoogleGenAiContent>,
    val systemInstruction: GoogleGenAiContent? = null,
    val generationConfig: GoogleGenAiFullConfig = GoogleGenAiFullConfig(),
)

@Serializable
private data class GoogleGenAiFullConfig(
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 4096,
)

@Serializable
private data class GoogleGenAiCandidate(
    val content: GoogleGenAiContent? = null,
    val finishReason: String? = null,
)

@Serializable
private data class GoogleGenAiUsage(val promptTokenCount: Int, val candidatesTokenCount: Int)

@Serializable
private data class GoogleGenAiResponse(
    val candidates: List<GoogleGenAiCandidate>? = null,
    val usageMetadata: GoogleGenAiUsage? = null,
    val error: GoogleGenAiErrorBody? = null,
)

@Serializable
private data class GoogleGenAiError(val message: String, val code: Int? = null, val status: String? = null)

@Serializable
private data class GoogleGenAiErrorBody(val error: GoogleGenAiError? = null)

class GoogleGenAiProtocol : AiProtocol {
    override val id: String = "google_genai"
    override val displayName: String = "Google Generative AI"

    override fun endpoint(config: AiConfig?): String {
        val model = config?.model?.takeIf { it.isNotBlank() } ?: "gemini-2.0-flash"
        return "/models/$model:generateContent"
    }

    override fun headers(config: AiConfig): Map<String, String> = mapOf(
        "x-goog-api-key" to config.apiKey,
        "Content-Type" to "application/json",
    )

    override fun buildTestRequestBody(config: AiConfig): String = json.encodeToString(
        GoogleGenAiRequest.serializer(), GoogleGenAiRequest(
            contents = listOf(GoogleGenAiContent(parts = listOf(GoogleGenAiPart("Hello")))),
        )
    )

    override fun parseTestResponse(responseBody: String): Result<String> {
        return try {
            val resp = json.decodeFromString(GoogleGenAiResponse.serializer(), responseBody)
            if (resp.error != null) {
                return Result.failure(AiException(resp.error.error?.message ?: "API error"))
            }
            val candidate = resp.candidates?.firstOrNull()
                ?: return Result.failure(AiException("No candidates returned"))
            val text = candidate.content?.parts?.firstOrNull()?.text ?: ""
            val tokens = resp.usageMetadata?.let { "${it.promptTokenCount}+${it.candidatesTokenCount}" } ?: "?"
            Result.success("OK ($tokens tokens)")
        } catch (e: Exception) {
            Result.failure(AiException("Parse error: ${e.message}"))
        }
    }

    override fun extractError(responseBody: String, statusCode: Int): String {
        return try {
            val err = json.decodeFromString(GoogleGenAiErrorBody.serializer(), responseBody)
            val e = err.error
            if (e != null) {
                listOfNotNull(e.code?.toString(), e.status, e.message).joinToString(" — ")
            } else "HTTP $statusCode"
        } catch (_: Exception) {
            "HTTP $statusCode"
        }
    }

    override fun buildChatRequest(config: AiConfig, systemPrompt: String, userMessage: String): String {
        return json.encodeToString(
            GoogleGenAiFullRequest.serializer(),
            GoogleGenAiFullRequest(
                contents = listOf(
                    GoogleGenAiContent(parts = listOf(GoogleGenAiPart(userMessage))),
                ),
                systemInstruction = GoogleGenAiContent(
                    role = "user",
                    parts = listOf(GoogleGenAiPart(systemPrompt)),
                ),
                generationConfig = GoogleGenAiFullConfig(
                    temperature = config.temperature.toDouble(),
                    maxOutputTokens = config.maxTokens.coerceIn(256, 8192),
                ),
            )
        )
    }

    override fun parseChatResponse(responseBody: String): Result<String> {
        return try {
            val resp = json.decodeFromString(GoogleGenAiResponse.serializer(), responseBody)
            if (resp.error != null) {
                return Result.failure(AiException(resp.error.error?.message ?: "API error"))
            }
            val text = resp.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return Result.failure(AiException("No text content in response"))
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(AiException("Parse error: ${e.message}"))
        }
    }
}

// ─── Protocol Registry ─────────────────────────────────────────────────

object AiProtocols {
    private val registry: Map<String, AiProtocol> = listOf(
        OpenAiChatProtocol(),
        OpenAiResponsesProtocol(),
        AnthropicMessagesProtocol(),
        GoogleGenAiProtocol(),
    ).associateBy { it.id }

    val all: List<AiProtocol> get() = registry.values.toList()

    fun findById(id: String): AiProtocol? = registry[id]

    fun defaultProtocol(): AiProtocol = registry["openai_chat"]!!
}

