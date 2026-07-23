package com.quietstudio.core.media.scenes

import com.quietstudio.core.media.scenes.CelestialKind.MOON
import com.quietstudio.core.media.scenes.CelestialKind.SUN
import com.quietstudio.core.media.scenes.ParticleKind.FIREFLIES
import com.quietstudio.core.media.scenes.ParticleKind.GULLS
import com.quietstudio.core.media.scenes.ParticleKind.LEAVES
import com.quietstudio.core.media.scenes.ParticleKind.PETALS
import com.quietstudio.core.media.scenes.ParticleKind.SEEDS
import com.quietstudio.core.media.scenes.ParticleKind.SHOOTING_STAR
import com.quietstudio.core.media.scenes.ParticleKind.SNOW
import com.quietstudio.core.media.scenes.ParticleKind.STARS
import com.quietstudio.core.media.scenes.RidgeKind.BLOCKS

/**
 * The authored scene library — original, procedural, in the painted-anime
 * family: soft pastoral light, electric suburban skies, cosmos, neon nights.
 * No characters, no logos, no named places; every image is built from
 * universal elements (skies, hills, wires, water, weather, light).
 *
 * Grouped in waves; ids are stable and stored in projects — never rename one.
 */
object SceneCatalog {

    private fun c(v: Long) = v.toInt()

    /* ---------------------- Wave 1 · Pastoral (10) ----------------------- */

    private val ROLLING_HILLS = SceneSpec(
        id = "rolling-hills", name = "Rolling Hills", group = SceneGroup.PASTORAL,
        layers = listOf(
            Sky(listOf(c(0xFF6FB0DE), c(0xFFBFE0EA), c(0xFFF2EFD3))),
            Celestial(SUN, x = 0.72f, y = 0.20f, radius = 0.09f, color = c(0xFFFFF7DC), glow = c(0x66FFE9A8)),
            Clouds(color = c(0xFFFFFFFF), count = 6, alpha = 215, wrapsPerMin = 0.9f),
            Ridge(c(0xFF9CC08B), baseY = 0.52f, amp = 0.05f),
            Ridge(c(0xFF6EA25E), baseY = 0.63f, amp = 0.06f),
            CloudShadows(count = 3, bandTop = 0.52f, bandBottom = 0.74f),
            Ridge(c(0xFF477742), baseY = 0.76f, amp = 0.06f),
            Grass(c(0xFF2F5A38), sway = 0.6f),
            Particles(SEEDS, color = c(0xE8FFFBEA), count = 14),
        ),
    )

    private val TERRACE_GOLD = SceneSpec(
        id = "terrace-gold", name = "Terrace Gold", group = SceneGroup.PASTORAL,
        layers = listOf(
            Sky(listOf(c(0xFFE9B96A), c(0xFFF4D9A0), c(0xFFF9EFCB))),
            Celestial(SUN, x = 0.30f, y = 0.26f, radius = 0.115f, color = c(0xFFFFEFC2), glow = c(0x77FFC46A)),
            Clouds(color = c(0xFFFFE9C4), count = 4, alpha = 170, wrapsPerMin = 0.6f),
            Ridge(c(0xFFC9A85F), baseY = 0.48f, amp = 0.030f, detail = 3),
            WaterBand(top = 0.520f, bottom = 0.545f, deep = c(0xFFE0B45E), light = c(0xFFFBE7AE), glints = 12),
            Ridge(c(0xFFA98A48), baseY = 0.575f, amp = 0.028f, detail = 3),
            WaterBand(top = 0.640f, bottom = 0.665f, deep = c(0xFFD8A852), light = c(0xFFF6DE9E), glints = 12),
            Ridge(c(0xFF87683A), baseY = 0.700f, amp = 0.026f, detail = 3),
            WaterBand(top = 0.775f, bottom = 0.800f, deep = c(0xFFCB9A48), light = c(0xFFF2D592), glints = 10),
            Ridge(c(0xFF5E4629), baseY = 0.845f, amp = 0.030f, detail = 3),
            Particles(SEEDS, color = c(0xCCFFF3D0), count = 10),
        ),
    )

