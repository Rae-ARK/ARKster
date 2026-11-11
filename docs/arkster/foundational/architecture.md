# Architecture

How ARKster is built: the storage model, the abstractions that keep file
formats out of the UI, and the feature set shipped so far. See
[`what-is-arkster.md`](what-is-arkster.md) for identity/scope and
[`scanner-invariants.md`](scanner-invariants.md) for the scanner's hard
rules specifically.

## Storage: Room + DataStore

- **Room** (structured, queryable): `novels`, `arcs`, `chapters`,
  `authors`, `chapter_overrides`, `reading_progress`, `scan_fingerprints`,
  `cached_metadata`.
- **DataStore** (lightweight preferences): `selected_library_uri`,
  `reader_theme`, `default_font_size`, `line_height`,
  `pagination_defaults`, `network_metadata_enabled`.

Room holds anything queried, sorted, or joined; DataStore holds flat app
prefs. This split, not a single store for everything, is deliberate: it
keeps the indexed library state (which can be rebuilt from the folder by a
rescan) separate from user preferences (which can't).

## BookSource abstraction — real, partially wired

```kotlin
interface BookSource {
  suspend fun listChapters(): List<Chapter>
  suspend fun getMetadata(): BookMetadata
  suspend fun fingerprint(): SourceFingerprint
}

class FolderSource(folder, novelId, ...) : BookSource { ... }  // real — domain/booksource/FolderSource.kt
class EpubSource : BookSource { ... }    // not started — see implementation/epub-support.md
class PdfSource  : BookSource { ... }    // not started — see proposal/README.md
```

This is the abstraction the scanner and reader are meant to be built on —
UI and ViewModels consuming only the normalized `Chapter`/`BookMetadata`
model, with each source hiding its own format's specifics (loose files vs.
a ZIP container vs. a page-based document). `BookSource`, `BookSourceFactory`,
and a real `FolderSource` now exist under `domain/booksource/` (promoted
from the formerly-unwired proof-of-concept at
`spec/android_poc/src/main/kotlin/arkster/data/BookSource.kt`), per Stage 5
of
[`implementation/oop-modular-monolith-refactor.md`](../implementation/oop-modular-monolith-refactor.md).

Two things this does **not** yet mean, stated plainly since this section
previously had to walk back an earlier overclaim of "shipped":

- `ScannerImpl.scanChaptersForNovel` does not call through `FolderSource`
  for its main chapter-parsing pass — it still walks a novel's root and arc
  subfolders directly, because arc detection and the chapter_overrides-
  preserving diff logic have nowhere to go in `FolderSource`'s current flat
  `Chapter` model. `FolderSource` today correctly handles a flat, arc-less
  folder; a novel with arcs still goes through `ScannerImpl`'s own code
  path, unchanged.
