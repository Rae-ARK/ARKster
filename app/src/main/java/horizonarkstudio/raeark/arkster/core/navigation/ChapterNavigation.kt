package horizonarkstudio.raeark.arkster.core.navigation

/*
 * STAGE 3 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor. Full plan,
 * rationale and what each later stage does:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md -- that doc
 * is scaffolding for the refactor itself, not a permanent fixture: see its
 * own header for what to do with this pointer once the doc is gone.
 *
 * Moved verbatim from MainActivity.openChapter (see bugs.md Bug 8 for why
 * this split - Ok/Failed - exists at all), now navigating instead of
 * assigning currentScreen. This has to live outside any single ViewModel:
 * it's the decision of WHICH destination to push (Reader vs
 * ChapterLoadError), made before either destination's ViewModel exists yet,
 * so it's called directly from the three places that open a chapter fresh
 * (Home's onContinueReading, NovelDetail's onChapterSelected,
 * ChapterLoadErrorScreen's onRetry) rather than from inside ReaderViewModel.
 * ReaderViewModel's own Previous/Next (which run once a ReaderViewModel
 * already exists) make the same Ok/Failed decision themselves and report a
 * failure via ReaderNavEvent instead - see its header for why those two
 * cases can't share this one function directly despite doing the same
 * check.
 *
 * `replaceCurrent` is true for ChapterLoadErrorScreen's onRetry: retrying
 * should replace the error screen in the back stack (success -> Reader,
 * repeat failure -> a fresh ChapterLoadError for the same chapter) rather
 * than stacking error screens or readers behind it. It's false for the two
 * first-open call sites, which should behave like any normal forward
 * navigation.
 */

import androidx.navigation.NavController
import horizonarkstudio.raeark.arkster.core.database.ChapterEntity
import horizonarkstudio.raeark.arkster.domain.repository.ChapterLoadResult
import horizonarkstudio.raeark.arkster.domain.repository.TextChapterContentRepository

// Known tradeoff: this reads the chapter's content once here just to decide Ok vs
// Failed, then ReaderViewModel.init reads it again once navigation lands, since
// ReaderRoute only carries IDs (see Routes.kt) rather than the content string
// itself - a second file/SAF read per chapter open that the pre-Stage-3 code
// didn't pay, in exchange for the reader surviving process death by ID instead of
// by an un-persistable in-memory string. getTextContent() is a local read with no
// network involved, so this is accepted rather than solved here; worth revisiting
// only if that read ever becomes slow enough to notice.
suspend fun navigateToChapter(
    navController: NavController,
    contentRepo: TextChapterContentRepository,
    novelId: String,
    chapter: ChapterEntity,
    replaceCurrent: Boolean = false
) {
    val targetRoute = when (contentRepo.getTextContent(chapter.sourcePath)) {
        is ChapterLoadResult.Ok -> ReaderRoute.createRoute(novelId, chapter.id)
        is ChapterLoadResult.Failed -> ChapterLoadErrorRoute.createRoute(novelId, chapter.id)
    }
    navController.navigate(targetRoute) {
        if (replaceCurrent) {
            popUpTo(navController.currentBackStackEntry?.destination?.route ?: targetRoute) {
                inclusive = true
            }
        }
    }
}
