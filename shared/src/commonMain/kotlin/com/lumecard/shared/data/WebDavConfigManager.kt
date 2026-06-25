package com.lumecard.shared.data

import com.lumecard.shared.repository.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.time.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WebDavConfigManager(
    private val settingsRepository: SettingsRepository,
    private val client: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val configsKey = "webdav_configs"
    private val defaultIdKey = "webdav_default_id"

    suspend fun getAll(): List<WebDavConfig> {
        val raw = settingsRepository.get(configsKey) ?: return emptyList()
        return try {
            json.decodeFromString<List<WebDavConfig>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getById(id: String): WebDavConfig? {
        return getAll().find { it.id == id }
    }

    suspend fun getDefault(): WebDavConfig? {
        val defaultId = settingsRepository.get(defaultIdKey) ?: return null
        val config = getById(defaultId)
        if (config == null) settingsRepository.delete(defaultIdKey)
        return config
    }

    suspend fun save(config: WebDavConfig) {
        val configs = getAll().toMutableList()
        val idx = configs.indexOfFirst { it.id == config.id }
        if (idx >= 0) {
            configs[idx] = config
        } else {
            configs.add(config)
        }
        settingsRepository.set(configsKey, json.encodeToString(configs))
        if (config.isDefault) {
            settingsRepository.set(defaultIdKey, config.id)
        }
    }

    suspend fun delete(id: String) {
        val configs = getAll().toMutableList()
        val removedDefault = configs.find { it.id == id }?.isDefault == true
        configs.removeAll { it.id == id }
        settingsRepository.set(configsKey, json.encodeToString(configs))
        if (removedDefault) {
            settingsRepository.delete(defaultIdKey)
        }
    }

    suspend fun setDefault(id: String) {
        val configs = getAll().map { it.copy(isDefault = it.id == id) }
        settingsRepository.set(configsKey, json.encodeToString(configs))
        settingsRepository.set(defaultIdKey, id)
    }

    suspend fun updateLastSync(id: String) {
        val configs = getAll().map {
            if (it.id == id) it.copy(lastSyncAt = Clock.System.now().toString())
            else it
        }
        settingsRepository.set(configsKey, json.encodeToString(configs))
    }

    suspend fun testConnection(config: WebDavConfig): Result<String> {
        return try {
            val url = config.url.trimEnd('/')
            if (url.isBlank()) return Result.failure(SyncException("URL is empty"))
            val response = client.get("$url/") {
                basicAuth(config.username, config.password)
            }
            Result.success("HTTP ${response.status.value}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnectionLiveness(url: String, username: String, password: String): Result<String> {
        return try {
            val base = url.trimEnd('/')
            if (base.isBlank()) return Result.failure(SyncException("URL is empty"))
            val response = client.get("$base/") {
                basicAuth(username, password)
            }
            Result.success("HTTP ${response.status.value}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
