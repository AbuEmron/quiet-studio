package com.quietstudio.core.media.scenes

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bundled animated-scene video library — looping cinemagraph MP4s hand-
 * animated from the app's anime art, played as muted seamless video
 * backgrounds.
 *
 * Loaded from assets/scenes/manifest.json, so growing the set (this is the
 * first 13 of a planned 40) is purely additive: drop the new mp4 into
 * assets/scenes/ and add a manifest row — no code change. Entirely on-device.
 */
@Singleton
class AnimatedScenes @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Serializable
    private data class ManifestEntry(
        val n: Int,
        val file: String,
        val title: String,
        val group: String = "scene",
    )

    private val json = Json { ignoreUnknownKeys = true }

    val scenes: List<AnimatedScene> by lazy {
        runCatching {
            val text = context.assets.open("scenes/manifest.json")
                .bufferedReader().use { it.readText() }
            json.decodeFromString<List<ManifestEntry>>(text).map {
                AnimatedScene(
                    id = "anim-%02d".format(it.n),
                    title = it.title,
                    group = it.group.replaceFirstChar { c -> c.uppercase() },
                    assetPath = "scenes/${it.file}",
                )
            }
        }.getOrElse { emptyList() }
    }

    val groups: List<String> by lazy { scenes.map { it.group }.distinct() }

    fun byId(id: String?): AnimatedScene? = scenes.firstOrNull { it.id == id }

    /** The URI stored on a project and read by ExoPlayer and the Transformer. */
    fun uriFor(scene: AnimatedScene): String = ASSET_PREFIX + scene.assetPath

    companion object {
        const val ASSET_PREFIX = "asset:///"

        /** True for a bundled-scene URI (vs a user-imported file/content video). */
        fun isAssetUri(uri: String?): Boolean = uri != null && uri.startsWith(ASSET_PREFIX)

        /** Path within assets/ for an asset:/// URI, else null. */
        fun assetPath(uri: String?): String? =
            if (isAssetUri(uri)) uri!!.removePrefix(ASSET_PREFIX) else null
    }
}

data class AnimatedScene(
    val id: String,
    val title: String,
    val group: String,
    val assetPath: String,
)
