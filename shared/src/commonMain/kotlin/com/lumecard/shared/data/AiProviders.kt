package com.lumecard.shared.data

data class AiProviderInfo(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val supportedProtocols: List<String>,
    val defaultProtocol: String,
)

object AiProviders {
    val all = listOf(
        AiProviderInfo(
            id = "openai",
            displayName = "OpenAI",
            defaultBaseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
            supportedProtocols = listOf("openai_chat", "openai_responses"),
            defaultProtocol = "openai_chat",
        ),
        AiProviderInfo(
            id = "deepseek",
            displayName = "DeepSeek",
            defaultBaseUrl = "https://api.deepseek.com",
            defaultModel = "deepseek-chat",
            supportedProtocols = listOf("openai_chat"),
            defaultProtocol = "openai_chat",
        ),
        AiProviderInfo(
            id = "anthropic",
            displayName = "Anthropic",
            defaultBaseUrl = "https://api.anthropic.com/v1",
            defaultModel = "claude-3-5-haiku-latest",
            supportedProtocols = listOf("anthropic_messages"),
            defaultProtocol = "anthropic_messages",
        ),
        AiProviderInfo(
            id = "gemini",
            displayName = "Gemini",
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
            defaultModel = "gemini-2.0-flash",
            supportedProtocols = listOf("openai_chat"),
            defaultProtocol = "openai_chat",
        ),
        AiProviderInfo(
            id = "openrouter",
            displayName = "OpenRouter",
            defaultBaseUrl = "https://openrouter.ai/api/v1",
            defaultModel = "openai/gpt-4o-mini",
            supportedProtocols = listOf("openai_chat"),
            defaultProtocol = "openai_chat",
        ),
        AiProviderInfo(
            id = "azure_openai",
            displayName = "Azure OpenAI",
            defaultBaseUrl = "https://{resource}.openai.azure.com",
            defaultModel = "gpt-4o-mini",
            supportedProtocols = listOf("openai_chat"),
            defaultProtocol = "openai_chat",
        ),
        AiProviderInfo(
            id = "custom",
            displayName = "Custom",
            defaultBaseUrl = "",
            defaultModel = "",
            supportedProtocols = listOf("openai_chat", "openai_responses", "anthropic_messages"),
            defaultProtocol = "openai_chat",
        ),
    )

    fun findById(id: String): AiProviderInfo? = all.find { it.id == id }

    fun detectProvider(url: String): AiProviderInfo? {
        val lower = url.lowercase()
        return all.firstOrNull { p ->
            p.id != "custom" && lower.contains(p.id) || (
                p.id == "openai" && lower.contains("api.openai.com")
            )
        }
    }
}
