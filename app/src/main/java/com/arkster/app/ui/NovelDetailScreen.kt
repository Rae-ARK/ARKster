package com.arkster.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.arkster.app.data.ArcEntity
import com.arkster.app.data.ChapterEntity
import com.arkster.app.data.NovelEntity

private val PAGE_SIZE_OPTIONS = listOf(10, 20, 50, 100)

// Roughly the point at which a Royal Road-style synopsis stops fitting in a
// collapsed few-line preview and needs a "Show more" toggle.
private const val DESCRIPTION_COLLAPSE_THRESHOLD = 220

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
    onFetchInfoClick: () -> Unit = {},
    onAuthorClick: () -> Unit = {}
) {
    val selectedTabIndex = remember { mutableIntStateOf(0) }
    // Seeded from the novel's persisted page_size so the preference survives navigation
    // away and back, not just within a single composition.
    val pageSize = remember { mutableIntStateOf(novel.pageSize) }
    val currentPage = remember { mutableIntStateOf(0) }
    val tabs = listOf("All Chapters") + arcs.map { it.name }
    var descriptionExpanded by remember { mutableStateOf(false) }

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
            title = { Text(novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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

        LazyColumn(modifier = Modifier.weight(1f, fill = true)) {
            // Cover + title/author header, Royal Road-style: big cover on the left,
            // title/author stacked beside it.
            item {
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)) {
                    NovelCoverThumb(
                        // Local cover.png from the scanned folder takes priority over a
                        // remote metadata cover (only present after "Fetch info" has
                        // been run) - the local file is the one the user actually
                        // placed there.
                        coverUrl = novel.coverUri ?: novel.remoteCoverUrl,
                        modifier = Modifier
                            .width(96.dp)
                            .height(136.dp)
                    )
                    Column(
                        modifier = Modifier
                            .padding(start = 14.dp)
                            .weight(1f),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Text(
                            novel.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!novel.author.isNullOrBlank()) {
                            Text(
                                "by ${novel.author}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    // Only a real tap target when this fiction actually
                                    // resolved to an author/<id>.json - otherwise there's
                                    // nowhere for the tap to go (see
                                    // AUTHOR_PAGE_AND_CHAPTER_REDESIGN.md's "no author
                                    // link" fallback), same as the byline just being
                                    // plain text today.
                                    .let { base ->
                                        if (novel.authorId != null) base.clickable { onAuthorClick() } else base
                                    }
                            )
                        }
                        val statusLabel = when (novel.readingStatus) {
                            "IN_PROGRESS" -> "Reading"
                            "COMPLETED" -> "Completed"
                            else -> null
                        }
                        if (statusLabel != null) {
                            Text(
                                statusLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }

            // Genre/tag chips - Royal Road shows these as a row of pills right under
            // the title. Scrollable since a novel can carry more tags than fit on screen.
            if (!novel.genres.isNullOrBlank()) {
                item {
                    val genreList = remember(novel.genres) {
                        novel.genres.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    }
                    LazyRow(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        items(genreList) { genre ->
                            AssistChip(
                                onClick = {},
                                label = { Text(genre, fontSize = 12.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // Synopsis - collapsed to a short preview with a "Show more" toggle, same
            // pattern Royal Road uses to keep the fold above the fiction's stats/ToC.
            if (!novel.description.isNullOrBlank()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            novel.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (novel.description.length > DESCRIPTION_COLLAPSE_THRESHOLD) {
                            Text(
                                if (descriptionExpanded) "Show less" else "Show more",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .clickable { descriptionExpanded = !descriptionExpanded }
                            )
                        }
                    }
                }
            }

            // Start Reading - the single, prominent primary action Royal Road leads
            // with. Jumps into the first chapter of the whole novel (not just the
            // currently selected arc tab).
            if (chapters.isNotEmpty()) {
                item {
                    Button(
                        onClick = { onChapterSelected(chapters.first()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoStories,
                            contentDescription = null,
                            modifier = Modifier.height(18.dp)
                        )
                        Text("Start Reading", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            item { Divider(modifier = Modifier.padding(vertical = 4.dp)) }

            // Table of contents header, mirroring Royal Road's "X Chapters" count.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.height(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Table of Contents",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Text(
                        "${chapters.size} Chapters",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Arc tabs
            if (tabs.size > 1) {
                item {
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
            }

            // Pagination size controls - kept compact, tucked under the ToC header
            // rather than competing with Start Reading for top-of-page attention.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Per page:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    PAGE_SIZE_OPTIONS.forEach { size ->
                        val selected = pageSize.intValue == size
                        AssistChip(
                            onClick = {
                                pageSize.intValue = size
                                onResizePages(size)
                            },
                            label = { Text("$size", fontSize = 12.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface,
                                labelColor = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            // Chapter list - flat rows with a hairline divider between them, closer to
            // Royal Road's dense chapter table than the previous per-row Card look.
            items(chaptersToShow) { chapter ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChapterSelected(chapter) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${chapter.number ?: ""} ${chapter.title}".trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (chapter.id in overriddenChapterIds) {
                            Text(
                                "Edited",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                    Divider()
                }
            }

            // Page navigation
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
    }
}

@Composable
private fun NovelCoverThumb(
    coverUrl: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(136.dp)
            )
        } else {
            Text("📚", fontSize = 32.sp)
        }
    }
}
