package com.arkster.app.data

// What a finished library scan should do about novels it did not rediscover.
//   deleteNow   - novel ids safe to delete (cascades to arcs/chapters/overrides/progress)
//   pendingNext - novel ids missing this pass that must be missing again next pass
//                 before they are deleted; carry into the next call as `pendingMissing`
//   skipped     - true when the whole cleanup was skipped because the listing looked
//                 unreliable (see below); `pendingNext` is then just `pendingMissing`
data class StaleNovelDecision(
    val deleteNow: Set<String>,
    val pendingNext: Set<String>,
    val skipped: Boolean
)

// A Storage Access Framework provider may hand back a partial or empty listing (not
// warmed up yet, a removable/network volume still populating, I/O jitter right after
// process start) with nothing in the result to tell that apart from a folder that
// really has fewer children. Deleting a novel row cascades to its chapter_overrides and
// reading_progress, which a rescan cannot regenerate, so one pass is never allowed to
// be proof of deletion on its own:
//  1. A listing with zero novel folders while the DB already has novels is treated as
//     "this listing probably failed" - nothing is deleted.
//  2. Otherwise a novel absent from this pass is only remembered; it is deleted only if
//     it was also absent from the previous completed pass (`pendingMissing`). Anything
//     rediscovered drops out of the pending set.
// Pure function, no Android types, so it is unit-testable (ScanReconcileTest).
fun decideStaleNovels(
    sawAnyNovelFolders: Boolean,
    dbNovelIds: Collection<String>,
    seenNovelIds: Set<String>,
    pendingMissing: Set<String>
): StaleNovelDecision {
    if (!sawAnyNovelFolders && dbNovelIds.isNotEmpty()) {
        return StaleNovelDecision(emptySet(), pendingMissing, skipped = true)
    }
    val missing = dbNovelIds.filter { it !in seenNovelIds }.toSet()
    val confirmed = missing.filter { it in pendingMissing }.toSet()
    return StaleNovelDecision(
        deleteNow = confirmed,
        pendingNext = missing - confirmed,
        skipped = false
    )
}