- `EpubSource`/`PdfSource` still don't exist. What **is** wired in is
  `BookSourceFactory` recognizing a `.epub`/`.pdf` file at the scan root as
  a format without an implementation yet, and `ScannerImpl.scanRoot`
  surfacing that to the user as a dialog (`LibrarySession.unsupportedSources`
  → `HomeScreen`'s `UnsupportedSourcesDialog`) instead of the file silently
  vanishing, which is what every unrecognized scan-root file still does.

## ChapterContentRepository

```kotlin
interface ChapterContentRepository {
  suspend fun getContent(chapterId: Long): ChapterContent
}
```

One API for chapter content, regardless of source. It hides ZIP entry
extraction, temp files, or page rendering; the UI receives
`ChapterContent` (today, always the `Text` case) and renders it the same
way no matter where it came from.

## Scanning

- `ScannerImpl` enumerates the root folder's immediate children via
  `DocumentFile.fromTreeUri()` and parses each one directly — not yet
  through a `BookSource` per entry; see the correction above.
- Each novel's folder may contain chapters as loose files, arcs as
  subfolders, or (once `implementation/epub-support.md` ships) a single
  `.epub` file.
- A per-document fingerprint (`uri`, `lastModified`, `size`) is persisted
  per novel folder; a rescan diffs against it and only reparses novels
  that changed. `CURRENT_SCAN_VERSION` is bumped whenever scanner *logic*
  changes, forcing one full reparse of every novel regardless of
  fingerprint.
- Reading order within a novel is: root-level chapters first, then each
  arc in its own position order, with bonus/closing content sorted after
  regular chapters inside its own grouping — see
  [`scanner-invariants.md`](scanner-invariants.md) for the exact rules and
  the bugs that taught them.

## Reading progress

```kotlin
ReadingProgress {
  novelId
  chapterId
  position
  positionType  // CHAR_OFFSET, PAGE_INDEX, EPUB_CFI, PERCENTAGE, ...
  updatedAt
}
```

`position` is interpreted according to `positionType`, per source. Text
chapters use `PERCENTAGE`/`CHAR_OFFSET` today; `EPUB_CFI` is a documented,
currently-unused value reserved for if/when EPUB chapters get real HTML
rendering instead of the flattened plain text they ship with today.

## Shipped feature set (v0.1 → v0.2)

- SAF root folder selection, persisted `Uri` permission.
- Folder-based scanning (`ScannerImpl`, direct — not yet behind
  `FolderSource`) for `.txt`/`.md`, with ranked heuristics for chapter
  numbering/titling and graceful fallbacks for messy folders
  (auto-generated covers, best-effort title normalization).
- Arc detection by folder-name pattern (`Arc`, `Volume`, `Book`, `Part`,
  plus CJK variants), with per-arc cover art and position ordering.

### Arc semantics across sources

`Arc` is one model (`ArcEntity`, position-ordered) but means something
different depending on where a novel came from, and the distinction is in
the data's origin, not in how it's stored or queried:

- **Folder-scanned web novels:** an Arc is an optional, human-imposed
  grouping — a subfolder someone named `Arc 2` for their own navigation
  comfort. A WN with no such subfolders has no arcs at all; its chapters
  are all root-level.
- **EPUB/PDF:** an Arc *is* a Volume, 1:1 — the format's actual native
  unit, not a convenience. Every chapter belongs to exactly one volume;
  there is no root-level-chapter case the way a flat WN folder has.

This needs no divergent code path, because `ChapterDao.forNovel`'s
existing ordering already covers both: root-level chapters first (by
`CASE WHEN chapters.arc_id IS NULL THEN 0 ELSE 1 END`), then each arc's
chapters in `arcs.position` order, natural order within an arc. For a
flat WN that's "ungrouped chapters, then each Arc-subfolder's chapters
block"; for an EPUB/PDF with no root-level chapters, the same query
reduces to "Vol 1's chapters, then Vol 2's, then Vol 3's, and so on" —
i.e. the "All Chapters" flat view *is* volumes concatenated in position
order, not a separate ordering scheme. Per-arc chapter lists (an arc's
own page) are just that arc's slice of the same ordering. See
`ChapterRepository.getEffectiveChapters()` for where this query is
consumed.
- Per-novel pagination preference (10/20/50/100), persisted in Room.
- Manual chapter override editor (reorder, rename, mark arc boundaries),
  layered on top of scanned data and diff-persisted so it survives a
  rescan.
- Incremental/diff-based rescan: unchanged novels are skipped by
  fingerprint; changed ones are upserted in place; novels whose folder is
  genuinely gone are reconciled away — see
  [`scanner-invariants.md`](scanner-invariants.md)'s deletion-safety
  rules.
- Author pages and the chapter-page "About the author" card, backed by
  `authors/<id>.json` — see
  [`author-and-reader-model.md`](author-and-reader-model.md).
- Royal Road-inspired Home, Fiction Browse, and Reader screens — see
  [`ui-design.md`](ui-design.md).
- Reading status (`NOT_STARTED`/`IN_PROGRESS`/`COMPLETED`) and a Continue
  Reading entry point back to the last-read chapter.

Database migrations to date: `v1 → v2` (page size, arcs,
chapter_overrides, scan_fingerprints) → `v3` (reading_progress) → `v4`
(reading_status) → ... → `v7 → v8` (chapter `sort_tier`, see
[`scanner-invariants.md`](scanner-invariants.md)).

## v0.3

Bugfix-only release: the fixes behind every rule in
[`scanner-invariants.md`](scanner-invariants.md). No feature-level change
to the set above.

## Not yet built

- EPUB support — in progress, see
  [`implementation/epub-support.md`](../implementation/epub-support.md).
- PDF support, a metadata provider, and other ideas not yet started — see
  [`proposal/`](../proposal/README.md).

**Verified:** 2026-11-11 @ (this patch)
