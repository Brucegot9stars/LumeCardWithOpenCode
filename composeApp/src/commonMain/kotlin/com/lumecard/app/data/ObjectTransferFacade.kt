package com.lumecard.app.data

import com.lumecard.app.i18n.I18nManager
import com.lumecard.app.platform.getMediaBasePath
import com.lumecard.app.platform.pickSaveFile
import com.lumecard.app.platform.platformDeleteFile
import com.lumecard.app.platform.platformFileExists
import com.lumecard.app.platform.readFileContent
import com.lumecard.app.platform.readFileHeader
import com.lumecard.app.platform.resolveMediaFilePath
import com.lumecard.shared.data.ObjectBuildResult
import com.lumecard.shared.data.ObjectExport
import com.lumecard.shared.data.ObjectExportBuilder
import com.lumecard.shared.data.ObjectFileKind
import com.lumecard.shared.data.ObjectImportPlan
import com.lumecard.shared.data.ObjectImportPlanResult
import com.lumecard.shared.data.ObjectImportRequest
import com.lumecard.shared.data.ObjectImportResult
import com.lumecard.shared.data.ObjectImportService
import com.lumecard.shared.data.ObjectTransferError
import com.lumecard.shared.data.ObjectTransferException
import com.lumecard.shared.data.ObjectType
import com.lumecard.shared.model.MediaType
import com.lumecard.shared.repository.CardRepository
import com.lumecard.shared.repository.DeckRepository
import com.lumecard.shared.repository.KnowledgeBaseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** A validated scoped file waiting for the user's import decisions. */
data class ObjectPendingImport(
    val path: String,
    val isArchive: Boolean,
    val entryNames: List<String>,
    val export: ObjectExport,
    val type: ObjectType,
    val objectName: String,
)

sealed interface ObjectLoadOutcome {
    data class Success(val pending: ObjectPendingImport) : ObjectLoadOutcome
    data class Failure(val message: String) : ObjectLoadOutcome
}

sealed interface ObjectExportOutcome {
    data class Success(val message: String) : ObjectExportOutcome
    data class Failure(val message: String) : ObjectExportOutcome
    /** The user closed the save dialog. Callers must not show this as a result. */
    object Cancelled : ObjectExportOutcome
}

sealed interface ObjectRunOutcome {
    data class Success(val message: String) : ObjectRunOutcome
    data class Failure(val message: String) : ObjectRunOutcome
    /**
     * The transaction committed but the report could not be produced. The screen must clear its
     * pending state anyway, otherwise the user retries and imports everything twice.
     */
    data class Committed(val message: String) : ObjectRunOutcome
}

/** What the chosen options would do, before anything is written. */
data class ObjectImportPreview(
    val importedKnowledgeBases: Int,
    val importedDecks: Int,
    val importedCards: Int,
    val deletedKnowledgeBases: Int,
    val deletedDecks: Int,
    val deletedCards: Int,
    val skippedCards: Int,
    val renamed: Int,
) {
    val isDestructive: Boolean
        get() = deletedKnowledgeBases + deletedDecks + deletedCards > 0
}

sealed interface ObjectPreviewOutcome {
    data class Success(val preview: ObjectImportPreview) : ObjectPreviewOutcome
    data class Failure(val message: String) : ObjectPreviewOutcome
}

private data class ObjectImportRun(val plan: ObjectImportPlan, val result: ObjectImportResult)

/**
 * Platform-facing orchestration for scoped export/import.
 *
 * Keeps file I/O, archive media handling and rollback out of the composables. The database work
 * itself stays in [ObjectImportService] (one transaction); this facade only owns the media files,
 * which live outside that transaction and therefore need an explicit, symmetric rollback.
 */
