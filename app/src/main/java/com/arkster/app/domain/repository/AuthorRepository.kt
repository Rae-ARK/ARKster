package com.arkster.app.domain.repository

/*
 * STAGE 6 MOVE -- 2026-11-11
 * Added as part of the staged OOP modular-monolith refactor (Repository +
 * Proxy). Full plan: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 * Wraps AuthorDao, the third of the three repositories the refactor doc
 * names explicitly. See NovelRepository's header for why this stays a thin
 * pass-through rather than absorbing LibrarySession's author-reconcile
 * logic (the id-collision/stale-author rules in
 * docs/arkster/foundational/author-and-reader-model.md) - that orchestration
 * still belongs to the scan pass that owns it, not to the repository it
 * reads/writes through.
 */

import com.arkster.app.core.database.AuthorDao
import com.arkster.app.core.database.AuthorEntity

class AuthorRepository(private val authorDao: AuthorDao) {
    suspend fun findById(authorId: String): AuthorEntity? = authorDao.findById(authorId)

    suspend fun all(): List<AuthorEntity> = authorDao.all()

    suspend fun upsert(author: AuthorEntity) = authorDao.upsert(author)

    suspend fun delete(authorId: String) = authorDao.delete(authorId)
}
