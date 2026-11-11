# Implementation

Work someone has committed to, staged as a rung ladder — see the lifecycle
rule in [`../README.md`](../README.md). A ladder lives here until its last
rung ships; then only the lasting decisions move to
[`../foundational/`](../foundational/README.md) and the ladder itself is
deleted.

| Ladder | Stage | Status |
|---|---|---|
| [`epub-support.md`](epub-support.md) | 0 of 4 | Stage 0 (documentation) done. Stages 1–4 (zip/OPF/NCX parsing, reader wiring, scanner integration, QA) not started — no app code touched yet. |
| [`oop-modular-monolith-refactor.md`](oop-modular-monolith-refactor.md) | 5 of 7 (partial) | Directory reorg, Application-scoped services, per-feature ViewModels, and `ScannerImpl` decomposition are done. Stage 5 (a real `BookSource`) landed partially this patch — see the doc's own Status section for exactly what shipped and what's still deferred. Remaining stages (a repository layer, QA) not started. This table only tracks how far along each ladder is, not what each stage entails, so it doesn't need editing when a stage's own description changes — it was simply left unedited for two patches running, which is the thing to not repeat. |

Landing order between the two ladders above isn't fixed — see
`oop-modular-monolith-refactor.md`'s open-questions section on whichever
lands first absorbing the other's `BookSource`-adjacent work.

Like every ladder here, both rows above exist only until their last stage
ships; per the lifecycle rule in [`../README.md`](../README.md), the row
and the file it links to are deleted together at that point rather than
kept as a record of finished work.

**Verified:** 2026-11-11 @ (this patch)
