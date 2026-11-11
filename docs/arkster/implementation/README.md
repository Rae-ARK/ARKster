# Implementation

Work someone has committed to, staged as a rung ladder — see the lifecycle
rule in [`../README.md`](../README.md). A ladder lives here until its last
rung ships; then only the lasting decisions move to
[`../foundational/`](../foundational/README.md) and the ladder itself is
deleted.

| Ladder | Stage | Status |
|---|---|---|
| [`epub-support.md`](epub-support.md) | 0 of 4 | Stage 0 (documentation) done. Stages 1–4 (zip/OPF/NCX parsing, reader wiring, scanner integration, QA) not started — no app code touched yet. |
| [`oop-modular-monolith-refactor.md`](oop-modular-monolith-refactor.md) | 2 of 7 | Directory reorg and Application-scoped services done. Remaining stages (per-feature ViewModels, scanner decomposition, a real `BookSource`, a repository layer, QA) not started. See the doc's own Status section for specifics — this table only tracks how far along each ladder is, not what each stage entails, so it doesn't need editing when a stage's own description changes. |

Landing order between the two ladders above isn't fixed — see
`oop-modular-monolith-refactor.md`'s open-questions section on whichever
lands first absorbing the other's `BookSource`-adjacent work.

Like every ladder here, both rows above exist only until their last stage
ships; per the lifecycle rule in [`../README.md`](../README.md), the row
and the file it links to are deleted together at that point rather than
kept as a record of finished work.

**Verified:** 2026-11-11 @ e07d4e5
