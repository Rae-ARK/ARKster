package horizonarkstudio.raeark.arkster.domain.repository

/*
 * STAGE 6 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor (Repository +
 * Proxy). Full plan: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * Wraps NovelDao, one of the three repositories the refactor doc names
 * explicitly. Every method here is a thin pass-through by design - this
 * class exists to be the one place that holds an `AppDatabase`/`NovelDao`
 * reference above `domain/`, not to add business logic of its own. The
 * merge/precedence logic that decides *what* to write during a scan still
 * lives in LibrarySession (the existing-vs-scanned-vs-override rules around
 * its `db.novelDao().upsert()` call) and NovelEntityBuilder - this
 * repository only owns the actual read/write, same division of
 * responsibility `ChapterContentRepository` already modeled pre-Stage-6.
 */

import horizonarkstudio.raeark.arkster.core.database.NovelDao
import horizonarkstudio.raeark.arkster.core.database.NovelEntity

class NovelRepository(private val novelDao: NovelDao) {
    suspend fun findById(novelId: String): NovelEntity? = novelDao.findById(novelId)

    suspend fun all(): List<NovelEntity> = novelDao.all()

    suspend fun upsert(novel: NovelEntity) = novelDao.upsert(novel)

    suspend fun delete(novelId: String) = novelDao.delete(novelId)

    suspend fun updatePageSize(novelId: String, pageSize: Int) = novelDao.updatePageSize(novelId, pageSize)

    suspend fun updateReadingStatus(novelId: String, status: String) = novelDao.updateReadingStatus(novelId, status)

    suspend fun byStatus(status: String): List<NovelEntity> = novelDao.byStatus(status)

    // Backs the author page's fiction list - see AuthorRepository/AuthorViewModel.
    suspend fun byAuthor(authorId: String): List<NovelEntity> = novelDao.byAuthor(authorId)

    suspend fun updateMetadata(
        novelId: String,
        description: String?,
        genres: String?,
        remoteCoverUrl: String?,
        publishedDate: String?,
        externalSourceUrl: String?,
        fetchedAt: Long
    ) = novelDao.updateMetadata(
        novelId = novelId,
        description = description,
        genres = genres,
        remoteCoverUrl = remoteCoverUrl,
        publishedDate = publishedDate,
        externalSourceUrl = externalSourceUrl,
        fetchedAt = fetchedAt
    )
}
