package com.quietstudio.scenes

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the bundled animated-scene manifest against the real asset files,
 * so a mis-generated manifest or a missing/renamed mp4 fails CI instead of
 * shipping a black background.
 */
class AnimatedScenesManifestTest {

    @Serializable
    private data class Entry(val n: Int, val file: String, val title: String, val group: String = "scene")

    private val scenesDir = File("src/main/assets/scenes")

    private fun manifest(): List<Entry> {
        val text = File(scenesDir, "manifest.json").readText()
        return Json { ignoreUnknownKeys = true }.decodeFromString(text)
    }

    @Test
    fun `manifest parses and lists thirteen scenes`() {
        assertEquals(13, manifest().size)
    }

    @Test
    fun `every scene points at an mp4 that exists and is non-trivial`() {
        manifest().forEach { e ->
            assertTrue("${e.file} must be an mp4", e.file.endsWith(".mp4"))
            val mp4 = File(scenesDir, e.file)
            assertTrue("missing ${e.file}", mp4.exists())
            assertTrue("${e.file} is suspiciously small", mp4.length() > 100_000)
            assertTrue("${e.title} blank title", e.title.isNotBlank())
        }
    }

    @Test
    fun `scene numbers are unique and files are distinct`() {
        val m = manifest()
        assertEquals(m.size, m.map { it.n }.toSet().size)
        assertEquals(m.size, m.map { it.file }.toSet().size)
    }
}
