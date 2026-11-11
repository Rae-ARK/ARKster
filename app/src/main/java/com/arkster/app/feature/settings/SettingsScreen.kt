package com.arkster.app.feature.settings

/*
 * STAGE 1 MOVE -- 2026-11-11
 * Relocated here (package rename only, no logic touched) as part of the
 * staged OOP modular-monolith refactor. Full plan, rationale, and what
 * each later stage does: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 */

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
import com.arkster.app.core.datastore.Theme

// Small uppercase, letter-spaced label used above each grouped card
// (PREFERENCES / LEGAL / ABOUT) so this screen reads as sections instead of
// one long undifferentiated list - lifted from ARKarium's SettingsScreen,
// same idiom.
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

// A round, tinted "badge" behind a row's leading icon. Circular icon badges
// like this are what turn a settings row from "a line of text with a glyph
// in front of it" into something that actually looks designed - the tinted
// circle gives the icon room to breathe and a splash of the app's primary
// color instead of a flat monochrome glyph sitting directly on the row
// background. Lifted from ARKarium's SettingsIconBadge; colors come from
// ARKster's own theme tokens (see ui/theme/AppTheme.kt), so this reads as
// ARKster's forest-green brand, not ARKarium's teal.
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

// A tappable, badge-and-chevron settings row - used for Theme, Library
// (rescan / pick a folder), the two Legal rows, and About Me, all of which
// either perform an action or navigate to their own screen (ThemeSettingsScreen,
// LegalDocumentScreen, AboutScreen) rather than doing anything inline here.
// `showDivider` draws the hairline that separates it from the next row in the
// same card - omitted after the last row so the card's own rounded bottom
// edge is the visual stop instead of a divider running right up against it.
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

// One rounded, tinted card wrapping a whole section's rows - the same
// "surfaceVariant Card" idiom ARKarium's SettingsCard uses, reused here so
// grouped settings feel consistent with the rest of ARKster's visual
// language (LegalDocumentScreen's section cards use the same idiom) rather
// than inventing a new style.
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
// a one-tap action, not a set of options to choose between. Presentation (section
// labels, icon-badged rows, grouped cards) is lifted from ARKarium's SettingsScreen,
// same idiom, restyled onto ARKster's own settings.
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
                    title = "Library",
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

private fun themeSubtitle(theme: Theme): String = when (theme) {
    Theme.LIGHT -> "Light"
    Theme.DARK -> "Dark"
    Theme.WARM_PAPER -> "Warm Paper"
    Theme.SYSTEM_DEFAULT -> "Matches the system"
}
