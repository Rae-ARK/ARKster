# EPUB support (staged plan)

## Status

**Stage 0 — documentation. Done.** Stages 1–4 below are not started. No
app code has been touched yet — this doc was re-checked against the
codebase after `oop-modular-monolith-refactor.md`'s Stage 6 landed
(see open question 0), and nothing in the plan's own stages changed as a
result, only the seams Stage 1 will build against.

## Motivation

The scanner (`ScannerImpl`) and reader (`ReaderScreen`) are built on one
load-bearing assumption: one novel is a folder, one chapter is a loose
`.txt`/`.md` file inside it, addressed by a plain, directly-openable SAF
`content://` URI (`ChapterEntity.sourcePath`,
`TextChapterContentRepository.getTextContent`). A large fraction of the
fiction people actually have sitting in a library folder is `.epub`, not a
folder of `.txt` files — and an EPUB is the opposite shape: one zip file
containing many chapters, ordered by an OPF manifest/spine rather than by
filename. Today those files are silently invisible to the scanner
(`parseChaptersInFolder`/`findCoverUri` only look for subfolders and
`.txt`/`.md`/`metadata.json`/`cover.*`/`arcs/`/`authors/`). This plan gives
EPUB a real, first-class path through the same scan → Room → reader
pipeline text novels already use, without regressing it.

## Why this is a bigger lift than metadata enrichment

Unlike `metadata.json`/`authors/<id>.json` (purely additive, optional,
read once, never touch the chapter model), EPUB changes a structural
assumption three layers currently share:

1. **`ChapterEntity.sourcePath` stops being directly openable.** Today
   it's a SAF URI handed straight to `contentResolver.openInputStream()`.
   A chapter living inside an EPUB has no such URI of its own — it's a zip
   entry path *within* a file. `sourcePath` needs to become a composite
   address, and `ChapterContentRepository` needs an implementation that
   knows how to resolve it.
2. **Chapter discovery itself changes shape.** `parseChaptersInFolder`
   walks a folder's files. For an EPUB, "walking chapters" means opening
   the zip once, parsing `META-INF/container.xml` → the OPF's `<spine>`,
   and producing one `ChapterEntity` per spine item — a different code
   path, not a filename-pattern tweak to the existing one.
3. **Chapter content is XHTML, not plain text.** `ReaderScreen` renders a
   chapter as one `Text()` composable fed pre-decoded plain text from
   `ChapterContent.Text`. EPUB bodies carry inline formatting, footnotes,
   and images the current model has nowhere to put.

None of this needs a new dependency — `java.util.zip` and Android's
built-in `XmlPullParser` are enough to parse an EPUB by hand (see open
question 4 for a library alternative) — but it's genuinely new code in
the scanner, the content repository, and (for full fidelity, later) the
reader.

## What EPUB actually is, for reference

```
SomeBook.epub  (a zip file)
├── mimetype                      <- must be first entry, stored uncompressed
├── META-INF/
│   └── container.xml             <- points at the OPF's path inside the zip
├── OEBPS/  (or any folder name the author chose)
│   ├── content.opf               <- manifest (every file in the book) + spine (read order)
│   ├── toc.ncx                   <- EPUB2 table of contents (nested, chapter titles)
│   ├── nav.xhtml                 <- EPUB3 nav doc (supersedes toc.ncx where present)
│   ├── cover.jpg                 <- declared as the cover in the OPF manifest/metadata
│   ├── chapter-001.xhtml         <- one spine item = one "chapter" for our purposes
│   ├── chapter-002.xhtml
│   └── images/
│       └── illustration-01.jpg
```

Reading order comes from the OPF's `<spine>` (a list of `idref`s into the
manifest), **not** filename order — some books number spine files
arbitrarily. This is why EPUB needs its own discovery path rather than a
rule bolted onto `parseChapter`'s filename-number heuristic.

## Design decisions

### `sourcePath` becomes a composite URI, one scheme per source type

`ChapterEntity.sourcePath` stays a single `String` column, but for EPUB
chapters it's no longer independently openable:

```
epub://<epub-file-saf-uri-percent-encoded>#<spine-index>
```

`TextChapterContentRepository` stays untouched and keeps handling plain
`content://` paths exactly as today; a new `EpubChapterContentRepository`
is the only thing that understands the `epub://` scheme.
`ChapterContentRepository` becomes a small dispatcher that picks the right
implementation by scheme — see Stage 1.

