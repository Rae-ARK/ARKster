# `core/`

**Stage 1 of the OOP modular-monolith refactor** moved files into this
directory (pure `git mv` + package rename -- no logic changed). See
[`docs/arkster/implementation/oop-modular-monolith-refactor.md`](../../../../../../../../docs/arkster/implementation/oop-modular-monolith-refactor.md)
for the full staged plan, including what still changes here in later stages.

Shared, feature-agnostic infrastructure (database, datastore, design tokens). Nothing here should import from feature/.

This file exists so that directory's *purpose and current limits* are
visible without having to find the refactor doc first -- delete or rewrite
it once this directory's shape has moved past what Stage 1 established.
