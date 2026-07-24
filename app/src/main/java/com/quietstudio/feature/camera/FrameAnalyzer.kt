package com.quietstudio.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.quietstudio.core.media.enhance.EnhanceGrade
import com.quietstudio.core.media.scenes.AnimatedScenes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Samples one representative frame of the project's footage and derives the
 * auto exposure + white-balance correction. Used once when enhance is switched
 * on; a procedural background with no footage returns the neutral (1, 0).
 */
object FrameAnalyzer {

    suspend fun analyze(context: Context, sourceUri: String?): Pair<Float, Float> =
        withContext(Dispatchers.IO) {
            val bmp = loadFrame(context, sourceUri) ?: return@withContext 1f to 0f
            val (r, g, b) = averageRgb(bmp)
            bmp.recycle()
            EnhanceGrade.autoLevels(r, g, b)
        }

    private fun loadFrame(context: Context, sourceUri: String?): Bitmap? {
        if (sourceUri == null) return null
        // Bundled animated scene (asset video).
        AnimatedScenes.assetPath(sourceUri)?.let { path ->
            return runCatching {
                val mmr = MediaMetadataRetriever()
                try {
                    context.assets.openFd(path).use { afd ->
                        mmr.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                    mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally { runCatching { mmr.release() } }
            }.getOrNull()
        }
        // file:// video (camera clip / imported).
        if (sourceUri.startsWith("file://")) {
            val file = File(Uri.parse(sourceUri).path ?: return null)
            if (!file.exists()) return null
            return runCatching {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(file.absolutePath)
                    mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally { runCatching { mmr.release() } }
            }.getOrNull()
        }
        // content:// image or video.
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use {
                BitmapFactory.decodeStream(it)
            }
        }.getOrNull()
    }

    /** Average linear-ish RGB (0..1) over a downsampled grid. */
    fun averageRgb(bmp: Bitmap): Triple<Float, Float, Float> {
        val step = maxOf(1, minOf(bmp.width, bmp.height) / 32)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val p = bmp.getPixel(x, y)
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
                x += step
            }
            y += step
        }
        if (n == 0L) return Triple(0.5f, 0.5f, 0.5f)
        return Triple(r / n / 255f, g / n / 255f, b / n / 255f)
    }
}
