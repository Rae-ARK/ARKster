package com.arkster.app.domain.booksource

/*
 * STAGE 5 (this patch) -- OOP modular-monolith refactor
 * Full plan: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * Promotes the unwired proof-of-concept at
 * spec/android_poc/src/main/kotlin/arkster/data/BookSource.kt into a real,
 * in-app interface. `architecture.md` previously had to correct an earlier
 * claim that this was "shipped" when it wasn't - everything in this file is
 * now real, compiled, and used by BookSourceFactory/FolderSource below, but
 * see FolderSource.kt's own header for exactly how far its wiring into the
 * existing scan pipeline (ScannerImpl.scanChaptersForNovel) goes in this
 * patch and what's deliberately left for a later stage - the same honesty
 * this correction asked for applies to this patch's own claims too.
 *
 * `Chapter`/`BookMetadata`/`SourceFingerprint` are intentionally the same
 * shape the POC used, not a direct mapping of ChapterEntity/NovelEntity -
 * this is the normalized model the interface promises callers, independent
 * of Room. A source-specific implementation (FolderSource today; EpubSource
 * once epub-support.md's Stage 1 lands) is responsible for producing it.
 */

/** Normalized model consumed by UI/ViewModel layers, regardless of source. */
data class Chapter(
    val id: String,
    val number: Int?,
    val title: String,
    val sortTier: Int,
    val sourcePath: String
)

data class BookMetadata(
    val id: String,
    val title: String,
    val author: String?,
    val coverUri: String?
)

data class SourceFingerprint(
    val uri: String,
    val lastModified: Long?,
    val size: Long?
)

/**
 * Source abstraction: one interface, with the concrete implementation chosen
 * by BookSourceFactory based on what's actually at the given path (a folder
 * today; an EPUB/PDF file once those formats get their own implementation).
 * Callers depend only on this interface, never on FolderSource/EpubSource
 * directly - the same Bridge the refactor doc's design-decisions section
 * describes.
 */
interface BookSource {
    suspend fun listChapters(): List<Chapter>
    suspend fun getMetadata(): BookMetadata
    suspend fun fingerprint(): SourceFingerprint
}

/**
 * A format BookSourceFactory recognized but has no BookSource implementation
 * for yet. Distinct from a file the factory doesn't recognize at all (a
 * stray README, a half-written temp file, etc.) - those stay silently
 * invisible, same as every format-less file at the scan root today. An
 * UnsupportedSource is a real, scan-root entry whose format ARKster knows
 * the name of but can't yet read - that's worth telling the user about
 * (see LibrarySession.unsupportedSources), where a wholly unrecognized file
 * is not.
 */
enum class UnsupportedFormatCode {
    EPUB,
    PDF
}

data class UnsupportedSource(
    val displayName: String,
    val uri: String,
    val code: UnsupportedFormatCode
) {
    /** Short, user-facing label for the dialog - "EPUB" / "PDF", not the enum name. */
    val formatLabel: String
        get() = when (code) {
            UnsupportedFormatCode.EPUB -> "EPUB"
            UnsupportedFormatCode.PDF -> "PDF"
        }
}
