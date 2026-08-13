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
}

@Dao
interface ChapterOverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: ChapterOverrideEntity)

    @Query("SELECT * FROM chapter_overrides WHERE chapter_id = :chapterId")
    suspend fun forChapter(chapterId: String): ChapterOverrideEntity?

    @Query("DELETE FROM chapter_overrides WHERE chapter_id = :chapterId")
    suspend fun delete(chapterId: String)
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
