package com.lumecard.shared.data.ai

data class AiProviderSpec(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val supportedProtocols: List<String>,
    val defaultProtocol: String,
    val models: List<AiModelSpec> = emptyList(),
)
