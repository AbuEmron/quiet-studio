package com.quietstudio.transcription.whisper

import android.content.Context
import com.quietstudio.BuildConfig
import com.quietstudio.core.audio.WavIo
import com.quietstudio.core.media.MediaDecode
import com.quietstudio.core.model.SubtitleCue
import com.quietstudio.transcription.CueSegmenter
import com.quietstudio.transcription.TranscriptionEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device transcription via vendored whisper.cpp.
 *
 * The ggml model file lives in filesDir/models/. It can be imported from
 * storage in Settings (fully offline) or fetched once from a public model
 * mirror — the only network call the app can ever make, always user-initiated.
 */
@Singleton
class WhisperTranscriptionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: WhisperModelManager,
) : TranscriptionEngine {

    override val isAvailable: Boolean
        get() = BuildConfig.WHISPER_ENABLED && WhisperBridge.ensureLoaded()

    override val requiresModel: Boolean = true

    override fun isModelReady(): Boolean = modelManager.installedModel() != null

    override suspend fun transcribe(
        wavFile: File,
        maxWordsPerCue: Int,
        onProgress: (Float) -> Unit,
    ): Result<List<SubtitleCue>> = withContext(Dispatchers.Default) {
        if (!isAvailable) return@withContext Result.failure(
            IllegalStateException("Whisper native library not available")
        )
        val model = modelManager.installedModel()
            ?: return@withContext Result.failure(IllegalStateException("No Whisper model installed"))

        onProgress(0.05f)
        val wav = WavIo.read(wavFile)
        var pcm = WavIo.toFloat(wav.samples)
        if (wav.channels == 2) {
            val mono = FloatArray(pcm.size / 2)
            for (i in mono.indices) mono[i] = (pcm[i * 2] + pcm[i * 2 + 1]) / 2f
            pcm = mono
        }
        val pcm16k = MediaDecode.resampleLinear(pcm, wav.sampleRate, 16000)
        onProgress(0.15f)

        // Bail before the expensive native call if the job was already cancelled.
        ensureActive()
        val ctx = WhisperBridge.initContext(model.absolutePath)
        if (ctx == 0L) return@withContext Result.failure(IllegalStateException("Failed to load model"))
        try {
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            val raw = WhisperBridge.transcribe(ctx, pcm16k, "auto", threads)
            // The native call can't be interrupted mid-flight; if we were
            // cancelled while it ran, stop here and free the context.
            ensureActive()
            onProgress(0.9f)
            val segments = raw.lineSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split('\t')
                    if (parts.size < 3) null
                    else CueSegmenter.Segment(
                        parts[0].toLongOrNull() ?: return@mapNotNull null,
                        parts[1].toLongOrNull() ?: return@mapNotNull null,
                        parts.drop(2).joinToString("\t").trim(),
                    )
                }
                .toList()
            onProgress(1f)
            Result.success(CueSegmenter.segment(segments, maxWordsPerCue))
        } finally {
            WhisperBridge.freeContext(ctx)
        }
    }
}

/** Fallback used when the native library is disabled or missing. */
@Singleton
class ManualTranscriptionEngine @Inject constructor() : TranscriptionEngine {
    override val isAvailable = false
    override val requiresModel = false
    override fun isModelReady() = false
    override suspend fun transcribe(
        wavFile: File,
        maxWordsPerCue: Int,
        onProgress: (Float) -> Unit,
    ): Result<List<SubtitleCue>> =
        Result.failure(UnsupportedOperationException("On-device transcription disabled in this build"))
}
