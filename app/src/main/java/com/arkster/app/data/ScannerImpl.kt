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
        private const val CURRENT_SCAN_VERSION = 2

        private val ARC_PATTERNS = listOf(
            Regex("^(?:Arc|Volume|Book|Part)\\s*(\\d+)?", RegexOption.IGNORE_CASE),
            Regex("^(?:弧|卷|部)\\s*(\\d+)?", RegexOption.IGNORE_CASE)
        )
    }

    suspend fun scanRoot(treeUri: Uri, onDiscovered: suspend (NovelEntity) -> Unit) = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext
        val children = root.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                val title = child.name ?: "Unknown"
                val id = UUID.nameUUIDFromBytes((treeUri.toString() + ":" + child.uri.toString()).toByteArray()).toString()
                val coverUri = findCoverUri(child)
                val novel = NovelEntity(id = id, title = title, author = null, coverUri = coverUri)
                onDiscovered(novel)
            }
        }
    }

    suspend fun scanChaptersForNovel(novelFolder: DocumentFile, novelId: String, db: AppDatabase) = withContext(Dispatchers.IO) {
        val fingerprint = computeFingerprint(novelFolder, novelId)

        // Check if we can skip rescan (incremental optimization)
        val existingFingerprint = db.scanFingerprintDao().forNovel(novelId)
        if (existingFingerprint != null && existingFingerprint.scanVersion == CURRENT_SCAN_VERSION &&
            existingFingerprint.lastModified == fingerprint.lastModified && existingFingerprint.size == fingerprint.size) {
            // Unchanged, skip rescan
            return@withContext
        }

        // Clear old data for this novel
        db.chapterDao().deleteForNovel(novelId)
        db.arcDao().deleteForNovel(novelId)

        // Detect arcs (subfolders matching arc patterns)
        val arcs = mutableMapOf<String, ArcEntity>()
        val arcFolders = novelFolder.listFiles()
            .filter { it.isDirectory && isArcFolder(it.name ?: "") }
        arcFolders.forEachIndexed { idx, arcFolder ->
            val arcName = arcFolder.name ?: "Arc ${idx + 1}"
            val arcId = UUID.nameUUIDFromBytes("${novelId}:${arcFolder.uri}".toByteArray()).toString()
            val arcCoverUri = findCoverUri(arcFolder)
            val arc = ArcEntity(id = arcId, novelId = novelId, name = arcName, coverUri = arcCoverUri, position = idx)
            db.arcDao().upsert(arc)
            arcs[arcFolder.uri.toString()] = arc
        }

        // Parse chapter files from root and arc subfolders
        parseChaptersInFolder(novelFolder, novelId, null, db)
        for (arcFolder in arcFolders) {
            val arcId = arcs[arcFolder.uri.toString()]?.id
            parseChaptersInFolder(arcFolder, novelId, arcId, db)
        }

        // Save fingerprint
        db.scanFingerprintDao().upsert(fingerprint)
    }

    private suspend fun parseChaptersInFolder(folder: DocumentFile, novelId: String, arcId: String?, db: AppDatabase) {
        val files = folder.listFiles().filter { it.isFile && it.name?.matches(Regex("(?i).*\\.(txt|md)$")) == true }
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
            withContext(Dispatchers.IO) {
                db.chapterDao().upsert(chapter)
            }
        }
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

    private fun computeFingerprint(novelFolder: DocumentFile, novelId: String): ScanFingerprintEntity {
        val lastModified = novelFolder.lastModified()
        val size = try {
            novelFolder.length()
        } catch (e: Exception) {
            null
        }
        val fingerprintId = UUID.nameUUIDFromBytes("${novelId}:fingerprint".toByteArray()).toString()
        return ScanFingerprintEntity(
            id = fingerprintId,
            novelId = novelId,
            folderUri = novelFolder.uri.toString(),
            lastModified = if (lastModified > 0) lastModified else null,
            size = size,
            scanVersion = CURRENT_SCAN_VERSION
        )
    }

    private fun findCoverUri(folder: DocumentFile): String? {
        val imgs = folder.listFiles().filter { it.isFile && it.name?.matches(Regex("(?i)cover\\.(jpg|png|webp)$")) == true }
        return imgs.firstOrNull()?.uri?.toString()
    }
}
