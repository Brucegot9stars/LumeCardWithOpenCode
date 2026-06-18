package com.lumecard.shared.data

import com.lumecard.shared.AppVersion
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val publishedAt: String,
    val hasUpdate: Boolean,
    val assets: List<UpdateAsset> = emptyList()
)

data class UpdateAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long
)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateState()
    data object UpToDate : UpdateState()
    data class Error(val message: String) : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data object Installing : UpdateState()
    data object Complete : UpdateState()
}

class UpdateManager(
    private val client: HttpClient
) {
    companion object {
        private const val RELEASES_URL = "https://api.github.com/repos/Brucegot9stars/LumeCardWithOpenCode/releases/latest"
        private const val ALL_RELEASES_URL = "https://api.github.com/repos/Brucegot9stars/LumeCardWithOpenCode/releases"
    }

    suspend fun checkForUpdate(currentVersion: String = AppVersion.VERSION_NAME): UpdateInfo? {
        return try {
            val response = client.get(RELEASES_URL)
            if (!response.status.isSuccess()) return null

            val body = response.bodyAsText()
            val tagName = extractJsonString(body, "tag_name")
            val htmlUrl = extractJsonString(body, "html_url")
            val bodyText = extractJsonString(body, "body")
            val publishedAt = extractJsonString(body, "published_at")

            if (tagName == null) return null

            val latestVersion = tagName.removePrefix("v")
            val hasUpdate = compareVersions(latestVersion, currentVersion) > 0

            val assets = parseAssets(body)

            UpdateInfo(
                version = latestVersion,
                downloadUrl = htmlUrl ?: "",
                releaseNotes = bodyText ?: "",
                publishedAt = publishedAt ?: "",
                hasUpdate = hasUpdate,
                assets = assets
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getReleaseHistory(): List<UpdateInfo> {
        return try {
            val response = client.get(ALL_RELEASES_URL)
            if (!response.status.isSuccess()) return emptyList()

            val body = response.bodyAsText()
            parseReleases(body)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun downloadApk(
        url: String,
        destFile: File,
        onProgress: (Float) -> Unit
    ): Boolean {
        val downloadClient = HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 600_000
                connectTimeoutMillis = 60_000
            }
            expectSuccess = false
        }
        return try {
            withContext(Dispatchers.IO) {
                val response = downloadClient.get(url) {
                    header("User-Agent", "LumeCard/Android")
                }
                if (!response.status.isSuccess()) {
                    throw SyncException("HTTP ${response.status.value}: ${response.status.description}")
                }

                val channel = response.bodyAsChannel()
                destFile.parentFile?.mkdirs()
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    while (true) {
                        bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                    }
                    onProgress(1f)
                }
                true
            }
        } catch (e: Exception) {
            throw SyncException("下载失败：${e.message ?: "未知错误"}")
        } finally {
            downloadClient.close()
        }
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*?)\""
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun parseAssets(json: String): List<UpdateAsset> {
        val assets = mutableListOf<UpdateAsset>()
        val assetRegex = """"name"\s*:\s*"([^"]*?)"[^}]*?"browser_download_url"\s*:\s*"([^"]*?)" """.toRegex()
        assetRegex.findAll(json).forEach { match ->
            val name = match.groupValues[1]
            val url = match.groupValues[2]
            if (name.endsWith(".apk")) {
                assets.add(UpdateAsset(
                    name = name,
                    downloadUrl = url,
                    size = 0
                ))
            }
        }
        return assets
    }

    private fun parseReleases(json: String): List<UpdateInfo> {
        val releases = mutableListOf<UpdateInfo>()
        val releasePattern = """\{[^}]*"tag_name"\s*:\s*"([^"]*?)"[^}]*"html_url"\s*:\s*"([^"]*?)"[^}]*"body"\s*:\s*"((?:[^"\\]|\\.)*)"[^}]*"published_at"\s*:\s*"([^"]*?)"[^}]*\}""".toRegex()
        releasePattern.findAll(json).forEach { match ->
            releases.add(UpdateInfo(
                version = match.groupValues[1].removePrefix("v"),
                downloadUrl = match.groupValues[2],
                releaseNotes = match.groupValues[3].replace("\\n", "\n").replace("\\\"", "\""),
                publishedAt = match.groupValues[4],
                hasUpdate = false
            ))
        }
        return releases
    }
}
