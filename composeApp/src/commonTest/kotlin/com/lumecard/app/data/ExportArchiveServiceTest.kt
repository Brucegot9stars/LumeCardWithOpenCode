package com.lumecard.app.data

import com.lumecard.app.platform.getMediaBasePath
import com.lumecard.app.platform.platformDeleteFile
import com.lumecard.app.platform.platformReadFileBytes
import com.lumecard.app.platform.readZipArchiveEntries
import com.lumecard.shared.model.Card
import com.lumecard.shared.model.CardType
import com.lumecard.shared.model.Media
import com.lumecard.shared.model.MediaType
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking

/**
 * End-to-end archive round trip against the real filesystem.
 *
 * `getMediaBasePath()` honours the `lumecard.media.dir` system property on Desktop, so these tests
 * redirect the media root to a temp directory instead of touching the user's real media folder.
 */
class ExportArchiveServiceTest {

    private lateinit var tempRoot: File
    private var previousMediaDir: String? = null

    @BeforeTest
    fun setUp() {
        tempRoot = File(System.getProperty("java.io.tmpdir"), "lumecard-archive-test-${System.nanoTime()}")
        tempRoot.mkdirs()
        previousMediaDir = System.getProperty("lumecard.media.dir")
        System.setProperty("lumecard.media.dir", File(tempRoot, "media").absolutePath)
        File(getMediaBasePath()).mkdirs()
    }

    @AfterTest
    fun tearDown() {
        if (previousMediaDir == null) System.clearProperty("lumecard.media.dir")
        else System.setProperty("lumecard.media.dir", previousMediaDir!!)
        tempRoot.deleteRecursively()
    }

    private fun writeMediaFile(name: String, bytes: ByteArray): File {
        val file = File(getMediaBasePath(), name)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    private fun card(vararg filePaths: String): Card {
        val now = Instant.parse("2026-11-20T00:00:00Z")
        return Card(
            id = "card-${filePaths.joinToString("-")}",
            deckId = "deck1",
            type = CardType.BASIC,
            front = "q",
            back = "a",
            tags = emptyList(),
            media = filePaths.mapIndexed { i, p -> Media(id = "m$i", type = MediaType.IMAGE, filePath = p) },
            createdAt = now,
            updatedAt = now,
        )
    }

    private val manifest = """{"exportType":"share","version":"1.0.0","schemaVersion":2}"""

    @Test
    fun `export without local media stays a plain json file`() = runBlocking {
        val cards = listOf(card("https://example.com/a.png"))
        val jsonOut = File(tempRoot, "out.json").absolutePath
        val archiveOut = File(tempRoot, "out.lumecard").absolutePath

        val outcome = packageExport(manifest, cards, archiveOut, jsonOut)

        assertFalse(outcome.isArchive)
        assertEquals(0, outcome.mediaIncluded)
        assertFalse(File(archiveOut).exists(), "no archive should be produced for remote-only images")
        assertEquals(manifest, File(jsonOut).readText())
    }

    @Test
    fun `export archives referenced local media and import restores it`() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        writeMediaFile("a1b2c3.png", bytes)
        val cards = listOf(card("a1b2c3.png"))
        val archiveOut = File(tempRoot, "out.lumecard").absolutePath
        val jsonOut = File(tempRoot, "out.json").absolutePath

        val outcome = packageExport(manifest, cards, archiveOut, jsonOut)
        assertTrue(outcome.isArchive)
        assertEquals(1, outcome.mediaIncluded)
        assertEquals(0, outcome.mediaMissing)

        val names = assertNotNull(LumecardArchive.listEntries(archiveOut))
        assertEquals(listOf("lumecard.json", "media/a1b2c3.png"), names)
        assertEquals(manifest, LumecardArchive.readManifest(archiveOut))

        // Wipe the media root, then import as a fresh device would.
        File(getMediaBasePath()).deleteRecursively()
        File(getMediaBasePath()).mkdirs()

        val mediaOutcome = importArchiveMedia(archiveOut, names, referencedPaths = listOf("a1b2c3.png"))
        assertEquals(1, mediaOutcome.written)
        assertEquals(0, mediaOutcome.skipped)
        assertEquals(0, mediaOutcome.missing)
        assertEquals(0, mediaOutcome.failed)
        assertTrue(mediaOutcome.renamed.isEmpty())
        assertContentEqualsSafe(bytes, File(getMediaBasePath(), "a1b2c3.png").readBytes())
    }

