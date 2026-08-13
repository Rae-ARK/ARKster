package com.arkster.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkster.app.data.ChapterEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    chapter: ChapterEntity,
    content: String,
    // Called with a 0f..1f scroll fraction so the caller can persist reading progress.
    onBack: (Float) -> Unit
) {
    val fontSize = remember { mutableFloatStateOf(16f) }
    val scrollState = rememberScrollState()

    fun currentProgress(): Float {
        val max = scrollState.maxValue
        return if (max <= 0) 1f else (scrollState.value.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text(chapter.title) },
            navigationIcon = {
                IconButton(onClick = { onBack(currentProgress()) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = content,
                style = TextStyle(fontSize = fontSize.value.sp),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
