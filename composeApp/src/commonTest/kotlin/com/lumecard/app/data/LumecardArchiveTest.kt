package com.lumecard.app.data

import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Media
import com.lumecard.shared.model.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Guards the archive path rules. These are pure functions, so they are the cheapest place to
 * prove an archive can never be pointed outside the media root.
 */
class LumecardArchiveTest {

    private fun card(vararg filePaths: String, type: MediaType = MediaType.IMAGE): Card {
        val now = Instant.parse("2026-11-20T00:00:00Z")
        return Card(
            id = "card1",
            deckId = "deck1",
            type = CardType.BASIC,
            front = "q",
            back = "a",
            tags = emptyList(),
            media = filePaths.mapIndexed { index, path ->
                Media(id = "m$index", type = type, filePath = path)
            },
            createdAt = now,
            updatedAt = now,
        )
    }

    @Test
    fun `relative media names are archivable`() {
        assertTrue(LumecardArchive.isArchivableMediaPath("a1b2c3.png"))
        assertTrue(LumecardArchive.isArchivableMediaPath("nested/dir/photo.jpg"))
    }

    @Test
    fun `external sources are not archived`() {
        assertFalse(LumecardArchive.isArchivableMediaPath("https://example.com/a.png"))
        assertFalse(LumecardArchive.isArchivableMediaPath("http://example.com/a.png"))
        assertFalse(LumecardArchive.isArchivableMediaPath("file:///tmp/a.png"))
        assertFalse(LumecardArchive.isArchivableMediaPath("content://media/external/1"))
        assertFalse(LumecardArchive.isArchivableMediaPath("data:image/png;base64,AAAA"))
        assertFalse(LumecardArchive.isArchivableMediaPath("/abs/a.png"))
        assertFalse(LumecardArchive.isArchivableMediaPath("C:/abs/a.png"))
        assertFalse(LumecardArchive.isArchivableMediaPath(""))
    }

    @Test
    fun `traversal and malformed segments are rejected`() {
        assertFalse(LumecardArchive.isArchivableMediaPath("../secret.png"))
        assertFalse(LumecardArchive.isArchivableMediaPath("a/../../secret.png"))
        assertFalse(LumecardArchive.isArchivableMediaPath("./a.png"))
        assertFalse(LumecardArchive.isArchivableMediaPath("a//b.png"))
        // Backslashes would normalise to separators on Windows and must not be accepted.
        assertFalse(LumecardArchive.isArchivableMediaPath("a\\b.png"))
    }

    @Test
    fun `entry names round trip through the media prefix`() {
        assertEquals("media/a1b2c3.png", LumecardArchive.mediaEntryName("a1b2c3.png"))
        assertEquals("a1b2c3.png", LumecardArchive.mediaPathFromEntry("media/a1b2c3.png"))
    }

    @Test
    fun `unexpected entry names are ignored rather than trusted`() {
        assertNull(LumecardArchive.mediaPathFromEntry("lumecard.json"))
        assertNull(LumecardArchive.mediaPathFromEntry("media/"))
        assertNull(LumecardArchive.mediaPathFromEntry("media/../../etc/passwd"))
        assertNull(LumecardArchive.mediaPathFromEntry("other/a.png"))
    }

    @Test
    fun `percent encoded traversal stays a literal file name`() {
        // Nothing in the import path URL-decodes an entry name, so `%2e%2e` must be treated as the
        // literal directory name it is. If decoding were ever introduced upstream (a "helpful"
        // fix that unescapes names), this assertion is the tripwire that catches it.
        assertEquals("%2e%2e/a.png", LumecardArchive.mediaPathFromEntry("media/%2e%2e/a.png"))
        assertEquals("a%2Fb.png", LumecardArchive.mediaPathFromEntry("media/a%2Fb.png"))
    }

