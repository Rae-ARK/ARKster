package horizonarkstudio.raeark.arkster.feature.home

/*
 * STAGE 3 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor. Full plan,
 * rationale and what each later stage does:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md -- that doc
 * is scaffolding for the refactor itself, not a permanent fixture: see its
 * own header for what to do with this pointer once the doc is gone.
 *
 * The auto-scan-on-launch logic below (collect libraryUri, scan once if a
 * library is already picked and nothing's loaded yet) moved here verbatim
 * from MainActivity.onCreate() - Home is always the start destination, so
 * this is the equivalent launch-time hook. It now lives in `init` instead
 * of onCreate() and runs in viewModelScope instead of lifecycleScope, so a
 * config change doesn't restart it (the ViewModel survives the Activity
 * recreation; onCreate() does not) - one more small piece of the config-
 * change gap this refactor closes, on top of what Stage 2 already did for
 * the service objects themselves.
 *
 * Everything else here is a thin pass-through onto LibrarySession's
 * StateFlows (see core/data/LibrarySession.kt for why that class, not this
 * one, owns the actual novel list and scan state) plus hasLibrary, read
 * directly from PreferencesManager the same reactive way MainActivity used
 * to with collectAsState.
 *
 * STAGE 6 MOVE -- 2026-11-11
 * Took `db: AppDatabase` before this patch and called readingProgressDao()/
 * chapterDao() directly in lastReadChapterFor - now takes
 * ChapterRepository/ReadingProgressRepository instead, same "nothing above
 * domain/ touches a Dao directly" rule every other call site in this patch
 * follows.
 */

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import horizonarkstudio.raeark.arkster.core.data.LibrarySession
import horizonarkstudio.raeark.arkster.core.database.NovelEntity
import horizonarkstudio.raeark.arkster.core.datastore.PreferencesManager
import horizonarkstudio.raeark.arkster.domain.booksource.UnsupportedSource
import horizonarkstudio.raeark.arkster.domain.repository.ChapterRepository
import horizonarkstudio.raeark.arkster.domain.repository.ReadingProgressRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class HomeUiState(
    val novels: List<NovelEntity> = emptyList(),
    val inProgressNovels: List<NovelEntity> = emptyList(),
    val hasLibrary: Boolean = false
)

class HomeViewModel(
    private val chapterRepository: ChapterRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val prefsManager: PreferencesManager,
    private val librarySession: LibrarySession
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        librarySession.novels,
        librarySession.inProgressNovels,
        prefsManager.libraryUri
    ) { novels, inProgress, libraryUri ->
        HomeUiState(novels = novels, inProgressNovels = inProgress, hasLibrary = libraryUri != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val scanProgress: StateFlow<Pair<Int, Int>?> = librarySession.scanProgress
    val scanMessage: StateFlow<String> = librarySession.scanMessage

    // STAGE 5 (this patch): thin pass-through onto LibrarySession's StateFlow,
    // same pattern every other property on this class already follows - see
    // LibrarySession.unsupportedSources for what populates it.
    val unsupportedSources: StateFlow<List<UnsupportedSource>> = librarySession.unsupportedSources

    fun dismissUnsupportedSources() {
        librarySession.dismissUnsupportedSources()
    }

    init {
        // Restore saved library URI and auto-scan - same guard as before
        // (`uri != null && novels.isEmpty()`), just reading librarySession's
        // StateFlow instead of MainActivity's mutableStateListOf directly.
        viewModelScope.launch {
            prefsManager.libraryUri.collect { uri ->
                if (uri != null && librarySession.novels.value.isEmpty()) {
                    try {
                        librarySession.scan(Uri.parse(uri))
                    } catch (e: Exception) {
                        // librarySession.scan() already fails soft internally; this
                        // outer catch is the same last-line-of-defense MainActivity
                        // used to keep around its own call to it.
                    }
                }
            }
        }
    }

    // Backs "Continue Reading" - looks up the last-read chapter for a novel, same
    // logic as MainActivity's onContinueReading. Returns null when there's no saved
    // progress (caller falls back to opening NovelDetail instead, same as before).
    suspend fun lastReadChapterFor(novel: NovelEntity) = run {
        val lastProgress = readingProgressRepository.forNovel(novel.id) ?: return@run null
        chapterRepository.findById(lastProgress.chapterId)
    }
}