A separate nullable `epub_entry_index` column was considered instead, but
it would make every caller of `sourcePath` (reader, cover/asset
resolution, debugging) thread a second nullable field through for a
distinction the URI scheme already encodes for free.

### Chapter identity across rescans

Text chapters key off a filename-derived `ChapterEntity.id` (unchanged by
this plan). EPUB spine items have no filename of their own to hash — the
manifest `id` attribute (e.g. `id="chapter-003"`) is the natural stable
key, since it's author-assigned and doesn't move even if the underlying
`.xhtml` filename or spine position does. Chapter id becomes
`<novelId>-epub-<manifest-id>`, close enough to the existing
`<novelId>-<slug>` shape that `ChapterOverrideEntity`/
`ReadingProgressEntity` (both foreign-keyed on `chapter_id`) need no
schema change.

### Sort tier, arc, and the bonus/closing marker convention

The `~`/`!` filename-prefix convention (see
[`../foundational/scanner-invariants.md`](../foundational/scanner-invariants.md))
has no analogue in a filename-less spine item. Fallback: match the
*resolved chapter title* (from the NCX/nav, not a filename) against the
same bonus/closing keyword list `parseChapter` already falls back to for
un-prefixed text files — EPUB chapters simply never reach the marker
branch. Nested NCX/nav entries (a "Volume 1" `<navPoint>` containing
several chapter `<navPoint>`s) map onto `ArcEntity` the same way a
subfolder does for text novels — one arc per top-level nav grouping that
itself contains children, in spine order.

### Cover art

`findCoverUri` matches a loose `cover.*` file next to the novel folder.
EPUB covers are binary data embedded in the zip, declared either via the
OPF's `<meta name="cover" content="...">` (EPUB2) or an `<item>` with
`properties="cover-image"` (EPUB3) — neither can be pointed at with a SAF
URI directly. `EpubChapterContentRepository`'s discovery pass extracts
the declared cover image, once, to app-private storage
(`context.filesDir/epub-covers/<novelId>.<ext>`) at scan time, and
`NovelEntity.coverUri` is set to a `file://` URI into that cache — the
same "resolve once, store a stable URI" pattern `findCoverUri` already
establishes, just with a cache step in front of it since the source bytes
aren't independently addressable.

Now that `oop-modular-monolith-refactor.md`'s Stage 6 has landed,
`CachingCoverResolverProxy` is a real, concrete precedent for exactly
this "resolve once, keep a null result as a valid cached answer rather
than a miss" shape (`ConcurrentHashMap<String, CachedResult>`, its
`CachedResult` wrapper existing solely so a resolved-to-nothing folder
isn't retried every call). It is **not**, however, something this
extraction step can literally plug into: `CoverSource.findCoverUri`
takes a `DocumentFile` folder and returns an existing SAF URI, whereas
EPUB cover extraction reads bytes out of a zip entry and writes a new
file into app-private storage — a different operation, not a different
input to the same one. `EpubChapterContentRepository`'s extraction cache
should follow the same pattern (a small `ConcurrentHashMap` keyed by
EPUB URI, wrapping the resolved `file://` URI or lack of one) rather than
be threaded through `CoverSource`/`CachingCoverResolverProxy` itself.

### Rendering: plain-text-strip for v1, HTML fidelity out of scope

Two options were weighed for chapter body rendering: stripping XHTML to
plain text (cheap, reuses `ChapterContent.Text` → `Text()` unmodified, but
loses italics/bold/footnotes/inline images), or real HTML rendering (a
`WebView` fallback that loses the Compose-native reading-mode controls for
those chapters, or a hand-rolled HTML→`AnnotatedString` renderer, a
materially larger separate project).

