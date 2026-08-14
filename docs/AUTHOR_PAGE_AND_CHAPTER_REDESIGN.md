# ARKster — Author Page & Chapter Page Redesign (staged plan)

## Status
**Stage 0 — Documentation only.** No Kotlin/Compose/Room code is added or changed
by this document or its accompanying patch. This file exists to lock down the
data contract and staging order *before* any implementation starts, the same
way `V0.2_ROADMAP.md` and `ARKster_ANDROID_DESIGN.md` preceded their features.

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
Inside each **fiction folder** (what the code calls the "novel folder"),
next to `metadata.json`, `cover.*`, and the chapter/arc contents:

```
MyLibraryRoot/
  Some Fiction/
    metadata.json
    cover.jpg
    author.json      <- new, optional
    author.jpg        <- optional avatar referenced by author.json
    0001 - Chapter One.txt
    Arc 1 - Whatever/
      ...
```

Rationale for per-fiction (not per-library-root) placement:
- A single scanned library folder can contain fictions by different authors;
  a root-level file couldn't express that.
- It mirrors `metadata.json`'s existing placement and failure model exactly,
  so the scanner change in Stage 1 is additive, not a new pattern.
- It matches the Royal Road mental model in the screenshots: the "About the
  author" box on a chapter page belongs to *that fiction's* author.

Like `metadata.json`, the file is entirely optional. No `author.json` means
no author card on the chapter page and no author page entry point — never a
scan failure, same guarantee `readLocalMetadata` already gives.

## `author.json` schema (draft)
```json
{
  "name": "Rae ARK",
  "avatar": "author.jpg",
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
- `avatar` is a filename relative to the fiction folder, resolved the same
  way `findCoverUri` resolves `cover.*` today.
- All fields optional; absent fields just don't render (same philosophy as
  `metadata.json`'s title/author/description/genres/publishedDate).
- `stats` are **manually authored, display-only numbers** — this is an
  offline, no-accounts app (per README), so there is no real follower/review
  system to compute these from. They exist purely so the profile screen can
  visually match Royal Road's stat strip for users who want that; the doc
  and, later, the UI copy must make clear these are not live counts.
- Fiction count and total word count are deliberately **not** stored in the
  json — see "Open questions" below on how/whether those get computed
  instead of hand-maintained.

## Staging order

### Stage 0 — Documentation (this change)
This file. Establishes the `author.json` contract and the stage sequence
below. No app code touched.

### Stage 1 — Data layer
- Add a sibling to `readLocalMetadata()` in `ScannerImpl` (e.g.
  `readAuthorMetadata()`) that parses `author.json` with the identical
  "malformed file never fails the scan" behavior.
- Decide and implement storage: either a new `authors` Room table +
  `AuthorDao` (keyed by normalized author name, refreshed on rescan like
  `novels`), or a non-persisted read-on-demand path if profile views turn
  out to be infrequent enough not to need caching. This decision should be
  made at Stage 1 kickoff, not in this doc.
- Resolve the avatar file the same way `findCoverUri` resolves `cover.*`.

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
- No `author.json` present (today's default — nothing should change).
- Multiple fictions sharing the same author name.
- Malformed/partial JSON.
- Missing or unreadable avatar file.
- Very long bio text in the chapter-page card vs. the full author page.

## Explicitly out of scope
- Any real Follow/notification/social behavior — ARKster is offline-first
  and account-free per `README.md`; nothing in this feature introduces
  network calls or accounts.
- In-app editing of `author.json`. Same as `metadata.json` today, it's
  read-only from the app's perspective; users hand-edit or script it.

## Open questions for Stage 1 kickoff
1. Does "Total Words" on the author page require a new `wordCount` column
   (on `NovelEntity` and/or `ChapterEntity`), computed at scan time? Or is
   that stat simply omitted for the first version of this feature?
2. Author identity/dedup across fictions: is an exact, case-insensitive
   match on `author.json`'s `name` field sufficient, or do we need an
   explicit author id a user can put in multiple `author.json` files to
   group fictions unambiguously?
3. Fallback when a fiction has a `cover.*` but no `author.json`/avatar —
   does the author card simply not render, or fall back to a generic
   placeholder avatar (consistent with the existing auto-generated cover
   placeholder logic in the scanner)?

These should be resolved (and this doc amended or superseded) before Stage 1
implementation work begins.
