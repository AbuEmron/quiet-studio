package com.quietstudio.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.quietstudio.core.model.BackgroundKind
import com.quietstudio.core.model.Codec
import com.quietstudio.core.model.MusicTrack
import com.quietstudio.core.model.ProjectContent
import com.quietstudio.core.model.Resolution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Default on-device export pipeline built on Media3 Transformer.
 *
 * Timeline assembly:
 *  - AUDIO: narration + looped/ducked music are pre-mixed offline by
 *    [AudioMixdown] into one stereo WAV -> its own sequence.
 *  - VIDEO: either the user's background video (repeated to cover the
 *    duration, audio stripped) or a generated base frame; all procedural
 *    visuals + burned-in subtitles are painted per-frame by [SceneRenderer]
 *    and [SubtitlePainter] through a composition-level [BitmapOverlay], so
 *    export matches the editor preview exactly.
 */
@Singleton
@UnstableApi
class Media3ExportEngine @Inject constructor() : MediaEngine {

    private var activeTransformer: Transformer? = null
    private var handler: Handler? = null

    override suspend fun exportProject(
        context: Context,
        project: ProjectContent,
        musicTrack: MusicTrack?,
        outFile: File,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val narrationPath = project.narration.wavPath
                ?: return@withContext Result.failure(IllegalStateException("No narration recorded"))

            // Resolve AUTO scenery to the music's mood + a per-project seed so
            // every video gets its own original animated scene.
            @Suppress("NAME_SHADOWING")
            val project = project.copy(
                visual = project.visual.resolvedScenery(
                    musicMood = musicTrack?.mood,
                    projectSeed = narrationPath.hashCode().toLong(),
                ),
            )

            // ---- 1. offline audio mixdown (0..15% of progress)
            onProgress(0.02f)
            val mixWav = File(context.cacheDir, "mix_${System.currentTimeMillis()}.wav")
            AudioMixdown.render(context, File(narrationPath), musicTrack, project.music, mixWav)
            onProgress(0.15f)

            val durationMs = probeWavDurationMs(mixWav)
            val res = Resolution.valueOf(project.export.resolution)
            val fps = project.export.fps

            // ---- 2. build composition
            val overlay = TimelineOverlay(context, project, res.width, res.height, durationMs)
            val overlayEffect = OverlayEffect(listOf<TextureOverlay>(overlay))
            val presentation = Presentation.createForWidthAndHeight(
                res.width, res.height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )

            val videoItems: List<EditedMediaItem> =
                if (BackgroundKind.valueOf(project.visual.kind) == BackgroundKind.VIDEO &&
                    project.visual.sourceUri != null
                ) {
                    buildVideoBackgroundItems(context, project.visual.sourceUri!!, durationMs)
                } else {
                    listOf(baseFrameItem(context, res.width, res.height, durationMs, fps))
                }

            val videoSeq = EditedMediaItemSequence(videoItems)
            val audioSeq = EditedMediaItemSequence(
                EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(mixWav))).build()
            )
            val composition = Composition.Builder(videoSeq, audioSeq)
                .setEffects(
                    Effects(emptyList(), listOf<Effect>(presentation, overlayEffect))
                )
                .build()

            // ---- 3. transformer
            val mime = when (Codec.valueOf(project.export.codec)) {
                Codec.H264 -> MimeTypes.VIDEO_H264
                Codec.HEVC -> MimeTypes.VIDEO_H265
            }
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(project.export.bitrateMbps * 1_000_000)
                        .build()
                )
                .build()

            val thread = HandlerThread("qs-export").apply { start() }
            val h = Handler(thread.looper)
            handler = h

            val result = try {
                suspendCancellableCoroutine<Result<File>> { cont ->
                h.post {
                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(mime)
                        .setEncoderFactory(encoderFactory)
                        .setLooper(thread.looper)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(c: Composition, r: ExportResult) {
                                if (cont.isActive) cont.resume(Result.success(outFile))
                            }

                            override fun onError(c: Composition, r: ExportResult, e: ExportException) {
                                if (cont.isActive) cont.resume(Result.failure(e))
                            }
                        })
                        .build()
                    activeTransformer = transformer

                    // progress poller (15%..100%)
                    val holder = ProgressHolder()
                    val poll = object : Runnable {
                        override fun run() {
                            val state = transformer.getProgress(holder)
                            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress(0.15f + 0.85f * (holder.progress / 100f))
                            }
                            if (cont.isActive) h.postDelayed(this, 400)
                        }
                    }
                    h.post(poll)
                    transformer.start(composition, outFile.absolutePath)
                }
                cont.invokeOnCancellation {
                    h.post { runCatching { activeTransformer?.cancel() } }
                }
                }
            } finally {
                runCatching { thread.quitSafely() }
                mixWav.delete()
            }
            onProgress(1f)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            activeTransformer = null
        }
    }

    override suspend fun cancel() {
        val h = handler ?: return
        h.post { runCatching { activeTransformer?.cancel() } }
    }

    /* -------------------------------------------------------------------- */

    /** Solid base frame used when the whole scene is painted procedurally. */
    private fun baseFrameItem(
        context: Context, w: Int, h: Int, durationMs: Long, fps: Int,
    ): EditedMediaItem {
        val file = File(context.cacheDir, "base_${w}x$h.png")
        if (!file.exists()) {
            val bmp = Bitmap.createBitmap(w / 4, h / 4, Bitmap.Config.ARGB_8888)
            Canvas(bmp).drawColor(Color.BLACK)
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        }
        return EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file)))
            .setDurationUs(durationMs * 1000)
            .setFrameRate(fps)
            .build()
    }

    /** Repeats the background video enough times to cover the narration. */
    private fun buildVideoBackgroundItems(
        context: Context, uriString: String, durationMs: Long,
    ): List<EditedMediaItem> {
        val uri = Uri.parse(uriString)
        val retriever = android.media.MediaMetadataRetriever()
        val videoDurMs = try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: durationMs
        } finally {
            runCatching { retriever.release() }
        }
        val safeDur = videoDurMs.coerceAtLeast(500)
        val repeats = ((durationMs + safeDur - 1) / safeDur).toInt().coerceAtLeast(1)
        val items = ArrayList<EditedMediaItem>(repeats)
        var remaining = durationMs
        repeat(repeats) {
            val clipMs = minOf(safeDur, remaining)
            val mi = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setEndPositionMs(clipMs).build()
                )
                .build()
            items.add(EditedMediaItem.Builder(mi).setRemoveAudio(true).build())
            remaining -= clipMs
        }
        return items
    }

    private fun probeWavDurationMs(file: File): Long {
        // 44-byte header, 48 kHz stereo 16-bit
        val dataBytes = file.length() - 44
        return dataBytes * 1000 / (48000L * 2 * 2)
    }
}

