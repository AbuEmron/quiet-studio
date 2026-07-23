package com.quietstudio.core.media.scenes

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Paints a [SceneSpec] layer stack.
 *
 * SEAMLESS LOOP: identical discipline to the original scenes — every
 * oscillation runs a whole number of periods per video ([oscW]) and every
 * drifting element completes whole wraps ([wraps]), so the last frame flows
 * into the first. Fully deterministic in (seed, timeMs, durationMs): the
 * editor preview and the export overlay render identical frames.
 */
class SpecSceneRenderer(private val width: Int, private val height: Int) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val TWO_PI = 2f * PI.toFloat()

    private var prog = 0f
    private var loopT = 30f
    private var drift = 0.5f
    private var seed = 77L

    private fun oscW(omega: Float, phase: Float): Float {
        val n = max(1, (omega * loopT / TWO_PI).roundToInt())
        return sin(TWO_PI * n * prog + phase)
    }

    private fun wraps(wrapsPerSec: Float): Float {
        val n = max(1, (wrapsPerSec * loopT).roundToInt())
        return prog * n
    }

    /** X of a wrapping element; draw at the result and again minus [span] for the seam. */
    private fun wrapX(x0: Float, wrapsPerSec: Float, span: Float): Float {
        val x = (x0 + wraps(wrapsPerSec) * span) % span
        return if (x < 0) x + span else x
    }

    fun draw(
        canvas: Canvas,
        spec: SceneSpec,
        seed: Long,
        motionIntensity: Float,
        timeMs: Long,
        durationMs: Long,
    ) {
        val dur = durationMs.coerceAtLeast(1000)
        loopT = dur / 1000f
        prog = (timeMs.coerceAtLeast(0) % dur).toFloat() / dur
        drift = motionIntensity.coerceIn(0f, 1f)
        this.seed = if (seed != 0L) seed else 77L

        for (layer in spec.layers) {
            when (layer) {
                is Sky -> sky(canvas, layer)
                is Celestial -> celestial(canvas, layer)
                is Clouds -> clouds(canvas, layer)
                is CloudShadows -> cloudShadows(canvas, layer)
                is Ridge -> ridge(canvas, layer)
                is WaterBand -> water(canvas, layer)
                is Grass -> grass(canvas, layer)
                is Mist -> mist(canvas, layer)
                is GodRays -> godRays(canvas, layer)
                is Lanterns -> lanterns(canvas, layer)
                is PowerLines -> powerLines(canvas, layer)
                is Particles -> particles(canvas, layer)
            }
        }
    }

    /* ------------------------------- layers ------------------------------ */

    private fun sky(canvas: Canvas, l: Sky) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.shader = LinearGradient(
            0f, 0f, 0f, h * l.extent,
            l.colors.toIntArray(),
            l.stops?.toFloatArray(),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun celestial(canvas: Canvas, l: Celestial) {
        val w = width.toFloat(); val h = height.toFloat()
        val bodies = when (l.kind) {
            CelestialKind.TWIN_SUNS -> listOf(
                Triple(l.x, l.y, l.radius),
                Triple(l.x + l.radius * 2.6f, l.y + l.radius * 1.2f, l.radius * 0.55f),
            )
            else -> listOf(Triple(l.x, l.y, l.radius))
        }
        bodies.forEach { (bx, by, br) ->
            val cx = w * bx
            val cy = h * by + oscW(0.05f, bx * 7f) * h * 0.008f
            val r = w * br
            paint.shader = RadialGradient(
                cx, cy, r * l.glowScale,
                intArrayOf(l.glow, 0x00FFFFFF), null, Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, r * l.glowScale, paint)
            paint.shader = null
            paint.color = l.color
            canvas.drawCircle(cx, cy, r, paint)
            if (l.kind == CelestialKind.MOON) {
                val rnd = Random(seed * 17)
                paint.color = 0x22203055
                repeat(4) {
                    canvas.drawCircle(
                        cx + (rnd.nextFloat() - 0.5f) * r,
                        cy + (rnd.nextFloat() - 0.5f) * r,
                        r * (0.12f + rnd.nextFloat() * 0.16f), paint,
                    )
                }
            }
        }
    }

    private fun clouds(canvas: Canvas, l: Clouds) {
        val w = width.toFloat(); val h = height.toFloat()
        val rnd = Random(seed * 13)
        val perSec = l.wrapsPerMin / 60f * (0.4f + drift)
        for (i in 0 until l.count) {
            val baseX = rnd.nextFloat() * w
            val y = h * (l.bandTop + rnd.nextFloat() * (l.bandBottom - l.bandTop))
            val s = (0.5f + rnd.nextFloat()) * l.scale
            val speed = perSec * (0.6f + rnd.nextFloat() * 0.8f)
            val x = wrapX(baseX, speed, w * 1.4f) - w * 0.2f
            paint.color = l.color
            paint.alpha = (l.alpha * (0.6f + 0.4f * rnd.nextFloat())).toInt()
            listOf(x, x - w * 1.4f).forEach { cx ->
                val rw = w * 0.16f * s
                canvas.drawOval(RectF(cx - rw, y - rw * 0.32f, cx + rw, y + rw * 0.32f), paint)
                canvas.drawOval(
                    RectF(cx - rw * 0.55f, y - rw * 0.52f, cx + rw * 0.45f, y + rw * 0.12f), paint,
                )
            }
        }
        paint.alpha = 255
    }

    private fun cloudShadows(canvas: Canvas, l: CloudShadows) {
        val w = width.toFloat(); val h = height.toFloat()
        val rnd = Random(seed * 29)
        val perSec = l.wrapsPerMin / 60f * (0.4f + drift)
        for (i in 0 until l.count) {
            val baseX = rnd.nextFloat() * w
            val y = h * (l.bandTop + rnd.nextFloat() * (l.bandBottom - l.bandTop))
            val speed = perSec * (0.7f + rnd.nextFloat() * 0.6f)
            val x = wrapX(baseX, speed, w * 1.6f) - w * 0.3f
            paint.color = l.color
            listOf(x, x - w * 1.6f).forEach { cx ->
                canvas.drawOval(
                    RectF(cx - w * 0.22f, y - h * 0.03f, cx + w * 0.22f, y + h * 0.03f), paint,
                )
            }
        }
    }

    private fun terrain(x: Float, s: Long, detail: Int): Float {
        val rnd = Random(s)
        var v = 0f; var amp = 1f; var total = 0f
        for (o in 0 until detail) {
            val f = 0.6f + rnd.nextFloat() * 1.4f * (o + 1)
            val ph = rnd.nextFloat() * TWO_PI
            v += amp * sin(x * f + ph)
            total += amp
            amp *= 0.55f
        }
        return v / total
    }

    private fun ridge(canvas: Canvas, l: Ridge) {
        val w = width.toFloat(); val h = height.toFloat()
        val s = seed + l.seedSalt + (l.baseY * 1000).toLong()
        val xShift = w * l.sway * (0.4f + drift) * oscW(0.06f, l.baseY * 9f)
        path.reset()
        path.moveTo(0f, h)
        when (l.kind) {
            RidgeKind.HILLS -> {
                val steps = 48
                for (i in 0..steps) {
                    val x = i * w / steps
                    val nx = (x + xShift) / w * 4.4f
                    path.lineTo(x, h * l.baseY + terrain(nx, s, l.detail) * h * l.amp)
                }
            }
            RidgeKind.BLOCKS -> {
                // Flat-topped seeded shapes: rooftops, parapets, concrete decks.
                val rnd = Random(s)
                var x = -w * 0.05f
                var y = h * l.baseY
                path.lineTo(0f, y)
                while (x < w * 1.05f) {
                    val bw = w * (0.05f + rnd.nextFloat() * 0.11f)
                    y = h * l.baseY - rnd.nextFloat() * h * l.amp
                    path.lineTo(x, y)
                    path.lineTo(x + bw, y)
                    x += bw
                }
            }
        }
        path.lineTo(w, h)
        path.close()
        paint.color = l.color
        canvas.drawPath(path, paint)
    }

    private fun water(canvas: Canvas, l: WaterBand) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.shader = LinearGradient(
            0f, h * l.top, 0f, h * l.bottom,
            intArrayOf(l.light, l.deep), null, Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, h * l.top, w, h * l.bottom, paint)
        paint.shader = null

        val rnd = Random(seed * 41)
        paint.color = l.glintColor
        for (i in 0 until l.glints) {
            val x = rnd.nextFloat() * w
            val y = h * (l.top + rnd.nextFloat() * (l.bottom - l.top) * 0.9f)
            val len = w * (0.02f + rnd.nextFloat() * 0.05f)
            val tw = 0.5f + 0.5f * oscW(0.5f + rnd.nextFloat() * 0.8f, i.toFloat())
            paint.alpha = (110 * tw).toInt()
            canvas.drawRect(x - len / 2f, y, x + len / 2f, y + h * 0.0035f, paint)
        }
        paint.alpha = 255
    }

    private fun grass(canvas: Canvas, l: Grass) {
        val w = width.toFloat(); val h = height.toFloat()
        val top = h * (1f - l.height)
        paint.color = l.color
        canvas.drawRect(0f, top + h * 0.02f, w, h, paint)
        val rnd = Random(seed * 7)
        val blades = 70
        for (i in 0 until blades) {
            val x = i * w / blades + rnd.nextFloat() * w / blades
            val bh = h * (0.02f + rnd.nextFloat() * 0.045f)
            val sway = w * 0.008f * l.sway * (0.4f + drift) *
                oscW(0.5f + rnd.nextFloat() * 0.7f, i * 0.6f)
            path.reset()
            path.moveTo(x - w * 0.004f, top + h * 0.025f)
            path.lineTo(x + sway, top + h * 0.025f - bh)
            path.lineTo(x + w * 0.004f, top + h * 0.025f)
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    private fun mist(canvas: Canvas, l: Mist) {
        val w = width.toFloat(); val h = height.toFloat()
        val rnd = Random(seed * 23)
        for (i in 0 until l.bands) {
            val y = h * (l.bandTop + (l.bandBottom - l.bandTop) * i / l.bands.coerceAtLeast(1))
            val dx = w * 0.06f * oscW(0.05f + i * 0.02f, i * 2f + rnd.nextFloat())
            paint.color = l.color
            canvas.drawRoundRect(
                RectF(-w * 0.1f + dx, y, w * 1.1f + dx, y + h * (0.035f + rnd.nextFloat() * 0.03f)),
                h * 0.03f, h * 0.03f, paint,
            )
        }
    }

    private fun godRays(canvas: Canvas, l: GodRays) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w * l.x; val cy = h * l.y
        val rnd = Random(seed * 37)
        for (i in 0 until l.rays) {
            val ang = (PI * 0.30 + rnd.nextFloat() * PI * 0.40).toFloat() // downward fan
            val spread = 0.03f + rnd.nextFloat() * 0.04f
            val len = h * (0.7f + rnd.nextFloat() * 0.4f)
            val pulse = 0.55f + 0.45f * oscW(0.08f + i * 0.015f, i * 1.3f)
            val dx1 = kotlin.math.cos(ang - spread) * len
            val dy1 = sin(ang - spread) * len
            val dx2 = kotlin.math.cos(ang + spread) * len
            val dy2 = sin(ang + spread) * len
            path.reset()
            path.moveTo(cx, cy)
            path.lineTo(cx + dx1, cy + dy1)
            path.lineTo(cx + dx2, cy + dy2)
            path.close()
            paint.color = l.color
            paint.alpha = ((l.color ushr 24) * pulse).toInt()
            canvas.drawPath(path, paint)
        }
        paint.alpha = 255
    }

    private fun lanterns(canvas: Canvas, l: Lanterns) {
        val w = width.toFloat(); val h = height.toFloat()
        val rnd = Random(seed * 43)
        for (i in 0 until l.count) {
            val x = w * (0.08f + 0.84f * i / (l.count - 1).coerceAtLeast(1))
            val y = h * (l.pathY + 0.04f * sin(i * 1.1f)) +
                h * 0.006f * oscW(0.4f + rnd.nextFloat() * 0.4f, i * 1.7f)
            val r = w * 0.020f * (0.85f + rnd.nextFloat() * 0.3f)
            paint.shader = RadialGradient(
                x, y, r * 4f, intArrayOf(l.glow, 0x00FFFFFF), null, Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x, y, r * 4f, paint)
            paint.shader = null
            paint.color = l.color
            canvas.drawOval(RectF(x - r * 0.8f, y - r, x + r * 0.8f, y + r), paint)
            paint.color = 0xFF241A10.toInt()
            canvas.drawRect(x - r * 0.35f, y - r * 1.35f, x + r * 0.35f, y - r, paint)
        }
    }

    private fun powerLines(canvas: Canvas, l: PowerLines) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.color = l.color
        paint.strokeCap = Paint.Cap.ROUND
        if (!l.perspective) {
            val topY = h * (l.horizonY - 0.30f)
            val xs = (0 until l.poles).map { w * (0.12f + 0.76f * it / (l.poles - 1).coerceAtLeast(1)) }
            xs.forEach { x ->
                paint.strokeWidth = w * 0.008f
                canvas.drawLine(x, topY, x, h * l.horizonY + h * 0.05f, paint)
                paint.strokeWidth = w * 0.006f
                canvas.drawLine(x - w * 0.045f, topY + h * 0.012f, x + w * 0.045f, topY + h * 0.012f, paint)
                canvas.drawLine(x - w * 0.032f, topY + h * 0.036f, x + w * 0.032f, topY + h * 0.036f, paint)
            }
            paint.strokeWidth = w * 0.0035f
            paint.style = Paint.Style.STROKE
            for (wire in 0 until 2) {
                val wy = topY + h * (0.012f + wire * 0.024f)
                for (i in 0 until xs.size - 1) {
                    val x1 = xs[i]; val x2 = xs[i + 1]
                    path.reset()
                    path.moveTo(x1, wy)
                    path.quadTo((x1 + x2) / 2f, wy + h * 0.028f, x2, wy)
                    canvas.drawPath(path, paint)
                }
            }
            paint.style = Paint.Style.FILL
        } else {
            // Receding to a vanishing point on the horizon.
            val vx = w * 0.30f; val vy = h * l.horizonY
            var px = w * 0.94f
            var poleH = h * 0.34f
            paint.style = Paint.Style.FILL
            for (i in 0 until l.poles) {
                val topYp = vy - poleH
                paint.strokeWidth = max(1.5f, w * 0.010f * poleH / (h * 0.34f))
                canvas.drawLine(px, vy + poleH * 0.15f, px, topYp, paint)
                val arm = w * 0.05f * poleH / (h * 0.34f)
                canvas.drawLine(px - arm, topYp + poleH * 0.05f, px + arm, topYp + poleH * 0.05f, paint)
                // wires from this pole toward the vanishing point
                paint.strokeWidth = max(1f, w * 0.003f * poleH / (h * 0.34f))
                canvas.drawLine(px, topYp, vx, vy - h * 0.004f, paint)
                px = vx + (px - vx) * 0.62f
                poleH *= 0.62f
            }
        }
        if (l.birds > 0) {
            val rnd = Random(seed * 53)
            paint.strokeWidth = w * 0.004f
            paint.style = Paint.Style.STROKE
            for (i in 0 until l.birds) {
                val span = w * 1.3f
                val bx = wrapX(rnd.nextFloat() * w, 0.010f * (0.5f + rnd.nextFloat()), span) - w * 0.15f
                val by = h * (0.12f + rnd.nextFloat() * 0.18f) + h * 0.008f * oscW(0.4f, i * 2f)
                val ws = w * 0.014f
                val flap = 0.4f + 0.6f * abs(oscW(1.6f + rnd.nextFloat(), i.toFloat()))
                path.reset()
                path.moveTo(bx - ws, by)
                path.quadTo(bx - ws * 0.4f, by - ws * flap, bx, by)
                path.quadTo(bx + ws * 0.4f, by - ws * flap, bx + ws, by)
                canvas.drawPath(path, paint)
            }
            paint.style = Paint.Style.FILL
        }
    }

    private fun particles(canvas: Canvas, l: Particles) {
        val w = width.toFloat(); val h = height.toFloat()
        val rnd = Random(seed * 61 + l.kind.ordinal)
        when (l.kind) {
            ParticleKind.STARS -> {
                for (i in 0 until l.count) {
                    val x = rnd.nextFloat() * w
                    val y = rnd.nextFloat() * h * l.bandBottom
                    val tw = 0.4f + 0.6f * (0.5f + 0.5f * oscW(0.6f + rnd.nextFloat(), i.toFloat()))
                    paint.color = l.color
                    paint.alpha = (205 * tw).toInt()
                    canvas.drawCircle(x, y, (1f + rnd.nextFloat() * 1.6f) * l.size * w / 540f, paint)
                }
            }
            ParticleKind.FIREFLIES -> {
                for (i in 0 until l.count) {
                    val bx = rnd.nextFloat() * w
                    val by = h * (0.45f + rnd.nextFloat() * 0.45f)
                    val x = bx + w * 0.05f * oscW(0.12f + rnd.nextFloat() * 0.2f, i * 1.1f)
                    val y = by + h * 0.03f * oscW(0.10f + rnd.nextFloat() * 0.2f, i * 2.3f)
                    val pulse = 0.25f + 0.75f * (0.5f + 0.5f * oscW(0.5f + rnd.nextFloat() * 0.8f, i.toFloat()))
                    val r = w * 0.006f * l.size
                    paint.shader = RadialGradient(
                        x, y, r * 4f,
                        intArrayOf(l.color, 0x00FFFFFF), null, Shader.TileMode.CLAMP,
                    )
                    paint.alpha = (255 * pulse).toInt()
                    canvas.drawCircle(x, y, r * 4f, paint)
                    paint.shader = null
                }
                paint.alpha = 255
            }
            ParticleKind.RAIN -> {
                paint.color = l.color
                paint.strokeWidth = w * 0.004f * l.size
                paint.strokeCap = Paint.Cap.ROUND
                val fall = 0.55f * l.speed * (0.5f + drift)
                for (i in 0 until l.count) {
                    val x0 = rnd.nextFloat() * w
                    val span = h * 1.2f
                    val y = ((rnd.nextFloat() * span + wraps(fall * (0.8f + rnd.nextFloat() * 0.5f)) * span) % span)
                    val len = h * (0.03f + rnd.nextFloat() * 0.03f)
                    canvas.drawLine(x0, y - h * 0.1f, x0 - w * 0.015f, y - h * 0.1f + len, paint)
                }
            }
            ParticleKind.SNOW, ParticleKind.PETALS, ParticleKind.SEEDS, ParticleKind.LEAVES -> {
                paint.color = l.color
                val base = when (l.kind) {
                    ParticleKind.SNOW -> 0.05f
                    ParticleKind.PETALS -> 0.06f
                    ParticleKind.LEAVES -> 0.07f
                    else -> 0.04f
                } * l.speed
                for (i in 0 until l.count) {
                    val x0 = rnd.nextFloat() * w
                    val span = h * 1.2f
                    val rising = l.kind == ParticleKind.SEEDS
                    val prog0 = rnd.nextFloat() * span
                    val travel = wraps(base * (0.7f + rnd.nextFloat() * 0.6f)) * span
                    val yRaw = (prog0 + travel) % span
                    val y = (if (rising) span - yRaw else yRaw) - h * 0.1f
                    val x = x0 + w * 0.04f * oscW(0.25f + rnd.nextFloat() * 0.5f, i * 1.9f)
                    val r = w * (0.004f + rnd.nextFloat() * 0.005f) * l.size
                    paint.alpha = 160 + (95 * rnd.nextFloat()).toInt()
                    when (l.kind) {
                        ParticleKind.PETALS, ParticleKind.LEAVES -> {
                            val stretch = 1.4f + 0.5f * oscW(0.8f + rnd.nextFloat(), i.toFloat())
                            canvas.drawOval(RectF(x - r * stretch, y - r, x + r * stretch, y + r), paint)
                        }
                        else -> canvas.drawCircle(x, y, r, paint)
                    }
                }
                paint.alpha = 255
            }
            ParticleKind.GULLS -> {
                paint.color = l.color
                paint.strokeWidth = w * 0.005f
                paint.strokeCap = Paint.Cap.ROUND
                paint.style = Paint.Style.STROKE
                for (i in 0 until l.count) {
                    val span = w * 1.3f
                    val x = wrapX(rnd.nextFloat() * w, 0.012f * l.speed * (0.6f + rnd.nextFloat()), span) - w * 0.15f
                    val y = h * (0.14f + rnd.nextFloat() * 0.16f) + h * 0.01f * oscW(0.3f, i * 2.1f)
                    val ws = w * (0.012f + rnd.nextFloat() * 0.008f) * l.size
                    val flap = 0.35f + 0.65f * abs(oscW(1.4f + rnd.nextFloat() * 0.8f, i.toFloat()))
                    path.reset()
                    path.moveTo(x - ws, y)
                    path.quadTo(x - ws * 0.4f, y - ws * flap, x, y)
                    path.quadTo(x + ws * 0.4f, y - ws * flap, x + ws, y)
                    canvas.drawPath(path, paint)
                }
                paint.style = Paint.Style.FILL
            }
            ParticleKind.SHOOTING_STAR -> {
                // A brief streak k whole times per loop, so it loops cleanly.
                val events = max(1, (loopT / 18f).roundToInt())
                val cycle = (prog * events) % 1f
                val window = 0.07f
                if (cycle < window) {
                    val k = cycle / window
                    val which = ((prog * events).toInt()) % events
                    val r2 = Random(seed * 71 + which)
                    val sx = w * (0.15f + r2.nextFloat() * 0.6f)
                    val sy = h * (0.06f + r2.nextFloat() * 0.16f)
                    val ex = sx + w * 0.28f
                    val ey = sy + h * 0.10f
                    val px = sx + (ex - sx) * k
                    val py = sy + (ey - sy) * k
                    paint.color = l.color
                    paint.strokeWidth = w * 0.004f
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.alpha = (255 * (1f - k)).toInt()
                    canvas.drawLine(px - w * 0.06f, py - h * 0.021f, px, py, paint)
                    paint.alpha = 255
                }
            }
        }
    }
}
