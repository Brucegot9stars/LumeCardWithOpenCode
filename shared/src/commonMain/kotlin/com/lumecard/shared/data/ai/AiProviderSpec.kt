package com.lumecard.shared.data.ai

data class AiProviderSpec(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val supportedProtocols: List<String>,
    val defaultProtocol: String,
    val models: List<AiModelSpec>,
) {
    fun modelById(id: String): AiModelSpec? = models.find { it.id == id }

    fun modelsWithCapability(capability: AiCapability): List<AiModelSpec> =
        models.filter { capability in it.capabilities }

    val defaultModelSpec: AiModelSpec?
        get() = modelById(defaultModel)

    val textModels: List<AiModelSpec>
        get() = modelsWithCapability(AiCapability.TEXT)

    val visionModels: List<AiModelSpec>
        get() = modelsWithCapability(AiCapability.VISION)
}
