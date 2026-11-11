package horizonarkstudio.raeark.arkster.core.datastore

/*
 * STAGE 1 MOVE -- 2026-11-11
 * Relocated here (package rename only, no logic touched) as part of the
 * staged OOP modular-monolith refactor. Full plan, rationale, and what
 * each later stage does: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 */

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore by preferencesDataStore(name = "arkster_prefs")

// SYSTEM_DEFAULT follows the device's day/night setting (resolved to LIGHT or DARK by
// MainActivity.resolveTheme) rather than being a palette of its own - same "follow
// system theme" option the sibling ARKarium reader already has; ARKster's own Settings
// page never offered it, LIGHT/DARK/WARM_PAPER only, until now.
enum class Theme {
    LIGHT, DARK, WARM_PAPER, SYSTEM_DEFAULT
}

class PreferencesManager(private val context: Context) {
    companion object {
        private val LIBRARY_URI_KEY = stringPreferencesKey("library_uri")
        private val THEME_KEY = stringPreferencesKey("theme")
        private val DEFAULT_PAGE_SIZE_KEY = intPreferencesKey("default_page_size")
        // Reader font size/line height used to live only as `remember` state inside
        // ReaderScreen - every value picked in the "Reader Preferences" pill was lost
        // the moment you left the reader (back to the fiction page, let alone the app),
        // so the very next chapter opened back at the hardcoded 18sp/1.8 defaults. These
        // two keys persist the last value chosen, read back as the pill's starting point
        // next time instead of resetting it.
        private val READER_FONT_SIZE_KEY = floatPreferencesKey("reader_font_size")
        private val READER_LINE_HEIGHT_KEY = floatPreferencesKey("reader_line_height")
        // Backs decideStaleNovels' two-pass reconciliation (see ScanReconcile.kt and
        // docs/arkster/foundational/scanner-invariants.md "Deletion safety"). LibrarySession
        // reads this once at the top of every scan and rewrites it in onScanCompleted, so a
        // novel missing on one pass is only actually deleted once it's also missing on the
        // very next completed pass - this has to survive a process restart (ARKster scans
        // once per launch), which is why it lives here instead of in-memory.
        private val PENDING_MISSING_NOVEL_IDS_KEY = stringSetPreferencesKey("pending_missing_novel_ids")
    }

    // libraryUri.collect() and theme.collect() run unattended in MainActivity.onCreate,
    // before the user has touched anything - same situation as startScan(). DataStore's
    // .data Flow throws IOException if the prefs file on disk can't be read (corrupted
    // file, first-run race, low storage, some OEM storage quirks); without this .catch,
    // that exception propagates out of collect() and crashes the whole app on launch
    // since nothing downstream of dataStore.data was previously guarding for it. Falling
    // back to emptyPreferences() just means defaults are used for that read, matching
    // DataStore's own recommended pattern.
    private val safePrefs = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val libraryUri: Flow<String?> = safePrefs.map { prefs ->
        prefs[LIBRARY_URI_KEY]
    }

    val theme: Flow<Theme> = safePrefs.map { prefs ->
        when (prefs[THEME_KEY]) {
            "DARK" -> Theme.DARK
            "WARM_PAPER" -> Theme.WARM_PAPER
            "SYSTEM_DEFAULT" -> Theme.SYSTEM_DEFAULT
            // Unset stays LIGHT rather than switching to SYSTEM_DEFAULT - existing
            // installs with no THEME_KEY written yet keep the theme they're already
            // seeing instead of it silently changing out from under them on upgrade.
            else -> Theme.LIGHT
        }
    }

    val defaultPageSize: Flow<Int> = safePrefs.map { prefs ->
        prefs[DEFAULT_PAGE_SIZE_KEY] ?: 10
    }

    val readerFontSize: Flow<Float> = safePrefs.map { prefs ->
        prefs[READER_FONT_SIZE_KEY] ?: 18f
    }

    val readerLineHeight: Flow<Float> = safePrefs.map { prefs ->
        prefs[READER_LINE_HEIGHT_KEY] ?: 1.8f
    }

    val pendingMissingNovelIds: Flow<Set<String>> = safePrefs.map { prefs ->
        prefs[PENDING_MISSING_NOVEL_IDS_KEY] ?: emptySet()
    }

    suspend fun setLibraryUri(uri: String) {
        context.dataStore.edit { prefs ->
            prefs[LIBRARY_URI_KEY] = uri
        }
    }

    suspend fun setTheme(theme: Theme) {
        context.dataStore.edit { prefs ->
            prefs[THEME_KEY] = theme.name
        }
    }

    suspend fun setDefaultPageSize(size: Int) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_PAGE_SIZE_KEY] = size
        }
    }

    suspend fun setReaderFontSize(size: Float) {
        context.dataStore.edit { prefs ->
            prefs[READER_FONT_SIZE_KEY] = size
        }
    }

    suspend fun setReaderLineHeight(height: Float) {
        context.dataStore.edit { prefs ->
            prefs[READER_LINE_HEIGHT_KEY] = height
        }
    }

    suspend fun setPendingMissingNovelIds(ids: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[PENDING_MISSING_NOVEL_IDS_KEY] = ids
        }
    }
}
