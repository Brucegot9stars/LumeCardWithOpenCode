package com.lumecard.shared.crypto

object SensitiveKeys {
    private val knownKeys = mutableSetOf(
        "ai_provider_api_key",
        "ai_provider_secret",
        "ai_provider_token",
        "access_key",
        "refresh_token",
        "webdav_configs",
        "ai_configs",
    )

    fun isSensitive(key: String): Boolean = key in knownKeys

    fun register(key: String) {
        knownKeys.add(key)
    }
}
