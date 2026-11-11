package com.arkster.app.scanner

/*
 * STAGE 4 MOVE -- 2026-11-11
 * Extracted from ScannerImpl.kt (Facade + Chain of Responsibility + Builder
 * decomposition). Full plan:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * Behavior unchanged from ScannerImpl's private computeFingerprint() and its
 * CURRENT_SCAN_VERSION constant. CURRENT_SCAN_VERSION moves here with it,
 * rather than staying a ScannerImpl companion constant, because it is
 * exclusively fingerprint-comparison semantics - every one of the versioned
 * rationale comments below is about forcing a fingerprint to read as stale,
 * not about scanning behavior in general.
 */

import androidx.documentfile.provider.DocumentFile
import com.arkster.app.core.database.ScanFingerprintEntity
import java.util.UUID

class FingerprintCalculator {

    companion object {
        // Bumped because rescan logic changed from delete-all-reinsert to a diff-based
        // upsert, which is required for chapter_overrides to survive a rescan.
        // v4: fingerprint is now computed from actual file contents (see
        // computeFingerprint) instead of the novel folder's own lastModified/length,
        // which many SAF providers don't update on child-file changes. Bumping forces
        // every existing fingerprint to be treated as stale exactly once.
        // v5: parseChapter now classifies bonus/closing content into sort_tier (see
        // bugs.md Bug 2) - bumped so every already-scanned novel gets reparsed once
        // and picks up the new tiers, instead of sitting unclassified until its next
        // unrelated rescan.
        // v6: arc folders are now sorted numerically before ArcEntity.position is
        // assigned (see arcSortNumber/bugs.md Stage 5) instead of trusting SAF
        // listFiles() order. scanChaptersForNovel returns early on an unchanged
        // fingerprint *before* ever reaching arc detection, so an already-scanned
        // novel with arcs stored in the old, possibly-wrong order would never
        // self-correct without this bump forcing one more full rescan.
        const val CURRENT_SCAN_VERSION = 6
    }

    // Walks the novel folder (root + one level of arc subfolders, matching where
    // chapters actually live) and aggregates real file metadata, rather than trusting
    // the novel folder DocumentFile's own lastModified()/length(). Many SAF providers
    // leave a directory's own metadata unchanged when a file inside it is added,
    // edited, or removed, which would otherwise make the incremental-rescan check
    // silently miss changes. File count is tracked alongside size/lastModified so a
    // same-size rename (delete A, add B) still changes the fingerprint.
    fun computeFingerprint(novelFolder: DocumentFile, novelId: String): ScanFingerprintEntity {
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
}
