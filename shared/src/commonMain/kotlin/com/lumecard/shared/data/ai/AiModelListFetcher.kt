package com.lumecard.shared.data.ai

import com.lumecard.shared.data.AiConfig
import com.lumecard.shared.data.AiProtocols
import com.lumecard.shared.repository.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AiModelListFetcher(
    private val client: HttpClient,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cachePrefix = "ai_fetched_models_"

    suspend fun fetchModels(config: AiConfig): List<String> {
        return try {
            val baseUrl = config.baseUrl.trimEnd('/')
            if (baseUrl.isBlank() || config.apiKey.isBlank()) {
                return getCachedModels(config.id) ?: emptyList()
            }

            val protocol = AiProtocols.findById(config.protocol)

            val response = client.get("$baseUrl/models") {
                if (protocol != null) {
                    protocol.headers(config).forEach { (k, v) -> header(k, v) }
                } else {
                    header("Authorization", "Bearer ${config.apiKey}")
                }
            }

            if (!response.status.isSuccess()) {
                val cached = getCachedModels(config.id)
                return cached ?: emptyList()
            }

            val body = response.bodyAsText()
            val modelIds = parseModelList(body)
            cacheModels(config.id, modelIds)
            modelIds
        } catch (_: Exception) {
            getCachedModels(config.id) ?: emptyList()
        }
    }

    private fun parseModelList(responseBody: String): List<String> {
        return try {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val data = root["data"]?.jsonArray ?: return emptyList()
            data.mapNotNull { element ->
                element.jsonObject["id"]?.jsonPrimitive?.content
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun getCachedModels(configId: String): List<String>? {
        val raw = settingsRepository.get("$cachePrefix$configId") ?: return null
        return try {
            json.decodeFromString<List<String>>(raw)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun cacheModels(configId: String, models: List<String>) {
        if (models.isEmpty()) return
        val encoded = json.encodeToString(models)
        settingsRepository.set("$cachePrefix$configId", encoded)
    }

    suspend fun removeFromCache(configId: String, modelId: String) {
        val cached = getCachedModels(configId)?.toMutableList() ?: return
        if (cached.remove(modelId)) {
            if (cached.isEmpty()) {
                settingsRepository.delete("$cachePrefix$configId")
            } else {
                settingsRepository.set("$cachePrefix$configId", json.encodeToString(cached))
            }
        }
    }
}
