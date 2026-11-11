package com.arkster.app.core.navigation

/*
 * STAGE 3 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor. Full plan,
 * rationale and what each later stage does:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md -- that doc
 * is scaffolding for the refactor itself, not a permanent fixture: see its
 * own header for what to do with this pointer once the doc is gone.
 *
 * Replaces the `Screen` sealed class that used to live at the top of
 * MainActivity.kt. The two are deliberately NOT equivalent:
 *
 * - Screen's cases carried whole entities (`NovelDetail(val novel: NovelEntity)`,
 *   `Reader(val novelId: String, val chapter: ChapterEntity, val content: String)`).
 *   That's exactly why it couldn't survive a Bundle round-trip (an Activity
 *   recreation from process death) - Screen itself was never saved anywhere.
 * - Every route below carries IDs only. Each destination's ViewModel re-derives
 *   the entity it needs from the DB via its SavedStateHandle (see each
 *   feature module's ViewModel.kt (e.g. NovelDetailViewModel.kt's
 *   `checkNotNull(savedStateHandle[...Arg])`).
 *   NavController saves these route+args pairs (plain strings) as part of its
 *   own back stack Bundle automatically - this is the "Memento" piece Stage 3
 *   was for, and it falls out of switching to Navigation-Compose rather than
 *   needing to be hand-written.
 *
 * Pinned to navigation-compose 2.7.5 (declared since Stage 1's dependency
 * research, never used until now) - type-safe `@Serializable` routes are a
 * 2.8+ feature, so these are plain string route templates + NavType args,
 * the same pattern "Now in Android" used before 2.8 shipped. If this
 * dependency is ever bumped to 2.8+, these could become serializable data
 * classes instead - not required, but worth a look at that point.
 *
 * Screen.Author used to carry `from: Screen` so its onBack could return to
 * wherever the tap originated (fiction page or reader) instead of always
 * landing on Home. NavController's real back stack makes that field
 * unnecessary: `navController.popBackStack()` already returns to whatever
 * entry actually preceded this one. One of a few simplifications this
 * stage gets "for free" by routing navigation through a real stack instead
 * of a single mutable `currentScreen` value - see AppNavHost.kt.
 */

object HomeRoute {
    const val route = "home"
}

object FictionBrowseRoute {
    const val route = "browse"
}

object NovelDetailRoute {
    const val novelIdArg = "novelId"
    const val route = "novelDetail/{$novelIdArg}"
    fun createRoute(novelId: String) = "novelDetail/$novelId"
}

object ReaderRoute {
    const val novelIdArg = "novelId"
    const val chapterIdArg = "chapterId"
    const val route = "reader/{$novelIdArg}/{$chapterIdArg}"
    fun createRoute(novelId: String, chapterId: String) = "reader/$novelId/$chapterId"
}

// See bugs.md Bug 8: a distinct destination shown instead of Reader when
// getTextContent comes back ChapterLoadResult.Failed, rather than opening the
// reader on an empty chapter. Carries the same two IDs as ReaderRoute for the
// same reason - the Retry action on this screen needs both to try again.
object ChapterLoadErrorRoute {
    const val novelIdArg = "novelId"
    const val chapterIdArg = "chapterId"
    const val route = "chapterLoadError/{$novelIdArg}/{$chapterIdArg}"
    fun createRoute(novelId: String, chapterId: String) = "chapterLoadError/$novelId/$chapterId"
}

object ChapterEditorRoute {
    const val novelIdArg = "novelId"
    const val route = "chapterEditor/{$novelIdArg}"
    fun createRoute(novelId: String) = "chapterEditor/$novelId"
}

object AuthorRoute {
    const val authorIdArg = "authorId"
    const val route = "author/{$authorIdArg}"
    fun createRoute(authorId: String) = "author/$authorId"
}

object SettingsRoute {
    const val route = "settings"
}

// Settings used to apply the Theme radio group inline on itself; it's its own
// destination, part of turning Settings into an index (see SettingsScreen.kt).
object ThemeSettingsRoute {
    const val route = "themeSettings"
}

object AboutRoute {
    const val route = "about"
}

object PrivacyPolicyRoute {
    const val route = "privacyPolicy"
}

object TermsAndConditionsRoute {
    const val route = "termsAndConditions"
}
