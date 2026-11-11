package com.arkster.app.scanner

/*
 * STAGE 1 MOVE -- 2026-11-11
 * Relocated here (package rename only, no logic touched) as part of the
 * staged OOP modular-monolith refactor. Full plan, rationale, and what
 * each later stage does: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * STAGE 4 MOVE -- 2026-11-11
 * This was the exact 660-line god object Stage 4 decomposes (Facade + Chain
 * of Responsibility + Builder - see the refactor doc's design-decisions
 * section). What moved out, and to where:
 *   - ChapterTitleParser.kt   <- parseChapter/ParsedChapter + tier constants (Chain
 *                                of Responsibility over a chapter filename)
 *   - CoverResolver.kt        <- findCoverUri
 *   - FingerprintCalculator.kt <- computeFingerprint + CURRENT_SCAN_VERSION
 *   - AuthorScanner.kt        <- findAuthorsFolder/scanAuthorsFolder/
 *                                readAuthorMetadata/avatar resolution, plus the
 *                                authorId-resolution logic that used to be
 *                                inlined in scanRoot's per-novel loop
 *   - NovelEntityBuilder.kt   <- readLocalMetadata/LocalMetadata + the Builder
 *                                that assembles a fresh NovelEntity (see that
 *                                file's header for what this Builder is and,
 *                                importantly, is NOT)
 * What's left here is exactly the thin facade the target layout described:
 * orchestrating a scan pass (walk the root, walk a novel's arcs/chapters,
 * decide what's stale) by calling the collaborators above, with no parsing/
 * resolution logic of its own left inline. Arc-folder detection/sorting
 * (isArcFolder/arcSortNumber, just below) stays here rather than becoming a
 * fifth collaborator - it's a few lines of folder-walk-level bookkeeping,
 * not parsing or resolution, and the design-decisions section only names
 * four collaborators for this stage.
 *
 * Same inputs and outputs as before this patch - scanRoot's and
 * scanChaptersForNovel's signatures, callback shapes, and the sequence of
 * callback firings are all unchanged; every caller (App.kt, LibrarySession)
 * needed no changes. This is a pure internal reorganization of one class,
 * same as Stage 1.
 */

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.arkster.app.core.database.AppDatabase
import com.arkster.app.core.database.ArcEntity
import com.arkster.app.core.database.AuthorEntity
import com.arkster.app.core.database.ChapterEntity
import com.arkster.app.core.database.NovelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Scanner facade for FolderSource-like behavior using SAF DocumentFile.
 * Enumerates immediate children, creates novel entries, parses chapters, and detects arcs.
 * Supports incremental rescan via fingerprinting. Delegates filename parsing, cover
 * resolution, author-folder parsing, fingerprinting, and NovelEntity assembly to the
 * focused collaborators listed above.
 */
class ScannerImpl(private val context: Context) {

    private val chapterTitleParser = ChapterTitleParser()
    private val coverResolver = CoverResolver()
    private val fingerprintCalculator = FingerprintCalculator()
    private val authorScanner = AuthorScanner(context)
    private val novelEntityBuilder = NovelEntityBuilder(context)

    companion object {
        private val ARC_PATTERNS = listOf(
            Regex("^(?:Arc|Volume|Book|Part)\\s*(\\d+)?", RegexOption.IGNORE_CASE),
            Regex("^(?:弧|卷|部)\\s*(\\d+)?", RegexOption.IGNORE_CASE)
        )
    }

