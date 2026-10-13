package com.lumecard.shared.data

import com.lumecard.shared.repository.InMemoryMediaCacheRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaSyncServiceTest {
    private val config = WebDavConfig("test", "test", "https://example.test/dav", "user", "password")
    private val manager = MediaManager(InMemoryMediaCacheRepository())
    private val bytes = "media".encodeToByteArray()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun entry(path: String = "image.png") = MediaManifestEntry(path, bytes.size.toLong(), hash(bytes))

    @Test
    fun uploadReadsOldManifestAndPublishesOnlyAfterFilesPreservingRemoteEntries() = runBlocking {
        val events = mutableListOf<String>()
        var published: MediaManifest? = null
        val old = MediaManifest(entries = listOf(entry("old.png")))
        val local = MediaManifest(entries = listOf(entry("folder/a b.png")))
        val client = HttpClient(MockEngine { request ->
            events.add("${request.method.value} ${request.url.encodedPath}")
            when {
                request.method == HttpMethod.Get -> respond(manager.manifestToJson(old))
                request.method.value == "MKCOL" -> respond("", HttpStatusCode.MethodNotAllowed)
                request.url.encodedPath.endsWith("media_manifest.json") -> {
                    val content = request.body as OutgoingContent.ByteArrayContent
                    published = manager.manifestFromJson(content.bytes().decodeToString())
                    respond("", HttpStatusCode.Created)
                }
                else -> respond("", HttpStatusCode.Created)
            }
        })
        try {
            MediaSyncService(SyncManager(client), manager).upload(config, local, { bytes }, ::hash)
            assertTrue(events.first().startsWith("GET "))
            assertTrue(events.last().endsWith("media_manifest.json"))
            assertTrue(events.any { it == "PUT /dav/LumeCard/media/folder/a%20b.png" })
            assertFalse(events.any { it.startsWith("MKCOL") && it.contains("a%20b.png") })
            assertEquals(setOf("old.png", "folder/a b.png"), published?.entries?.map { it.path }?.toSet())
        } finally { client.close() }
    }

    @Test
    fun failedUploadDoesNotPublishManifest() = runBlocking {
        var manifestWrites = 0
        val client = HttpClient(MockEngine { request ->
            when {
                request.method == HttpMethod.Get -> respond("", HttpStatusCode.NotFound)
                request.method.value == "MKCOL" -> respond("", HttpStatusCode.Created)
                request.url.encodedPath.endsWith("media_manifest.json") -> {
                    manifestWrites++
                    respond("", HttpStatusCode.Created)
                }
                else -> respond("", HttpStatusCode.InternalServerError)
            }
        })
        try {
            assertFailsWith<SyncException> {
                MediaSyncService(SyncManager(client), manager).upload(config, MediaManifest(entries = listOf(entry())), { bytes }, ::hash)
            }
            assertEquals(0, manifestWrites)
        } finally { client.close() }
    }

    @Test
    fun downloadWorksWithEmptyLocalDirectoryAndDoesNotWriteCloud() = runBlocking {
        val methods = mutableListOf<HttpMethod>()
        val remote = MediaManifest(entries = listOf(entry()))
        val client = HttpClient(MockEngine { request ->
            methods.add(request.method)
            if (request.url.encodedPath.endsWith("media_manifest.json")) respond(manager.manifestToJson(remote))
            else respond(bytes)
        })
        val written = mutableMapOf<String, ByteArray>()
        try {
            MediaSyncService(SyncManager(client), manager).download(config, MediaManifest(), { path, data -> written[path] = data }, ::hash)
            assertContentEquals(bytes, written["image.png"])
            assertEquals(listOf(HttpMethod.Get, HttpMethod.Get), methods)
        } finally { client.close() }
    }

    @Test
    fun changedLocalFilesAreDownloadedAgainAndCorruptionIsRejectedBeforeWriting() = runBlocking {
        val remote = MediaManifest(entries = listOf(entry()))
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("media_manifest.json")) respond(manager.manifestToJson(remote))
            else respond("bad".encodeToByteArray())
        })
        var writes = 0
        try {
            val outdated = MediaManifest(entries = listOf(entry().copy(hash = "0".repeat(40))))
            assertFailsWith<IllegalStateException> {
                MediaSyncService(SyncManager(client), manager).download(config, outdated, { _, _ -> writes++ }, ::hash)
            }
            assertEquals(0, writes)
        } finally { client.close() }
    }

    @Test
    fun authenticationFailureDoesNotBecomeEmptyManifest() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond("", HttpStatusCode.Unauthorized)
        })
        try {
            val failure = assertFailsWith<SyncException> {
                MediaSyncService(SyncManager(client), manager).upload(config, MediaManifest(entries = listOf(entry())), { bytes }, ::hash)
            }
            assertEquals(401, failure.statusCode)
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test
    fun onlyNotFoundMayBeIgnoredBeforeOverwrite() {
        assertNull(Result.failure<String>(SyncException("missing", 404)).getOrNullIfNotFound())
        for (status in listOf(401, 403, 500)) {
            assertFailsWith<SyncException> { Result.failure<String>(SyncException("failed", status)).getOrNullIfNotFound() }
        }
        assertFailsWith<java.io.IOException> { Result.failure<String>(java.io.IOException("timeout")).getOrNullIfNotFound() }
        assertFailsWith<CancellationException> { Result.failure<String>(CancellationException()).getOrNullIfNotFound() }
    }

    @Test
    fun unsafePathsAreRejected() {
        for (path in listOf("../outside", "/outside", "a/../../outside", "a\\outside", "C:/outside", "a//b", "%2e%2e/outside")) {
            assertFailsWith<IllegalArgumentException> { validateMediaPath(path) }
        }
        validateMediaPath("folder/图片.png")
    }
}
