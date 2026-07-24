package com.quietstudio.data

import android.content.Context
import android.net.Uri
import com.quietstudio.core.model.ProjectContent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup & restore for the sideloaded build — the real insurance against losing
 * work when an update forces a reinstall.
 *
 * A backup is a single ZIP the user saves anywhere (Downloads/Drive via the
 * system file picker), so it survives even an uninstall. It carries every
 * project's metadata AND the per-project media those projects point at
 * (narration audio, recorded/imported video), with paths rewritten to a
 * portable token so restore relinks them into fresh app storage. Bundled
 * assets (scenery/animated/music that ship in the APK) stay as asset:// URIs
 * and need no copying.
 *
 * Paths are rewritten on the typed [ProjectContent] fields, not by string
 * surgery, because narration wants a raw filesystem path while a video
 * background wants a file:// URI — restore reconstructs each correctly.
 */
@Singleton
class ProjectBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projects: ProjectRepository,
) {
    @Serializable
    private data class BackupManifest(val version: Int, val projects: List<BackupProject>)

    @Serializable
    private data class BackupProject(
        val title: String,
        val tags: List<String>,
        val createdAt: Long,
        val updatedAt: Long,
        val contentJson: String,
    )

    private val restoreDir: File get() = File(context.filesDir, "restored").apply { mkdirs() }

    data class Result(val count: Int)

    /** Writes all projects + their media into [target]. Returns how many. */
    suspend fun export(target: Uri): Result = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(target)
            ?: error("Couldn't open the backup destination")
        out.use { exportTo(it) }
    }

    /** Reads all projects + their media from [source]. Returns how many. */
    suspend fun import(source: Uri): Result = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(source)
            ?: error("Couldn't open the backup file")
        input.use { importFrom(it) }
    }

    /** Stream-level export, testable without a ContentResolver. */
    suspend fun exportTo(out: java.io.OutputStream): Result = withContext(Dispatchers.IO) {
        val all = projects.allProjectsOnce()
        val staged = LinkedHashMap<String, File>() // entryName -> source file

        val backupProjects = all.map { p ->
            val portable = p.content.copy(
                narration = p.content.narration.copy(
                    wavPath = tokenizeRaw(p.content.narration.wavPath, staged),
                ),
                visual = p.content.visual.copy(
                    sourceUri = tokenizeUri(p.content.visual.sourceUri, staged),
                ),
            )
            BackupProject(
                p.title, p.tags, p.createdAt, p.updatedAt,
                AppJson.encodeToString(ProjectContent.serializer(), portable),
            )
        }

        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(
                AppJson.encodeToString(
                    BackupManifest.serializer(),
                    BackupManifest(FORMAT_VERSION, backupProjects),
                ).toByteArray(),
            )
            zip.closeEntry()
            for ((entryName, src) in staged) {
                if (!src.exists()) continue
                zip.putNextEntry(ZipEntry(entryName))
                src.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        Result(all.size)
    }

    /** Stream-level import, testable without a ContentResolver. */
    suspend fun importFrom(input: java.io.InputStream): Result = withContext(Dispatchers.IO) {
        var manifestText: String? = null
        ZipInputStream(input.buffered()).use { zip ->
            var e: ZipEntry? = zip.nextEntry
            while (e != null) {
                val entry = e
                when {
                    entry.name == MANIFEST -> manifestText = zip.readBytes().decodeToString()
                    entry.name.startsWith("files/") -> {
                        val name = entry.name.removePrefix("files/")
                        if (name.isNotBlank() && !name.contains('/')) {
                            File(restoreDir, name).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                zip.closeEntry()
                e = zip.nextEntry
            }
        }

        val manifest = manifestText?.let {
            runCatching { AppJson.decodeFromString(BackupManifest.serializer(), it) }.getOrNull()
        } ?: error("This file isn't a Quiet Studio backup")

        var restored = 0
        for (bp in manifest.projects) {
            val content = runCatching {
                AppJson.decodeFromString(ProjectContent.serializer(), bp.contentJson)
            }.getOrNull() ?: continue

            val fixed = content.copy(
                narration = content.narration.copy(
                    wavPath = detokenizeRaw(content.narration.wavPath),
                ),
                visual = content.visual.copy(
                    sourceUri = detokenizeUri(content.visual.sourceUri),
                ),
            )

            val now = System.currentTimeMillis()
            projects.insertRestored(
                Project(
                    id = UUID.randomUUID().toString(), // new id → never overwrites current work
                    title = bp.title,
                    folderId = null,
                    content = fixed,
                    tags = bp.tags,
                    createdAt = bp.createdAt.takeIf { it > 0 } ?: now,
                    updatedAt = now,
                )
            )
            restored++
        }
        Result(restored)
    }

    // ── tokenising (export) ──────────────────────────────────────────────────

    /** Raw filesystem path (narration WAV) → token; stages the file. */
    private fun tokenizeRaw(path: String?, staged: MutableMap<String, File>): String? {
        if (path.isNullOrBlank() || path.startsWith(TOKEN) || path.startsWith("asset:")) return path
        // Any existing on-disk file, regardless of path style, is a raw local
        // media file worth carrying in the backup.
        val f = File(path)
        if (!f.isFile) return path
        staged["files/${f.name}"] = f
        return "$TOKEN/${f.name}"
    }

    /** file:// media URI (video background) → token; stages the file. */
    private fun tokenizeUri(uri: String?, staged: MutableMap<String, File>): String? {
        if (uri.isNullOrBlank() || !uri.startsWith("file://")) return uri
        val f = File(Uri.parse(uri).path ?: return uri)
        if (!f.exists()) return uri
        staged["files/${f.name}"] = f
        return "$TOKEN/${f.name}"
    }

    // ── detokenising (restore) ───────────────────────────────────────────────

    private fun detokenizeRaw(value: String?): String? {
        val name = tokenName(value) ?: return value
        return File(restoreDir, name).absolutePath
    }

    private fun detokenizeUri(value: String?): String? {
        val name = tokenName(value) ?: return value
        return "file://${File(restoreDir, name).absolutePath}"
    }

    private fun tokenName(value: String?): String? =
        if (value != null && value.startsWith("$TOKEN/")) value.removePrefix("$TOKEN/") else null

    companion object {
        const val FORMAT_VERSION = 1
        const val MANIFEST = "manifest.json"
        /** Portable path token stored in a backup; relinked on restore. */
        const val TOKEN = "qsbackup://files"
        const val MIME = "application/zip"
        fun suggestedName(): String = "quiet-studio-backup.qsb.zip"
    }
}
