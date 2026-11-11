package horizonarkstudio.raeark.arkster.feature.settings

/*
 * STAGE 3 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor. Full plan,
 * rationale and what each later stage does:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md -- that doc
 * is scaffolding for the refactor itself, not a permanent fixture: see its
 * own header for what to do with this pointer once the doc is gone.
 *
 * Backs SettingsScreen and ThemeSettingsScreen - one ViewModel for the
 * whole feature/settings/ package, same as the target layout's comment
 * ("SettingsScreen.kt, ThemeSettingsScreen.kt, AboutScreen.kt,
 * LegalDocumentScreen.kt, LegalContent.kt + ViewModel", singular). AboutScreen
 * and LegalDocumentScreen take no dynamic state (LegalContent.kt is a plain
 * object of static sections) so they don't need anything from this class -
 * only Settings (theme + hasLibrary + Rescan) and ThemeSettings (theme) do.
 *
 * Rescan calls the exact same LibrarySession.scan() HomeViewModel's own
 * auto-scan-on-launch uses - see core/data/LibrarySession.kt's header for
 * why that's a shared Application-scoped singleton both ViewModels are
 * hand-ed, rather than this ViewModel reaching into HomeViewModel's.
 */

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import horizonarkstudio.raeark.arkster.core.data.LibrarySession
import horizonarkstudio.raeark.arkster.core.datastore.PreferencesManager
import horizonarkstudio.raeark.arkster.core.datastore.Theme
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefsManager: PreferencesManager,
    private val librarySession: LibrarySession
) : ViewModel() {

    val currentTheme: StateFlow<Theme> =
        prefsManager.theme.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Theme.LIGHT)

    // No direct Flow<Boolean> on PreferencesManager - mapping libraryUri down to
    // presence-only here rather than adding one, since nothing else needs it.
    val hasLibrary: StateFlow<Boolean> =
        prefsManager.libraryUri.map { it != null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setTheme(theme: Theme) {
        viewModelScope.launch { prefsManager.setTheme(theme) }
    }

    // No-library case (first launch, nothing picked yet) is handled by the caller
    // launching the folder picker instead - same split MainActivity's Settings
    // onRescan branch used to make, now based on hasLibrary.value rather than
    // reading savedUri.value directly.
    fun rescan(treeUri: Uri) {
        viewModelScope.launch { librarySession.scan(treeUri) }
    }
}