/**
 * Composition-level overlay that paints procedural background + film grain +
 * vignette + subtitles for every frame at its composition timestamp.
 */
@UnstableApi
private class TimelineOverlay(
    context: Context,
    private val project: ProjectContent,
    private val width: Int,
    private val height: Int,
    private val durationMs: Long,
) : BitmapOverlay() {

    private val scene = SceneRenderer(width, height)
    private val subtitles = SubtitlePainter(width, height, context)
    private val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val isVideoBg = BackgroundKind.valueOf(project.visual.kind) == BackgroundKind.VIDEO

    init {
        if (BackgroundKind.valueOf(project.visual.kind) == BackgroundKind.IMAGE &&
            project.visual.sourceUri != null
        ) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(project.visual.sourceUri))
                    ?.use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull()?.let { scene.setSourceBitmap(project.visual.sourceUri, it) }
        }
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val timeMs = presentationTimeUs / 1000
        if (isVideoBg) {
            // keep decoder frames visible; only paint grain/vignette/subs
            bitmap.eraseColor(Color.TRANSPARENT)
            val cfg = project.visual.copy(kind = BackgroundKind.VIDEO.name)
            scene.drawFrame(canvas, cfg, timeMs, durationMs)
        } else {
            scene.drawFrame(canvas, project.visual, timeMs, durationMs)
        }
        subtitles.draw(canvas, project.cues, project.subtitleStyle, timeMs)
        return bitmap
    }
}
