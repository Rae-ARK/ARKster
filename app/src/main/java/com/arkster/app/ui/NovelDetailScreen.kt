package com.arkster.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkster.app.data.ArcEntity
import com.arkster.app.data.ChapterEntity
import com.arkster.app.data.NovelEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelDetailScreen(
    novel: NovelEntity,
    chapters: List<ChapterEntity>,
    arcs: List<ArcEntity>,
    onBack: () -> Unit,
    onChapterSelected: (ChapterEntity) -> Unit,
    onResizePages: (Int) -> Unit = {},
    onEditClick: () -> Unit = {}
) {
    val selectedTabIndex = remember { mutableIntStateOf(0) }
    val tabs = listOf("All Chapters") + arcs.map { it.name }

    val chaptersToShow = when (selectedTabIndex.intValue) {
        0 -> chapters
        else -> {
            val arcId = arcs.getOrNull(selectedTabIndex.intValue - 1)?.id
            chapters.filter { it.arcId == arcId }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text(novel.title) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
            }
        )

        // Pagination controls
        Row(modifier = Modifier.padding(8.dp)) {
            listOf(10, 20, 50, 100).forEach { size ->
                Button(
                    onClick = { onResizePages(size) },
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

        // Chapter list
        LazyColumn {
            items(chaptersToShow) { chapter ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChapterSelected(chapter) }
                        .padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${chapter.number ?: ""} ${chapter.title}".trim())
                    }
                }
            }
        }
    }
}
