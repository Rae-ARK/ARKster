package com.arkster.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.arkster.app.BuildConfig
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.arkster.app.feature.settings.AboutScreen
import com.arkster.app.feature.noveldetail.ChapterEditorScreen
import com.arkster.app.feature.reader.ChapterLoadErrorScreen
import com.arkster.app.feature.author.AuthorPageScreen
import com.arkster.app.feature.home.HomeScreen
import com.arkster.app.feature.settings.LegalContent
import com.arkster.app.feature.settings.LegalDocumentScreen
import com.arkster.app.feature.noveldetail.MetadataSearchDialog
import com.arkster.app.feature.noveldetail.NovelDetailScreen
import com.arkster.app.feature.reader.ReaderScreen
import com.arkster.app.feature.settings.SettingsScreen
import com.arkster.app.feature.settings.ThemeSettingsScreen
import com.arkster.app.feature.splash.SplashScreen
import com.arkster.app.feature.browse.FictionBrowseScreen
import com.arkster.app.core.database.AppDatabase
import com.arkster.app.core.database.ArcEntity
import com.arkster.app.core.database.AuthorEntity
import com.arkster.app.core.database.ChapterOverrideEntity
import com.arkster.app.domain.metadata.GoogleBooksMetadataProvider
import com.arkster.app.domain.metadata.NovelMetadataCandidate
import com.arkster.app.core.database.ReadingProgressEntity
import com.arkster.app.scanner.ScannerImpl
import com.arkster.app.core.database.NovelEntity
import com.arkster.app.core.database.ChapterEntity
import com.arkster.app.core.datastore.PreferencesManager
import com.arkster.app.core.datastore.Theme
import com.arkster.app.core.database.NovelStatus
import com.arkster.app.domain.repository.TextChapterContentRepository
import com.arkster.app.domain.repository.ChapterLoadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class Screen {
    object Home : Screen()
    data class NovelDetail(val novel: NovelEntity) : Screen()
    data class Reader(val novelId: String, val chapter: ChapterEntity, val content: String) : Screen()
    // See bugs.md Bug 8: shown instead of Reader when getTextContent comes back
    // ChapterLoadResult.Failed, rather than opening the reader on an empty chapter.
    data class ChapterLoadError(val novelId: String, val chapter: ChapterEntity) : Screen()
    data class ChapterEditor(val novel: NovelEntity) : Screen()
    // Carries the previous screen so onBack can return to wherever the tap into the
    // author page came from (fiction page byline or chapter page's "About the author"
    // card) instead of always landing back on Home.
    data class Author(val authorId: String, val from: Screen) : Screen()
    object Settings : Screen()
    // Settings used to apply the Theme radio group inline on itself; now its own
    // destination, part of turning Settings into an index (see SettingsScreen.kt).
    object ThemeSettings : Screen()
    // New - Settings previously had no "About" entry at all (see AboutScreen.kt).
    object AboutMe : Screen()
    object FictionBrowse : Screen()
    object PrivacyPolicy : Screen()
    object TermsAndConditions : Screen()
}

// Drives the "Fetch info" dialog from NovelDetailScreen. Idle = dialog hidden.
sealed class MetadataSearchState {
    object Idle : MetadataSearchState()
    data class Loading(val novel: NovelEntity) : MetadataSearchState()
    data class Results(val novel: NovelEntity, val candidates: List<NovelMetadataCandidate>) : MetadataSearchState()
    data class Error(val novel: NovelEntity, val message: String) : MetadataSearchState()
}

// Warm, sepia-toned reading theme - lower contrast than pure light/dark, meant to
// be easier on the eyes for long reading sessions (a common e-reader "paper" mode).
//
// NOTE: primaryContainer/onPrimaryContainer are explicitly set here too. Every other
// role above overrides lightColorScheme()'s default, but these two were left out -
// so anything using them (e.g. HomeScreen's "Browse All Novels" card) fell back to
// Material3's stock light-purple/dark-purple pair, which clashes with this warm/sepia
// palette and is what made that button look inconsistent with the rest of the theme.
private fun warmPaperColorScheme() = lightColorScheme(
    primary = Color(0xFF8B5E34),
    onPrimary = Color(0xFFFFFBF5),
    primaryContainer = Color(0xFFD9BE97),
    onPrimaryContainer = Color(0xFF3E2C1C),
    background = Color(0xFFF5ECD9),
    onBackground = Color(0xFF3E2C1C),
    surface = Color(0xFFF0E4CB),
    onSurface = Color(0xFF3E2C1C),
    surfaceVariant = Color(0xFFE6D7B8),
    onSurfaceVariant = Color(0xFF4E3B26)
)

private fun colorSchemeFor(theme: Theme) = when (theme) {
    Theme.LIGHT -> lightColorScheme()
    Theme.DARK -> darkColorScheme()
    Theme.WARM_PAPER -> warmPaperColorScheme()
    // Never actually reached - resolveTheme() below always substitutes SYSTEM_DEFAULT
    // for a concrete Light/Dark theme before colorSchemeFor is called. Kept as an
    // exhaustive branch (falls back to the same lightColorScheme() Theme.LIGHT uses)
    // rather than `else`, so adding a future Theme case fails to compile here instead
    // of silently falling through.
    Theme.SYSTEM_DEFAULT -> lightColorScheme()
}