    private val RIVER_BEND = SceneSpec(
        id = "river-bend", name = "River Bend Dusk", group = SceneGroup.PASTORAL,
        layers = listOf(
            Sky(listOf(c(0xFF544276), c(0xFFB86F60), c(0xFFECA96B))),
            Celestial(SUN, x = 0.55f, y = 0.34f, radius = 0.10f, color = c(0xFFFFE0AC), glow = c(0x66FFAE62)),
            Clouds(color = c(0xFFE8C3AC), count = 4, alpha = 150, wrapsPerMin = 0.5f),
            Ridge(c(0xFF6E5070), baseY = 0.50f, amp = 0.045f),
            Ridge(c(0xFF4C3757), baseY = 0.60f, amp = 0.05f),
            WaterBand(top = 0.66f, bottom = 0.86f, deep = c(0xFF3A2C50), light = c(0xFFDF9C64), glintColor = c(0x88FFD9A0)),
            Ridge(c(0xFF2C1F3A), baseY = 0.86f, amp = 0.035f),
            Particles(LEAVES, color = c(0xCCE8B07A), count = 10),
        ),
    )

    private val BLOSSOM_DRIFT = SceneSpec(
        id = "blossom-drift", name = "Blossom Drift", group = SceneGroup.PASTORAL,
        layers = listOf(
            Sky(listOf(c(0xFF9CC5E8), c(0xFFD9E4EE), c(0xFFF6E7E4))),
            Celestial(SUN, x = 0.80f, y = 0.18f, radius = 0.08f, color = c(0xFFFFF9E8), glow = c(0x55FFE7C2)),
            Clouds(color = c(0xFFFFFFFF), count = 5, alpha = 190, wrapsPerMin = 0.7f),
            Ridge(c(0xFFC9A9BC), baseY = 0.55f, amp = 0.04f),
            Ridge(c(0xFF9D7E96), baseY = 0.66f, amp = 0.05f),
            Ridge(c(0xFF6E5570), baseY = 0.78f, amp = 0.05f),
            Grass(c(0xFF4E3D57), sway = 0.4f),
            Particles(PETALS, color = c(0xF2FFCFD8), count = 26),
        ),
    )

    private val SUMMER_FIELDS = SceneSpec(
        id = "summer-fields", name = "Summer Fields", group = SceneGroup.PASTORAL,
        layers = listOf(
            Sky(listOf(c(0xFF3F8FD2), c(0xFF8FC5E8), c(0xFFEAF4F2))),
            Celestial(SUN, x = 0.24f, y = 0.14f, radius = 0.085f, color = c(0xFFFFFBE6), glow = c(0x55FFF2B8)),
            Clouds(color = c(0xFFFFFFFF), count = 7, alpha = 235, wrapsPerMin = 1.1f, scale = 1.35f),
            Ridge(c(0xFF7FB56A), baseY = 0.62f, amp = 0.03f, detail = 3),
            CloudShadows(count = 4, bandTop = 0.62f, bandBottom = 0.85f),
            Ridge(c(0xFF579A4C), baseY = 0.74f, amp = 0.035f, detail = 3),
            Grass(c(0xFF3B7A3E), sway = 0.8f),
            Particles(SEEDS, color = c(0xDDFFFFFF), count = 16),
        ),
    )

    private val FOREST_LIGHT = SceneSpec(
        id = "forest-light", name = "Forest Light", group = SceneGroup.PASTORAL,
        layers = listOf(
            Sky(listOf(c(0xFFB9D9A8), c(0xFF7FAF70), c(0xFF3F6B44)), extent = 1f),
            GodRays(x = 0.68f, y = -0.08f, color = c(0x3AFFF6C4), rays = 6),
            Ridge(c(0xFF3E6B46), baseY = 0.42f, amp = 0.10f, detail = 5),
            Ridge(c(0xFF2C5236), baseY = 0.58f, amp = 0.10f, detail = 5),
            Mist(color = c(0x2EE9F2D8), bands = 2, bandTop = 0.55f, bandBottom = 0.7f),
            Ridge(c(0xFF1C3A26), baseY = 0.78f, amp = 0.08f, detail = 5),
            Particles(SEEDS, color = c(0xCCF6F2CE), count = 18),
        ),
    )

