package com.arkster.app.scanner

// See docs/arkster/proposal/bugs.md "Folder rename orphans a novel's data": novel/arc/
// chapter ids are hashed from the SAF folder URI, so renaming a novel's folder makes it
// look like a brand-new novel to the next scan, and the two-pass reconciliation in
// ScanReconcile.kt then deletes the old id's reading_progress/chapter_overrides once it's
// gone unseen for two passes. A real fix (a stable id persisted into the novel folder at
// first scan) needs write access this app doesn't request or use anywhere - see that bug's
// entry for why that's a permission-model decision, not just a code change.
//
// Short of that, a plain rename doesn't touch any chapter file's own name - only the
// parent folder's path segment changes - so the set of chapter filenames a novel has is a
// signal that survives the exact failure mode this bug describes, with no write access
// needed. This is a mitigation for that one case (a folder renamed with its contents
// otherwise untouched), not a replacement for a real stable id: a rename combined with
// adding/removing/renaming a chapter in the same pass, or two different novels that
// happen to share an identical filename set, won't match (the second case deliberately
// fails closed - see the uniqueness check below - rather than risk migrating one novel's
// progress onto an unrelated one).

data class RenameMatch(val oldNovelId: String, val newNovelId: String)

// candidateStaleIds: novel ids decideStaleNovels is about to delete this pass.
// candidateNewIds: novel ids this pass upserted that didn't exist in the DB before this
//   scan started - i.e. ids a renamed (or genuinely new) folder produced.
// chapterFilenamesByNovelId: each candidate id's chapter filenames (decoded leaf name of
//   ChapterEntity.sourcePath - see LibrarySession's call site), for whichever of the two
//   sets above it belongs to. A novel with no chapters has no signal to match on and is
//   left out, same as a non-match - it's handled by the normal delete path instead.
//
// Pure function, no Android/DB types, so it's unit-testable the same way
// ScanReconcile.decideStaleNovels is (see RenameMigrationTest).
fun detectRenameMatches(
    candidateStaleIds: Set<String>,
    candidateNewIds: Set<String>,
    chapterFilenamesByNovelId: Map<String, Set<String>>
): List<RenameMatch> {
    val staleSignatures = candidateStaleIds.mapNotNull { id ->
        chapterFilenamesByNovelId[id]?.takeIf { it.isNotEmpty() }?.let { id to it }
    }
    val newSignatures = candidateNewIds.mapNotNull { id ->
        chapterFilenamesByNovelId[id]?.takeIf { it.isNotEmpty() }?.let { id to it }
    }

    return staleSignatures.mapNotNull { (oldId, signature) ->
        val matchingNew = newSignatures.filter { it.second == signature }
        val matchingOld = staleSignatures.filter { it.second == signature }
        // Require the signature to be unique on BOTH sides before trusting it: if two
        // stale novels (or two new ones) share an identical filename set, there's no way
        // to tell which pairs with which, so neither is migrated and both fall back to
        // the normal delete/discover path instead of guessing.
        if (matchingNew.size == 1 && matchingOld.size == 1) {
            RenameMatch(oldNovelId = oldId, newNovelId = matchingNew.single().first)
        } else {
            null
        }
    }
}
