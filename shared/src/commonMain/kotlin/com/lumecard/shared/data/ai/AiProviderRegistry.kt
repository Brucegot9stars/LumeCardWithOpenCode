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

    fun providersWithCapability(capability: AiCapability): List<AiProviderSpec> =
        all.filter { p -> p.models.any { capability in it.capabilities } }

    fun findModel(modelId: String): AiModelSpec? =
        all.firstNotNullOfOrNull { p -> p.modelById(modelId) }

    fun defaultProvider(): AiProviderSpec? = findById("openai")

    fun defaultModel(): AiModelSpec? = defaultProvider()?.defaultModelSpec

    fun fallbackModels(primaryProviderId: String): List<AiModelSpec> {
        val primary = findById(primaryProviderId) ?: return emptyList()
        return primary.textModels.filter { it.id != primary.defaultModel }
    }

    fun providerForModel(modelId: String): AiProviderSpec? =
        all.firstOrNull { p -> p.modelById(modelId) != null }

    init {
        registerProviders()
    }

    private fun registerProviders() {
        register(
            AiProviderSpec(
                id = "openai",
                displayName = "OpenAI",
                defaultBaseUrl = "https://api.openai.com/v1",
                defaultModel = "",
                supportedProtocols = listOf("openai_chat", "openai_responses"),
                defaultProtocol = "openai_chat",
                models = listOf(
                    AiModelSpec("gpt-4o", "GPT-4o", 128000, setOf(AiCapability.TEXT, AiCapability.VISION, AiCapability.STREAMING, AiCapability.TOOL_CALL, AiCapability.JSON_OUTPUT)),
                    AiModelSpec("gpt-4o-mini", "GPT-4o Mini", 128000, setOf(AiCapability.TEXT, AiCapability.VISION, AiCapability.STREAMING, AiCapability.TOOL_CALL, AiCapability.JSON_OUTPUT)),
                    AiModelSpec("gpt-4-turbo", "GPT-4 Turbo", 128000, setOf(AiCapability.TEXT, AiCapability.VISION, AiCapability.STREAMING, AiCapability.TOOL_CALL, AiCapability.JSON_OUTPUT)),
                    AiModelSpec("o1-mini", "o1 Mini", 128000, setOf(AiCapability.TEXT, AiCapability.STREAMING, AiCapability.TOOL_CALL)),
                    AiModelSpec("o3-mini", "o3 Mini", 200000, setOf(AiCapability.TEXT, AiCapability.STREAMING, AiCapability.TOOL_CALL, AiCapability.JSON_OUTPUT)),
                ),
            )
        )
        register(
            AiProviderSpec(
                id = "deepseek",
                displayName = "DeepSeek",
                defaultBaseUrl = "https://api.deepseek.com",
                defaultModel = "",
                supportedProtocols = listOf("openai_chat"),
                defaultProtocol = "openai_chat",
                models = listOf(
                    AiModelSpec("deepseek-chat", "DeepSeek Chat (V3)", 64000, setOf(AiCapability.TEXT, AiCapability.STREAMING, AiCapability.JSON_OUTPUT)),
                    AiModelSpec("deepseek-reasoner", "DeepSeek Reasoner (R1)", 64000, setOf(AiCapability.TEXT, AiCapability.STREAMING)),
                ),
            )
        )
        register(
            AiProviderSpec(
                id = "anthropic",
                displayName = "Anthropic",
                defaultBaseUrl = "https://api.anthropic.com/v1",
                defaultModel = "",
                supportedProtocols = listOf("anthropic_messages"),
                defaultProtocol = "anthropic_messages",
                models = listOf(
                    AiModelSpec("claude-3-5-haiku-latest", "Claude 3.5 Haiku", 200000, setOf(AiCapability.TEXT, AiCapability.VISION, AiCapability.STREAMING, AiCapability.TOOL_CALL)),
                    AiModelSpec("claude-3-5-sonnet-latest", "Claude 3.5 Sonnet", 200000, setOf(AiCapability.TEXT, AiCapability.VISION, AiCapability.STREAMING, AiCapability.TOOL_CALL)),
                    AiModelSpec("claude-opus-4-20250514", "Claude Opus 4", 200000, setOf(AiCapability.TEXT, AiCapability.VISION, AiCapability.STREAMING, AiCapability.TOOL_CALL)),
                ),
            )
        )
        register(
            AiProviderSpec(
                id = "gemini",
                displayName = "Gemini",
                defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
                defaultModel = "",
                supportedProtocols = listOf("openai_chat"),
                defaultProtocol = "openai_chat",
                models = listOf(
                    AiModelSpec("gemini-2.0-flash", "Gemini 2.0 Flash", 1000000, setOf(AiCapability.TEXT, AiCapability.VISION, AiCapability.STREAMING, AiCapability.TOOL_CALL, AiCapability.JSON_OUTPUT)),
                    AiModelSpec("gemini-2.0-flash-lite", "Gemini 2.0 Flash Lite", 1000000, setOf(AiCapability.TEXT, AiCapability.VISION, AiCapability.STREAMING, AiCapability.TOOL_CALL, AiCapability.JSON_OUTPUT)),
                    AiModelSpec("gemini-2.5-pro-exp-03-25", "Gemini 2.5 Pro (experimental)", 1000000, setOf(AiCapability.TEXT, AiCapability.VISION, AiCapability.STREAMING, AiCapability.TOOL_CALL, AiCapability.JSON_OUTPUT)),
                ),
            )
        )
        register(
            AiProviderSpec(
                id = "openrouter",
                displayName = "OpenRouter",
                defaultBaseUrl = "https://openrouter.ai/api/v1",
                defaultModel = "",
                supportedProtocols = listOf("openai_chat"),
                defaultProtocol = "openai_chat",
                models = listOf(
                    AiModelSpec("openai/gpt-4o-mini", "OpenAI GPT-4o Mini", 128000, setOf(AiCapability.TEXT, AiCapability.VISION, AiCapability.STREAMING, AiCapability.TOOL_CALL, AiCapability.JSON_OUTPUT)),
                    AiModelSpec("openai/gpt-4o", "OpenAI GPT-4o", 128000, setOf(AiCapability.TEXT, AiCapability.VISION, AiCapability.STREAMING, AiCapability.TOOL_CALL, AiCapability.JSON_OUTPUT)),
                    AiModelSpec("deepseek/deepseek-chat", "DeepSeek V3", 64000, setOf(AiCapability.TEXT, AiCapability.STREAMING, AiCapability.JSON_OUTPUT)),
                    AiModelSpec("anthropic/claude-3.5-haiku", "Claude 3.5 Haiku", 200000, setOf(AiCapability.TEXT, AiCapability.VISION, AiCapability.STREAMING, AiCapability.TOOL_CALL)),
                ),
            )
        )
        register(
            AiProviderSpec(
                id = "azure_openai",
                displayName = "Azure OpenAI",
                defaultBaseUrl = "https://{resource}.openai.azure.com",
                defaultModel = "",
                supportedProtocols = listOf("openai_chat"),
                defaultProtocol = "openai_chat",
                models = listOf(
                    AiModelSpec("gpt-4o-mini", "GPT-4o Mini", 128000, setOf(AiCapability.TEXT, AiCapability.VISION, AiCapability.STREAMING, AiCapability.TOOL_CALL, AiCapability.JSON_OUTPUT)),
                    AiModelSpec("gpt-4o", "GPT-4o", 128000, setOf(AiCapability.TEXT, AiCapability.VISION, AiCapability.STREAMING, AiCapability.TOOL_CALL, AiCapability.JSON_OUTPUT)),
                ),
            )
        )
        register(
            AiProviderSpec(
                id = "custom",
                displayName = "Custom",
                defaultBaseUrl = "",
                defaultModel = "",
                supportedProtocols = listOf("openai_chat", "openai_responses", "anthropic_messages"),
                defaultProtocol = "openai_chat",
                models = emptyList(),
            )
        )
    }
}