    private val GULL_CLIFFS = SceneSpec(
        id = "gull-cliffs", name = "Gull Cliffs", group = SceneGroup.PASTORAL,
        layers = listOf(
            Sky(listOf(c(0xFF8FC3E8), c(0xFFD8ECF4), c(0xFFF3E9CE))),
            Celestial(SUN, x = 0.38f, y = 0.18f, radius = 0.09f, color = c(0xFFFFF8E1), glow = c(0x55FFE9AE)),
            Clouds(color = c(0xFFFFFFFF), count = 5, alpha = 210, wrapsPerMin = 1.0f),
            WaterBand(top = 0.52f, bottom = 0.88f, deep = c(0xFF2F6B93), light = c(0xFF9FD0E4)),
            Ridge(c(0xFF6B7C6A), baseY = 0.46f, amp = 0.16f, detail = 5, seedSalt = 4L),
            Ridge(c(0xFF44573F), baseY = 0.80f, amp = 0.14f, detail = 5, seedSalt = 9L),
            Particles(GULLS, color = c(0xFF3A4448), count = 4),
        ),
    )

    private val WIND_OVER_GRASS = SceneSpec(
        id = "wind-grass", name = "Wind Over Grass", group = SceneGroup.PASTORAL,
        layers = listOf(
            Sky(listOf(c(0xFF7FB2D9), c(0xFFC5DEE0), c(0xFFF6E3B4)), extent = 0.7f),
            Clouds(color = c(0xFFFFFFFF), count = 6, alpha = 200, wrapsPerMin = 1.6f, bandBottom = 0.45f),
            Celestial(SUN, x = 0.86f, y = 0.10f, radius = 0.07f, color = c(0xFFFFF7DC), glow = c(0x55FFE9A8)),
            Ridge(c(0xFF7CA968), baseY = 0.70f, amp = 0.02f, detail = 2),
            Grass(c(0xFF4C8148), sway = 1.4f, height = 0.26f),
            Particles(SEEDS, color = c(0xE0FFFDEB), count = 22),
        ),
    )

    private val MISTY_MORNING = SceneSpec(
        id = "misty-valley", name = "Misty Morning", group = SceneGroup.PASTORAL,
        layers = listOf(
            Sky(listOf(c(0xFFC5CFDB), c(0xFFE3E7E4), c(0xFFF4EFE0))),
            Celestial(SUN, x = 0.62f, y = 0.22f, radius = 0.075f, color = c(0xFFFDF6E2), glow = c(0x3DFFEFC2)),
            Ridge(c(0xFFA9B4B5), baseY = 0.46f, amp = 0.06f),
            Mist(color = c(0x55EFF3EE), bands = 2, bandTop = 0.44f, bandBottom = 0.56f),
            Ridge(c(0xFF808F8B), baseY = 0.60f, amp = 0.06f),
            Mist(color = c(0x66ECF1EA), bands = 3, bandTop = 0.58f, bandBottom = 0.78f),
            Ridge(c(0xFF525F58), baseY = 0.78f, amp = 0.06f),
            Mist(color = c(0x77E9EFE6), bands = 2, bandTop = 0.80f, bandBottom = 0.92f),
        ),
    )

    private val LANTERN_PATH = SceneSpec(
        id = "lantern-path", name = "Lantern Path", group = SceneGroup.PASTORAL,
        layers = listOf(
            Sky(listOf(c(0xFF121A38), c(0xFF23305A), c(0xFF3A4470))),
            Particles(STARS, color = c(0xFFEFF3FF), count = 60, bandBottom = 0.45f),
            Celestial(MOON, x = 0.20f, y = 0.14f, radius = 0.06f, color = c(0xFFF2F4FA), glow = c(0x44708FD9)),
            Ridge(c(0xFF1C2544), baseY = 0.45f, amp = 0.09f, detail = 5),
            Ridge(c(0xFF131A33), baseY = 0.62f, amp = 0.10f, detail = 5),
            Lanterns(count = 6, pathY = 0.60f),
            Ridge(c(0xFF0B1122), baseY = 0.82f, amp = 0.06f),
            Particles(FIREFLIES, color = c(0xFFCBE896), count = 8),
        ),
    )

