package com.lumecard.shared.data.ai

data class AiModelSpec(
    val id: String,
    val name: String,
    val contextWindow: Int,
    val capabilities: Set<AiCapability>,
)
