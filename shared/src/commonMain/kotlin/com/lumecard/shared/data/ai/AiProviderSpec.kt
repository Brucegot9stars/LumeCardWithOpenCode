package com.lumecard.shared.data.ai

data class AiProviderSpec(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val supportedProtocols: List<String>,
    val defaultProtocol: String,
    val defaultBaseUrls: List<String> = emptyList(),
    val models: List<AiModelSpec> = emptyList(),
)
