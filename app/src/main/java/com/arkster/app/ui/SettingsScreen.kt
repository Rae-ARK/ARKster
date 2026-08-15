package com.arkster.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkster.app.BuildConfig
import com.arkster.app.data.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: Theme,
    hasLibrary: Boolean = true,
    onThemeSelected: (Theme) -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Text("Theme", modifier = Modifier.padding(bottom = 8.dp))
            listOf(Theme.LIGHT, Theme.DARK, Theme.WARM_PAPER).forEach { theme ->
                androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth()) {
                    RadioButton(
                        selected = currentTheme == theme,
                        onClick = { onThemeSelected(theme) }
                    )
                    Text(theme.name.replace("_", " "))
                }
            }

            Button(
                onClick = onRescan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(if (hasLibrary) "Rescan Library" else "Select Library Folder")
            }

            Divider(modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))

            // BuildConfig.VERSION_NAME is generated from app/build.gradle.kts'
            // defaultConfig.versionName - the single source of truth for the app's
            // version. This is the only place in the running app's normal UI that
            // shows it; the crash screens in MainActivity read the same field.
            Text(
                "ARKster v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
