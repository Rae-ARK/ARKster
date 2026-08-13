# ARKster — Android Design (cleaned, opinionated)

Purpose: a compact, practical Android-first design that implements the Royal Road browsing UX for a private local library of novels. This doc is opinionated about architecture, storage, and an incremental MVP that minimizes early complexity (EPUB/PDF handled later).

## Principles
- Offline-first and privacy-respecting: no accounts by default; network metadata is opt-in and cached locally.
- SAF-first storage model: use Android Storage Access Framework and persist `Uri` permissions.
- The folder is a source of truth; Room is the indexed representation and query surface.
- Keep EPUB/PDF complexity confined to the data layer; UI consumes a normalized model.

## Quick overview
- App name: ARKster (case sensitive)
- Platform: Android native (Kotlin + Jetpack Compose)
- Core: scan one root library folder, index books, present a Royal Road–style Home and Fiction pages, native reader UI.

## Primary user flow
1. First launch onboarding explains offline model and requests root library folder (SAF `ACTION_OPEN_DOCUMENT_TREE`). Persist permission via `takePersistableUriPermission()`.
2. App performs an initial scan and builds a Room-backed index.
3. Home (Royal Road style) shows category containers and search.
4. Open fiction → fiction header (cover left, metadata right) → index/tabbed area (Fiction Index / Arc Index) → open chapter → read.

## Royal Road–style UI (high level)
- Home: carousels for Most Read, Recently Updated, New, Continue Reading; search bar.
- Fiction page: cover + metadata header, tabs for Fiction Index and Arc Index, chapter lists with per-fiction pagination (10/20/50/100) stored per fiction in DB.
- Reader: native text rendering, font controls, theme (Light/Dark/Warm), save progress.

## File formats & Book sources
Support: `txt`, `md` initially; EPUB and PDF added later behind `BookSource` types.

Canonical patterns supported (flexible, forgiving):
- One novel per folder (recommended). Folder may contain chapters as files, arcs as subfolders, or a single `book.epub`/`book.pdf` file.

Do not assume perfect structure — scanner must be resilient and provide sensible fallbacks.

## BookSource abstraction (core design)
Define:
```
interface BookSource {
  suspend fun listChapters(): List<Chapter>
  suspend fun getMetadata(): BookMetadata
}

class FolderSource : BookSource { ... }
class EpubSource : BookSource { ... }
class PdfSource  : BookSource { ... }
```

UI and ViewModels consume normalized `Chapter` and `BookMetadata`; sources hide format specifics.

## ChapterContentRepository
Provide a single API for content:
```
interface ChapterContentRepository {
  suspend fun getContent(chapterId: Long): ChapterContent
}
```
The repository hides ZIP entry extraction, temp files, or PDF rendering. UI receives text/html/page index as `ChapterContent` and renders it accordingly.

## Storage: Room + DataStore
- Room (structured): `novels`, `arcs`, `chapters`, `reading_progress`, `scan_fingerprints`, `cached_metadata`.
- DataStore (preferences): `selected_library_uri`, `reader_theme`, `default_font_size`, `pagination_defaults`, `network_metadata_enabled`.

Separation rationale: Room for queryable indexed state; DataStore for lightweight app prefs.

## ReadingProgress (flexible schema)
```
ReadingProgress {
  novelId
  chapterId
  position
  positionType  // CHAR_OFFSET, PAGE_INDEX, EPUB_CFI, PERCENTAGE, etc.
  updatedAt
}
```
Interpret `position` according to `positionType` per source.

## Scanning & incremental rescans
- Persist per-document fingerprint: `uri`, `lastModified?`, `size?`, `etag?` if available.
- On rescan: detect New / Modified / Removed and update Room incrementally.
- Keep `scan_version` to trigger full recompute when scanner logic changes.

Scanning pseudocode (high level):
1. Given persisted `treeUri`, use `DocumentFile.fromTreeUri()` to enumerate immediate children.
2. For each entry that looks like a book folder or file, create an appropriate `BookSource`.
3. Call `listChapters()` on the source to get normalized chapters and metadata.
4. Upsert novel/chapters in Room using fingerprints to skip unchanged items.

