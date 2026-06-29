package com.lumecard.shared.data.ai

enum class AiCapability(val displayName: String) {
    TEXT("Text Generation"),
    VISION("Vision"),
    STREAMING("Streaming"),
    TOOL_CALL("Tool Calling"),
    JSON_OUTPUT("JSON Output"),
}
