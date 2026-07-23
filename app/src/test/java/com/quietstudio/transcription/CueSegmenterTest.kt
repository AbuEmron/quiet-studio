package com.quietstudio.transcription

import com.quietstudio.transcription.CueSegmenter.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the tight-sync behaviour: with the whisper bridge now emitting one
 * segment per word, caption in/out boundaries must be the model's own word
 * times — never interpolated chunk fractions.
 */
class CueSegmenterTest {

    /** Word-level segments as the bridge emits them (token_timestamps + max_len=1). */
    private fun word(startMs: Long, endMs: Long, text: String) = Segment(startMs, endMs, text)

    @Test
    fun `cue boundaries are the exact word times, not rounded chunks`() {
        // Five words with deliberately uneven, non-chunk-aligned times.
        val words = listOf(
            word(120, 410, "make"),
            word(430, 700, "the"),
            word(715, 1180, "decision"),
            word(1290, 1600, "every"),
            word(1610, 2205, "morning"),
        )

        val cues = CueSegmenter.segment(words, maxWords = 3)

        assertEquals(2, cues.size)
        // First cue: exactly first word's start .. third word's end.
        assertEquals(120, cues[0].startMs)
        assertEquals(1180, cues[0].endMs)
        assertEquals("make the decision", cues[0].text)
        // Second cue: exactly fourth word's start .. fifth word's end.
        assertEquals(1290, cues[1].startMs)
        assertEquals(2205, cues[1].endMs)
        assertEquals("every morning", cues[1].text)
    }

    @Test
    fun `a silence gap starts a new caption even under the word limit`() {
        val words = listOf(
            word(0, 300, "hello"),
            word(320, 600, "there"),
            // 900ms of silence — longer than GAP_NEW_CUE_MS.
            word(1500, 1800, "welcome"),
            word(1820, 2100, "back"),
        )

        val cues = CueSegmenter.segment(words, maxWords = 5)

        assertEquals(2, cues.size)
        assertEquals("hello there", cues[0].text)
        assertEquals(600, cues[0].endMs)     // caption drops at the last word's end…
        assertEquals(1500, cues[1].startMs)  // …and the next appears with its first word
        assertEquals("welcome back", cues[1].text)
    }

    @Test
    fun `the old phrase-level input still works as an interpolated fallback`() {
        // One multi-word segment, as an older bridge or another engine emits.
        val phrase = Segment(1000, 4000, "one two three four five six")

        val cues = CueSegmenter.segment(listOf(phrase), maxWords = 3)

        assertEquals(2, cues.size)
        assertEquals("one two three", cues[0].text)
        assertEquals("four five six", cues[1].text)
        // Interpolated boundaries: halfway through the 3s segment.
        assertEquals(1000, cues[0].startMs)
        assertEquals(2500, cues[0].endMs)
        assertEquals(2500, cues[1].startMs)
        assertEquals(4000, cues[1].endMs)
    }

    @Test
    fun `very short cues get a minimum display time without overlapping the next`() {
        val words = listOf(
            word(0, 100, "hi"),          // 100ms — below the 350ms floor
            word(2000, 2400, "there"),
        )

        val cues = CueSegmenter.segment(words, maxWords = 1)

        assertEquals(2, cues.size)
        assertEquals(350, cues[0].endMs)               // stretched to the floor…
        assertTrue(cues[0].endMs <= cues[1].startMs)   // …but never into the next cue
    }

    @Test
    fun `empty and blank segments are ignored`() {
        val cues = CueSegmenter.segment(
            listOf(word(0, 10, "   "), Segment(20, 30, "")),
            maxWords = 3,
        )
        assertTrue(cues.isEmpty())
    }
}
