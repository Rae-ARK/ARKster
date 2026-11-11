package com.arkster.app.domain.repository

/*
 * STAGE 3 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor. Full plan,
 * rationale and what each later stage does:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md -- that doc
 * is scaffolding for the refactor itself, not a permanent fixture: see its
 * own header for what to do with this pointer once the doc is gone.
 *
 * Extracted from MainActivity.loadNovelDetails (chapter_overrides merge
 * logic, unchanged) rather than inlined into NovelDetailViewModel alone,
 * because ReaderViewModel needs the exact same effective, override-applied,
 * correctly-ordered chapter list to compute Previous/Next - pre-Stage-3,
 * both read the same `chapters` field MainActivity populated once via
 * loadNovelDetails(). Now that each destination has its own ViewModel
 * instance (NovelDetailViewModel for NovelDetail/ChapterEditor,
 * ReaderViewModel for Reader) with no shared Activity-held state between
 * them, this function is the one place that logic lives so both call the
 * same code instead of silently drifting apart. Stage 6 ("Repository layer
 * over Dao") is the natural home to fold this into a real
 * ChapterRepository; left as a plain top-level function in `domain/
 * repository/` for now rather than inventing that class early.
 */

import com.arkster.app.core.database.AppDatabase
import com.arkster.app.core.database.ChapterEntity

data class EffectiveChapterData(
    val chapters: List<ChapterEntity>, // overrides already applied, in display order
    val overriddenChapterIds: Set<String>,
    val arcStartChapterIds: Set<String>
)

suspend fun loadEffectiveChapters(db: AppDatabase, novelId: String): EffectiveChapterData {
    val raw = db.chapterDao().forNovel(novelId)
    val overrides = db.chapterOverrideDao().forNovel(novelId)
    val overridesByChapterId = overrides.associateBy { it.chapterId }
    val rawIndexById = raw.withIndex().associate { (i, c) -> c.id to i }

    val effective = raw
        .map { chapter ->
            val override = overridesByChapterId[chapter.id]
            if (override?.titleOverride != null) chapter.copy(title = override.titleOverride) else chapter
        }
        .sortedBy { chapter -> overridesByChapterId[chapter.id]?.positionOverride ?: rawIndexById[chapter.id] ?: 0 }

    return EffectiveChapterData(
        chapters = effective,
        overriddenChapterIds = overridesByChapterId.keys,
        arcStartChapterIds = overrides.filter { it.isArcStart }.map { it.chapterId }.toSet()
    )
}
