package com.arkster.app.domain.repository

/*
 * STAGE 6 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor (Repository +
 * Proxy). Full plan, rationale and what each later stage does:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md -- that doc
 * is scaffolding for the refactor itself, not a permanent fixture: see its
 * own header for what to do with this pointer once the doc is gone.
 *
 * Wraps ChapterDao + ChapterOverrideDao - the pair EffectiveChapters.kt
 * (deleted this patch) already combined, and the same pair every call site
 * that touched either one used to reach for AppDatabase.chapterDao()/
 * chapterOverrideDao() directly to get at. Folded into ONE repository,
 * not two, because every real caller needs both together (an "effective"
 * chapter is a scanned ChapterEntity plus whatever override sits on top of
 * it) - a split ChapterRepository/ChapterOverrideRepository pair would just
 * force every call site to inject both and coordinate them itself, which is
 * exactly the coordination this class exists to own in one place.
 *
 * getEffectiveChapters() is EffectiveChapters.kt's loadEffectiveChapters()
 * moved in verbatim (logic unchanged) - see that file's own old header,
 * which named this exact repository as its intended home. ReaderViewModel
 * and NovelDetailViewModel both call it so neither duplicates (or drifts
 * from) the override-merge rules.
 *
 * ScannerImpl is also a caller as of this patch (forNovel/upsert/delete) -
 * Stage 6 closes the Dao-access gap everywhere, not just in the ViewModel
 * layer: see the refactor doc's Stage 6 status note for why ScannerImpl's
 * own scan-time writes go through this repository too, instead of being
 * treated as "the data layer, so direct Dao access is fine there."
 */

import com.arkster.app.core.database.ChapterDao
import com.arkster.app.core.database.ChapterEntity
import com.arkster.app.core.database.ChapterOverrideDao
import com.arkster.app.core.database.ChapterOverrideEntity

data class EffectiveChapterData(
    val chapters: List<ChapterEntity>, // overrides already applied, in display order
    val overriddenChapterIds: Set<String>,
    val arcStartChapterIds: Set<String>
)

class ChapterRepository(
    private val chapterDao: ChapterDao,
    private val chapterOverrideDao: ChapterOverrideDao
) {
    // Natural scan order, no overrides applied - used where a caller needs the raw
    // scanned list to diff against (e.g. ChapterEditor's saveChapterEdits, comparing
    // an edited list back against what the scanner originally produced).
    suspend fun forNovel(novelId: String): List<ChapterEntity> = chapterDao.forNovel(novelId)

    suspend fun forArc(arcId: String): List<ChapterEntity> = chapterDao.forArc(arcId)

    suspend fun findById(chapterId: String): ChapterEntity? = chapterDao.findById(chapterId)

    suspend fun upsert(chapter: ChapterEntity) = chapterDao.upsert(chapter)

    suspend fun delete(chapterId: String) = chapterDao.delete(chapterId)

    suspend fun deleteForNovel(novelId: String) = chapterDao.deleteForNovel(novelId)

    suspend fun upsertOverride(override: ChapterOverrideEntity) = chapterOverrideDao.upsert(override)

    suspend fun deleteOverride(chapterId: String) = chapterOverrideDao.delete(chapterId)

    // Exposes the same join getEffectiveChapters() already does internally, for callers
    // that need the raw override rows themselves rather than chapters with overrides
    // already applied - see LibrarySession's rename-migration path (bugs.md "Folder
    // rename orphans a novel's data"), which re-points a matched old novel's overrides
    // at its new chapter ids one row at a time instead of merging them into a display list.
    suspend fun overridesForNovel(novelId: String): List<ChapterOverrideEntity> = chapterOverrideDao.forNovel(novelId)

    // Moved verbatim from EffectiveChapters.kt's loadEffectiveChapters(). See that
    // (now-deleted) file's header for why this merge - raw scanned chapters with
    // title/position overrides layered on top - lives in exactly one place rather
    // than being duplicated at each of its two call sites.
    suspend fun getEffectiveChapters(novelId: String): EffectiveChapterData {
        val raw = chapterDao.forNovel(novelId)
        val overrides = chapterOverrideDao.forNovel(novelId)
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
}
