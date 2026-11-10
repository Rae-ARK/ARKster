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

## BookSource abstraction

```kotlin
interface BookSource {
  suspend fun listChapters(): List<Chapter>
  suspend fun getMetadata(): BookMetadata
}

class FolderSource : BookSource { ... }  // shipped
class EpubSource : BookSource { ... }    // see implementation/epub-support.md
class PdfSource  : BookSource { ... }    // not yet started
```

UI and ViewModels consume only the normalized `Chapter`/`BookMetadata`
model; each source hides its own format's specifics (loose files vs. a ZIP
container vs. a page-based document).

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
  `DocumentFile.fromTreeUri()` and builds an appropriate `BookSource` per
  entry.
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
- `FolderSource` scanning for `.txt`/`.md`, with ranked heuristics for
  chapter numbering/titling and graceful fallbacks for messy folders
  (auto-generated covers, best-effort title normalization).
- Arc detection by folder-name pattern (`Arc`, `Volume`, `Book`, `Part`,
  plus CJK variants), with per-arc cover art and position ordering.
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

## Not yet built

- EPUB support — in progress, see
  [`implementation/epub-support.md`](../implementation/epub-support.md).
- PDF support, a metadata provider, and other ideas not yet started — see
  [`proposal/`](../proposal/README.md).

**Verified:** 2026-11-10 @ (this patch)
