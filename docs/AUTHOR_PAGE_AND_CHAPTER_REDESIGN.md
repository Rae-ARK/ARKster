# ARKster — Author Page & Chapter Page Redesign (staged plan)

## Status
**Stage 1 — Data layer, in progress.** Stage 0 (this doc) is superseded on one
point: `author.json` moved from per-fiction folders to a single root-level
`authors/` folder, decided at Stage 1 kickoff (see "Where author.json lives"
below). Everything else from Stage 0 still stands.

## Motivation
ARKster's whole pitch (see `README.md`) is making a folder of novels *feel*
like a private Royal Road site. Two pieces of that experience are currently
missing:

1. **Chapter page parity.** Royal Road's chapter page has an "About the
   author" card under the chapter body (avatar, short bio, fiction count,
   a link into the full profile) plus Previous/Next chapter navigation.
   `ReaderScreen.kt` today has neither — only a back arrow and the
   font/spacing/reading-mode controls.
2. **No author identity.** `NovelEntity.author` is a bare string. There is no
   place to show who wrote a fiction beyond that one line on the fiction
   page, and nothing resembling Royal Road's author profile (stats,
   personal info, bio, links).

This plan closes both gaps using a new optional `author.json`, following the
same "optional, user-authored, never blocks a scan" pattern `metadata.json`
already established in `ScannerImpl.readLocalMetadata`.

## Where `author.json` lives
**Superseded from Stage 0:** not one `author.json` per fiction folder.
Instead, a single **`authors/` folder at the library root**, sibling to the
fiction folders, holding one JSON file per author:

```
MyLibraryRoot/
  authors/
    rae-ark.json       <- one author, id = filename ("rae-ark")
    rae-ark.jpg         <- optional avatar, referenced by rae-ark.json's "avatar"
    some-other-author.json
  Some Fiction/
    metadata.json       <- optional "authorId": "rae-ark" links this fiction to the author above
    cover.jpg
    0001 - Chapter One.txt
    Arc 1 - Whatever/
      ...
  Some Other Fiction/
    metadata.json        <- "authorId": "rae-ark" again - same author, two fictions
    ...
```

Rationale for the root-level `authors/` folder instead of per-fiction:
- One author routinely writes multiple fictions; a root-level folder lets
  every fiction that shares an author point at the *same* file instead of
  duplicating (and inevitably desyncing) bio/avatar/stats copies per folder.
- It directly answers Stage 0's "Open question 2" (author dedup): identity
  is now the JSON's filename/id, not a fuzzy name match, so two fictions by
  "Rae ARK" are unambiguously the same author without relying on exact
  string equality of a free-text name field. A name-based fallback still
  exists for fictions that don't set `authorId` (see below), but it's a
  fallback, not the primary mechanism.
- A single scanned library folder can still contain fictions by different
  authors - each just carries its own `authorId` (or none).

Like `metadata.json`, every part of this is optional. No `authors/` folder,
no `authorId` in a given fiction's `metadata.json`, or no name match: that
fiction simply has no author card / no author page entry point. Never a scan
failure - same guarantee `readLocalMetadata` already gives, extended to
`readAuthorMetadata`.

### Author identity: filename is the id, not a hand-validated field
Rather than asking the user to invent a globally-unique id and having the
scanner reject the scan if two files claim the same one, the id rides on
something the filesystem already guarantees is unique *within* `authors/`:
the filename. `authors/rae-ark.json` has id `rae-ark` unless the JSON itself
sets `"id"` to something else (useful if you want to rename the file later
without breaking existing `authorId` links).

If a JSON's own `"id"` override collides with an id already claimed earlier
in the same scan, that's the one edge case an id *can* still collide - the
first file wins, the later one is skipped, and it's surfaced through the
same `onProgress` soft-warning channel every other scan issue already uses
(lost permissions, skipped novel folders, etc). It never throws and never
aborts the scan, matching every other malformed-input case in `ScannerImpl`.

## `authors/<id>.json` schema (draft)
```json
{
  "id": "rae-ark",
  "name": "Rae ARK",
  "avatar": "rae-ark.jpg",
  "bio": "Short bio text, shown both truncated (chapter card) and in full (author page).",
  "joined": "2025-04-24",
  "location": "",
  "gender": "Female",
  "links": {
    "twitter": "https://twitter.com/handle",
    "website": "https://example.com"
  },
  "stats": {
    "followers": 6,
    "favorites": 1,
    "reviews_received": 1,
    "ratings_received": 3
  }
}
```

Notes:
- `id` is optional; defaults to the filename (see above).
- `avatar` is a filename relative to the `authors/` folder now (not the
  fiction folder), resolved the same way `findCoverUri` resolves `cover.*`.
- All other fields optional; absent fields just don't render (same
  philosophy as `metadata.json`'s title/author/description/genres/
  publishedDate).
- `stats` are **manually authored, display-only numbers** — this is an
  offline, no-accounts app (per README), so there is no real follower/review
  system to compute these from. They exist purely so the profile screen can
  visually match Royal Road's stat strip for users who want that; the doc
  and, later, the UI copy must make clear these are not live counts.
- Fiction count and total word count are deliberately **not** stored in the
  json — see "Open questions" below on how/whether those get computed
  instead of hand-maintained.

