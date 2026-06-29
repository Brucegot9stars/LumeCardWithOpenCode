package com.lumecard.shared.data

import com.lumecard.shared.repository.SettingsRepository
import kotlin.time.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AiConfigManager(
    private val settingsRepository: SettingsRepository,
    private val client: AiClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val configsKey = "ai_configs"
    private val defaultIdKey = "ai_default_id"

    suspend fun getAll(): List<AiConfig> {
        val raw = settingsRepository.get(configsKey) ?: return emptyList()
        return try {
            val configs = json.decodeFromString<List<AiConfig>>(raw)
            migrateConfigs(configs)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun migrateConfigs(configs: List<AiConfig>): List<AiConfig> {
        val migrated = configs.map { c ->
            var changed = false
            var result = c
            if (result.fallbackConfigId == null && result.isDefault) {
                val sibling = configs.firstOrNull { it.id != result.id && it.provider == result.provider }
                if (sibling != null) {
                    result = result.copy(fallbackConfigId = sibling.id)
                    changed = true
                }
            }
            if (changed) {
                save(result)
            }
            result
        }
        return migrated
    }

    suspend fun getById(id: String): AiConfig? {
        return getAll().find { it.id == id }
    }

    suspend fun getDefault(): AiConfig? {
        val defaultId = settingsRepository.get(defaultIdKey) ?: return null
        val config = getById(defaultId)
        if (config == null) settingsRepository.delete(defaultIdKey)
        return config
    }

    suspend fun save(config: AiConfig) {
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

    suspend fun testConnection(config: AiConfig): Result<String> {
        return client.testConnection(config)
    }
}
