package com.lumecard.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Clock

class SyncManager(
    private val client: HttpClient
) {
    companion object {
        private const val BACKUP_DIR = "LumeCard"
        private const val BACKUP_FILENAME = "lumecard_backup.json"
        private const val BACKUP_PATH = "$BACKUP_DIR/$BACKUP_FILENAME"
        private const val SYNC_LOG_PATH = "$BACKUP_DIR/sync_log.json"
        private const val LEGACY_PATH = BACKUP_FILENAME
    }

    private suspend fun ensureDir(baseUrl: String, username: String, password: String, dir: String) {
        val dirUrl = baseUrl.trimEnd('/') + "/" + dir.trim('/') + "/"
        try {
            val r = client.request(dirUrl) {
                method = HttpMethod("MKCOL")
                basicAuth(username, password)
            }
            val s = r.status.value
            val ok = s in 200..299 || s == 405
            if (!ok) throw SyncException("MKCOL $dir failed: $s")
        } catch (e: SyncException) {
            throw e
        } catch (_: Exception) { }
    }

    suspend fun upload(
        baseUrl: String,
        username: String,
        password: String,
        json: String
    ): Result<Unit> {
        return try {
            ensureDir(baseUrl, username, password, BACKUP_DIR)
            val url = baseUrl.trimEnd('/') + "/" + BACKUP_PATH
            val response = client.put(url) {
                basicAuth(username, password)
                contentType(ContentType.Application.Json)
                setBody(json)
            }
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(SyncException("Upload failed: ${response.status}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun download(
        baseUrl: String,
        username: String,
        password: String
    ): Result<String> {
        return try {
            val result = downloadPath(baseUrl, username, password, BACKUP_PATH)
            if (result.isSuccess) return result
            val newResult = downloadPath(baseUrl, username, password, LEGACY_PATH)
            if (newResult.isSuccess) return newResult
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun downloadPath(
        baseUrl: String,
        username: String,
        password: String,
        path: String
    ): Result<String> {
        return try {
            val url = baseUrl.trimEnd('/') + "/" + path
            val response = client.get(url) {
                basicAuth(username, password)
            }
            if (response.status == HttpStatusCode.OK) Result.success(response.bodyAsText())
            else if (response.status == HttpStatusCode.NotFound) Result.failure(SyncException("No remote backup found"))
            else Result.failure(SyncException("Download failed: ${response.status}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadSyncLog(
        baseUrl: String,
        username: String,
        password: String,
        json: String
    ): Result<Unit> {
        return try {
            ensureDir(baseUrl, username, password, BACKUP_DIR)
            val url = baseUrl.trimEnd('/') + "/" + SYNC_LOG_PATH
            val response = client.put(url) {
                basicAuth(username, password)
                contentType(ContentType.Application.Json)
                setBody(json)
            }
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(SyncException("Upload sync log failed: ${response.status}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadSyncLog(
        baseUrl: String,
        username: String,
        password: String
    ): Result<String> {
        return try {
            val url = baseUrl.trimEnd('/') + "/" + SYNC_LOG_PATH
            val response = client.get(url) {
                basicAuth(username, password)
            }
            if (response.status == HttpStatusCode.OK) Result.success(response.bodyAsText())
            else Result.failure(SyncException("No sync log found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upload(config: WebDavConfig, json: String): Result<Unit> {
        return upload(config.url, config.username, config.password, json)
    }

    suspend fun download(config: WebDavConfig): Result<String> {
        return download(config.url, config.username, config.password)
    }

    /**
     * Full bidirectional sync with version-based conflict resolution.
     *
     * Strategy:
     * 1. Download remote export
     * 2. For each entity (KB, Deck, Card, ReviewLog):
     *    - If only local exists → upload local
     *    - If only remote exists → import remote
     *    - If both exist → compare version, keep higher version
     * 3. Handle deletedAt: if one side has deletedAt and the other doesn't, deleted wins
     * 4. Upload merged result
     */
    suspend fun performSync(
        config: WebDavConfig,
        localKnowledgeBases: List<com.lumecard.shared.model.KnowledgeBase>,
        localDecks: List<com.lumecard.shared.model.Deck>,
        localCards: List<com.lumecard.shared.model.Card>,
        localReviewLogs: List<com.lumecard.shared.model.ReviewLog>,
        localSettings: Map<String, String>,
        exportManager: ExportManager,
    ): SyncResult {
        val remoteResult = download(config)

        // Remote doesn't exist → upload local (first sync)
        if (remoteResult.isFailure) {
            if (localDecks.isEmpty() && localCards.isEmpty()) return SyncResult.Skipped("Nothing to sync")
            val json = exportManager.exportToJson(
                knowledgeBases = localKnowledgeBases,
                decks = localDecks,
                cards = localCards,
                reviewLogs = localReviewLogs,
                settings = localSettings
            )
            val up = upload(config, json)
            return if (up.isSuccess) SyncResult.Success(true, false, localDecks.size)
            else SyncResult.Error(up.exceptionOrNull()?.message ?: "Upload failed")
        }

        val remoteJson = remoteResult.getOrThrow()
        val remoteExport = exportManager.importFromJson(remoteJson)

        // Remote exists but parse failed → overwrite with local
        if (remoteExport == null) {
            val json = exportManager.exportToJson(
                knowledgeBases = localKnowledgeBases,
                decks = localDecks,
                cards = localCards,
                reviewLogs = localReviewLogs,
                settings = localSettings
            )
            upload(config, json)
            return SyncResult.Success(true, false, localDecks.size)
        }

        // Local empty → import all remote (new device)
        if (localDecks.isEmpty() && localCards.isEmpty()) {
            return SyncResult.RemoteImport(remoteExport)
        }

        val now = Clock.System.now()

        // Merge KnowledgeBases
        val localKbMap = localKnowledgeBases.associateBy { it.id }
        val remoteKbMap = remoteExport.knowledgeBases.associateBy { it.id }
        val mergedKbs = mutableListOf<com.lumecard.shared.model.KnowledgeBase>()
        val allKbIds = localKbMap.keys + remoteKbMap.keys
        for (id in allKbIds) {
            val local = localKbMap[id]
            val remote = remoteKbMap[id]
            when {
                local != null && remote == null -> mergedKbs.add(local)
                local == null && remote != null -> mergedKbs.add(remote.toKnowledgeBase())
                local != null && remote != null -> {
                    val remoteVer = remote.version
                    val localVer = local.version
                    if (remoteVer > localVer) mergedKbs.add(remote.toKnowledgeBase())
                    else mergedKbs.add(local)
                }
            }
        }

        // Merge Decks
        val localDeckMap = localDecks.associateBy { it.id }
        val remoteDeckMap = remoteExport.decks.associateBy { it.id }
        val mergedDecks = mutableListOf<com.lumecard.shared.model.Deck>()
        val allDeckIds = localDeckMap.keys + remoteDeckMap.keys
        for (id in allDeckIds) {
            val local = localDeckMap[id]
            val remote = remoteDeckMap[id]
            when {
                local != null && remote == null -> mergedDecks.add(local)
                local == null && remote != null -> mergedDecks.add(remote.toDeck())
                local != null && remote != null -> {
                    val remoteVer = remote.version
                    val localVer = local.version
                    val localDeleted = local.deletedAt != null
                    val remoteDeleted = remote.deletedAt != null
                    when {
                        localDeleted && !remoteDeleted && remoteVer > localVer -> mergedDecks.add(remote.toDeck())
                        !localDeleted && remoteDeleted && localVer >= remoteVer -> mergedDecks.add(local)
                        remoteVer > localVer -> mergedDecks.add(remote.toDeck())
                        else -> mergedDecks.add(local)
                    }
                }
            }
        }

        // Merge Cards
        val localCardMap = localCards.associateBy { it.id }
        val remoteCardMap = remoteExport.cards.associateBy { it.id }
        val mergedCards = mutableListOf<com.lumecard.shared.model.Card>()
        val allCardIds = localCardMap.keys + remoteCardMap.keys
        for (id in allCardIds) {
            val local = localCardMap[id]
            val remote = remoteCardMap[id]
            when {
                local != null && remote == null -> mergedCards.add(local)
                local == null && remote != null -> mergedCards.add(remote.toCard())
                local != null && remote != null -> {
                    val remoteVer = remote.version
                    val localVer = local.version
                    val localDeleted = local.deletedAt != null
                    val remoteDeleted = remote.deletedAt != null
                    when {
                        localDeleted && !remoteDeleted && remoteVer > localVer -> mergedCards.add(remote.toCard())
                        !localDeleted && remoteDeleted && localVer >= remoteVer -> mergedCards.add(local)
                        remoteVer > localVer -> mergedCards.add(remote.toCard())
                        else -> mergedCards.add(local)
                    }
                }
            }
        }

        // Merge ReviewLogs (keep all, dedup by id, higher version wins)
        val localLogMap = localReviewLogs.associateBy { it.id }
        val remoteLogMap = remoteExport.reviewLogs.associateBy { it.id }
        val mergedLogs = mutableListOf<com.lumecard.shared.model.ReviewLog>()
        val allLogIds = localLogMap.keys + remoteLogMap.keys
        for (id in allLogIds) {
            val local = localLogMap[id]
            val remote = remoteLogMap[id]
            when {
                local != null && remote == null -> mergedLogs.add(local)
                local == null && remote != null -> mergedLogs.add(remote.toReviewLog())
                local != null && remote != null -> {
                    if (remote.version > local.version) mergedLogs.add(remote.toReviewLog())
                    else mergedLogs.add(local)
                }
            }
        }

        // Merge settings (remote wins on conflict)
        val mergedSettings = localSettings.toMutableMap()
        mergedSettings.putAll(remoteExport.settings)

        // Filter out soft-deleted items from final export
        val activeDecks = mergedDecks.filter { it.deletedAt == null }
        val activeCards = mergedCards.filter { it.deletedAt == null }
        val activeLogs = mergedLogs.filter { it.deletedAt == null }
        val activeKbs = mergedKbs.filter { it.deletedAt == null }

        // Upload merged result
        val mergedJson = exportManager.exportToJson(
            knowledgeBases = activeKbs,
            decks = activeDecks,
            cards = activeCards,
            reviewLogs = activeLogs,
            settings = mergedSettings
        )
        upload(config, mergedJson)

        val imported = mergedDecks.size > localDecks.size || mergedCards.size > localCards.size
        return SyncResult.Success(true, imported, activeDecks.size)
    }
}

class SyncException(message: String) : Exception(message)

fun ExportKnowledgeBase.toKnowledgeBase() = com.lumecard.shared.model.KnowledgeBase(
    id = id,
    name = name,
    description = description,
    createdAt = try { kotlinx.datetime.Instant.parse(createdAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    updatedAt = try { kotlinx.datetime.Instant.parse(updatedAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    version = version,
    deletedAt = deletedAt?.let { try { kotlinx.datetime.Instant.parse(it) } catch (_: Exception) { null } }
)

fun ExportDeck.toDeck() = com.lumecard.shared.model.Deck(
    id = id,
    knowledgeBaseId = knowledgeBaseId,
    name = name,
    description = description,
    color = color,
    icon = icon,
    parentId = parentId,
    createdAt = try { kotlinx.datetime.Instant.parse(createdAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    updatedAt = try { kotlinx.datetime.Instant.parse(updatedAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    version = version,
    deletedAt = deletedAt?.let { try { kotlinx.datetime.Instant.parse(it) } catch (_: Exception) { null } }
)

fun ExportCard.toCard() = com.lumecard.shared.model.Card(
    id = id,
    deckId = deckId,
    type = try { com.lumecard.shared.model.CardType.valueOf(type) } catch (_: Exception) { com.lumecard.shared.model.CardType.BASIC },
    front = front,
    back = back,
    tags = tags,
    createdAt = try { kotlinx.datetime.Instant.parse(createdAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    updatedAt = try { kotlinx.datetime.Instant.parse(updatedAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    lastReviewedAt = lastReviewedAt?.let { try { kotlinx.datetime.Instant.parse(it) } catch (_: Exception) { null } },
    nextReviewAt = nextReviewAt?.let { try { kotlinx.datetime.Instant.parse(it) } catch (_: Exception) { null } },
    version = version,
    deletedAt = deletedAt?.let { try { kotlinx.datetime.Instant.parse(it) } catch (_: Exception) { null } }
)

fun ExportReviewLog.toReviewLog() = com.lumecard.shared.model.ReviewLog(
    id = id,
    cardId = cardId,
    rating = rating,
    reviewTime = reviewTime,
    interval = interval,
    easeFactor = easeFactor,
    repetitions = repetitions,
    lapseCount = lapseCount,
    reviewedAt = try { kotlinx.datetime.Instant.parse(reviewedAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    version = version,
    deletedAt = deletedAt?.let { try { kotlinx.datetime.Instant.parse(it) } catch (_: Exception) { null } }
)

sealed class SyncResult {
    data class Success(val backedUp: Boolean, val imported: Boolean, val decksSynced: Int) : SyncResult()
    data class RemoteImport(val export: LumeCardExport) : SyncResult()
    data class Skipped(val reason: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
}
