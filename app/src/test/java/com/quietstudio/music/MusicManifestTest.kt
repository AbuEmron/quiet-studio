package com.quietstudio.music

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates the bundled music manifest against the app's parser and the actual
 * asset files on disk, so a mis-generated manifest or a missing MP3 fails CI
 * instead of shipping a silent or crashing track.
 */
class MusicManifestTest {

    @Serializable
    private data class Manifest(val version: Int, val tracks: List<Track>)

    @Serializable
    private data class Track(
        val id: String,
        val title: String,
        val mood: String,
        val tags: List<String> = emptyList(),
        val bpm: Int,
        val key: String,
        val energy: Int,
        val durationSec: Double,
        val file: String,
        val loop: Boolean = true,
        val instruments: List<String> = emptyList(),
    )

    private val assetsDir = File("src/main/assets/music")

    private fun manifest(): Manifest {
        val text = File(assetsDir, "manifest.json").readText()
        return Json { ignoreUnknownKeys = true }.decodeFromString(text)
    }

    @Test
    fun `manifest parses and holds sixty tracks`() {
        assertEquals(60, manifest().tracks.size)
    }

    @Test
    fun `every track points at an mp3 that exists and is non-trivial`() {
        manifest().tracks.forEach { t ->
            assertTrue("${t.id} file must live under music/", t.file.startsWith("music/"))
            val mp3 = File(assetsDir.parentFile, t.file)
            assertTrue("missing asset ${t.file}", mp3.exists())
            assertTrue("${t.file} is suspiciously small", mp3.length() > 50_000)
        }
    }

    @Test
    fun `ids are unique and every track has a title, key and duration`() {
        val tracks = manifest().tracks
        assertEquals(tracks.size, tracks.map { it.id }.toSet().size)
        tracks.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.key.isNotBlank())
            assertTrue("${it.id} has no duration", it.durationSec > 0)
            assertTrue("${it.id} energy out of range", it.energy in 1..5)
        }
    }

    @Test
    fun `tracks cover the four genres, fifteen each`() {
        val byMood = manifest().tracks.groupBy { it.mood }
        assertEquals(setOf("Warm Lo-fi", "Rainy Lo-fi", "Funk / Space-disco", "Jazz Trio"), byMood.keys)
        byMood.values.forEach { assertEquals(15, it.size) }
    }
}
