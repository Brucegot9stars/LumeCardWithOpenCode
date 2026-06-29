package com.lumecard.shared.data

enum class EntityOperationType {
    KB_MERGE,
    DECK_MERGE,
    DECK_MOVE,
    CARD_MOVE,
}

data class MoveMergeRequest(
    val operationType: EntityOperationType,
    val sourceId: String,
    val targetId: String,
)

data class MoveMergeResult(
    val operationType: EntityOperationType,
    val sourceId: String,
    val targetId: String,
    val itemsMoved: Int,
    val conflictsResolved: Int,
    val sourceDeleted: Boolean,
    val conflictMessages: List<String> = emptyList(),
)

enum class ConflictType {
    DUPLICATE_DECK_NAME,
    CARD_FRONT_CONFLICT,
}

data class ConflictInfo(
    val type: ConflictType,
    val sourceName: String,
    val targetName: String,
    val resolvedName: String? = null,
)

enum class OperationConfirmAction {
    CONFIRM,
    CANCEL,
}
