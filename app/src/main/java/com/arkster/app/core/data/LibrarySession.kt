package com.arkster.app.core.data

/*
 * STAGE 3 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor. Full plan,
 * rationale and what each later stage does:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md -- that doc
 * is scaffolding for the refactor itself, not a permanent fixture: see its
 * own header for what to do with this pointer once the doc is gone.
 *
 * Moved verbatim (logic, not structure) from MainActivity.startScan/
 * refreshRecentlyRead, which used to populate `novels`/`inProgressNovels`/
 * `recentlyRead`/`scanProgress`/`scanMessage` as Activity-owned
 * mutableStateListOf/mutableStateOf fields - rebuilt from scratch, and
 * re-scanned from an empty list, on every single rotation. An App
 * (Application)-scoped singleton instead of a ViewModel on purpose: scan
 * state and the novel list are shared by two different destinations' worth
 * of ViewModels (HomeViewModel's auto-scan-on-launch AND SettingsViewModel's
 * manual Rescan both need to trigger the same scan and see the same
 * resulting list), and a ViewModel's lifecycle is scoped to one
 * NavBackStackEntry - it would be destroyed and lose this state the moment
 * the user navigated away from whichever screen happened to own it. This
 * class lives exactly as long as App does (Stage 2 precedent), so the
 * scan this triggers and its result both survive navigation and
 * rotation.
 *
 * StateFlow instead of Compose's mutableStateListOf/mutableStateOf, since
 * this class has no Compose/Activity dependency at all (by design - it's
 * constructed once in App.kt, same as db/scanner/prefsManager) - every
 * collector (HomeViewModel, FictionBrowseViewModel today) re-exposes these
 * the same reactive way regardless. That also means every mutation below
 * can run on whatever dispatcher ScannerImpl's callbacks happen to be on
 * instead of needing the withContext(Dispatchers.Main) hops
 * mutableStateListOf required in the old Activity-owned version - a
 * StateFlow's `.value`/`.update` are safe from any thread.
 *
 * STAGE 6 MOVE -- 2026-11-11
 * Took `db: AppDatabase` before this patch and called novelDao()/authorDao()/
 * readingProgressDao() directly - now takes NovelRepository/AuthorRepository/
 * ReadingProgressRepository instead, same "nothing above domain/ touches a
 * Dao directly" rule App.kt's other construction sites follow as of this
 * stage. scanner.scanChaptersForNovel's call site below also drops the `db`
 * argument it used to pass through - ScannerImpl now owns its own repository
 * collaborators (see that class's own STAGE 6 MOVE header).
 */

