package com.lumecard.shared.data

data class WebDavProvider(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val nameJa: String,
    val nameEs: String,
    val nameZhTw: String,
    val urlTemplate: String,
    val domainPatterns: List<String>,
)

object WebDavProviders {
    val all = listOf(
        WebDavProvider(
            id = "jianguoyun",
            nameZh = "坚果云",
            nameEn = "Jianguoyun",
            nameJa = "Jianguoyun",
            nameEs = "Jianguoyun",
            nameZhTw = "堅果雲",
            urlTemplate = "https://dav.jianguoyun.com/dav/",
            domainPatterns = listOf("dav.jianguoyun.com", "jianguoyun.com"),
        ),
        WebDavProvider(
            id = "nextcloud",
            nameZh = "Nextcloud",
            nameEn = "Nextcloud",
            nameJa = "Nextcloud",
            nameEs = "Nextcloud",
            nameZhTw = "Nextcloud",
            urlTemplate = "",
            domainPatterns = listOf("nextcloud"),
        ),
        WebDavProvider(
            id = "owncloud",
            nameZh = "ownCloud",
            nameEn = "ownCloud",
            nameJa = "ownCloud",
            nameEs = "ownCloud",
            nameZhTw = "ownCloud",
            urlTemplate = "",
            domainPatterns = listOf("owncloud"),
        ),
        WebDavProvider(
            id = "syncthing",
            nameZh = "Syncthing",
            nameEn = "Syncthing",
            nameJa = "Syncthing",
            nameEs = "Syncthing",
            nameZhTw = "Syncthing",
            urlTemplate = "",
            domainPatterns = listOf("syncthing"),
        ),
    )

    fun detectProvider(url: String): WebDavProvider? {
        val lowerUrl = url.lowercase()
        return all.firstOrNull { provider ->
            provider.domainPatterns.any { pattern -> lowerUrl.contains(pattern) }
        }
    }

    fun getName(provider: WebDavProvider, locale: String): String {
        return when (locale) {
            "zh-CN" -> provider.nameZh
            "zh-TW" -> provider.nameZhTw
            "ja" -> provider.nameJa
            "es" -> provider.nameEs
            else -> provider.nameEn
        }
    }
}
