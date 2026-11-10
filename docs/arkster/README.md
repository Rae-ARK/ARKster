# ARKster docs

Docs for the app in this repo, organised by **lifecycle**: work flows
`proposal → implementation → foundational` (future → present → past, kept
because it still matters).

| Folder | Time | Holds |
|---|---|---|
| [`proposal/`](proposal/README.md) | future | Documented but untouched: ideas, open bug reports, planned work nobody has started. |
| [`implementation/`](implementation/README.md) | present | Work someone has committed to, staged as a rung ladder. |
| [`foundational/`](foundational/README.md) | past, permanent | What ARKster is, how it is built, and the decisions earlier work left behind. |

Start with [`foundational/what-is-arkster.md`](foundational/what-is-arkster.md).

## Lifecycle rule

1. A new idea or report starts in `proposal/`.
2. When someone commits to building it, `git mv` it to `implementation/`
   and split it into rungs (or write it directly there, staged from rung
   0, the way `implementation/epub-support.md` and
   `implementation/oop-modular-monolith-refactor.md` do).
3. When the last rung ships, move only the **decisions and why** into
   `foundational/` and delete the ladder. The commit history is the record
   of how the work happened; `foundational/` only needs to say what is
   true now and why it ended up that way.

Never leave a doc in a folder that no longer describes its truth. Moving a
doc is the update.

This is the same lifecycle rule ARKarium's own docs use
([`ARKarium/docs/arkarium/README.md`](https://github.com/Rae-ARK/ARKarium/blob/main/docs/arkarium/README.md))
— ARKster forked its scanner and sort-tier logic, and it's worth the docs
staying organised the same way even though the two apps' content is its
own. The no-drift rule (keeping all of this true) is in
[`../README.md`](../README.md).
