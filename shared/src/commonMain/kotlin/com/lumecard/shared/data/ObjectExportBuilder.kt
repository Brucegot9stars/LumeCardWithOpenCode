package com.lumecard.shared.data

import com.lumecard.shared.AppVersion
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Deck
import com.lumecard.shared.model.KnowledgeBase
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a picked file actually is; see [ObjectExportBuilder.detect]. */
enum class ObjectFileKind { SCOPED, FULL, UNREADABLE }

/**
 * Builds and validates scoped export payloads.
 *
 * Everything here is pure Kotlin so it can be exercised in `commonTest` without a database or a
 * platform file system. The produced JSON is deliberately a superset of a full `share` export:
 * the legacy importer ignores `objectScope` and simply imports the three arrays.
 */
object ObjectExportBuilder {
    /** Caps keep a hand-crafted file from turning an import into an unbounded allocation. */
    const val MAX_KNOWLEDGE_BASES = 5_000
    const val MAX_DECKS = 20_000
    const val MAX_CARDS = 100_000
    /** Per-field caps; these values are persisted *and* mirrored into the FTS index. */
    const val MAX_TEXT_FIELD = 100_000
    const val MAX_TAGS = 100
    private const val MAX_NAME_FIELD = 200
    private const val MAX_DESCRIPTION_FIELD = 20_000

    // encodeDefaults matters: ObjectExport.exportType defaults to "share", and kotlinx would
    // otherwise omit the key entirely. A legacy reader decoding that JSON as DataExport would
    // then fall back to its own "backup" default, and importBackup() could mistake a scoped
    // share file for a destructive full backup. Always write the discriminator explicitly.
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(export: ObjectExport): String = json.encodeToString(ObjectExport.serializer(), export)

    fun decode(text: String): ObjectExport? = try {
        json.decodeFromString(ObjectExport.serializer(), text)
    } catch (e: Throwable) {
        // Deeply nested JSON throws StackOverflowError, which is an Error, not an Exception.
        if (e is kotlinx.coroutines.CancellationException) throw e
        null
    }

    /**
     * Apply the renames produced by `importArchiveMedia` to a decoded manifest before planning the
     * import. Without this, a card would point at the archive's original name while the bytes
     * actually landed under the collision-avoiding suffixed name. Matching tolerates the same
     * whitespace/case noise as `resolveRenamedPath`.
     */
    fun rewriteMediaPaths(export: ObjectExport, renamed: Map<String, String>): ObjectExport {
        if (renamed.isEmpty()) return export
        // Two entries may differ only by case (`Photo.PNG` vs `photo.png`). Indexing by lower
        // case would silently drop one and point the wrong card at the other file, so the
        // case-insensitive fallback only applies when exactly one candidate exists.
        val normalized = renamed.entries.groupBy({ it.key.trim().lowercase() }, { it.value })
        return export.copy(cards = export.cards.map { card ->
            card.copy(media = card.media.map { media ->
                val replacement = renamed[media.filePath]
                    ?: renamed[media.filePath.trim()]
                    ?: normalized[media.filePath.trim().lowercase()]?.singleOrNull()
                if (replacement != null) media.copy(filePath = replacement) else media
            })
        })
    }

    /**
     * Peek at the object type without fully trusting the file. A missing or unknown marker means
     * "legacy full export", which the object-aware importer intentionally refuses so that the
     * unchanged full-import button remains the single path for full backups.
     */
    fun objectTypeOf(text: String): ObjectType {
        if (detect(text) != ObjectFileKind.SCOPED) return ObjectType.FULL
        // Same guarantee as detect(): a pathological nesting depth surfaces as StackOverflowError,
        // which must be reported as "not a scoped file" instead of killing the process. Parsing
        // here without this guard made the function strictly less safe than detect() itself.
        val scope = try {
            (json.parseToJsonElement(text) as? JsonObject)?.get("objectScope") as? JsonObject
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        } ?: return ObjectType.FULL
        return ObjectType.fromWire((scope["objectType"] as? JsonPrimitive)?.content)
    }