    /* ---------------------- Wave 1 · Electric (5) ------------------------ */

    private val WIRES_AT_SUNSET = SceneSpec(
        id = "wires-sunset", name = "Wires at Sunset", group = SceneGroup.ELECTRIC,
        layers = listOf(
            Sky(listOf(c(0xFF472F6B), c(0xFFB05471), c(0xFFF2914E), c(0xFFFFC96B)), extent = 0.9f),
            Celestial(SUN, x = 0.5f, y = 0.52f, radius = 0.13f, color = c(0xFFFFDA8C), glow = c(0x77FF9E4E)),
            Clouds(color = c(0xFFB27089), count = 3, alpha = 130, wrapsPerMin = 0.5f, bandBottom = 0.3f),
            Ridge(c(0xFF241833), baseY = 0.66f, amp = 0.020f, detail = 2, kind = BLOCKS),
            PowerLines(color = c(0xFF120B1C), poles = 4, horizonY = 0.62f, birds = 3),
            Ridge(c(0xFF150D22), baseY = 0.84f, amp = 0.02f, detail = 2),
        ),
    )

    private val OVERPASS = SceneSpec(
        id = "overpass", name = "Overpass", group = SceneGroup.ELECTRIC,
        layers = listOf(
            Sky(listOf(c(0xFF5B6C86), c(0xFF93A0AC), c(0xFFD9C9A8))),
            Clouds(color = c(0xFFC9CFD4), count = 4, alpha = 170, wrapsPerMin = 0.7f),
            Ridge(c(0xFF6E7987), baseY = 0.46f, amp = 0.015f, kind = BLOCKS, seedSalt = 3L),
            WaterBand(top = 0.56f, bottom = 0.76f, deep = c(0xFF46586A), light = c(0xFFABB4A9), glints = 18),
            Ridge(c(0xFF3E4653), baseY = 0.76f, amp = 0.010f, detail = 2, kind = BLOCKS, seedSalt = 8L),
            Ridge(c(0xFF272D38), baseY = 0.88f, amp = 0.015f, detail = 2, kind = BLOCKS, seedSalt = 12L),
            Particles(GULLS, color = c(0xFF3B424C), count = 2),
        ),
    )

    private val ROOFTOP_STATIC = SceneSpec(
        id = "rooftop-static", name = "Rooftop Static", group = SceneGroup.ELECTRIC,
        layers = listOf(
            Sky(listOf(c(0xFF0A0F26), c(0xFF1A2244), c(0xFF33305C))),
            Particles(STARS, color = c(0xFFEFF3FF), count = 90, bandBottom = 0.6f),
            Particles(SHOOTING_STAR, color = c(0xFFDCE6FF), count = 1),
            Celestial(MOON, x = 0.76f, y = 0.13f, radius = 0.055f, color = c(0xFFEFF3FF), glow = c(0x395E77C9)),
            Ridge(c(0xFF171B33), baseY = 0.62f, amp = 0.05f, kind = BLOCKS, seedSalt = 2L),
            PowerLines(color = c(0xFF0B0E1E), poles = 3, horizonY = 0.70f),
            Ridge(c(0xFF0B0E1E), baseY = 0.80f, amp = 0.06f, kind = BLOCKS, seedSalt = 7L),
        ),
    )

