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

    private data class TimedWord(val startMs: Long, val endMs: Long, val text: String)

    /** A silence this long between words starts a new caption. */
    const val GAP_NEW_CUE_MS = 600L

    /**
     * Groups timed words into caption-sized cues whose in/out boundaries are
     * the first word's start and the last word's end — no rounding to phrase
     * chunks.
     *
     * The whisper bridge now emits one segment per word (token timestamps +
     * max_len=1), so those boundaries are the model's own word times. A
     * multi-word segment — output from an older bridge or another engine —
     * still works: its words are interpolated linearly across the segment,
     * which is exactly the old behaviour, only as a fallback instead of the
     * rule.
     */
    fun segment(segments: List<Segment>, maxWords: Int): List<SubtitleCue> {
        val words = ArrayList<TimedWord>()
        for (seg in segments) {
            val ws = seg.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            when {
                ws.isEmpty() -> Unit
                ws.size == 1 -> words.add(TimedWord(seg.startMs, seg.endMs, ws[0]))
                else -> {
                    val dur = (seg.endMs - seg.startMs).coerceAtLeast(1)
                    ws.forEachIndexed { i, w ->
                        words.add(
                            TimedWord(
                                startMs = seg.startMs + dur * i / ws.size,
                                endMs = seg.startMs + dur * (i + 1) / ws.size,
                                text = w,
                            )
                        )
                    }
                }
            }
        }

        val cues = ArrayList<SubtitleCue>()
        var id = 1L
        val buffer = ArrayList<TimedWord>()

        fun flush() {
            if (buffer.isEmpty()) return
            cues.add(
                SubtitleCue(
                    id = id++,
                    startMs = buffer.first().startMs,
                    endMs = buffer.last().endMs,
                    text = buffer.joinToString(" ") { it.text },
                )
            )
            buffer.clear()
        }

        val limit = maxWords.coerceAtLeast(1)
        for (word in words) {
            if (buffer.isNotEmpty() &&
                (buffer.size >= limit || word.startMs - buffer.last().endMs > GAP_NEW_CUE_MS)
            ) flush()
            buffer.add(word)
        }
        flush()

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