class ObjectTransferFacade(
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val importService: ObjectImportService,
    private val i18nManager: I18nManager,
) {
    private val strings get() = i18nManager.strings

    /**
     * One import at a time. Two imports share the media root, so a rollback from one would delete
     * files the other has already committed. The UI also disables its button, but a double tap
     * can land before recomposition.
     */
    private val importMutex = Mutex()

    /** The save dialog is a process-wide singleton on Android, so exports must not overlap. */
    private val exportMutex = Mutex()

    /** Build and write a single-object export. */
    suspend fun export(type: ObjectType, rootIds: List<String>): ObjectExportOutcome =
        exportMutex.withLock {
            try {
                val kbs = knowledgeBaseRepository.getAll().first()
                val decks = deckRepository.getAll().first()
                val cards = cardRepository.getAll().first()
                val built = withContext(Dispatchers.IO) {
                    ObjectExportBuilder.build(type, rootIds, kbs, decks, cards, Clock.System.now().toString())
                }
                when (built) {
                    is ObjectBuildResult.Failure -> ObjectExportOutcome.Failure(
                        strings.objectTransferExportFailed(
                            strings.objectTransferErrorLabel(built.error.name)
                        )
                    )
                    is ObjectBuildResult.Success -> {
                        val data = built.data
                        val objectName = data.export.objectScope?.objectName
                            .orEmpty()
                            .ifBlank { strings.objectTransferTypeLabel(type.wire) }
                        val manifest = withContext(Dispatchers.IO) { ObjectExportBuilder.encode(data.export) }
                        val hasLocalMedia = withContext(Dispatchers.IO) {
                            LumecardArchive.referencedLocalMedia(data.payloadCards).isNotEmpty()
                        }
                        val suggested = "${safeFileName(objectName)}." +
                            if (hasLocalMedia) LumecardArchive.EXTENSION else "json"
                        val chosenPath = pickSaveFile(
                            suggested,
                            if (hasLocalMedia) LumecardArchive.MIME_TYPE else "application/json",
                        ) ?: return@withLock ObjectExportOutcome.Cancelled
                        // The write must finish even if the user navigates away: a cancelled zip
                        // leaves a file that cannot be opened and cannot be cleaned up.
                        val outcome = withContext(Dispatchers.IO + NonCancellable) {
                            packageExport(
                                manifestJson = manifest,
                                cards = data.payloadCards,
                                outputPath = chosenPath,
                                jsonOutputPath = jsonSiblingPath(chosenPath),
                            )
                        }
                        if (outcome.writeFailed || outcome.plainJsonWriteFailed) {
                            // Two distinct failures:
                            //  - the archive broke but a plain JSON landed (the user has a file,
                            //    just without images) — say where it went;
                            //  - nothing landed at all — naming a path there would be a lie.
                            // settingsExportArchiveFailed() is a full sentence, so wrapping it in
                            // objectTransferExportFailed() produced "Export failed: Export
                            // failed: ...".
                            val message = if (outcome.plainJsonWriteFailed) {
                                strings.objectTransferExportFailed(
                                    strings.objectTransferErrorLabel(ObjectTransferError.WRITE_FAILED.name)
                                )
                            } else {
                                strings.settingsExportArchiveFailed() + " · " + outcome.path
                            }
                            ObjectExportOutcome.Failure(message)
                        } else {
                            var message = strings.objectTransferExportSuccess(objectName, outcome.path)
                            // A silent "success" that dropped every image is the one outcome a
                            // user must not discover later on another device.
                            if (outcome.mediaMissing > 0) {
                                message += " · ${strings.objectTransferExportMediaMissing(outcome.mediaMissing)}"
                            }
                            // Archives only carry images today; say so instead of pretending the
                            // audio travelled with the deck.
                            val nonImage = data.payloadCards.sumOf { card ->
                                card.media.count {
                                    it.type != MediaType.IMAGE && !it.filePath.contains("://")
                                }
                            }
                            if (nonImage > 0) {
                                message += " · ${strings.objectTransferNonImageSkipped(nonImage)}"
                            }
                            ObjectExportOutcome.Success(message)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e !is ObjectTransferException) println("[LumeCard] ObjectTransferFacade export failed: ${e.message}")
                ObjectExportOutcome.Failure(
                    strings.objectTransferExportFailed(strings.errorUnknown)
                )
            }
        }
    /** Read and validate a picked `.json` / `.lumecard` file without touching the database. */
    suspend fun load(path: String): ObjectLoadOutcome {
        val s = strings
        return try {
            val isArchive = withContext(Dispatchers.IO) {
                readFileHeader(path, 4)?.let { LumecardArchive.looksLikeZip(it) } == true
            }
            val entryNames = if (isArchive) {
                withContext(Dispatchers.IO) { LumecardArchive.listEntries(path).orEmpty() }
            } else emptyList()
            val json = withContext(Dispatchers.IO) {
                if (isArchive) LumecardArchive.readManifest(path) else readFileContent(path)
            }
            if (json == null) {
                return ObjectLoadOutcome.Failure(s.objectTransferImportFailed(s.settingsImportErrorReadFile))
            }
            // A zip that yields no entry names is damaged or sits behind a provider that cannot
            // re-open it; continuing would import dead media references and still report success.
            if (isArchive && entryNames.isEmpty()) {
                return ObjectLoadOutcome.Failure(
                    s.objectTransferImportFailed(
                        s.objectTransferErrorLabel(ObjectTransferError.READ_FAILED.name)
                    )
                )
            }
            // Parsing and validating a large manifest builds a full JsonElement tree plus the
            // object graph (and validates up to 100k cards). On the caller's thread that is an ANR
            // on Android and a frozen window on Desktop, so both steps stay off the UI thread.
            val kind = withContext(Dispatchers.IO) { ObjectExportBuilder.detect(json) }
            when (kind) {
                ObjectFileKind.UNREADABLE -> return ObjectLoadOutcome.Failure(
                    s.objectTransferImportFailed(
                        s.objectTransferErrorLabel(ObjectTransferError.INVALID_JSON.name)
                    )
                )
                ObjectFileKind.FULL -> return ObjectLoadOutcome.Failure(s.objectTransferFullRejected)
                ObjectFileKind.SCOPED -> Unit
            }
            val decoded = withContext(Dispatchers.IO) {
                val export = ObjectExportBuilder.decode(json) ?: return@withContext null
                export to ObjectExportBuilder.validate(export)
            }
            if (decoded == null) {
                return ObjectLoadOutcome.Failure(
                    s.objectTransferImportFailed(s.objectTransferErrorLabel(ObjectTransferError.INVALID_JSON.name))
                )
            }
            val (export, error) = decoded
            if (error != null) {
                return ObjectLoadOutcome.Failure(
                    s.objectTransferImportFailed(s.objectTransferErrorLabel(error.name))
                )
            }
            val scope = export.objectScope ?: return ObjectLoadOutcome.Failure(
                s.objectTransferImportFailed(
                    s.objectTransferErrorLabel(ObjectTransferError.UNKNOWN_OBJECT_TYPE.name)
                )
            )
            ObjectLoadOutcome.Success(
                ObjectPendingImport(
                    path = path,
                    isArchive = isArchive,
                    entryNames = entryNames,
                    export = export,
                    type = scope.type,
                    objectName = scope.objectName.ifBlank { s.objectTransferObjectName },
                )
            )
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            ObjectLoadOutcome.Failure(s.objectTransferImportFailed(s.errorUnknown))
        }
    }

    /**
     * Resolve the plan without touching media or the database.
     *
     * The screen calls this first so an overwrite can state exactly how many objects it will
     * delete instead of asking "are you sure?" with no numbers behind it.
     */
    suspend fun preview(
        pending: ObjectPendingImport,
        request: ObjectImportRequest,
    ): ObjectPreviewOutcome {
        val s = strings
        return try {
            val plan = withContext(Dispatchers.IO) {
                when (val planned = importService.plan(pending.export, request)) {
                    is ObjectImportPlanResult.Failure -> throw ObjectTransferException(planned.error)
                    is ObjectImportPlanResult.Success -> planned.plan
                }
            }
            // Seeds alone understate an overwrite by orders of magnitude: one knowledge-base seed
            // becomes that knowledge base's whole deck/card tree once the transaction expands it.
            val deletes = withContext(Dispatchers.IO) { importService.expandedDeleteCounts(plan) }
            ObjectPreviewOutcome.Success(
                ObjectImportPreview(
                    importedKnowledgeBases = plan.knowledgeBases.size,
                    importedDecks = plan.decks.size,
                    importedCards = plan.cards.size,
                    deletedKnowledgeBases = deletes.knowledgeBases,
                    deletedDecks = deletes.decks,
                    deletedCards = deletes.cards,
                    skippedCards = plan.skippedCards,
                    renamed = plan.renamed.size,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val message = if (e is ObjectTransferException) {
                s.objectTransferImportFailed(s.objectTransferErrorLabel(e.error.name))
            } else {
                s.objectTransferImportFailed(s.errorUnknown)
            }
            ObjectPreviewOutcome.Failure(message)
        }
    }

    /** Import archived media, plan, execute, clean up — or roll everything back. */
    suspend fun import(
        pending: ObjectPendingImport,
        request: ObjectImportRequest,
    ): ObjectRunOutcome = importMutex.withLock {
        val s = strings
        var mediaOutcome: MediaImportOutcome? = null
        var committed = false
        try {
            if (pending.isArchive) {
                val referenced = withContext(Dispatchers.IO) {
                    LumecardArchive.referencedLocalMediaFrom(
                        pending.export.cards.flatMap { c -> c.media.map { it.filePath to it.type.name } }
                    )
                }
                // Cancellation cannot interrupt the extraction loop (all blocking calls), so it
                // used to be delivered *after* the files were written but *before* this assignment
                // — leaving `mediaOutcome` null and every file untracked. Protect the write and
                // its result together, exactly as the database commit below is protected.
                mediaOutcome = withContext(Dispatchers.IO + NonCancellable) {
                    importArchiveMedia(pending.path, pending.entryNames, referenced)
                }
            }
            // Rewriting every card's media list is O(cards × media). At the 100k-card cap this
            // belongs on IO like load()/preview(); running it on the caller's Main thread froze the
            // UI and made the progress indicator useless.
            val effectiveExport = withContext(Dispatchers.IO) {
                ObjectExportBuilder.rewriteMediaPaths(
                    pending.export, mediaOutcome?.renamed.orEmpty()
                )
            }
            val writtenPaths = mediaOutcome?.newlyWrittenPaths.orEmpty()
            val run = withContext(Dispatchers.IO + NonCancellable) {
                val plan = when (val planned = importService.plan(effectiveExport, request)) {
                    is ObjectImportPlanResult.Failure -> throw ObjectTransferException(planned.error)
                    is ObjectImportPlanResult.Success -> planned.plan
                }
                // NonCancellable is mandatory here, not just defensive: `execute()` is a blocking
                // driver transaction that cannot be interrupted, so a cancellation would be
                // delivered *after* the commit but *before* this block returns. `committed` used to
                // be set after `withContext`, which left a window where the rows were in the
                // database while the catch below still deleted the media they reference.
                ObjectImportRun(plan, importService.execute(plan, writtenPaths)).also {
                    committed = true
                }
            }
            // Files this import wrote that nothing references (every card was a skipped duplicate)
            // are ours and can be removed; orphans owned by tombstones are kept on purpose.
            // The commit above is NonCancellable; cleaning up its leftovers must be too. Otherwise
            // a cancellation delivered right after the commit leaves files no card references.
            val removed = withContext(NonCancellable) {
                cleanupUnused(run.result.unusedNewMediaPaths)
            }
            ObjectRunOutcome.Success(buildSuccessMessage(run, mediaOutcome, removed))
        } catch (e: CancellationException) {
            // Navigating away mid-import: the transaction rolls back, so the media written for it
            // must go too — otherwise the files stay orphaned with no record that they exist.
            if (!committed) withContext(NonCancellable) { rollbackMedia(mediaOutcome) }
            throw e
        } catch (e: Throwable) {
            // Raw driver text is not localizable or actionable; log it and show a stable message.
            if (e !is ObjectTransferException) println("[LumeCard] ObjectTransferFacade import failed: ${e.message}")
            val message = if (e is ObjectTransferException) {
                s.objectTransferImportFailed(s.objectTransferErrorLabel(e.error.name))
            } else {
                s.objectTransferImportFailed(s.errorUnknown)
            }
            if (committed) {
                // Data landed; only the report failed. The screen must not offer a retry.
                ObjectRunOutcome.Committed(message)
            } else {
                failOutcome(mediaOutcome, message)
            }
        }
    }

    /** Roll back newly written media and report files that survived, instead of hiding them. */
    private suspend fun failOutcome(
        mediaOutcome: MediaImportOutcome?,
        message: String,
    ): ObjectRunOutcome {
        val leftovers = rollbackMedia(mediaOutcome)
        val text = if (leftovers > 0) {
            "$message · ${strings.objectTransferRollbackIncomplete(leftovers)}"
        } else {
            message
        }
        return ObjectRunOutcome.Failure(text)
    }

    private fun buildSuccessMessage(
        run: ObjectImportRun,
        mediaOutcome: MediaImportOutcome?,
        removedUnused: Int,
    ): String {
        val s = strings
        val r = run.result
        val p = run.plan
        return buildList {
            add(s.objectTransferImportSuccess(r.importedKnowledgeBases, r.importedDecks, r.importedCards))
            // An overwrite is destructive; the counts must be visible, not inferred.
            if (r.deletedKnowledgeBases + r.deletedDecks + r.deletedCards > 0) {
                add(s.objectTransferDeleted(r.deletedKnowledgeBases, r.deletedDecks, r.deletedCards))
            }
            if (p.skippedCards > 0) add(s.objectTransferSkipped(p.skippedCards))
            if (p.renamed.isNotEmpty()) add(s.objectTransferRenamed(p.renamed.size))
            if (r.scrubbedPlans > 0) add(s.objectTransferPlansScrubbed(r.scrubbedPlans))
            if (removedUnused > 0) add(s.objectTransferUnusedMediaRemoved(removedUnused))
            val missing = mediaOutcome?.missing ?: 0
            if (missing > 0) add(s.objectTransferMediaMissing(missing))
            val failed = mediaOutcome?.failed ?: 0
            if (failed > 0) add(s.objectTransferMediaFailed(failed))
        }.joinToString(" · ")
    }

    /** Delete only files this import created; returns how many could not be removed. */
    private suspend fun rollbackMedia(mediaOutcome: MediaImportOutcome?): Int {
        val paths = mediaOutcome?.newlyWrittenPaths.orEmpty()
        if (paths.isEmpty()) return 0
        return withContext(Dispatchers.IO) {
            paths.count { path ->
                val target = resolveMediaFilePath(getMediaBasePath(), path)
                // A `false` return also covers "already gone", so only count it when the file is
                // still there: on Desktop a locked or read-only file makes `File.delete()` return
                // false instead of throwing, which used to hide the leftover entirely.
                val deleted = runCatching { platformDeleteFile(target) }.getOrDefault(false)
                !deleted && runCatching { platformFileExists(target) }.getOrDefault(false)
            }
        }
    }

    /** Delete files this import wrote that no imported card references. Never user data. */
    private suspend fun cleanupUnused(paths: List<String>): Int {
        if (paths.isEmpty()) return 0
        return withContext(Dispatchers.IO) {
            paths.count { path ->
                runCatching { platformDeleteFile(resolveMediaFilePath(getMediaBasePath(), path)) }
                    .getOrDefault(false)
            }
        }
    }

    private fun safeFileName(name: String): String {
        val cleaned = buildString {
            name.forEach { ch ->
                append(if (ch in "/\\:*?\"<>|" || ch.code < 32) '_' else ch)
            }
        }.trim().take(48)
        return cleaned.ifEmpty { "object" }
    }
}
