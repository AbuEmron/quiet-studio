package com.quietstudio.transcription

import com.quietstudio.core.model.SubtitleCue
import java.io.File

/**
 * Pluggable speech-to-text boundary.
 *
 * Implementations today: [WhisperTranscriptionEngine] (on-device whisper.cpp)
 * and [ManualTranscriptionEngine] (no-op fallback when native code is
 * disabled). Future modules (a different local model, a desktop companion,
 * an opt-in cloud service) implement this same interface and bind via Hilt —
 * nothing else in the app changes.
 */
interface TranscriptionEngine {
    val isAvailable: Boolean
    val requiresModel: Boolean
    fun isModelReady(): Boolean

    /**
     * Transcribes a mono WAV file and returns subtitle cues already
     * segmented for on-screen captions.
     */
    suspend fun transcribe(
        wavFile: File,
        maxWordsPerCue: Int = 5,
        onProgress: (Float) -> Unit = {},
    ): Result<List<SubtitleCue>>
}

/** Splits raw timed segments into short caption-sized cues. */
object CueSegmenter {
    data class Segment(val startMs: Long, val endMs: Long, val text: String)

    fun segment(segments: List<Segment>, maxWords: Int): List<SubtitleCue> {
        val cues = ArrayList<SubtitleCue>()
        var id = 1L
        for (seg in segments) {
            val words = seg.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            val chunks = words.chunked(maxWords.coerceAtLeast(1))
            val dur = (seg.endMs - seg.startMs).coerceAtLeast(1)
            var cursor = 0
            for (chunk in chunks) {
                val startFrac = cursor.toFloat() / words.size
                val endFrac = (cursor + chunk.size).toFloat() / words.size
                cues.add(
                    SubtitleCue(
                        id = id++,
                        startMs = seg.startMs + (dur * startFrac).toLong(),
                        endMs = seg.startMs + (dur * endFrac).toLong(),
                        text = chunk.joinToString(" "),
                    )
                )
                cursor += chunk.size
            }
        }
        // enforce minimum display time & no overlaps
        for (i in cues.indices) {
            val c = cues[i]
            val minEnd = c.startMs + 350
            val cap = cues.getOrNull(i + 1)?.startMs ?: Long.MAX_VALUE
            if (c.endMs < minEnd) cues[i] = c.copy(endMs = minOf(minEnd, cap))
        }
        return cues
    }
}
