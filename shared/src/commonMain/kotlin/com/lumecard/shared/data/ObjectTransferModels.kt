package com.lumecard.shared.data

import com.lumecard.shared.AppVersion
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import kotlinx.serialization.Serializable

/**
 * Granularity of a scoped ("single object") export.
 *
 * [FULL] is not produced by the scoped exporter; it is what the importer reports for a legacy
 * full export that carries no [ObjectScope] marker.
 */
enum class ObjectType(val wire: String) {
    FULL("full"),
    KNOWLEDGE_BASE("knowledgeBase"),
    DECK("deck"),
    CARD("card");

    companion object {
        fun fromWire(value: String?): ObjectType = entries.firstOrNull { it.wire == value } ?: FULL
    }
}

/**
 * Additive metadata carried by a scoped export.
 *
 * This is the **only** difference between a scoped export and a full `share` export. Every legacy
 * reader decodes with `Json { ignoreUnknownKeys = true }`, so the extra top-level key is ignored
 * and the file remains a perfectly valid full share export. Payload id lists let the object-aware
 * importer distinguish the exported objects from the ancestor "containers" that were added purely
 * to keep foreign keys resolvable for the legacy path.
 */
@Serializable
data class ObjectScope(
    val objectType: String,
    val rootIds: List<String> = emptyList(),
    val payloadKnowledgeBaseIds: List<String> = emptyList(),
    val payloadDeckIds: List<String> = emptyList(),
    val payloadCardIds: List<String> = emptyList(),
    val objectName: String = "",
) {
    val type: ObjectType get() = ObjectType.fromWire(objectType)
}

/**
 * Wire shape of a scoped export. Field names, order and defaults deliberately mirror
 * [ShareExport] so that [ExportManager.importData] and [ExportManager.importShareData] accept it
 * without any change. `exportType` stays `"share"` (the legacy importer whitelists backup/share).
 */
@Serializable
data class ObjectExport(
    val exportType: String = "share",
    val version: String = AppVersion.EXPORT_VERSION,
    val schemaVersion: Int = AppVersion.SCHEMA_VERSION,
    val exportDate: String,
    val deviceId: String? = null,
    val knowledgeBases: List<ExportKnowledgeBase> = emptyList(),
    val decks: List<ExportDeck> = emptyList(),
    val cards: List<ExportCard> = emptyList(),
    val objectScope: ObjectScope? = null,
)

/** Machine-readable outcomes; the UI maps each one to a localized message. */
enum class ObjectTransferError {
    EMPTY_ROOT,
    UNKNOWN_OBJECT_TYPE,
    INVALID_JSON,
    UNSUPPORTED_SCHEMA,
    EMPTY_PAYLOAD,
    INVALID_ID,
    INVALID_TIMESTAMP,
    INVALID_CARD_TYPE,
    DUPLICATE_ID,
    DECK_CYCLE,
    PAYLOAD_TOO_LARGE,
    TARGET_REQUIRED,
    TARGET_NOT_FOUND,
    TARGET_DECK_MISMATCH,
    NO_KNOWLEDGE_BASE,
    READ_FAILED,
    WRITE_FAILED,
}

/**
 * Carries a [ObjectTransferError] out of the shared layer so the UI can localize it.
 *
 * Lives here (not in the app layer) because services inside `execute()` must abort a transaction
 * on a condition only they can detect — a target deleted between planning and commit.
 */
class ObjectTransferException(val error: ObjectTransferError) : Exception(error.name)

/** How a name collision (knowledge base / deck) is resolved. */
enum class ObjectConflictPolicy { OVERWRITE, KEEP_BOTH }

/** How duplicate cards inside the target deck are resolved. */
enum class ObjectDuplicatePolicy { OVERWRITE, KEEP_BOTH, SKIP }

/**
 * Fully resolved user decisions for one object import. Target ids/name pairs implement the
 * "pick a target, or create one when none exists" flow described in the feature spec.
 */
