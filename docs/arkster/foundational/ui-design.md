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

```kotlin
sealed class Screen {
    object Home : Screen()
    object FictionBrowse : Screen()
    object Library : Screen()          // legacy direct folder scan entry
    data class NovelDetail(val novel: NovelEntity) : Screen()
    data class Reader(val novelId: String, val chapter: ChapterEntity, val content: String) : Screen()
    data class ChapterEditor(val novel: NovelEntity) : Screen()
    object Settings : Screen()
    data class Author(val authorId: String, val from: Screen) : Screen()
}
```

Home is the initial screen; Library still exists as a direct
folder-scanning entry point. `Screen.Author` carries the screen it was
entered from so back returns to the fiction page or the chapter page,
whichever sent the user there, rather than always bouncing to Home.

## Visual language

Material 3 color scheme throughout, with a custom orange
(`#FF9800`) reserved for in-progress status badges. Rounded corners
(8–12dp), subtle elevation, gradient accents on featured content.
Headline-small for section titles, title-large for novel titles in
cards, label-small for metadata, body-small for descriptions; serif in
the reader specifically. Minimum 48dp touch targets throughout.

**Verified:** 2026-11-10 @ (this patch)
