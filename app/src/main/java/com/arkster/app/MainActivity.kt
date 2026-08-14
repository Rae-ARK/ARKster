package com.arkster.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.arkster.app.ui.ChapterEditorScreen
import com.arkster.app.ui.HomeScreen
import com.arkster.app.ui.LibraryScreen
import com.arkster.app.ui.NovelDetailScreen
import com.arkster.app.ui.ReaderScreen
import com.arkster.app.ui.SettingsScreen
import com.arkster.app.ui.FictionBrowseScreen
import com.arkster.app.data.AppDatabase
import com.arkster.app.data.ArcEntity
import com.arkster.app.data.ChapterOverrideEntity
import com.arkster.app.data.ReadingProgressEntity
import com.arkster.app.data.ScannerImpl
import com.arkster.app.data.NovelEntity
import com.arkster.app.data.ChapterEntity
import com.arkster.app.data.PreferencesManager
import com.arkster.app.data.Theme
import com.arkster.app.data.NovelStatus
import com.arkster.app.data.TextChapterContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class Screen {
    object Home : Screen()
    object Library : Screen()
    data class NovelDetail(val novel: NovelEntity) : Screen()
    data class Reader(val novelId: String, val chapter: ChapterEntity, val content: String) : Screen()
    data class ChapterEditor(val novel: NovelEntity) : Screen()
    object Settings : Screen()
    object FictionBrowse : Screen()
}

// Warm, sepia-toned reading theme - lower contrast than pure light/dark, meant to
// be easier on the eyes for long reading sessions (a common e-reader "paper" mode).
private fun warmPaperColorScheme() = lightColorScheme(
    primary = Color(0xFF8B5E34),
    onPrimary = Color(0xFFFFFBF5),
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
}

class MainActivity : ComponentActivity() {

    private val novels = mutableStateListOf<NovelEntity>()
    private val chapters = mutableStateListOf<ChapterEntity>() // overrides already applied
    private val arcs = mutableStateListOf<ArcEntity>()
    private val recentlyRead = mutableStateListOf<NovelEntity>()
    private val inProgressNovels = mutableStateListOf<NovelEntity>()
    private val overriddenChapterIds = mutableStateOf<Set<String>>(emptySet())
    private val arcStartChapterIds = mutableStateOf<Set<String>>(emptySet())
    private val currentScreen = mutableStateOf<Screen>(Screen.Home)
    private val currentTheme = mutableStateOf(Theme.LIGHT)
    private val scanProgress = mutableStateOf<Pair<Int, Int>?>(null)  // (current, total) or null if not scanning
    private val scanMessage = mutableStateOf("")
    private lateinit var db: AppDatabase
    private lateinit var scanner: ScannerImpl
    private lateinit var prefsManager: PreferencesManager
    private lateinit var contentRepo: TextChapterContentRepository

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
        scanner.scanRoot(treeUri, 
            onDiscovered = { scanned, novelFolder ->
                // scanRoot always builds a fresh NovelEntity with default field values (e.g.
                // pageSize = 10). Upsert REPLACEs the whole row, so on rescan we'd silently
                // wipe out anything the user has customized (like their pagination choice)
                // unless we carry it over from the existing row first.
                val existing = db.novelDao().findById(scanned.id)
                val novel = if (existing != null) scanned.copy(pageSize = existing.pageSize) else scanned
                db.novelDao().upsert(novel)
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
            },
            onProgress = { current, total, message ->
                withContext(Dispatchers.Main) {
                    scanProgress.value = Pair(current, total)
                    scanMessage.value = message
                }
            }
        )
        withContext(Dispatchers.Main) {
            scanProgress.value = null
            scanMessage.value = ""
        }
        refreshRecentlyRead()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.create(this)
        scanner = ScannerImpl(this)
        prefsManager = PreferencesManager(this)
        contentRepo = TextChapterContentRepository(this)

        // Restore saved library URI and auto-scan
        lifecycleScope.launch {
            prefsManager.libraryUri.collect { uri ->
                if (uri != null && novels.isEmpty()) {
                    startScan(Uri.parse(uri))
                }
            }
        }

        // Watch theme preference
        lifecycleScope.launch {
            prefsManager.theme.collect { theme ->
                currentTheme.value = theme
            }
        }

        setContent {
            MaterialTheme(colorScheme = colorSchemeFor(currentTheme.value)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val savedUri = prefsManager.libraryUri.collectAsState(initial = null)

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
                                                val chapterContent = contentRepo.getTextContent(chapter.sourcePath)
                                                currentScreen.value = Screen.Reader(novel.id, chapter, chapterContent.body)
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
                                }
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

                        is Screen.Library -> {
                            Button(onClick = { pickFolder.launch(null) }) {
                                Text(if (savedUri.value != null) "Rescan" else "Select library folder")
                            }
                            LibraryScreen(
                                novels = novels,
                                recentlyRead = recentlyRead,
                                inProgressNovels = inProgressNovels,
                                scanProgress = scanProgress.value,
                                scanMessage = scanMessage.value,
                                onNovelSelected = { novel ->
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
                                                val chapterContent = contentRepo.getTextContent(chapter.sourcePath)
                                                currentScreen.value = Screen.Reader(novel.id, chapter, chapterContent.body)
                                            }
                                        } else {
                                            loadNovelDetails(novel)
                                            currentScreen.value = Screen.NovelDetail(novel)
                                        }
                                    }
                                },
                                onSettingsClick = { currentScreen.value = Screen.Settings }
                            )
                        }

                        is Screen.NovelDetail -> {
                            val novel = (currentScreen.value as Screen.NovelDetail).novel
                            NovelDetailScreen(
                                novel = novel,
                                chapters = chapters,
                                arcs = arcs,
                                overriddenChapterIds = overriddenChapterIds.value,
                                onBack = { currentScreen.value = Screen.Library },
                                onChapterSelected = { chapter ->
                                    lifecycleScope.launch {
                                        // Mark novel as IN_PROGRESS when starting to read
                                        db.novelDao().updateReadingStatus(novel.id, NovelStatus.IN_PROGRESS.name)
                                        refreshRecentlyRead()
                                        val chapterContent = contentRepo.getTextContent(chapter.sourcePath)
                                        currentScreen.value = Screen.Reader(novel.id, chapter, chapterContent.body)
                                    }
                                },
                                onResizePages = { pageSize ->
                                    lifecycleScope.launch {
                                        db.novelDao().updatePageSize(novel.id, pageSize)
                                    }
                                },
                                onEditClick = { currentScreen.value = Screen.ChapterEditor(novel) }
                            )
                        }

                        is Screen.Reader -> {
                            val reader = currentScreen.value as Screen.Reader
                            ReaderScreen(
                                chapter = reader.chapter,
                                content = reader.content,
                                onBack = { progress ->
                                    lifecycleScope.launch {
                                        saveReadingProgress(reader.novelId, reader.chapter.id, progress)
                                        currentScreen.value = Screen.Library
                                    }
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

                        is Screen.Settings -> {
                            SettingsScreen(
                                currentTheme = currentTheme.value,
                                onThemeSelected = { theme ->
                                    lifecycleScope.launch {
                                        prefsManager.setTheme(theme)
                                    }
                                },
                                onRescan = {
                                    lifecycleScope.launch {
                                        savedUri.value?.let { uri ->
                                            novels.clear()
                                            startScan(Uri.parse(uri))
                                        }
                                    }
                                },
                                onBack = { currentScreen.value = Screen.Home }
                            )
                        }
                    }
                }
            }
        }
    }
}
