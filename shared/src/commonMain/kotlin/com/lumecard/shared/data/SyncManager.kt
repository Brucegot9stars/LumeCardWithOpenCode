package com.lumecard.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.readBytes
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class SyncManager(
    private val client: HttpClient
) {
    companion object {
        private const val BACKUP_DIR = "LumeCard"
        private const val DATA_FILENAME = "data.json"
        private const val DATA_PATH = "$BACKUP_DIR/$DATA_FILENAME"
        private const val CONFIG_FILENAME = "config.json"
        private const val CONFIG_PATH = "$BACKUP_DIR/$CONFIG_FILENAME"
        private const val LEGACY_PATH = "lumecard_backup.json"
        private const val MEDIA_DIR = "$BACKUP_DIR/media"
        private const val MANIFEST_PATH = "$BACKUP_DIR/media_manifest.json"

        private const val HISTORY_DIR = "$BACKUP_DIR/history"
        private const val HISTORY_INDEX_FILENAME = "history_index.json"
        private const val HISTORY_INDEX_PATH = "$BACKUP_DIR/$HISTORY_INDEX_FILENAME"
        private const val MAX_HISTORY = 15
    }

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun ensureDir(baseUrl: String, username: String, password: String, dir: String) {
        val dirUrl = baseUrl.trimEnd('/') + "/" + dir.trim('/') + "/"
        val r = client.request(dirUrl) {
            method = HttpMethod("MKCOL")
            basicAuth(username, password)
        }
        val s = r.status.value
        // HTTP 405 = Method Not Allowed = directory already exists → success
        if (s != 405 && (s < 200 || s > 299)) {
            throw SyncException("MKCOL $dir failed: $s")
        }
    }

    suspend fun uploadData(config: WebDavConfig, json: String): Result<Unit> {
        return try {
            ensureDir(config.url, config.username, config.password, BACKUP_DIR)
            val url = config.url.trimEnd('/') + "/" + DATA_PATH
            val response = client.put(url) {
                basicAuth(config.username, config.password)
                contentType(ContentType.Application.Json)
                setBody(json)
            }
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(SyncException("Upload data failed: ${response.status}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadData(config: WebDavConfig): Result<String> {
        return try {
            val url = config.url.trimEnd('/') + "/" + DATA_PATH
            val response = client.get(url) {
                basicAuth(config.username, config.password)
            }
            if (response.status == HttpStatusCode.OK) Result.success(response.bodyAsText())
            else Result.failure(SyncException("No data found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadConfig(config: WebDavConfig, json: String): Result<Unit> {
        return try {
            ensureDir(config.url, config.username, config.password, BACKUP_DIR)
            val url = config.url.trimEnd('/') + "/" + CONFIG_PATH
            val response = client.put(url) {
                basicAuth(config.username, config.password)
                contentType(ContentType.Application.Json)
                setBody(json)
            }
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(SyncException("Upload config failed: ${response.status}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadConfig(config: WebDavConfig): Result<String> {
        return try {
            val url = config.url.trimEnd('/') + "/" + CONFIG_PATH
            val response = client.get(url) {
                basicAuth(config.username, config.password)
            }
            if (response.status == HttpStatusCode.OK) Result.success(response.bodyAsText())
            else Result.failure(SyncException("No config found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadLegacy(config: WebDavConfig): Result<String> {
        return try {
            val url = config.url.trimEnd('/') + "/" + LEGACY_PATH
            val response = client.get(url) {
                basicAuth(config.username, config.password)
            }
            if (response.status == HttpStatusCode.OK) Result.success(response.bodyAsText())
            else Result.failure(SyncException("No legacy backup found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadManifest(config: WebDavConfig, manifestJson: String): Result<Unit> {
        return try {
            ensureDir(config.url, config.username, config.password, BACKUP_DIR)
            val url = config.url.trimEnd('/') + "/" + MANIFEST_PATH
            val response = client.put(url) {
                basicAuth(config.username, config.password)
                contentType(ContentType.Application.Json)
                setBody(manifestJson)
            }
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(SyncException("Upload manifest failed: ${response.status}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadManifest(config: WebDavConfig): Result<String> {
        return try {
            val url = config.url.trimEnd('/') + "/" + MANIFEST_PATH
            val response = client.get(url) {
                basicAuth(config.username, config.password)
            }
            if (response.status == HttpStatusCode.OK) Result.success(response.bodyAsText())
            else Result.failure(SyncException("No manifest found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadMedia(config: WebDavConfig, relativePath: String, data: ByteArray): Result<Unit> {
        return try {
            val dir = MEDIA_DIR + "/" + relativePath.substringBeforeLast("/")
            ensureDir(config.url, config.username, config.password, dir)
            val url = config.url.trimEnd('/') + "/" + MEDIA_DIR + "/" + relativePath
            val response = client.put(url) {
                basicAuth(config.username, config.password)
                contentType(ContentType.Application.OctetStream)
                setBody(data)
            }
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(SyncException("Upload media failed: ${response.status}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadMedia(config: WebDavConfig, relativePath: String): Result<ByteArray> {
        return try {
            val url = config.url.trimEnd('/') + "/" + MEDIA_DIR + "/" + relativePath
            val response = client.get(url) {
                basicAuth(config.username, config.password)
            }
            if (response.status == HttpStatusCode.OK) {
                val channel = response.bodyAsChannel()
                Result.success(channel.readRemaining().readBytes())
            } else {
                Result.failure(SyncException("Media not found: $relativePath"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upload(config: WebDavConfig, json: String): Result<Unit> {
        return uploadData(config, json)
    }

    suspend fun download(config: WebDavConfig): Result<String> {
        return downloadData(config)
    }

    /** Download the current remote data.json, archive it to history/ with timestamp + deviceId. */
    suspend fun archiveCurrentSnapshot(config: WebDavConfig): Result<SyncHistoryEntry?> {
        return try {
            val currentResult = downloadData(config)
            if (currentResult.isFailure) return Result.success(null)

            val currentJson = currentResult.getOrThrow()
            val deviceId = try {
                json.decodeFromString(DataExport.serializer(), currentJson).deviceId ?: "unknown"
            } catch (_: Exception) { "unknown" }

            val timestamp = Clock.System.now().toString().replace("T", "_").replace(":", "-").substringBefore("Z")
            val safeDeviceId = deviceId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(16)
            val filename = "${HISTORY_DIR}/${timestamp}_${safeDeviceId}.json"

            ensureDir(config.url, config.username, config.password, HISTORY_DIR)
            val url = config.url.trimEnd('/') + "/" + filename
            val response = client.put(url) {
                basicAuth(config.username, config.password)
                contentType(ContentType.Application.Json)
                setBody(currentJson)
            }
            if (response.status.isSuccess()) {
                val entry = SyncHistoryEntry(
                    timestamp = Clock.System.now().toString(),
                    deviceId = deviceId,
                    filename = filename
                )
                updateHistoryIndex(config, entry)
                Result.success(entry)
            } else {
                Result.failure(SyncException("Archive failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Append an entry to the remote history_index.json, keeping at most MAX_HISTORY entries. */
    private suspend fun updateHistoryIndex(config: WebDavConfig, newEntry: SyncHistoryEntry): Result<Unit> {
        return try {
            val existingResult = downloadHistoryIndex(config)
            val existing = existingResult.getOrNull() ?: SyncHistoryIndex()
            val allEntries = existing.entries + newEntry
            val pruned = if (allEntries.size > MAX_HISTORY) {
                val toRemove = allEntries.take(allEntries.size - MAX_HISTORY)
                for (old in toRemove) {
                    try {
                        val delUrl = config.url.trimEnd('/') + "/" + old.filename
                        client.delete(delUrl) { basicAuth(config.username, config.password) }
                    } catch (_: Exception) { }
                }
                allEntries.takeLast(MAX_HISTORY)
            } else {
                allEntries
            }
            val updated = SyncHistoryIndex(pruned)
            val indexBody = json.encodeToString(updated)
            val url = config.url.trimEnd('/') + "/" + HISTORY_INDEX_PATH
            val response = client.put(url) {
                basicAuth(config.username, config.password)
                contentType(ContentType.Application.Json)
                setBody(indexBody)
            }
            if (response.status.isSuccess()) Result.success(Unit)
            else Result.failure(SyncException("Update history index failed: ${response.status}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Download the history_index.json from remote. */
    suspend fun downloadHistoryIndex(config: WebDavConfig): Result<SyncHistoryIndex> {
        return try {
            val url = config.url.trimEnd('/') + "/" + HISTORY_INDEX_PATH
            val response = client.get(url) {
                basicAuth(config.username, config.password)
            }
            if (response.status == HttpStatusCode.OK) {
                val index = json.decodeFromString<SyncHistoryIndex>(response.bodyAsText())
                Result.success(index)
            } else {
                Result.success(SyncHistoryIndex())
            }
        } catch (_: Exception) {
            Result.success(SyncHistoryIndex())
        }
    }

    /** Download the content of a specific history snapshot by filename. */
    suspend fun downloadSnapshot(config: WebDavConfig, filename: String): Result<String> {
        return try {
            val url = config.url.trimEnd('/') + "/" + filename
            val response = client.get(url) {
                basicAuth(config.username, config.password)
            }
            if (response.status == HttpStatusCode.OK) Result.success(response.bodyAsText())
            else Result.failure(SyncException("Snapshot not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Full bidirectional data sync with version-based conflict resolution.
     */
    suspend fun performSync(
        config: WebDavConfig,
        localKnowledgeBases: List<com.lumecard.shared.model.KnowledgeBase>,
        localDecks: List<com.lumecard.shared.model.Deck>,
        localCards: List<com.lumecard.shared.model.Card>,
        localReviewLogs: List<com.lumecard.shared.model.ReviewLog>,
        localLearningPlans: List<com.lumecard.shared.model.LearningPlan>,
        exportManager: ExportManager,
    ): SyncResult {
        val remoteResult = downloadData(config)

        if (remoteResult.isFailure) {
            if (localDecks.isEmpty() && localCards.isEmpty()) return SyncResult.Skipped("Nothing to sync")
            val json = exportManager.exportData(localKnowledgeBases, localDecks, localCards, localReviewLogs, localLearningPlans)
            val up = uploadData(config, json)
            return if (up.isSuccess) SyncResult.Success(true, false, localDecks.size)
            else SyncResult.Error(up.exceptionOrNull()?.message ?: "Upload failed")
        }

        val remoteJson = remoteResult.getOrThrow()
        val remoteExport = exportManager.importData(remoteJson)

        if (remoteExport == null) {
            archiveCurrentSnapshot(config)
            val json = exportManager.exportData(localKnowledgeBases, localDecks, localCards, localReviewLogs, localLearningPlans)
            uploadData(config, json)
            return SyncResult.Success(true, false, localDecks.size)
        }

        if (localDecks.isEmpty() && localCards.isEmpty()) return SyncResult.RemoteImport(remoteExport)

        fun <T> mergeByVersion(
            local: List<T>,
            remote: List<T>,
            key: (T) -> String,
            version: (T) -> Long,
        ): List<T> {
            val localMap = local.associateBy { key(it) }
            val remoteMap = remote.associateBy { key(it) }
            return (localMap.keys + remoteMap.keys).mapNotNull { id ->
                val l = localMap[id]; val r = remoteMap[id]
                when {
                    l != null && r == null -> l
                    l == null && r != null -> r
                    l != null && r != null -> if (version(r) > version(l)) r else l
                    else -> null
                }
            }
        }

        val remoteKbs = remoteExport.knowledgeBases.map { it.toKnowledgeBase() }
        val remoteDecks = remoteExport.decks.map { it.toDeck() }
        val remoteCards = remoteExport.cards.map { it.toCard() }
        val remoteLogs = remoteExport.reviewLogs.map { it.toReviewLog() }
        val remotePlans = remoteExport.learningPlans.map { it.toLearningPlan() }

        val mergedKbs = mergeByVersion(localKnowledgeBases, remoteKbs, { it.id }, { it.version })
        val mergedDecks = mergeByVersion(localDecks, remoteDecks, { it.id }, { it.version })
        val mergedCards = mergeByVersion(localCards, remoteCards, { it.id }, { it.version })
        val mergedLogs = mergeByVersion(localReviewLogs, remoteLogs, { it.id }, { it.version })
        val mergedPlans = mergeByVersion(localLearningPlans, remotePlans, { it.id }, { it.version })

        val activeKbs = mergedKbs.filter { it.deletedAt == null }
        val activeDecks = mergedDecks.filter { it.deletedAt == null }
        val activeCards = mergedCards.filter { it.deletedAt == null }
        val activeLogs = mergedLogs.filter { it.deletedAt == null }
        val activePlans = mergedPlans.filter { it.deletedAt == null }

        archiveCurrentSnapshot(config)
        val mergedJson = exportManager.exportData(
            knowledgeBases = activeKbs, decks = activeDecks, cards = activeCards,
            reviewLogs = activeLogs, learningPlans = activePlans
        )
        uploadData(config, mergedJson)

        val imported = mergedDecks.size > localDecks.size || mergedCards.size > localCards.size
        return SyncResult.Success(true, imported, activeDecks.size)
    }
}

class SyncException(message: String) : Exception(message)

fun ExportKnowledgeBase.toKnowledgeBase() = com.lumecard.shared.model.KnowledgeBase(
    id = id, name = name, description = description,
    createdAt = try { kotlinx.datetime.Instant.parse(createdAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    updatedAt = try { kotlinx.datetime.Instant.parse(updatedAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    version = version,
    deletedAt = deletedAt?.let { try { kotlinx.datetime.Instant.parse(it) } catch (_: Exception) { null } }
)

fun ExportDeck.toDeck() = com.lumecard.shared.model.Deck(
    id = id, knowledgeBaseId = knowledgeBaseId, name = name, description = description,
    color = color, icon = icon, parentId = parentId,
    createdAt = try { kotlinx.datetime.Instant.parse(createdAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    updatedAt = try { kotlinx.datetime.Instant.parse(updatedAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    version = version,
    deletedAt = deletedAt?.let { try { kotlinx.datetime.Instant.parse(it) } catch (_: Exception) { null } }
)

fun ExportCard.toCard() = com.lumecard.shared.model.Card(
    id = id, deckId = deckId,
    type = try { com.lumecard.shared.model.CardType.valueOf(type) } catch (_: Exception) { com.lumecard.shared.model.CardType.BASIC },
    front = front, back = back, tags = tags,
    createdAt = try { kotlinx.datetime.Instant.parse(createdAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    updatedAt = try { kotlinx.datetime.Instant.parse(updatedAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    lastReviewedAt = lastReviewedAt?.let { try { kotlinx.datetime.Instant.parse(it) } catch (_: Exception) { null } },
    nextReviewAt = nextReviewAt?.let { try { kotlinx.datetime.Instant.parse(it) } catch (_: Exception) { null } },
    version = version,
    deletedAt = deletedAt?.let { try { kotlinx.datetime.Instant.parse(it) } catch (_: Exception) { null } }
)

fun ExportReviewLog.toReviewLog() = com.lumecard.shared.model.ReviewLog(
    id = id, cardId = cardId, rating = rating, reviewTime = reviewTime,
    interval = interval, easeFactor = easeFactor, repetitions = repetitions,
    lapseCount = lapseCount,
    reviewedAt = try { kotlinx.datetime.Instant.parse(reviewedAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    version = version,
    deletedAt = deletedAt?.let { try { kotlinx.datetime.Instant.parse(it) } catch (_: Exception) { null } }
)

fun ExportLearningPlan.toLearningPlan() = com.lumecard.shared.model.LearningPlan(
    id = id, name = name, description = description,
    status = try { com.lumecard.shared.model.PlanStatus.valueOf(status) } catch (_: Exception) { com.lumecard.shared.model.PlanStatus.NOT_STARTED },
    isDefault = isDefault, knowledgeBaseIds = knowledgeBaseIds, deckIds = deckIds,
    cardIds = cardIds, totalCards = totalCards, completedCards = completedCards,
    createdAt = try { kotlinx.datetime.Instant.parse(createdAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    updatedAt = try { kotlinx.datetime.Instant.parse(updatedAt) } catch (_: Exception) { kotlinx.datetime.Clock.System.now() },
    version = version,
    deletedAt = deletedAt?.let { try { kotlinx.datetime.Instant.parse(it) } catch (_: Exception) { null } }
)

sealed class SyncResult {
    data class Success(val backedUp: Boolean, val imported: Boolean, val decksSynced: Int) : SyncResult()
    data class RemoteImport(val export: DataExport) : SyncResult()
    data class Skipped(val reason: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
}