    @Test
    fun `control characters and disguised names are rejected`() {
        assertFalse(LumecardArchive.isSafeMediaRelativePath("a\u0000b.png"))
        assertFalse(LumecardArchive.isSafeMediaRelativePath("a\nb.png"))
        assertFalse(LumecardArchive.isSafeMediaRelativePath("a\u001Fb.png"))
        // Absolute-looking names under the media prefix must still not escape.
        assertNull(LumecardArchive.mediaPathFromEntry("media//etc/passwd"))
        assertNull(LumecardArchive.mediaPathFromEntry("media/C:/Windows/a.png"))
        assertNull(LumecardArchive.mediaPathFromEntry("media/a\\..\\b.png"))
        // A right-to-left override is a legitimate Unicode code point, not an escape: it is accepted
        // as part of a file name. Recorded here so the rule is explicit rather than accidental —
        // the containment check that matters is canonical-path validation, not character policing.
        assertTrue(LumecardArchive.isSafeMediaRelativePath("a\u202Eb.png"))
    }

    @Test
    fun `only referenced image media is collected and de-duplicated`() {
        val cards = listOf(
            card("a.png", "a.png", "https://example.com/b.png"),
            card("c.png", type = MediaType.AUDIO),
        )
        assertEquals(listOf("a.png"), LumecardArchive.referencedLocalMedia(cards).sorted())
    }

    @Test
    fun `import-side reference list filters types the same way the writer does`() {
        // The writer archives IMAGE only. If the reader counted audio as "referenced", every import
        // of a card with a local sound clip would report a missing image.
        val entries = listOf(
            "a.png" to "IMAGE",
            "clip.mp3" to "AUDIO",
            "movie.mp4" to "VIDEO",
            "https://example.com/x.png" to "IMAGE",
        )
        assertEquals(listOf("a.png"), LumecardArchive.referencedLocalMediaFrom(entries))
    }

    @Test
    fun `per-image alignment and compression flags survive serialization`() {
        // Alignment and compress live only in the JSON payload, so an archive round trip depends on
        // the Media fields being serialized verbatim. Off-centre or uncompressed images must not
        // silently revert to the defaults when a knowledge base travels between devices.
        val now = Instant.parse("2026-11-20T00:00:00Z")
        val original = Media(
            id = "m1",
            type = MediaType.IMAGE,
            filePath = "a1b2c3.png",
            caption = "cap",
            alignment = "end",
            compress = false,
        )
        val card = Card(
            id = "c1", deckId = "d1", type = CardType.BASIC,
            front = "q", back = "a", tags = emptyList(),
            media = listOf(original), createdAt = now, updatedAt = now,
        )

        val json = kotlinx.serialization.json.Json.encodeToString(Card.serializer(), card)
        val restored = kotlinx.serialization.json.Json.decodeFromString(Card.serializer(), json)

        assertEquals("end", restored.media.single().alignment)
        assertEquals(false, restored.media.single().compress)
        assertEquals("cap", restored.media.single().caption)
    }

    @Test
    fun `entry list puts the manifest first and drops duplicates`() {
        val entries = LumecardArchive.buildEntries(
            manifestTempPath = "/tmp/lumecard.json",
            mediaFiles = listOf(
                "a.png" to "/media/a.png",
                "a.png" to "/media/a.png",
                "../evil.png" to "/media/evil.png",
                "b.png" to "/media/b.png",
            ),
        )
        assertEquals(LumecardArchive.ENTRY_MANIFEST, entries.first().first)
        assertEquals(listOf("lumecard.json", "media/a.png", "media/b.png"), entries.map { it.first })
    }

    @Test
    fun `zip signature detection distinguishes archives from json`() {
        assertTrue(LumecardArchive.looksLikeZip(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        // Empty archive marker.
        assertTrue(LumecardArchive.looksLikeZip(byteArrayOf(0x50, 0x4B, 0x05, 0x06)))
        assertFalse(LumecardArchive.looksLikeZip("{\"exportType\":".encodeToByteArray()))
        assertFalse(LumecardArchive.looksLikeZip(byteArrayOf()))
        assertFalse(LumecardArchive.looksLikeZip(byteArrayOf(0x50, 0x4B)))
    }
}
