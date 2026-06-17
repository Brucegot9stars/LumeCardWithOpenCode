package com.lumecard.shared.data

import com.lumecard.shared.AppVersion
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val publishedAt: String,
    val hasUpdate: Boolean
)

class UpdateManager(
    private val client: HttpClient
) {
    companion object {
        private const val RELEASES_URL = "https://api.github.com/repos/Brucegot9stars/LumeCardWithOpenCode/releases/latest"
    }

    suspend fun checkForUpdate(): UpdateInfo? {
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
            val currentVersion = AppVersion.VERSION_NAME
            val hasUpdate = compareVersions(latestVersion, currentVersion) > 0

            UpdateInfo(
                version = latestVersion,
                downloadUrl = htmlUrl?.replace("github.com", "api.github.com/repos")?.replace("/releases/", "/releases/download/") ?: "",
                releaseNotes = bodyText ?: "",
                publishedAt = publishedAt ?: "",
                hasUpdate = hasUpdate
            )
        } catch (e: Exception) {
            null
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
}