import android.net.Uri
import com.arkster.app.core.database.NovelEntity
import com.arkster.app.domain.booksource.UnsupportedSource
import com.arkster.app.domain.repository.AuthorRepository
import com.arkster.app.domain.repository.NovelRepository
import com.arkster.app.domain.repository.ReadingProgressRepository
import com.arkster.app.scanner.ScannerImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LibrarySession(
    private val novelRepository: NovelRepository,
    private val authorRepository: AuthorRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val scanner: ScannerImpl
) {
    private val _novels = MutableStateFlow<List<NovelEntity>>(emptyList())
    val novels: StateFlow<List<NovelEntity>> = _novels.asStateFlow()

    private val _inProgressNovels = MutableStateFlow<List<NovelEntity>>(emptyList())
    val inProgressNovels: StateFlow<List<NovelEntity>> = _inProgressNovels.asStateFlow()

    // Queried (recentNovelIds) and kept current the same as before, but still not
    // rendered by any screen - HomeScreen's call site never took a `recentlyRead`
    // param pre-Stage-3 either. Kept rather than dropped so a future "Recently Read"
    // row doesn't have to rediscover this query; see refreshRecentlyRead() below.
    private val _recentlyRead = MutableStateFlow<List<NovelEntity>>(emptyList())
    val recentlyRead: StateFlow<List<NovelEntity>> = _recentlyRead.asStateFlow()

    private val _scanProgress = MutableStateFlow<Pair<Int, Int>?>(null) // (current, total) or null if not scanning
    val scanProgress: StateFlow<Pair<Int, Int>?> = _scanProgress.asStateFlow()

    private val _scanMessage = MutableStateFlow("")
    val scanMessage: StateFlow<String> = _scanMessage.asStateFlow()

    // STAGE 5 (this patch): files at the scan root whose format
    // BookSourceFactory recognized (.epub, .pdf) but ARKster has no
    // BookSource implementation for yet. Replaced wholesale on every scan
    // pass (not accumulated) so a file that's since been removed, or a
    // format that's since shipped real support, doesn't linger here forever.
    // HomeViewModel surfaces this as a dialog; see dismissUnsupportedSources
    // below for how it's cleared without waiting for the next scan.
    private val _unsupportedSources = MutableStateFlow<List<UnsupportedSource>>(emptyList())
    val unsupportedSources: StateFlow<List<UnsupportedSource>> = _unsupportedSources.asStateFlow()

    // Scans unattended on every app launch (via HomeViewModel.init) once a library
    // folder has been picked once, and again on demand from Settings' Rescan.
    // ScannerImpl already fails soft on SAF errors (revoked permissions, deleted
    // folders, etc), but this wraps the whole thing defensively too - a stray DB
    // exception here should never be allowed to crash the app. Worst case the user
    // sees a stale/partial library and can retry from Settings.
    suspend fun scan(treeUri: Uri) {
        // Tracks which novel IDs this scan pass actually found, so onScanCompleted
        // below can remove DB rows (and their cascaded arcs/chapters/overrides/
        // progress) for novels whose folder is genuinely gone - see bugs.md Bug 4. A
        // novel that hits the catch block below and never reaches the upsert is
        // deliberately NOT added here, matching the same accepted tradeoff
        // scanChaptersForNovel's seenChapterIds/seenArcIds already make for a single
        // skipped item.
        val seenNovelIds = mutableSetOf<String>()
        // See bugs.md Bug 6: set from onAuthorsDiscovered, which scanRoot always
        // fires before any per-novel onDiscovered below (same scan pass), then read
        // inside onDiscovered's merge. Defaults to true so a scan that never gets as
        // far as calling onAuthorsDiscovered at all (e.g. the early "folder no
        // longer accessible" returns in scanRoot) doesn't itself look like a missing
        // authors/.
        var authorsFolderFound = true
        val unsupportedThisPass = mutableListOf<UnsupportedSource>()
        try {
            scanner.scanRoot(
                treeUri,
                onDiscovered = { scanned, novelFolder ->
                    try {
                        // scanRoot builds a fresh NovelEntity every scan - pageSize and
                        // readingStatus are always defaults on it, and description/genres/
                        // publishedDate/author come from this novel's metadata.json if one
                        // exists (readLocalMetadata in ScannerImpl), null otherwise. Upsert()
                        // REPLACEs the whole row, so without carrying values over from the
                        // existing row, a rescan would silently wipe pageSize/readingStatus,
                        // and - once a remote "Fetch info" lookup has actually run for this
                        // novel (existing.metadataFetchedAt != null) - would let a rescan
                        // clobber that curated remote data with an empty/stale local
                        // metadata.json. Before any remote fetch, prefer this scan's freshly
                        // read metadata.json values (so editing the file and rescanning
                        // actually takes effect), falling back to whatever was already saved
                        // only for fields this scan didn't find a value for.
                        //
                        // authorId is deliberately NOT included in that fallback (unlike the
                        // free-text `author` right next to it): ScannerImpl resolves it fresh
                        // every single scan from that scan's authors/ folder contents (see
                        // "Linking a fiction to an author" in AUTHOR_PAGE_AND_CHAPTER_REDESIGN.md),
                        // not just from this fiction's own metadata.json - so unlike `author`/
                        // `description`/etc, a null `scanned.authorId` isn't "this scan didn't
                        // look," it's "this scan looked and the link no longer resolves" (the
                        // authors/<id>.json was deleted or renamed). Falling back to the old
                        // `existing.authorId` here would make a resolved author link permanent
                        // even after its source file is gone - the fiction page and chapter
                        // page would keep pointing at a dead author. Leaving it out of this
                        // copy() lets `scanned.authorId` (including null) win outright, the
                        // same way `coverUri` already does by simply not appearing in this
                        // copy() call's argument list at all.
                        val existing = novelRepository.findById(scanned.id)
                        val novel = if (existing != null) {
                            val remoteFetched = existing.metadataFetchedAt != null
                            scanned.copy(
                                pageSize = existing.pageSize,
                                readingStatus = existing.readingStatus,
                                author = scanned.author ?: existing.author,
                                // See bugs.md Bug 6: the long comment above explains why
                                // scanned.authorId normally wins outright instead of falling
                                // back like `author` does - but that's only true when this
                                // pass actually reached authors/. When it didn't
                                // (authorsFolderFound == false), ScannerImpl had nothing to
                                // resolve against, so scanned.authorId is null for every
                                // novel regardless of whether that novel's link is really
                                // gone. Keep the existing link in that case instead of
                                // wiping it.
                                authorId = if (authorsFolderFound) scanned.authorId else existing.authorId,
                                description = if (remoteFetched) existing.description else (scanned.description ?: existing.description),
                                genres = if (remoteFetched) existing.genres else (scanned.genres ?: existing.genres),
                                remoteCoverUrl = existing.remoteCoverUrl,
                                publishedDate = if (remoteFetched) existing.publishedDate else (scanned.publishedDate ?: existing.publishedDate),
                                externalSourceUrl = existing.externalSourceUrl,
                                metadataFetchedAt = existing.metadataFetchedAt
                            )
                        } else scanned
                        novelRepository.upsert(novel)
                        seenNovelIds.add(novel.id)
                        _novels.update { current ->
                            val idx = current.indexOfFirst { it.id == novel.id }
                            if (idx >= 0) current.toMutableList().also { it[idx] = novel } else current + novel
                        }
                        // Scan this novel's chapters/arcs using the folder scanRoot already
                        // resolved, instead of re-listing the root and searching by name again.
                        scanner.scanChaptersForNovel(novelFolder, novel.id) { message ->
                            _scanMessage.value = "Scanning ${novel.title}: $message"
                        }
                    } catch (e: Exception) {
                        _scanMessage.value = "Skipped ${scanned.title}: ${e.message}"
                    }
                },
                onAuthorsDiscovered = { discoveredAuthors, authorsFolderFoundThisPass ->
                    authorsFolderFound = authorsFolderFoundThisPass
                    discoveredAuthors.forEach { authorRepository.upsert(it) }
                    // See bugs.md Bug 6: only prune authors that scan didn't see this time
                    // when authors/ was actually found - an empty discoveredAuthors list
                    // because authorsFolder itself couldn't be found (transient SAF hiccup)
                    // must not be read as "every author was deleted", same reasoning as
                    // decideStaleNovels for novels (see ScanReconcile.kt).
                    if (authorsFolderFoundThisPass) {
                        val seenIds = discoveredAuthors.map { it.id }.toSet()
                        authorRepository.all().filter { it.id !in seenIds }.forEach { stale ->
                            authorRepository.delete(stale.id)
                        }
                    }
                },
                onScanCompleted = {
                    // See bugs.md Bug 4: this used to be a `novels.clear()` at the call
                    // site in Settings' onRescan, which cleared the UI list *before*
                    // scanning rather than reconciling it against a completed scan. That
                    // meant every manual rescan flashed the whole library to empty while
                    // novels trickled back in one at a time, and - worse - any novel this
                    // particular pass failed to rediscover (a transient SAF hiccup, the
                    // user backing out mid-scan, etc) stayed permanently missing from the
                    // UI even though its row was never touched in the DB. Doing the
                    // removal here instead, keyed off seenNovelIds and gated on a genuine
                    // onScanCompleted (never fired on an aborted/partial scan - see
                    // ScannerImpl.scanRoot), means the UI only ever loses a novel when
                    // this scan actually confirmed its folder is gone.
                    val staleIds = novelRepository.all().map { it.id }.filter { it !in seenNovelIds }.toSet()
                    staleIds.forEach { novelRepository.delete(it) }
                    if (staleIds.isNotEmpty()) {
                        _novels.update { current -> current.filterNot { it.id in staleIds } }
                    }
                },
                onUnsupportedSourceFound = { unsupported ->
                    unsupportedThisPass.add(unsupported)
                },
                onProgress = { current, total, message ->
                    _scanProgress.value = Pair(current, total)
                    _scanMessage.value = message
                }
            )
            _unsupportedSources.value = unsupportedThisPass
            refreshRecentlyRead()
            _scanProgress.value = null
            _scanMessage.value = ""
        } catch (e: Exception) {
            _scanProgress.value = null
            _scanMessage.value = "Library scan failed: ${e.message}"
        }
    }

    // Called after a scan completes and whenever reading progress changes
    // (NovelDetailViewModel.markChapterOpened, ReaderViewModel.saveProgress) - the
    // in-progress/recently-read lists are derived from reading_progress rows, which
    // nothing here observes reactively, so callers that just wrote one re-trigger
    // this explicitly rather than this class polling on a timer.
    suspend fun refreshRecentlyRead() {
        val ids = readingProgressRepository.recentNovelIds()
        val byId = _novels.value.associateBy { it.id }
        _recentlyRead.value = ids.mapNotNull { byId[it] }

        val inProgress = novelRepository.byStatus("IN_PROGRESS")
        _inProgressNovels.value = inProgress
    }

    // Lets the dialog be dismissed without waiting for the next scan pass to
    // naturally clear it - the next scan() call still rebuilds this list from
    // scratch regardless (see scan() above), so dismissing early doesn't lose
    // any file the next scan would otherwise have reported again.
    fun dismissUnsupportedSources() {
        _unsupportedSources.value = emptyList()
    }
}
