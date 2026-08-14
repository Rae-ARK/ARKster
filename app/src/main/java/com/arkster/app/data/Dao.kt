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

    @Query("SELECT * FROM chapters WHERE novel_id = :novelId ORDER BY number, title")
    suspend fun forNovel(novelId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE arc_id = :arcId ORDER BY number, title")
    suspend fun forArc(arcId: String): List<ChapterEntity>

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
