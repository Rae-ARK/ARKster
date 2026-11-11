# `feature/`

**Stage 1 of the OOP modular-monolith refactor** moved files into this
directory (pure `git mv` + package rename -- no logic changed). See
[`docs/arkster/implementation/oop-modular-monolith-refactor.md`](../../../../../../../../docs/arkster/implementation/oop-modular-monolith-refactor.md)
for the full staged plan, including what still changes here in later stages.

One package per screen/user-facing feature. As of Stage 1 these hold only the Composable screens moved from the old ui/ package -- no ViewModels yet (that is Stage 3), so screens still take their state as plain constructor params the way MainActivity's inline when-block always passed it.

This file exists so that directory's *purpose and current limits* are
visible without having to find the refactor doc first -- delete or rewrite
it once this directory's shape has moved past what Stage 1 established.
