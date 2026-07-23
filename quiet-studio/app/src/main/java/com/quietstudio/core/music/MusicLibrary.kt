package com.quietstudio.core.music

import android.content.Context
import com.quietstudio.core.model.MusicTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the bundled original-instrumental library from assets/music/manifest.json.
 *
 * Adding more tracks later — even hundreds — is purely additive: drop new
 * .ogg files + manifest entries into assets (or import at runtime into
 * app storage); the playback engine and every screen read through this
 * same catalog. No code changes required.
 */
@Singleton
class MusicLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Serializable
    private data class Manifest(val version: Int, val tracks: List<ManifestTrack>)

    @Serializable
    private data class ManifestTrack(
        val id: String,
        val title: String,
        val mood: String,
        val tags: List<String>,
        val bpm: Int,
        val key: String,
        val energy: Int,
        val durationSec: Double,
        val file: String,
        val loop: Boolean = true,
        val lufs: Double? = null,
        val instruments: List<String> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    val tracks: List<MusicTrack> by lazy {
        runCatching {
            val text = context.assets.open("music/manifest.json")
                .bufferedReader().use { it.readText() }
            json.decodeFromString<Manifest>(text).tracks.map {
                MusicTrack(
                    id = it.id, title = it.title, mood = it.mood, tags = it.tags,
                    bpm = it.bpm, key = it.key, energy = it.energy,
                    durationSec = it.durationSec, file = it.file, loop = it.loop,
                    instruments = it.instruments,
                )
            }
        }.getOrElse { emptyList() }
    }

    val moods: List<String> by lazy { tracks.map { it.mood }.distinct().sorted() }
    val allTags: List<String> by lazy { tracks.flatMap { it.tags }.distinct().sorted() }

    fun byId(id: String?): MusicTrack? = tracks.firstOrNull { it.id == id }

    fun search(
        query: String = "",
        mood: String? = null,
        tag: String? = null,
        bpmRange: IntRange? = null,
        energy: Int? = null,
        extra: List<MusicTrack> = emptyList(),
    ): List<MusicTrack> =
        (tracks + extra).filter { t ->
            (query.isBlank() || t.title.contains(query, true) ||
                t.mood.contains(query, true) || t.tags.any { it.contains(query, true) } ||
                t.instruments.any { it.contains(query, true) }) &&
                (mood == null || t.mood == mood) &&
                (tag == null || t.tags.contains(tag)) &&
                (bpmRange == null || t.bpm in bpmRange) &&
                (energy == null || t.energy == energy)
        }

    fun random(mood: String? = null): MusicTrack? =
        (if (mood == null) tracks else tracks.filter { it.mood == mood }).randomOrNull()
}
