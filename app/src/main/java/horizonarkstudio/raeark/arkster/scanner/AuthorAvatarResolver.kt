package horizonarkstudio.raeark.arkster.scanner

/*
 * STAGE 6 MOVE -- 2026-11-11
 * Extracted from AuthorScanner.kt's private resolveAuthorAvatarUri/
 * findAuthorAvatarUri as part of the staged OOP modular-monolith refactor
 * (Repository + Proxy). Full plan:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * Behavior unchanged from AuthorScanner's two private methods - only moved
 * out and merged into the one resolveAvatarUri entry point so this class can
 * sit behind the same Real Subject/Proxy shape as CoverResolver/
 * CachingCoverResolverProxy (see that file's header for the GoF reference
 * and why). AuthorScanner now calls an injected AvatarSource instead of
 * owning this resolution logic itself.
 */

import androidx.documentfile.provider.DocumentFile

interface AvatarSource {
    // explicitAvatarFilename is author.json's own "avatar" field, if it set one -
    // resolution tries that first, then falls back to the authors/<id-or-name>.*
    // convention, same precedence AuthorScanner.scanAuthorsFolder relied on before
    // this extraction.
    fun resolveAvatarUri(
        authorsFolder: DocumentFile,
        id: String,
        name: String,
        explicitAvatarFilename: String?
    ): String?
}

/**
 * Real Subject (see CachingAuthorAvatarResolverProxy) - every call does a real SAF
 * `listFiles()` + regex pass, no caching of its own.
 */
class AuthorAvatarResolver : AvatarSource {

    override fun resolveAvatarUri(
        authorsFolder: DocumentFile,
        id: String,
        name: String,
        explicitAvatarFilename: String?
    ): String? {
        explicitAvatarFilename?.let { filename ->
            resolveExplicit(authorsFolder, filename)?.let { return it }
        }
        return findByConvention(authorsFolder, id, name)
    }

    // Resolves author.json's "avatar" filename against the authors/ folder, the same
    // way CoverResolver.findCoverUri resolves cover.* against a novel folder.
    private fun resolveExplicit(authorsFolder: DocumentFile, avatarFilename: String): String? =
        authorsFolder.listFiles().firstOrNull {
            it.isFile && it.name?.equals(avatarFilename, ignoreCase = true) == true
        }?.uri?.toString()

    // Convention-based fallback for when author.json doesn't specify an "avatar" (or
    // has none at all): a same-folder image named after the author, e.g.
    // "authors/<name>.png" or "authors/<id>.png", the same pattern as a novel/arc's
    // cover.*. Tries the id first (usually already filename-safe, lowercase) then the
    // display name, and accepts jpg/jpeg/png/webp.
    private fun findByConvention(authorsFolder: DocumentFile, id: String, name: String): String? {
        val images = authorsFolder.listFiles().filter {
            it.isFile && it.name?.matches(Regex("(?i).*\\.(jpg|jpeg|png|webp)$")) == true
        }
        fun matching(key: String): String? = images.firstOrNull {
            it.name?.substringBeforeLast('.')?.equals(key, ignoreCase = true) == true
        }?.uri?.toString()
        return matching(id) ?: matching(name)
    }
}
