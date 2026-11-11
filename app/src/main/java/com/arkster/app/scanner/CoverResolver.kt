package com.arkster.app.scanner

/*
 * STAGE 4 MOVE -- 2026-11-11
 * Extracted from ScannerImpl.kt (Facade + Chain of Responsibility + Builder
 * decomposition). Full plan:
 * docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * Behavior unchanged from ScannerImpl's private findCoverUri().
 */

import androidx.documentfile.provider.DocumentFile

/**
 * Resolves a novel or arc folder's cover image. See bugs.md Bug 3a: this used
 * to require the filename to be *exactly* "cover.<ext>", so per-arc covers
 * named e.g. "Arc1_Cover.jpg" or "cover (1).png" were silently skipped. Now:
 * prefer an exact "cover.<ext>" match if one exists (keeps today's convention
 * as the unambiguous default), otherwise fall back to any image in the folder
 * whose name contains "cover" as a standalone word.
 */
class CoverResolver {

    fun findCoverUri(folder: DocumentFile): String? {
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
