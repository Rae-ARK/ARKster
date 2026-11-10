package com.arkster.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.CodingErrorAction

sealed class ChapterContent {
    data class Text(val body: String) : ChapterContent()
}

// Outcome of loading one chapter's text. A read failure (lost SAF permission, provider
// hiccup, file moved or deleted underneath us) used to be swallowed into an empty
// ByteArray, which rendered as a silent chapter indistinguishable from a genuinely empty
// file - and reading progress was then saved against it. A sealed result lets the caller
// tell the two apart and offer a real retry. CancellationException is still rethrown,
// never turned into Failed: cancelling a load is not a read failure.
sealed class ChapterLoadResult {
    data class Ok(val content: ChapterContent.Text) : ChapterLoadResult()
    data class Failed(val message: String, val cause: Throwable? = null) : ChapterLoadResult()
}

interface ChapterContentRepository {
    suspend fun getTextContent(sourcePath: String): ChapterLoadResult
}

class TextChapterContentRepository(private val context: Context) : ChapterContentRepository {
    override suspend fun getTextContent(sourcePath: String): ChapterLoadResult = withContext(Dispatchers.IO) {
        val uri = Uri.parse(sourcePath)
        val bytes = try {
            // openInputStream() returns null when the provider is unavailable (e.g. it
            // crashed). That is a failed read, not an empty file - a genuinely empty file
            // still yields a stream, just with zero bytes.
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext ChapterLoadResult.Failed("No data returned for $sourcePath")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext ChapterLoadResult.Failed("Couldn't read chapter content from $sourcePath", e)
        }

        // Try UTF-8 first, fallback to ISO-8859-1. Note: bytes.toString(Charsets.UTF_8)
        // never throws on malformed input (it silently substitutes replacement chars),
        // so we need a strict decoder to actually detect invalid UTF-8 and trigger the
        // fallback instead of rendering mojibake.
        val text = try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            bytes.toString(Charsets.ISO_8859_1)
        }

        ChapterLoadResult.Ok(ChapterContent.Text(body = text))
    }
}
