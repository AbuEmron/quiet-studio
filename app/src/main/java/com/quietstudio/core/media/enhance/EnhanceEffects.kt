package com.quietstudio.core.media.enhance

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import com.quietstudio.core.model.EnhanceSettings

/**
 * Builds the burned-in export grade from [EnhanceSettings] using Media3's
 * stable built-in effects, driven by the same [EnhanceGrade] levels the
 * preview uses. Letterbox bars and film grain are handled by the export
 * overlay, not here.
 */
@UnstableApi
object EnhanceEffects {

    fun build(settings: EnhanceSettings): List<Effect> {
        if (!settings.enabled) return emptyList()
        val l = EnhanceGrade.levels(settings)
        val scales = EnhanceGrade.channelScales(l)

        return buildList {
            // Exposure + white-balance as per-channel RGB scale.
            add(
                RgbAdjustment.Builder()
                    .setRedScale(scales[0])
                    .setGreenScale(scales[1])
                    .setBlueScale(scales[2])
                    .build()
            )
            if (kotlin.math.abs(l.contrast) > 0.001f) {
                add(Contrast(l.contrast.coerceIn(-1f, 1f)))
            }
            if (kotlin.math.abs(l.saturation) > 0.001f) {
                add(
                    HslAdjustment.Builder()
                        .adjustSaturation(l.saturation.coerceIn(-1f, 1f) * 100f)
                        .build()
                )
            }
        }
    }
}
