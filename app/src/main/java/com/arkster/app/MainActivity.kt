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
import com.arkster.app.ui.LibraryScreen
import com.arkster.app.ui.NovelDetailScreen
import com.arkster.app.ui.ReaderScreen
import com.arkster.app.data.AppDatabase
import com.arkster.app.data.ScannerImpl
import com.arkster.app.data.NovelEntity
import com.arkster.app.data.ChapterEntity
import com.arkster.app.data.PreferencesManager
import com.arkster.app.data.TextChapterContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen {
    object Library : Screen()
    data class NovelDetail(val novel: NovelEntity) : Screen()
    data class Reader(val chapter: ChapterEntity, val content: String) : Screen()
    data class ChapterEditor(val novel: NovelEntity) : Screen()
    object Settings : Screen()
}

class MainActivity : ComponentActivity() {

    private val novels = mutableStateListOf<NovelEntity>()
    private val chapters = mutableStateListOf<ChapterEntity>()
    private val arcs = mutableStateListOf<ArcEntity>()
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
        // Refresh to load arcs and updated chapters
        loadNovelDetails(novels.firstOrNull() ?: return)
    }

    private suspend fun loadNovelDetails(novel: NovelEntity) {
        chapters.clear()
        arcs.clear()
        chapters.addAll(db.chapterDao().forNovel(novel.id))
        arcs.addAll(db.arcDao().forNovel(novel.id))
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
                                refcentlyRead = novels.take(3),
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
                                        currentScreen.value = Screen.Reader(chapter, chapterContent.body)
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
                                onBack = { currentScreen.value = Screen.Library }
                            )
                        }

                        is Screen.ChapterEditor -> {
                            val novel = (currentScreen.value as Screen.ChapterEditor).novel
                            ChapterEditorScreen(
                                chapters = chapters,
                                onSave = { updatedChapters ->
                                    // Persist changes to DB would go here
                                    chapters.clear()
                                    chapters.addAll(updatedChapters)
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
                                        val uri = prefsManager.libraryUri.collect { it }
                                        if (uri != null) {
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