## EPUB caution
- EPUB is a ZIP container. Parse OPF/manifest/spine/TOC in the data layer and store TOC in Room.
- Do not expose raw ZIP entry URIs to the UI. Use `ChapterContentRepository` to provide HTML/text for chapters (temp files or streams as needed).
- Plan to render EPUB content via a WebView or sanitized HTML renderer; allow images via content streams.

## PDF handling
- Treat PDF as a page-based reader in the MVP. Provide page navigation and bookmark support where available.
- Do not attempt robust text reflow or universal chapter extraction in early releases.

## Search (separate scopes)
- Library search (titles/authors/tags/chapter filenames) via Room queries.
- Content search (chapter body) is optional for later; consider SQLite FTS for full-text indexing.

## Metadata downloads & privacy
- No network calls unless `network_metadata_enabled` is true.
- Metadata provider is optional and user-controlled; cache results in Room and record `fetchedAt`.

## Error handling & UX
- Show clear states: Loading, Empty, Permission needed, Importing (progress), Error.
- Handle common edge cases: encoding fallback, duplicate chapter numbers (warn + fallback ordering), missing covers (placeholder), revoked SAF permission (banner + action).

## Graceful degradation, scanning UX & normalization
The product's credibility depends on making messy folders feel polished. The scanner and UI must be intentionally forgiving and provide one-time fixes when heuristics fail.

- Auto-generated covers: if no cover image is found, generate a consistent placeholder using the novel title's first letter and a color derived from a stable hash of the title. This produces an identifiable, consistent cover per novel without network or file copying.

- Progressive scan UX: perform scanning incrementally and populate the Library UI as novels are discovered. Show a compact progress indicator and allow the user to continue browsing discovered items while the background scan continues. Provide a friendly summary when scanning finishes and log non-fatal parsing issues without blocking the user.

- Ranked heuristics (not brittle regexes): build a small ordered set of heuristics to detect chapter files and titles. Examples include:
  1. Leading number patterns: `^\s*(0*\d{1,4})\D+(.+?)` (covers `01 - Title`, `1_Title`, `001.Title`).
  2. `Chapter\s+\d+` prefixes: `Chapter 12 - Title`.
  3. Pure numeric filenames: `012.txt` → number `12`, title from parent folder or `Chapter 12` fallback.
  4. Fallback to filename tokenization (replace `_`/`-` with spaces, title-case, strip extension).
  5. EPUB/PDF TOC mapping when available.

- Manual override and editor: provide an in-app editor on the Novel page where users can:
  - Reorder chapters (drag/drop)
  - Edit chapter titles
  - Merge or split entries
  - Mark volumes/arcs
  Persist these overrides in Room as an `override` layer that sits above the scanned index so repairs are one-time operations.

- Non-fatal parsing: surface parsing warnings unobtrusively (a small indicator on a novel card and a diagnostics view) rather than blocking the library population with errors.

- Normalization helpers: attempt best-effort title-casing and whitespace normalization on parsed titles so `the fall of a hero.txt` becomes `The Fall Of A Hero` by default; allow user to edit if wrong.

These measures keep the initial scan from feeling broken on messy real-world folders and make the library feel cohesive immediately.

## Rescan UX
- Provide `Rescan Library` (full) and `Check for Changes` (incremental) actions in Settings and Library UI.

## MVP sequencing (opinionated)
- v0.1 (target initial ship):
  - SAF root selection
  - FolderSource scanning for `txt`/`md` with progressive scan UX and heuristics
  - Room index
  - Library home (Royal Road style skeleton) with Continue Reading carousel
  - Global search (library-scoped: titles/authors/chapter filenames)
  - Fiction page (header + chapter list) with manual override editor
  - Reader (native text view, progress tracking)
  - Rescan action

- v0.2:
  - Arcs support
  - Per-fiction pagination preferences
  - Incremental rescanning optimizations

- v0.3:
  - EPUB support (EpubSource + ChapterContentRepository)
  - Metadata provider (opt-in)

- v0.4:
  - PDF support (page-based reader)
  - Optional richer content search

## Minimal tech stack & libs
- Kotlin, Coroutines, Jetpack Compose, Navigation-Compose
- Room, DataStore (Proto/Preferences)
- Coil for image loading (Content URI support)
- A lightweight EPUB parsing library (evaluate options), or implement OPF/NCX parsing in data layer

