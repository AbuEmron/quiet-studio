package com.quietstudio.core.media.enhance

import com.quietstudio.core.model.EnhanceSettings
import kotlin.math.max

/**
 * The pure math behind the one-tap "make professional" grade.
 *
 * A look plus the footage's auto-analysis reduce to four numbers — exposure,
 * contrast, saturation, warmth — that drive BOTH the preview approximation
 * (an Android ColorMatrix) and the burned-in export (Media3 RgbAdjustment +
 * Contrast + HslAdjustment). Keeping the numbers here, framework-free, means
 * preview and export are driven by one source of truth and this is unit-tested.
 */
object EnhanceGrade {

    /**
     * @param exposure   uniform brightness gain (1 = neutral)
     * @param contrast   Media3 Contrast value in -1..1 (0 = neutral)
     * @param saturation HSL saturation delta in -1..1 (0 = neutral)
     * @param warmth     red-up / blue-down amount, roughly -0.3..0.3 (0 = neutral)
     */
    data class Levels(
        val exposure: Float,
        val contrast: Float,
        val saturation: Float,
        val warmth: Float,
    )

    /** Base look before auto-correction. */
    fun baseLook(look: String): Levels = when (look) {
        "WARM" -> Levels(exposure = 1.04f, contrast = 0.12f, saturation = 0.18f, warmth = 0.14f)
        "NATURAL" -> Levels(exposure = 1.02f, contrast = 0.08f, saturation = 0.06f, warmth = 0.02f)
        else -> Levels(exposure = 1.03f, contrast = 0.18f, saturation = 0.12f, warmth = 0.06f) // CINEMATIC
    }

    /**
     * Auto exposure + white balance from a frame's average linear RGB (0..1).
     * Nudges mid-luminance toward [TARGET_LUMA] and neutralises a colour cast,
     * both gently and clamped so it never wrecks a deliberately-styled shot.
     */
    fun autoLevels(avgR: Float, avgG: Float, avgB: Float): Pair<Float, Float> {
        val luma = (0.299f * avgR + 0.587f * avgG + 0.114f * avgB).coerceIn(0.001f, 1f)
        val exposure = (TARGET_LUMA / luma).coerceIn(0.75f, 1.4f)
        // Warmth: if blue outweighs red the shot is cool → warm it, and vice versa.
        val cast = (avgB - avgR)
        val warmth = (cast * 0.5f).coerceIn(-0.15f, 0.15f)
        return exposure to warmth
    }

    /** Combine a look with the stored auto-correction. */
    fun levels(s: EnhanceSettings): Levels {
        val b = baseLook(s.look)
        return b.copy(
            exposure = (b.exposure * s.autoExposure).coerceIn(0.6f, 1.8f),
            warmth = (b.warmth + s.autoWarmth).coerceIn(-0.3f, 0.3f),
        )
    }

    /** Per-channel scale for exposure + warmth (used by preview and export). */
    fun channelScales(l: Levels): FloatArray {
        val e = l.exposure
        val w = l.warmth
        return floatArrayOf(
            (e * (1f + w)),   // red
            (e * (1f + w * 0.1f)), // green (mostly untouched)
            (e * (1f - w)),   // blue
        )
    }

    /**
     * A 4x5 Android-style colour matrix (row-major) approximating the grade for
     * the preview: per-channel scale (exposure+warmth), a saturation mix, and a
     * contrast pivot around mid-grey. Returned as a plain FloatArray so it is
     * testable without the Android framework.
     */
    fun colorMatrix(l: Levels): FloatArray {
        val s = channelScales(l)
        // saturation weights (Rec.601 luma)
        val sat = (1f + l.saturation).coerceIn(0f, 2f)
        val lr = 0.299f; val lg = 0.587f; val lb = 0.114f
        val sr = (1 - sat) * lr
        val sg = (1 - sat) * lg
        val sb = (1 - sat) * lb

        // contrast: out = (in - 0.5) * c + 0.5, folded into scale + offset (0..255)
        val c = 1f + l.contrast
        val off = (0.5f - 0.5f * c) * 255f

        // Saturation mix, then per-channel scale (exposure+warmth), then contrast.
        val m = floatArrayOf(
            (sr + sat) * s[0] * c, sg * s[0] * c, sb * s[0] * c, 0f, off,
            sr * s[1] * c, (sg + sat) * s[1] * c, sb * s[1] * c, 0f, off,
            sr * s[2] * c, sg * s[2] * c, (sb + sat) * s[2] * c, 0f, off,
            0f, 0f, 0f, 1f, 0f,
        )
        return m
    }

    const val TARGET_LUMA = 0.46f

    /** 2.39:1 cinematic letterbox: fraction of height for EACH bar. */
    fun letterboxBarFraction(frameW: Int, frameH: Int): Float {
        if (frameW <= 0 || frameH <= 0) return 0f
        val targetH = frameW / 2.39f
        if (targetH >= frameH) return 0f
        return max(0f, (frameH - targetH) / (2f * frameH))
    }
}
