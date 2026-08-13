package com.arkster.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.arkster.app.ui.ChapterEditorScreen
import com.arkster.app.ui.LibraryScreen
import com.arkster.app.ui.NovelDetailScreen
import com.arkster.app.ui.ReaderScreen
import com.arkster.app.ui.SettingsScreen
import com.arkster.app.data.AppDatabase
import com.arkster.app.data.ArcEntity
import com.arkster.app.data.ChapterOverrideEntity
import com.arkster.app.data.ReadingProgressEntity
import com.arkster.app.data.ScannerImpl
import com.arkster.app.data.NovelEntity
import com.arkster.app.data.ChapterEntity
import com.arkster.app.data.PreferencesManager
import com.arkster.app.data.Theme
import com.arkster.app.data.TextChapterContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class Screen {
    object Library : Screen()
    data class NovelDetail(val novel: NovelEntity) : Screen()
    data class Reader(val novelId: String, val chapter: ChapterEntity, val content: String) : Screen()
    data class ChapterEditor(val novel: NovelEntity) : Screen()
    object Settings : Screen()
}

class MainActivity : ComponentActivity() {

    private val novels = mutableStateListOf<NovelEntity>()
    private val chapters = mutableStateListOf<ChapterEntity>() // overrides already applied
    private val arcs = mutableStateListOf<ArcEntity>()
    private val recentlyRead = mutableStateListOf<NovelEntity>()
    private val currentScreen = mutableStateOf<Screen>(Screen.Library)
    private val currentTheme = mutableStateOf(Theme.LIGHT)
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
        scanner.scanRoot(treeUri) { novel ->
            db.novelDao().upsert(novel)
            withContext(Dispatchers.Main) {
                novels.add(novel)
            }
            // After discovering a novel, scan its chapters and arcs
            val novelFolder = DocumentFile.fromTreeUri(this@MainActivity, treeUri)
                ?.listFiles()?.find { it.name == novel.title && it.isDirectory }
            if (novelFolder != null) {
                scanner.scanChaptersForNovel(novelFolder, novel.id, db)
            }
        }
        refreshRecentlyRead()
    }

    // Loads the raw scanned chapters for a novel and applies any saved chapter_overrides
    // (title/position) on top, so every screen downstream sees the "effective" chapter
    // list rather than raw scan data.
    private suspend fun loadNovelDetails(novel: NovelEntity) {
        val raw = db.chapterDao().forNovel(novel.id)
        val overridesByChapterId = db.chapterOverrideDao().forNovel(novel.id).associateBy { it.chapterId }
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
    }

    // Diffs the editor's edited chapter list against the raw scanned chapters and
    // persists per-chapter overrides only where something actually changed. If an edit
    // matches the scanned default again, any stale override for that chapter is removed
    // instead of being kept around with stale values.
    private suspend fun saveChapterEdits(novel: NovelEntity, edited: List<ChapterEntity>) {
        val raw = db.chapterDao().forNovel(novel.id) // natural scan order, no overrides
        val rawById = raw.associateBy { it.id }
        val rawIndexById = raw.withIndex().associate { (i, c) -> c.id to i }

        edited.forEachIndexed { index, chapter ->
            val original = rawById[chapter.id] ?: return@forEachIndexed
            val titleOverride = if (chapter.title != original.title) chapter.title else null
            val positionOverride = if (index != rawIndexById[chapter.id]) index else null

            if (titleOverride != null || positionOverride != null) {
                db.chapterOverrideDao().upsert(
                    ChapterOverrideEntity(
                        id = UUID.nameUUIDFromBytes("override:${chapter.id}".toByteArray()).toString(),
                        chapterId = chapter.id,
                        titleOverride = titleOverride,
                        positionOverride = positionOverride
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
            MaterialTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    val savedUri = prefsManager.libraryUri.collectAsState(initial = null)

                    when (currentScreen.value) {
                        is Screen.Library -> {
                            Button(onClick = { pickFolder.launch(null) }) {
                                Text(if (savedUri.value != null) "Rescan" else "Select library folder")
                            }
                            LibraryScreen(
                                novels = novels,
                                recentlyRead = recentlyRead,
                                onNovelSelected = { novel ->
                                    lifecycleScope.launch {
                                        loadNovelDetails(novel)
                                        currentScreen.value = Screen.NovelDetail(novel)
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
                                onBack = { currentScreen.value = Screen.Library },
                                onChapterSelected = { chapter ->
                                    lifecycleScope.launch {
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
                                onSave = { updatedChapters ->
                                    saveChapterEdits(novel, updatedChapters)
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
                                onBack = { currentScreen.value = Screen.Library }
                            )
                        }
                    }
                }
            }
        }
    }
}
