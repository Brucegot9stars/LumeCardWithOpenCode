package com.lumecard.shared.data

import kotlinx.serialization.Serializable

@Serializable
data class AiConfig(
    val id: String,
    val name: String,
    val provider: String,
    val protocol: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val topP: Float = 1.0f,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,
    val isDefault: Boolean = false,
    val lastSyncAt: String? = null,
    val fallbackConfigId: String? = null,
)
