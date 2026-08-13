package com.arkster.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "novels")
data class NovelEntity(
    @PrimaryKey val id: String,
    @ColumnInfo val title: String,
    @ColumnInfo val author: String?,
    @ColumnInfo(name = "cover_uri") val coverUri: String?,
    @ColumnInfo(name = "page_size") val pageSize: Int = 10  // per-fiction pagination default
)

@Entity(tableName = "arcs",
    foreignKeys = [
        ForeignKey(entity = NovelEntity::class, parentColumns = ["id"], childColumns = ["novel_id"], onDelete = ForeignKey.CASCADE)
    ])
data class ArcEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "novel_id") val novelId: String,
    @ColumnInfo val name: String,
    @ColumnInfo(name = "cover_uri") val coverUri: String?,
    @ColumnInfo val position: Int  // order within novel
)

@Entity(tableName = "chapters",
    foreignKeys = [
        ForeignKey(entity = NovelEntity::class, parentColumns = ["id"], childColumns = ["novel_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ArcEntity::class, parentColumns = ["id"], childColumns = ["arc_id"], onDelete = ForeignKey.SET_NULL)
    ])
data class ChapterEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "novel_id") val novelId: String,
    @ColumnInfo(name = "arc_id") val arcId: String?,
    @ColumnInfo val number: Int?,
    @ColumnInfo val title: String,
    @ColumnInfo(name = "source_path") val sourcePath: String
)

@Entity(tableName = "chapter_overrides",
    foreignKeys = [
        ForeignKey(entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapter_id"], onDelete = ForeignKey.CASCADE)
    ])
data class ChapterOverrideEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    @ColumnInfo(name = "title_override") val titleOverride: String?,
    @ColumnInfo(name = "position_override") val positionOverride: Int?,
    @ColumnInfo(name = "is_arc_start") val isArcStart: Boolean = false
)

@Entity(tableName = "scan_fingerprints",
    foreignKeys = [
        ForeignKey(entity = NovelEntity::class, parentColumns = ["id"], childColumns = ["novel_id"], onDelete = ForeignKey.CASCADE)
    ])
data class ScanFingerprintEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "novel_id") val novelId: String,
    @ColumnInfo(name = "folder_uri") val folderUri: String,
    @ColumnInfo(name = "last_modified") val lastModified: Long?,
    @ColumnInfo(name = "size") val size: Long?,
    @ColumnInfo(name = "scan_version") val scanVersion: Int = 1  // bump to force full rescan
)
