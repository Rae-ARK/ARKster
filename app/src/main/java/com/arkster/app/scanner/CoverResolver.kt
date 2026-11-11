package com.arkster.app.scanner

/*
 * STAGE 4 MOVE -- 2026-11-11
 * Extracted from ScannerImpl.kt (Facade + Chain of Responsibility + Builder
 * decomposition). Full plan:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * Behavior unchanged from ScannerImpl's private findCoverUri().
 *
 * STAGE 6 MOVE -- 2026-11-11
 * Extracted the CoverSource interface so this class has a seam a Proxy can
 * sit in front of - see CachingCoverResolverProxy.kt, the real/proxy pairing
 * the refactor doc's design-decisions section names for this exact case
 * ("Proxy — cache-on-first-access resolution ... cover/avatar URIs").
 * CoverResolver itself is unchanged other than implementing this interface.
 */

import androidx.documentfile.provider.DocumentFile

interface CoverSource {
    fun findCoverUri(folder: DocumentFile): String?
}

/**
 * Resolves a novel or arc folder's cover image. See bugs.md Bug 3a: this used
 * to require the filename to be *exactly* "cover.<ext>", so per-arc covers
 * named e.g. "Arc1_Cover.jpg" or "cover (1).png" were silently skipped. Now:
 * prefer an exact "cover.<ext>" match if one exists (keeps today's convention
 * as the unambiguous default), otherwise fall back to any image in the folder
 * whose name contains "cover" as a standalone word.
 *
 * This is the Proxy pattern's Real Subject (see CachingCoverResolverProxy) -
 * every call does a real SAF `listFiles()` + regex pass, no caching of its
 * own.
 */
class CoverResolver : CoverSource {

    override fun findCoverUri(folder: DocumentFile): String? {
        val images = folder.listFiles().filter {
            it.isFile && it.name?.matches(Regex("(?i).*\\.(jpg|jpeg|png|webp)$")) == true
        }
        images.firstOrNull { it.name?.matches(Regex("(?i)cover\\.(jpg|jpeg|png|webp)$")) == true }
            ?.let { return it.uri.toString() }
        return images.firstOrNull { image ->
            val base = image.name?.substringBeforeLast('.') ?: return@firstOrNull false
            Regex("(?i)(^|[^a-z0-9])cover([^a-z0-9]|$)").containsMatchIn(base)
        }?.uri?.toString()
    }
}
