package com.quietstudio.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects", indices = [Index("folderId"), Index("updatedAt")])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folderId: String? = null,
    /** JSON-serialized ProjectContent */
    val contentJson: String,
    val tagsCsv: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val durationMs: Long = 0,
)

@Entity(tableName = "project_versions", indices = [Index("projectId")])
data class ProjectVersionEntity(
    @PrimaryKey(autoGenerate = true) val versionId: Long = 0,
    val projectId: String,
    val contentJson: String,
    val label: String,
    val createdAt: Long,
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** JSON-serialized TemplateContent */
    val contentJson: String,
    val createdAt: Long,
)

@Entity(tableName = "visual_packs")
data class VisualPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** JSON-serialized VisualPack */
    val contentJson: String,
    val createdAt: Long,
)

@Entity(tableName = "music_prefs")
data class MusicPrefEntity(
    @PrimaryKey val trackId: String,
    val favorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val trackIdsCsv: String = "",
    val createdAt: Long,
)

@Entity(tableName = "imported_tracks")
data class ImportedTrackEntity(
    @PrimaryKey val id: String,
    /** JSON-serialized MusicTrack */
    val trackJson: String,
    val createdAt: Long,
)

@Entity(tableName = "export_jobs", indices = [Index("state")])
data class ExportJobEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val projectTitle: String,
    val configJson: String,
    val state: String,          // ExportState
    val progress: Float = 0f,
    val outputPath: String? = null,
    val error: String? = null,
    val createdAt: Long,
)
