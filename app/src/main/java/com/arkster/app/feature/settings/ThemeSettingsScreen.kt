package com.arkster.app.feature.settings

/*
 * STAGE 1 MOVE -- 2026-11-11
 * Relocated here (package rename only, no logic touched) as part of the
 * staged OOP modular-monolith refactor. Full plan, rationale, and what
 * each later stage does: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 */

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arkster.app.core.datastore.Theme

// Theme used to be chosen inline on the old flat Settings screen, three bare
// RadioButton rows with no "follow system theme" option at all (see bugs.md/
// SettingsScreen.kt history). Pulled out to its own page as part of turning
// Settings into a proper index (one destination per option, same pattern the
// sibling ARKarium reader already settled on for its own Settings page) -
// and System Default is a new fourth option here, resolved to Light or Dark
// by MainActivity.resolveTheme based on the device's own day/night setting.
//
// ARKarium's equivalent page also offers a "daytime look" sub-choice for
// System Default (Light vs Warm Paper during the day). That's deliberately
// not carried over here - it's an extra decision ARKster's simpler Settings
// page was never asked for, and System Default behaving as a plain Light/Dark
// toggle matches what "follow system theme" means on most apps.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    currentTheme: Theme,
    onThemeSelected: (Theme) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("Theme") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(modifier = Modifier.padding(16.dp)) {
            listOf(Theme.LIGHT, Theme.DARK, Theme.WARM_PAPER, Theme.SYSTEM_DEFAULT).forEach { theme ->
                ThemeOptionCard(
                    theme = theme,
                    selected = currentTheme == theme,
                    onClick = { onThemeSelected(theme) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

// theme's display name - System Default doesn't read well as "SYSTEM DEFAULT" with
// the same underscore-split-then-titlecase treatment the other three get, so it gets
// its own label.
private fun displayName(theme: Theme): String = when (theme) {
    Theme.SYSTEM_DEFAULT -> "System Default"
    else -> theme.name.split("_").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun ThemeOptionCard(
    theme: Theme,
    selected: Boolean,
    onClick: () -> Unit
) {
    // System Default has no single palette of its own (it borrows Dark at night and
    // Light during the day - see MainActivity.resolveTheme), so its swatch is a plain
    // half-and-half light/dark hint rather than one of the three real theme palettes.
    val (swatchA, swatchB) = when (theme) {
        Theme.LIGHT -> Color(0xFFFFFBFE) to Color(0xFF6750A4)
        Theme.DARK -> Color(0xFF1C1B1F) to Color(0xFFD0BCFF)
        Theme.WARM_PAPER -> Color(0xFFF5ECD9) to Color(0xFF8B5E34)
        Theme.SYSTEM_DEFAULT -> Color(0xFFFFFBFE) to Color(0xFF1C1B1F)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(32.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(swatchA, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(17.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(swatchB, CircleShape)
                    )
                }
            }

            Text(
                displayName(theme),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            )

            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