## Linking a fiction to an author
Each fiction's existing `metadata.json` gains one new optional field:
```json
{ "authorId": "rae-ark" }
```
Resolution order in the scanner, per fiction:
1. `metadata.json`'s `authorId`, if present and it matches an id found in
   `authors/` during this same scan.
2. Otherwise, `metadata.json`'s existing free-text `author` field, matched
   case-insensitively against `authors/*.json` `name` values.
3. Otherwise, no author link - identical to today's behavior.

## Staging order

### Stage 0 — Documentation (this change)
This file. Establishes the `author.json` contract and the stage sequence
below. No app code touched.

### Stage 1 — Data layer
**Decisions made at kickoff (superseding the open items below this doc had):**
- Storage is a new `authors` Room table + `AuthorDao`, keyed by the id
  described above, refreshed on every full rescan (parallel to how `arcs`
  are diffed/removed in `scanChaptersForNovel`).
- `NovelEntity` gets a new nullable `author_id` column (plain, not a Room
  `@ForeignKey` - see code comment on why: it's added via `ALTER TABLE` to
  an existing table, and SQLite can't retroactively attach an enforced FK
  constraint that way) resolved per the "Linking a fiction to an author"
  section above.
- `ScannerImpl.scanRoot` gains a `scanAuthorsFolder()` step that runs once
  per scan (not per-novel) and an `onAuthorsDiscovered` callback, mirroring
  the existing `onDiscovered`-does-the-DB-write style already used for
  novels rather than having `ScannerImpl` own a `db` reference for this path.
- The avatar file is resolved the same way `findCoverUri` resolves
  `cover.*`, just scoped to the `authors/` folder instead of the fiction
  folder.

### Stage 2 — Author page UI
- New `AuthorPageScreen.kt` composable modeled on the profile screenshot:
  header banner + avatar, stat strip (Follows / Favorites / Reviews /
  Fictions), "Personal Information" card, "Activity" card, "Author
  Information" card (fiction count, total words, etc.).
- Since ARKster has no accounts, network-social affordances from Royal
  Road's version (Follow button, Block button, notifications) need an
  explicit per-item decision at Stage 2 kickoff: omit, or keep purely as
  inert styling for visual parity. Not decided here.

### Stage 3 — Chapter page (`ReaderScreen.kt`) redesign
- Header row above the chapter body: fiction title / "back to fiction" link
  and the arc + chapter title, matching the screenshot's top block.
- Previous/Next chapter buttons, both above and below the chapter body.
  `ReaderScreen` currently takes only a single `chapter`/`content` pair and
  an `onBack`; it will need `onPrevious`/`onNext` callbacks (or a small
  navigation model) wired in from wherever it's launched.
- "About the author" card at the end of the chapter, sourced from the same
  `author.json`, with avatar, name, a short bio excerpt, and a tap target
  into the Stage 2 `AuthorPageScreen`.
- Existing font size / line spacing / reading-mode (Light/Sepia/Dark)
  controls stay as-is — those are ARKster reader features Royal Road's web
  chapter page doesn't have, and are out of scope for this change.

### Stage 4 — Navigation & wiring
- Add an `author/{...}` route to the nav graph in `MainActivity.kt`.
- Wire tap targets from `NovelDetailScreen` (fiction page byline) and the
  new Stage 3 "About the author" card into that route.

### Stage 5 — QA / polish
Exercise the same edge cases `metadata.json` already has to handle:
- No `authors/` folder present (today's default — nothing should change).
- Multiple fictions sharing the same `authorId` (expected, common case).
- Two fictions with the same free-text `author` name but no `authorId` set
  on either (name-fallback match, not id match).
- Two `authors/*.json` files whose `"id"` override collides - first wins,
  second is skipped with a soft warning, scan still completes.
- Malformed/partial JSON in an author file.
- Missing or unreadable avatar file.
- Very long bio text in the chapter-page card vs. the full author page.

## Explicitly out of scope
- Any real Follow/notification/social behavior — ARKster is offline-first
  and account-free per `README.md`; nothing in this feature introduces
  network calls or accounts.
- In-app editing of `author.json`. Same as `metadata.json` today, it's
  read-only from the app's perspective; users hand-edit or script it.

## Open questions
1. Does "Total Words" on the author page require a new `wordCount` column
   (on `NovelEntity` and/or `ChapterEntity`), computed at scan time? Or is
   that stat simply omitted for the first version of this feature? *(Still
   open - not needed for Stage 1's data layer.)*
2. ~~Author identity/dedup across fictions~~ **Resolved at Stage 1 kickoff**:
   root-level `authors/<id>.json`, id defaults to filename, optional
   `authorId` in a fiction's `metadata.json` links unambiguously; free-text
   name match is a fallback only. See "Where `author.json` lives" above.
3. Fallback when a fiction has a `cover.*` but no linked author/avatar —
   does the author card simply not render, or fall back to a generic
   placeholder avatar (consistent with the existing auto-generated cover
   placeholder logic in the scanner)? *(Still open - a Stage 2/3 UI
   question, not a Stage 1 data-layer one.)*

Remaining open items should be resolved before the Stage they block begins.
