# Foundational

What ARKster is, how it is built, and why — permanent, kept true by the
no-drift rule in [`../../README.md`](../../README.md). This is the **past**:
once a ladder in `../implementation/` ships, only its lasting decisions move
here; the ladder itself is deleted.

| Doc | Holds |
|---|---|
| [`what-is-arkster.md`](what-is-arkster.md) | Identity, scope, and ARKster's place in the ARK ecosystem (ARKarium, ARKium). Start here. |
| [`architecture.md`](architecture.md) | Storage model (Room + DataStore), the `BookSource`/`ChapterContentRepository` abstractions, scanning, and the shipped v0.1/v0.2 feature set. |
| [`scanner-invariants.md`](scanner-invariants.md) | Hard-won rules the scanner and its reconcile step must keep: sort tiers, arc ordering, deletion safety. Each one names the bug that taught it. |
| [`author-and-reader-model.md`](author-and-reader-model.md) | The `authors/<id>.json` convention and the Royal-Road-style chapter page (author card, Previous/Next). |
| [`ui-design.md`](ui-design.md) | The Royal Road-inspired visual language: reading modes, typography controls, navigation flow. |

**Verified:** 2026-11-10 @ (this patch)
