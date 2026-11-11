package com.arkster.app

/*
 * STAGE 2 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor. Full plan,
 * rationale and what each later stage does:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md -- that doc
 * is a scaffolding artifact for the refactor itself and is expected to be
 * deleted once Stage 7 ships; this comment (and the STAGE N markers on
 * every file this refactor has touched) will need to point somewhere else,
 * or just quote the relevant bit inline, once it's gone. Don't treat the
 * doc link as permanent.
 *
 * Owns db/scanner/prefsManager/contentRepo/metadataProvider for the
 * process's lifetime (Singleton), replacing the identical set of
 * `lateinit var`s MainActivity used to rebuild from scratch in onCreate()
 * on every single config change (rotation). That churn is exactly the
 * "config-change/process-death gap" the refactor doc opens with -- this
 * class closes the config-change half of it on its own, ahead of any
 * ViewModel work (Stage 3 closes the process-death half via
 * SavedStateHandle, because *this* class still dies with the process the
 * same as everything else did before it).
 *
 * Each property is `by lazy` rather than constructed eagerly here in
 * Application.onCreate(): MainActivity.onCreate() installs the crash
 * handler and the "did we crash last launch" check BEFORE it first touches
 * any of these, specifically so a bad AppDatabase.create() (stale Room
 * schema, corrupted kapt cache, etc.) shows the diagnostic crash screen
 * instead of a silent, undebuggable instant death with nothing on screen
 * (see the long comment at that call site in MainActivity.kt). Application
 * .onCreate() runs before any of that exists. Had these been constructed
 * eagerly here instead, the exact failure this app was hardened against
 * would again kill the process with nothing to show for it -- so laziness
 * here isn't a performance nicety, it's required for behavior parity with
 * pre-Stage-2 MainActivity. First access still happens inside MainActivity
 * onCreate()'s existing try/catch, same as before; what changes is that a
 * rotation no longer re-triggers that first access at all, because the
 * already-constructed instance now lives here instead of dying with the
 * Activity.
 */

import android.app.Application
import com.arkster.app.core.data.LibrarySession
import com.arkster.app.core.database.AppDatabase
import com.arkster.app.core.datastore.PreferencesManager
import com.arkster.app.domain.metadata.GoogleBooksMetadataProvider
import com.arkster.app.domain.repository.ArcRepository
import com.arkster.app.domain.repository.AuthorRepository
import com.arkster.app.domain.repository.ChapterRepository
import com.arkster.app.domain.repository.NovelRepository
import com.arkster.app.domain.repository.ReadingProgressRepository
import com.arkster.app.domain.repository.ScanFingerprintRepository
import com.arkster.app.domain.repository.TextChapterContentRepository
import com.arkster.app.scanner.ScannerImpl

class App : Application() {
    // STAGE 6 MOVE -- 2026-11-11: private as of this stage - every other property
    // below reaches the Daos it needs through a repository instead, and nothing
    // outside this class should still be able to get at a Dao directly (that's the
    // point of the stage). db itself still has to be constructed somewhere, and the
    // crash-guard reasoning in this file's header still applies to it specifically.
    private val db: AppDatabase by lazy { AppDatabase.create(this) }

    // The three repositories the refactor doc names explicitly, plus the four extra
    // Daos (Arc/ReadingProgress/ScanFingerprint, plus ChapterOverride folded into
    // ChapterRepository) that were reached past domain/ the same as the named three -
    // see each repository's own STAGE 6 MOVE header for why it wasn't folded into one
    // of the other three instead.
    val novelRepository: NovelRepository by lazy { NovelRepository(db.novelDao()) }
    val chapterRepository: ChapterRepository by lazy { ChapterRepository(db.chapterDao(), db.chapterOverrideDao()) }
    val authorRepository: AuthorRepository by lazy { AuthorRepository(db.authorDao()) }
    val arcRepository: ArcRepository by lazy { ArcRepository(db.arcDao()) }
    val readingProgressRepository: ReadingProgressRepository by lazy { ReadingProgressRepository(db.readingProgressDao()) }
    val scanFingerprintRepository: ScanFingerprintRepository by lazy { ScanFingerprintRepository(db.scanFingerprintDao()) }

    // STAGE 6 MOVE -- 2026-11-11: takes the repository collaborators it writes
    // through during a scan pass instead of `db` directly - see ScannerImpl's own
    // STAGE 6 MOVE header.
    val scanner: ScannerImpl by lazy { ScannerImpl(this, chapterRepository, arcRepository, scanFingerprintRepository) }
    val prefsManager: PreferencesManager by lazy { PreferencesManager(this) }
    val contentRepo: TextChapterContentRepository by lazy { TextChapterContentRepository(this) }

    // Stateless, can't throw, needs no Context - same "doesn't need the
    // guarded try/catch" reasoning that applied at its old call site in
    // MainActivity, just relocated: see the removed property's comment in
    // MainActivity.kt's history (git blame) if that reasoning is ever in
    // doubt.
    val metadataProvider: GoogleBooksMetadataProvider by lazy { GoogleBooksMetadataProvider() }

    // STAGE 3 MOVE -- 2026-11-11: joins the properties above for the same
    // Singleton reasoning, not because Stage 2 missed it - LibrarySession
    // (core/data/LibrarySession.kt) didn't exist until Stage 3 pulled the
    // novel list/scan-state fields out of MainActivity. `by lazy` here is
    // purely for construction-order consistency with db/scanner above (it
    // takes both as constructor args); unlike db specifically, it holds no
    // resource that can fail to open, so there's no crash-guard reason for
    // the laziness the way there is there.
    //
    // STAGE 6 MOVE -- 2026-11-11: takes novelRepository/authorRepository/
    // readingProgressRepository instead of `db` directly - see LibrarySession's own
    // STAGE 6 MOVE header.
    val librarySession: LibrarySession by lazy {
        LibrarySession(novelRepository, authorRepository, readingProgressRepository, scanner)
    }
}
