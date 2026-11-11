package com.arkster.app.feature.author

/*
 * STAGE 3 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor. Full plan,
 * rationale and what each later stage does:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md -- that doc
 * is scaffolding for the refactor itself, not a permanent fixture: see its
 * own header for what to do with this pointer once the doc is gone.
 *
 * Moved verbatim from MainActivity.loadAuthorPage, which used to be
 * re-triggered via LaunchedEffect(screen.authorId) - a missing/unknown
 * authorId just leaves `author` null, same never-fail-on-missing-optional-
 * metadata guarantee as before. Now loaded once in init from the
 * destination's SavedStateHandle instead of reactively on every authorId
 * change, since authorId is this ViewModel's own fixed identity (a new
 * author tapped while one's already showing pushes a NEW AuthorRoute/
 * NavBackStackEntry/ViewModel instance, rather than mutating an existing
 * one in place) - see Routes.kt's note on Screen.Author's old `from` field
 * for the other simplification a real back stack gives this screen for free.
 */

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arkster.app.core.database.AppDatabase
import com.arkster.app.core.database.AuthorEntity
import com.arkster.app.core.database.NovelEntity
import com.arkster.app.core.navigation.AuthorRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthorUiState(
    val author: AuthorEntity? = null,
    val novels: List<NovelEntity> = emptyList(),
    val isLoading: Boolean = true
)

class AuthorViewModel(
    savedStateHandle: SavedStateHandle,
    private val db: AppDatabase
) : ViewModel() {

    private val authorId: String = checkNotNull(savedStateHandle[AuthorRoute.authorIdArg])

    private val _uiState = MutableStateFlow(AuthorUiState())
    val uiState: StateFlow<AuthorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = AuthorUiState(
                author = db.authorDao().findById(authorId),
                novels = db.novelDao().byAuthor(authorId),
                isLoading = false
            )
        }
    }
}
