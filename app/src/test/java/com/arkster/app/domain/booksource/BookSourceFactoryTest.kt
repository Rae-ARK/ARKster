package com.arkster.app.domain.booksource

/*
 * STAGE 5 (this patch) -- OOP modular-monolith refactor
 * Full plan: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * Plain JUnit, no Robolectric - BookSourceFactory.classify() takes a
 * (name, isDirectory) pair rather than a DocumentFile specifically so this
 * is possible, same precedent as ScanReconcileTest.
 */

import org.junit.Assert.assertEquals
import org.junit.Test

class BookSourceFactoryTest {

    @Test
    fun `a directory is always a Folder, regardless of its name`() {
        assertEquals(SourceClassification.Folder, BookSourceFactory.classify("Some Novel", isDirectory = true))
        assertEquals(SourceClassification.Folder, BookSourceFactory.classify("book.epub", isDirectory = true))
    }

    @Test
    fun `an epub file is Unsupported with the EPUB code`() {
        val result = BookSourceFactory.classify("My Book.epub", isDirectory = false)
        assertEquals(SourceClassification.Unsupported(UnsupportedFormatCode.EPUB), result)
    }

    @Test
    fun `epub extension match is case-insensitive`() {
        val result = BookSourceFactory.classify("My Book.EPUB", isDirectory = false)
        assertEquals(SourceClassification.Unsupported(UnsupportedFormatCode.EPUB), result)
    }

    @Test
    fun `a pdf file is Unsupported with the PDF code`() {
        val result = BookSourceFactory.classify("Scanned Volume.pdf", isDirectory = false)
        assertEquals(SourceClassification.Unsupported(UnsupportedFormatCode.PDF), result)
    }

    @Test
    fun `an unrecognized file is Ignored, same as today`() {
        assertEquals(SourceClassification.Ignored, BookSourceFactory.classify("cover.jpg", isDirectory = false))
        assertEquals(SourceClassification.Ignored, BookSourceFactory.classify("metadata.json", isDirectory = false))
        assertEquals(SourceClassification.Ignored, BookSourceFactory.classify("README", isDirectory = false))
    }

    @Test
    fun `a null name is Ignored rather than throwing`() {
        assertEquals(SourceClassification.Ignored, BookSourceFactory.classify(null, isDirectory = false))
    }
}
