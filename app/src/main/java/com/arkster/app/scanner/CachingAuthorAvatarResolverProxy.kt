package com.arkster.app.scanner

/*
 * STAGE 6 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor (Repository +
 * Proxy). Full plan: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * Same Real Subject/Proxy shape and reasoning as CachingCoverResolverProxy -
 * see that file's header. Cache key combines the authors/ folder's URI, the
 * author id, and the explicit avatar filename (if any) rather than just the
 * folder, since one authors/ folder resolves a different avatar per author
 * id inside it - unlike CoverResolver, where one folder has exactly one
 * cover.
 */

import androidx.documentfile.provider.DocumentFile
import java.util.concurrent.ConcurrentHashMap

class CachingAuthorAvatarResolverProxy(
    private val real: AvatarSource = AuthorAvatarResolver()
) : AvatarSource {

    private class CachedResult(val uri: String?)

    private val cache = ConcurrentHashMap<String, CachedResult>()

    override fun resolveAvatarUri(
        authorsFolder: DocumentFile,
        id: String,
        name: String,
        explicitAvatarFilename: String?
    ): String? {
        val key = "${authorsFolder.uri}:$id:${explicitAvatarFilename.orEmpty()}"
        return cache.computeIfAbsent(key) {
            CachedResult(real.resolveAvatarUri(authorsFolder, id, name, explicitAvatarFilename))
        }.uri
    }
}
