package com.arkster.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ChapterContent {
    data class Text(val body: String) : ChapterContent()
}

interface ChapterContentRepository {
    suspend fun getTextContent(sourcePath: String): ChapterContent.Text
}

class TextChapterContentRepository(private val context: Context) : ChapterContentRepository {
    override suspend fun getTextContent(sourcePath: String): ChapterContent.Text = withContext(Dispatchers.IO) {
        val uri = Uri.parse(sourcePath)
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        } catch (e: Exception) {
            ByteArray(0)
        }
        
        // Try UTF-8 first, fallback to ISO-8859-1
        val text = try {
            bytes.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            bytes.toString(Charsets.ISO_8859_1)
        }
        
        ChapterContent.Text(body = text)
    }
}