// Resolves "follow system theme" down to a concrete Light/Dark theme based on the
// device's own day/night setting. System Default has no palette of its own - it
// borrows Dark at night and Light during the day - so every caller that needs an
// actual color scheme or reading-mode palette (colorSchemeFor above, ReaderScreen's
// appTheme) resolves through this first rather than switching on SYSTEM_DEFAULT
// itself. Pulled out as a plain, Context-free function (same reasoning as the
// sibling ARKarium reader's own resolveTheme) so it's usable outside a Composable
// and easy to unit test later even though this module has no test source set yet.
internal fun resolveTheme(theme: Theme, systemInDarkTheme: Boolean): Theme =
    if (theme == Theme.SYSTEM_DEFAULT) {
        if (systemInDarkTheme) Theme.DARK else Theme.LIGHT
    } else {
        theme
    }

class MainActivity : ComponentActivity() {

    private val novels = mutableStateListOf<NovelEntity>()
    private val chapters = mutableStateListOf<ChapterEntity>() // overrides already applied
    private val arcs = mutableStateListOf<ArcEntity>()
    private val recentlyRead = mutableStateListOf<NovelEntity>()
    private val inProgressNovels = mutableStateListOf<NovelEntity>()
    private val overriddenChapterIds = mutableStateOf<Set<String>>(emptySet())
    private val arcStartChapterIds = mutableStateOf<Set<String>>(emptySet())
    // Author of the fiction currently open in the reader, resolved once when entering
    // Screen.Reader (see the two entry points below) rather than looked up reactively
    // per-recomposition - it doesn't change across a Previous/Next hop within the same
    // fiction, only when a *different* fiction's reader is opened. Null covers both
    // "not resolved yet" and "this fiction has no linked author" (see NovelEntity.authorId).
    private val readerAuthor = mutableStateOf<AuthorEntity?>(null)
    // Backs Screen.Author - reloaded via LaunchedEffect whenever the authorId changes,
    // same "state lives in the Activity, screen just renders it" pattern as chapters/arcs.
    private val authorPageAuthor = mutableStateOf<AuthorEntity?>(null)
    private val authorPageNovels = mutableStateListOf<NovelEntity>()
    private val currentScreen = mutableStateOf<Screen>(Screen.Home)
    // Gates the branded splash (see SplashScreen.kt) shown for a moment on every
    // cold launch before the real UI (Home, or wherever currentScreen already
    // points) becomes visible. Lives here rather than as a Screen case since it's
    // not a navigable destination - nothing ever sets currentScreen back to it.
    private val showSplash = mutableStateOf(true)
    private val currentTheme = mutableStateOf(Theme.LIGHT)
    private val scanProgress = mutableStateOf<Pair<Int, Int>?>(null)  // (current, total) or null if not scanning
    private val scanMessage = mutableStateOf("")
    private val metadataSearchState = mutableStateOf<MetadataSearchState>(MetadataSearchState.Idle)
    // STAGE 2 MOVE -- 2026-11-11: these five used to be this Activity's own
    // `lateinit var`s, constructed fresh in onCreate() below on every single
    // rotation. They now live on App (Application), which survives rotation,
    // so a config change no longer tears down and rebuilds the database
    // connection, re-walks nothing, etc. - see App.kt for the full reasoning,
    // including why App still constructs them lazily rather than eagerly.
    // `get()` instead of plain vals so every existing reference below keeps
    // working unchanged - that's the only reason these are typed out instead
    // of just writing `app.db` etc. at every call site.
    private val app: App get() = application as App
    private val db: AppDatabase get() = app.db
    private val scanner: ScannerImpl get() = app.scanner
    private val prefsManager: PreferencesManager get() = app.prefsManager
    private val contentRepo: TextChapterContentRepository get() = app.contentRepo
    private val metadataProvider: GoogleBooksMetadataProvider get() = app.metadataProvider

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { selectedUri ->
            contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            lifecycleScope.launch {
                prefsManager.setLibraryUri(selectedUri.toString())
                startScan(selectedUri)
            }
        }
    }

    private suspend fun startScan(treeUri: Uri) {
        // startScan runs unattended on every app launch once a library folder has been
        // picked once (see the libraryUri.collect below), with no user interaction in
        // between. ScannerImpl already fails soft on SAF errors (revoked permissions,
        // deleted folders, etc), but this wraps the whole thing defensively too - a
        // stray DB exception here should never be allowed to crash the app on startup;
        // worst case the user sees a stale/partial library and can retry from Settings.
        // Tracks which novel IDs this scan pass actually found, so onScanCompleted below
        // can remove DB rows (and their cascaded arcs/chapters/overrides/progress) for
        // novels whose folder is genuinely gone - see bugs.md Bug 4. A novel that hits
        // the catch block below and never reaches the upsert is deliberately NOT added
        // here, matching the same accepted tradeoff scanChaptersForNovel's
        // seenChapterIds/seenArcIds already make for a single skipped item.
        val seenNovelIds = mutableSetOf<String>()
        // See bugs.md Bug 6: set from onAuthorsDiscovered, which scanRoot always fires
        // before any per-novel onDiscovered below (same scan pass), then read inside
        // onDiscovered's merge. Defaults to true so a scan that never gets as far as
        // calling onAuthorsDiscovered at all (e.g. the early "folder no longer
        // accessible" returns in scanRoot) doesn't itself look like a missing authors/.
        var authorsFolderFound = true
        try {
            scanner.scanRoot(treeUri,
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
                        val existing = db.novelDao().findById(scanned.id)
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
                        db.novelDao().upsert(novel)
                        seenNovelIds.add(novel.id)
                        withContext(Dispatchers.Main) {
                            val idx = novels.indexOfFirst { it.id == novel.id }
                            if (idx >= 0) novels[idx] = novel else novels.add(novel)
                        }
                        // Scan this novel's chapters/arcs using the folder scanRoot already
                        // resolved, instead of re-listing the root and searching by name again.
                        scanner.scanChaptersForNovel(novelFolder, novel.id, db) { message ->
                            withContext(Dispatchers.Main) {
                                scanMessage.value = "Scanning ${novel.title}: $message"
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            scanMessage.value = "Skipped ${scanned.title}: ${e.message}"
                        }
                    }
                },
                onAuthorsDiscovered = { discoveredAuthors, authorsFolderFoundThisPass ->
                    authorsFolderFound = authorsFolderFoundThisPass
                    discoveredAuthors.forEach { db.authorDao().upsert(it) }
                    // See bugs.md Bug 6: only prune authors that scan didn't see this time
                    // when authors/ was actually found - an empty discoveredAuthors list
                    // because authorsFolder itself couldn't be found (transient SAF hiccup)
                    // must not be read as "every author was deleted", same reasoning as
                    // decideStaleNovels for novels (see ScanReconcile.kt).
                    if (authorsFolderFoundThisPass) {
                        val seenIds = discoveredAuthors.map { it.id }.toSet()
                        db.authorDao().all().filter { it.id !in seenIds }.forEach { stale ->
                            db.authorDao().delete(stale.id)
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
                    // removal here instead, keyed off seenNovelIds and gated on a
                    // genuine onScanCompleted (never fired on an aborted/partial scan -
                    // see ScannerImpl.scanRoot), means the UI only ever loses a novel
                    // when this scan actually confirmed its folder is gone.
                    val staleIds = db.novelDao().all().map { it.id }.filter { it !in seenNovelIds }.toSet()
                    staleIds.forEach { db.novelDao().delete(it) }
                    if (staleIds.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            novels.removeAll { it.id in staleIds }
                        }
                    }
                },
                onProgress = { current, total, message ->
                    withContext(Dispatchers.Main) {
                        scanProgress.value = Pair(current, total)
                        scanMessage.value = message
                    }
                }
            )
            refreshRecentlyRead()
            withContext(Dispatchers.Main) {
                scanProgress.value = null
                scanMessage.value = ""
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                scanProgress.value = null
                scanMessage.value = "Library scan failed: ${e.message}"
            }
        }
    }

    // Loads the raw scanned chapters for a novel and applies any saved chapter_overrides
    // (title/position) on top, so every screen downstream sees the "effective" chapter
    // list rather than raw scan data.
    private suspend fun loadNovelDetails(novel: NovelEntity) {
        val raw = db.chapterDao().forNovel(novel.id)
        val overrides = db.chapterOverrideDao().forNovel(novel.id)
        val overridesByChapterId = overrides.associateBy { it.chapterId }
        val rawIndexById = raw.withIndex().associate { (i, c) -> c.id to i }

        val effective = raw
            .map { chapter ->
                val override = overridesByChapterId[chapter.id]
                if (override?.titleOverride != null) chapter.copy(title = override.titleOverride) else chapter
            }
            .sortedBy { chapter -> overridesByChapterId[chapter.id]?.positionOverride ?: rawIndexById[chapter.id] ?: 0 }

        chapters.clear()
        chapters.addAll(effective)
        arcs.clear()
        arcs.addAll(db.arcDao().forNovel(novel.id))
        overriddenChapterIds.value = overridesByChapterId.keys
        arcStartChapterIds.value = overrides.filter { it.isArcStart }.map { it.chapterId }.toSet()
    }

    // Loads the AuthorEntity + every novel linked to it (NovelDao.byAuthor) for
    // Screen.Author. A missing/unknown authorId (author.json removed since the fiction
    // page or reader last resolved it) just leaves authorPageAuthor null - AuthorPage's
    // caller below treats that as "nothing to show" rather than crashing, same
    // never-fail-on-missing-optional-metadata guarantee the rest of the app follows.
    private suspend fun loadAuthorPage(authorId: String) {
        authorPageAuthor.value = db.authorDao().findById(authorId)
        authorPageNovels.clear()
        authorPageNovels.addAll(db.novelDao().byAuthor(authorId))
    }

    // See bugs.md Bug 8: getTextContent now returns a ChapterLoadResult instead of the
    // chapter text directly, so every entry point into the reader (initial open,
    // Previous/Next) routes through here instead of reaching into `.content.body`
    // itself and risks silently opening an empty chapter on a read failure.
    private suspend fun openChapter(novelId: String, chapter: ChapterEntity) {
        when (val result = contentRepo.getTextContent(chapter.sourcePath)) {
            is ChapterLoadResult.Ok -> currentScreen.value = Screen.Reader(novelId, chapter, result.content.body)
            is ChapterLoadResult.Failed -> currentScreen.value = Screen.ChapterLoadError(novelId, chapter)
        }
    }

    // Diffs the editor's edited chapter list against the raw scanned chapters and
    // persists per-chapter overrides only where something actually changed. If an edit
    // matches the scanned default again, any stale override for that chapter is removed
    // instead of being kept around with stale values.
    private suspend fun saveChapterEdits(novel: NovelEntity, edited: List<ChapterEntity>, arcStartIds: Set<String>) {
        val raw = db.chapterDao().forNovel(novel.id) // natural scan order, no overrides
        val rawById = raw.associateBy { it.id }
        val rawIndexById = raw.withIndex().associate { (i, c) -> c.id to i }

        edited.forEachIndexed { index, chapter ->
            val original = rawById[chapter.id] ?: return@forEachIndexed
            val titleOverride = if (chapter.title != original.title) chapter.title else null
            val positionOverride = if (index != rawIndexById[chapter.id]) index else null
            val isArcStart = chapter.id in arcStartIds

            if (titleOverride != null || positionOverride != null || isArcStart) {
                db.chapterOverrideDao().upsert(
                    ChapterOverrideEntity(
                        id = UUID.nameUUIDFromBytes("override:${chapter.id}".toByteArray()).toString(),
                        chapterId = chapter.id,
                        titleOverride = titleOverride,
                        positionOverride = positionOverride,
                        isArcStart = isArcStart
                    )
                )
            } else {
                db.chapterOverrideDao().delete(chapter.id)
            }
        }
        loadNovelDetails(novel)
    }

    private suspend fun saveReadingProgress(novelId: String, chapterId: String, progress: Float) {
        db.readingProgressDao().upsert(
            ReadingProgressEntity(
                novelId = novelId,
                chapterId = chapterId,
                position = progress,
                positionType = "PERCENTAGE",
                updatedAt = System.currentTimeMillis()
            )
        )
        refreshRecentlyRead()
    }

    private suspend fun refreshRecentlyRead() {
        val ids = db.readingProgressDao().recentNovelIds()
        val byId = novels.associateBy { it.id }
        recentlyRead.clear()
        recentlyRead.addAll(ids.mapNotNull { byId[it] })
        
        // Also refresh in-progress novels
        val inProgress = db.novelDao().byStatus("IN_PROGRESS")
        inProgressNovels.clear()
        inProgressNovels.addAll(inProgress)
    }

    // Kicks off a "Fetch info" search for one novel. User-triggered only (see
    // NovelDetailScreen's info action) - never called automatically during a scan.
    private fun fetchMetadataFor(novel: NovelEntity) {
        metadataSearchState.value = MetadataSearchState.Loading(novel)
        lifecycleScope.launch {
            try {
                val results = metadataProvider.search(novel.title)
                metadataSearchState.value = MetadataSearchState.Results(novel, results)
            } catch (e: Exception) {
                metadataSearchState.value = MetadataSearchState.Error(novel, "Couldn't reach the metadata source: ${e.message}")
            }
        }
    }

    // Persists a user-confirmed match and refreshes every in-memory copy of this novel
    // (the library list and, if it's the screen currently on screen, NovelDetail) so the
    // new info shows up immediately without navigating away and back.
    private fun applyMetadata(novel: NovelEntity, candidate: NovelMetadataCandidate) {
        lifecycleScope.launch {
            try {
                db.novelDao().updateMetadata(
                    novelId = novel.id,
                    description = candidate.description,
                    genres = candidate.categories.joinToString(", ").ifBlank { null },
                    remoteCoverUrl = candidate.thumbnailUrl,
                    publishedDate = candidate.publishedDate,
                    externalSourceUrl = candidate.infoLink,
                    fetchedAt = System.currentTimeMillis()
                )
                val updated = db.novelDao().findById(novel.id) ?: return@launch
                val idx = novels.indexOfFirst { it.id == updated.id }
                if (idx >= 0) novels[idx] = updated
                val screen = currentScreen.value
                if (screen is Screen.NovelDetail && screen.novel.id == updated.id) {
                    currentScreen.value = Screen.NovelDetail(updated)
                }
                metadataSearchState.value = MetadataSearchState.Idle
            } catch (e: Exception) {
                metadataSearchState.value = MetadataSearchState.Error(novel, "Couldn't save the fetched info: ${e.message}")
            }
        }
    }

    // Shared fallback UI for anything that throws before/while the real screen renders.
    // Pulled out so BOTH the service-construction guard, the setContent guard, and the
    // saved-crash check below can show the same "here's exactly what broke" screen
    // instead of a silent process death - a stack trace on-screen beats needing
    // adb/logcat to debug a phone-only crash report.
    private fun renderCrashScreen(title: String, details: String) {
        setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    // Same BuildConfig.VERSION_NAME used in Settings' About line -
                    // worth having on a crash report screen specifically, since "what
                    // version were they on" is the first thing a bug report needs.
                    Text(
                        "ARKster v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                        item {
                            Text(details, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    private fun renderCrashScreen(e: Throwable) {
        renderCrashScreen(
            "ARKster failed to start",
            (e::class.java.name + ": " + e.message) + "\n\n" +
                e.stackTrace.take(30).joinToString("\n") { "  at $it" }
        )
    }

    companion object {
        private const val CRASH_PREFS = "arkster_crash_info"
        private const val CRASH_KEY = "last_crash"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Safety net of last resort. The try/catch blocks below only catch exceptions
        // thrown synchronously on THIS call stack - they cannot catch anything thrown
        // inside a lifecycleScope.launch { } coroutine body (theme.collect, libraryUri.
        // collect, etc.), since those run on their own dispatcher and throw well after
        // the launch{} call that started them has already returned. An uncaught
        // exception there crashes the process instantly with nothing on screen and
        // nothing catchable here - which is exactly what "opens and immediately exits,
        // no error screen" looks like. Installing a global handler is the only way to
        // observe it: it can't stop the crash (the process still has to die), but it
        // persists the trace first so the NEXT launch can show it.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                getSharedPreferences(CRASH_PREFS, MODE_PRIVATE).edit()
                    .putString(
                        CRASH_KEY,
                        "ARKster v${BuildConfig.VERSION_NAME}\n\n" +
                            (throwable::class.java.name + ": " + throwable.message) + "\n\n" +
                            throwable.stackTrace.take(30).joinToString("\n") { "  at $it" }
                    )
                    .commit() // commit(), not apply() - must be on disk before the process dies
            } catch (_: Throwable) {
                // Never let the crash handler itself throw and mask the original crash.
            }
            previousHandler?.uncaughtException(thread, throwable)
        }

        // If the previous launch crashed, show that trace now instead of trying to
        // start normally again (which would likely just crash the same way a second
        // time with nothing new learned).
        val crashPrefs = getSharedPreferences(CRASH_PREFS, MODE_PRIVATE)
        val lastCrash = crashPrefs.getString(CRASH_KEY, null)
        if (lastCrash != null) {
            crashPrefs.edit().remove(CRASH_KEY).apply()
            renderCrashScreen("ARKster crashed last time it opened", lastCrash)
            return
        }

        // AppDatabase.create() (and the other service objects below) are the very first
        // things onCreate does, before setContent ever runs. Room.databaseBuilder(...).build()
        // in particular locates its generated *_Impl class via reflection at *runtime*, not
        // a compile-time link - a stale/corrupted incremental kapt cache (common after a CI
        // rebuild) can compile clean and still throw the instant this line runs, taking the
        // whole app down before a single frame is drawn - "installs fine, crashes on open,
        // never even shows a screen". Wrapping this means a failure here shows an error
        // screen with the real exception instead of a silent, undebuggable instant crash.
        //
        // STAGE 2 MOVE -- 2026-11-11: db/scanner/prefsManager/contentRepo are now `by
        // lazy` on App (see App.kt) instead of assigned here - on a config change
        // they're already built and this block below is a no-op (first access already
        // happened on cold launch, success or failure). Referencing each one as a bare
        // statement is deliberate: it's what forces the lazy's first construction to
        // happen right here, inside this try/catch, instead of wherever the first real
        // use further down happens to be - which is the entire point, since that's what
        // keeps a bad AppDatabase.create() landing on the crash screen below instead of
        // surfacing as an unrelated exception mid-scan or mid-composition.
        try {
            db
            scanner
            prefsManager
            contentRepo
        } catch (e: Throwable) {
            renderCrashScreen(e)
            return
        }

        // Restore saved library URI and auto-scan. This runs unattended on every launch
        // once a library has been picked once, before the user has touched anything -
        // startScan() already fails soft internally, but this outer catch is the last
        // line of defense so that literally nothing thrown on this path (a malformed
        // saved URI via Uri.parse, an unexpected DB error, etc) can crash the app on
        // startup. An uncaught exception here previously did exactly that.
        lifecycleScope.launch {
            prefsManager.libraryUri.collect { uri ->
                if (uri != null && novels.isEmpty()) {
                    try {
                        startScan(Uri.parse(uri))
                    } catch (e: Exception) {
                        scanProgress.value = null
                        scanMessage.value = "Couldn't load your library: ${e.message}"
                    }
                }
            }
        }

        // Watch theme preference
        lifecycleScope.launch {
            prefsManager.theme.collect { theme ->
                currentTheme.value = theme
            }
        }

        // Everything from here down (status-bar theming, HomeScreen and its new
        // empty-library state, etc.) is new UI code that runs on literally every cold
        // launch, before the user touches anything - and unlike the service construction
        // above, it was NOT guarded. The first composition pass of setContent() runs
        // synchronously on this call stack, so any exception thrown while building this
        // tree (a bad Icon reference, a null somewhere in HomeScreen, etc.) was previously
        // propagating straight past this function and crashing the process outright, with
        // no on-screen trace to debug from. Wrapping it surfaces the same diagnostic
        // screen as the guard above instead of a silent crash.
        try {
            renderMainContent()
        } catch (e: Throwable) {
            renderCrashScreen(e)
        }
    }

    private fun renderMainContent() {
        setContent {
            // Read once per composition and reused everywhere a resolved (non-
            // SYSTEM_DEFAULT) theme is needed below - the color scheme here and
            // ReaderScreen's appTheme param at its call site - so both agree on the
            // same light/dark resolution within a single frame.
            val systemInDarkTheme = isSystemInDarkTheme()
            val resolvedTheme = resolveTheme(currentTheme.value, systemInDarkTheme)
            val colorScheme = colorSchemeFor(resolvedTheme)
            MaterialTheme(colorScheme = colorScheme) {
                // The activity's manifest theme is static (always light), so without this
                // the system status bar icons stay dark-on-dark whenever the user picks
                // the Dark theme in Settings, and dark-on-light for Warm Paper's lower
                // contrast background - both unreadable. Recompute on every theme change
                // instead of once, since currentTheme can change at runtime.
                val view = LocalView.current

                if (showSplash.value) {
                    // Splash always renders on its own solid-black canvas (see
                    // SplashScreen.kt) regardless of the selected reader theme, so force
                    // light/white status bar icons here rather than deriving them from
                    // colorScheme.background like the real content below does, and paint
                    // the status bar itself black to match that canvas (see the SideEffect
                    // below for why statusBarColor needs to be set explicitly at all).
                    SideEffect {
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                        window.statusBarColor = Color.Black.toArgb()
                    }
                    SplashScreen(onFinished = { showSplash.value = false })
                    return@MaterialTheme
                }

                // The manifest's android:theme is a fixed system light theme, so its
                // statusBarColor default never tracks Light/Dark/Warm Paper - the status
                // bar stayed one fixed color while everything below it changed, sticking
                // out against every theme but the one that happened to match the default.
                // Painting it with the resolved background on every theme change (not just
                // fixing the icon color above) makes the status bar read as part of the
                // app's surface instead of a leftover system chrome strip.
                SideEffect {
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                        colorScheme.background.luminance() > 0.5f
                    window.statusBarColor = colorScheme.background.toArgb()
                }

                // The manifest's android:theme is a fixed system light theme (needed before
                // Compose ever runs), and MaterialTheme's colorScheme only styles the widgets
                // that explicitly read it - it does NOT paint the window/root background for
                // you. Without an explicit background here, any area not covered by a themed
                // widget (status bar edge-to-edge gaps, screen transition frames, etc.) shows
                // the underlying light window background regardless of Dark/Warm Paper being
                // selected - the theme "not applying evenly" that users see when switching
                // themes. Painting the root with the resolved background fixes that.
                //
                // Surface (not a plain Column + .background()) is required here, not just
                // for the paint: Surface is what sets LocalContentColor to `contentColor`
                // for everything composed inside it. A plain Column().background(...) paints
                // the background fine but never touches LocalContentColor, which is left at
                // its hardcoded default (black) - so every Text()/Icon() below that doesn't
                // pass an explicit `color` renders black regardless of theme. That was
                // invisible in Light/Warm Paper (black-on-light already looks intentional)
                // but unreadable in Dark theme (black-on-near-black). Routing the whole tree
                // through Surface fixes it for every screen at once instead of patching each
                // Text() call individually.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.background,
                    contentColor = colorScheme.onBackground
                ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val savedUri = prefsManager.libraryUri.collectAsState(initial = null)
                    // Reader font size/line height defaults - see PreferencesManager's
                    // doc comment on READER_FONT_SIZE_KEY/READER_LINE_HEIGHT_KEY for why
                    // these exist now. Read here (same collectAsState pattern as
                    // savedUri above) and passed into ReaderScreen as its starting point.
                    val readerFontSize = prefsManager.readerFontSize.collectAsState(initial = 18f)
                    val readerLineHeight = prefsManager.readerLineHeight.collectAsState(initial = 1.8f)

                    when (currentScreen.value) {
                        is Screen.Home -> {
                            HomeScreen(
                                novels = novels,
                                inProgressNovels = inProgressNovels,
                                onNovelClick = { novel ->
                                    lifecycleScope.launch {
                                        loadNovelDetails(novel)
                                        currentScreen.value = Screen.NovelDetail(novel)
                                    }
                                },
                                onContinueReading = { novel ->
                                    lifecycleScope.launch {
                                        val lastProgress = db.readingProgressDao().forNovel(novel.id)
                                        if (lastProgress != null) {
                                            val chapter = db.chapterDao().findById(lastProgress.chapterId)
                                            if (chapter != null) {
                                                // "Continue Reading" used to jump straight into
                                                // Screen.Reader without ever calling
                                                // loadNovelDetails(), unlike every other path into
                                                // the reader - so `chapters`/`arcs` could still hold
                                                // a *different* novel's data (or be empty) here.
                                                // That was harmless before Stage 3, since ReaderScreen
                                                // didn't read them; now Previous/Next need the
                                                // correctly-scoped chapter list to compute neighbors.
                                                loadNovelDetails(novel)
                                                readerAuthor.value = novel.authorId?.let { db.authorDao().findById(it) }
                                                openChapter(novel.id, chapter)
                                            }
                                        } else {
                                            loadNovelDetails(novel)
                                            currentScreen.value = Screen.NovelDetail(novel)
                                        }
                                    }
                                },
                                onBrowseClick = { currentScreen.value = Screen.FictionBrowse },
                                onSettingsClick = { currentScreen.value = Screen.Settings },
                                onSearch = { query ->
                                    if (query.isNotEmpty()) {
                                        currentScreen.value = Screen.FictionBrowse
                                    }
                                },
                                onSelectFolderClick = { pickFolder.launch(null) }
                            )
                        }

                        is Screen.FictionBrowse -> {
                            FictionBrowseScreen(
                                novels = novels,
                                onNovelSelected = { novel ->
                                    lifecycleScope.launch {
                                        loadNovelDetails(novel)
                                        currentScreen.value = Screen.NovelDetail(novel)
                                    }
                                },
                                onBack = { currentScreen.value = Screen.Home }
                            )
                        }

                        is Screen.NovelDetail -> {
                            val novel = (currentScreen.value as Screen.NovelDetail).novel
                            NovelDetailScreen(
                                novel = novel,
                                chapters = chapters,
                                arcs = arcs,
                                overriddenChapterIds = overriddenChapterIds.value,
                                onBack = { currentScreen.value = Screen.Home },
                                onChapterSelected = { chapter ->
                                    lifecycleScope.launch {
                                        // Mark novel as IN_PROGRESS when starting to read
                                        db.novelDao().updateReadingStatus(novel.id, NovelStatus.IN_PROGRESS.name)
                                        refreshRecentlyRead()
                                        readerAuthor.value = novel.authorId?.let { db.authorDao().findById(it) }
                                        openChapter(novel.id, chapter)
                                    }
                                },
                                onResizePages = { pageSize ->
                                    lifecycleScope.launch {
                                        db.novelDao().updatePageSize(novel.id, pageSize)
                                    }
                                },
                                onEditClick = { currentScreen.value = Screen.ChapterEditor(novel) },
                                onFetchInfoClick = { fetchMetadataFor(novel) },
                                onAuthorClick = {
                                    val authorId = novel.authorId
                                    if (authorId != null) {
                                        currentScreen.value = Screen.Author(authorId, from = currentScreen.value)
                                    }
                                }
                            )
                        }

                        is Screen.Reader -> {
                            val reader = currentScreen.value as Screen.Reader
                            // `chapters`/`arcs` are scoped to whichever novel was last loaded via
                            // loadNovelDetails() - both paths that create Screen.Reader now call
                            // it first (see onContinueReading/onChapterSelected above), so this is
                            // safe to read directly rather than re-querying the DB here.
                            val novel = novels.firstOrNull { it.id == reader.novelId }
                            val currentIndex = chapters.indexOfFirst { it.id == reader.chapter.id }
                            val previousChapter = chapters.getOrNull(currentIndex - 1).takeIf { currentIndex > 0 }
                            val nextChapter = chapters.getOrNull(currentIndex + 1).takeIf { currentIndex >= 0 }
                            val arcTitle = reader.chapter.arcId?.let { arcId -> arcs.firstOrNull { it.id == arcId }?.name }
                            // Arc cover -> fiction cover -> null (renders the placeholder) - see
                            // bugs.md Bug 3b. Resolved here rather than in ReaderScreen so it stays
                            // decoupled from ArcEntity/NovelEntity, same rationale as novelTitle/arcTitle.
                            val readerCoverUri = reader.chapter.arcId
                                ?.let { arcId -> arcs.firstOrNull { it.id == arcId }?.coverUri }
                                ?: novel?.coverUri
                            ReaderScreen(
                                chapter = reader.chapter,
                                content = reader.content,
                                // resolvedTheme, not currentTheme.value directly - the reader's
                                // own Light/Sepia/Dark palette seeds from the app theme (see
                                // readingModeFor in ReaderScreen.kt), which has no SYSTEM_DEFAULT
                                // case of its own; passing the already-resolved concrete theme
                                // here keeps that seeding correct now that System Default exists.
                                appTheme = resolvedTheme,
                                novelTitle = novel?.title,
                                arcTitle = arcTitle,
                                coverUri = readerCoverUri,
                                author = readerAuthor.value,
                                initialFontSize = readerFontSize.value,
                                initialLineHeight = readerLineHeight.value,
                                onFontSizeChange = { size ->
                                    lifecycleScope.launch { prefsManager.setReaderFontSize(size) }
                                },
                                onLineHeightChange = { height ->
                                    lifecycleScope.launch { prefsManager.setReaderLineHeight(height) }
                                },
                                onBack = { progress ->
                                    lifecycleScope.launch {
                                        saveReadingProgress(reader.novelId, reader.chapter.id, progress)
                                        currentScreen.value = Screen.Home
                                    }
                                },
                                onBackToFiction = { progress ->
                                    if (novel != null) {
                                        lifecycleScope.launch {
                                            saveReadingProgress(reader.novelId, reader.chapter.id, progress)
                                            currentScreen.value = Screen.NovelDetail(novel)
                                        }
                                    }
                                },
                                onPrevious = previousChapter?.let { prev ->
                                    { progress: Float ->
                                        lifecycleScope.launch {
                                            saveReadingProgress(reader.novelId, reader.chapter.id, progress)
                                            openChapter(reader.novelId, prev)
                                        }
                                    }
                                },
                                onNext = nextChapter?.let { next ->
                                    { progress: Float ->
                                        lifecycleScope.launch {
                                            saveReadingProgress(reader.novelId, reader.chapter.id, progress)
                                            openChapter(reader.novelId, next)
                                        }
                                    }
                                },
                                onAuthorClick = {
                                    val authorId = readerAuthor.value?.id
                                    if (authorId != null) {
                                        currentScreen.value = Screen.Author(authorId, from = currentScreen.value)
                                    }
                                }
                            )
                        }

                        is Screen.ChapterLoadError -> {
                            val errorScreen = currentScreen.value as Screen.ChapterLoadError
                            ChapterLoadErrorScreen(
                                chapterTitle = errorScreen.chapter.title,
                                onRetry = {
                                    lifecycleScope.launch {
                                        openChapter(errorScreen.novelId, errorScreen.chapter)
                                    }
                                },
                                onBack = {
                                    val novel = novels.firstOrNull { it.id == errorScreen.novelId }
                                    currentScreen.value = if (novel != null) Screen.NovelDetail(novel) else Screen.Home
                                }
                            )
                        }

                        is Screen.ChapterEditor -> {
                            val novel = (currentScreen.value as Screen.ChapterEditor).novel
                            ChapterEditorScreen(
                                chapters = chapters,
                                initialArcStartIds = arcStartChapterIds.value,
                                onSave = { updatedChapters, arcStartIds ->
                                    saveChapterEdits(novel, updatedChapters, arcStartIds)
                                },
                                onBack = { currentScreen.value = Screen.NovelDetail(novel) }
                            )
                        }

                        is Screen.Author -> {
                            val screen = currentScreen.value as Screen.Author
                            // Reload whenever the authorId changes (tapping into a
                            // different author's page while one is already showing isn't
                            // a real path today, but this keeps the screen correct if it
                            // ever is) - not on every recomposition.
                            LaunchedEffect(screen.authorId) {
                                loadAuthorPage(screen.authorId)
                            }
                            val author = authorPageAuthor.value
                            if (author != null) {
                                AuthorPageScreen(
                                    author = author,
                                    novels = authorPageNovels,
                                    onBack = { currentScreen.value = screen.from },
                                    onNovelClick = { novel ->
                                        lifecycleScope.launch {
                                            loadNovelDetails(novel)
                                            currentScreen.value = Screen.NovelDetail(novel)
                                        }
                                    }
                                )
                            }
                        }

                        is Screen.Settings -> {
                            SettingsScreen(
                                currentTheme = currentTheme.value,
                                hasLibrary = savedUri.value != null,
                                onThemeClick = { currentScreen.value = Screen.ThemeSettings },
                                onRescan = {
                                    val currentUri = savedUri.value
                                    if (currentUri != null) {
                                        lifecycleScope.launch {
                                            // No novels.clear() here - see bugs.md Bug 4.
                                            // startScan's onScanCompleted now reconciles
                                            // stale novels against the DB once the scan
                                            // actually finishes, instead of blanking the
                                            // visible library up front and hoping the scan
                                            // fully repopulates it.
                                            startScan(Uri.parse(currentUri))
                                        }
                                    } else {
                                        // No library selected yet - "Rescan" would previously
                                        // silently do nothing here. Send the user to the
                                        // picker instead.
                                        pickFolder.launch(null)
                                    }
                                },
                                onPrivacyPolicy = { currentScreen.value = Screen.PrivacyPolicy },
                                onTermsAndConditions = { currentScreen.value = Screen.TermsAndConditions },
                                onAboutMe = { currentScreen.value = Screen.AboutMe },
                                onBack = { currentScreen.value = Screen.Home }
                            )
                        }

                        is Screen.ThemeSettings -> {
                            ThemeSettingsScreen(
                                currentTheme = currentTheme.value,
                                onThemeSelected = { theme ->
                                    lifecycleScope.launch {
                                        prefsManager.setTheme(theme)
                                    }
                                },
                                onBack = { currentScreen.value = Screen.Settings }
                            )
                        }

                        is Screen.AboutMe -> {
                            AboutScreen(onBack = { currentScreen.value = Screen.Settings })
                        }

                        is Screen.PrivacyPolicy -> {
                            LegalDocumentScreen(
                                title = "Privacy Policy",
                                sections = LegalContent.privacyPolicy,
                                onBack = { currentScreen.value = Screen.Settings }
                            )
                        }

                        is Screen.TermsAndConditions -> {
                            LegalDocumentScreen(
                                title = "Terms & Conditions",
                                sections = LegalContent.termsAndConditions,
                                onBack = { currentScreen.value = Screen.Settings }
                            )
                        }
                    }
                }
                }

                when (val state = metadataSearchState.value) {
                    is MetadataSearchState.Loading -> {
                        MetadataSearchDialog(
                            novelTitle = state.novel.title,
                            isLoading = true,
                            errorMessage = null,
                            candidates = emptyList(),
                            onCandidateSelected = {},
                            onDismiss = { metadataSearchState.value = MetadataSearchState.Idle }
                        )
                    }
                    is MetadataSearchState.Results -> {
                        MetadataSearchDialog(
                            novelTitle = state.novel.title,
                            isLoading = false,
                            errorMessage = null,
                            candidates = state.candidates,
                            onCandidateSelected = { candidate -> applyMetadata(state.novel, candidate) },
                            onDismiss = { metadataSearchState.value = MetadataSearchState.Idle }
                        )
                    }
                    is MetadataSearchState.Error -> {
                        MetadataSearchDialog(
                            novelTitle = state.novel.title,
                            isLoading = false,
                            errorMessage = state.message,
                            candidates = emptyList(),
                            onCandidateSelected = {},
                            onDismiss = { metadataSearchState.value = MetadataSearchState.Idle }
                        )
                    }
                    is MetadataSearchState.Idle -> {}
                }
            }
        }
    }
}
