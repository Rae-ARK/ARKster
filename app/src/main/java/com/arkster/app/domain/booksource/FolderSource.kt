package com.arkster.app.domain.booksource

/*
 * STAGE 5 (this patch) -- OOP modular-monolith refactor
 * Full plan: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * The real FolderSource `architecture.md` previously had to say didn't
 * exist. Built on the exact Stage 4 collaborators ScannerImpl already uses
 * (ChapterTitleParser, CoverResolver, FingerprintCalculator), not a
 * reimplementation of their logic - so there is exactly one place each of
 * those rules (sort-tier markers, cover-match fallback, the fingerprint's
 * one-level-into-arcs walk) lives.
 *
 * WHAT THIS PATCH DOES NOT DO, stated plainly so this file doesn't become
 * the next thing a doc has to correct: ScannerImpl.scanChaptersForNovel
 * does not yet call through this class - it still walks root + arc
 * subfolders directly and upserts Room rows itself, because that method
 * also owns arc detection/ArcEntity upserts and the chapter_overrides-
 * preserving diff logic, none of which `BookSource.listChapters()`'s flat
 * `List<Chapter>` has anywhere to put (no `arcId`/`arcPosition` in the
 * normalized model). Routing the main scan pipeline through FolderSource
 * for real - the arc-aware version of listChapters(), and retiring
 * ScannerImpl's parseChaptersInFolder in its favor - is follow-on work, not
 * done here. What this patch does land: a real, standalone,
 * directly-constructible BookSource implementation for a flat novel folder
 * (no arcs), which is what Stage 5's own wording asked for
 * ("implement FolderSource for real against the scanning logic from Stage
 * 4") without silently expanding into the larger arc-aware rewrite nobody
 * asked for in this pass.
 */

import androidx.documentfile.provider.DocumentFile
import com.arkster.app.scanner.ChapterTitleParser
import com.arkster.app.scanner.CoverResolver
import com.arkster.app.scanner.FingerprintCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class FolderSource(
    private val folder: DocumentFile,
    private val novelId: String,
    private val chapterTitleParser: ChapterTitleParser = ChapterTitleParser(),
    private val coverResolver: CoverResolver = CoverResolver(),
    private val fingerprintCalculator: FingerprintCalculator = FingerprintCalculator()
) : BookSource {

    // Root-level chapter files only - matches ScannerImpl.parseChaptersInFolder's
    // own root-folder call (arcId = null). Arc subfolders are a separate concern
    // ScannerImpl still owns directly; see this file's header.
    override suspend fun listChapters(): List<Chapter> = withContext(Dispatchers.IO) {
        folder.listFiles()
            .filter { it.isFile && it.name?.matches(Regex("(?i).*\\.(txt|md)$")) == true }
            .map { file ->
                val parsed = chapterTitleParser.parse(file.name ?: "Unknown")
                Chapter(
                    id = UUID.nameUUIDFromBytes("${novelId}:${file.uri}".toByteArray()).toString(),
                    number = parsed.number,
                    title = parsed.title,
                    sortTier = parsed.sortTier,
                    sourcePath = file.uri.toString()
                )
            }
    }

    // Best-effort: folder name as title, CoverResolver for cover, no author (that
    // still comes from metadata.json/authors/ via NovelEntityBuilder/AuthorScanner,
    // which this class deliberately does not duplicate - see this file's header).
    override suspend fun getMetadata(): BookMetadata = withContext(Dispatchers.IO) {
        BookMetadata(
            id = novelId,
            title = folder.name ?: "Unknown",
            author = null,
            coverUri = coverResolver.findCoverUri(folder)
        )
    }

    override suspend fun fingerprint(): SourceFingerprint = withContext(Dispatchers.IO) {
        val entity = fingerprintCalculator.computeFingerprint(folder, novelId)
        SourceFingerprint(
            uri = entity.folderUri,
            lastModified = entity.lastModified,
            size = entity.size
        )
    }
}
