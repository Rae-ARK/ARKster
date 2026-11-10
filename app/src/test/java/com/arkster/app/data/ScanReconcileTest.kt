package com.arkster.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanReconcileTest {

    @Test
    fun `nothing missing means nothing deleted or pending`() {
        val d = decideStaleNovels(true, listOf("a", "b"), setOf("a", "b"), emptySet())
        assertEquals(emptySet<String>(), d.deleteNow)
        assertEquals(emptySet<String>(), d.pendingNext)
        assertFalse(d.skipped)
    }

    @Test
    fun `first miss is remembered, not deleted`() {
        val d = decideStaleNovels(true, listOf("a", "b"), setOf("a"), emptySet())
        assertEquals(emptySet<String>(), d.deleteNow)
        assertEquals(setOf("b"), d.pendingNext)
    }

    @Test
    fun `second consecutive miss deletes`() {
        val d = decideStaleNovels(true, listOf("a", "b"), setOf("a"), setOf("b"))
        assertEquals(setOf("b"), d.deleteNow)
        assertEquals(emptySet<String>(), d.pendingNext)
    }

    @Test
    fun `a novel rediscovered after one miss is cleared`() {
        val d = decideStaleNovels(true, listOf("a", "b"), setOf("a", "b"), setOf("b"))
        assertEquals(emptySet<String>(), d.deleteNow)
        assertEquals(emptySet<String>(), d.pendingNext)
    }

    @Test
    fun `empty listing with a non-empty database skips cleanup and keeps pending as is`() {
        val d = decideStaleNovels(false, listOf("a", "b"), emptySet(), setOf("b"))
        assertTrue(d.skipped)
        assertEquals(emptySet<String>(), d.deleteNow)
        assertEquals(setOf("b"), d.pendingNext)
    }

    @Test
    fun `empty listing with an empty database is a normal pass`() {
        val d = decideStaleNovels(false, emptyList(), emptySet(), emptySet())
        assertFalse(d.skipped)
        assertEquals(emptySet<String>(), d.deleteNow)
    }

    @Test
    fun `a novel that was seen (even if its later processing failed) is never stale`() {
        // Mirrors ScannerImpl.onNovelFolderSeen: the id is recorded before any I/O that
        // can throw, so a failure downstream cannot make the novel look deleted.
        val d = decideStaleNovels(true, listOf("a"), setOf("a"), setOf("a"))
        assertEquals(emptySet<String>(), d.deleteNow)
    }
}
