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
  `BookSource` was designed to grow into.
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