    suspend fun scanRoot(
        treeUri: Uri,
        // Hands back the DocumentFile for the novel's own folder alongside the entity,
        // so callers (e.g. to then scan its chapters/arcs) don't need to re-list the
        // root and search for it by name again - that was previously redone once per
        // novel, making a full scan O(n^2) in the number of novels, and was fragile if
        // a folder happened to share a name with another entry.
        onDiscovered: suspend (NovelEntity, DocumentFile) -> Unit,
        // Fired exactly once per scanRoot call, after the root-level authors/ folder
        // (if any) has been parsed and before novel folders are iterated - callers do
        // the actual DB upsert/stale-removal themselves, same "ScannerImpl never holds
        // a db reference in scanRoot" style already used for onDiscovered above.
        //
        // The Boolean is true only when an authors/ folder actually exists at the root -
        // distinct from the list being empty, which is also true when the folder exists
        // but holds no profiles. A pass that couldn't find authors/ at all (transient SAF
        // hiccup) must not be read as "every author was deleted" - see
        // MainActivity.startScan.
        onAuthorsDiscovered: suspend (List<AuthorEntity>, Boolean) -> Unit = { _, _ -> },
        // Fired exactly once, only after the full children loop below has finished a
        // genuine, uninterrupted pass (i.e. NOT on the early SecurityException/
        // "folder no longer accessible" returns above it, and not per-child - a single
        // child throwing is caught and skipped without aborting the whole loop). This
        // is the caller's cue that it's safe to reconcile "which novels still exist"
        // against the DB - see MainActivity.startScan's seenNovelIds. Firing this on a
        // partial/aborted scan would let a transient SAF error wipe the entire library
        // instead of just leaving it stale until the next successful scan.
        //
        // The Boolean is true when the (post-authors/-filter) listing contained at least
        // one directory, regardless of whether building or upserting it later succeeded.
        // `false` should be read as "this listing may have failed", not "the library now
        // has zero novels" - a SAF provider can return an empty cursor while still
        // fetching, and nothing in listFiles()'s return value tells that apart from a
        // folder that really has no children. See ScanReconcile.decideStaleNovels.
        onScanCompleted: suspend (sawAnyNovelFolders: Boolean) -> Unit = {},
        // Fired for every directory child found in the listing, BEFORE any I/O that can
        // fail for that novel specifically (readLocalMetadata, findCoverUri, and
        // everything the caller's own onDiscovered does: merge, upsert, chapter scan).
        // Lets the caller mark the folder "seen" the moment it is found, instead of only
        // after its whole per-novel pipeline succeeds - otherwise a novel that throws
        // anywhere downstream of being found is indistinguishable, at onScanCompleted,
        // from one whose folder was really deleted.
        onNovelFolderSeen: suspend (novelId: String) -> Unit = {},
        onProgress: suspend (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        // Everything below touches the Storage Access Framework, which throws
        // SecurityException the moment a persisted grant is no longer valid (folder
        // moved/deleted, permission revoked by the system, app data partially wiped,
        // etc). This scan runs unattended on every app launch once a library has been
        // picked once (see MainActivity's libraryUri.collect), so an uncaught exception
        // here previously crashed the app on startup with no way to recover short of
        // clearing app data. Fail soft instead: surface it as a status message and stop
        // the scan for this call, rather than propagating and taking the whole app down.
        try {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: run {
                onProgress(0, 0, "Library folder is no longer accessible")
                return@withContext
            }
            if (!root.canRead()) {
                onProgress(0, 0, "Library folder permission was revoked")
                return@withContext
            }
            // Root-level authors/ folder, parsed once per scan (not per-novel) - see
            // AuthorScanner.scanAuthorsFolder for the id-collision/fail-soft rules. Built
            // before the novel loop below so each novel's metadata.json can be resolved
            // against it.
            val authorsFolder = authorScanner.findAuthorsFolder(root)
            val discoveredAuthors = if (authorsFolder != null) {
                authorScanner.scanAuthorsFolder(authorsFolder) { message ->
                    onProgress(0, 0, message)
                }
            } else emptyList()
            onAuthorsDiscovered(discoveredAuthors, authorsFolder != null)
            val authorsById = discoveredAuthors.associateBy { it.id }
            // Fallback lookup for fictions that set a free-text `author` but no
            // `authorId` - first author with a given normalized name wins ties, same as
            // the id-collision rule in AuthorScanner.scanAuthorsFolder.
            val authorIdByNormalizedName = mutableMapOf<String, String>()
            discoveredAuthors.forEach { authorIdByNormalizedName.putIfAbsent(it.name.trim().lowercase(), it.id) }

            val children = root.listFiles()
                // The authors/ folder holds author profiles, not a fiction - never treat
                // it as a novel folder even though it lives at the same level.
                .filterNot { it.isDirectory && it.name?.equals("authors", ignoreCase = true) == true }
            val total = children.size
            var sawAnyNovelFolders = false
            children.forEachIndexed { index, child ->
                onProgress(index + 1, total, "Scanning ${child.name}...")
                if (child.isDirectory) {
                    sawAnyNovelFolders = true
                    // Pure URI hashing, no I/O - so this can't itself be the thing that
                    // fails. Marked seen before the try block so a throw anywhere inside
                    // still leaves this id recorded as "this novel exists".
                    val id = novelIdFor(treeUri, child)
                    onNovelFolderSeen(id)
                    try {
                        val title = child.name ?: "Unknown"
                        val coverUri = coverResolver.findCoverUri(child)
                        // Optional per-novel metadata.json in the novel's own folder, next to
                        // cover.* and the chapter/arc folders - see
                        // NovelEntityBuilder.readLocalMetadata for the schema. Lets a user set
                        // title/author/description/genres/publishedDate by hand (or via a
                        // script) without needing the "Fetch info" lookup, which only searches
                        // Google Books and mostly won't have web novels.
                        val localMetadata = novelEntityBuilder.readLocalMetadata(child)
                        // Resolution order (see "Linking a fiction to an author" in
                        // docs/arkster/foundational/author-and-reader-model.md): explicit
                        // authorId first, then a case-insensitive name fallback, then no
                        // link at all.
                        val resolvedAuthorId = authorScanner.resolveAuthorId(
                            localAuthorId = localMetadata?.authorId,
                            localAuthorName = localMetadata?.author,
                            authorsById = authorsById,
                            authorIdByNormalizedName = authorIdByNormalizedName
                        )
                        val novel = novelEntityBuilder.build(
                            id = id,
                            folderTitle = title,
                            coverUri = coverUri,
                            localMetadata = localMetadata,
                            resolvedAuthorId = resolvedAuthorId
                        )
                        onDiscovered(novel, child)
                    } catch (e: Exception) {
                        // Don't let one bad/inaccessible novel folder abort the whole scan.
                        onProgress(index + 1, total, "Skipped ${child.name}: ${e.message}")
                    }
                }
            }
            onScanCompleted(sawAnyNovelFolders)
            onProgress(total, total, "Scan complete")
        } catch (e: SecurityException) {
            onProgress(0, 0, "Lost access to the library folder - please reselect it")
        } catch (e: Exception) {
            onProgress(0, 0, "Scan failed: ${e.message}")
        }
    }

    // Pure URI hashing, no I/O. Pulled out so scanRoot can compute a novel's id (to fire
    // onNovelFolderSeen) before running any of that novel's own I/O, with the same
    // derivation the rest of the scan uses - the two can never drift into different ids
    // for the same folder.
    private fun novelIdFor(treeUri: Uri, child: DocumentFile): String =
        UUID.nameUUIDFromBytes((treeUri.toString() + ":" + child.uri.toString()).toByteArray()).toString()

    suspend fun scanChaptersForNovel(
        novelFolder: DocumentFile,
        novelId: String,
        db: AppDatabase,
        onProgress: suspend (message: String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
      try {
        onProgress("Checking fingerprint...")
        val fingerprint = fingerprintCalculator.computeFingerprint(novelFolder, novelId)

        // Check if we can skip rescan (incremental optimization)
        val existingFingerprint = db.scanFingerprintDao().forNovel(novelId)
        if (existingFingerprint != null && existingFingerprint.scanVersion == FingerprintCalculator.CURRENT_SCAN_VERSION &&
            existingFingerprint.lastModified == fingerprint.lastModified &&
            existingFingerprint.size == fingerprint.size &&
            existingFingerprint.fileCount == fingerprint.fileCount) {
            // Unchanged, skip rescan
            onProgress("No changes detected")
            return@withContext
        }

        // Diff-based rescan: chapter IDs are deterministic (hashed from novelId + file URI),
        // so upserting a chapter that already exists overwrites it in place without a
        // delete step, which means chapter_overrides (FK CASCADE on chapter_id) is never
        // touched for files that are still present. Overrides only disappear if their
        // underlying chapter file has actually been removed - see removal pass below.
        // This matches the design rule: manual overrides persist across rescans unless
        // the source file itself is gone.

        // Detect arcs (subfolders matching arc patterns) and upsert them in place.
        //
        // listFiles() on a SAF DocumentFile makes no ordering guarantee at all - most
        // providers return entries in filesystem/creation order, not alphabetically -
        // so without an explicit sort, "Arc 2 - Whatever" could easily enumerate before
        // "Arc 1 - Whatever". ArcEntity.position (assigned by index right below) then
        // bakes that arbitrary order in permanently, which is what was surfacing as
        // arcs listed out of order in the fiction page's tabs/table of contents. Sort
        // explicitly by the numeric arc index ARC_PATTERNS already captures (e.g. "2"
        // out of "Arc 2 - Whatever"), numerically rather than lexicographically so
        // "Arc 10" doesn't sort before "Arc 2" - a plain string sort would get that
        // wrong the same way the old unsorted listFiles() order could. Folders that
        // match an arc pattern but have no captured number (just "Arc" / "Volume" with
        // nothing after it) fall back to sorting after every numbered arc, by name.
        onProgress("Detecting arcs...")
        val arcFolders = novelFolder.listFiles()
            .filter { it.isDirectory && isArcFolder(it.name ?: "") }
            .sortedWith(
                compareBy(
                    { arcSortNumber(it.name ?: "") ?: Int.MAX_VALUE },
                    { it.name ?: "" }
                )
            )
        val arcs = mutableMapOf<String, ArcEntity>()
        val seenArcIds = mutableSetOf<String>()
        arcFolders.forEachIndexed { idx, arcFolder ->
            val arcName = arcFolder.name ?: "Arc ${idx + 1}"
            val arcId = UUID.nameUUIDFromBytes("${novelId}:${arcFolder.uri}".toByteArray()).toString()
            val arcCoverUri = coverResolver.findCoverUri(arcFolder)
            val arc = ArcEntity(id = arcId, novelId = novelId, name = arcName, coverUri = arcCoverUri, position = idx)
            db.arcDao().upsert(arc)
            arcs[arcFolder.uri.toString()] = arc
            seenArcIds.add(arcId)
        }
        // Remove arcs whose folder no longer exists (chapters under it fall back to
        // novel-level via the ON DELETE SET NULL foreign key rather than being deleted).
        val existingArcs = db.arcDao().forNovel(novelId)
        existingArcs.filter { it.id !in seenArcIds }.forEach { stale ->
            db.arcDao().delete(stale.id)
        }

        // Parse chapter files from root and arc subfolders, tracking which chapter IDs
        // are still present so we can remove only the ones that truly vanished.
        onProgress("Parsing chapters...")
        val seenChapterIds = mutableSetOf<String>()
        seenChapterIds += parseChaptersInFolder(novelFolder, novelId, null, db)
        for (arcFolder in arcFolders) {
            val arcId = arcs[arcFolder.uri.toString()]?.id
            seenChapterIds += parseChaptersInFolder(arcFolder, novelId, arcId, db)
        }
        val existingChapters = db.chapterDao().forNovel(novelId)
        existingChapters.filter { it.id !in seenChapterIds }.forEach { stale ->
            db.chapterDao().delete(stale.id)
        }

        // Save fingerprint
        onProgress("Saving fingerprint...")
        db.scanFingerprintDao().upsert(fingerprint)
      } catch (e: SecurityException) {
          // Permission to this novel's folder was revoked mid-scan - skip it instead of
          // taking down the whole library scan (and the app) with it.
          onProgress("Lost access to this novel's folder, skipping")
      } catch (e: Exception) {
          onProgress("Failed to scan this novel: ${e.message}")
      }
    }

    private suspend fun parseChaptersInFolder(
        folder: DocumentFile,
        novelId: String,
        arcId: String?,
        db: AppDatabase
    ): List<String> = withContext(Dispatchers.IO) {
        val files = folder.listFiles().filter { it.isFile && it.name?.matches(Regex("(?i).*\\.(txt|md)$")) == true }
        val ids = mutableListOf<String>()
        files.forEachIndexed { idx, file ->
            val parsed = chapterTitleParser.parse(file.name ?: "Chapter ${idx + 1}")
            val chapterId = UUID.nameUUIDFromBytes("${novelId}:${file.uri}".toByteArray()).toString()
            val chapter = ChapterEntity(
                id = chapterId,
                novelId = novelId,
                arcId = arcId,
                number = parsed.number,
                title = parsed.title,
                sortTier = parsed.sortTier,
                sourcePath = file.uri.toString()
            )
            db.chapterDao().upsert(chapter)
            ids.add(chapterId)
        }
        ids
    }

    private fun isArcFolder(folderName: String): Boolean {
        return ARC_PATTERNS.any { it.containsMatchIn(folderName) }
    }

    // Extracts the numeric arc index ARC_PATTERNS captures (e.g. 2 from "Arc 2 -
    // Whatever"/"Volume 2"/"卷2"), trying each pattern in order and returning the
    // first captured number found. Null when the folder matches an arc pattern but
    // has no number after it (e.g. a bare "Arc" folder) - callers sort those last.
    private fun arcSortNumber(folderName: String): Int? {
        for (pattern in ARC_PATTERNS) {
            val match = pattern.find(folderName) ?: continue
            val number = match.groupValues.getOrNull(1)?.toIntOrNull()
            if (number != null) return number
        }
        return null
    }
}
