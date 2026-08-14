# Bug Report — ARKster

Investigated against commit `44acecb` ("Added new features part 3").

Reported by the maintainer, three issues. Note up front: the maintainer's
running app is described as "at least 4 commits old" ahead of what's on
GitHub (author page / chapter redesign completed "until stage 3"), so parts
of Bug 3 below reference UI that isn't in this repo snapshot yet. Where
that's the case it's called out explicitly rather than guessed at.

---

## Bug 1 — "First image" issue, happens sometimes

**Status: needs reproduction detail.**

The report references a first screenshot showing this happening
intermittently, but the screenshot itself isn't available in this session
(only the text of the conversation carried over). Nothing in the
description alone (no filename, screen, or stack trace) is specific enough
to point at a code path with confidence, so this bug is not yet
root-caused. Needs one of:
- the screenshot re-attached, or
- a short repro description (which screen, what's expected vs. what shows,
  whether it's a cover image, a chapter image, or something else, and
  what "sometimes" correlates with — first scan vs. rescan, specific
  novel, cold start, etc.)

Will be root-caused and patched once that's available.

---

## Bug 2 — Afterword/interlude ordering inside arc folders

**Status: root-caused.**

**Symptom:** Within an arc folder, chapters like `Interlude` and `Afterword`
don't sort to the end of that arc's chapter list the way they should (e.g.
Arc 1 should read `... -> Ch 5 -> Interlude -> Afterword`), and instead land
wherever their filename happens to sort.

**Root cause:**

- `ScannerImpl.parseChapter()` (`app/src/main/java/com/arkster/app/data/ScannerImpl.kt:238`)
  only recognizes a chapter number via one heuristic: a leading integer
  followed by a space, e.g. `026 Not Again.txt`. Any file that doesn't
  match that pattern — including `Afterword.txt`, `Interlude.txt`,
  `Author's Afterword.txt` — falls into the fallback branch and gets
  `number = null`.
- Chapters are then read back out via
  `ChapterDao.forNovel/forArc` (`app/src/main/java/com/arkster/app/data/Dao.kt:92,95`),
  which is `ORDER BY number, title`. SQLite sorts `NULL` before any
  non-null value in ascending order, so every afterword/interlude/bonus
  chapter — having `number = null` — sorts to the *front* of the arc, not
  the back, and multiple such chapters within the same arc then sort
  alphabetically by title (`Afterword` before `Interlude`) rather than in
  any author-intended order.
- `MainActivity.loadNovelDetails()` (`app/src/main/java/com/arkster/app/MainActivity.kt:229-240`)
  takes that same DB order as `rawIndexById` and uses it as the default
  sort key (unless a manual `positionOverride` exists), so the bad
  ordering flows straight through to every screen that lists chapters.
- There is currently no keyword or marker-based detection at all for
  "this is bonus/closing content" — the scanner treats these files purely
  as untitled/unnumbered chapters.

**Proposed fix (already discussed, not yet in this patch):** a filename
marker convention read by the scanner, independent of English keywords:
- no prefix → regular chapter, ordered by its own number/filename as today
- `~` prefix → bonus/side content (interlude, extra chapter, SS, omake,
  side story, etc.), sorted after all regular chapters in that folder
- `!` prefix → closing/meta content (afterword, author's note), always
  sorted last in that folder
- optional number right after the symbol (e.g. `~2 Side Story.txt`)
  controls order within its own tier; otherwise falls back to filename
- the marker is stripped before display, so `!Afterword.txt` shows as
  "Afterword"
- for existing libraries with no marker, keep a best-effort keyword
  fallback (`afterword`, `interlude`, etc.) so nothing already scanned
  regresses to being unordered, but the marker is authoritative once
  present

This requires changes to `parseChapter()`/`parseChaptersInFolder()` in
`ScannerImpl.kt` to compute an explicit sort tier + in-tier order (rather
than relying solely on `ORDER BY number, title`), and likely a schema
addition (e.g. a `sortTier` column) so the ordering survives without
re-parsing on every read.

---

## Bug 3 — Arc covers not showing up / reader page cover fallback

**Status: partially root-caused; part of this depends on code not yet in
this repo.**

### 3a. Arc covers prepared per-arc aren't showing up (3rd screenshot)

- `ScannerImpl.scanChaptersForNovel()` already looks for a per-arc cover:
  `findCoverUri(arcFolder)` at `ScannerImpl.kt:170`, stored on
  `ArcEntity.coverUri` (`ScannerImpl.kt:171`).
- `findCoverUri()` (`ScannerImpl.kt:300-303`) only matches a file whose
  *entire* name is exactly `cover.jpg` / `cover.png` / `cover.webp`
  (`Regex("(?i)cover\\.(jpg|png|webp)$")` matched against the whole
  filename, case-insensitively, but no other characters allowed before
  `cover`). Any arc cover named differently — `Arc1_Cover.jpg`,
  `cover (1).png`, `Cover_Art.jpg`, anything with a suffix/prefix/space —
  is silently skipped and `arcCoverUri` ends up `null`.
- So whether this reproduces depends on the exact filenames used per arc.
  Worth confirming the maintainer's actual arc cover filenames against
  that regex; if they don't match it exactly, that's the whole bug.
- Separately: `arcDao().upsert(arc)` runs on every scan, so as long as
  `findCoverUri` *does* resolve a URI, it should persist correctly —
  this looks like purely a filename-matching gap, not a persistence bug.

### 3b. Reader page should show arc cover -> fiction cover -> placeholder

- This repo's `ReaderScreen.kt` (as of `44acecb`) has no cover image logic
  at all — the only image-ish element on the page is `ReaderAuthorAvatar`
  (`ReaderScreen.kt:487-508`), which renders the *author's* avatar with an
  emoji placeholder, not a novel/arc cover.
- The maintainer's note that "the cover on the reader's page which you've
  added" implies this exists in their local build (ahead of what's
  pushed). Since that code isn't in this repo snapshot, the exact current
  fallback behavior there can't be inspected directly yet — only the
  requirement can be recorded here:
  1. If the chapter being read belongs to an arc, and that arc has a
     `coverUri`, use it.
  2. Else if the novel has a `coverUri`, use it.
  3. Else show the existing placeholder.
- Once the newer reader-header code is available (either pushed, or
  pasted in), this needs to be pointed at `arcId` -> `ArcEntity.coverUri`
  -> `NovelEntity.coverUri` -> placeholder, in that order, rather than
  whatever single source it currently reads from.

---

## Summary of concrete code-level causes found

| Bug | File | Line(s) | Cause |
|---|---|---|---|
| 1 | — | — | Unknown — needs screenshot/repro |
| 2 | `ScannerImpl.kt` | 238-250 | No sort-tier concept; unnumbered chapters get `number = null` |
| 2 | `Dao.kt` | 92, 95 | `ORDER BY number, title` puts `NULL` first, not last |
| 2 | `MainActivity.kt` | 233, 240 | Default chapter order inherited straight from DB order above |
| 3a | `ScannerImpl.kt` | 300-303 | `findCoverUri` regex requires filename to be exactly `cover.<ext>` |
| 3b | `ReaderScreen.kt` | (not present in this commit) | No arc/novel/placeholder cover fallback chain on this screen yet |
