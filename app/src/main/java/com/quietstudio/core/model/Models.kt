package com.quietstudio.core.model

import kotlinx.serialization.Serializable

/* ---------------------------------------------------------------------------
 * Quiet Studio domain model.
 *
 * Everything that describes a project is a plain serializable value object.
 * The full editable state of a project serializes to JSON (stored in Room and
 * as autosave snapshots), which is what makes templates, version history and
 * future cloud sync possible without schema surgery.
 * ------------------------------------------------------------------------ */

@Serializable
data class SubtitleCue(
    val id: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

enum class SubtitlePosition { TOP, CENTER, BOTTOM }

enum class SubtitleAnimation { NONE, FADE, POP, SLIDE_UP, KARAOKE, TYPEWRITER }

@Serializable
data class SubtitleStyle(
    val fontId: String = "poppins_bold",
    val sizeSp: Float = 26f,
    val colorArgb: Long = 0xFFFFFFFF,
    /** Accent color for the emphasized word (mock: gold on "decision"). */
    val highlightColorArgb: Long = 0xFFF5C044,
    val highlightEveryNthWord: Int = 0,     // 0 = off
    val position: String = SubtitlePosition.CENTER.name,
    val animation: String = SubtitleAnimation.FADE.name,
    val shadow: Boolean = true,
    val shadowRadius: Float = 6f,
    /** "NONE" | "LIGHT" | "BOLD" */
    val outlineMode: String = "NONE",
    val outlineColorArgb: Long = 0xFF000000,
    val backgroundPill: Boolean = true,
    val backgroundOpacity: Float = 0.8f,
    val marginXPct: Float = 8f,
    val marginYPct: Float = 8f,
    val allCaps: Boolean = false,
    val maxWordsPerLine: Int = 5,
    /**
     * Free caption placement: the caption block's centre as fractions of the
     * frame (0..1 each). Negative means "not set", and the legacy [position]
     * enum drives layout instead — so projects saved before this field
     * existed keep their captions exactly where they were.
     */
    val posX: Float = -1f,
    val posY: Float = -1f,
    /** Line justification within the caption block: LEFT | CENTER | RIGHT. */
    val justify: String = "CENTER",
) {
    val hasCustomPosition: Boolean get() = posX in 0f..1f && posY in 0f..1f

    /**
     * Where the block's centre sits, custom or derived from the legacy enum.
     * Single source of truth used by the drag UI, the preset chips, the
     * preview painter and the export painter — they cannot disagree.
     */
    fun anchor(): Pair<Float, Float> =
        if (hasCustomPosition) posX to posY
        else 0.5f to when (
            runCatching { SubtitlePosition.valueOf(position) }.getOrDefault(SubtitlePosition.CENTER)
        ) {
            SubtitlePosition.TOP -> ANCHOR_TOP_Y
            SubtitlePosition.CENTER -> 0.5f
            SubtitlePosition.BOTTOM -> ANCHOR_LOWER_THIRD_Y
        }

    companion object {
        const val ANCHOR_TOP_Y = 0.18f
        const val ANCHOR_LOWER_THIRD_Y = 0.78f

        /** How close to the frame edge the block centre may be dragged. */
        const val POS_MIN = 0.05f
        const val POS_MAX = 0.95f
    }
}

/* ------------------------------- visuals -------------------------------- */

enum class BackgroundKind { SOLID, GRADIENT, IMAGE, VIDEO, PARTICLES, MOTION, SCENERY, ANIMATED }

/** Animated scenery themes. AUTO picks a theme matching the music's mood. */
object SceneryThemes {
    const val AUTO = "AUTO"
    val ALL = listOf("MEADOW", "DUSK", "NIGHT", "RAIN", "COAST", "SNOW")

    fun forMood(mood: String?): String = when (mood) {
        "Morning Energy", "Hopeful", "Uplifting", "Inspirational" -> "MEADOW"
        "Late Night", "Deep Thinking" -> "NIGHT"
        "Deep Focus", "Creative" -> "RAIN"
        "Calm", "Peaceful", "Documentary" -> "COAST"
        else -> "DUSK"
    }
}

enum class MotionEffect { NONE, SLOW_ZOOM, KEN_BURNS, PARALLAX, DRIFT }

@Serializable
data class VisualConfig(
    val kind: String = BackgroundKind.GRADIENT.name,
    /** file uri for IMAGE / VIDEO kinds */
    val sourceUri: String? = null,
    val gradientColors: List<Long> = listOf(0xFF10131C, 0xFF2C2237, 0xFF0B0B0F),
    val gradientAngleDeg: Float = 30f,
    val gradientAnimated: Boolean = true,
    val particleColorArgb: Long = 0xFFE8C9A0,
    val particleDensity: Float = 0.5f,
    val motion: String = MotionEffect.SLOW_ZOOM.name,
    val motionIntensity: Float = 0.5f,
    val filmGrain: Float = 0.25f,
    val vignette: Float = 0.35f,
    /** SCENERY kind: theme name or AUTO (match music mood). */
    val sceneryTheme: String = SceneryThemes.AUTO,
    /** SCENERY kind: 0 = derive per project, so each video gets its own scene. */
    val scenerySeed: Long = 0,
) {
    /** Resolves AUTO theme + per-project seed at render time. */
    fun resolvedScenery(musicMood: String?, projectSeed: Long): VisualConfig {
        if (kind != BackgroundKind.SCENERY.name) return this
        val theme = if (sceneryTheme == SceneryThemes.AUTO) SceneryThemes.forMood(musicMood) else sceneryTheme
        val seed = if (scenerySeed != 0L) scenerySeed else (projectSeed.takeIf { it != 0L } ?: 77L)
        return copy(sceneryTheme = theme, scenerySeed = seed)
    }
}

/** A saved, reusable look (background + subtitle style), aka "visual pack". */
@Serializable
data class VisualPack(
    val id: String,
    val name: String,
    val visual: VisualConfig,
    val subtitleStyle: SubtitleStyle,
)

/* -------------------------------- music --------------------------------- */

@Serializable
data class MusicTrack(
    val id: String,
    val title: String,
    val mood: String,
    val tags: List<String>,
    val bpm: Int,
    val key: String,
    val energy: Int,             // 1..5
    val durationSec: Double,
    val file: String,            // asset path or absolute file path for imports
    val loop: Boolean = true,
    val instruments: List<String> = emptyList(),
    val isImported: Boolean = false,
)

@Serializable
data class MusicSelection(
    val trackId: String? = null,
    val volume: Float = 0.55f,
    val duckingDb: Float = -12f,       // gain applied under narration
    val duckAttackMs: Int = 120,
    val duckReleaseMs: Int = 450,
    val fadeInMs: Int = 1200,
    val fadeOutMs: Int = 1800,
)

/* -------------------------------- audio --------------------------------- */

@Serializable
data class NarrationInfo(
    val wavPath: String? = null,
    val durationMs: Long = 0,
    val processed: Boolean = false,
)

/* -------------------------------- export -------------------------------- */

enum class Resolution(val width: Int, val height: Int, val label: String) {
    FHD(1080, 1920, "1080 × 1920"),
    UHD(2160, 3840, "4K · 2160 × 3840"),
}

enum class Codec(val label: String) { H264("H.264"), HEVC("HEVC") }

@Serializable
data class ExportConfig(
    val resolution: String = Resolution.FHD.name,
    val fps: Int = 30,
    val codec: String = Codec.H264.name,
    val bitrateMbps: Int = 12,
)

enum class ExportState { QUEUED, RENDERING, DONE, FAILED, CANCELLED }

/* ------------------------------- project -------------------------------- */

/** The complete editable state of one video project. */
@Serializable
data class ProjectContent(
    val narration: NarrationInfo = NarrationInfo(),
    val cues: List<SubtitleCue> = emptyList(),
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val visual: VisualConfig = VisualConfig(),
    val music: MusicSelection = MusicSelection(),
    val export: ExportConfig = ExportConfig(),
)

@Serializable
data class TemplateContent(
    val subtitleStyle: SubtitleStyle,
    val visual: VisualConfig,
    val music: MusicSelection,
    val export: ExportConfig,
)
