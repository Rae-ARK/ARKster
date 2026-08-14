package com.arkster.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.arkster.app.data.ArcEntity
import com.arkster.app.data.ChapterEntity
import com.arkster.app.data.NovelEntity

private val PAGE_SIZE_OPTIONS = listOf(10, 20, 50, 100)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelDetailScreen(
    novel: NovelEntity,
    chapters: List<ChapterEntity>,
    arcs: List<ArcEntity>,
    overriddenChapterIds: Set<String> = emptySet(),
    onBack: () -> Unit,
    onChapterSelected: (ChapterEntity) -> Unit,
    onResizePages: (Int) -> Unit = {},
    onEditClick: () -> Unit = {},
    onFetchInfoClick: () -> Unit = {}
) {
    val selectedTabIndex = remember { mutableIntStateOf(0) }
    // Seeded from the novel's persisted page_size so the preference survives navigation
    // away and back, not just within a single composition.
    val pageSize = remember { mutableIntStateOf(novel.pageSize) }
    val currentPage = remember { mutableIntStateOf(0) }
    val tabs = listOf("All Chapters") + arcs.map { it.name }

    val chaptersInTab = when (selectedTabIndex.intValue) {
        0 -> chapters
        else -> {
            val arcId = arcs.getOrNull(selectedTabIndex.intValue - 1)?.id
            chapters.filter { it.arcId == arcId }
        }
    }

    // Switching tabs (or resizing) changes the item count under the current page, so
    // reset back to page 1 rather than showing a now-meaningless page index.
    LaunchedEffect(selectedTabIndex.intValue, pageSize.intValue) {
        currentPage.intValue = 0
    }

    val totalPages = if (chaptersInTab.isEmpty()) 1
        else ((chaptersInTab.size - 1) / pageSize.intValue) + 1
    val safePage = currentPage.intValue.coerceIn(0, totalPages - 1)
    val chaptersToShow = chaptersInTab
        .drop(safePage * pageSize.intValue)
        .take(pageSize.intValue)

    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text(novel.title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = onFetchInfoClick) {
                    Icon(Icons.Default.Info, contentDescription = "Fetch info")
                }
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
            }
        )

        // About section - only present once the user has run "Fetch info" at least
        // once (see MainActivity.fetchMetadataFor). Absent for scanned novels that
        // haven't been matched to anything, so this never displaces the chapter list.
        if (novel.description != null || novel.remoteCoverUrl != null) {
            Row(modifier = Modifier.padding(16.dp)) {
                if (novel.remoteCoverUrl != null) {
                    AsyncImage(
                        model = novel.remoteCoverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .width(64.dp)
                            .height(88.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                Column(modifier = Modifier.padding(start = if (novel.remoteCoverUrl != null) 12.dp else 0.dp)) {
                    if (!novel.genres.isNullOrBlank()) {
                        Text(
                            novel.genres,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!novel.description.isNullOrBlank()) {
                        Text(
                            novel.description,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Pagination size controls
        Row(modifier = Modifier.padding(8.dp)) {
            PAGE_SIZE_OPTIONS.forEach { size ->
                Button(
                    onClick = {
                        pageSize.intValue = size
                        onResizePages(size)
                    },
                    modifier = Modifier
                        .padding(4.dp)
                        .weight(1f)
                ) {
                    Text("$size")
                }
            }
        }

        // Arc tabs
        if (tabs.isNotEmpty()) {
            TabRow(selectedTabIndex = selectedTabIndex.intValue) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex.intValue == index,
                        onClick = { selectedTabIndex.intValue = index },
                        text = { Text(tab) }
                    )
                }
            }
        }

        // Chapter list (current page only)
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(chaptersToShow) { chapter ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChapterSelected(chapter) }
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${chapter.number ?: ""} ${chapter.title}".trim(),
                            modifier = Modifier.weight(1f)
                        )
                        if (chapter.id in overriddenChapterIds) {
                            Text(
                                "Edited",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Page navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { if (safePage > 0) currentPage.intValue = safePage - 1 },
                enabled = safePage > 0
            ) {
                Text("Prev")
            }
            Text(
                "Page ${safePage + 1} of $totalPages",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            OutlinedButton(
                onClick = { if (safePage < totalPages - 1) currentPage.intValue = safePage + 1 },
                enabled = safePage < totalPages - 1
            ) {
                Text("Next")
            }
        }
    }
}
