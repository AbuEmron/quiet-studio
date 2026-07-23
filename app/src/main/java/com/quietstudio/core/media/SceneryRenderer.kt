package com.quietstudio.core.media

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import com.quietstudio.core.media.scenes.SceneCatalog
import com.quietstudio.core.media.scenes.SpecSceneRenderer
import com.quietstudio.core.model.VisualConfig
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural animated scenery — living, painterly landscapes generated
 * on-device. Every scene is an original composition built from universal
 * nature imagery (skies, hills, meadows, weather, light); nothing is
 * sampled from or modeled on any artist's or studio's work.
 *
 * SEAMLESS LOOP: all motion is quantized to whole cycles over the video's
 * duration — oscillations round to integer periods and drifting elements
 * complete integer wraps — so the final frame flows perfectly back into
 * the first when the video repeats. Fully deterministic in
 * (seed, timeMs, durationMs): preview and export render identical frames.
 *
 * Themes: MEADOW (sunrise), DUSK (golden hills), NIGHT (moon + fireflies),
 * RAIN (misty rainfall), COAST (sea shimmer), SNOW (quiet snowfall).
 */
class SceneryRenderer(private val width: Int, private val height: Int) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private val TWO_PI = 2f * PI.toFloat()

    /** loop phase 0..1 over the video, and loop length in seconds */
    private var prog = 0f
    private var loopT = 30f

    /** Sine oscillation at ~[omega] rad/s, quantized to whole loop cycles. */
    private fun oscW(omega: Float, phase: Float): Float {
        val n = max(1, (omega * loopT / TWO_PI).roundToInt())
        return sin(TWO_PI * n * prog + phase)
    }

    /** Linear drift at ~[wrapsPerSec] wraps/s, quantized to whole wraps (0..n). */
    private fun wraps(wrapsPerSec: Float): Float {
        val n = max(1, (wrapsPerSec * loopT).roundToInt())
        return prog * n
    }

    private data class Palette(
        val skyTop: Int, val skyMid: Int, val skyBottom: Int,
        val sun: Int, val sunGlow: Int,
        val ridges: List<Int>,          // far -> near
        val foreground: Int,
        val particle: Int,
        val cloud: Int,
    )

    private fun palette(theme: String): Palette = when (theme) {
        "MEADOW" -> Palette(
            skyTop = 0xFF7FB2D9.toInt(), skyMid = 0xFFF6E3B4.toInt(), skyBottom = 0xFFFAD9A0.toInt(),
            sun = 0xFFFFF3D6.toInt(), sunGlow = 0x66FFD98A,
            ridges = listOf(0xFF9DB9A5.toInt(), 0xFF6F9B72.toInt(), 0xFF4C7C52.toInt()),
            foreground = 0xFF2F5A38.toInt(), particle = 0xFFFFFBEA.toInt(), cloud = 0xFFFFFFFF.toInt(),
        )
        "NIGHT" -> Palette(
            skyTop = 0xFF0A1030.toInt(), skyMid = 0xFF1B2A52.toInt(), skyBottom = 0xFF2C3B63.toInt(),
            sun = 0xFFEFF3FF.toInt(), sunGlow = 0x445E77C9,
            ridges = listOf(0xFF232F55.toInt(), 0xFF18213E.toInt(), 0xFF0E142A.toInt()),
            foreground = 0xFF080C1A.toInt(), particle = 0xFFCBE896.toInt(), cloud = 0xFF3A4A78.toInt(),
        )
        "RAIN" -> Palette(
            skyTop = 0xFF4E5E6B.toInt(), skyMid = 0xFF6C7B85.toInt(), skyBottom = 0xFF8A968F.toInt(),
            sun = 0x00FFFFFF, sunGlow = 0x00FFFFFF,
            ridges = listOf(0xFF5D6E70.toInt(), 0xFF465759.toInt(), 0xFF2F3F41.toInt()),
            foreground = 0xFF1F2C2E.toInt(), particle = 0x80D7E4EA.toInt(), cloud = 0xFF8C99A1.toInt(),
        )
        "COAST" -> Palette(
            skyTop = 0xFF8FC3E8.toInt(), skyMid = 0xFFD8ECF4.toInt(), skyBottom = 0xFFF3E9CE.toInt(),
            sun = 0xFFFFF8E1.toInt(), sunGlow = 0x55FFE9AE,
            ridges = listOf(0xFF7FAECB.toInt(), 0xFF4E86AC.toInt(), 0xFF2F6B93.toInt()),
            foreground = 0xFF1F5578.toInt(), particle = 0xFFFFFFFF.toInt(), cloud = 0xFFFFFFFF.toInt(),
        )
        "SNOW" -> Palette(
            skyTop = 0xFFB9C6D4.toInt(), skyMid = 0xFFDCE4EC.toInt(), skyBottom = 0xFFEFF2F5.toInt(),
            sun = 0xFFF7F9FC.toInt(), sunGlow = 0x33FFFFFF,
            ridges = listOf(0xFFC9D4DE.toInt(), 0xFFAEBCC9.toInt(), 0xFF8FA1B1.toInt()),
            foreground = 0xFFE9EEF3.toInt(), particle = 0xFFFFFFFF.toInt(), cloud = 0xFFF2F5F8.toInt(),
        )
        else -> Palette( // DUSK
            skyTop = 0xFF3D3260.toInt(), skyMid = 0xFFB86B57.toInt(), skyBottom = 0xFFE9A25F.toInt(),
            sun = 0xFFFFE3B0.toInt(), sunGlow = 0x66FFB25E,
            ridges = listOf(0xFF6B4E6E.toInt(), 0xFF4A3556.toInt(), 0xFF2C1F3A.toInt()),
            foreground = 0xFF1A1226.toInt(), particle = 0xFFF3C98B.toInt(), cloud = 0xFFEED3C0.toInt(),
        )
    }

    /** Spec-driven scenes (wave 1+). Legacy themes below render unchanged. */
    private val specRenderer = SpecSceneRenderer(width, height)

    fun draw(canvas: Canvas, config: VisualConfig, timeMs: Long, durationMs: Long) {
        val theme = if (config.sceneryTheme == "AUTO") "DUSK" else config.sceneryTheme
        val seed = if (config.scenerySeed != 0L) config.scenerySeed else 77L

        SceneCatalog.byId(theme)?.let { spec ->
            specRenderer.draw(canvas, spec, seed, config.motionIntensity, timeMs, durationMs)
            return
        }
        val p = palette(theme)
        val dur = durationMs.coerceAtLeast(1000)
        loopT = dur / 1000f
        prog = (timeMs.coerceAtLeast(0) % dur).toFloat() / dur
        val drift = config.motionIntensity.coerceIn(0f, 1f)
        val w = width.toFloat(); val h = height.toFloat()

        // ---- sky
        paint.shader = LinearGradient(
            0f, 0f, 0f, h * 0.8f,
            intArrayOf(p.skyTop, p.skyMid, p.skyBottom),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // ---- stars (night / dusk hint)
        if (theme == "NIGHT" || theme == "DUSK") {
            val rnd = Random(seed * 31)
            val count = if (theme == "NIGHT") 90 else 25
            for (i in 0 until count) {
                val sx = rnd.nextFloat() * w
                val sy = rnd.nextFloat() * h * 0.45f
                val tw = 0.4f + 0.6f * (0.5f + 0.5f * oscW(0.6f + rnd.nextFloat(), i.toFloat()))
                paint.color = Color.WHITE
                paint.alpha = (tw * (if (theme == "NIGHT") 200 else 90)).toInt()
                canvas.drawCircle(sx, sy, 1f + rnd.nextFloat() * 1.6f * w / 540f, paint)
            }
            paint.alpha = 255
        }

        // ---- sun / moon with slow breathing
        if (theme != "RAIN") {
            val sunX = w * (0.5f + 0.22f * sin(seed % 7 * 0.9f))
            val sunY = h * (if (theme == "NIGHT") 0.16f else 0.30f) + oscW(0.05f, 0f) * h * 0.01f
            val sunR = w * (if (theme == "NIGHT") 0.085f else 0.11f)
            paint.shader = RadialGradient(
                sunX, sunY, sunR * 3.2f,
                intArrayOf(p.sunGlow, 0x00FFFFFF), null, Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(sunX, sunY, sunR * 3.2f, paint)
            paint.shader = null
            paint.color = p.sun
            canvas.drawCircle(sunX, sunY, sunR, paint)
            if (theme == "NIGHT") { // crater shading for the moon
                val rnd = Random(seed * 17)
                paint.color = 0x22203055
                repeat(4) {
                    canvas.drawCircle(
                        sunX + (rnd.nextFloat() - 0.5f) * sunR,
                        sunY + (rnd.nextFloat() - 0.5f) * sunR,
                        sunR * (0.12f + rnd.nextFloat() * 0.16f), paint,
                    )
                }
            }
        }

        // ---- clouds: layered soft puffs drifting with seamless wrap
        drawClouds(canvas, p, seed, drift, theme)

        // ---- ridgelines: gentle oscillating parallax (loops by construction)
        val ridgeBase = floatArrayOf(0.52f, 0.62f, 0.72f)
        for (layer in 0 until 3) {
            val sway = w * (0.02f + layer * 0.02f) * (0.4f + drift)
            drawRidge(
                canvas, p.ridges[layer],
                baseY = h * ridgeBase[layer],
                amp = h * (0.045f + layer * 0.02f),
                xShift = sway * oscW(0.06f + layer * 0.02f, layer * 1.7f),
                seed = seed + layer * 101,
                detail = 3 + layer,
            )
        }

        // ---- theme foreground + weather
        when (theme) {
            "MEADOW", "DUSK" -> {
                drawGrass(canvas, p.foreground, drift, seed)
                if (theme == "MEADOW") drawSeeds(canvas, p.particle, seed)
                else drawLeaves(canvas, p.particle, seed)
            }
            "NIGHT" -> {
                drawGrass(canvas, p.foreground, drift * 0.6f, seed)
                drawFireflies(canvas, p.particle, seed)
            }
            "RAIN" -> {
                drawMistBand(canvas, seed)
                drawGrass(canvas, p.foreground, drift * 1.4f, seed)
                drawRain(canvas, p.particle, seed)
            }
            "COAST" -> drawSea(canvas, p, seed)
            "SNOW" -> {
                drawSnowGround(canvas, p.foreground)
                drawSnow(canvas, p.particle, seed)
            }
        }
    }

    /* --------------------------- layer helpers --------------------------- */

    /** Continuous seeded terrain function: sum of sines. */
    private fun terrain(x: Float, seed: Long, detail: Int): Float {
        val rnd = Random(seed)
        var v = 0f
        var amp = 1f
        var total = 0f
        for (o in 0 until detail) {
            val f = 0.6f + rnd.nextFloat() * 1.4f * (o + 1)
            val ph = rnd.nextFloat() * TWO_PI
            v += amp * sin(x * f + ph)
            total += amp
            amp *= 0.55f
        }
        return v / total
    }

    private fun drawRidge(
        canvas: Canvas, color: Int, baseY: Float, amp: Float,
        xShift: Float, seed: Long, detail: Int,
    ) {
        val w = width.toFloat(); val h = height.toFloat()
        path.reset()
        path.moveTo(0f, h)
        val steps = 48
        for (i in 0..steps) {
            val x = i * w / steps
            val nx = (x + xShift) / w * 4.4f
            val y = baseY + terrain(nx, seed, detail) * amp
            path.lineTo(x, y)
        }
        path.lineTo(w, h)
        path.close()
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun drawClouds(canvas: Canvas, p: Palette, seed: Long, drift: Float, theme: String) {
        val w = width.toFloat(); val h = height.toFloat()
        val rnd = Random(seed * 13)
        val count = if (theme == "RAIN") 7 else 5
        for (c in 0 until count) {
            val speed = (0.008f + rnd.nextFloat() * 0.012f) * (0.4f + drift)
            // whole wraps of the 1.3-wide band -> seamless at the loop point
            val cx = ((rnd.nextFloat() + wraps(speed / 1.3f) * 1.3f) % 1.3f - 0.15f) * w
            val cy = h * (0.10f + rnd.nextFloat() * 0.22f) + oscW(0.1f, c.toFloat()) * h * 0.006f
            val scale = 0.55f + rnd.nextFloat() * 0.8f
            val alpha = if (theme == "RAIN") 200 else 130
            for (puff in 0 until 4) {
                val px = cx + (puff - 1.5f) * w * 0.055f * scale
                val py = cy + sin(puff * 2.1f + c) * h * 0.012f * scale
                val pr = w * (0.055f + (puff % 2) * 0.025f) * scale
                paint.shader = RadialGradient(
                    px, py, pr,
                    intArrayOf((alpha shl 24) or (p.cloud and 0x00FFFFFF), (p.cloud and 0x00FFFFFF)),
                    null, Shader.TileMode.CLAMP,
                )
                canvas.drawCircle(px, py, pr, paint)
            }
            paint.shader = null
        }
    }

    private fun drawGrass(canvas: Canvas, color: Int, sway: Float, seed: Long) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.color = color
        canvas.drawRect(0f, h * 0.86f, w, h, paint)
        paint.strokeWidth = w / 220f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        val rnd = Random(seed * 7)
        val blades = 70
        for (i in 0 until blades) {
            val bx = rnd.nextFloat() * w
            val bh = h * (0.035f + rnd.nextFloat() * 0.05f)
            val phase = rnd.nextFloat() * 6f
            val rootY = h * 0.90f + rnd.nextFloat() * h * 0.08f
            val lean = oscW(0.8f + rnd.nextFloat() * 0.5f, phase) * (6f + 14f * sway) +
                (rnd.nextFloat() - 0.5f) * 8f
            path.reset()
            path.moveTo(bx, rootY)
            val topX = bx + lean * w / 540f
            val topY = rootY - bh
            path.quadTo(bx + lean * 0.3f * w / 540f, topY + bh * 0.5f, topX, topY)
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawSeeds(canvas: Canvas, color: Int, seed: Long) {
        val w = width.toFloat(); val h = height.toFloat()
        val rnd = Random(seed * 5)
        for (i in 0 until 26) {
            val speed = 0.012f + rnd.nextFloat() * 0.02f
            val px = ((rnd.nextFloat() + wraps(speed / 1.1f) * 1.1f) % 1.1f - 0.05f) * w
            val py = h * (0.25f + 0.55f * rnd.nextFloat()) +
                oscW(0.7f + rnd.nextFloat(), i.toFloat()) * h * 0.03f
            paint.color = color
            paint.alpha = 120 + (60 * oscW(1f, i.toFloat())).toInt().coerceIn(-60, 60)
            canvas.drawCircle(px, py, (1.6f + rnd.nextFloat() * 2.2f) * w / 540f, paint)
        }
        paint.alpha = 255
    }

    private fun drawLeaves(canvas: Canvas, color: Int, seed: Long) {
        val w = width.toFloat(); val h = height.toFloat()
        val rnd = Random(seed * 3)
        for (i in 0 until 16) {
            val fall = 0.03f + rnd.nextFloat() * 0.03f
            val driftX = 0.02f + rnd.nextFloat() * 0.015f
            val px = ((rnd.nextFloat() + wraps(driftX / 1.1f) * 1.1f) % 1.1f - 0.05f) * w
            val py = ((rnd.nextFloat() + wraps(fall / 1.1f) * 1.1f) % 1.1f - 0.05f) * h
            // whole turns over the loop -> rotation is seamless too
            val turns = max(1, ((40f + rnd.nextFloat() * 60f) * loopT / 360f).roundToInt())
            val rot = prog * turns * 360f + i * 40f
            paint.color = color
            paint.alpha = 170
            canvas.save()
            canvas.rotate(rot, px, py)
            canvas.drawOval(
                px - 4.5f * w / 540f, py - 2.2f * w / 540f,
                px + 4.5f * w / 540f, py + 2.2f * w / 540f, paint,
            )
            canvas.restore()
        }
        paint.alpha = 255
    }

    private fun drawFireflies(canvas: Canvas, color: Int, seed: Long) {
        val w = width.toFloat(); val h = height.toFloat()
        val rnd = Random(seed * 11)
        for (i in 0 until 22) {
            val cx = rnd.nextFloat() * w
            val cy = h * (0.55f + rnd.nextFloat() * 0.38f)
            val ax = w * (0.02f + rnd.nextFloat() * 0.05f)
            val ay = h * (0.01f + rnd.nextFloat() * 0.03f)
            val px = cx + ax * oscW(0.3f + rnd.nextFloat() * 0.5f, i * 1.7f)
            val py = cy + ay * oscW(0.2f + rnd.nextFloat() * 0.4f, i * 0.9f)
            val pulse = 0.5f + 0.5f * oscW(1.2f + rnd.nextFloat(), i * 2.3f)
            paint.shader = RadialGradient(
                px, py, 9f * w / 540f,
                intArrayOf(((pulse * 200).toInt() shl 24) or (color and 0x00FFFFFF), color and 0x00FFFFFF),
                null, Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(px, py, 9f * w / 540f, paint)
            paint.shader = null
            paint.color = color
            paint.alpha = (pulse * 255).toInt()
            canvas.drawCircle(px, py, 1.8f * w / 540f, paint)
        }
        paint.alpha = 255
    }

    private fun drawRain(canvas: Canvas, color: Int, seed: Long) {
        val w = width.toFloat(); val h = height.toFloat()
        val rnd = Random(seed * 19)
        paint.color = color
        paint.strokeWidth = w / 460f
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 110) {
            val colX = rnd.nextFloat()
            val speed = 0.9f + rnd.nextFloat() * 0.5f
            val phase = rnd.nextFloat()
            val py = ((phase + wraps(speed / 1.05f) * 1.05f) % 1.05f) * h
            val px = colX * w - py * 0.12f
            val len = h * (0.02f + rnd.nextFloat() * 0.02f)
            paint.alpha = 70 + rnd.nextInt(70)
            canvas.drawLine(px, py, px + len * 0.12f, py + len, paint)
        }
        paint.alpha = 255
    }

    private fun drawMistBand(canvas: Canvas, seed: Long) {
        val w = width.toFloat(); val h = height.toFloat()
        val rnd = Random(seed * 23)
        for (i in 0 until 3) {
            val my = h * (0.58f + i * 0.07f)
            val mx = ((rnd.nextFloat() + wraps(0.01f * (i + 1) / 1.4f) * 1.4f) % 1.4f - 0.2f) * w
            paint.shader = RadialGradient(
                mx, my, w * 0.5f,
                intArrayOf(0x2EFFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, my - h * 0.06f, w, my + h * 0.06f, paint)
            paint.shader = null
        }
    }

    private fun drawSea(canvas: Canvas, p: Palette, seed: Long) {
        val w = width.toFloat(); val h = height.toFloat()
        val horizon = h * 0.62f
        paint.shader = LinearGradient(
            0f, horizon, 0f, h,
            intArrayOf(p.ridges[1], p.foreground), null, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, horizon, w, h, paint)
        paint.shader = null
        val rnd = Random(seed * 29)
        paint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 60) {
            val gy = horizon + rnd.nextFloat() * (h - horizon) * 0.85f
            val depth = (gy - horizon) / (h - horizon)
            val gx = rnd.nextFloat() * w
            val tw = 0.5f + 0.5f * oscW(1f + rnd.nextFloat() * 1.5f, i * 1.3f)
            paint.color = p.particle
            paint.alpha = (tw * 150 * (0.4f + 0.6f * depth)).toInt()
            paint.strokeWidth = (1f + depth * 2.4f) * w / 540f
            canvas.drawLine(gx, gy, gx + (6f + depth * 22f) * w / 540f, gy, paint)
        }
        paint.alpha = 255
        for (b in 0 until 3) {
            val by = horizon + (h - horizon) * (0.3f + b * 0.25f)
            val nWave = max(1, ((0.5f + b * 0.2f) * loopT / TWO_PI).roundToInt())
            path.reset()
            path.moveTo(0f, h)
            val steps = 40
            for (i in 0..steps) {
                val x = i * w / steps
                val y = by + sin(x / w * 6f + TWO_PI * nWave * prog) * h * 0.006f
                path.lineTo(x, y)
            }
            path.lineTo(w, h)
            path.close()
            paint.color = p.ridges[2]
            paint.alpha = 40
            canvas.drawPath(path, paint)
        }
        paint.alpha = 255
    }

    private fun drawSnowGround(canvas: Canvas, color: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.color = color
        path.reset()
        path.moveTo(0f, h)
        path.lineTo(0f, h * 0.82f)
        path.quadTo(w * 0.5f, h * 0.78f, w, h * 0.84f)
        path.lineTo(w, h)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawSnow(canvas: Canvas, color: Int, seed: Long) {
        val w = width.toFloat(); val h = height.toFloat()
        val rnd = Random(seed * 37)
        paint.color = color
        for (i in 0 until 70) {
            val fall = 0.035f + rnd.nextFloat() * 0.04f
            val swayA = w * 0.01f * (0.5f + rnd.nextFloat())
            val px = rnd.nextFloat() * w + swayA * oscW(0.5f + rnd.nextFloat() * 0.5f, i.toFloat())
            val py = ((rnd.nextFloat() + wraps(fall / 1.05f) * 1.05f) % 1.05f) * h
            paint.alpha = 140 + rnd.nextInt(100)
            canvas.drawCircle(
                (px % (w * 1.02f) + w * 1.02f) % (w * 1.02f), py,
                (1.2f + rnd.nextFloat() * 2.4f) * w / 540f, paint,
            )
        }
        paint.alpha = 255
    }
}
