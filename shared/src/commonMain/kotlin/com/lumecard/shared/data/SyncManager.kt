package com.lumecard.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class SyncManager(
    private val client: HttpClient
) {
    companion object {
        private const val BACKUP_DIR = "LumeCard"
        private const val BACKUP_FILENAME = "lumecard_backup.json"
        private const val BACKUP_PATH = "$BACKUP_DIR/$BACKUP_FILENAME"
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
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(SyncException("Upload failed: ${response.status}"))
            }
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
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.bodyAsText())
            } else if (response.status == HttpStatusCode.NotFound) {
                Result.failure(SyncException("No remote backup found"))
            } else {
                Result.failure(SyncException("Download failed: ${response.status}"))
            }
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
}

class SyncException(message: String) : Exception(message)

    /**
     * Perform a full bidirectional sync with WebDAV.
     *
     * Strategy:
     * 1. Download remote backup FIRST
     * 2. If remote not found → upload local as initial backup
     * 3. If local is empty AND remote exists → import all remote (new device restore)
     * 4. If both have data → union merge (add remote items not in local, keep local items)
     * 5. Upload merged result, return merged data for caller to apply locally
     */
    suspend fun performSync(
        config: WebDavConfig,
        localDecks: List<com.lumecard.shared.model.Deck>,
        localCards: List<com.lumecard.shared.model.Card>,
        exportManager: ExportManager,
    ): SyncResult {
        // Step 1: Download remote first
        val remoteResult = download(config)

        if (remoteResult.isFailure) {
            // No remote backup → upload local as initial backup
            if (localDecks.isEmpty() && localCards.isEmpty()) {
                return SyncResult.Skipped("Nothing to sync")
            }
            val json = exportManager.exportToJson(localDecks, localCards)
            val uploadResult = upload(config, json)
            return if (uploadResult.isSuccess) {
                SyncResult.Success(backedUp = true, imported = false, decksSynced = localDecks.size)
            } else {
                SyncResult.Error(uploadResult.exceptionOrNull()?.message ?: "Upload failed")
            }
        }

        // Parse remote
        val remoteJson = remoteResult.getOrThrow()
        val remoteExport = exportManager.importFromJson(remoteJson)
        if (remoteExport == null) {
            // Remote corrupt → overwrite with local
            if (localDecks.isNotEmpty()) {
                val json = exportManager.exportToJson(localDecks, localCards)
                upload(config, json)
            }
            return SyncResult.Success(backedUp = true, imported = false, decksSynced = localDecks.size)
        }

        // Step 2: If local is empty → new device, import all remote
        if (localDecks.isEmpty() && localCards.isEmpty()) {
            return SyncResult.RemoteImport(remoteExport)
        }

        // Step 3: Both have data → union merge
        val localDeckIds = localDecks.map { it.id }.toSet()
        val localCardIds = localCards.map { it.id }.toSet()

        val mergedDecks = localDecks.toMutableList()
        val mergedCards = localCards.toMutableList()

        // Add remote decks not in local
        for (ed in remoteExport.decks) {
            if (ed.id !in localDeckIds) {
                val now = kotlinx.datetime.Clock.System.now()
                mergedDecks.add(com.lumecard.shared.model.Deck(
                    id = ed.id,
                    knowledgeBaseId = ed.knowledgeBaseId,
                    name = ed.name,
                    description = ed.description,
                    color = ed.color,
                    icon = ed.icon,
                    parentId = ed.parentId,
                    createdAt = try { kotlinx.datetime.Instant.parse(ed.createdAt) } catch (_: Exception) { now },
                    updatedAt = try { kotlinx.datetime.Instant.parse(ed.updatedAt) } catch (_: Exception) { now },
                ))
            }
        }

        // Add remote cards not in local
        for (ec in remoteExport.cards) {
            if (ec.id !in localCardIds) {
                val now = kotlinx.datetime.Clock.System.now()
                mergedCards.add(com.lumecard.shared.model.Card(
                    id = ec.id,
                    deckId = ec.deckId,
                    type = try { com.lumecard.shared.model.CardType.valueOf(ec.type) } catch (_: Exception) { com.lumecard.shared.model.CardType.BASIC },
                    front = ec.front,
                    back = ec.back,
                    tags = ec.tags,
                    createdAt = try { kotlinx.datetime.Instant.parse(ec.createdAt) } catch (_: Exception) { now },
                    updatedAt = try { kotlinx.datetime.Instant.parse(ec.updatedAt) } catch (_: Exception) { now },
                ))
            }
        }

        // Upload merged result
        val mergedJson = exportManager.exportToJson(mergedDecks, mergedCards)
        upload(config, mergedJson)

        return SyncResult.Success(
            backedUp = true,
            imported = (mergedDecks.size - localDecks.size) > 0 || (mergedCards.size - localCards.size) > 0,
            decksSynced = mergedDecks.size,
        )
    }
}

sealed class SyncResult {
    /** Remote backup created/updated with local data. No merge needed. */
    data class Success(val backedUp: Boolean, val imported: Boolean, val decksSynced: Int) : SyncResult()

    /** Local was empty, all remote data returned for import. Caller must apply locally. */
    data class RemoteImport(val export: LumeCardExport) : SyncResult()

    /** Nothing to sync (both local and remote are empty). */
    data class Skipped(val reason: String) : SyncResult()

    data class Error(val message: String) : SyncResult()
}