    private val BLUE_HOUR_BANK = SceneSpec(
        id = "blue-hour-bank", name = "Blue Hour Bank", group = SceneGroup.ELECTRIC,
        layers = listOf(
            Sky(listOf(c(0xFF1B2C55), c(0xFF32508A), c(0xFF6B85B4))),
            Particles(STARS, color = c(0xCCE8F0FF), count = 24, bandBottom = 0.3f),
            Clouds(color = c(0xFF3E5686), count = 3, alpha = 140, wrapsPerMin = 0.5f),
            Ridge(c(0xFF25355C), baseY = 0.52f, amp = 0.035f),
            WaterBand(top = 0.60f, bottom = 0.84f, deep = c(0xFF14213E), light = c(0xFF4A6BA4), glintColor = c(0x77BBD4FF)),
            Grass(c(0xFF101B33), sway = 0.5f, height = 0.20f),
            Particles(FIREFLIES, color = c(0xFFB9D9FF), count = 5),
        ),
    )

    private val VANISHING_LINE = SceneSpec(
        id = "vanishing-line", name = "Vanishing Line", group = SceneGroup.ELECTRIC,
        layers = listOf(
            Sky(listOf(c(0xFF6A5586), c(0xFFC98A76), c(0xFFF4C98A)), extent = 0.75f),
            Celestial(SUN, x = 0.30f, y = 0.47f, radius = 0.085f, color = c(0xFFFFE3AE), glow = c(0x66FFB066)),
            Clouds(color = c(0xFFD9A990), count = 3, alpha = 140, wrapsPerMin = 0.6f, bandBottom = 0.28f),
            Ridge(c(0xFF3A2E4E), baseY = 0.56f, amp = 0.015f, detail = 2),
            PowerLines(color = c(0xFF191225), poles = 5, horizonY = 0.56f, perspective = true, birds = 2),
            Ridge(c(0xFF241B36), baseY = 0.74f, amp = 0.015f, detail = 2),
            Grass(c(0xFF171025), sway = 0.5f, height = 0.14f),
        ),
    )

    /* ----------------------- Wave 2 · Cosmic (10) ------------------------ */

    private val STAR_DRIFT = SceneSpec(
        id = "star-drift", name = "Star Drift", group = SceneGroup.COSMIC,
        layers = listOf(
            Sky(listOf(c(0xFF05060F), c(0xFF0B0D22), c(0xFF141634)), extent = 1f),
            Particles(STARS, color = c(0xFFBFC8FF), count = 70, bandBottom = 1f, size = 0.8f),
            Particles(STARS, color = c(0xFFFFFFFF), count = 40, bandBottom = 1f, size = 1.4f),
            Nebula(listOf(c(0x33324C8A), c(0x2A6A3C7E)), cx = 0.6f, cy = 0.4f, spread = 0.8f, blobs = 5),
            Particles(SHOOTING_STAR, color = c(0xFFDCE6FF), count = 1),
        ),
    )

    private val NEBULA_DRIFT = SceneSpec(
        id = "nebula-drift", name = "Nebula Drift", group = SceneGroup.COSMIC,
        layers = listOf(
            Sky(listOf(c(0xFF0A0716), c(0xFF160B2A), c(0xFF0A0716)), extent = 1f),
            Nebula(listOf(c(0x55C0348A), c(0x4422B0A8), c(0x44E0559E)), cx = 0.45f, cy = 0.42f, spread = 0.9f, blobs = 8),
            Nebula(listOf(c(0x3320C4C4), c(0x33B03CA0)), cx = 0.7f, cy = 0.6f, spread = 0.6f, blobs = 5),
            Particles(STARS, color = c(0xFFFFFFFF), count = 80, bandBottom = 1f),
            Particles(STARS, color = c(0xFFFFD9F4), count = 20, bandBottom = 1f, size = 1.6f),
        ),
    )

