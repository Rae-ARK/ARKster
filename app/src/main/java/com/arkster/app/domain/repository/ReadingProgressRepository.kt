package com.arkster.app.domain.repository

/*
 * STAGE 6 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor (Repository +
 * Proxy). Full plan: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * Not one of the three repositories the refactor doc names explicitly, same
 * situation as ArcRepository - ReadingProgressDao was touched directly by
 * ReaderViewModel, HomeViewModel and LibrarySession, so it gets its own
 * repository rather than being left as the one Dao still reached past
 * domain/. Kept separate from NovelRepository rather than folded in: reading
 * progress is keyed by novelId but is its own table with its own lifecycle
 * (one row per novel, overwritten on every chapter-close/Previous/Next -
 * see ReaderViewModel.saveProgress), not a column on NovelEntity itself.
 */

import com.arkster.app.core.database.ReadingProgressDao
import com.arkster.app.core.database.ReadingProgressEntity

class ReadingProgressRepository(private val readingProgressDao: ReadingProgressDao) {
    suspend fun forNovel(novelId: String): ReadingProgressEntity? = readingProgressDao.forNovel(novelId)

    suspend fun upsert(progress: ReadingProgressEntity) = readingProgressDao.upsert(progress)

    // Backs "Continue Reading" - most recently read novels first. See
    // LibrarySession.refreshRecentlyRead, the one caller.
    suspend fun recentNovelIds(limit: Int = 10): List<String> = readingProgressDao.recentNovelIds(limit)
}
