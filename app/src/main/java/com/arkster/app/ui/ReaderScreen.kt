package com.arkster.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkster.app.data.ChapterEntity
import com.arkster.app.data.Theme

enum class ReadingMode {
    LIGHT, SEPIA, DARK
}

// Reader's own LIGHT/SEPIA/DARK palette is intentionally separate from the app-wide
// Theme (it needs a sepia option Theme doesn't have, and users may want a different
// reading background than their nav-screen background) - but it should still *start*
// aligned with the app theme rather than always opening in LIGHT regardless of what
// the user picked in Settings, which read as the app "forgetting" a Dark/Warm Paper
// preference every time a chapter was opened.
private fun readingModeFor(theme: Theme): ReadingMode = when (theme) {
    Theme.LIGHT -> ReadingMode.LIGHT
    Theme.DARK -> ReadingMode.DARK
    Theme.WARM_PAPER -> ReadingMode.SEPIA
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    chapter: ChapterEntity,
    content: String,
    appTheme: Theme = Theme.LIGHT,
    onBack: (Float) -> Unit
) {
    val fontSize = remember { mutableFloatStateOf(18f) }
    val lineHeight = remember { mutableFloatStateOf(1.8f) }
    val readingMode = remember { mutableStateOf(readingModeFor(appTheme)) }
    val showControls = remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    fun currentProgress(): Float {
        val max = scrollState.maxValue
        return if (max <= 0) 1f else (scrollState.value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    }

    // Rough page estimate for the progress readout: ~2000 characters per "page",
    // matching a typical screenful of body text at the default font size.
    val estimatedTotalPages = remember(content) { (content.length / 2000).coerceAtLeast(1) }

    val backgroundColor = when (readingMode.value) {
        ReadingMode.LIGHT -> Color(0xFFFAF9F6)
        ReadingMode.SEPIA -> Color(0xFFF5ECD9)
        ReadingMode.DARK -> Color(0xFF1A1A1A)
    }

    val textColor = when (readingMode.value) {
        ReadingMode.LIGHT -> Color(0xFF2C2C2C)
        ReadingMode.SEPIA -> Color(0xFF3E2C1C)
        ReadingMode.DARK -> Color(0xFFF0F0F0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Chapter title
            Text(
                chapter.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                textAlign = TextAlign.Center,
                color = textColor
            )

            // Chapter content with enhanced typography
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = content,
                    style = TextStyle(
                        fontSize = fontSize.value.sp,
                        lineHeight = (fontSize.value * lineHeight.value).sp,
                        color = textColor,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Justify
                    ),
                    modifier = Modifier.padding(20.dp)
                )
            }

            // Bottom padding for scrolling
            Box(modifier = Modifier.padding(bottom = 100.dp))
        }

        // Top bar
        if (showControls.value) {
            TopAppBar(
                title = { Text(chapter.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { onBack(currentProgress()) }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                modifier = Modifier.background(backgroundColor)
            )
        }

        // Bottom controls panel
        if (showControls.value) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .padding(16.dp)
            ) {
                // Reading progress
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val progressPercent = (currentProgress() * 100).toInt()
                    val currentPage = (currentProgress() * estimatedTotalPages).toInt().coerceIn(1, estimatedTotalPages)
                    Text(
                        "$progressPercent% • page $currentPage of $estimatedTotalPages",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }

                // Font size control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Text:", style = MaterialTheme.typography.labelSmall, color = textColor)
                    IconButton(
                        onClick = { if (fontSize.value > 12) fontSize.value -= 2 },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease font", tint = textColor)
                    }
                    Text(
                        "${fontSize.value.toInt()}",
                        modifier = Modifier.padding(horizontal = 8.dp),
                        color = textColor
                    )
                    IconButton(
                        onClick = { if (fontSize.value < 28) fontSize.value += 2 },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase font", tint = textColor)
                    }
                }

                // Line height control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Spacing:", style = MaterialTheme.typography.labelSmall, color = textColor)
                    Slider(
                        value = lineHeight.value,
                        onValueChange = { lineHeight.value = it },
                        valueRange = 1.0f..2.5f,
                        steps = 10,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    Text(
                        "${String.format("%.1f", lineHeight.value)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Reading mode toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Mode:", style = MaterialTheme.typography.labelSmall, color = textColor)
                    ReadingMode.values().forEach { mode ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                // The mode Surfaces had no onClick/clickable at all before this -
                                // the Light/Sepia/Dark row rendered and highlighted the active
                                // mode correctly, but tapping any of them did nothing, since
                                // nothing ever wrote to `readingMode.value`. This is what made
                                // the reading-mode toggle look broken.
                                .clickable { readingMode.value = mode },
                            color = if (readingMode.value == mode)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                when (mode) {
                                    ReadingMode.LIGHT -> Icon(
                                        Icons.Default.Brightness7,
                                        contentDescription = "Light mode",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (readingMode.value == mode) Color.White else Color.Black
                                    )
                                    ReadingMode.SEPIA -> Text(
                                        "📖",
                                        fontSize = 12.sp
                                    )
                                    ReadingMode.DARK -> Icon(
                                        Icons.Default.Brightness4,
                                        contentDescription = "Dark mode",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (readingMode.value == mode) Color.White else Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