    private val RING_RISE = SceneSpec(
        id = "ring-rise", name = "Ring Rise", group = SceneGroup.COSMIC,
        layers = listOf(
            Sky(listOf(c(0xFF0B0A1E), c(0xFF1E1636), c(0xFF3A2450)), extent = 0.9f),
            Particles(STARS, color = c(0xFFEFF0FF), count = 60, bandBottom = 0.7f),
            Nebula(listOf(c(0x2A7A3C9E)), cx = 0.3f, cy = 0.3f, spread = 0.5f, blobs = 3),
            Planet(
                x = 0.62f, y = 0.60f, radius = 0.26f,
                body = listOf(c(0xFFE0A867), c(0xFFB4703E), c(0xFF6E3C28)),
                ring = c(0xCCD8B98A), ringTilt = 0.30f, glow = c(0x33FFD9A0),
            ),
            Ridge(c(0xFF120E22), baseY = 0.86f, amp = 0.03f, detail = 3),
        ),
    )

    private val COMET_ROAD = SceneSpec(
        id = "comet-road", name = "Comet Road", group = SceneGroup.COSMIC,
        layers = listOf(
            Sky(listOf(c(0xFF040610), c(0xFF0A1030), c(0xFF122048)), extent = 1f),
            Particles(STARS, color = c(0xFFCAD4FF), count = 90, bandBottom = 1f),
            Nebula(listOf(c(0x2830708A)), cx = 0.5f, cy = 0.5f, spread = 1f, blobs = 4),
            Particles(SHOOTING_STAR, color = c(0xFFEAF2FF), count = 1),
            Particles(SHOOTING_STAR, color = c(0xFFBFD8FF), count = 1),
        ),
    )

    private val WARP_JUMP = SceneSpec(
        id = "warp-jump", name = "Warp Jump", group = SceneGroup.COSMIC,
        layers = listOf(
            Sky(listOf(c(0xFF05030E), c(0xFF0B0820), c(0xFF05030E)), extent = 1f),
            WarpStreaks(color = c(0xFFBFD0FF), cx = 0.5f, cy = 0.46f, count = 120),
            Nebula(listOf(c(0x33305CC0), c(0x2288308A)), cx = 0.5f, cy = 0.46f, spread = 0.4f, blobs = 3),
        ),
    )

    private val NEON_DINER = SceneSpec(
        id = "neon-diner", name = "Neon Diner", group = SceneGroup.COSMIC,
        layers = listOf(
            Sky(listOf(c(0xFF08061A), c(0xFF130B2E), c(0xFF1E1240)), extent = 0.9f),
            Particles(STARS, color = c(0xFFEFF0FF), count = 50, bandBottom = 0.55f),
            Planet(
                x = 0.16f, y = 0.22f, radius = 0.08f, hasRing = false,
                body = listOf(c(0xFF5E77C9), c(0xFF2E3E6E)), ring = 0, glow = c(0x33708FD9),
            ),
            Ridge(c(0xFF15112A), baseY = 0.70f, amp = 0.02f, detail = 2, kind = BLOCKS),
            LightBox(
                glow = c(0x66FF5AA8), x = 0.5f, y = 0.66f, w = 0.42f, h = 0.14f,
                face = listOf(c(0xFF3A1830), c(0xFF20101C)),
            ),
            Neon(color = c(0xFFFF5AA8), x = 0.5f, y = 0.60f, w = 0.30f, h = 0.020f),
            Neon(color = c(0xFF34E0D0), x = 0.5f, y = 0.635f, w = 0.20f, h = 0.014f),
            Reflections(listOf(c(0xFFFF5AA8), c(0xFF34E0D0)), top = 0.80f),
        ),
    )

    private val TWIN_SUN_DESERT = SceneSpec(
        id = "twin-sun-desert", name = "Twin Sun Desert", group = SceneGroup.COSMIC,
        layers = listOf(
            Sky(listOf(c(0xFFE9A96A), c(0xFFE8C48E), c(0xFFF2E0BC)), extent = 0.8f),
            Celestial(CelestialKind.TWIN_SUNS, x = 0.42f, y = 0.30f, radius = 0.085f, color = c(0xFFFFF0D2), glow = c(0x66FFC074)),
            Ridge(c(0xFFCB9A5E), baseY = 0.64f, amp = 0.05f, detail = 3),
            Ridge(c(0xFFA9773F), baseY = 0.76f, amp = 0.06f, detail = 3),
            Ridge(c(0xFF7E5730), baseY = 0.88f, amp = 0.05f, detail = 3),
            Particles(SEEDS, color = c(0x99FFE9C0), count = 10),
        ),
    )

