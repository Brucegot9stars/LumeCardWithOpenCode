package com.lumecard.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class SyncManager(
    private val client: HttpClient
) {
    companion object {
        private const val BACKUP_FILENAME = "lumecard_backup.json"
    }

    suspend fun upload(
        baseUrl: String,
        username: String,
        password: String,
        json: String
    ): Result<Unit> {
        return try {
            val url = baseUrl.trimEnd('/') + "/" + BACKUP_FILENAME
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
            val url = baseUrl.trimEnd('/') + "/" + BACKUP_FILENAME
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
