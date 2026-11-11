# UI design

The Royal Road-inspired visual language: screens, reading modes,
typography controls, and navigation flow. States what's shipped now; see
[`architecture.md`](architecture.md) for the data these screens are built
on.

## Home screen

Landing screen (`Screen.Home` is the app's initial screen). Carousels:
Featured (hero banner for one novel), Continue Reading (in-progress
novels with a Continue button), Trending, and New Releases, plus a search
bar and a Browse-all call to action.

## Fiction Browse screen

Two-column grid of novels, with:
- **Sort**: Recently Updated (default), Most Popular, Highest Rated,
  Newest.
- **Filter**: All (default), In Progress, Completed, Not Started —
  shown as active filter chips.
- Status badges per card: Reading (orange), Done (tertiary), New
  (primary).

## Fiction (novel detail) page

Cover + metadata header, tabbed "All Chapters" / per-arc view, chapter
list with per-fiction pagination (10/20/50/100, persisted per novel). An
"Edit" action opens the chapter override editor (reorder, rename, mark
arc boundaries). When a specific arc tab (not "All Chapters") is selected,
a small cover + arc name + chapter-count header appears above that arc's
chapters.

## Reader

- **Reading modes**: Light (white background, dark text), Sepia (warm
  paper background `#F5ECD9`, brown text `#3E2C1C`), Dark (`#1A1A1A`
  background, light text). Toggled from a bottom control panel.
- **Typography controls**: font size 12sp–28sp (default 18sp) and line
  height 1.0x–2.5x (default 1.8x), both adjustable from the same panel.
- Serif font, justified body text, a progress indicator as a percentage,
  and the chapter title at the top of the content.
- Cover thumbnail (arc → novel → placeholder, see
  [`scanner-invariants.md`](scanner-invariants.md)), "back to fiction"
  link, Previous/Next navigation, and the "About the author" card — see
  [`author-and-reader-model.md`](author-and-reader-model.md) for all
  three.

## Navigation

As of the Stage 3 refactor (2026-11-11), navigation is Navigation-Compose
with plain string route templates, not the `Screen` sealed class shown in
earlier drafts of this doc — that class was deleted and its cases replaced
one-for-one in [`Routes.kt`](../../../app/src/main/java/com/arkster/app/core/navigation/Routes.kt):

| Route object            | Path template                          |
|--------------------------|-----------------------------------------|
| `HomeRoute`              | `home` (start destination)              |
| `FictionBrowseRoute`     | `browse`                                |
| `NovelDetailRoute`       | `novelDetail/{novelId}`                 |
| `ReaderRoute`            | `reader/{novelId}/{chapterId}`          |
| `ChapterLoadErrorRoute`  | `chapterLoadError/{novelId}/{chapterId}`|
| `ChapterEditorRoute`     | `chapterEditor/{novelId}`               |
| `AuthorRoute`            | `author/{authorId}`                     |
| `SettingsRoute`          | `settings`                              |
| `ThemeSettingsRoute`     | `themeSettings`                         |
| `AboutRoute`             | `about`                                 |
| `PrivacyPolicyRoute`     | `privacyPolicy`                         |
| `TermsAndConditionsRoute`| `termsAndConditions`                    |

Home is the start destination. There is no `Library` route — the old
"legacy direct folder scan entry point" case was dropped with the rest of
`Screen` in Stage 3 and should not have still been listed here; folder
scanning is reached through Settings. The old `Screen.Author.from` field
(which screen to return to) is gone too: `NavController` keeps a real back
stack, so `popBackStack()` already returns to whichever entry — fiction
page or chapter page — actually preceded Author.

One exception to "every screen is a route": the splash screen
(`feature/splash/SplashScreen.kt`) is not in the table above and has no
route. `MainActivity` shows it in front of the `NavHost` entirely, gated by
a local boolean, and swaps to the real nav graph once it finishes. That's
deliberate — you don't want back navigation able to return to a splash —
but it does mean splash can't be deep-linked to and never appears on the
back stack, unlike every other screen in the app.

## Visual language

Material 3 color scheme is the intent, though it's not consistently
sourced yet: the reader's sepia/dark/light palette
(`feature/reader/ReaderScreen.kt`) and the theme picker's seed colors
(`feature/settings/ThemeSettingsScreen.kt`) are each hand-rolled with
their own hardcoded hex literals rather than both reading from one
`MaterialTheme` color scheme — tracked as a follow-up, not yet fixed.
A custom orange (`#FF9800`) is reserved for in-progress status badges. Rounded corners
(8–12dp), subtle elevation, gradient accents on featured content.
Headline-small for section titles, title-large for novel titles in
cards, label-small for metadata, body-small for descriptions; serif in
the reader specifically. Minimum 48dp touch targets throughout.

**Verified:** 2026-11-11 @ (this patch) — the 2026-11-10 verification predated
the Stage 3 navigation refactor by less than a day; the Navigation section
above was rewritten against the current `Routes.kt`/`AppNavHost.kt` rather
than the deleted `Screen` sealed class.