    private val ASTEROID_DRIFT = SceneSpec(
        id = "asteroid-drift", name = "Asteroid Drift", group = SceneGroup.COSMIC,
        layers = listOf(
            Sky(listOf(c(0xFF06070F), c(0xFF0C0F24), c(0xFF10132C)), extent = 1f),
            Particles(STARS, color = c(0xFFD4DCFF), count = 70, bandBottom = 1f),
            Nebula(listOf(c(0x22506AA0)), cx = 0.4f, cy = 0.5f, spread = 0.8f, blobs = 3),
            Planet(
                x = 0.80f, y = 0.24f, radius = 0.10f, hasRing = false,
                body = listOf(c(0xFF8A6ABF), c(0xFF43305E)), ring = 0, glow = c(0x33A07CD9),
            ),
            CloudShadows(color = c(0x66101828), count = 5, bandTop = 0.45f, bandBottom = 0.8f, wrapsPerMin = 0.9f),
            Particles(SNOW, color = c(0xFF6A6E82), count = 20, size = 2.4f, speed = 0.5f),
        ),
    )

    private val AURORA_PLANET = SceneSpec(
        id = "aurora-planet", name = "Aurora Planet", group = SceneGroup.COSMIC,
        layers = listOf(
            Sky(listOf(c(0xFF030A10), c(0xFF06131C), c(0xFF0A1C24)), extent = 1f),
            Particles(STARS, color = c(0xFFE0F0FF), count = 60, bandBottom = 0.5f),
            Aurora(listOf(c(0x8834E0A0), c(0x6640C0C8), c(0x55A050D0)), top = 0.34f, height = 0.24f, bands = 5),
            Ridge(c(0xFF08161C), baseY = 0.74f, amp = 0.05f, detail = 4),
            Ridge(c(0xFF040D11), baseY = 0.88f, amp = 0.04f, detail = 4),
        ),
    )

    private val ORBIT_CITY = SceneSpec(
        id = "orbit-city", name = "Orbit City", group = SceneGroup.COSMIC,
        layers = listOf(
            Sky(listOf(c(0xFF0A0722), c(0xFF1B1140), c(0xFF2E1A55)), extent = 0.9f),
            Particles(STARS, color = c(0xFFEFF0FF), count = 55, bandBottom = 0.6f),
            Planet(
                x = 0.22f, y = 0.20f, radius = 0.13f,
                body = listOf(c(0xFF6E5AC0), c(0xFF34285E)),
                ring = c(0xAA9C86D8), ringTilt = 0.28f, glow = c(0x33A07CD9),
            ),
            SpaceSkyline(silhouette = c(0xFF140E2C), windowColor = c(0xFF7FD8FF), horizonY = 0.80f, towers = 10),
            Reflections(listOf(c(0xFF7FD8FF), c(0xFFB98CFF)), top = 0.86f),
        ),
    )

    /** Wave 1. Later waves append here; ids are forever. */
    val ALL: List<SceneSpec> = listOf(
        ROLLING_HILLS, TERRACE_GOLD, RIVER_BEND, BLOSSOM_DRIFT, SUMMER_FIELDS,
        FOREST_LIGHT, GULL_CLIFFS, WIND_OVER_GRASS, MISTY_MORNING, LANTERN_PATH,
        WIRES_AT_SUNSET, OVERPASS, ROOFTOP_STATIC, BLUE_HOUR_BANK, VANISHING_LINE,
        STAR_DRIFT, NEBULA_DRIFT, RING_RISE, COMET_ROAD, WARP_JUMP,
        NEON_DINER, TWIN_SUN_DESERT, ASTEROID_DRIFT, AURORA_PLANET, ORBIT_CITY,
    )

    private val byId = ALL.associateBy { it.id }

    fun byId(id: String?): SceneSpec? = byId[id]
}
