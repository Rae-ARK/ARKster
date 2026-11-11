package com.arkster.app.core.navigation

/*
 * STAGE 3 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor. Full plan,
 * rationale and what each later stage does:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md -- that doc
 * is scaffolding for the refactor itself, not a permanent fixture: see its
 * own header for what to do with this pointer once the doc is gone.
 *
 * This is the Mediator the refactor doc's design-decisions section names:
 * replaces MainActivity's inline `when (currentScreen.value)` branching, so
 * screens no longer reach into shared Activity-held state to navigate each
 * other - they call navController.navigate(...)/popBackStack() instead, the
 * same way every destination below does.
 *
 * Each `composable(...)` block below corresponds 1:1 to an old `is Screen.X`
 * branch in MainActivity.kt - worth diffing against git history there if a
 * destination's wiring here ever looks surprising; nothing changed in what
 * each screen receives or does, only where that plumbing lives.
 *
 * STAGE 6 MOVE -- 2026-11-11
 * ChapterLoadErrorRoute's destination below called `app.db.chapterDao()`/
 * `app.db.novelDao()` directly - the one call site in this class, easy to
 * miss since it's not a ViewModel constructor argument the way every other
 * Dao-touching class in this stage was. It's a real call site like any
 * other (`app.db` is private as of this stage specifically so nothing can
 * still reach a Dao this way), so it moves to `app.chapterRepository`/
 * `app.novelRepository` the same as everywhere else.
 */

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arkster.app.App
import com.arkster.app.core.datastore.Theme
import com.arkster.app.feature.author.AuthorPageScreen
import com.arkster.app.feature.author.AuthorViewModel
import com.arkster.app.feature.browse.FictionBrowseScreen
import com.arkster.app.feature.browse.FictionBrowseViewModel
import com.arkster.app.feature.home.HomeScreen
import com.arkster.app.feature.home.HomeViewModel
import com.arkster.app.feature.noveldetail.ChapterEditorScreen
import com.arkster.app.feature.noveldetail.MetadataSearchDialog
import com.arkster.app.feature.noveldetail.MetadataSearchState
import com.arkster.app.feature.noveldetail.NovelDetailScreen
import com.arkster.app.feature.noveldetail.NovelDetailViewModel
import com.arkster.app.feature.reader.ChapterLoadErrorScreen
import com.arkster.app.feature.reader.ReaderNavEvent
import com.arkster.app.feature.reader.ReaderScreen
import com.arkster.app.feature.reader.ReaderViewModel
import com.arkster.app.feature.settings.AboutScreen
import com.arkster.app.feature.settings.LegalContent
import com.arkster.app.feature.settings.LegalDocumentScreen
import com.arkster.app.feature.settings.SettingsScreen
import com.arkster.app.feature.settings.SettingsViewModel
import com.arkster.app.feature.settings.ThemeSettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(
    app: App,
    resolvedTheme: Theme,
    readerFontSize: Float,
    readerLineHeight: Float,
    onReaderFontSizeChange: (Float) -> Unit,
    onReaderLineHeightChange: (Float) -> Unit,
    onPickFolder: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    val coroutineScope = rememberCoroutineScope()

    NavHost(navController = navController, startDestination = HomeRoute.route) {

        composable(HomeRoute.route) { backStackEntry ->
            val viewModel: HomeViewModel = viewModel(factory = AppViewModelFactory(app, backStackEntry))
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
            val scanMessage by viewModel.scanMessage.collectAsStateWithLifecycle()
            val unsupportedSources by viewModel.unsupportedSources.collectAsStateWithLifecycle()
            // scanProgress/scanMessage aren't rendered by HomeScreen today (they
            // weren't passed to it pre-Stage-3 either - MainActivity collected them
            // into fields nothing downstream of Screen.Home actually read). Collected
            // here with the same lifecycle-aware scope every other StateFlow uses so a
            // future screen update can surface them without another refactor; left
            // unused otherwise rather than invented a UI for here, since adding one
            // would be a behavior change this stage isn't scoped for.
            HomeScreen(
                novels = uiState.novels,
                inProgressNovels = uiState.inProgressNovels,
                onNovelClick = { novel ->
                    navController.navigate(NovelDetailRoute.createRoute(novel.id))
                },
                onContinueReading = { novel ->
                    coroutineScope.launch {
                        val chapter = viewModel.lastReadChapterFor(novel)
                        if (chapter != null) {
                            navigateToChapter(navController, app.contentRepo, novel.id, chapter)
                        } else {
                            navController.navigate(NovelDetailRoute.createRoute(novel.id))
                        }
                    }
                },
                onBrowseClick = { navController.navigate(FictionBrowseRoute.route) },
                onSettingsClick = { navController.navigate(SettingsRoute.route) },
                onSearch = { query ->
                    if (query.isNotEmpty()) {
                        navController.navigate(FictionBrowseRoute.route)
                    }
                },
                onSelectFolderClick = onPickFolder,
                unsupportedSources = unsupportedSources,
                onDismissUnsupportedSources = { viewModel.dismissUnsupportedSources() }
            )
        }

        composable(FictionBrowseRoute.route) { backStackEntry ->
            val viewModel: FictionBrowseViewModel = viewModel(factory = AppViewModelFactory(app, backStackEntry))
            val novels by viewModel.novels.collectAsStateWithLifecycle()
            FictionBrowseScreen(
                novels = novels,
                onNovelSelected = { novel ->
                    navController.navigate(NovelDetailRoute.createRoute(novel.id))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            NovelDetailRoute.route,
            arguments = listOf(navArgument(NovelDetailRoute.novelIdArg) { type = NavType.StringType })
        ) { backStackEntry ->
            val viewModel: NovelDetailViewModel = viewModel(factory = AppViewModelFactory(app, backStackEntry))
            // Reload on every return to this destination (e.g. coming back from
            // ChapterEditor after a save) - this ViewModel owns a private snapshot of
            // chapters/arcs rather than a shared Flow the way FictionBrowseViewModel's
            // `novels` is, so unlike Browse it doesn't pick up another screen's writes
            // for free. See NovelDetailViewModel's header for why it isn't shared
            // across the two destinations instead.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, viewModel) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) viewModel.load()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val novel by viewModel.novel.collectAsStateWithLifecycle()
            val chapters by viewModel.chapters.collectAsStateWithLifecycle()
            val arcs by viewModel.arcs.collectAsStateWithLifecycle()
            val overriddenChapterIds by viewModel.overriddenChapterIds.collectAsStateWithLifecycle()
            val metadataSearchState by viewModel.metadataSearchState.collectAsStateWithLifecycle()

            val currentNovel = novel
            if (currentNovel != null) {
                NovelDetailScreen(
                    novel = currentNovel,
                    chapters = chapters,
                    arcs = arcs,
                    overriddenChapterIds = overriddenChapterIds,
                    onBack = { navController.popBackStack() },
                    onChapterSelected = { chapter ->
                        viewModel.markChapterOpened()
                        coroutineScope.launch {
                            navigateToChapter(navController, app.contentRepo, currentNovel.id, chapter)
                        }
                    },
                    onResizePages = { pageSize -> viewModel.resizePages(pageSize) },
                    onEditClick = { navController.navigate(ChapterEditorRoute.createRoute(currentNovel.id)) },
                    onFetchInfoClick = { viewModel.fetchMetadata() },
                    onAuthorClick = {
                        val authorId = currentNovel.authorId
                        if (authorId != null) {
                            navController.navigate(AuthorRoute.createRoute(authorId))
                        }
                    }
                )
            }

            when (val state = metadataSearchState) {
                is MetadataSearchState.Loading -> MetadataSearchDialog(
                    novelTitle = state.novel.title,
                    isLoading = true,
                    errorMessage = null,
                    candidates = emptyList(),
                    onCandidateSelected = {},
                    onDismiss = { viewModel.dismissMetadataSearch() }
                )
                is MetadataSearchState.Results -> MetadataSearchDialog(
                    novelTitle = state.novel.title,
                    isLoading = false,
                    errorMessage = null,
                    candidates = state.candidates,
                    onCandidateSelected = { candidate -> viewModel.applyMetadata(candidate) },
                    onDismiss = { viewModel.dismissMetadataSearch() }
                )
                is MetadataSearchState.Error -> MetadataSearchDialog(
                    novelTitle = state.novel.title,
                    isLoading = false,
                    errorMessage = state.message,
                    candidates = emptyList(),
                    onCandidateSelected = {},
                    onDismiss = { viewModel.dismissMetadataSearch() }
                )
                is MetadataSearchState.Idle -> {}
            }
        }

        composable(
            ChapterEditorRoute.route,
            arguments = listOf(navArgument(ChapterEditorRoute.novelIdArg) { type = NavType.StringType })
        ) { backStackEntry ->
            val viewModel: NovelDetailViewModel = viewModel(factory = AppViewModelFactory(app, backStackEntry))
            val chapters by viewModel.chapters.collectAsStateWithLifecycle()
            val arcStartChapterIds by viewModel.arcStartChapterIds.collectAsStateWithLifecycle()
            ChapterEditorScreen(
                chapters = chapters,
                initialArcStartIds = arcStartChapterIds,
                onSave = { updatedChapters, arcStartIds ->
                    viewModel.saveChapterEdits(updatedChapters, arcStartIds)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            ReaderRoute.route,
            arguments = listOf(
                navArgument(ReaderRoute.novelIdArg) { type = NavType.StringType },
                navArgument(ReaderRoute.chapterIdArg) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val viewModel: ReaderViewModel = viewModel(factory = AppViewModelFactory(app, backStackEntry))
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(viewModel) {
                viewModel.navEvents.collect { event ->
                    when (event) {
                        is ReaderNavEvent.ChapterLoadFailed -> {
                            navController.navigate(
                                ChapterLoadErrorRoute.createRoute(event.novelId, event.chapter.id)
                            ) {
                                popUpTo(ReaderRoute.route) { inclusive = true }
                            }
                        }
                    }
                }
            }

            val chapter = uiState.chapter
            if (chapter != null) {
                ReaderScreen(
                    chapter = chapter,
                    content = uiState.content,
                    appTheme = resolvedTheme,
                    novelTitle = uiState.novel?.title,
                    arcTitle = uiState.arcTitle,
                    coverUri = uiState.coverUri,
                    author = uiState.author,
                    initialFontSize = readerFontSize,
                    initialLineHeight = readerLineHeight,
                    onFontSizeChange = onReaderFontSizeChange,
                    onLineHeightChange = onReaderLineHeightChange,
                    onBack = { progress ->
                        coroutineScope.launch {
                            viewModel.saveProgress(progress)
                            navController.popBackStack(HomeRoute.route, inclusive = false)
                        }
                    },
                    onBackToFiction = { progress ->
                        val novelId = uiState.novel?.id
                        if (novelId != null) {
                            coroutineScope.launch {
                                viewModel.saveProgress(progress)
                                // navigate+popUpTo(Home), not popBackStack(route) - this
                                // has to work even when Reader was reached straight from
                                // Home's "Continue Reading" without ever pushing a
                                // NovelDetail entry for this novel, in which case
                                // popBackStack(route) would silently no-op instead of
                                // getting there. Always lands on NovelDetail for this
                                // novel with Home directly beneath it, matching the old
                                // unconditional `currentScreen.value = Screen.NovelDetail(novel)`.
                                navController.navigate(NovelDetailRoute.createRoute(novelId)) {
                                    popUpTo(HomeRoute.route) { inclusive = false }
                                }
                            }
                        }
                    },
                    onPrevious = uiState.previousChapter?.let { { progress: Float -> viewModel.goToPrevious(progress) } },
                    onNext = uiState.nextChapter?.let { { progress: Float -> viewModel.goToNext(progress) } },
                    onAuthorClick = {
                        val authorId = uiState.author?.id
                        if (authorId != null) {
                            navController.navigate(AuthorRoute.createRoute(authorId))
                        }
                    }
                )
            }
        }

        composable(
            ChapterLoadErrorRoute.route,
            arguments = listOf(
                navArgument(ChapterLoadErrorRoute.novelIdArg) { type = NavType.StringType },
                navArgument(ChapterLoadErrorRoute.chapterIdArg) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val novelId = checkNotNull(backStackEntry.arguments?.getString(ChapterLoadErrorRoute.novelIdArg))
            val chapterId = checkNotNull(backStackEntry.arguments?.getString(ChapterLoadErrorRoute.chapterIdArg))
            // No dedicated ViewModel: this screen owns no state of its own beyond the
            // one chapter title it looks up to display, and both of its actions
            // (Retry, Back) are one-shot DB reads rather than anything that benefits
            // from surviving recomposition - a full ViewModel class here would be
            // ceremony with nothing to hold.
            var chapterTitle by remember { mutableStateOf("") }
            LaunchedEffect(chapterId) {
                chapterTitle = app.chapterRepository.findById(chapterId)?.title ?: ""
            }
            ChapterLoadErrorScreen(
                chapterTitle = chapterTitle,
                onRetry = {
                    coroutineScope.launch {
                        val chapter = app.chapterRepository.findById(chapterId)
                        if (chapter != null) {
                            navigateToChapter(navController, app.contentRepo, novelId, chapter, replaceCurrent = true)
                        }
                    }
                },
                onBack = {
                    coroutineScope.launch {
                        val novel = app.novelRepository.findById(novelId)
                        if (novel != null) {
                            navController.navigate(NovelDetailRoute.createRoute(novel.id)) {
                                popUpTo(ChapterLoadErrorRoute.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(HomeRoute.route) {
                                popUpTo(ChapterLoadErrorRoute.route) { inclusive = true }
                            }
                        }
                    }
                }
            )
        }

        composable(
            AuthorRoute.route,
            arguments = listOf(navArgument(AuthorRoute.authorIdArg) { type = NavType.StringType })
        ) { backStackEntry ->
            val viewModel: AuthorViewModel = viewModel(factory = AppViewModelFactory(app, backStackEntry))
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val author = uiState.author
            if (author != null) {
                AuthorPageScreen(
                    author = author,
                    novels = uiState.novels,
                    // NavController's own back stack already knows whether the fiction
                    // page or the reader pushed this destination - see Routes.kt's note
                    // on why Screen.Author's old `from` field isn't needed anymore.
                    onBack = { navController.popBackStack() },
                    onNovelClick = { novel ->
                        navController.navigate(NovelDetailRoute.createRoute(novel.id))
                    }
                )
            }
        }

        composable(SettingsRoute.route) { backStackEntry ->
            val viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory(app, backStackEntry))
            val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
            val hasLibrary by viewModel.hasLibrary.collectAsStateWithLifecycle()
            SettingsScreen(
                currentTheme = currentTheme,
                hasLibrary = hasLibrary,
                onThemeClick = { navController.navigate(ThemeSettingsRoute.route) },
                onRescan = {
                    if (hasLibrary) {
                        coroutineScope.launch {
                            val uri = app.prefsManager.libraryUri.first()
                            if (uri != null) viewModel.rescan(Uri.parse(uri))
                        }
                    } else {
                        // No library selected yet - send the user to the picker instead
                        // of silently doing nothing, same as before.
                        onPickFolder()
                    }
                },
                onPrivacyPolicy = { navController.navigate(PrivacyPolicyRoute.route) },
                onTermsAndConditions = { navController.navigate(TermsAndConditionsRoute.route) },
                onAboutMe = { navController.navigate(AboutRoute.route) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ThemeSettingsRoute.route) { backStackEntry ->
            val viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory(app, backStackEntry))
            val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
            ThemeSettingsScreen(
                currentTheme = currentTheme,
                onThemeSelected = { theme -> viewModel.setTheme(theme) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(AboutRoute.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        composable(PrivacyPolicyRoute.route) {
            LegalDocumentScreen(
                title = "Privacy Policy",
                sections = LegalContent.privacyPolicy,
                onBack = { navController.popBackStack() }
            )
        }

        composable(TermsAndConditionsRoute.route) {
            LegalDocumentScreen(
                title = "Terms & Conditions",
                sections = LegalContent.termsAndConditions,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
