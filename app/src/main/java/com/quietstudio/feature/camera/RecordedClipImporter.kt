package com.quietstudio.feature.camera

import android.content.Context
import android.media.MediaMetadataRetriever
import com.quietstudio.core.audio.WavIo
import com.quietstudio.core.media.MediaDecode
import com.quietstudio.core.model.BackgroundKind
import com.quietstudio.core.model.NarrationInfo
import com.quietstudio.core.model.ProjectContent
import com.quietstudio.core.model.VisualConfig
import com.quietstudio.data.Project
import com.quietstudio.data.ProjectRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a freshly recorded camera clip into a project.
 *
 * The clip becomes the project's VIDEO background (reusing the existing
 * video-background compositing path — plays in preview, burns into export),
 * and its own audio is extracted to a narration WAV so the recorded sound is
 * the soundtrack and flows through the mixdown + subtitles. Everything stays
 * on-device; nothing is uploaded.
 */
@Singleton
class RecordedClipImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projects: ProjectRepository,
) {
    private val clipsDir = File(context.filesDir, "camera").apply { mkdirs() }

    /** Copies [recorded] into app storage and creates a project from it. */
    suspend fun createProject(recorded: File, title: String? = null): Project = withContext(Dispatchers.IO) {
        val stamp = System.currentTimeMillis()
        val mp4 = File(clipsDir, "clip_$stamp.mp4")
        if (recorded.absolutePath != mp4.absolutePath) {
            runCatching { recorded.copyTo(mp4, overwrite = true) }
        }

        val durationMs = probeDurationMs(mp4)
        val wav = File(clipsDir, "clip_$stamp.wav")
        extractAudioToWav(mp4, wav, durationMs)

        val name = title ?: "Recording " +
            java.text.SimpleDateFormat("MMM d · HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(stamp))

        projects.create(
            name,
            ProjectContent(
                narration = NarrationInfo(wav.absolutePath, durationMs, processed = true),
                visual = VisualConfig(
                    kind = BackgroundKind.VIDEO.name,
                    sourceUri = "file://${mp4.absolutePath}",
                ),
            ),
        )
    }

    private fun probeDurationMs(file: File): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { mmr.release() }
        }
    }

    /**
     * Decodes the clip's audio track to a mono 16 kHz WAV. If the clip has no
     * audio (or decoding fails), writes a short silent WAV so the export
     * pipeline — which needs a narration track — still works.
     */
    private fun extractAudioToWav(mp4: File, wav: File, durationMs: Long) {
        val pcm = runCatching { MediaDecode.decodeFile(mp4, SAMPLE_RATE) }.getOrDefault(FloatArray(0))
        val shorts = if (pcm.isNotEmpty()) {
            ShortArray(pcm.size) { i -> floatToPcm16(pcm[i]) }
        } else {
            val silentLen = ((durationMs.coerceAtLeast(500)) * SAMPLE_RATE / 1000L).toInt()
            ShortArray(silentLen)
        }
        WavIo.write(wav, shorts, SAMPLE_RATE, channels = 1)
    }

    private fun floatToPcm16(v: Float): Short =
        (v.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

    companion object {
        const val SAMPLE_RATE = 16_000

        /** Pure conversion, exposed for tests. */
        fun pcm16(v: Float): Short = (v.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
    }
}
