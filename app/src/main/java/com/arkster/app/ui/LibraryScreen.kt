package com.arkster.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    refcentlyRead: List<NovelEntity> = emptyList(),
    onNovelSelected: (NovelEntity) -> Unit = {},
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

        // Search bar
        TextField(
            value = searchQuery.value,
            onValueChange = { searchQuery.value = it },
            label = { Text("Search") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )

        LazyColumn {
            // Continue Reading section
            if (refcentlyRead.isNotEmpty()) {
                item {
                    Text("Continue Reading", modifier = Modifier.padding(12.dp))
                    LazyRow(modifier = Modifier.padding(horizontal = 8.dp)) {
                        items(refcentlyRead) { novel ->
                            Card(
                                modifier = Modifier
                                    .clickable { onNovelSelected(novel) }
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
                        .clickable { onNovelSelected(novel) }
                        .padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(novel.title)
                        if (!novel.author.isNullOrBlank()) {
                            Text(novel.author!!)
                        }
                    }
                }
            }
        }
    }
}
