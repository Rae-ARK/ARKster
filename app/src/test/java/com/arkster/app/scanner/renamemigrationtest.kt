package com.arkster.app.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RenameMigrationTest {

    @Test
    fun `identical filename set pairs the renamed folder with its old id`() {
        val matches = detectRenameMatches(
            candidateStaleIds = setOf("old"),
            candidateNewIds = setOf("new"),
            chapterFilenamesByNovelId = mapOf(
                "old" to setOf("chapter1.txt", "chapter2.txt"),
                "new" to setOf("chapter1.txt", "chapter2.txt")
            )
        )
        assertEquals(listOf(RenameMatch("old", "new")), matches)
    }

    @Test
    fun `different filename sets never match`() {
        val matches = detectRenameMatches(
            candidateStaleIds = setOf("old"),
            candidateNewIds = setOf("new"),
            chapterFilenamesByNovelId = mapOf(
                "old" to setOf("chapter1.txt", "chapter2.txt"),
                "new" to setOf("chapter1.txt", "chapter3.txt")
            )
        )
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `a novel with no chapters is never matched`() {
        val matches = detectRenameMatches(
            candidateStaleIds = setOf("old"),
            candidateNewIds = setOf("new"),
            chapterFilenamesByNovelId = mapOf(
                "old" to emptySet(),
                "new" to emptySet()
            )
        )
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `an identical filename set shared by two stale novels matches neither`() {
        val matches = detectRenameMatches(
            candidateStaleIds = setOf("old1", "old2"),
            candidateNewIds = setOf("new"),
            chapterFilenamesByNovelId = mapOf(
                "old1" to setOf("chapter1.txt"),
                "old2" to setOf("chapter1.txt"),
                "new" to setOf("chapter1.txt")
            )
        )
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `an identical filename set shared by two new novels matches neither`() {
        val matches = detectRenameMatches(
            candidateStaleIds = setOf("old"),
            candidateNewIds = setOf("new1", "new2"),
            chapterFilenamesByNovelId = mapOf(
                "old" to setOf("chapter1.txt"),
                "new1" to setOf("chapter1.txt"),
                "new2" to setOf("chapter1.txt")
            )
        )
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `several unambiguous renames in one pass all match`() {
        val matches = detectRenameMatches(
            candidateStaleIds = setOf("oldA", "oldB"),
            candidateNewIds = setOf("newA", "newB"),
            chapterFilenamesByNovelId = mapOf(
                "oldA" to setOf("a1.txt", "a2.txt"),
                "oldB" to setOf("b1.txt"),
                "newA" to setOf("a1.txt", "a2.txt"),
                "newB" to setOf("b1.txt")
            )
        )
        assertEquals(
            setOf(RenameMatch("oldA", "newA"), RenameMatch("oldB", "newB")),
            matches.toSet()
        )
    }

    @Test
    fun `chapterFilenameFrom extracts the leaf filename from a SAF document URI`() {
        val sourcePath =
            "content://com.android.externalstorage.documents/tree/primary%3AStories/document/primary%3AStories%2FNovel%2FChapter%201.txt"
        assertEquals("Chapter 1.txt", chapterFilenameFrom(sourcePath))
    }

    @Test
    fun `chapterFilenameFrom round-trips a filename containing a literal plus sign`() {
        val sourcePath =
            "content://com.android.externalstorage.documents/tree/primary%3AStories/document/primary%3AStories%2FNovel%2FChapter%2B1.txt"
        assertEquals("Chapter+1.txt", chapterFilenameFrom(sourcePath))
    }

    @Test
    fun `chapterFilenameFrom falls back to the last path segment with no document marker`() {
        assertEquals("chapter1.txt", chapterFilenameFrom("plain/path/chapter1.txt"))
    }
}