    /**
     * Three-way classification used by the import screen.
     *
     * Collapsing "cannot parse at all" into [ObjectFileKind.FULL] would tell a user who picked a
     * screenshot or a truncated file to "use the regular full-import button", which can never
     * work. Broken files must say they are broken.
     */
    fun detect(text: String): ObjectFileKind {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Throwable) {
            // A pathological nesting depth surfaces as StackOverflowError; treat it as unreadable
            // rather than letting it kill the process.
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        } ?: return ObjectFileKind.UNREADABLE
        val scope = root["objectScope"] as? JsonObject ?: return ObjectFileKind.FULL
        val wire = (scope["objectType"] as? JsonPrimitive)?.content
        return when {
            wire == null || wire == ObjectType.FULL.wire -> ObjectFileKind.FULL
            ObjectType.fromWire(wire) == ObjectType.FULL -> ObjectFileKind.UNREADABLE
            else -> ObjectFileKind.SCOPED
        }
    }

    /**
     * Build a self-contained sub-graph for [type] and [rootIds].
     *
     * Payload = the selected objects and everything they own:
     * - knowledge base -> itself, all its decks (nested ones included), all their cards
     * - deck          -> itself, all descendant sub-decks, all their cards
     * - card          -> exactly the selected cards
     *
     * Ancestor decks/knowledge bases are appended as **containers** so that the file still
     * satisfies every foreign key when it goes through the legacy full-import path. The object
     * aware importer uses `ObjectScope.payload*` to skip those containers.
     */
    fun build(
        type: ObjectType,
        rootIds: List<String>,
        knowledgeBases: List<KnowledgeBase>,
        decks: List<Deck>,
        cards: List<Card>,
        exportDate: String,
        deviceId: String? = null,
    ): ObjectBuildResult {
        if (type == ObjectType.FULL) return ObjectBuildResult.Failure(ObjectTransferError.UNKNOWN_OBJECT_TYPE)
        val uniqueRoots = rootIds.distinct()
        if (uniqueRoots.isEmpty()) return ObjectBuildResult.Failure(ObjectTransferError.EMPTY_ROOT)

        val kbsById = knowledgeBases.filter { it.deletedAt == null }.associateBy { it.id }
        val decksById = decks.filter { it.deletedAt == null }.associateBy { it.id }
        val cardsById = cards.filter { it.deletedAt == null }.associateBy { it.id }

        // Built lazily: a CARD export never needs the descendant walk, and indexing 20k decks for
        // three cards would be the new cost centre.
        val deckTree by lazy(LazyThreadSafetyMode.NONE) { DeckTree.of(decksById.values.toList()) }

        val payloadKbIds = LinkedHashSet<String>()
        val payloadDeckIds = LinkedHashSet<String>()
        val payloadCardIds = LinkedHashSet<String>()

        when (type) {
            ObjectType.KNOWLEDGE_BASE -> {
                uniqueRoots.filterTo(payloadKbIds) { kbsById.containsKey(it) }
                if (payloadKbIds.isEmpty()) return ObjectBuildResult.Failure(ObjectTransferError.EMPTY_ROOT)
                decksById.values.filter { it.knowledgeBaseId in payloadKbIds }
                    .mapTo(payloadDeckIds) { it.id }
                cardsById.values.filter { it.deckId in payloadDeckIds }
                    .mapTo(payloadCardIds) { it.id }
            }
            ObjectType.DECK -> {
                uniqueRoots.filterTo(payloadDeckIds) { decksById.containsKey(it) }
                if (payloadDeckIds.isEmpty()) return ObjectBuildResult.Failure(ObjectTransferError.EMPTY_ROOT)
                expandDescendants(payloadDeckIds, deckTree)
                cardsById.values.filter { it.deckId in payloadDeckIds }
                    .mapTo(payloadCardIds) { it.id }
            }
            ObjectType.CARD -> {
                // A card whose deck no longer exists cannot be represented with a valid FK; drop
                // it rather than fabricate a container deck or write a broken row.
                uniqueRoots.filterTo(payloadCardIds) {
                    val card = cardsById[it] ?: return@filterTo false
                    decksById.containsKey(card.deckId)
                }
                if (payloadCardIds.isEmpty()) return ObjectBuildResult.Failure(ObjectTransferError.EMPTY_ROOT)
            }
            ObjectType.FULL -> return ObjectBuildResult.Failure(ObjectTransferError.UNKNOWN_OBJECT_TYPE)
        }

        // A root that was dropped above (a deleted knowledge base, a card whose deck vanished —
        // see the CARD branch) must not stay in `rootIds`. `validate()` requires every root to be
        // part of the payload, so a file carrying a stale root would be rejected by our own
        // importer on every device even though `build()` reported success.
        val survivingRoots = when (type) {
            ObjectType.KNOWLEDGE_BASE -> uniqueRoots.filter { it in payloadKbIds }
            ObjectType.DECK -> uniqueRoots.filter { it in payloadDeckIds }
            ObjectType.CARD -> uniqueRoots.filter { it in payloadCardIds }
            ObjectType.FULL -> emptyList()
        }
        if (survivingRoots.isEmpty()) return ObjectBuildResult.Failure(ObjectTransferError.EMPTY_ROOT)

        // Every deck referenced by a payload card must be present, plus its whole parent chain.
        val requiredDeckIds = LinkedHashSet<String>(payloadDeckIds)
        payloadCardIds.forEach { id -> cardsById[id]?.let { requiredDeckIds += it.deckId } }
        expandAncestors(requiredDeckIds, deckTree)
        val missingDeck = requiredDeckIds.firstOrNull { !decksById.containsKey(it) }
        if (missingDeck != null) return ObjectBuildResult.Failure(ObjectTransferError.INVALID_ID)

        val requiredKbIds = LinkedHashSet<String>(payloadKbIds)
        requiredDeckIds.forEach { id -> decksById[id]?.let { requiredKbIds += it.knowledgeBaseId } }
        val missingKb = requiredKbIds.firstOrNull { !kbsById.containsKey(it) }
        if (missingKb != null) return ObjectBuildResult.Failure(ObjectTransferError.INVALID_ID)

        val containerKbIds = requiredKbIds - payloadKbIds

        val allKbIds = payloadKbIds + containerKbIds
        // Parents must precede children: the legacy full-import path inserts `decks` in array
        // order with foreign keys enabled, so a child-before-parent file fails there.
        val allDeckIds = orderParentsFirst(requiredDeckIds, decksById)
        val allDeckIdSet = allDeckIds.toHashSet()
        if (allKbIds.size > MAX_KNOWLEDGE_BASES || allDeckIds.size > MAX_DECKS ||
            payloadCardIds.size > MAX_CARDS
        ) return ObjectBuildResult.Failure(ObjectTransferError.PAYLOAD_TOO_LARGE)

        val objectName = displayName(type, survivingRoots, kbsById, decksById, cardsById)
        val export = ObjectExport(
            exportDate = exportDate,
            deviceId = deviceId,
            knowledgeBases = allKbIds.mapNotNull { kbsById[it]?.toExport() },
            // A soft-deleted parent is absent from `decksById`, so `expandAncestors` cannot add it
            // and the child's own `parentId` would point outside this payload. `validate()` rejects
            // that FK on every importing device, so export the orphan as a root instead.
            decks = allDeckIds.mapNotNull { id ->
                decksById[id]?.toExport()?.let { exported ->
                    if (exported.parentId != null && exported.parentId !in allDeckIdSet) {
                        exported.copy(parentId = null)
                    } else exported
                }
            },
            cards = payloadCardIds.mapNotNull { cardsById[it]?.toExport() },
            objectScope = ObjectScope(
                objectType = type.wire,
                rootIds = survivingRoots,
                payloadKnowledgeBaseIds = payloadKbIds.toList(),
                payloadDeckIds = payloadDeckIds.toList(),
                payloadCardIds = payloadCardIds.toList(),
                objectName = objectName,
            ),
        )
        return ObjectBuildResult.Success(ObjectExportData(export, payloadCardIds.mapNotNull { cardsById[it] }))
    }

    /**
     * Validate a decoded file before any database work. Runs on the whole payload (containers
     * included) because the legacy importer will insert all three arrays with FK enforcement on.
     */
    fun validate(export: ObjectExport): ObjectTransferError? {
        val scope = export.objectScope
            ?: return ObjectTransferError.UNKNOWN_OBJECT_TYPE
        if (scope.type == ObjectType.FULL) return ObjectTransferError.UNKNOWN_OBJECT_TYPE
        if (export.schemaVersion != AppVersion.SCHEMA_VERSION) return ObjectTransferError.UNSUPPORTED_SCHEMA
        if (export.exportType != "share") return ObjectTransferError.UNSUPPORTED_SCHEMA

        if (export.knowledgeBases.size > MAX_KNOWLEDGE_BASES ||
            export.decks.size > MAX_DECKS || export.cards.size > MAX_CARDS
        ) return ObjectTransferError.PAYLOAD_TOO_LARGE

        fun idsUnique(list: List<String>): Boolean =
            list.all { it.isNotBlank() } && list.toSet().size == list.size

        val kbIds = export.knowledgeBases.map { it.id }
        val deckIds = export.decks.map { it.id }
        val cardIds = export.cards.map { it.id }
        if (!idsUnique(kbIds) || !idsUnique(deckIds) || !idsUnique(cardIds)) return ObjectTransferError.DUPLICATE_ID

        // Sets, not List.containsAll: at the documented caps (100k cards) the list form is O(n·m)
        // and turns a legitimate large file into an apparent freeze.
        val kbIdSet = kbIds.toSet()
        val deckIdSet = deckIds.toSet()
        val cardIdSet = cardIds.toSet()

        val payloadKbIds = scope.payloadKnowledgeBaseIds
        val payloadDeckIds = scope.payloadDeckIds
        val payloadCardIds = scope.payloadCardIds
        if (!idsUnique(payloadKbIds) || !idsUnique(payloadDeckIds) || !idsUnique(payloadCardIds)) {
            return ObjectTransferError.DUPLICATE_ID
        }
        if (payloadKbIds.any { it !in kbIdSet } || payloadDeckIds.any { it !in deckIdSet } ||
            payloadCardIds.any { it !in cardIdSet }
        ) return ObjectTransferError.INVALID_ID

        // `rootIds` drives the object name and nothing else, so a mismatch used to pass silently
        // and surface as "exported 5 cards, imported 1". Every root must be part of the payload.
        val rootIds = scope.rootIds
        if (rootIds.isEmpty()) return ObjectTransferError.EMPTY_ROOT
        if (rootIds.any { it !in kbIdSet && it !in deckIdSet && it !in cardIdSet }) {
            return ObjectTransferError.INVALID_ID
        }
        if (rootIds.any { it !in payloadKbIds && it !in payloadDeckIds && it !in payloadCardIds }) {
            return ObjectTransferError.INVALID_ID
        }

        // Unbounded strings are persisted (and copied into the FTS index), so a single 60 MB
        // `front` would wedge every later render of that deck.
        export.knowledgeBases.forEach {
            if (it.name.length > MAX_NAME_FIELD || (it.description?.length ?: 0) > MAX_DESCRIPTION_FIELD) {
                return ObjectTransferError.PAYLOAD_TOO_LARGE
            }
        }
        export.decks.forEach {
            if (it.name.length > MAX_NAME_FIELD || (it.description?.length ?: 0) > MAX_DESCRIPTION_FIELD) {
                return ObjectTransferError.PAYLOAD_TOO_LARGE
            }
        }
        export.cards.forEach {
            if (it.front.length > MAX_TEXT_FIELD || it.back.length > MAX_TEXT_FIELD ||
                it.title.length > MAX_NAME_FIELD || it.tags.size > MAX_TAGS ||
                it.tags.any { tag -> tag.length > MAX_NAME_FIELD }
            ) {
                return ObjectTransferError.PAYLOAD_TOO_LARGE
            }
        }

        // An empty payload is a legitimate "nothing to import" only for an empty knowledge base;
        // the other two types must carry at least one object of their own kind.
        when (scope.type) {
            ObjectType.KNOWLEDGE_BASE -> if (payloadKbIds.isEmpty()) return ObjectTransferError.EMPTY_PAYLOAD
            ObjectType.DECK -> if (payloadDeckIds.isEmpty()) return ObjectTransferError.EMPTY_PAYLOAD
            ObjectType.CARD -> if (payloadCardIds.isEmpty()) return ObjectTransferError.EMPTY_PAYLOAD
            ObjectType.FULL -> return ObjectTransferError.UNKNOWN_OBJECT_TYPE
        }

        // Timestamps, enums and foreign keys.
        fun validTimestamp(value: String) = try {
            Instant.parse(value); true
        } catch (_: Exception) {
            false
        }
        if (!validTimestamp(export.exportDate)) return ObjectTransferError.INVALID_TIMESTAMP
        export.knowledgeBases.forEach {
            if (!validTimestamp(it.createdAt) || !validTimestamp(it.updatedAt)) return ObjectTransferError.INVALID_TIMESTAMP
            it.deletedAt?.let { v -> if (!validTimestamp(v)) return ObjectTransferError.INVALID_TIMESTAMP }
            it.syncedAt?.let { v -> if (!validTimestamp(v)) return ObjectTransferError.INVALID_TIMESTAMP }
        }
        export.decks.forEach {
            if (it.knowledgeBaseId !in kbIdSet || (it.parentId != null && it.parentId !in deckIdSet)) {
                return ObjectTransferError.INVALID_ID
            }
            if (!validTimestamp(it.createdAt) || !validTimestamp(it.updatedAt)) return ObjectTransferError.INVALID_TIMESTAMP
            it.deletedAt?.let { v -> if (!validTimestamp(v)) return ObjectTransferError.INVALID_TIMESTAMP }
            it.syncedAt?.let { v -> if (!validTimestamp(v)) return ObjectTransferError.INVALID_TIMESTAMP }
        }
        export.cards.forEach {
            if (it.deckId !in deckIdSet) return ObjectTransferError.INVALID_ID
            try {
                CardType.valueOf(it.type)
            } catch (_: Exception) {
                return ObjectTransferError.INVALID_CARD_TYPE
            }
            if (!validTimestamp(it.createdAt) || !validTimestamp(it.updatedAt)) return ObjectTransferError.INVALID_TIMESTAMP
            listOfNotNull(it.lastReviewedAt, it.nextReviewAt, it.deletedAt, it.syncedAt).forEach { v ->
                if (!validTimestamp(v)) return ObjectTransferError.INVALID_TIMESTAMP
            }
            it.media.forEach { media ->
                if (!isValidMediaReference(media.filePath)) return ObjectTransferError.INVALID_ID
            }
        }

        // Iterative topological walk also rejects parent cycles.
        val children = export.decks.filter { it.parentId != null }.groupBy { it.parentId }
        val pending = ArrayDeque(export.decks.filter { it.parentId == null })
        var seen = 0
        while (pending.isNotEmpty()) {
            val deck = pending.removeFirst()
            seen++
            children[deck.id].orEmpty().forEach { pending.addLast(it) }
        }
        if (seen != export.decks.size) return ObjectTransferError.DECK_CYCLE
        return null
    }

    /**
     * A media reference may be a remote URL, a platform URI, an absolute local path, or a
     * media-root relative name — the four forms `CardImageLoader` understands. Only the relative
     * form is used for archiving and orphan deletion, so only it gets the strict rules; the other
     * forms are checked for control characters, which is what breaks rendering and file lookups.
     */
    private fun isValidMediaReference(path: String): Boolean {
        if (path.isBlank() || path.any { it.code < 32 }) return false
        val lower = path.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("file://") || lower.startsWith("content://") ||
            lower.startsWith("data:")
        ) return true
        if (isAbsolutePlatformPath(path)) return true
        return MediaPathRules.isSafeRelativePath(path)
    }

    private fun isAbsolutePlatformPath(path: String): Boolean {
        if (path.startsWith("/") || path.startsWith("\\\\")) return true
        return path.length >= 3 && path[0].isLetter() && path[1] == ':' &&
            (path[2] == '\\' || path[2] == '/')
    }

    /** Breadth-first over the selected ids, so every parent is emitted before its children. */
    private fun orderParentsFirst(ids: Set<String>, decksById: Map<String, Deck>): List<String> {
        val childrenByParent = ids.mapNotNull { id ->
            val parent = decksById[id]?.parentId ?: return@mapNotNull null
            if (parent in ids) parent to id else null
        }.groupBy({ it.first }, { it.second })
        val roots = ids.filter { id ->
            val parent = decksById[id]?.parentId
            parent == null || parent !in ids
        }
        val ordered = ArrayList<String>(ids.size)
        val seen = HashSet<String>(ids.size)
        val pending = ArrayDeque(roots)
        while (pending.isNotEmpty()) {
            val id = pending.removeFirst()
            if (!seen.add(id)) continue
            ordered += id
            childrenByParent[id].orEmpty().forEach { pending.addLast(it) }
        }
        // A cycle would leave rows out; validate() rejects those files, but never lose data here.
        if (ordered.size != ids.size) ordered += ids.filterNot { it in seen }
        return ordered
    }

    /**
     * Descendant closure of [target] in one pass.
     *
     * Both helpers used to re-sweep the entire deck list until a round added nothing, which is
     * O(n²) and dominates an export of a 20 000-deck knowledge base (the documented cap). The
     * child index turns each into a single breadth-first walk.
     */
    private fun expandDescendants(target: MutableSet<String>, tree: DeckTree) {
        tree.expandDescendants(target)
    }

    private fun expandAncestors(target: MutableSet<String>, tree: DeckTree) {
        tree.expandAncestors(target)
    }

    private fun displayName(
        type: ObjectType,
        roots: List<String>,
        kbs: Map<String, KnowledgeBase>,
        decks: Map<String, Deck>,
        cards: Map<String, Card>,
    ): String = when (type) {
        // This is a pure shared layer: no locale is available here. Return blank on a missing name
        // so the app layer can substitute its own localized type label instead of leaking English.
        ObjectType.KNOWLEDGE_BASE -> kbs[roots.first()]?.name.orEmpty()
        ObjectType.DECK -> decks[roots.first()]?.name.orEmpty()
        ObjectType.CARD -> {
            if (roots.size == 1) {
                val card = cards[roots.first()]
                card?.title?.takeIf { it.isNotBlank() }
                    ?: card?.front?.take(40)?.takeIf { it.isNotBlank() }
                    ?: ""
            } else ""
        }
        ObjectType.FULL -> ""
    }

    private fun KnowledgeBase.toExport() = ExportKnowledgeBase(
        id = id, name = name, description = description, icon = icon,
        createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
        version = version, deletedAt = deletedAt?.toString(), syncedAt = syncedAt?.toString(),
    )

    private fun Deck.toExport() = ExportDeck(
        id = id, knowledgeBaseId = knowledgeBaseId, name = name, description = description,
        color = color, icon = icon, parentId = parentId,
        createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
        version = version, deletedAt = deletedAt?.toString(), syncedAt = syncedAt?.toString(),
    )

    private fun Card.toExport() = ExportCard(
        id = id, deckId = deckId, type = type.name, front = front, back = back,
        title = title, tags = tags, media = media, metadata = metadata,
        createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
        lastReviewedAt = lastReviewedAt?.toString(), nextReviewAt = nextReviewAt?.toString(),
        version = version, deletedAt = deletedAt?.toString(), syncedAt = syncedAt?.toString(),
    )
}
