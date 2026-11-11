# OOP modular-monolith refactor (staged plan)

## Status

**Stage 0 — documentation. Done.**
**Stage 1 — directory/package reorganization. Done.** Pure
`git mv` + package renames only; see the per-file `STAGE 1 MOVE` header
comments and the `core/README.md`, `domain/README.md`, `scanner/README.md`
and `feature/README.md` directory notes this patch added for exactly what
moved where and what, if anything, still touches each file in a later
stage. `MainActivity.kt` is still 1,041-ish lines and not yet thin (Stage
3); `ScannerImpl.kt` is still one 648-line class (Stage 4).
**Stage 2 — application-scoped services (Singleton). Done (this patch).**
New `App : Application` (see `App.kt`'s `STAGE 2 MOVE` header for the full
reasoning, including why its five properties stay `by lazy` rather than
built eagerly in `Application.onCreate()`). `MainActivity` now reaches
these through `get()` properties delegating to `app.db` etc. instead of
owning `lateinit var`s — every other reference to `db`/`scanner`/
`prefsManager`/`contentRepo`/`metadataProvider` in `MainActivity.kt` is
unchanged. This closes the config-change half of the gap described above;
the process-death half is still open until Stage 3's `SavedStateHandle`
work.
**Stage 3 — per-feature ViewModels. Done (this patch).** Open question #3
resolved as Jetpack Navigation-Compose (`NavController`/`NavHost`), not a
hand-rolled Mediator — see that question's entry below for why. One
`ViewModel` per `feature/` package (`HomeViewModel`, `FictionBrowseViewModel`,
`NovelDetailViewModel`, `ReaderViewModel`, `AuthorViewModel`,
`SettingsViewModel`), each created via `core/navigation/AppViewModelFactory.kt`
and backed by `SavedStateHandle` where it owns route args. `MainActivity` is
now the thin NavHost-host the target layout always described; everything it
used to own directly moved to `core/navigation/AppNavHost.kt` (the Mediator),
`core/navigation/Routes.kt` (route templates, replacing `Screen`),
`core/data/LibrarySession.kt` (the shared, App-scoped novel list/scan state —
see its header for why that's a Singleton rather than a ViewModel), and the
per-feature ViewModels themselves. This closes the process-death half of the
config-change/process-death gap: `NavController` persists the back stack
(route + args) through its own `Bundle`, and each ViewModel re-derives its
entity state from the DB via `SavedStateHandle` args rather than holding an
unparcelable entity directly, the way `Screen.Reader`'s old `content: String`
field could not survive one. See each new file's own `STAGE 3 MOVE` header
for exactly what moved from `MainActivity.kt` and why.
Stages 4–7 below are not started.

**Note on this patch's history:** the ViewModels, routes, and `AppNavHost`
that make up this stage were first committed (as "stage 2.5") with the
correct package declarations but the wrong file *paths* — all seven files
landed at the repo root instead of under `app/src/main/java/com/arkster/app/
…`, so Gradle never picked them up and the stray root copy of
`MainActivity.kt` sat inert next to the real one. This patch is that same
code, moved (`git mv`) to its correct location and completed: the draft
referenced several things that didn't exist yet (`LibrarySession`,
`NovelDetailViewModel`, `FictionBrowseViewModel`, `ReaderViewModel`,
`AppViewModelFactory`, `MetadataSearchState`'s new home, the shared
`EffectiveChapters.kt` helper Reader and NovelDetail both need for
Previous/Next) — all now written. Noted here, not just in a commit message,
because "half-finished and silently never built" is exactly the kind of gap
this doc exists to keep someone from rediscovering the hard way.

