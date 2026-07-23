package com.quietstudio.feature.export

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.quietstudio.core.media.MediaEngine
import com.quietstudio.core.model.ExportConfig
import com.quietstudio.core.music.MusicLibrary
import com.quietstudio.data.AppJson
import com.quietstudio.data.ExportQueueRepository
import com.quietstudio.data.MusicRepository
import com.quietstudio.data.ProjectRepository
import kotlinx.coroutines.flow.first
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * Background renderer. Runs each queued export job under a foreground
 * notification so long renders survive the app being backgrounded; jobs
 * queue through WorkManager's unique-work chain (see ExportViewModel).
 */
@HiltWorker
class ExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val projects: ProjectRepository,
    private val queue: ExportQueueRepository,
    private val engine: MediaEngine,
    private val musicLibrary: MusicLibrary,
    private val musicRepository: MusicRepository,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_JOB_ID = "jobId"
        const val CHANNEL = "exports"
    }

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val job = queue.get(jobId) ?: return Result.failure()
        val project = projects.get(job.projectId)
            ?: return Result.failure().also { queue.update(jobId, "FAILED", 0f, error = "Project missing") }

        setForeground(foregroundInfo(job.projectTitle, 0))
        queue.update(jobId, "RENDERING", 0f)

        val config = runCatching {
            AppJson.decodeFromString<ExportConfig>(job.configJson)
        }.getOrElse { ExportConfig() }
        val content = project.content.copy(export = config)

        val outDir = File(applicationContext.filesDir, "exports").apply { mkdirs() }
        val safeTitle = job.projectTitle.replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "export" }
        val outFile = File(outDir, "${safeTitle}_${System.currentTimeMillis()}.mp4")

        // Resolve from the database as well as the bundled catalog: a queued
        // render can start in a fresh process, where the in-memory imported
        // registry has not been populated yet.
        val track = musicLibrary.byId(content.music.trackId)
            ?: musicRepository.observeImported().first().firstOrNull { it.id == content.music.trackId }
        var lastNotified = 0
        val result = engine.exportProject(
            applicationContext, content, track, outFile,
            onProgress = { p ->
                val pct = (p * 100).toInt()
                if (pct != lastNotified) {
                    lastNotified = pct
                    runCatching { setForegroundAsync(foregroundInfo(job.projectTitle, pct)) }
                }
                kotlinx.coroutines.runBlocking { queue.update(jobId, "RENDERING", p) }
            },
        )

        return result.fold(
            onSuccess = {
                queue.update(jobId, "DONE", 1f, output = it.absolutePath)
                Result.success()
            },
            onFailure = {
                queue.update(jobId, "FAILED", 0f, error = it.message ?: "Render failed")
                Result.failure()
            },
        )
    }

    private fun foregroundInfo(title: String, pct: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Exports", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setContentTitle("Rendering “$title”")
            .setProgress(100, pct, pct == 0)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return when {
            Build.VERSION.SDK_INT >= 35 ->
                ForegroundInfo(1042, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            Build.VERSION.SDK_INT >= 29 ->
                ForegroundInfo(1042, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> ForegroundInfo(1042, notif)
        }
    }
}
