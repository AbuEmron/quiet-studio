package com.quietstudio.core.music

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.quietstudio.core.model.MusicTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings the user's own music into the studio, privately.
 *
 * The picked file is copied into app-private storage — a SAF grant does not
 * survive a reboot, and a project must still play next month — and never
 * leaves the device. Metadata comes from the file itself; anything missing
 * falls back to the display name.
 */
@Singleton
class MusicImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val directory = File(context.filesDir, "music").apply { mkdirs() }

    /**
     * Copies [uri] into app storage and describes it as a [MusicTrack].
     * Returns null when the source cannot be read or holds no audio.
     */
    fun import(uri: Uri): MusicTrack? {
        val name = displayName(uri) ?: "Imported track"
        val extension = name.substringAfterLast('.', "").lowercase()
            .takeIf { it.length in 2..5 } ?: "audio"
        val id = "imported-${UUID.randomUUID()}"
        val target = File(directory, "$id.$extension")

        runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "unreadable source" }
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }.getOrElse { return null }
        if (target.length() == 0L) {
            target.delete()
            return null
        }

        val retriever = MediaMetadataRetriever()
        val (title, durationMs) = try {
            retriever.setDataSource(target.absolutePath)
            val metaTitle = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            (metaTitle ?: name.substringBeforeLast('.')) to duration
        } catch (e: Exception) {
            // Not decodable as audio — don't offer a track that cannot play.
            target.delete()
            return null
        } finally {
            runCatching { retriever.release() }
        }

        return MusicTrack(
            id = id,
            title = title,
            mood = IMPORTED_MOOD,
            tags = listOf("imported"),
            bpm = 0,
            key = "—",
            energy = 3,
            durationSec = durationMs / 1000.0,
            file = target.absolutePath,
            loop = true,
            isImported = true,
        )
    }

    fun delete(track: MusicTrack) {
        if (track.isImported) runCatching { File(track.file).delete() }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    companion object {
        /** Mood bucket imported tracks appear under in every mood filter. */
        const val IMPORTED_MOOD = "Imported"
    }
}