This doc itself is scaffolding for the refactor, not a permanent fixture:
once the refactor it describes is fully landed, this file is meant to go
away rather than linger as a historical record — `git log` is where that
history belongs (see the no-drift rule in `docs/README.md`). Every
in-code comment that points back here for context (`STAGE N MOVE` headers
and similar) is borrowing against that fact, so whoever retires this doc
owns giving that context a new home first — inlined at the call site, or
folded into a foundational doc — rather than leaving a link that quietly
goes stale. Noting the principle here, rather than a specific stage
number, so this note doesn't itself go stale if the staging plan below is
ever reshuffled, split, or renumbered.

## Motivation

ARKster's ~6,800 lines of Kotlin (23 files) are organized by *layer*
(`data/`, `ui/`) rather than by *feature*, and two files have grown into
god objects:

- **`MainActivity.kt`, 1,041 lines.** Every piece of UI state — `novels`,
  `chapters`, `arcs`, `currentScreen`, `scanProgress`,
  `metadataSearchState`, `readerAuthor`, and a dozen more — is a raw
  `mutableStateOf`/`mutableStateListOf` field directly on the `Activity`.
  Navigation is a sealed `Screen` plus an inline `when` inside
  `setContent`. Scan orchestration, metadata search/apply, crash
  rendering, and reader Previous/Next resolution all live here too,
  alongside the Compose tree itself.
- **`ScannerImpl.kt`, 648 lines.** One class does folder traversal, arc
  detection, chapter-filename parsing (a ranked list of heuristics),
  fingerprinting, cover resolution, `metadata.json` reading, and the
  whole `authors/` folder + avatar resolution pass.

### The config-change/process-death gap this causes

`AndroidManifest.xml` declares no `configChanges` for `MainActivity`, and
there is no `ViewModel` anywhere in the app — so every field listed above
lives and dies with the `Activity` instance. A rotation destroys and
recreates it from scratch: in-flight scan progress, the metadata-search
dialog state, and reader position all vanish and must be reloaded or
re-derived, because nothing was scoped to survive the recreation in the
first place. This is a real, user-visible gap, not a style complaint, and
closing it is as much a goal of this refactor as the directory
reorganization is.

### A second gap this refactor surfaces: `BookSource` isn't real yet

