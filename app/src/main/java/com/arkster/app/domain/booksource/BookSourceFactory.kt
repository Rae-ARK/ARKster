package com.arkster.app.domain.booksource

/*
 * STAGE 5 (this patch) -- OOP modular-monolith refactor
 * Full plan: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * The one-interface-dispatches-by-file decision point: given a scan root's
 * child entry, decide whether it's a folder (handled by FolderSource/
 * ScannerImpl's existing per-novel pipeline), a recognized-but-unsupported
 * single-file format (EPUB, PDF - no BookSource implementation exists for
 * either yet), or something the scanner has never cared about (a stray
 * README, a leftover .jpg at the root, etc.) and silently ignores, same as
 * today.
 *
 * Deliberately takes a plain (name, isDirectory) pair rather than a
 * DocumentFile: this keeps classify() a pure function with no Android
 * dependency, testable with plain JUnit the same way ScanReconcileTest
 * already covers ScanReconcile.decideStaleNovels - no Robolectric needed.
 * ScannerImpl is the only caller, and it already has the DocumentFile to
 * pull child.name/child.isDirectory from.
 */
sealed class SourceClassification {
    object Folder : SourceClassification()
    data class Unsupported(val code: UnsupportedFormatCode) : SourceClassification()
    object Ignored : SourceClassification()
}

object BookSourceFactory {

    fun classify(name: String?, isDirectory: Boolean): SourceClassification {
        if (isDirectory) return SourceClassification.Folder
        val fileName = name ?: return SourceClassification.Ignored
        return when {
            fileName.endsWith(".epub", ignoreCase = true) ->
                SourceClassification.Unsupported(UnsupportedFormatCode.EPUB)
            fileName.endsWith(".pdf", ignoreCase = true) ->
                SourceClassification.Unsupported(UnsupportedFormatCode.PDF)
            else -> SourceClassification.Ignored
        }
    }
}
