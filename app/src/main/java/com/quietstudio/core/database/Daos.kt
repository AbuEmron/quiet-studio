package com.quietstudio.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query(
        "SELECT * FROM projects WHERE (:folderId IS NULL OR folderId = :folderId) " +
            "AND (title LIKE '%' || :query || '%' OR tagsCsv LIKE '%' || :query || '%') " +
            "ORDER BY updatedAt DESC"
    )
    fun observeFiltered(folderId: String?, query: String): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Insert
    suspend fun insertVersion(version: ProjectVersionEntity)

    @Query("SELECT * FROM project_versions WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeVersions(projectId: String): Flow<List<ProjectVersionEntity>>

    @Query(
        "DELETE FROM project_versions WHERE projectId = :projectId AND versionId NOT IN " +
            "(SELECT versionId FROM project_versions WHERE projectId = :projectId " +
            "ORDER BY createdAt DESC LIMIT :keep)"
    )
    suspend fun pruneVersions(projectId: String, keep: Int)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name")
    fun observeAll(): Flow<List<FolderEntity>>

    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun get(id: String): TemplateEntity?

    @Upsert
    suspend fun upsert(template: TemplateEntity)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface VisualPackDao {
    @Query("SELECT * FROM visual_packs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VisualPackEntity>>

    @Upsert
    suspend fun upsert(pack: VisualPackEntity)

    @Query("DELETE FROM visual_packs WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MusicDao {
    @Query("SELECT * FROM music_prefs")
    fun observePrefs(): Flow<List<MusicPrefEntity>>

    @Upsert
    suspend fun upsertPref(pref: MusicPrefEntity)

    @Query("SELECT * FROM music_prefs WHERE trackId = :trackId")
    suspend fun getPref(trackId: String): MusicPrefEntity?

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("SELECT * FROM imported_tracks ORDER BY createdAt DESC")
    fun observeImported(): Flow<List<ImportedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImported(track: ImportedTrackEntity)
}

@Dao
interface ExportDao {
    @Query("SELECT * FROM export_jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ExportJobEntity>>

    @Query("SELECT * FROM export_jobs WHERE id = :id")
    suspend fun get(id: String): ExportJobEntity?

    @Upsert
    suspend fun upsert(job: ExportJobEntity)

    @Query("UPDATE export_jobs SET state = :state, progress = :progress, outputPath = :output, error = :error WHERE id = :id")
    suspend fun updateState(id: String, state: String, progress: Float, output: String?, error: String?)

    @Query("DELETE FROM export_jobs WHERE state IN ('DONE','FAILED','CANCELLED')")
    suspend fun clearFinished()
}
