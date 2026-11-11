package horizonarkstudio.raeark.arkster.domain.repository

/*
 * STAGE 6 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor (Repository +
 * Proxy). Full plan: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * Not one of the three repositories the refactor doc names explicitly, and
 * unlike Arc/ReadingProgress above, this one has exactly one caller in the
 * entire app: ScannerImpl.scanChaptersForNovel, deciding whether a novel's
 * folder is unchanged since the last pass. It's still wrapped rather than
 * left as a direct Dao reference, because "everything" is what Stage 6 was
 * asked to cover - see the refactor doc's Stage 6 status note - and leaving
 * the one Dao nothing else touches as the sole exception would be exactly
 * the kind of inconsistency a future debugging session has to rediscover
 * the reasoning for. A single-caller repository is still a real seam: it's
 * the one place that would need to change if fingerprinting ever grew a
 * second consumer (e.g. a "last scanned" timestamp shown in Settings).
 */

import horizonarkstudio.raeark.arkster.core.database.ScanFingerprintDao
import horizonarkstudio.raeark.arkster.core.database.ScanFingerprintEntity

class ScanFingerprintRepository(private val scanFingerprintDao: ScanFingerprintDao) {
    suspend fun forNovel(novelId: String): ScanFingerprintEntity? = scanFingerprintDao.forNovel(novelId)

    suspend fun upsert(fingerprint: ScanFingerprintEntity) = scanFingerprintDao.upsert(fingerprint)

    suspend fun delete(novelId: String) = scanFingerprintDao.delete(novelId)
}
