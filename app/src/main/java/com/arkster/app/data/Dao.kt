package com.arkster.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NovelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(novel: NovelEntity)

    @Query("SELECT * FROM novels ORDER BY title")
    suspend fun all(): List<NovelEntity>

    @Query("SELECT * FROM novels WHERE id = :novelId")
    suspend fun findById(novelId: String): NovelEntity?

    @Query("UPDATE novels SET page_size = :pageSize WHERE id = :novelId")
    suspend fun updatePageSize(novelId: String, pageSize: Int)

    @Query("UPDATE novels SET reading_status = :status WHERE id = :novelId")
    suspend fun updateReadingStatus(novelId: String, status: String)

    @Query("SELECT * FROM novels WHERE reading_status = :status ORDER BY title")
    suspend fun byStatus(status: String): List<NovelEntity>

    // Backs the Stage 2 author page's fiction list.
    @Query("SELECT * FROM novels WHERE author_id = :authorId ORDER BY title")
    suspend fun byAuthor(authorId: String): List<NovelEntity>

    // Writes a confirmed metadata match (see NovelMetadataProvider) onto an existing
    // novel row. A plain UPDATE rather than upsert, since we're patching fields on a
    // row that must already exist - upserting a partial entity here would null out
    // every column not listed.
    @Query("""
        UPDATE novels SET
            description = :description,
            genres = :genres,
            remote_cover_url = :remoteCoverUrl,
            published_date = :publishedDate,
            external_source_url = :externalSourceUrl,
            metadata_fetched_at = :fetchedAt
        WHERE id = :novelId
    """)
    suspend fun updateMetadata(
        novelId: String,
        description: String?,
        genres: String?,
        remoteCoverUrl: String?,
        publishedDate: String?,
        externalSourceUrl: String?,
        fetchedAt: Long
    )
}

@Dao
interface AuthorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(author: AuthorEntity)

    @Query("SELECT * FROM authors ORDER BY name")
    suspend fun all(): List<AuthorEntity>

    @Query("SELECT * FROM authors WHERE id = :authorId")
    suspend fun findById(authorId: String): AuthorEntity?

    @Query("DELETE FROM authors WHERE id = :authorId")
    suspend fun delete(authorId: String)
}

@Dao
interface ArcDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(arc: ArcEntity)

    @Query("SELECT * FROM arcs WHERE novel_id = :novelId ORDER BY position")
    suspend fun forNovel(novelId: String): List<ArcEntity>

    @Query("DELETE FROM arcs WHERE novel_id = :novelId")
    suspend fun deleteForNovel(novelId: String)

    @Query("DELETE FROM arcs WHERE id = :arcId")
    suspend fun delete(arcId: String)
}

@Dao
interface ChapterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chapter: ChapterEntity)

    // sort_tier first so bonus (1) and closing (2) content always lands after every
    // regular chapter (0) in the same folder, regardless of number/title - see
    // bugs.md Bug 2. number/title remain the tie-break within a tier, same as before.
    @Query("SELECT * FROM chapters WHERE novel_id = :novelId ORDER BY sort_tier, number, title")
    suspend fun forNovel(novelId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE arc_id = :arcId ORDER BY sort_tier, number, title")
    suspend fun forArc(arcId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun findById(chapterId: String): ChapterEntity?

    @Query("DELETE FROM chapters WHERE novel_id = :novelId")
    suspend fun deleteForNovel(novelId: String)

    @Query("DELETE FROM chapters WHERE id = :chapterId")
    suspend fun delete(chapterId: String)
}

@Dao
interface ChapterOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: ChapterOverrideEntity)

    @Query("SELECT * FROM chapter_overrides WHERE chapter_id = :chapterId")
    suspend fun forChapter(chapterId: String): ChapterOverrideEntity?

    // Joins through chapters so we can fetch every override for a novel in one query,
    // used to apply overrides on top of the scanned chapter list at read time.
    @Query("""
        SELECT chapter_overrides.* FROM chapter_overrides
        INNER JOIN chapters ON chapter_overrides.chapter_id = chapters.id
        WHERE chapters.novel_id = :novelId
    """)
    suspend fun forNovel(novelId: String): List<ChapterOverrideEntity>

    @Query("DELETE FROM chapter_overrides WHERE chapter_id = :chapterId")
    suspend fun delete(chapterId: String)
}

@Dao
interface ReadingProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE novel_id = :novelId")
    suspend fun forNovel(novelId: String): ReadingProgressEntity?

    // Backs the "Continue Reading" row: most recently read novels first.
    @Query("SELECT novel_id FROM reading_progress ORDER BY updated_at DESC LIMIT :limit")
    suspend fun recentNovelIds(limit: Int = 10): List<String>
}

@Dao
interface ScanFingerprintDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fingerprint: ScanFingerprintEntity)

    @Query("SELECT * FROM scan_fingerprints WHERE novel_id = :novelId")
    suspend fun forNovel(novelId: String): ScanFingerprintEntity?

    @Query("DELETE FROM scan_fingerprints WHERE novel_id = :novelId")
    suspend fun delete(novelId: String)
}
