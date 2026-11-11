package com.arkster.app.core.database

/*
 * STAGE 1 MOVE -- 2026-11-11
 * Relocated here (package rename only, no logic touched) as part of the
 * staged OOP modular-monolith refactor. Full plan, rationale, and what
 * each later stage does: docs/arkster/implementation/oop-modular-monolith-refactor.md
 *
 */

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NovelEntity::class,
        AuthorEntity::class,
        ArcEntity::class,
        ChapterEntity::class,
        ChapterOverrideEntity::class,
        ScanFingerprintEntity::class,
        ReadingProgressEntity::class
    ],
    version = 8
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun novelDao(): NovelDao
    abstract fun authorDao(): AuthorDao
    abstract fun arcDao(): ArcDao
    abstract fun chapterDao(): ChapterDao
    abstract fun chapterOverrideDao(): ChapterOverrideDao
    abstract fun scanFingerprintDao(): ScanFingerprintDao
    abstract fun readingProgressDao(): ReadingProgressDao

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

        // Migration from v2 to v3: add reading_progress table.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reading_progress (
                        novel_id TEXT PRIMARY KEY NOT NULL,
                        chapter_id TEXT NOT NULL,
                        position REAL NOT NULL,
                        position_type TEXT NOT NULL DEFAULT 'PERCENTAGE',
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(novel_id) REFERENCES novels(id) ON DELETE CASCADE,
                        FOREIGN KEY(chapter_id) REFERENCES chapters(id) ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        // Migration from v3 to v4: add reading_status column to novels
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE novels ADD COLUMN reading_status TEXT NOT NULL DEFAULT 'NOT_STARTED'")
            }
        }

        // Migration from v4 to v5: add file_count to scan_fingerprints. Fingerprints are
        // now computed by walking the novel folder's actual files rather than reading the
        // folder's own metadata, so old fingerprint rows are no longer comparable; scan
        // logic forces a rescan of stale rows via CURRENT_SCAN_VERSION regardless of the
        // default value backfilled here.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE scan_fingerprints ADD COLUMN file_count INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration from v5 to v6: add optional external-metadata columns to novels
        // (description, genres, remote cover, published date, source link, fetch
        // timestamp). All nullable/defaulted, so existing rows just get NULLs and
        // nothing downstream needs to change to keep working.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE novels ADD COLUMN description TEXT")
                database.execSQL("ALTER TABLE novels ADD COLUMN genres TEXT")
                database.execSQL("ALTER TABLE novels ADD COLUMN remote_cover_url TEXT")
                database.execSQL("ALTER TABLE novels ADD COLUMN published_date TEXT")
                database.execSQL("ALTER TABLE novels ADD COLUMN external_source_url TEXT")
                database.execSQL("ALTER TABLE novels ADD COLUMN metadata_fetched_at INTEGER")
            }
        }

        // Migration from v6 to v7: add the `authors` table (see AuthorEntity /
        // AUTHOR_PAGE_AND_CHAPTER_REDESIGN.md Stage 1) and a plain, unenforced
        // `author_id` column on novels pointing at it. No FK constraint is declared
        // here - SQLite's ALTER TABLE can't retroactively attach one to an existing
        // table, so NovelEntity.authorId deliberately isn't annotated with
        // @ForeignKey either; the link is resolved in application code (ScannerImpl,
        // NovelDao.byAuthor) instead of at the DB layer.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS authors (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        avatar_uri TEXT,
                        bio TEXT,
                        joined TEXT,
                        location TEXT,
                        gender TEXT,
                        links_json TEXT,
                        followers INTEGER,
                        favorites INTEGER,
                        reviews_received INTEGER,
                        ratings_received INTEGER,
                        source_path TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("ALTER TABLE novels ADD COLUMN author_id TEXT")
            }
        }

        // Migration from v7 to v8: add sort_tier to chapters (see bugs.md Bug 2 -
        // ScannerImpl now classifies each chapter as regular/bonus/closing via a
        // filename marker, instead of relying solely on `number`/`title` ordering).
        // Existing rows backfill to 0 (regular); ScannerImpl.CURRENT_SCAN_VERSION is
        // bumped alongside this so every novel gets rescanned once and its chapters
        // reclassified, rather than sitting at the default tier until the next
        // unrelated rescan happens to touch them.
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chapters ADD COLUMN sort_tier INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Room's own exception text for an unhandled version, in both directions, names
        // the stored and requested versions as "... migration from X to Y was required
        // but not found ...". There is no dedicated exception subtype or accessor for
        // it, so this is a deliberately narrow string match: anything that doesn't match
        // passes through as the original exception rather than being guessed at.
        private val UNHANDLED_VERSION_MESSAGE =
            Regex("migration from (\\d+) to (\\d+)", RegexOption.IGNORE_CASE)

        // A downgrade is "from" > "to": the version already on disk (written by a newer
        // APK) is higher than the version this build's migration list tops out at.
        // Anything else - including a genuinely missing upgrade migration, which is a
        // real bug to catch in development, not something to soften - is returned as is.
        internal fun asDowngradeExceptionIfApplicable(e: IllegalStateException): IllegalStateException {
            val (from, to) = UNHANDLED_VERSION_MESSAGE.find(e.message ?: "")
                ?.destructured
                ?: return e
            return if (from.toInt() > to.toInt()) {
                DatabaseDowngradeException(
                    "This library was last opened by a newer version of ARKster - " +
                        "update the app rather than reinstalling an older version."
                )
            } else {
                e
            }
        }

        // No fallbackToDestructiveMigration(): it drops and recreates the whole database
        // for any version Room has no explicit path for, and chapter_overrides (manual
        // title/order edits) and reading_progress cannot be regenerated by a rescan. A
        // missing migration now throws instead of silently wiping them.
        //
        // That makes a *downgrade* (an older APK sideloaded over newer data - easy to do
        // by accident with sideload-only releases) throw too, so it gets a clear message
        // via DatabaseDowngradeException instead of a raw stack trace. Room opens the
        // SQLite file lazily - the unhandled-version exception would otherwise surface on
        // the first query, deep inside a coroutine, where onCreate's guard can't see it -
        // so the open is forced here. Migrations are a few ALTER TABLEs, so this is cheap.
        fun create(context: Context): AppDatabase {
            val built = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "arkster.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
            try {
                built.openHelper.writableDatabase
            } catch (e: IllegalStateException) {
                throw asDowngradeExceptionIfApplicable(e)
            }
            return built
        }
    }
}
