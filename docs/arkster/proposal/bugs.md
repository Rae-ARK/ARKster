# Open bug reports

Found but not yet picked up — see the lifecycle rule in
[`README.md`](README.md). A one-off bug fix doesn't need a rung ladder in
`implementation/`: land the fix and delete that bug's entry here in the
same commit. The no-drift rule in
[`../../README.md`](../../README.md#no-drift-rule) applies to this file
like any other doc.

## First image issue, happens sometimes

Reported alongside the bugs that became
[`scanner-invariants.md`](../foundational/scanner-invariants.md), but
never root-caused: no screenshot or repro detail survived (which screen,
whether it's a cover or chapter image, what "sometimes" correlates with —
first scan vs. rescan, a specific novel, cold start). Needs a fresh repro
before anyone can pick it up.

## Persisted folder-grant leak on re-pick

The
[`scanner-invariants.md`](../foundational/scanner-invariants.md#release-the-previous-folder-grant-before-storing-a-new-one)
invariant this is supposed to satisfy is violated in practice:
`MainActivity`'s folder-picker callback calls `takePersistableUriPermission`
on every re-pick, but nothing in the app ever calls
`releasePersistableUriPermission`. Re-picking a library folder repeatedly
accumulates grants toward Android's per-app cap instead of releasing the
previous one, and the next pick past the cap throws.

## Cover/avatar resolver caches never invalidate

`CachingCoverResolverProxy` and `CachingAuthorAvatarResolverProxy` key
their cache on folder URI alone (no content hash, no `lastModified`, no
eviction), for the lifetime of the `App`-scoped scanner singleton. A
rescan that picks up a cover or avatar image newly added to an
already-scanned folder won't show it until the app process restarts — at
odds with this app's diff-based rescan premise, since the "Deletion
safety" reconciliation in `scanner-invariants.md` only ever covered the
novel/arc/chapter tree itself, not resolved cover/avatar URIs.

## Fiction Browse's sort options don't sort by what they claim

Of the four options [`ui-design.md`](../foundational/ui-design.md)
promises (Recently Updated, Most Popular, Highest Rated, Newest), none use
real data — `NovelEntity` has no timestamp, popularity, or rating field.
In `FictionBrowseScreen`, Recently Updated is actually alphabetical by
title, Newest is reverse-alphabetical, and Most Popular / Highest Rated
each re-shuffle the whole grid on every filter change. This is the main
library-browsing screen's primary control, exercised on every visit.

**Verified:** 2026-11-11 @ (this patch)