[`architecture.md`](../foundational/architecture.md) and
[`epub-support.md`](epub-support.md) both describe a `BookSource`
interface (`FolderSource`/`EpubSource`/`PdfSource`) with `architecture.md`
marking `FolderSource` **"shipped."** It isn't: searching `app/` for
`BookSource` or `FolderSource` turns up nothing but a comment in
`ScannerImpl.kt` ("Scanner implementation for FolderSource-like
behavior"). The actual interface only exists as an unwired proof-of-concept
under `spec/android_poc/src/main/kotlin/arkster/data/BookSource.kt`,
never imported by `app/`. `architecture.md`'s claim is corrected as part
of this patch (see "Files touched this stage" below) — this refactor is
what makes it true.

## Scope: directory structure, not features

This is a **pure internal restructuring**. No screen gains or loses a
feature, no visible behavior changes, and (outside of the `BookSource`
seam itself) no database schema changes. The deliverable is a codebase
organized **by feature, not by layer** — a modular monolith: one
deployable app, but with the internal seams a real module boundary would
have, so a feature's code, state, and tests live together instead of
being split across `data/`/`ui/` and reassembled inside `MainActivity`.
`MainActivity` ends this refactor as what its name always implied and
never was: a thin entry point/index that hosts navigation and nothing
else.

### Target package layout (Stage 1)

```
com.arkster.app/
  App.kt                       <- new Application class; owns app-scoped singletons
  MainActivity.kt               <- thin: NavHost + splash gate, nothing else
  core/
    database/                   <- AppDatabase, Dao.kt, Entities.kt, DatabaseDowngradeException.kt
    datastore/                  <- PreferencesManager.kt
    design/                     <- theme/AppTheme.kt moved here
    navigation/                 <- Screen.kt, the Mediator-style nav coordinator
  domain/
    booksource/                 <- BookSource.kt, FolderSource.kt (real, not spec/'s stub),
                                    EpubSource.kt once epub-support.md ships
    repository/                 <- NovelRepository, ChapterRepository, AuthorRepository
  scanner/                      <- ScannerImpl decomposed (see Stage 4)
  feature/
    home/                       <- HomeScreen.kt + HomeViewModel.kt
    library/                    <- legacy direct-scan entry point
    browse/                     <- FictionBrowseScreen.kt + ViewModel
    noveldetail/                <- NovelDetailScreen.kt, ChapterEditorScreen.kt + ViewModel
    reader/                     <- ReaderScreen.kt, ChapterLoadErrorScreen.kt + ViewModel
    author/                     <- AuthorPageScreen.kt + ViewModel
    settings/                   <- SettingsScreen.kt, ThemeSettingsScreen.kt, AboutScreen.kt,
                                    LegalDocumentScreen.kt, LegalContent.kt + ViewModel
    splash/                     <- SplashScreen.kt
```

This is a **package reorganization inside the existing single `app`
Gradle module** — not a split into separate Gradle modules. See "Open
questions" for why that bigger step isn't committed to here.

## Design decisions, mapped to the GoF patterns actually demonstrated in
[`docs/kotlin-design-patterns`](../../kotlin-design-patterns/README.md)

Every pattern below has a working Kotlin example in that vendored repo;
nothing here reaches for a pattern it doesn't also demonstrate.

- **Facade** — `ScannerImpl` becomes a thin facade in front of focused
  collaborators (`ChapterTitleParser`, `CoverResolver`, `AuthorScanner`,
  `FingerprintCalculator`), each independently testable, instead of one
  648-line class owning all of it.
- **Chain of Responsibility** — `parseChapter`'s ranked heuristics
  (leading number, `Chapter N` prefix, pure numeric filename, filename
  tokenization fallback) already *are* a chain; today they're inlined
  sequential checks instead of composed handler objects. Formalizing this
  means the same chain can be reused for EPUB's NCX/nav-title fallback
  matching (`epub-support.md`'s sort-tier section), instead of that plan
  writing its own copy.
- **Builder** — the existing-vs-scanned-vs-override merge in
  `ScannerImpl.scanChaptersForNovel` (today, long `copy()` calls with
  inline comments explaining field-by-field precedence) becomes one
  `NovelMergeBuilder`/`ChapterMergeBuilder` owning those rules in one
  place.
- **Abstract Factory** + **Bridge** — a `BookSourceFactory` produces the
  matching *family* of collaborators for a source type
  (`BookSource` + `ChapterContentRepository` + `CoverResolver` together),
  replacing the ad hoc URI-scheme dispatch `epub-support.md` currently
  proposes (`epub://...` parsed by a string-scheme check). The abstraction
  (what the reader needs — chapter content) stays decoupled from the
  implementation hierarchy (plain file vs. zip entry vs., later, a PDF
  page) via Bridge, the way `ChapterContentRepository` already gestures at
  but doesn't yet formalize.
- **Adapter** — platform/third-party types (SAF `DocumentFile` today; a
  future EPUB-parsing library, per `epub-support.md`'s open question 4)
  stay behind a stable internal interface instead of leaking into the
  scanner directly.
- **Observer** — per-feature `ViewModel`s expose `StateFlow`, observed via
  `collectAsStateWithLifecycle`, replacing `MainActivity`'s raw
  `mutableStateOf` fields as the single observable source of UI state.
- **Memento** — `SavedStateHandle` on each `ViewModel` snapshots the state
  that must survive process death (not just a config change): current
  screen args, in-flight metadata-search state, scan progress.
- **Singleton** — `db`, `scanner`, `prefsManager`, `contentRepo`, and
  `metadataProvider` move from `MainActivity` `lateinit var`s (rebuilt
  every recreation) to a new `App : Application` owning them once for the
  process's lifetime — see Stage 2, which lands before any `ViewModel`
  exists and already closes part of the config-change gap on its own.
- **Mediator** — a navigation coordinator (or Jetpack Navigation-Compose's
  `NavHost`, see "Open questions") replaces `MainActivity`'s inline
  `when (currentScreen.value)` branching, so screens stop reaching into
  shared Activity-held state to navigate each other.
- **State** — `Screen`'s sealed-class cases already resemble the State
  pattern; today the transition logic and each state's own behavior are
  both jammed into `MainActivity`'s Compose body instead of living with
  the state. Tightens naturally alongside the Mediator-based coordinator.
- **Composite** — the Arc→Chapter nesting ("All Chapters" is a flattened
  composite view across arcs) and, later, an EPUB NCX/nav tree
  (`epub-support.md` Stage 4's "deeply nested nav trees" question) are
  both naturally Composite structures.
- **Decorator** — `ChapterOverrideEntity` layering a title/position change
  on top of a scanned chapter without touching the scanned row is
  conceptually a Decorator; worth stating explicitly rather than leaving
  it as an unnamed convention.
- **Proxy** — cache-on-first-access resolution (today: cover/avatar URIs;
  soon: `epub-support.md`'s planned cover-extraction-to-cache step) is a
  caching Proxy in front of the real source (zip bytes, SAF query).
- **Visitor** — *not scheduled.* Flagged only as a candidate for a future
  cross-cutting tree operation (e.g. the still-open "Total Words" stat in
  [`author-and-reader-model.md`](../foundational/author-and-reader-model.md)).
  Nothing in the current codebase needs it yet; listed here so it isn't
  silently forgotten when that need shows up.

## Different from ARKarium, on purpose

ARKarium forked ARKster's scanner, sort-tier, and author-page logic early
(see [`what-is-arkster.md`](../foundational/what-is-arkster.md)) and has
since layered its own priorities on top — relay sync, notifications,
per-chapter read-event stats, character-aware TTS — on a codebase that has
already diverged from this one. This refactor is **not** meant to be
back-ported to ARKarium or kept in lockstep with it. Bugfixes to shared
logic are still worth porting there (per the existing policy); a purely
internal reorganization of *this* repo's directory structure is not.

## Staging order

### Stage 0 — Documentation. Done.
This file, plus the `architecture.md` correction noted below. No app code
touched.

### Stage 1 — Directory/package reorganization. Done.
Moved files into the target layout above. Pure `git mv` plus package-name
updates — no logic changes, no behavior changes. `MainActivity.kt` is not
yet thin at the end of this stage (that's Stage 3); this stage only
establishes where things live. Two placements weren't explicit in the
target tree above and were decided during the move, each flagged with a
file-header comment so a later stage doesn't have to rediscover the
reasoning: `ChapterContentRepository.kt` → `domain/repository/` (it already
was a repository, ahead of Stage 6 formalizing the rest) and
`NovelMetadataProvider.kt` → `domain/metadata/` (a data-access concern,
same reasoning).

### Stage 2 — Application-scoped services (Singleton). Done.
New `App : Application` owns `db`/`scanner`/`prefsManager`/`contentRepo`/
`metadataProvider` for the process's lifetime, each `by lazy` so first
construction still happens inside `MainActivity.onCreate`'s existing
try/catch (preserving the crash-screen behavior for a bad
`AppDatabase.create()`) rather than in `Application.onCreate()`, which runs
before that guard exists. This alone stops those services churning on
every rotation, ahead of any `ViewModel` work.

### Stage 3 — Per-feature ViewModels (Observer + Memento + Mediator + State). Done.
One `ViewModel` per `feature/` package, each exposing `StateFlow` and backed
by `SavedStateHandle` for anything that must survive process death (route
args only — see `Routes.kt`). The Mediator is Navigation-Compose's own
`NavController`/`NavHost` (`AppNavHost.kt`), resolving open question #3 in
favor of the more idiomatic, less bespoke option rather than a hand-rolled
`Screen` + coordinator class. `MainActivity`'s `when`-based navigation and
every per-screen field it used to own are gone; what's left is exactly the
thin NavHost-host the target layout described. This closes the
config-change/process-death gap described above in full — Stage 2 already
had the config-change half.

### Stage 4 — `ScannerImpl` decomposition (Facade + Chain of Responsibility + Builder). Not started.
Split into the thin facade plus `ChapterTitleParser`, `CoverResolver`,
`AuthorScanner`, `FingerprintCalculator`, and a merge `Builder`, as
described above. Same inputs and outputs as today; different internal
seams.

### Stage 5 — Real `BookSource`/`ChapterContentRepository` (Abstract Factory + Bridge + Adapter). Not started.
Promote `spec/android_poc`'s stub into `domain/booksource/`, implement
`FolderSource` for real against the scanning logic from Stage 4, and add
the `BookSourceFactory`. Corrects `architecture.md`'s "shipped" claim for
real. `epub-support.md`'s own Stage 1 should land against this seam
instead of inventing its own URI-scheme dispatcher, if this stage ships
first; if EPUB support starts first instead, its dispatcher gets absorbed
into the factory here rather than the other way around. Whichever lands
first, the other plan's corresponding stage should be re-read before it
starts.

### Stage 6 — Repository layer over `Dao` (Repository + Proxy). Not started.
`NovelRepository`/`ChapterRepository`/`AuthorRepository` wrap the existing
`Dao`s as the single source of truth `ViewModel`s depend on — nothing
above `domain/` touches a `Dao` directly after this stage. Cover/avatar
cache-on-first-access becomes an explicit Proxy.

### Stage 7 — QA / polish. Not started.
Rotate every screen; kill the process via "Don't keep activities" while
backgrounded; confirm state survives via `SavedStateHandle` and that
`StateFlow` resubscribes correctly; confirm the Stage 2 singletons aren't
duplicated; confirm `epub-support.md`'s plan still applies cleanly against
whichever of Stage 5/EPUB landed second.

## Explicitly out of scope

- **Any new user-facing feature or visible behavior change.** This plan
  only moves and reshapes code.
- **A Gradle multi-module split** (separate `:core`, `:domain`,
  `:feature:*` build modules with real build-time boundaries, build-time
  parallelization, etc.) — see "Open questions."
- **Database schema changes**, except whatever Stage 6's repository
  extraction incidentally requires at the call-site level; no new Room
  migration is expected.
- **Adopting a DI framework** (Hilt/Koin) — see "Open questions."

## Open questions

1. Hand-rolled `Application`-scoped singletons (Stage 2) vs. a DI
   framework (Hilt/Koin)? Leaning hand-rolled — the app is currently
   single-module and small enough that a new build dependency isn't
   obviously worth it yet. Revisit if `feature/` modules multiply enough
   that manual wiring gets unwieldy.
2. Package-by-feature within the single `app` module (as staged above) vs.
   true Gradle multi-module separation? This plan deliberately starts with
   the former — lower risk, no build-script changes, reversible. A real
   module split is a plausible future follow-on, not committed to here.
3. ~~Hand-rolled `Screen` sealed class + Mediator coordinator (what Stage 3
   assumes) vs. migrating to Jetpack Navigation-Compose's `NavHost`/
   `NavController`?~~ **Resolved for Stage 3: Navigation-Compose.** More
   idiomatic, gets deep-linking for free, and `navigation-compose:2.7.5` was
   already a declared-but-unused dependency since Stage 1's research (see
   `Routes.kt`'s header on why routes are plain string templates rather than
   2.8+'s `@Serializable` routes, given that pinned version). The one-time
   call-site rewrite this implied landed as part of Stage 3 itself.
4. Where would Visitor actually earn its place? Not needed to start any
   stage above; revisit only once a real cross-cutting tree operation
   (e.g. a word-count stat) is actually being built.

Remaining open items should be resolved before the Stage they block
begins.

**Verified:** 2026-11-11 @ (this patch)
