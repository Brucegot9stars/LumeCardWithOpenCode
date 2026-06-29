package com.lumecard.shared.data.ai

object AiProviderRegistry {

    private val registry = mutableMapOf<String, AiProviderSpec>()

    val all: List<AiProviderSpec> get() = registry.values.toList()

    fun findById(id: String): AiProviderSpec? = registry[id]

    fun register(provider: AiProviderSpec) {
        registry[provider.id] = provider
    }

    fun detectProvider(url: String): AiProviderSpec? {
        val lower = url.lowercase()
        return all.firstOrNull { p ->
            p.id != "custom" && lower.contains(p.id) || (
                p.id == "openai" && lower.contains("api.openai.com")
            )
        }
    }

    fun defaultProvider(): AiProviderSpec? = findById("openai")

    init {
        registerProviders()
    }

    private fun registerProviders() {
        register(
            AiProviderSpec(
                id = "mimo",
                displayName = "小米 MiMo",
                defaultBaseUrl = "https://api.xiaomimimo.com/v1",
                supportedProtocols = listOf("openai_compatible", "openai_responses", "anthropic_messages"),
                defaultProtocol = "openai_compatible",
            )
        )
        register(
            AiProviderSpec(
                id = "openai",
                displayName = "OpenAI",
                defaultBaseUrl = "https://api.openai.com/v1",
                supportedProtocols = listOf("openai_chat", "openai_responses"),
                defaultProtocol = "openai_chat",
            )
        )
        register(
            AiProviderSpec(
                id = "deepseek",
                displayName = "DeepSeek",
                defaultBaseUrl = "https://api.deepseek.com",
                supportedProtocols = listOf("openai_compatible"),
                defaultProtocol = "openai_compatible",
            )
        )
        register(
            AiProviderSpec(
                id = "anthropic",
                displayName = "Anthropic",
                defaultBaseUrl = "https://api.anthropic.com/v1",
                supportedProtocols = listOf("anthropic_messages"),
                defaultProtocol = "anthropic_messages",
            )
        )
        register(
            AiProviderSpec(
                id = "gemini",
                displayName = "Gemini",
                defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
                supportedProtocols = listOf("google_genai", "openai_compatible"),
                defaultProtocol = "google_genai",
            )
        )
        register(
            AiProviderSpec(
                id = "openrouter",
                displayName = "OpenRouter",
                defaultBaseUrl = "https://openrouter.ai/api/v1",
                supportedProtocols = listOf("openai_compatible"),
                defaultProtocol = "openai_compatible",
            )
        )
        register(
            AiProviderSpec(
                id = "azure_openai",
                displayName = "Azure OpenAI",
                defaultBaseUrl = "https://{resource}.openai.azure.com",
                supportedProtocols = listOf("openai_compatible"),
                defaultProtocol = "openai_compatible",
            )
        )
        register(
            AiProviderSpec(
                id = "custom",
                displayName = "Custom",
                defaultBaseUrl = "",
                supportedProtocols = listOf("openai_compatible", "openai_responses", "anthropic_messages", "google_genai"),
                defaultProtocol = "openai_compatible",
            )
        )
    }
}
