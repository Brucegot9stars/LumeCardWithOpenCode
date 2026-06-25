package com.lumecard.shared.data

data class WebDavProvider(
    val id: String,
    val name: String,
    val url: String,
    val domainPatterns: List<String> = emptyList(),
)

object WebDavProviders {
    val all = listOf(
        WebDavProvider(
            id = "jianguoyun",
            name = "坚果云",
            url = "https://dav.jianguoyun.com/dav/",
            domainPatterns = listOf("dav.jianguoyun.com", "jianguoyun.com"),
        ),
        WebDavProvider(
            id = "nutstore",
            name = "Nutstore（坚果云海外版）",
            url = "https://dav.nutstore.net/dav/",
            domainPatterns = listOf("dav.nutstore.net", "nutstore.net"),
        ),
        WebDavProvider(
            id = "pcloud_us",
            name = "pCloud (US)",
            url = "https://webdav.pcloud.com/",
            domainPatterns = listOf("webdav.pcloud.com", "pcloud.com"),
        ),
        WebDavProvider(
            id = "pcloud_eu",
            name = "pCloud (EU)",
            url = "https://ewebdav.pcloud.com/",
            domainPatterns = listOf("ewebdav.pcloud.com"),
        ),
        WebDavProvider(
            id = "koofr",
            name = "Koofr",
            url = "https://app.koofr.net/dav/Koofr/",
            domainPatterns = listOf("app.koofr.net", "koofr.net", "koofr.eu"),
        ),
        WebDavProvider(
            id = "yandex_disk",
            name = "Yandex Disk",
            url = "https://webdav.yandex.com/",
            domainPatterns = listOf("webdav.yandex.com", "yandex.com"),
        ),
        WebDavProvider(
            id = "infini_cloud",
            name = "InfiniCLOUD",
            url = "https://asgard.teracloud.jp/dav/",
            domainPatterns = listOf("asgard.teracloud.jp", "teracloud.jp", "infinicloud"),
        ),
        WebDavProvider(
            id = "hidrive",
            name = "HiDrive",
            url = "https://webdav.hidrive.strato.com/",
            domainPatterns = listOf("webdav.hidrive.strato.com", "hidrive.strato.com", "hidrive"),
        ),
        WebDavProvider(
            id = "drivehq",
            name = "DriveHQ",
            url = "https://webdav.drivehq.com/",
            domainPatterns = listOf("webdav.drivehq.com", "drivehq.com", "drivehq"),
        ),
        WebDavProvider(
            id = "box",
            name = "Box",
            url = "https://dav.box.com/dav/",
            domainPatterns = listOf("dav.box.com", "box.com"),
        ),
        WebDavProvider(
            id = "nextcloud",
            name = "Nextcloud",
            url = "https://{host}/remote.php/dav/files/{username}/",
            domainPatterns = listOf("nextcloud"),
        ),
        WebDavProvider(
            id = "owncloud",
            name = "ownCloud",
            url = "https://{host}/remote.php/webdav/",
            domainPatterns = listOf("owncloud"),
        ),
        WebDavProvider(
            id = "synology",
            name = "Synology NAS",
            url = "https://{host}:5006/",
            domainPatterns = listOf("synology"),
        ),
        WebDavProvider(
            id = "qnap",
            name = "QNAP NAS",
            url = "https://{host}:8081/",
            domainPatterns = listOf("qnap"),
        ),
        WebDavProvider(
            id = "seafile",
            name = "Seafile",
            url = "https://{host}/seafdav",
            domainPatterns = listOf("seafile"),
        ),
        WebDavProvider(
            id = "custom",
            name = "Custom",
            url = "https://",
            domainPatterns = emptyList(),
        ),
    )

    fun detectProvider(url: String): WebDavProvider? {
        val lowerUrl = url.lowercase()
        return all.firstOrNull { provider ->
            provider.domainPatterns.any { pattern -> lowerUrl.contains(pattern) }
        }
    }
}
