package com.arkster.app.scanner

/*
 * STAGE 4 MOVE -- 2026-11-11
 * Extracted from ScannerImpl.kt (Facade + Chain of Responsibility + Builder
 * decomposition). Full plan:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * Behavior unchanged from ScannerImpl's private findAuthorsFolder,
 * scanAuthorsFolder, readAuthorMetadata, resolveAuthorAvatarUri, and
 * findAuthorAvatarUri. resolveAuthorId is new in name only - it's the same
 * authorId-resolution logic that used to be inlined directly in
 * ScannerImpl.scanRoot's per-novel loop, pulled in here because it's
 * resolution against *this* class's own authorsById/authorIdByNormalizedName
 * output, not novel-construction logic - see
 * docs/arkster/foundational/author-and-reader-model.md's "Linking a fiction
 * to an author" for the resolution order this implements.
 *
 * STAGE 6 MOVE -- 2026-11-11
 * resolveAuthorAvatarUri/findAuthorAvatarUri moved out to
 * AuthorAvatarResolver.kt (Real Subject) + CachingAuthorAvatarResolverProxy.kt
 * (Proxy) - see those files' headers. This class now takes an AvatarSource
 * collaborator instead of owning avatar-resolution logic directly, same
 * "inject the collaborator, default to the caching Proxy" shape ScannerImpl
 * uses for CoverSource.
 */

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.arkster.app.core.database.AuthorEntity
import org.json.JSONObject

class AuthorScanner(
    private val context: Context,
    private val avatarSource: AvatarSource = CachingAuthorAvatarResolverProxy()
) {

    private data class AuthorMetadata(
        val id: String?,
        val name: String?,
        val avatar: String?,
        val bio: String?,
        val joined: String?,
        val location: String?,
        val gender: String?,
        val linksJson: String?,
        val followers: Int?,
        val favorites: Int?,
        val reviewsReceived: Int?,
        val ratingsReceived: Int?
    )

    fun findAuthorsFolder(root: DocumentFile): DocumentFile? =
        root.listFiles().firstOrNull { it.isDirectory && it.name?.equals("authors", ignoreCase = true) == true }

    // Reads and parses one authors/<id>.json file. See "authors/<id>.json schema" in
    // docs/arkster/foundational/author-and-reader-model.md for the full field list.
    // All fields optional; malformed JSON just means this file contributes nothing
    // (fail soft, same philosophy as NovelEntityBuilder.readLocalMetadata) - it's up
    // to the caller (scanAuthorsFolder) to still fall back to the filename for the
    // id/name in that case.
    private fun readAuthorMetadata(file: DocumentFile): AuthorMetadata? {
        return try {
            val text = context.contentResolver.openInputStream(file.uri)
                ?.bufferedReader()
                ?.use { it.readText() } ?: return null
            val json = JSONObject(text)
            val links = json.optJSONObject("links")
            val stats = json.optJSONObject("stats")
            AuthorMetadata(
                id = json.optString("id").takeIf { it.isNotBlank() },
                name = json.optString("name").takeIf { it.isNotBlank() },
                avatar = json.optString("avatar").takeIf { it.isNotBlank() },
                bio = json.optString("bio").takeIf { it.isNotBlank() },
                joined = json.optString("joined").takeIf { it.isNotBlank() },
                location = json.optString("location").takeIf { it.isNotBlank() },
                gender = json.optString("gender").takeIf { it.isNotBlank() },
                linksJson = links?.toString(),
                followers = stats?.takeIf { it.has("followers") }?.optInt("followers"),
                favorites = stats?.takeIf { it.has("favorites") }?.optInt("favorites"),
                reviewsReceived = stats?.takeIf { it.has("reviews_received") }?.optInt("reviews_received"),
                ratingsReceived = stats?.takeIf { it.has("ratings_received") }?.optInt("ratings_received")
            )
        } catch (e: Exception) {
            null
        }
    }

    // Parses every authors/<id>.json file in the given authors/ folder into an
    // AuthorEntity. The filename (minus extension) is the id unless the json's own
    // "id" field overrides it. Ids are never hand-validated for global uniqueness -
    // filenames are already unique within a folder by construction, so the only
    // possible collision is two files whose "id" override happens to match; when that
    // happens the file seen first (folder listing order) wins and the later one is
    // skipped with a soft warning via onProgress, same fail-soft philosophy as every
    // other malformed/ambiguous input this scanner already tolerates - never a thrown
    // error, never an aborted scan.
    suspend fun scanAuthorsFolder(
        authorsFolder: DocumentFile,
        onProgress: suspend (message: String) -> Unit
    ): List<AuthorEntity> {
        val files = authorsFolder.listFiles().filter {
            it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true
        }
        val result = mutableListOf<AuthorEntity>()
        val seenIds = mutableSetOf<String>()
        for (file in files) {
            val filenameId = file.name?.let {
                it.substring(0, it.length - ".json".length).trim().lowercase()
            }
            val parsed = readAuthorMetadata(file)
            val id = parsed?.id?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: filenameId
            if (id.isNullOrBlank()) continue
            if (!seenIds.add(id)) {
                // Collision on an explicit "id" override (or, theoretically, two files
                // differing only by case) - keep the first one seen, skip this one, and
                // surface it the same soft-warning way every other scan issue already is.
                onProgress("Skipped ${file.name}: author id \"$id\" is already used by another file in authors/")
                continue
            }
            val name = parsed?.name?.takeIf { it.isNotBlank() }
                ?: file.name?.let { it.substring(0, it.length - 5) }
                ?: id
            result.add(
                AuthorEntity(
                    id = id,
                    name = name,
                    // Explicit "avatar" field in author.json wins if it resolves;
                    // otherwise fall back to a same-folder authors/<id-or-name>.png
                    // (jpg/webp also accepted) - see AuthorAvatarResolver.
                    avatarUri = avatarSource.resolveAvatarUri(
                        authorsFolder = authorsFolder,
                        id = id,
                        name = name,
                        explicitAvatarFilename = parsed?.avatar
                    ),
                    bio = parsed?.bio,
                    joined = parsed?.joined,
                    location = parsed?.location,
                    gender = parsed?.gender,
                    linksJson = parsed?.linksJson,
                    followers = parsed?.followers,
                    favorites = parsed?.favorites,
                    reviewsReceived = parsed?.reviewsReceived,
                    ratingsReceived = parsed?.ratingsReceived,
                    sourcePath = file.uri.toString()
                )
            )
        }
        return result
    }

    // Resolution order, per fiction, on every scan (see "Linking a fiction to an
    // author" in docs/arkster/foundational/author-and-reader-model.md):
    // 1. metadata.json's authorId, if it matches an id found in authors/ during this
    //    same scan.
    // 2. Otherwise, metadata.json's free-text author field, matched
    //    case-insensitively against authors/*.json name values.
    // 3. Otherwise, no author link.
    fun resolveAuthorId(
        localAuthorId: String?,
        localAuthorName: String?,
        authorsById: Map<String, AuthorEntity>,
        authorIdByNormalizedName: Map<String, String>
    ): String? =
        localAuthorId
            ?.trim()?.lowercase()
            ?.takeIf { authorsById.containsKey(it) }
            ?: localAuthorName?.let { authorIdByNormalizedName[it.trim().lowercase()] }
}
