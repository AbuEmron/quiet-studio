package com.quietstudio.transcription

import com.quietstudio.transcription.whisper.WhisperModelManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the fast-default decision behind the auto-subtitle hang fix: the Base
 * model is the recommended, first-offered model so new users get a run that
 * finishes quickly, while turbo stays available as a slower high-accuracy pick.
 */
class ModelCatalogTest {

    @Test
    fun `base is the recommended, first-offered model`() {
        val first = WhisperModelManager.CATALOG.first()
        assertEquals("base", first.id)
        assertTrue("base must be recommended", first.recommended)
    }

    @Test
    fun `turbo is offered but not the default and is flagged slower`() {
        val turbo = WhisperModelManager.CATALOG.first { it.id == "turbo" }
        assertFalse("turbo must not be the default", turbo.recommended)
        assertTrue("turbo note should warn about speed", turbo.note.contains("slower", ignoreCase = true))
        assertTrue("turbo is the larger model", turbo.approxSizeMb > WhisperModelManager.BASE.approxSizeMb)
    }

    @Test
    fun `exactly one model is recommended`() {
        assertEquals(1, WhisperModelManager.CATALOG.count { it.recommended })
    }

    @Test
    fun `every catalog model has a real download url and filename`() {
        WhisperModelManager.CATALOG.forEach {
            assertTrue(it.url.startsWith("https://"))
            assertTrue(it.fileName.startsWith("ggml-") && it.fileName.endsWith(".bin"))
            assertTrue(it.approxSizeMb > 0)
        }
    }
}
