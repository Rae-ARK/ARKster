package horizonarkstudio.raeark.arkster.feature.settings

/*
 * STAGE 1 MOVE -- 2026-11-11
 * Relocated here (package rename only, no logic touched) as part of the
 * staged OOP modular-monolith refactor. Full plan, rationale, and what
 * each later stage does: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 */

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import horizonarkstudio.raeark.arkster.BuildConfig

// Settings' "About" section used to have no row at all - the sibling ARKarium reader's
// Settings index has an "About Me" row that opens the author's site in-app; ARKster's
// own index was missing the equivalent. ARKarium's version is a WebView wrapper around
// a personal author portfolio, which fits ARKarium's "direct author-to-reader" identity
// but doesn't fit ARKster's: this is a general-purpose, GPLv3, no-accounts local library
// app, not a single author's distribution channel, and ARKster has no androidx.webkit
// dependency to embed a browser with. So this is a plain native About screen instead -
// app identity, version (the same BuildConfig.VERSION_NAME single source of truth the
// Settings index and crash screens already read), and links that hand off to the
// system browser via ACTION_VIEW rather than opening in-app, same as how a link inside
// one of the Legal documents would leave the app today.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            // No browser app available - nothing to fall back to from here.
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("About Me") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text("ARKster", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Text(
                "A privacy-first, offline-first local library and reader - a messy " +
                    "folder of novels, made to feel like a private Royal Road on your " +
                    "phone. No accounts, no sign-in, no analytics. Built by Rae-ARK.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)
            )

            Divider()

            AboutLinkRow(
                icon = Icons.Filled.Person,
                title = "More from Rae-ARK",
                subtitle = "rae-ark.horizonarkstudio.workers.dev",
                onClick = { openUrl("https://rae-ark.horizonarkstudio.workers.dev/") }
            )
            Divider(modifier = Modifier.padding(start = 50.dp))
            AboutLinkRow(
                icon = Icons.Filled.Code,
                title = "Source code",
                subtitle = "GPLv3 - github.com/Rae-ARK/ARKster",
                onClick = { openUrl("https://github.com/Rae-ARK/ARKster") }
            )
            Divider(modifier = Modifier.padding(start = 50.dp))
        }
    }
}

@Composable
private fun AboutLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}
