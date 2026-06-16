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
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.bodyAsText())
            } else {
                Result.failure(SyncException("No sync log found"))
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

    suspend fun performSync(
        config: WebDavConfig,
        localDecks: List<com.lumecard.shared.model.Deck>,
        localCards: List<com.lumecard.shared.model.Card>,
        exportManager: ExportManager,
    ): SyncResult {
        val remoteResult = download(config)
        if (remoteResult.isFailure) {
            if (localDecks.isEmpty() && localCards.isEmpty()) return SyncResult.Skipped("Nothing to sync")
            val json = exportManager.exportToJson(
                knowledgeBases = emptyList(),
                decks = localDecks,
                cards = localCards
            )
            val up = upload(config, json)
            return if (up.isSuccess) SyncResult.Success(true, false, localDecks.size)
            else SyncResult.Error(up.exceptionOrNull()?.message ?: "Upload failed")
        }
        val remoteJson = remoteResult.getOrThrow()
        val remoteExport = exportManager.importFromJson(remoteJson)
        if (remoteExport == null) {
            if (localDecks.isNotEmpty()) {
                val json = exportManager.exportToJson(
                    knowledgeBases = emptyList(),
                    decks = localDecks,
                    cards = localCards
                )
                upload(config, json)
            }
            return SyncResult.Success(true, false, localDecks.size)
        }
        if (localDecks.isEmpty() && localCards.isEmpty()) return SyncResult.RemoteImport(remoteExport)
        val now = Clock.System.now()
        val localDeckIds = localDecks.map { it.id }.toSet()
        val localCardIds = localCards.map { it.id }.toSet()
        val mergedDecks = localDecks.toMutableList()
        val mergedCards = localCards.toMutableList()
        for (ed in remoteExport.decks) {
            if (ed.id !in localDeckIds) {
                mergedDecks.add(com.lumecard.shared.model.Deck(id=ed.id, knowledgeBaseId=ed.knowledgeBaseId,
                    name=ed.name, description=ed.description, color=ed.color, icon=ed.icon,
                    parentId=ed.parentId,
                    createdAt=try { kotlinx.datetime.Instant.parse(ed.createdAt) } catch(_: Exception) { now },
                    updatedAt=try { kotlinx.datetime.Instant.parse(ed.updatedAt) } catch(_: Exception) { now }))
            }
        }
        for (ec in remoteExport.cards) {
            if (ec.id !in localCardIds) {
                mergedCards.add(com.lumecard.shared.model.Card(id=ec.id, deckId=ec.deckId,
                    type=try { com.lumecard.shared.model.CardType.valueOf(ec.type) } catch(_: Exception) { com.lumecard.shared.model.CardType.BASIC },
                    front=ec.front, back=ec.back, tags=ec.tags,
                    createdAt=try { kotlinx.datetime.Instant.parse(ec.createdAt) } catch(_: Exception) { now },
                    updatedAt=try { kotlinx.datetime.Instant.parse(ec.updatedAt) } catch(_: Exception) { now }))
            }
        }
        val mergedJson = exportManager.exportToJson(
            knowledgeBases = emptyList(),
            decks = mergedDecks,
            cards = mergedCards
        )
        upload(config, mergedJson)
        return SyncResult.Success(true, (mergedDecks.size-localDecks.size)>0||(mergedCards.size-localCards.size)>0, mergedDecks.size)
    }
}

class SyncException(message: String) : Exception(message)

sealed class SyncResult {
    data class Success(val backedUp: Boolean, val imported: Boolean, val decksSynced: Int) : SyncResult()
    data class RemoteImport(val export: LumeCardExport) : SyncResult()
    data class Skipped(val reason: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
}
