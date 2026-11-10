package com.arkster.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkster.app.BuildConfig
import com.arkster.app.data.Theme

// Small uppercase, letter-spaced label above each grouped card (PREFERENCES / LEGAL /
// ABOUT), so the index page reads as sections instead of one flat list.
@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsIconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsCardRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconBadge(icon)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        if (showDivider) {
            Divider(
                modifier = Modifier.padding(start = 62.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            content()
        }
    }
}

// Settings used to render everything inline on one flat screen: a bare RadioButton
// group for Theme (LIGHT/DARK/WARM_PAPER only - no "follow system theme"), a Rescan
// button, then Legal rows, with no About entry anywhere. Reworked into an index, same
// "each option is its own page" pattern the sibling ARKarium reader's Settings page
// already uses: Theme now opens its own ThemeSettingsScreen (with the new System
// Default option), and a new About Me row opens AboutScreen. Library stays a direct
// action row here rather than a drill-down page, since "rescan" / "pick a folder" is
// a one-tap action, not a set of options to choose between.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: Theme,
    hasLibrary: Boolean = true,
    onThemeClick: () -> Unit,
    onRescan: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onTermsAndConditions: () -> Unit,
    onAboutMe: () -> Unit,
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
            SettingsSectionLabel("Preferences")
            SettingsCard {
                SettingsCardRow(
                    icon = Icons.Filled.Palette,
                    title = "Theme",
                    subtitle = themeSubtitle(currentTheme),
                    onClick = onThemeClick,
                    showDivider = true
                )
                SettingsCardRow(
                    icon = Icons.Filled.FolderOpen,
                    title = if (hasLibrary) "Rescan Library" else "Select Library Folder",
                    subtitle = if (hasLibrary) {
                        "Check your library folder for new or changed chapters"
                    } else {
                        "Where ARKster reads your novels from"
                    },
                    onClick = onRescan,
                    showDivider = false
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            SettingsSectionLabel("Legal")
            SettingsCard {
                SettingsCardRow(
                    icon = Icons.Filled.PrivacyTip,
                    title = "Privacy Policy",
                    onClick = onPrivacyPolicy,
                    showDivider = true
                )
                SettingsCardRow(
                    icon = Icons.Filled.Gavel,
                    title = "Terms & Conditions",
                    onClick = onTermsAndConditions,
                    showDivider = false
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            SettingsSectionLabel("About")
            SettingsCard {
                SettingsCardRow(
                    icon = Icons.Filled.Person,
                    title = "About Me",
                    onClick = onAboutMe,
                    showDivider = false
                )
            }

            Divider(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))

            // BuildConfig.VERSION_NAME - the single source of truth for the app's
            // version, also read by the crash screens and LegalDocumentScreen. Stays
            // on the index page; it's not really "a setting" so it doesn't get its
            // own sub-page.
            Text(
                "ARKster v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

private fun themeSubtitle(theme: Theme): String = when (theme) {
    Theme.LIGHT -> "Light"
    Theme.DARK -> "Dark"
    Theme.WARM_PAPER -> "Warm Paper"
    Theme.SYSTEM_DEFAULT -> "Matches the system"
}
