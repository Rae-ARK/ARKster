package com.arkster.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Scanner implementation for FolderSource-like behavior using SAF DocumentFile.
 * Enumerates immediate children, creates novel entries, parses chapters, and detects arcs.
 * Supports incremental rescan via fingerprinting.
 */
class ScannerImpl(private val context: Context) {

    companion object {
        // Bumped because rescan logic changed from delete-all-reinsert to a diff-based
        // upsert, which is required for chapter_overrides to survive a rescan.
        // v4: fingerprint is now computed from actual file contents (see
        // computeFingerprint) instead of the novel folder's own lastModified/length,
        // which many SAF providers don't update on child-file changes. Bumping forces
        // every existing fingerprint to be treated as stale exactly once.
        private const val CURRENT_SCAN_VERSION = 4

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
        onProgress: suspend (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext
        val children = root.listFiles()
        val total = children.size
        children.forEachIndexed { index, child ->
            onProgress(index + 1, total, "Scanning ${child.name}...")
            if (child.isDirectory) {
                val title = child.name ?: "Unknown"
                val id = UUID.nameUUIDFromBytes((treeUri.toString() + ":" + child.uri.toString()).toByteArray()).toString()
                val coverUri = findCoverUri(child)
                val novel = NovelEntity(id = id, title = title, author = null, coverUri = coverUri)
                onDiscovered(novel, child)
            }
        }
        onProgress(total, total, "Scan complete")
    }

    suspend fun scanChaptersForNovel(
        novelFolder: DocumentFile,
        novelId: String,
        db: AppDatabase,
        onProgress: suspend (message: String) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        onProgress("Checking fingerprint...")
        val fingerprint = computeFingerprint(novelFolder, novelId)

        // Check if we can skip rescan (incremental optimization)
        val existingFingerprint = db.scanFingerprintDao().forNovel(novelId)
        if (existingFingerprint != null && existingFingerprint.scanVersion == CURRENT_SCAN_VERSION &&
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
        onProgress("Detecting arcs...")
        val arcFolders = novelFolder.listFiles()
            .filter { it.isDirectory && isArcFolder(it.name ?: "") }
        val arcs = mutableMapOf<String, ArcEntity>()
        val seenArcIds = mutableSetOf<String>()
        arcFolders.forEachIndexed { idx, arcFolder ->
            val arcName = arcFolder.name ?: "Arc ${idx + 1}"
            val arcId = UUID.nameUUIDFromBytes("${novelId}:${arcFolder.uri}".toByteArray()).toString()
            val arcCoverUri = findCoverUri(arcFolder)
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
            val (number, title) = parseChapter(file.name ?: "Chapter ${idx + 1}")
            val chapterId = UUID.nameUUIDFromBytes("${novelId}:${file.uri}".toByteArray()).toString()
            val chapter = ChapterEntity(
                id = chapterId,
                novelId = novelId,
                arcId = arcId,
                number = number,
                title = title,
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

    private fun parseChapter(filename: String): Pair<Int?, String> {
        // Heuristic 1: leading number + delimiter
        val numberPattern = Regex("^\\s*(\\d+)\\s+(.+?)\\.(txt|md)$", RegexOption.IGNORE_CASE)
        val match = numberPattern.matchEntire(filename)
        if (match != null) {
            return Pair(match.groupValues[1].toIntOrNull(), match.groupValues[2].trim())
        }
        // Fallback: strip extension and normalize title
        val title = filename.replaceFirst(Regex("\\.(txt|md)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[_-]"), " ")
            .trim()
        return Pair(null, title)
    }

    // Walks the novel folder (root + one level of arc subfolders, matching where
    // chapters actually live) and aggregates real file metadata, rather than trusting
    // the novel folder DocumentFile's own lastModified()/length(). Many SAF providers
    // leave a directory's own metadata unchanged when a file inside it is added,
    // edited, or removed, which would otherwise make the incremental-rescan check
    // silently miss changes. File count is tracked alongside size/lastModified so a
    // same-size rename (delete A, add B) still changes the fingerprint.
    private fun computeFingerprint(novelFolder: DocumentFile, novelId: String): ScanFingerprintEntity {
        var fileCount = 0
        var maxLastModified = 0L
        var totalSize = 0L

        fun accumulateFiles(folder: DocumentFile) {
            folder.listFiles().forEach { entry ->
                if (entry.isFile) {
                    fileCount++
                    val entryModified = entry.lastModified()
                    if (entryModified > maxLastModified) maxLastModified = entryModified
                    totalSize += try {
                        entry.length()
                    } catch (e: Exception) {
                        0L
                    }
                }
                // Directories at this level are skipped here; they're walked explicitly
                // one level down below, matching where chapters actually live.
            }
        }
        // Root-level files, plus exactly one level into each subfolder (arc folders).
        // Deliberately NOT recursive - a genuinely recursive walk would keep descending
        // into arbitrarily deep folder trees the scanner never reads chapters from,
        // which both wastes time and would make the fingerprint sensitive to changes
        // scanChaptersForNovel() doesn't even look at.
        accumulateFiles(novelFolder)
        novelFolder.listFiles().filter { it.isDirectory }.forEach { accumulateFiles(it) }

        val fingerprintId = UUID.nameUUIDFromBytes("${novelId}:fingerprint".toByteArray()).toString()
        return ScanFingerprintEntity(
            id = fingerprintId,
            novelId = novelId,
            folderUri = novelFolder.uri.toString(),
            lastModified = if (maxLastModified > 0) maxLastModified else null,
            size = totalSize,
            fileCount = fileCount,
            scanVersion = CURRENT_SCAN_VERSION
        )
    }

    private fun findCoverUri(folder: DocumentFile): String? {
        val imgs = folder.listFiles().filter { it.isFile && it.name?.matches(Regex("(?i)cover\\.(jpg|png|webp)$")) == true }
        return imgs.firstOrNull()?.uri?.toString()
    }
}