**Decision: v1 ships plain-text-strip.** It's the only option that reuses
`ReaderScreen` unmodified. Real HTML/image fidelity is explicitly
deferred — see "Explicitly out of scope" below. `ChapterContent` gains no
new case for v1; `EpubChapterContentRepository.getTextContent` returns the
same `ChapterContent.Text`, just populated from tag-stripped XHTML instead
of a raw `.txt` file, so `ReaderScreen` never needs to learn EPUB exists.
(A future `ChapterContent.Html` case for HTML-fidelity work would be
additive whenever that's tackled.)

### Reading progress

`ReadingProgressEntity.positionType` already lists `EPUB_CFI` as a
documented-but-unused value (see
[`../foundational/architecture.md`](../foundational/architecture.md)) —
anticipated, not a new concept. v1 does not need it: since chapter body is
flattened to plain text same as `.txt` chapters, `PERCENTAGE`/
`CHAR_OFFSET` positioning works unchanged. `EPUB_CFI` only becomes
relevant once the HTML-fidelity renderer exists, since only then does a
position need to survive a re-render of real markup rather than a flat
string.

## Staging order

### Stage 0 — Documentation. Done.
This file. No app code touched.

### Stage 1 — Zip/OPF/NCX parsing + data layer. Not started.
- New `EpubParser` under `data/`: given a SAF URI for an `.epub` file,
  opens it over a `ContentResolver` stream, locates
  `META-INF/container.xml`, resolves and parses the OPF for manifest +
  spine + declared cover + title/author/description, then parses whichever
  of `toc.ncx`/`nav.xhtml` is present for chapter titles and nesting.
- `ChapterContentRepository` becomes scheme-dispatching (see "composite
  URI" above); new `EpubChapterContentRepository` implements
  tag-stripping for chapter bodies.
- `ScannerImpl` gains an EPUB discovery path: when a novel folder (or the
  scan root itself, per open question 1) contains a `.epub` file, route
  it through `EpubParser` instead of `parseChaptersInFolder`, producing
  `ChapterEntity`/`ArcEntity` rows per the rules above, and a
  `NovelEntity` seeded from OPF metadata (falling back to
  `metadata.json`/filename-derived title exactly where OPF itself is
  missing a field — never blocks a scan, same guarantee every other
  optional-metadata path in `ScannerImpl` already gives). With Stage 6 of
  the refactor now landed, these rows are persisted through
  `NovelRepository`/`ChapterRepository`/`ArcRepository` — the same
  constructor-injected repositories `ScannerImpl` already holds for the
  folder-scan path — not through `NovelDao`/`ChapterDao`/`ArcDao`
  directly; "nothing above `domain/` touches a `Dao` directly" applies to
  this new path exactly as it does to the existing one.
- Cover extraction to app-private cache, as described above.
- No UI changes this stage — purely data layer, verifiable by inspecting
  the resulting Room rows for a handful of sample EPUB files.

### Stage 2 — Reader wiring. Not started.
- `ReaderScreen` needs no rendering changes (v1 stays plain-text), but
  Previous/Next neighbor-resolution in `MainActivity` and any other code
  that assumes `sourcePath` is a bare openable URI needs to route through
  the scheme-dispatching `ChapterContentRepository` instead of calling
  `contentResolver.openInputStream()` directly.
- Verify page-splitting-by-character-count (`NovelDetailScreen`) keeps
  working unchanged, since it only operates on the resolved chapter list.

### Stage 3 — Scanner integration polish. Not started.
- Sort-tier keyword fallback wired against NCX/nav titles, per "Sort
  tier, arc, and the bonus/closing marker convention" above.
- Incremental-rescan fingerprinting (`ScanFingerprintEntity`, currently
  aggregated over every file under the novel folder) extended to treat an
  `.epub` file's own `lastModified`/`length` as the fingerprint input
  instead of walking children, since there are none to walk.
- Soft-warning surfacing (`onProgress`) for corrupt/malformed EPUBs
  (missing `container.xml`, unparseable OPF, empty spine) — never a scan
  failure, matching `readLocalMetadata`'s fail-soft precedent.

### Stage 4 — QA / polish. Not started.
- EPUB2 (`toc.ncx`) vs EPUB3 (`nav.xhtml`) parsing, and books that ship
  both (`nav.xhtml` should win, per spec); a book with no declared cover.
- Spine items with no matching NCX/nav entry (title falls back to the
  manifest item's own filename).
- Deeply nested nav trees — decide whether to flatten past one level of
  `ArcEntity` nesting or extend the arc model; likely flatten for v1,
  revisit only if a real example demands otherwise.
- Non-UTF-8 XHTML content (mirrors `TextChapterContentRepository`'s
  existing UTF-8 → ISO-8859-1 fallback decoder).
- A `.epub` file placed directly at the scan root vs. one sitting inside a
  novel folder alongside a `metadata.json`/`cover.jpg` — confirm the
  precedence decided in open question 1.
- Mixed library: some fictions are folders of `.txt` files, others are
  single `.epub` files, in the same scan root — confirm neither path
  regresses the other.

## Explicitly out of scope

- **Real HTML/inline-formatting/inline-image rendering.** v1 flattens
  every chapter to plain text. This deserves its own design doc, not a
  late addition folded into this plan's stages.
- **EPUB writing/export.** This plan is read-only, matching how the app
  treats `.txt`/`.md` novels today — ARKster never writes back into a
  user's library folder.
- **DRM-protected EPUBs.** Out of scope entirely; these aren't plain zip
  files in the way this plan assumes, and ARKster is offline/account-free
  per [`../foundational/what-is-arkster.md`](../foundational/what-is-arkster.md)
  with no store integration to justify it.
- **`EPUB_CFI`-precision reading position.** v1 keeps `PERCENTAGE`/
  `CHAR_OFFSET` against the flattened text. True CFI only matters once
  real HTML rendering exists.

## Open questions

0. **Resolved by `oop-modular-monolith-refactor.md`'s Stage 5, then Stage
   6, landing first:** this plan's own composite `epub://<uri>#<spine-index>`
   scheme was written assuming EPUB support would need to invent its own
   file-type dispatcher. `BookSourceFactory`/`BookSource` now exist for
   real (`domain/booksource/`) and already recognize a `.epub` scan-root
   file as a distinct, unsupported case. Stage 1 below should implement
   `EpubSource : BookSource` against that seam — `BookSourceFactory`
   picking it the same way it already picks `Unsupported(EPUB)` today, not
   a parallel URI-scheme check inside `ChapterContentRepository`. The
   `sourcePath`-as-composite-URI design further down is still a reasonable
   shape for `ChapterEntity.sourcePath` specifically, but re-read it
   against `FolderSource.kt`'s actual shape before Stage 1 starts, per the
   refactor doc's own instruction to re-read whichever stage lands second.
   Stage 6 landing afterward adds a second seam to design against before
   Stage 1 starts: `NovelRepository`/`ChapterRepository`/`ArcRepository`
   are now the only sanctioned way to write those rows (see the Stage 1
   bullet above), and `CachingCoverResolverProxy` is now a concrete,
   copyable precedent for the cover-extraction cache described under
   "Cover art" — though not a literal interface it plugs into, since its
   `CoverSource` contract is folder-shaped, not zip-entry-shaped.

1. Where does a `.epub` file live relative to the existing "one folder =
   one novel" convention — a loose file directly in the scan root (its
   own filename becomes the novel identity), or does it still need to sit
   inside a novel folder (so it can carry a `metadata.json`/`cover.jpg`/
   author override alongside it)? Leaning toward supporting both, but this
   needs to be pinned down before Stage 1's `ScannerImpl` changes, since it
   determines where the new discovery path hooks into `scanRoot` vs. the
   existing per-novel-folder loop.
2. Does OPF-declared metadata (title/author/description) take precedence
   over an EPUB-adjacent `metadata.json`, or the reverse? Precedent
   (`readLocalMetadata` already lets local `metadata.json` override
   scanner-derived state for text novels) suggests `metadata.json` should
   win when both are present, with OPF only filling gaps — but this should
   be stated explicitly before Stage 1, not decided implicitly by whichever
   code path happens to run last.
3. Multi-level nav nesting (see Stage 4) — flatten to one `ArcEntity`
   level for v1, or extend the arc model to support nesting? Still open;
   not needed to start Stage 1, only Stage 3/4.
4. Hand-roll `EpubParser` against `java.util.zip`/`XmlPullParser` as
   scoped above, or pull in a small EPUB-parsing library instead? One
   candidate looked at: `epublib` (`psiegman/epublib`, LGPL, not on Maven
   Central but published via the author's own repo) — stable and
   functionally complete, but plain Java rather than Kotlin-idiomatic, not
   actively maintained, and prone to choking on nonstandard OPF/NCX from
   modern webnovel-to-epub exporters. If used, it should sit behind a
   single wrapper/adapter rather than being called directly from the
   scanner, so it stays swappable. Still open — hand-rolled is the Stage 1
   starting assumption, not a final commitment; worth revisiting once
   real-world malformed EPUBs are tested in Stage 4.

Remaining open items should be resolved before the Stage they block
begins.

**Verified:** 2026-11-11 @ d46debb (Stage 6 landed, plan re-checked
against current code; no stage content changed)
