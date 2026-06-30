package com.lumecard.shared.data

import com.lumecard.shared.data.ai.AiProviderRegistry

data class AiProviderInfo(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultBaseUrls: List<String>,
    val supportedProtocols: List<String>,
    val defaultProtocol: String,
    val supportsModelListing: Boolean = true,
)

object AiProviders {
    val all: List<AiProviderInfo> get() = AiProviderRegistry
        .all.map { it.toProviderInfo() }

    fun findById(id: String): AiProviderInfo? =
        AiProviderRegistry.findById(id)?.toProviderInfo()

    fun detectProvider(url: String): AiProviderInfo? =
        AiProviderRegistry.detectProvider(url)?.toProviderInfo()
}

private fun com.lumecard.shared.data.ai.AiProviderSpec.toProviderInfo() = AiProviderInfo(
    id = id,
    displayName = displayName,
    defaultBaseUrl = defaultBaseUrl,
    defaultBaseUrls = defaultBaseUrls,
    supportedProtocols = supportedProtocols,
    defaultProtocol = defaultProtocol,
    supportsModelListing = supportsModelListing,
)
