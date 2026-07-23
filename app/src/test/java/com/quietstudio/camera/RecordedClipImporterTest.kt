package com.quietstudio.camera

import com.quietstudio.feature.camera.RecordedClipImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure float→PCM16 conversion used when extracting a recorded
 * clip's audio to the narration WAV: full-scale maps correctly and out-of-range
 * input is clamped rather than wrapping to loud noise.
 */
class RecordedClipImporterTest {

    @Test
    fun `zero maps to silence`() {
        assertEquals(0.toShort(), RecordedClipImporter.pcm16(0f))
    }

    @Test
    fun `full scale maps near the 16-bit extremes`() {
        assertEquals(32767.toShort(), RecordedClipImporter.pcm16(1f))
        assertEquals((-32767).toShort(), RecordedClipImporter.pcm16(-1f))
    }

    @Test
    fun `out of range input is clamped, never wraps`() {
        // Without the clamp, 2f * 32767 overflows Int→Short into a small/negative
        // value — a loud click. Clamping keeps it pinned to the rail.
        assertEquals(32767.toShort(), RecordedClipImporter.pcm16(2f))
        assertEquals((-32767).toShort(), RecordedClipImporter.pcm16(-9f))
    }

    @Test
    fun `mid levels scale linearly`() {
        val half = RecordedClipImporter.pcm16(0.5f).toInt()
        assertTrue("expected ~16383, got $half", half in 16000..16600)
    }
}
