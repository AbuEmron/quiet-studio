package com.quietstudio.data

import com.quietstudio.core.database.ExportDao
import com.quietstudio.core.database.ExportJobEntity
import com.quietstudio.core.database.FolderDao
import com.quietstudio.core.database.FolderEntity
import com.quietstudio.core.database.ImportedTrackEntity
import com.quietstudio.core.database.MusicDao
import com.quietstudio.core.database.MusicPrefEntity
import com.quietstudio.core.database.PlaylistEntity
import com.quietstudio.core.database.ProjectDao
import com.quietstudio.core.database.ProjectEntity
import com.quietstudio.core.database.ProjectVersionEntity
import com.quietstudio.core.database.TemplateDao
import com.quietstudio.core.database.TemplateEntity
import com.quietstudio.core.database.VisualPackDao
import com.quietstudio.core.database.VisualPackEntity
import com.quietstudio.core.model.ExportConfig
import com.quietstudio.core.model.MusicTrack
import com.quietstudio.core.model.ProjectContent
import com.quietstudio.core.model.TemplateContent
import com.quietstudio.core.model.VisualPack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

val AppJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/* ------------------------------- projects -------------------------------- */

data class Project(
    val id: String,
    val title: String,
    val folderId: String?,
    val content: ProjectContent,
    val tags: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val folderDao: FolderDao,
) {
    private fun ProjectEntity.toDomain() = Project(
        id, title, folderId,
        runCatching { AppJson.decodeFromString<ProjectContent>(contentJson) }
            .getOrElse { ProjectContent() },
        tagsCsv.split(',').filter { it.isNotBlank() },
        createdAt, updatedAt,
    )

    fun observeProjects(folderId: String? = null, query: String = ""): Flow<List<Project>> =
        projectDao.observeFiltered(folderId, query).map { list -> list.map { it.toDomain() } }

    fun observeFolders(): Flow<List<FolderEntity>> = folderDao.observeAll()

    suspend fun get(id: String): Project? = projectDao.get(id)?.toDomain()

    suspend fun create(title: String, content: ProjectContent = ProjectContent()): Project {
        val now = System.currentTimeMillis()
        val p = Project(UUID.randomUUID().toString(), title, null, content, emptyList(), now, now)
        save(p, snapshot = false)
        return p
    }

    /** Autosave. Takes a version snapshot at most every [snapshotIntervalMs]. */
    suspend fun save(project: Project, snapshot: Boolean = true) {
        val json = AppJson.encodeToString(ProjectContent.serializer(), project.content)
        projectDao.upsert(
            ProjectEntity(
                id = project.id, title = project.title, folderId = project.folderId,
                contentJson = json, tagsCsv = project.tags.joinToString(","),
                createdAt = project.createdAt, updatedAt = System.currentTimeMillis(),
                durationMs = project.content.narration.durationMs,
            )
        )
        if (snapshot) {
            projectDao.insertVersion(
                ProjectVersionEntity(
                    projectId = project.id, contentJson = json,
                    label = "Autosave", createdAt = System.currentTimeMillis(),
                )
            )
            projectDao.pruneVersions(project.id, keep = 25)
        }
    }

    suspend fun duplicate(id: String): Project? {
        val src = get(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = src.copy(
            id = UUID.randomUUID().toString(),
            title = "${src.title} copy",
            createdAt = now, updatedAt = now,
        )
        save(copy, snapshot = false)
        return copy
    }

    suspend fun delete(id: String) = projectDao.delete(id)

    suspend fun rename(id: String, title: String) {
        get(id)?.let { save(it.copy(title = title), snapshot = false) }
    }

    suspend fun setTags(id: String, tags: List<String>) {
        get(id)?.let { save(it.copy(tags = tags), snapshot = false) }
    }

    suspend fun moveToFolder(id: String, folderId: String?) {
        get(id)?.let { save(it.copy(folderId = folderId), snapshot = false) }
    }

    suspend fun createFolder(name: String) =
        folderDao.upsert(FolderEntity(UUID.randomUUID().toString(), name, System.currentTimeMillis()))

    fun observeVersions(projectId: String) = projectDao.observeVersions(projectId)

    suspend fun restoreVersion(projectId: String, version: ProjectVersionEntity): Project? {
        val p = get(projectId) ?: return null
        val content = runCatching {
            AppJson.decodeFromString<ProjectContent>(version.contentJson)
        }.getOrNull() ?: return null
        val restored = p.copy(content = content)
        save(restored, snapshot = true)
        return restored
    }
}

/* -------------------------------- music ---------------------------------- */

@Singleton
class MusicRepository @Inject constructor(
    private val musicDao: MusicDao,
) {
    fun observeFavorites(): Flow<Set<String>> =
        musicDao.observePrefs().map { prefs -> prefs.filter { it.favorite }.map { it.trackId }.toSet() }

    suspend fun toggleFavorite(trackId: String) {
        val cur = musicDao.getPref(trackId) ?: MusicPrefEntity(trackId)
        musicDao.upsertPref(cur.copy(favorite = !cur.favorite))
    }

    suspend fun notePlayed(trackId: String) {
        val cur = musicDao.getPref(trackId) ?: MusicPrefEntity(trackId)
        musicDao.upsertPref(cur.copy(playCount = cur.playCount + 1, lastPlayedAt = System.currentTimeMillis()))
    }

    fun observePlaylists(): Flow<List<PlaylistEntity>> = musicDao.observePlaylists()

    suspend fun savePlaylist(id: String?, name: String, trackIds: List<String>) {
        musicDao.upsertPlaylist(
            PlaylistEntity(
                id ?: UUID.randomUUID().toString(), name,
                trackIds.joinToString(","), System.currentTimeMillis(),
            )
        )
    }

    suspend fun deletePlaylist(id: String) = musicDao.deletePlaylist(id)

    fun observeImported(): Flow<List<MusicTrack>> =
        musicDao.observeImported().map { list ->
            list.mapNotNull {
                runCatching { AppJson.decodeFromString<MusicTrack>(it.trackJson) }.getOrNull()
            }
        }

    suspend fun addImported(track: MusicTrack) {
        musicDao.insertImported(
            ImportedTrackEntity(
                track.id, AppJson.encodeToString(MusicTrack.serializer(), track),
                System.currentTimeMillis(),
            )
        )
    }
}

/* ------------------------------ templates --------------------------------- */

@Singleton
class TemplateRepository @Inject constructor(
    private val templateDao: TemplateDao,
    private val visualPackDao: VisualPackDao,
) {
    fun observeTemplates(): Flow<List<Pair<TemplateEntity, TemplateContent?>>> =
        templateDao.observeAll().map { list ->
            list.map {
                it to runCatching { AppJson.decodeFromString<TemplateContent>(it.contentJson) }.getOrNull()
            }
        }

    suspend fun saveTemplate(name: String, content: TemplateContent) {
        templateDao.upsert(
            TemplateEntity(
                UUID.randomUUID().toString(), name,
                AppJson.encodeToString(TemplateContent.serializer(), content),
                System.currentTimeMillis(),
            )
        )
    }

    suspend fun getTemplate(id: String): TemplateContent? =
        templateDao.get(id)?.let {
            runCatching { AppJson.decodeFromString<TemplateContent>(it.contentJson) }.getOrNull()
        }

    suspend fun deleteTemplate(id: String) = templateDao.delete(id)

    fun observeVisualPacks(): Flow<List<VisualPack>> =
        visualPackDao.observeAll().map { list ->
            list.mapNotNull {
                runCatching { AppJson.decodeFromString<VisualPack>(it.contentJson) }.getOrNull()
            }
        }

    suspend fun saveVisualPack(pack: VisualPack) {
        visualPackDao.upsert(
            VisualPackEntity(
                pack.id, pack.name,
                AppJson.encodeToString(VisualPack.serializer(), pack),
                System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteVisualPack(id: String) = visualPackDao.delete(id)
}

/* -------------------------------- export ---------------------------------- */

@Singleton
class ExportQueueRepository @Inject constructor(
    private val exportDao: ExportDao,
) {
    fun observeJobs(): Flow<List<ExportJobEntity>> = exportDao.observeAll()

    suspend fun enqueue(projectId: String, projectTitle: String, config: ExportConfig): String {
        val id = UUID.randomUUID().toString()
        exportDao.upsert(
            ExportJobEntity(
                id = id, projectId = projectId, projectTitle = projectTitle,
                configJson = AppJson.encodeToString(ExportConfig.serializer(), config),
                state = "QUEUED", createdAt = System.currentTimeMillis(),
            )
        )
        return id
    }

    suspend fun get(id: String) = exportDao.get(id)

    suspend fun update(id: String, state: String, progress: Float, output: String? = null, error: String? = null) =
        exportDao.updateState(id, state, progress, output, error)

    suspend fun clearFinished() = exportDao.clearFinished()
}
