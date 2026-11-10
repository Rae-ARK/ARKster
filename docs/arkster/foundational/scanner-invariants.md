# Scanner invariants

Hard-won rules the scanner, its reconcile step, and the screens built on
top of them must keep. Each one names the bug that taught it — not for
history's sake, but because every one of these was lost once already and
the project doesn't want to lose it again. Full stage-by-stage narratives
live in `git log`, not here; see
[`../../README.md`](../../README.md#no-drift-rule).

## Chapter sort tier

A chapter's position within its folder is **never** just "sorted by
number" — `number = null` (an unnumbered file) must not sort to the
*front*. Every chapter gets an explicit `sortTier`:

- no prefix → tier 0, regular chapter, ordered by its own number/filename.
- `~` prefix → tier 1, bonus/side content (interlude, side story, omake),
  sorted after every tier-0 chapter in its folder. An optional number
  right after the symbol (`~2 Side Story.txt`) orders within the tier.
- `!` prefix → tier 2, closing/meta content (afterword, author's note),
  always last in its folder.
- the marker is stripped before display (`!Afterword.txt` shows as
  "Afterword").
- files with no marker fall back to a keyword match (`afterword`,
  `interlude`, `side story`, `omake`, `extra chapter`, `bonus chapter`) so
  libraries scanned before the marker convention existed don't regress —
  but the marker wins whenever present.

*(taught by Bug 2.)*

## Sort tier is scoped to its arc, never global

`ORDER BY sort_tier, number, title` must **never** run across an entire
novel's chapters in one pass — that puts every tier-0 chapter from every
arc ahead of any tier-1/2 chapter from any arc, so an early arc's
Afterword sorts below a later arc's regular chapters instead of
immediately after its own arc. The correct order is: root-level chapters
first, then each arc's own chapters as one contiguous block in the arc's
`position` order, with tier/number/title only breaking ties *within* a
block. (`ChapterDao.forArc`, which only ever queries one arc, has no
such requirement — this only matters for the "all chapters" / Previous-Next
view.)

*(taught by Bug 2's follow-up: this exact ordering bug didn't reproduce in
the per-arc tab, only in "All Chapters" and chapter-to-chapter
navigation.)*

## Arc position comes from an explicit numeric sort, not enumeration order

SAF's `DocumentFile.listFiles()` makes **no ordering guarantee** — most
providers return filesystem/creation order, not alphabetical. Arc
`position` must be assigned from an explicit sort over the numeric group
each arc-name pattern captures (`"Arc 2 - Whatever"` → `2`), compared
**numerically**, not lexicographically (a string sort still gets `"Arc
10"` before `"Arc 2"` wrong). An arc folder that matches the pattern but
carries no number sorts after every numbered arc, by name.

*(taught by the arc-ordering bug in Stage 5 — tabs were reading "Arc 2,
Arc 1, Arc 3".)*

## Cover resolution: exact match first, then a broadened fallback

`findCoverUri` must match a file whose name is exactly `cover.<ext>`
first, and must **also** fall back to any image in the same folder whose
name contains "cover" as a standalone word (`Arc1_Cover.jpg`, `cover
(1).png`, `COVER_ART.jpg`). A regex that only accepts an exact filename
silently drops any arc or novel that names its cover slightly differently.

*(taught by Bug 3a.)*

## Reader-page cover fallback chain

A chapter's displayed cover resolves **arc → novel → placeholder**, in
that order, resolved once per screen entry (in `MainActivity`, alongside
where `novelTitle`/`arcTitle` are already resolved) rather than inside the
reader composable itself. Never show a blank gap or crash when neither
level has a cover — fall through to the placeholder.

*(taught by Bug 3b.)*

## Deletion safety: a novel is never deleted on one unseen pass

Never clear the in-memory library list before a rescan completes — the
list is only ever trimmed by the reconcile step described below, after a
scan has actually confirmed a folder is gone. A novel is deleted from the
index only when:

1. a scan pass reports it unseen, **and**
2. it was *also* unseen on the previous completed pass (tracked
   persistently, not just in memory, since ARKster scans once per launch),
   **and**
3. the pass that reported it unseen actually saw *some* novel folders at
   all — an empty or partial SAF listing over a previously non-empty
   library must never be read as "the library shrank".

Any per-novel failure that happens *after* the folder was already reported
seen (a metadata read, cover resolution, or upsert throwing) must **not**
count as "unseen" — only a genuinely absent folder does. An unseen novel's
cascade-delete also takes its `chapter_overrides` and `reading_progress`,
which a rescan cannot regenerate, so this gate exists specifically to
avoid destroying data a rescan can't recreate.

*(taught by Bug 4, and Bug 5's two holes in Bug 4's first fix.)*

## An empty `authors/` listing must never wipe existing links

A scan pass that doesn't find the `authors/` folder at all (`authorsFolder
== null`) must delete nothing and must not let a null `authorId` overwrite
an existing link during the novel merge. Only an `authors/` folder that
was *actually found and read* is allowed to prune authors or clear links.

*(taught by Bug 6.)*

## Never fall back to a destructive DB migration

`AppDatabase.create()` must never use `fallbackToDestructiveMigration()` —
a version Room has no migration path for must throw (surfaced as
`DatabaseDowngradeException` for the downgrade case specifically, shown to
the user as "update the app" rather than crashing silently), never
silently drop every table. The database must also be force-opened inside
the same `try`/`catch` (`openHelper.writableDatabase`) rather than left to
Room's lazy-open, so this exception can't escape from inside an unrelated
coroutine later.

*(taught by Bug 7. Still open: no exported schema or migration tests —
this makes a missed migration crash loudly instead of wiping data, but
nothing catches it before release.)*

## An unreadable chapter must surface as an error, never as an empty one

A failed or null content read must never be silently treated as an empty
chapter body — reading progress would then save against content the user
never saw. Return an explicit success/failure result (cancellation still
propagates) and route a failure to a dedicated error screen with retry.

*(taught by Bug 8.)*

## Release the previous folder grant before storing a new one

Android caps the number of persistable URI grants per app, and picking a
new library folder must release the previous one if it differs — not
accumulate grants until the cap is hit and the next pick throws.

*(taught by Bug 9.)*

## Screen-scoped async results must check they're still on-screen

An async lookup that outlives the screen that started it (e.g. a
network metadata fetch) must only apply its result while that same entity
is still the current screen — never write into whatever entity happens to
be on screen when the result arrives.

*(taught by Bug 10.)*

**Verified:** 2026-11-10 @ (this patch)
