package com.quietstudio.core.media.scenes

/**
 * Data-driven scene descriptions for the animated scenery engine.
 *
 * A scene is an ordered stack of [Layer]s, painted back to front by
 * [SpecSceneRenderer]. Authoring a new scene is a [SceneSpec] literal in
 * [SceneCatalog] — no new drawing code unless it needs a genuinely new layer
 * type. Every layer's motion is quantized to whole cycles over the video's
 * duration, so every scene loops seamlessly by construction, exactly like the
 * original six.
 *
 * All imagery is original and procedural — gradients, silhouettes, particles,
 * light. Nothing is sampled from or modelled on any studio's work; the specs
 * only chase the same feelings: painted skies, quiet horizons, neon nights,
 * big cosmos.
 */

enum class SceneGroup(val label: String) {
    PASTORAL("Pastoral"),
    ELECTRIC("Electric"),
    COSMIC("Cosmic"),
    NIGHTLIFE("Night"),
}

data class SceneSpec(
    /** Stable id stored in VisualConfig.sceneryTheme. Never rename. */
    val id: String,
    val name: String,
    val group: SceneGroup,
    val layers: List<Layer>,
)

sealed interface Layer

/** Vertical sky gradient, top to bottom. Colors are ARGB ints. */
data class Sky(
    val colors: List<Int>,
    /** 0..1 gradient stops; null = evenly spread. */
    val stops: List<Float>? = null,
    /** Fraction of frame height the gradient spans (rest holds last color). */
    val extent: Float = 0.85f,
) : Layer

enum class CelestialKind { SUN, MOON, TWIN_SUNS }

/** Sun, moon or twin suns with a soft radial glow and a slow breathing bob. */
data class Celestial(
    val kind: CelestialKind,
    val x: Float,
    val y: Float,
    val radius: Float,          // fraction of frame width
    val color: Int,
    val glow: Int,
    val glowScale: Float = 3.2f,
) : Layer

/** Soft drifting cloud puffs with seamless horizontal wrap. */
data class Clouds(
    val color: Int,
    val count: Int = 5,
    val bandTop: Float = 0.06f,
    val bandBottom: Float = 0.34f,
    val alpha: Int = 200,
    /** Approximate wraps per minute — quantized to whole wraps per loop. */
    val wrapsPerMin: Float = 0.8f,
    val scale: Float = 1f,
) : Layer

/** Dark drifting blotches over the terrain — cloud shadows crossing hills. */
data class CloudShadows(
    val color: Int = 0x2A143020,
    val count: Int = 3,
    val bandTop: Float = 0.45f,
    val bandBottom: Float = 0.75f,
    val wrapsPerMin: Float = 0.6f,
) : Layer

enum class RidgeKind { HILLS, BLOCKS }

/** One silhouette band. [BLOCKS] renders seeded flat-topped shapes (rooftops, concrete). */
data class Ridge(
    val color: Int,
    val baseY: Float,
    val amp: Float,
    val detail: Int = 4,
    val kind: RidgeKind = RidgeKind.HILLS,
    /** Horizontal sway amount as a width fraction; quantized oscillation. */
    val sway: Float = 0.02f,
    val seedSalt: Long = 0L,
) : Layer

/** Horizontal water band with oscillating shimmer glints. */
data class WaterBand(
    val top: Float,
    val bottom: Float = 1f,
    val deep: Int,
    val light: Int,
    val glintColor: Int = 0x66FFFFFF,
    val glints: Int = 26,
) : Layer

/** Foreground grass fringe with wind sway. */
data class Grass(
    val color: Int,
    val sway: Float = 0.5f,
    val height: Float = 0.16f,
) : Layer

/** Horizontal fog/mist bands sliding slowly. */
data class Mist(
    val color: Int = 0x33E8EEF2,
    val bands: Int = 3,
    val bandTop: Float = 0.45f,
    val bandBottom: Float = 0.8f,
) : Layer

/** Translucent light shafts from a point — forest god-rays. */
data class GodRays(
    val x: Float,
    val y: Float,
    val color: Int = 0x2EFFF6C8,
    val rays: Int = 5,
) : Layer

/** Glowing lanterns hung along a gentle curve, swaying. */
data class Lanterns(
    val color: Int = 0xFFFFB65C.toInt(),
    val glow: Int = 0x55FFA843,
    val count: Int = 6,
    val pathY: Float = 0.58f,
) : Layer

/**
 * Utility poles and sagging catenary wires.
 * [perspective] draws them receding to a vanishing point instead of side-on.
 */
data class PowerLines(
    val color: Int = 0xFF10121A.toInt(),
    val poles: Int = 4,
    val horizonY: Float = 0.62f,
    val perspective: Boolean = false,
    val birds: Int = 0,
) : Layer

enum class ParticleKind { SEEDS, LEAVES, PETALS, FIREFLIES, RAIN, SNOW, STARS, GULLS, SHOOTING_STAR }

/** Seeded, wrapping particle field. Semantics per [ParticleKind]. */
data class Particles(
    val kind: ParticleKind,
    val color: Int,
    val count: Int,
    /** Motion scale relative to the kind's default. */
    val speed: Float = 1f,
    /** Size scale relative to the kind's default. */
    val size: Float = 1f,
    /** For STARS: fraction of frame height the field occupies. */
    val bandBottom: Float = 0.5f,
) : Layer
