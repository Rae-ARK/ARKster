package com.arkster.app.feature.reader

/*
 * STAGE 3 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor. Full plan,
 * rationale and what each later stage does:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md -- that doc
 * is scaffolding for the refactor itself, not a permanent fixture: see its
 * own header for what to do with this pointer once the doc is gone.
 *
 * ReaderRoute only carries novelId+chapterId (see Routes.kt's header on why
 * Screen.Reader's old in-memory `content: String` couldn't survive a Bundle
 * round-trip) - this ViewModel re-derives everything else from the DB,
 * including the effective, override-applied chapter list
 * (domain/repository/EffectiveChapters.kt) needed to compute
 * previousChapter/nextChapter, same merge NovelDetailViewModel applies, now
 * shared rather than duplicated.
 *
 * Previous/Next are NOT NavController navigations to a new ReaderRoute
 * entry - see ChapterNavigation.kt's header for why: goToPrevious/goToNext
 * below reuse this same ViewModel instance and just reload its uiState in
 * place, exactly like MainActivity used to reassign
 * `currentScreen.value = Screen.Reader(...)` without pushing a new screen.
 * That means the NavController back stack's own chapterId arg for this
 * entry goes stale the moment Previous/Next is used once - onBack/
 * onBackToFiction always save progress against uiState.value.chapter (the
 * ViewModel's own notion of "current chapter"), never against the route arg,
 * so that staleness is harmless by construction. A load failure here (chapter
 * deleted/moved since the list was built) can't just "show nothing" the way
 * the initial open's ChapterNavigation.navigateToChapter does by picking a
 * different destination up front - this ViewModel already IS the Reader
 * destination - so it reports the failure via ReaderNavEvent instead and
 * lets AppNavHost navigate to ChapterLoadErrorRoute.
 *
 * STAGE 6 MOVE -- 2026-11-11
 * Took `db: AppDatabase` before this patch and called chapterDao()/
 * novelDao()/arcDao()/authorDao()/readingProgressDao() directly - now takes
 * ChapterRepository/NovelRepository/ArcRepository/AuthorRepository/
 * ReadingProgressRepository instead, one per Dao this class touched.
 * `loadEffectiveChapters(db, novelId)` becomes
 * `chapterRepository.getEffectiveChapters(novelId)` - see
 * ChapterRepository's own STAGE 6 MOVE header.
 */

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arkster.app.core.data.LibrarySession
import com.arkster.app.core.database.AuthorEntity
import com.arkster.app.core.database.ChapterEntity
import com.arkster.app.core.database.NovelEntity
import com.arkster.app.core.database.ReadingProgressEntity
import com.arkster.app.core.navigation.ReaderRoute
import com.arkster.app.domain.repository.ArcRepository
import com.arkster.app.domain.repository.AuthorRepository
import com.arkster.app.domain.repository.ChapterLoadResult
import com.arkster.app.domain.repository.ChapterRepository
import com.arkster.app.domain.repository.NovelRepository
import com.arkster.app.domain.repository.ReadingProgressRepository
import com.arkster.app.domain.repository.TextChapterContentRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReaderUiState(
    val chapter: ChapterEntity? = null,
    val content: String = "",
    val novel: NovelEntity? = null,
    val arcTitle: String? = null,
    val coverUri: String? = null,
    val author: AuthorEntity? = null,
    val previousChapter: ChapterEntity? = null,
    val nextChapter: ChapterEntity? = null
)

// One-shot navigation request this ViewModel can't satisfy itself (see this file's
// header on why Previous/Next's own load failures can't just swap destinations the
// way the initial open does).
sealed class ReaderNavEvent {
    data class ChapterLoadFailed(val novelId: String, val chapter: ChapterEntity) : ReaderNavEvent()
}

class ReaderViewModel(
    savedStateHandle: SavedStateHandle,
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository,
    private val arcRepository: ArcRepository,
    private val authorRepository: AuthorRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val contentRepo: TextChapterContentRepository,
    private val librarySession: LibrarySession
) : ViewModel() {

    private val novelId: String = checkNotNull(savedStateHandle[ReaderRoute.novelIdArg])
    private val initialChapterId: String = checkNotNull(savedStateHandle[ReaderRoute.chapterIdArg])

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _navEvents = MutableSharedFlow<ReaderNavEvent>()
    val navEvents: SharedFlow<ReaderNavEvent> = _navEvents.asSharedFlow()

    init {
        viewModelScope.launch { loadChapter(initialChapterId) }
    }

    // See bugs.md Bug 8: getTextContent returns a ChapterLoadResult instead of the
    // chapter text directly, so a read failure here reports ReaderNavEvent.
    // ChapterLoadFailed instead of silently showing an empty chapter.
    private suspend fun loadChapter(chapterId: String) {
        val chapter = chapterRepository.findById(chapterId) ?: return
        val novel = novelRepository.findById(novelId)
        val arcs = arcRepository.forNovel(novelId)
        val effective = chapterRepository.getEffectiveChapters(novelId)
        val currentIndex = effective.chapters.indexOfFirst { it.id == chapter.id }
        val previousChapter = effective.chapters.getOrNull(currentIndex - 1).takeIf { currentIndex > 0 }
        val nextChapter = effective.chapters.getOrNull(currentIndex + 1).takeIf { currentIndex >= 0 }
        val arcTitle = chapter.arcId?.let { arcId -> arcs.firstOrNull { it.id == arcId }?.name }
        // Arc cover -> fiction cover -> null (renders the placeholder) - see bugs.md
        // Bug 3b.
        val coverUri = chapter.arcId
            ?.let { arcId -> arcs.firstOrNull { it.id == arcId }?.coverUri }
            ?: novel?.coverUri
        val author = novel?.authorId?.let { authorRepository.findById(it) }

        when (val result = contentRepo.getTextContent(chapter.sourcePath)) {
            is ChapterLoadResult.Ok -> {
                _uiState.value = ReaderUiState(
                    chapter = chapter,
                    content = result.content.body,
                    novel = novel,
                    arcTitle = arcTitle,
                    coverUri = coverUri,
                    author = author,
                    previousChapter = previousChapter,
                    nextChapter = nextChapter
                )
            }
            is ChapterLoadResult.Failed -> {
                _navEvents.emit(ReaderNavEvent.ChapterLoadFailed(novelId, chapter))
            }
        }
    }

    suspend fun saveProgress(progress: Float) {
        val chapter = _uiState.value.chapter ?: return
        readingProgressRepository.upsert(
            ReadingProgressEntity(
                novelId = novelId,
                chapterId = chapter.id,
                position = progress,
                positionType = "PERCENTAGE",
                updatedAt = System.currentTimeMillis()
            )
        )
        librarySession.refreshRecentlyRead()
    }

    fun goToPrevious(progress: Float) {
        val prev = _uiState.value.previousChapter ?: return
        viewModelScope.launch {
            saveProgress(progress)
            loadChapter(prev.id)
        }
    }

    fun goToNext(progress: Float) {
        val next = _uiState.value.nextChapter ?: return
        viewModelScope.launch {
            saveProgress(progress)
            loadChapter(next.id)
        }
    }
}
