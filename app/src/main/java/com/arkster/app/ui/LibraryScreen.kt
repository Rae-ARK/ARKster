package com.arkster.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkster.app.data.NovelEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    novels: List<NovelEntity>,
    recentlyRead: List<NovelEntity> = emptyList(),
    inProgressNovels: List<NovelEntity> = emptyList(),
    scanProgress: Pair<Int, Int>? = null,
    scanMessage: String = "",
    onNovelSelected: (NovelEntity) -> Unit = {},
    onContinueReading: (NovelEntity) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val searchQuery = remember { mutableStateOf("") }
    
    val filteredNovels = novels.filter {
        it.title.contains(searchQuery.value, ignoreCase = true) ||
        (it.author?.contains(searchQuery.value, ignoreCase = true) ?: false)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("Library") },
            actions = {
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        )

        // Scan progress indicator
        if (scanProgress != null) {
            val (current, total) = scanProgress
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)) {
                LinearProgressIndicator(
                    progress = { if (total > 0) current.toFloat() / total.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "$scanMessage ($current/$total)",
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Search bar
        TextField(
            value = searchQuery.value,
            onValueChange = { searchQuery.value = it },
            label = { Text("Search") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            enabled = scanProgress == null
        )

        LazyColumn {
            // In Progress section
            if (inProgressNovels.isNotEmpty()) {
                item {
                    Text("In Progress", modifier = Modifier.padding(12.dp))
                }
                items(inProgressNovels) { novel ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(novel.title)
                                if (!novel.author.isNullOrBlank()) {
                                    Text(novel.author!!)
                                }
                            }
                            Button(onClick = { onContinueReading(novel) }, enabled = scanProgress == null) {
                                Text("Continue")
                            }
                        }
                    }
                }
            }

            // Continue Reading section (most recently read)
            if (recentlyRead.isNotEmpty()) {
                item {
                    Text("Continue Reading", modifier = Modifier.padding(12.dp))
                    LazyRow(modifier = Modifier.padding(horizontal = 8.dp)) {
                        items(recentlyRead) { novel ->
                            Card(
                                modifier = Modifier
                                    .clickable(enabled = scanProgress == null) { onNovelSelected(novel) }
                                    .padding(4.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(novel.title)
                                }
                            }
                        }
                    }
                }
            }

            // All novels
            item {
                Text("All Novels", modifier = Modifier.padding(12.dp))
            }

            items(filteredNovels) { novel ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = scanProgress == null) { onNovelSelected(novel) }
                        .padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(novel.title)
                        if (!novel.author.isNullOrBlank()) {
                            Text(novel.author!!)
                        }
                        // Show reading status
                        val statusText = when (novel.readingStatus) {
                            "COMPLETED" -> "Completed"
                            "IN_PROGRESS" -> "Reading..."
                            else -> ""
                        }
                        if (statusText.isNotEmpty()) {
                            Text(statusText, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
