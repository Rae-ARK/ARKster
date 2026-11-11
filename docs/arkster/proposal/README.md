# Proposal

Documented but untouched: ideas and open bug reports nobody has started
building — see the lifecycle rule in [`../README.md`](../README.md). When
someone commits to one, `git mv` it into
[`../implementation/`](../implementation/README.md) and stage it as a rung
ladder (or write it directly there, staged from rung 0, the way
`implementation/epub-support.md` does).

## Open bug reports

- **"First image" issue, happens sometimes.** Reported alongside the bugs
  that became [`scanner-invariants.md`](../foundational/scanner-invariants.md),
  but never root-caused: no screenshot or repro detail survived (which
  screen, whether it's a cover or chapter image, what "sometimes"
  correlates with — first scan vs. rescan, a specific novel, cold start).
  Needs a fresh repro before anyone can pick it up.

- **Persisted folder-grant leak on re-pick.** The
  [`scanner-invariants.md`](../foundational/scanner-invariants.md#release-the-previous-folder-grant-before-storing-a-new-one)
  invariant this is supposed to satisfy is violated in practice:
  `MainActivity`'s folder-picker callback calls
  `takePersistableUriPermission` on every re-pick, but nothing in the app
  ever calls `releasePersistableUriPermission`. Re-picking a library folder
  repeatedly accumulates grants toward Android's per-app cap instead of
  releasing the previous one, and the next pick past the cap throws.

- **Cover/avatar resolver caches never invalidate.** `CachingCoverResolverProxy`
  and `CachingAuthorAvatarResolverProxy` key their cache on folder URI alone
  (no content hash, no `lastModified`, no eviction), for the lifetime of the
  `App`-scoped scanner singleton. A rescan that picks up a cover or avatar
  image newly added to an already-scanned folder won't show it until the
  app process restarts — at odds with this app's diff-based rescan premise,
  since the "Deletion safety" reconciliation above only ever covered the
  novel/arc/chapter tree itself, not resolved cover/avatar URIs.

- **Folder rename orphans a novel's data.** Novel/arc/chapter ids are
  derived by hashing the SAF tree/child URI (`ScannerImpl`'s id
  generation). On the standard local-storage SAF provider a folder's URI is
  path-based, so renaming a novel folder changes its derived id; the next
  scan treats it as a brand-new novel, and the
  [deletion-safety reconciliation](../foundational/scanner-invariants.md#deletion-safety-a-novel-is-never-deleted-on-one-unseen-pass)
  removes the old id's `reading_progress` and `chapter_overrides` once it's
  gone unseen for two passes. Nothing makes a novel's id survive an
  ordinary rename today. The obvious fix — a stable id written to a sidecar
  file in the novel folder at first scan — needs write access the app
  doesn't currently request or use anywhere (`MainActivity`'s folder picker
  only grants `FLAG_GRANT_READ_URI_PERMISSION`, and there is no
  `createFile`/`openOutputStream` call in `app/src/main`), so this also
  needs a permission-model decision before it can be picked up, not just a
  code change.

- **Fiction Browse's sort options don't sort by what they claim.** Of the
  four options [`ui-design.md`](../foundational/ui-design.md) promises
  (Recently Updated, Most Popular, Highest Rated, Newest), none use real
  data — `NovelEntity` has no timestamp, popularity, or rating field. In
  `FictionBrowseScreen`, Recently Updated is actually alphabetical by
  title, Newest is reverse-alphabetical, and Most Popular / Highest Rated
  each re-shuffle the whole grid on every filter change. This is the main
  library-browsing screen's primary control, exercised on every visit.

## Candidate features

Carried over from pre-v0.2 planning docs, pruned of anything that no
longer fits the app's offline/no-account scope
([`what-is-arkster.md`](../foundational/what-is-arkster.md)): a live
rating/review system, follows, a recommendation engine, and other social
features were dropped here for the same reason the author page's
Follow/Activity UI was omitted — there's no real relationship for them to
act on.

- **PDF support** (`PdfSource`) — page-based reader with bookmarks; no
  robust text reflow or chapter extraction planned. Named alongside EPUB in
  `architecture.md`'s "Not yet built" section as the second format
  `BookSource` was designed to grow into. `BookSourceFactory` (see
  `implementation/oop-modular-monolith-refactor.md`'s Stage 5) already
  recognizes a `.epub`/`.pdf` scan-root file as "unsupported" and surfaces
  it to the user instead of leaving it silently invisible — whoever picks
  this up implements `PdfSource` against that existing seam rather than
  inventing a new detection path.
- **Opt-in network metadata provider** — cover art/author/synopsis lookup,
  cached in Room with a `fetchedAt` timestamp, off unless
  `network_metadata_enabled` is set. The preference and the
  `cached_metadata` table already exist (see `architecture.md`); no
  provider is wired up to populate them.
- **Chapter-body content search** — SQLite FTS over chapter text, distinct
  from the shipped library search (titles/authors/chapter filenames).
- **Advanced library filters** — filter/sort the library by reading
  status, author, or date, beyond today's title/author substring search.
- **Bookmarks and reading lists** — saved positions or lists independent
  of the single linear reading-progress pointer each novel has today.
- **Export/backup** — a way to get reading history and library state off
  the device, for migration or safekeeping.

**Verified:** 2026-11-11 @ (this patch)
