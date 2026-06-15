package com.lumecard.shared.data

import kotlinx.serialization.Serializable

@Serializable
data class WebDavConfig(
    val id: String,
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val isDefault: Boolean = false,
    val lastSyncAt: String? = null
)