## Next steps (concrete)
1. Implement `FolderSource` scanner + incremental rescan proof-of-concept (v0.1 core).
2. Add Room schema and simple Library UI that renders the index.
3. Implement `ChapterContentRepository` for TXT/MD; defer EPUB/PDF to v0.3/0.4.

This cleaned design focuses early work on the features that deliver the Royal Road UX for local files while keeping harder formats isolated and scheduled later.

## Architectural recommendations & decisions
These changes reflect feedback to keep the data layer robust, keep EPUB/PDF complexity out of the UI, and sequence the work for a practical MVP.

1. Room + DataStore separation
  - Use Room for structured application state: `novels`, `arcs`, `chapters`, `reading_progress`, `scan_metadata`, and `cached_online_metadata`.
  - Use Jetpack `DataStore` (Preferences or Proto) for lighter preferences: `selected_library_uri`, `reader_theme`, `font_size`, `line_height`, `pagination_preference`, and `network_metadata_enabled`.

2. BookSource abstraction
  - Introduce `BookSource` abstraction to encapsulate different storage formats:
    - `FolderSource` (txt/md files, per-file chapters)
    - `EpubSource` (EPUB container)
    - `PdfSource` (PDF document)
  - Each source implements an interface that exposes a normalized list of `Chapter` entries and metadata. The UI consumes the normalized model only.

3. Chapter content access via repository
  - Implement `ChapterContentRepository` with `suspend fun getContent(chapterId): ChapterContent`.
  - The repository hides how content is read (raw file, EPUB ZIP entry extraction, or PDF page rendering). UI never reads ZIP entries or PDF internals directly.

4. EPUB handling (caution)
  - EPUBs are ZIP packages; do not expose internal ZIP entry URIs to the UI. Parse EPUB TOC/spine in the data layer and provide chapter HTML/XHTML content via the `ChapterContentRepository` (in-memory or temp file streams as needed).
  - Use a robust EPUB parser (e.g., epub-parser, epubcheck-derived libraries or Android-friendly libs) and cache parsed TOC in Room.

5. PDF as page-based reader (MVP)
  - Treat PDF as a separate reader mode: page-based navigation and bookmarks. Do not attempt full text reflow or reliable chapter extraction in v0.1/v0.2.

6. Incremental rescanning & indexing
  - Store scan fingerprints per document (URI, lastModified, size, optional document id). On rescans, detect new/modified/removed items and update Room incrementally instead of full reindex.
  - Keep a `scan_version` to aid migrations and to trigger full rescans when the scanner changes.

7. ReadingProgress schema
  - Use a flexible `ReadingProgress`:
    - `novelId`, `chapterId`, `position`, `positionType`, `updatedAt`.
  - `positionType` indicates interpretation: `CHAR_OFFSET`, `HTML_LOCATION`, `EPUB_CFI` (optional future), `PAGE_INDEX`, or `PERCENTAGE`.

8. Search: separate library vs content search
  - Library search (titles, authors, tags, chapter filenames) via Room queries.
  - Content search (chapter text / EPUB body) is optional for later; implement with SQLite FTS or an indexing service when needed.

9. Metadata downloading boundary
  - No network activity unless `network_metadata_enabled` is true. Make metadata provider optional and explicitly user-driven (initial lookup or manual refresh).
  - Cache downloaded metadata in Room and store `fetchedAt` timestamps.

10. Rescan actions
  - Provide prominent actions: `Rescan Library` (full) and `Check for Changes` (incremental). Expose these in Settings and on the Library screen.

11. MVP sequencing (recommended)
  - v0.1: SAF folder selection, TXT/MD parsing, folder scanning, `metadata.json`, covers, Room index, Library, Novel page, chapter list, reader, reading progress.
  - v0.2: Arcs, themes, reader customization, incremental rescanning, search (library fields), better error diagnostics.
  - v0.3: EPUB support (TOC/spine parsing, EPUB content repository, caching metadata).
  - v0.4: PDF support (page reader, bookmarks), optional online metadata provider.

12. Conceptual model
  - Treat ARKster as a local library index + document reader. The folder is the source of truth; Room becomes the indexed, queryable representation that powers search, sorting, recently read, and UI features.

These recommendations keep EPUB/PDF complexity confined to the data layer, make rescanning fast for large libraries, respect user privacy, and provide a clear, incremental delivery path.
