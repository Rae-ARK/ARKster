package com.arkster.app.core.navigation

/*
 * STAGE 3 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor. Full plan,
 * rationale and what each later stage does:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md -- that doc
 * is scaffolding for the refactor itself, not a permanent fixture: see its
 * own header for what to do with this pointer once the doc is gone.
 *
 * Every destination in AppNavHost.kt constructs its ViewModel with
 * `viewModel(factory = AppViewModelFactory(app, backStackEntry))` instead of
 * a no-arg default factory, because every ViewModel below needs at least
 * one of App's services (db, scanner-backed LibrarySession, etc) and some
 * also need a SavedStateHandle (AuthorViewModel, NovelDetailViewModel,
 * ReaderViewModel) scoped to that destination's own route args -
 * AbstractSavedStateViewModelFactory is what wires a SavedStateHandle up
 * correctly either way, so every ViewModel below takes one in its
 * constructor even where it doesn't use it (HomeViewModel,
 * FictionBrowseViewModel, SettingsViewModel), rather than needing two
 * factory shapes.
 *
 * Deliberately a hand-rolled `when` over the requested class rather than a
 * DI framework - this is the same call the refactor doc's "Open questions"
 * #1 already made for Stage 2's App-scoped singletons, extended here to
 * ViewModels for the same reason: single-module, small enough that Hilt/
 * Koin isn't obviously worth the new build dependency yet.
 */

import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.arkster.app.App
import com.arkster.app.feature.author.AuthorViewModel
import com.arkster.app.feature.browse.FictionBrowseViewModel
import com.arkster.app.feature.home.HomeViewModel
import com.arkster.app.feature.noveldetail.NovelDetailViewModel
import com.arkster.app.feature.reader.ReaderViewModel
import com.arkster.app.feature.settings.SettingsViewModel

class AppViewModelFactory(
    private val app: App,
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle? = null
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        val viewModel = when (modelClass) {
            HomeViewModel::class.java ->
                HomeViewModel(app.db, app.prefsManager, app.librarySession)
            FictionBrowseViewModel::class.java ->
                FictionBrowseViewModel(app.librarySession)
            NovelDetailViewModel::class.java ->
                NovelDetailViewModel(handle, app.db, app.metadataProvider, app.librarySession)
            ReaderViewModel::class.java ->
                ReaderViewModel(handle, app.db, app.contentRepo, app.librarySession)
            AuthorViewModel::class.java ->
                AuthorViewModel(handle, app.db)
            SettingsViewModel::class.java ->
                SettingsViewModel(app.prefsManager, app.librarySession)
            else ->
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return viewModel as T
    }
}
