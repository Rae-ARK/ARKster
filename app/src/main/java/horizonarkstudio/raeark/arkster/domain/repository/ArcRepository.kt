package horizonarkstudio.raeark.arkster.domain.repository

/*
 * STAGE 6 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor (Repository +
 * Proxy). Full plan: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * Not one of the three repositories the refactor doc names explicitly
 * (Novel/Chapter/Author), but ArcDao was touched directly by ScannerImpl,
 * ReaderViewModel and NovelDetailViewModel same as every Dao the named
 * three wrap - "nothing above domain/ touches a Dao directly after this
 * stage" doesn't carve out an exception for Arc, so it gets the same
 * treatment as a dedicated repository rather than being folded into
 * NovelRepository or ChapterRepository as a side effect of either.
 *
 * Kept separate from ChapterRepository on purpose even though chapters and
 * arcs are read together everywhere (ReaderViewModel/NovelDetailViewModel
 * both load both): an arc is a first-class concept, not a chapter concern -
 * see docs/arkster/foundational/architecture.md's "Arc semantics across
 * sources" for why its meaning (organizational comfort for a folder-scanned
 * web novel vs. the native volume unit for an EPUB/PDF) is worth a seam of
 * its own as EpubSource/PdfSource come online, rather than being an
 * implementation detail buried inside chapter access.
 */

import horizonarkstudio.raeark.arkster.core.database.ArcDao
import horizonarkstudio.raeark.arkster.core.database.ArcEntity

class ArcRepository(private val arcDao: ArcDao) {
    suspend fun forNovel(novelId: String): List<ArcEntity> = arcDao.forNovel(novelId)

    suspend fun upsert(arc: ArcEntity) = arcDao.upsert(arc)

    suspend fun delete(arcId: String) = arcDao.delete(arcId)

    suspend fun deleteForNovel(novelId: String) = arcDao.deleteForNovel(novelId)
}
