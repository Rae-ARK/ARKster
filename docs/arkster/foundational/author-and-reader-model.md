# Author & reader model

The `authors/<id>.json` convention, how a fiction links to one, and the
Royal Road-style chapter page built on top of it (author card,
Previous/Next navigation). This feature shipped in full across five
stages; this doc states what's true now, not the stage history.

## Where `author.json` lives

One **`authors/` folder at the library root**, sibling to the fiction
folders, holding one JSON file per author — not one `author.json` per
fiction folder:

```
MyLibraryRoot/
  authors/
    rae-ark.json       <- one author, id = filename ("rae-ark")
    rae-ark.jpg         <- optional avatar, referenced by rae-ark.json's "avatar"
  Some Fiction/
    metadata.json       <- optional "authorId": "rae-ark" links this fiction to the author above
    cover.jpg
    0001 - Chapter One.txt
  Some Other Fiction/
    metadata.json        <- "authorId": "rae-ark" again - same author, two fictions
```

A root-level folder, rather than per-fiction, lets every fiction that
shares an author point at one file instead of duplicating (and
desyncing) bio/avatar/stats copies, and gives author identity an
unambiguous key — the JSON's filename — instead of a fuzzy name match.
Everything here is optional, exactly like `metadata.json`: no `authors/`
folder, no `authorId`, or no name match just means that fiction has no
author card and no author page entry point. It is never a scan failure.

### Author identity

The filename is the id (`authors/rae-ark.json` → `rae-ark`) unless the
JSON itself sets `"id"` to something else. If two files' own `"id"`
override collides (comparison is case-insensitive), the first file
scanned wins and the later one is skipped with a soft `onProgress`
warning — never a scan failure.

## `authors/<id>.json` schema

```json
{
  "id": "rae-ark",
  "name": "Rae ARK",
  "avatar": "rae-ark.jpg",
  "bio": "Short bio text, shown both truncated (chapter card) and in full (author page).",
  "joined": "2025-04-24",
  "location": "",
  "gender": "Female",
  "links": { "twitter": "https://twitter.com/handle", "website": "https://example.com" },
  "stats": { "followers": 6, "favorites": 1, "reviews_received": 1, "ratings_received": 3 }
}
```

- `id` is optional; defaults to the filename.
- `avatar` is a filename relative to `authors/`, resolved the same way
  `findCoverUri` resolves `cover.*`.
- All other fields are optional; absent fields simply don't render.
- `stats` are **manually authored, display-only numbers** — ARKster is
  offline and account-free, so there is no real follow/review system to
  compute these from. They exist only so the profile screen can visually
  match Royal Road's stat strip; they are not live counts.
- Fiction count is computed from the novels actually linked to this
  author, not stored in the JSON. Total word count is not shown — no
  `wordCount` is tracked anywhere yet.

## Linking a fiction to an author

Each fiction's `metadata.json` may carry `"authorId": "rae-ark"`.
Resolution order, per fiction, on every scan:

1. `metadata.json`'s `authorId`, if it matches an id found in `authors/`
   during this same scan.
2. Otherwise, `metadata.json`'s free-text `author` field, matched
   case-insensitively against `authors/*.json` `name` values.
3. Otherwise, no author link.

`authorId` is **fully recomputed on every scan**, unlike the free-text
`author`/`description`/`genres` fields around it. Those legitimately keep
their old value when a scan finds nothing new (they only ever come from
*this fiction's own* `metadata.json`), but `authorId` also depends on the
external `authors/` folder's current contents — if the linked
`authors/<id>.json` is deleted or renamed, the fiction must actually
unlink, not keep showing a byline/card that leads to an empty page.

## Author page

`AuthorPageScreen` takes an already-resolved `AuthorEntity` and
`List<NovelEntity>` as plain parameters; it never queries the database
itself. It shows: banner + avatar, a stat strip (Follows/Favorites/Reviews
from `stats`, rendered as "—" when unset; Fictions computed live), a
Personal Information card (joined/location/gender/links — rendered only
when at least one is set), and a full, untruncated "About" card for `bio`
when present. Royal Road's Follow/Block buttons and Activity feed are
deliberately **omitted**, not kept as inert styling — there is no real
follow relationship to act on, and an Activity feed would have nothing
genuine to show.

The author page is read-only from the app's perspective, same as
`metadata.json`: users hand-edit or script `author.json` themselves.

## Chapter page (`ReaderScreen`)

- A header above the chapter body: the fiction title as a "back to
  fiction" link (saves reading progress on exit, same as the main back
  button), plus the arc name when the chapter belongs to one.
- Previous/Next navigation, rendered above and below the chapter body.
  `null` (not a no-op) means "no such chapter" — the buttons render
  disabled at the first/last chapter rather than doing nothing when
  tapped. Neighbor resolution looks at the current chapter's index ± 1 in
  the already-loaded, override-applied chapter list — the same list the
  fiction page renders — so it always agrees with what the fiction page's
  chapter list shows.
- An "About the author" card at the end of the chapter body: avatar, name,
  and a bio excerpt truncated to 160 characters (longer than that, and
  only `maxLines = 3`, unlike the untruncated bio on the full author
  page). No linked author means no card, same as every other
  optional-metadata absence in this app. Resolved once per fiction, not
  re-resolved on every Previous/Next hop, since the author doesn't change
  within one fiction.
- Tapping the card or the fiction page's "by {author}" byline opens the
  author page, carrying which screen it was entered from so back returns
  there rather than always bouncing to Home. A fiction with no resolved
  author link renders the byline as plain, non-interactive text.

## Explicitly out of scope

- Any real Follow/notification/social behavior, or in-app editing of
  `author.json` — both would need either network calls or write access
  into the user's library folder, neither of which this app does.

## Open

- Whether a "Total Words" stat on the author page is worth a new
  `wordCount` column computed at scan time, or should stay omitted
  indefinitely.
- Whether a fiction with a cover but no linked author/avatar should fall
  back to a generic placeholder avatar (consistent with the
  auto-generated cover-placeholder pattern) instead of simply showing no
  card.

**Verified:** 2026-11-10 @ (this patch)
