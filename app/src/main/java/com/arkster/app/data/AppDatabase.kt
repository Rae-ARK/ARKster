package com.arkster.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NovelEntity::class,
        ArcEntity::class,
        ChapterEntity::class,
        ChapterOverrideEntity::class,
        ScanFingerprintEntity::class
    ],
    version = 2
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun novelDao(): NovelDao
    abstract fun arcDao(): ArcDao
    abstract fun chapterDao(): ChapterDao
    abstract fun chapterOverrideDao(): ChapterOverrideDao
    abstract fun scanFingerprintDao(): ScanFingerprintDao

    companion object {
        // Migration from v1 to v2: add new tables
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add page_size column to novels
                database.execSQL("ALTER TABLE novels ADD COLUMN page_size INTEGER NOT NULL DEFAULT 10")

                // Create arcs table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS arcs (
                        id TEXT PRIMARY KEY,
                        novel_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        cover_uri TEXT,
                        position INTEGER NOT NULL,
                        FOREIGN KEY(novel_id) REFERENCES novels(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // Create chapter_overrides table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chapter_overrides (
                        id TEXT PRIMARY KEY,
                        chapter_id TEXT NOT NULL,
                        title_override TEXT,
                        position_override INTEGER,
                        is_arc_start INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(chapter_id) REFERENCES chapters(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // Create scan_fingerprints table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS scan_fingerprints (
                        id TEXT PRIMARY KEY,
                        novel_id TEXT NOT NULL,
                        folder_uri TEXT NOT NULL,
                        last_modified INTEGER,
                        size INTEGER,
                        scan_version INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(novel_id) REFERENCES novels(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // Add arc_id column to chapters (nullable)
                database.execSQL("ALTER TABLE chapters ADD COLUMN arc_id TEXT")
            }
        }

        fun create(context: Context): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "arkster.db"
        ).addMigrations(MIGRATION_1_2).build()
    }
}
