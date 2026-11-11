# Implementation

Work someone has committed to, staged as a rung ladder — see the lifecycle
rule in [`../README.md`](../README.md). A ladder lives here until its last
rung ships; then only the lasting decisions move to
[`../foundational/`](../foundational/README.md) and the ladder itself is
deleted.

| Ladder | Stage | Status |
|---|---|---|
| [`epub-support.md`](epub-support.md) | 0 of 4 | Stage 0 (documentation) done. Stages 1–4 (zip/OPF/NCX parsing, reader wiring, scanner integration, QA) not started — no app code touched yet. |
| [`oop-modular-monolith-refactor.md`](oop-modular-monolith-refactor.md) | 0 of 7 | Stage 0 (documentation) done. Stages 1–7 (directory reorg, Application-scoped services, per-feature ViewModels, scanner decomposition, a real `BookSource`, a repository layer, QA) not started — no app code touched yet. |

Landing order between the two ladders above isn't fixed — see
`oop-modular-monolith-refactor.md`'s Stage 5 note on whichever lands
first absorbing the other's `BookSource`-adjacent work.

**Verified:** 2026-11-11 @ (this patch)