    @Test
    fun `identical existing media is skipped instead of rewritten`() = runBlocking {
        val bytes = byteArrayOf(9, 8, 7)
        writeMediaFile("hash.png", bytes)
        val cards = listOf(card("hash.png"))
        val archiveOut = File(tempRoot, "out.lumecard").absolutePath

        packageExport(manifest, cards, archiveOut, File(tempRoot, "o.json").absolutePath)
        val names = assertNotNull(LumecardArchive.listEntries(archiveOut))

        // Media root still holds the same bytes: import must be a no-op, not a duplicate write.
        val outcome = importArchiveMedia(archiveOut, names, referencedPaths = listOf("hash.png"))
        assertEquals(0, outcome.written)
        assertEquals(1, outcome.skipped)
        assertEquals(0, outcome.missing)
    }

    @Test
    fun `same name with different content is renamed and reported`() = runBlocking {
        writeMediaFile("clash.png", byteArrayOf(1, 1, 1))
        val cards = listOf(card("clash.png"))
        val archiveOut = File(tempRoot, "out.lumecard").absolutePath
        packageExport(manifest, cards, archiveOut, File(tempRoot, "o.json").absolutePath)
        val names = assertNotNull(LumecardArchive.listEntries(archiveOut))

        // Replace the local file with different content, keeping the name.
        writeMediaFile("clash.png", byteArrayOf(2, 2, 2))

        val outcome = importArchiveMedia(archiveOut, names, referencedPaths = listOf("clash.png"))
        assertEquals(1, outcome.written)
        assertEquals(0, outcome.skipped)
        assertEquals(0, outcome.missing)
        val renamedTo = assertNotNull(outcome.renamed["clash.png"], "collision must be reported so cards can be rewritten")
        assertTrue(renamedTo != "clash.png")
        // Both the user's file and the archived one survive.
        assertContentEqualsSafe(byteArrayOf(2, 2, 2), File(getMediaBasePath(), "clash.png").readBytes())
        assertContentEqualsSafe(byteArrayOf(1, 1, 1), File(getMediaBasePath(), renamedTo).readBytes())
    }

    @Test
    fun `missing local media is counted but does not fail the export`() = runBlocking {
        writeMediaFile("present.png", byteArrayOf(5))
        val cards = listOf(card("present.png", "ghost.png"))
        val archiveOut = File(tempRoot, "out.lumecard").absolutePath

        val outcome = packageExport(manifest, cards, archiveOut, File(tempRoot, "o.json").absolutePath)

        assertTrue(outcome.isArchive)
        assertEquals(1, outcome.mediaIncluded)
        assertEquals(1, outcome.mediaMissing)
        val names = assertNotNull(LumecardArchive.listEntries(archiveOut))
        assertEquals(listOf("lumecard.json", "media/present.png"), names)
    }

    @Test
    fun `export with only missing media falls back to plain json`() = runBlocking {
        val cards = listOf(card("ghost.png"))
        val archiveOut = File(tempRoot, "out.lumecard").absolutePath
        val jsonOut = File(tempRoot, "out.json").absolutePath

        val outcome = packageExport(manifest, cards, archiveOut, jsonOut)

        assertFalse(outcome.isArchive, "an archive with no media would be misleading")
        assertEquals(0, outcome.mediaIncluded)
        assertEquals(1, outcome.mediaMissing)
        assertFalse(File(archiveOut).exists())
        assertEquals(manifest, File(jsonOut).readText())
    }