data class ObjectImportRequest(
    val conflictPolicy: ObjectConflictPolicy = ObjectConflictPolicy.KEEP_BOTH,
    val duplicatePolicy: ObjectDuplicatePolicy = ObjectDuplicatePolicy.KEEP_BOTH,
    val targetKnowledgeBaseId: String? = null,
    val newKnowledgeBaseName: String? = null,
    val targetDeckId: String? = null,
    val newDeckName: String? = null,
)

/** One renamed object, so the UI can explain *what* changed instead of only counting. */
data class ObjectRename(val original: String, val newName: String)

/**
 * An immutable, fully resolved execution plan produced by pure code and consumed by
 * [ObjectImportService] inside one database transaction. All imported ids are freshly generated,
 * so a scoped import is always a copy operation and can never collide with local primary keys.
 *
 * The id maps record `old id -> new id` for copied objects **and** `overwrittenLocalId -> newId`
 * for local rows an overwrite soft-deletes (those rows are not copied). They exist so an
 * overwrite can rewrite learning-plan membership instead of silently dropping it: plans store raw
 * id arrays with no foreign keys, so a replace must carry the user's plan forward.
 */
data class ObjectImportPlan(
    val objectType: ObjectType,
    val knowledgeBases: List<KnowledgeBase>,
    /** Ordered parents-first so `Deck.parent_id` foreign keys always resolve. */
    val decks: List<Deck>,
    val cards: List<Card>,
    /** Seeds; the service expands these into complete sub-trees from the database. */
    val deleteKnowledgeBaseIds: List<String>,
    val deleteDeckIds: List<String>,
    val deleteCardIds: List<String>,
    val skippedCards: Int,
    val renamed: List<ObjectRename>,
    val overwritten: Boolean,
    val targetKnowledgeBaseId: String?,
    val targetDeckId: String?,
    val knowledgeBaseIdMap: Map<String, String> = emptyMap(),
    val deckIdMap: Map<String, String> = emptyMap(),
    /** Only populated when a duplicate card is replaced, so plan membership follows the new row. */
    val cardIdMap: Map<String, String> = emptyMap(),
)

/** What actually landed, plus the files this import owns and may clean up. */
data class ObjectImportResult(
    val importedKnowledgeBases: Int,
    val importedDecks: Int,
    val importedCards: Int,
    val deletedKnowledgeBases: Int,
    val deletedDecks: Int,
    val deletedCards: Int,
    /**
     * Files this import wrote that no card ended up referencing (for example every card was skipped
     * as a duplicate). These are safe to delete — nothing ever pointed at them.
     *
     * Note there is no counterpart for "media the overwrite orphaned": deleted cards are *soft*
     * deleted and a tombstone keeps its `media` array, so those files are still referenced and must
     * stay on disk for sync to restore the row.
     */
    val unusedNewMediaPaths: List<String> = emptyList(),
    val scrubbedPlans: Int = 0,
)

data class ObjectExportData(
    val export: ObjectExport,
    /** Domain cards that are payload — used to package local media into the archive. */
    val payloadCards: List<Card>,
)

/** What an overwrite will actually remove, sub-trees included. */
data class ObjectDeleteCounts(
    val knowledgeBases: Int,
    val decks: Int,
    val cards: Int,
)

/**
 * Read-only snapshot of the local library used by the import planner. Using a plain value instead
 * of live repositories keeps conflict resolution pure and fully testable in `commonTest`.
 * Only live (non-deleted) rows belong here.
 */
data class ObjectImportContext(
    val knowledgeBases: List<KnowledgeBase> = emptyList(),
    val decks: List<Deck> = emptyList(),
    val cards: List<Card> = emptyList(),
)

sealed interface ObjectBuildResult {
    data class Success(val data: ObjectExportData) : ObjectBuildResult
    data class Failure(val error: ObjectTransferError) : ObjectBuildResult
}

sealed interface ObjectImportPlanResult {
    data class Success(val plan: ObjectImportPlan) : ObjectImportPlanResult
    data class Failure(val error: ObjectTransferError) : ObjectImportPlanResult
}
