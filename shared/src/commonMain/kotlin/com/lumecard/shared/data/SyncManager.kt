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
