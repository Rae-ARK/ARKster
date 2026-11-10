# Docs index

| Folder | What it is |
|---|---|
| [`arkster/`](arkster/README.md) | **This app.** Organised by lifecycle: `proposal` (future) → `implementation` (present) → `foundational` (permanent). Start at [`arkster/foundational/what-is-arkster.md`](arkster/foundational/what-is-arkster.md). |
| [`kotlin-design-patterns/`](kotlin-design-patterns/README.md) | **This app's GoF Reference.** I believe the name itself is self explanatory. It is a clone of iamjosephmj's kotlin-design-patters. Start at [`kotlin-design-patterns/README.md`](kotlin-design-patterns/README.md). It contains info, with src showing examples, for both app and tests using GoF. |

ARKster has no `reference/` folder the way its sibling
[ARKarium](https://github.com/Rae-ARK/ARKarium) does. ARKarium's own docs
explain why it needed one (a frozen copy of this repo's old `bugs.md`,
pulled in so its source comments had something to point at, later removed
once it had gone stale) — ARKster is the original, not the inheritor, so
there is nothing upstream of it to freeze a copy of.

## No-drift rule

Docs rot silently; this section is the guard. Whoever changes the code owns
the docs for that change.

**With every change** (same commit):

- Changed behavior, a screen, a table, or a scan/storage contract → update
  the doc that describes it.
- Finished or started a rung → update its state in the ladder and in
  `implementation/README.md`.
- Moved, renamed, or deleted a doc → fix every pointer:
  `grep -rn "<old name>" app/ docs/ README.md`.
- Added a doc → add it to its folder's `README.md` index.
- Verified a doc against the code → bump its `Verified:` line (date and
  short commit). Never bump it without actually checking.

**Periodically** (every release bump, and at least monthly):

1. Skim `foundational/architecture.md` against `app/` for things that no
   longer exist or are missing.
2. Skim `implementation/` for a ladder whose rungs are all done (graduate
   it) or stalled (demote it back to `proposal/`).
3. Skim `proposal/` for items now built (delete or graduate) or dead
   (delete).

**Writing rules** (these keep docs maintainable):

- One fact, one home. Link instead of repeating.
- Point at code (`file`, `symbol`) rather than pasting it; avoid line
  numbers and counts that go stale.
- State only what is true now. History belongs to `git log`, not to the
  doc.
- Foundational docs describe what exists; plans carry "will", and only in
  `proposal/` and `implementation/`.
- Be short. If a doc outgrows its purpose, split or cut it.
