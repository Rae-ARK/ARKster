package com.arkster.app.feature.browse

/*
 * STAGE 3 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor. Full plan,
 * rationale and what each later stage does:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md -- that doc
 * is scaffolding for the refactor itself, not a permanent fixture: see its
 * own header for what to do with this pointer once the doc is gone.
 *
 * A thin pass-through onto LibrarySession.novels - unlike
 * NovelDetailViewModel's private snapshot, this one shares the exact same
 * StateFlow instance Home reads, so a scan or a metadata fetch completing
 * elsewhere shows up here without this screen doing anything to reload -
 * see AppNavHost.kt's note at NovelDetailRoute on why NovelDetail can't do
 * the same.
 */

import androidx.lifecycle.ViewModel
import com.arkster.app.core.data.LibrarySession
import com.arkster.app.core.database.NovelEntity
import kotlinx.coroutines.flow.StateFlow

class FictionBrowseViewModel(
    librarySession: LibrarySession
) : ViewModel() {
    val novels: StateFlow<List<NovelEntity>> = librarySession.novels
}
