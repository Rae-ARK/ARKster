package com.arkster.app.scanner

/*
 * STAGE 4 MOVE -- 2026-11-11
 * Extracted from ScannerImpl.kt (Facade + Chain of Responsibility + Builder
 * decomposition). Full plan:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * This is the Builder named in that doc's design-decisions section, scoped
 * to what actually lives in ScannerImpl: assembling one scan pass's fresh
 * NovelEntity from folder title + an optional metadata.json + the already-
 * resolved authorId, field-by-field, instead of that construction sitting
 * inline in scanRoot's per-novel loop. It is NOT the "existing-vs-scanned-
 * vs-override merge" that same doc paragraph describes - that merge (the
 * long copy() call carrying over pageSize/readingStatus/remote-fetched
 * metadata/etc. from the *previous* DB row) lives in
 * core/data/LibrarySession.kt's scan()/onDiscovered, added in Stage 3, not
 * in ScannerImpl at all. Stage 4 only decomposes ScannerImpl, so that merge
 * is left exactly where Stage 3 put it; see this stage's note in
 * docs/arkster/implementation/oop-modular-monolith-refactor.md for why the
 * doc's own description of where that logic lives needed this correction,
 * the same way Stage 1 corrected architecture.md's "FolderSource: shipped"
 * claim.
 *
 * readLocalMetadata/LocalMetadata are unchanged from ScannerImpl's private
 * versions, just grouped with the builder they exclusively feed.
 */

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.arkster.app.core.database.NovelEntity
import org.json.JSONObject

class NovelEntityBuilder(private val context: Context) {

    data class LocalMetadata(
        val title: String?,
        val author: String?,
        val authorId: String?,
        val description: String?,
        val genres: String?,
        val publishedDate: String?
    )

    // Reads an optional `metadata.json` sitting directly in the novel's folder, e.g.:
    // {
    //   "title": "Summoned By Mistake, I Decided To Learn How To Live",
    //   "author": "Some Author",
    //   "authorId": "rae-ark",
    //   "description": "A short synopsis...",
    //   "genres": ["Fantasy", "Isekai"],
    //   "publishedDate": "2023"
    // }
    // All fields are optional and applied only where present - a missing/unparseable
    // file just means no local metadata, never a scan failure (this is loaded once per
    // novel on every scan, so a bad file simply doesn't override anything that scan).
    // "genres" accepts either a JSON array of strings or a single comma-separated string.
    // "authorId" links this fiction to authors/<authorId>.json - see
    // docs/arkster/foundational/author-and-reader-model.md's "Linking a fiction to an
    // author".
    fun readLocalMetadata(folder: DocumentFile): LocalMetadata? {
        val file = folder.listFiles().firstOrNull {
            it.isFile && it.name?.equals("metadata.json", ignoreCase = true) == true
        } ?: return null
        return try {
            val text = context.contentResolver.openInputStream(file.uri)
                ?.bufferedReader()
                ?.use { it.readText() } ?: return null
            val json = JSONObject(text)
            val genresArray = json.optJSONArray("genres")
            val genres = if (genresArray != null) {
                (0 until genresArray.length()).joinToString(", ") { genresArray.optString(it) }
            } else {
                json.optString("genres").takeIf { it.isNotBlank() }
            }
            LocalMetadata(
                title = json.optString("title").takeIf { it.isNotBlank() },
                author = json.optString("author").takeIf { it.isNotBlank() },
                authorId = json.optString("authorId").takeIf { it.isNotBlank() },
                description = json.optString("description").takeIf { it.isNotBlank() },
                genres = genres,
                publishedDate = json.optString("publishedDate").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            // Malformed metadata.json shouldn't take down the scan for this novel - it
            // just means this novel falls back to folder-name title / no metadata, same
            // as if the file weren't there.
            null
        }
    }

    // Assembles this scan pass's fresh NovelEntity. Every field below is either the
    // folder/metadata.json value for *this* pass or null - carrying over values from
    // whatever's already in the DB (pageSize, readingStatus, a previous remote
    // metadata fetch, ...) is LibrarySession.scan's job, not this builder's; see the
    // file header above for why that split is deliberate, not an oversight.
    fun build(
        id: String,
        folderTitle: String,
        coverUri: String?,
        localMetadata: LocalMetadata?,
        resolvedAuthorId: String?
    ): NovelEntity = NovelEntity(
        id = id,
        title = localMetadata?.title?.takeIf { it.isNotBlank() } ?: folderTitle,
        author = localMetadata?.author,
        authorId = resolvedAuthorId,
        coverUri = coverUri,
        description = localMetadata?.description,
        genres = localMetadata?.genres,
        publishedDate = localMetadata?.publishedDate
    )
}