    @Test
    fun `crafted archive cannot write outside the media root`() = runBlocking {
        // Hand-build an archive whose entry tries to escape via traversal.
        val evil = File(tempRoot, "evil.lumecard").absolutePath
        ZipOutputStream(File(evil).outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("lumecard.json"))
            zip.write(manifest.encodeToByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("media/../../escaped.png"))
            zip.write(byteArrayOf(6, 6, 6))
            zip.closeEntry()
        }
        val names = assertNotNull(LumecardArchive.listEntries(evil))
        assertTrue(names.any { it.contains("..") })

        val outcome = importArchiveMedia(evil, names, referencedPaths = listOf("../../escaped.png"))

        assertEquals(0, outcome.written)
        assertFalse(File(tempRoot, "escaped.png").exists(), "traversal entry must be rejected")
        assertFalse(File(getMediaBasePath()).parentFile!!.resolve("escaped.png").exists())
    }

    @Test
    fun `reading a missing entry returns null instead of throwing`() = runBlocking {
        writeMediaFile("a.png", byteArrayOf(1))
        val archiveOut = File(tempRoot, "out.lumecard").absolutePath
        packageExport(manifest, listOf(card("a.png")), archiveOut, File(tempRoot, "o.json").absolutePath)

        assertNull(LumecardArchive.readMedia(archiveOut, "nope.png"))
        assertNull(LumecardArchive.readMedia(archiveOut, "../a.png"))
        assertNull(LumecardArchive.readManifest(File(tempRoot, "absent.lumecard").absolutePath))
    }

    @Test
    fun `exported archive payload is byte identical to the plain json`() = runBlocking {
        writeMediaFile("x.png", byteArrayOf(4, 4))
        val archiveOut = File(tempRoot, "out.lumecard").absolutePath
        val outcome = packageExport(
            manifest, listOf(card("x.png")), archiveOut,
            File(tempRoot, "o.json").absolutePath,
        )
        assertTrue(outcome.isArchive)

        // The embedded JSON must be byte-identical to what a plain .json export would contain, so
        // an archive can also be opened by a zip tool and its payload imported as a bare JSON file.
        assertEquals(manifest, LumecardArchive.readManifest(archiveOut))
    }

    @Test
    fun `staging manifest never lands in the media root`() = runBlocking {
        // Staging lives in the app cache, not the media root: a leftover `lumecard.json` beside the
        // user's images would be picked up by the media scan and synced as if it were media.
        writeMediaFile("stage.png", byteArrayOf(8))
        val archiveOut = File(tempRoot, "stage.lumecard").absolutePath

        val outcome = packageExport(
            manifest, listOf(card("stage.png")), archiveOut,
            File(tempRoot, "stage.json").absolutePath,
        )

        assertTrue(outcome.isArchive)
        val strays = File(getMediaBasePath()).listFiles().orEmpty().map { it.name }
        assertEquals(listOf("stage.png"), strays, "only real media may remain in the media root")
    }

    @Test
    fun `export with lumecard name but no media writes a single dot json`() = runBlocking {
        // The caller derives the JSON path, so a `.lumecard` name must collapse to `a.json`
        // instead of producing the double-suffixed `a.lumecard.json`.
        val chosenPath = File(tempRoot, "a.lumecard").absolutePath
        val jsonOut = File(tempRoot, "a.json").absolutePath
        val cards = listOf(card("https://example.com/a.png"))

        val outcome = packageExport(manifest, cards, chosenPath, jsonOut)

        assertFalse(outcome.isArchive)
        assertEquals(jsonOut, outcome.path)
        assertEquals(manifest, File(jsonOut).readText())
        assertFalse(File("$chosenPath.json").exists(), "no double-suffixed file may appear")
    }

    @Test
    fun `archive write failure falls back to plain json and reports failure`() = runBlocking {
        writeMediaFile("present.png", byteArrayOf(7))
        val cards = listOf(card("present.png"))
        // A directory squatting on the target path makes the zip write throw.
        val blockedOut = File(tempRoot, "blocked").absolutePath
        assertTrue(File(blockedOut).mkdirs(), "need an unwritable target to simulate a broken write")
        val jsonOut = File(tempRoot, "blocked.json").absolutePath

        val outcome = packageExport(manifest, cards, blockedOut, jsonOut)

        assertFalse(outcome.isArchive)
        assertTrue(outcome.writeFailed)
        assertEquals(jsonOut, outcome.path)
        assertEquals(manifest, File(jsonOut).readText())
    }

    @Test
    fun `rename lookup tolerates surrounding whitespace`() {
        assertEquals("a_xx.png", resolveRenamedPath(mapOf("a.png" to "a_xx.png"), "  a.png "))
    }

    @Test
    fun `rename lookup tolerates case difference`() {
        assertEquals("A_xx.png", resolveRenamedPath(mapOf("A.PNG" to "A_xx.png"), "a.png"))
    }

    @Test
    fun `media missing counts referenced paths absent from the archive`() = runBlocking {
        writeMediaFile("present.png", byteArrayOf(3))
        val archiveOut = File(tempRoot, "out.lumecard").absolutePath
        packageExport(
            manifest, listOf(card("present.png")), archiveOut,
            File(tempRoot, "o.json").absolutePath,
        )
        val names = assertNotNull(LumecardArchive.listEntries(archiveOut))

        // Fresh device: nothing is on disk yet, and the manifest references a file that never
        // made it into the archive.
        File(getMediaBasePath()).deleteRecursively()
        File(getMediaBasePath()).mkdirs()

        val outcome = importArchiveMedia(
            archiveOut,
            names,
            referencedPaths = listOf("present.png", "ghost.png"),
        )

        assertEquals(1, outcome.written)
        assertEquals(1, outcome.missing)
    }

    @Test
    fun `extra unreferenced archive entry does not inflate missing`() = runBlocking {
        val archiveOut = File(tempRoot, "extra.lumecard").absolutePath
        val mediaDir = File(getMediaBasePath())
        mediaDir.mkdirs()
        val present = File(mediaDir, "present.png").apply { writeBytes(byteArrayOf(1)) }
        val junk = File(mediaDir, "junk.png").apply { writeBytes(byteArrayOf(2)) }
        ZipOutputStream(File(archiveOut).outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("lumecard.json"))
            zip.write(manifest.encodeToByteArray())
            zip.closeEntry()
            for (file in listOf("present.png" to present, "junk.png" to junk)) {
                zip.putNextEntry(ZipEntry("media/${file.first}"))
                zip.write(file.second.readBytes())
                zip.closeEntry()
            }
        }
        val names = assertNotNull(LumecardArchive.listEntries(archiveOut))

        File(getMediaBasePath()).deleteRecursively()
        File(getMediaBasePath()).mkdirs()

        val outcome = importArchiveMedia(archiveOut, names, referencedPaths = listOf("present.png"))

        // The un-referenced entry is still unpacked, but it is neither missing nor failed.
        assertEquals(0, outcome.missing)
        assertEquals(2, outcome.written)
        assertTrue(File(getMediaBasePath(), "junk.png").exists())
    }

    @Test
    fun `json sibling path strips only the file extension`() {
        assertEquals("/tmp/a.json", jsonSiblingPath("/tmp/a.lumecard"))
        assertEquals("/tmp/a.json", jsonSiblingPath("/tmp/a.json"))
        // Case-insensitive suffix match returns the chosen path unchanged, case preserved.
        assertEquals("/tmp/a.JSON", jsonSiblingPath("/tmp/a.JSON"))
        // A dot in a directory name must not be mistaken for the extension separator.
        assertEquals("/a.b/export.json", jsonSiblingPath("/a.b/export"))
        assertEquals("C:\\dir.x\\out.json", jsonSiblingPath("C:\\dir.x\\out.lumecard"))
        // A leading dot is part of the name, not an extension.
        assertEquals("/tmp/.hidden.json", jsonSiblingPath("/tmp/.hidden"))
        // No separator at all: keep it relative.
        assertEquals("export.json", jsonSiblingPath("export.lumecard"))
    }

    @Test
    fun `document uri is never turned into a path by appending json`() {
        // A `content://` document id is not a path: `…/123.json` resolves to nothing, so the
        // fallback path must stay identical and let the archive write be the only Android outcome.
        val uri = "content://com.android.providers.downloads.documents/document/123"
        assertEquals(uri, jsonSiblingPath(uri))
        assertEquals("file:///tmp/a.lumecard", jsonSiblingPath("file:///tmp/a.lumecard"))
    }

    @Test
    fun `archive always carries its manifest as the first entry`() = runBlocking {
        // The manifest is the archive's payload: an archive without it cannot be imported, so the
        // writer must never report success when the entry was skipped.
        writeMediaFile("payload.png", byteArrayOf(4, 2))
        val archiveOut = File(tempRoot, "manifest-check.lumecard").absolutePath

        val outcome = packageExport(
            manifest, listOf(card("payload.png")), archiveOut,
            File(tempRoot, "manifest-check.json").absolutePath,
        )

        assertTrue(outcome.isArchive)
        val names = assertNotNull(LumecardArchive.listEntries(archiveOut))
        assertEquals(LumecardArchive.ENTRY_MANIFEST, names.first(), "manifest must lead the archive")
        assertEquals(manifest, LumecardArchive.readManifest(archiveOut))
    }

    @Test
    fun `failed media writes are counted separately from missing`() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3)
        writeMediaFile("blocked.png", bytes)
        val archiveOut = File(tempRoot, "out.lumecard").absolutePath
        packageExport(
            manifest, listOf(card("blocked.png")), archiveOut,
            File(tempRoot, "o.json").absolutePath,
        )
        val names = assertNotNull(LumecardArchive.listEntries(archiveOut))

        // Fresh device, then squat a directory on the exact target path so the atomic write fails.
        File(getMediaBasePath()).deleteRecursively()
        File(getMediaBasePath()).mkdirs()
        assertTrue(File(getMediaBasePath(), "blocked.png").mkdirs(), "need an unwritable target")

        val outcome = importArchiveMedia(archiveOut, names, referencedPaths = listOf("blocked.png"))

        assertTrue(outcome.failed >= 1, "an unwritable media target must be reported as failed")
        assertEquals(0, outcome.missing, "the entry was in the archive, so it is not missing")
        assertEquals(0, outcome.written)
    }

    @Test
    fun `duplicate entry names are unpacked once`() = runBlocking {
        // ZipOutputStream refuses to write the same name twice, but a hand-built archive can hold
        // it (and some tools do). The entry list is the input the importer trusts, so deduplicate
        // there: counting it twice would report more files than actually landed on disk.
        val archive = File(tempRoot, "dup.lumecard").absolutePath
        ZipOutputStream(File(archive).outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(LumecardArchive.ENTRY_MANIFEST))
            zip.write(manifest.encodeToByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("media/twice.png"))
            zip.write(byteArrayOf(1, 2))
            zip.closeEntry()
        }
        val names = assertNotNull(LumecardArchive.listEntries(archive))
        val withDuplicate = names + "media/twice.png"

        val outcome = importArchiveMedia(archive, withDuplicate, referencedPaths = listOf("twice.png"))

        assertEquals(1, outcome.written, "the same entry name must not be written twice")
        assertEquals(0, outcome.failed)
        assertEquals(0, outcome.missing)
    }

    @Test
    fun `entry count is bounded so a hostile archive cannot demand unbounded work`() = runBlocking {
        // 10k is the cap; the overflow is reported as failed instead of being silently dropped.
        val archive = File(tempRoot, "flood.lumecard").absolutePath
        val total = 10_050
        ZipOutputStream(File(archive).outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(LumecardArchive.ENTRY_MANIFEST))
            zip.write(manifest.encodeToByteArray())
            zip.closeEntry()
            repeat(total) { i ->
                zip.putNextEntry(ZipEntry("media/n$i.png"))
                zip.write(byteArrayOf(0))
                zip.closeEntry()
            }
        }
        val names = assertNotNull(LumecardArchive.listEntries(archive))

        val outcome = importArchiveMedia(archive, names)

        assertTrue(outcome.failed >= 50, "entries past the cap must be reported, got ${outcome.failed}")
        assertTrue(outcome.written <= 10_000, "no more than the cap may be written")
    }

    @Test
    fun `newly written paths list exactly what the import created`() = runBlocking {
        // The caller needs this list to undo its own writes when the card insert fails afterwards.
        // Files that already existed must not appear, or a rollback would delete the user's media.
        writeMediaFile("fresh.png", byteArrayOf(1, 1))
        writeMediaFile("present.png", byteArrayOf(2, 2))
        val archive = File(tempRoot, "partial.lumecard").absolutePath
        ZipOutputStream(File(archive).outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(LumecardArchive.ENTRY_MANIFEST))
            zip.write(manifest.encodeToByteArray())
            zip.closeEntry()
            for (name in listOf("fresh.png", "present.png")) {
                zip.putNextEntry(ZipEntry("media/$name"))
                zip.write(if (name == "fresh.png") byteArrayOf(1, 1) else byteArrayOf(2, 2))
                zip.closeEntry()
            }
        }
        // Remove only `fresh.png` so the import has one file to create and one to skip.
        File(getMediaBasePath(), "fresh.png").delete()
        val names = assertNotNull(LumecardArchive.listEntries(archive))

        val outcome = importArchiveMedia(archive, names)

        assertEquals(1, outcome.written)
        assertEquals(1, outcome.skipped)
        assertEquals(listOf("fresh.png"), outcome.newlyWrittenPaths)
    }

    @Test
    fun `repeated import is idempotent`() = runBlocking {
        // Importing the same archive twice must not duplicate media or report phantom writes.
        writeMediaFile("same.png", byteArrayOf(3, 3, 3))
        val archiveOut = File(tempRoot, "idem.lumecard").absolutePath
        packageExport(
            manifest, listOf(card("same.png")), archiveOut,
            File(tempRoot, "idem.json").absolutePath,
        )
        val names = assertNotNull(LumecardArchive.listEntries(archiveOut))

        val first = importArchiveMedia(archiveOut, names)
        val second = importArchiveMedia(archiveOut, names)

        assertEquals(0, first.written)
        assertEquals(1, first.skipped, "the file was already on disk, so nothing is written")
        assertEquals(0, second.written)
        assertEquals(1, second.skipped)
        assertTrue(second.newlyWrittenPaths.isEmpty(), "a no-op import must not claim new files")
        val mediaFiles = File(getMediaBasePath()).listFiles().orEmpty().map { it.name }.sorted()
        assertEquals(listOf("same.png"), mediaFiles, "no duplicate copies may appear")
    }

    @Test
    fun `batch read returns the same bytes as single entry reads`() = runBlocking {
        // The importer now reads every entry in one pass (Android has no random access), so the
        // batch API must agree exactly with the per-entry one it replaced.
        writeMediaFile("one.png", byteArrayOf(1, 1, 1))
        writeMediaFile("two.png", byteArrayOf(2, 2, 2))
        val archiveOut = File(tempRoot, "batch.lumecard").absolutePath
        packageExport(
            manifest, listOf(card("one.png", "two.png")), archiveOut,
            File(tempRoot, "batch.json").absolutePath,
        )

        val batch = assertNotNull(
            readZipArchiveEntries(
                archiveOut,
                setOf("media/one.png", "media/two.png", "media/absent.png"),
                LumecardArchive.MAX_MEDIA_BYTES,
            )
        )

        assertEquals(setOf("media/one.png", "media/two.png"), batch.keys)
        assertEquals(
            LumecardArchive.readMedia(archiveOut, "one.png")?.toList(),
            batch["media/one.png"]?.toList(),
        )
        assertEquals(
            LumecardArchive.readMedia(archiveOut, "two.png")?.toList(),
            batch["media/two.png"]?.toList(),
        )
    }

    private fun assertContentEqualsSafe(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.toList(), actual.toList())
    }
}
