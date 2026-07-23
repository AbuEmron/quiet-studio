package com.quietstudio.feature.templates

import com.quietstudio.core.model.BackgroundKind
import com.quietstudio.core.model.ExportConfig
import com.quietstudio.core.model.MotionEffect
import com.quietstudio.core.model.MusicSelection
import com.quietstudio.core.model.SubtitleAnimation
import com.quietstudio.core.model.SubtitlePosition
import com.quietstudio.core.model.SubtitleStyle
import com.quietstudio.core.model.TemplateContent
import com.quietstudio.core.model.VisualConfig

data class BuiltInTemplate(
    val id: String,
    val name: String,
    val tagline: String,
    val content: TemplateContent,
)

/** The five signature looks from the design: one tap → same style, new voice. */
val BUILT_IN_TEMPLATES: List<BuiltInTemplate> = listOf(
    BuiltInTemplate(
        "cinematic", "Cinematic", "Clean cinematic",
        TemplateContent(
            subtitleStyle = SubtitleStyle(
                fontId = "poppins_bold", sizeSp = 27f, highlightEveryNthWord = 1,
                position = SubtitlePosition.CENTER.name, animation = SubtitleAnimation.FADE.name,
                backgroundPill = true, backgroundOpacity = 0.8f,
            ),
            visual = VisualConfig(
                kind = BackgroundKind.MOTION.name,
                gradientColors = listOf(0xFF2A1E4F, 0xFF8A4C63, 0xFF12101C),
                motion = MotionEffect.SLOW_ZOOM.name,
                filmGrain = 0.35f, vignette = 0.5f,
            ),
            music = MusicSelection(trackId = "qs108", volume = 0.5f),
            export = ExportConfig(fps = 30),
        ),
    ),
    BuiltInTemplate(
        "minimal", "Minimal", "Clean & simple",
        TemplateContent(
            subtitleStyle = SubtitleStyle(
                fontId = "poppins_semibold", sizeSp = 24f,
                position = SubtitlePosition.CENTER.name, animation = SubtitleAnimation.NONE.name,
                backgroundPill = false, shadow = true, highlightEveryNthWord = 0,
            ),
            visual = VisualConfig(
                kind = BackgroundKind.SOLID.name,
                gradientColors = listOf(0xFF101014, 0xFF101014, 0xFF101014),
                filmGrain = 0.1f, vignette = 0.2f,
            ),
            music = MusicSelection(trackId = "qs128", volume = 0.45f),
            export = ExportConfig(),
        ),
    ),
    BuiltInTemplate(
        "moody", "Moody", "Dark & dramatic",
        TemplateContent(
            subtitleStyle = SubtitleStyle(
                fontId = "poppins_bold", sizeSp = 29f, allCaps = true,
                position = SubtitlePosition.BOTTOM.name, animation = SubtitleAnimation.POP.name,
                backgroundPill = false, outlineMode = "BOLD", highlightEveryNthWord = 1,
                highlightColorArgb = 0xFF9D7DFF,
            ),
            visual = VisualConfig(
                kind = BackgroundKind.MOTION.name,
                gradientColors = listOf(0xFF0B0B12, 0xFF31184A, 0xFF060609),
                motion = MotionEffect.DRIFT.name,
                filmGrain = 0.5f, vignette = 0.65f,
            ),
            music = MusicSelection(trackId = "qs120", volume = 0.5f),
            export = ExportConfig(),
        ),
    ),
    BuiltInTemplate(
        "vintage", "Vintage", "Film aesthetic",
        TemplateContent(
            subtitleStyle = SubtitleStyle(
                fontId = "serif", sizeSp = 25f,
                position = SubtitlePosition.BOTTOM.name, animation = SubtitleAnimation.TYPEWRITER.name,
                backgroundPill = false, shadow = true, highlightEveryNthWord = 0,
                colorArgb = 0xFFF2E6D8,
            ),
            visual = VisualConfig(
                kind = BackgroundKind.GRADIENT.name,
                gradientColors = listOf(0xFF1C1410, 0xFF4C3520, 0xFF120D08),
                gradientAnimated = true,
                filmGrain = 0.7f, vignette = 0.55f,
            ),
            music = MusicSelection(trackId = "qs114", volume = 0.5f),
            export = ExportConfig(fps = 30),
        ),
    ),
    BuiltInTemplate(
        "nature", "Nature", "Organic feel",
        TemplateContent(
            subtitleStyle = SubtitleStyle(
                fontId = "poppins_medium", sizeSp = 25f,
                position = SubtitlePosition.CENTER.name, animation = SubtitleAnimation.SLIDE_UP.name,
                backgroundPill = true, backgroundOpacity = 0.6f, highlightEveryNthWord = 1,
                highlightColorArgb = 0xFF84CC16,
            ),
            visual = VisualConfig(
                kind = BackgroundKind.PARTICLES.name,
                gradientColors = listOf(0xFF0E1210, 0xFF29452E, 0xFF080B08),
                particleColorArgb = 0xFFB8E986, particleDensity = 0.4f,
                filmGrain = 0.2f, vignette = 0.4f,
            ),
            music = MusicSelection(trackId = "qs124", volume = 0.55f),
            export = ExportConfig(),
        ),
    ),
)
