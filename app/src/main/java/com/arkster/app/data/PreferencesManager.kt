package com.arkster.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "arkster_prefs")

enum class Theme {
    LIGHT, DARK, WARM_PAPER
}

class PreferencesManager(private val context: Context) {
    companion object {
        private val LIBRARY_URI_KEY = stringPreferencesKey("library_uri")
        private val THEME_KEY = stringPreferencesKey("theme")
        private val DEFAULT_PAGE_SIZE_KEY = intPreferencesKey("default_page_size")
    }

    val libraryUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[LIBRARY_URI_KEY]
    }

    val theme: Flow<Theme> = context.dataStore.data.map { prefs ->
        when (prefs[THEME_KEY]) {
            "DARK" -> Theme.DARK
            "WARM_PAPER" -> Theme.WARM_PAPER
            else -> Theme.LIGHT
        }
    }

    val defaultPageSize: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_PAGE_SIZE_KEY] ?: 10
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
}
