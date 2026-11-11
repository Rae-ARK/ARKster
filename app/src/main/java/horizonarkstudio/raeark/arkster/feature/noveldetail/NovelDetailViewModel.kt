package horizonarkstudio.raeark.arkster.feature.noveldetail

/*
 * STAGE 3 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor. Full plan,
 * rationale and what each later stage does:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md -- that doc
 * is scaffolding for the refactor itself, not a permanent fixture: see its
 * own header for what to do with this pointer once the doc is gone.
 *
 * Backs BOTH NovelDetailRoute and ChapterEditorRoute (see AppNavHost.kt) -
 * each destination gets its own instance (different NavBackStackEntry),
 * loading independently from the same novelId route arg, the same way the
 * target package layout's comment describes "NovelDetailScreen.kt,
 * ChapterEditorScreen.kt + ViewModel" (singular) for this package.
 *
 * `novel`/`chapters`/`arcs`/`overriddenChapterIds`/`arcStartChapterIds`/
 * `metadataSearchState` moved verbatim (logic, not structure) from
 * MainActivity's loadNovelDetails/fetchMetadataFor/applyMetadata/
 * saveChapterEdits and the MetadataSearchState sealed class that used to
 * sit at the top of MainActivity.kt. `load()` is called once from `init`
 * for the initial display, and again from AppNavHost's
 * DisposableEffect(ON_RESUME) so returning here after a ChapterEditor save
 * (a different NovelDetailViewModel instance) picks up the change - this
 * ViewModel owns a private snapshot rather than observing a shared Flow the
 * way FictionBrowseViewModel's `novels` does, since chapters/overrides
 * don't change anywhere else while this screen isn't visible.
 *
 * STAGE 6 MOVE -- 2026-11-11
 * Took `db: AppDatabase` before this patch and called novelDao()/
 * chapterDao()/chapterOverrideDao()/arcDao() directly - now takes
 * NovelRepository/ChapterRepository/ArcRepository instead.
 * `loadEffectiveChapters(db, novelId)` becomes
 * `chapterRepository.getEffectiveChapters(novelId)` - see
 * ChapterRepository's own STAGE 6 MOVE header for why that merge logic
 * moved there (EffectiveChapters.kt is deleted this patch).
 */

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import horizonarkstudio.raeark.arkster.core.data.LibrarySession
import horizonarkstudio.raeark.arkster.core.database.ArcEntity
import horizonarkstudio.raeark.arkster.core.database.ChapterEntity
import horizonarkstudio.raeark.arkster.core.database.ChapterOverrideEntity
import horizonarkstudio.raeark.arkster.core.database.NovelEntity
import horizonarkstudio.raeark.arkster.core.database.NovelStatus
import horizonarkstudio.raeark.arkster.core.navigation.NovelDetailRoute
import horizonarkstudio.raeark.arkster.domain.metadata.GoogleBooksMetadataProvider
import horizonarkstudio.raeark.arkster.domain.metadata.NovelMetadataCandidate
import horizonarkstudio.raeark.arkster.domain.repository.ArcRepository
import horizonarkstudio.raeark.arkster.domain.repository.ChapterRepository
import horizonarkstudio.raeark.arkster.domain.repository.NovelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

// Drives the "Fetch info" dialog from NovelDetailScreen. Idle = dialog hidden.
sealed class MetadataSearchState {
    object Idle : MetadataSearchState()
    data class Loading(val novel: NovelEntity) : MetadataSearchState()
    data class Results(val novel: NovelEntity, val candidates: List<NovelMetadataCandidate>) : MetadataSearchState()
    data class Error(val novel: NovelEntity, val message: String) : MetadataSearchState()
}

class NovelDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val novelRepository: NovelRepository,
    private val chapterRepository: ChapterRepository,
    private val arcRepository: ArcRepository,
    private val metadataProvider: GoogleBooksMetadataProvider,
    private val librarySession: LibrarySession
) : ViewModel() {

    private val novelId: String = checkNotNull(savedStateHandle[NovelDetailRoute.novelIdArg])

    private val _novel = MutableStateFlow<NovelEntity?>(null)
    val novel: StateFlow<NovelEntity?> = _novel.asStateFlow()

    private val _chapters = MutableStateFlow<List<ChapterEntity>>(emptyList())
    val chapters: StateFlow<List<ChapterEntity>> = _chapters.asStateFlow()

    private val _arcs = MutableStateFlow<List<ArcEntity>>(emptyList())
    val arcs: StateFlow<List<ArcEntity>> = _arcs.asStateFlow()

    private val _overriddenChapterIds = MutableStateFlow<Set<String>>(emptySet())
    val overriddenChapterIds: StateFlow<Set<String>> = _overriddenChapterIds.asStateFlow()

    private val _arcStartChapterIds = MutableStateFlow<Set<String>>(emptySet())
    val arcStartChapterIds: StateFlow<Set<String>> = _arcStartChapterIds.asStateFlow()

    private val _metadataSearchState = MutableStateFlow<MetadataSearchState>(MetadataSearchState.Idle)
    val metadataSearchState: StateFlow<MetadataSearchState> = _metadataSearchState.asStateFlow()

    init {
        load()
    }

    // Loads the novel entity plus the effective (overrides-applied) chapter list -
    // see ChapterRepository.getEffectiveChapters for why that merge lives there
    // rather than being duplicated here and in ReaderViewModel.
    fun load() {
        viewModelScope.launch {
            _novel.value = novelRepository.findById(novelId)
            val effective = chapterRepository.getEffectiveChapters(novelId)
            _chapters.value = effective.chapters
            _overriddenChapterIds.value = effective.overriddenChapterIds
            _arcStartChapterIds.value = effective.arcStartChapterIds
            _arcs.value = arcRepository.forNovel(novelId)
        }
    }

    // Called right before navigating into the reader (see AppNavHost's
    // onChapterSelected) - marks the novel IN_PROGRESS and refreshes
    // LibrarySession's in-progress/recently-read lists so Home picks up the
    // change, same as MainActivity's onChapterSelected used to do inline.
    fun markChapterOpened() {
        viewModelScope.launch {
            novelRepository.updateReadingStatus(novelId, NovelStatus.IN_PROGRESS.name)
            librarySession.refreshRecentlyRead()
        }
    }

    fun resizePages(pageSize: Int) {
        viewModelScope.launch {
            novelRepository.updatePageSize(novelId, pageSize)
        }
    }

    // Kicks off a "Fetch info" search for this novel. User-triggered only (see
    // NovelDetailScreen's info action) - never called automatically during a scan.
    fun fetchMetadata() {
        val currentNovel = _novel.value ?: return
        _metadataSearchState.value = MetadataSearchState.Loading(currentNovel)
        viewModelScope.launch {
            try {
                val results = metadataProvider.search(currentNovel.title)
                _metadataSearchState.value = MetadataSearchState.Results(currentNovel, results)
            } catch (e: Exception) {
                _metadataSearchState.value = MetadataSearchState.Error(
                    currentNovel,
                    "Couldn't reach the metadata source: ${e.message}"
                )
            }
        }
    }

    // Persists a user-confirmed match and refreshes this ViewModel's own `novel` -
    // unlike MainActivity's version, there's no separate `novels` list copy to patch
    // here (that lives in LibrarySession, refreshed on its own next scan); Home/
    // Browse showing stale info for this one novel until their next reload is
    // accepted rather than threading a second update path through here for it.
    fun applyMetadata(candidate: NovelMetadataCandidate) {
        val currentNovel = _novel.value ?: return
        viewModelScope.launch {
            try {
                novelRepository.updateMetadata(
                    novelId = currentNovel.id,
                    description = candidate.description,
                    genres = candidate.categories.joinToString(", ").ifBlank { null },
                    remoteCoverUrl = candidate.thumbnailUrl,
                    publishedDate = candidate.publishedDate,
                    externalSourceUrl = candidate.infoLink,
                    fetchedAt = System.currentTimeMillis()
                )
                _novel.value = novelRepository.findById(currentNovel.id)
                _metadataSearchState.value = MetadataSearchState.Idle
            } catch (e: Exception) {
                _metadataSearchState.value = MetadataSearchState.Error(
                    currentNovel,
                    "Couldn't save the fetched info: ${e.message}"
                )
            }
        }
    }

    fun dismissMetadataSearch() {
        _metadataSearchState.value = MetadataSearchState.Idle
    }

    // Diffs the editor's edited chapter list against the raw scanned chapters and
    // persists per-chapter overrides only where something actually changed. If an
    // edit matches the scanned default again, any stale override for that chapter is
    // removed instead of being kept around with stale values.
    suspend fun saveChapterEdits(edited: List<ChapterEntity>, arcStartIds: Set<String>) {
        val raw = chapterRepository.forNovel(novelId) // natural scan order, no overrides
        val rawById = raw.associateBy { it.id }
        val rawIndexById = raw.withIndex().associate { (i, c) -> c.id to i }

        edited.forEachIndexed { index, chapter ->
            val original = rawById[chapter.id] ?: return@forEachIndexed
            val titleOverride = if (chapter.title != original.title) chapter.title else null
            val positionOverride = if (index != rawIndexById[chapter.id]) index else null
            val isArcStart = chapter.id in arcStartIds

            if (titleOverride != null || positionOverride != null || isArcStart) {
                chapterRepository.upsertOverride(
                    ChapterOverrideEntity(
                        id = UUID.nameUUIDFromBytes("override:${chapter.id}".toByteArray()).toString(),
                        chapterId = chapter.id,
                        titleOverride = titleOverride,
                        positionOverride = positionOverride,
                        isArcStart = isArcStart
                    )
                )
            } else {
                chapterRepository.deleteOverride(chapter.id)
            }
        }
        load()
    }
}
