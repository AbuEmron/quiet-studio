package com.quietstudio.core.media

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.quietstudio.core.model.BackgroundKind
import com.quietstudio.core.model.MotionEffect
import com.quietstudio.core.model.VisualConfig
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Deterministic, time-parameterized renderer for every procedural background
 * Quiet Studio supports. The SAME code paints the live editor preview
 * (via Compose Canvas) and every exported frame (via the export overlay),
 * so what you see is exactly what renders.
 *
 * All animation is a pure function of `timeMs`, which keeps frames
 * reproducible and lets the export pipeline render out of order.
 */
class SceneRenderer(
    private val width: Int,
    private val height: Int,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Paint()
    private var grainFrames: List<Bitmap>? = null
    private var sourceBitmap: Bitmap? = null
    private var sourceBitmapUri: String? = null

    /** Optional decoded background image (for IMAGE kind). */
    fun setSourceBitmap(uri: String?, bitmap: Bitmap?) {
        sourceBitmapUri = uri
        sourceBitmap = bitmap
    }

    fun drawFrame(canvas: Canvas, config: VisualConfig, timeMs: Long, durationMs: Long) {
        val t = timeMs / 1000f
        val progress = if (durationMs > 0) (timeMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
        when (BackgroundKind.valueOf(config.kind)) {
            BackgroundKind.SOLID -> canvas.drawColor(config.gradientColors.first().toInt())
            BackgroundKind.GRADIENT -> drawGradient(canvas, config, t)
            BackgroundKind.IMAGE -> drawImage(canvas, config, progress)
            BackgroundKind.VIDEO -> Unit // video frames come from the decoder underneath
            BackgroundKind.PARTICLES -> { drawGradient(canvas, config, t * 0.4f); drawParticles(canvas, config, t) }
            BackgroundKind.MOTION -> drawMotion(canvas, config, t)
        }
        if (config.filmGrain > 0.01f) drawGrain(canvas, config.filmGrain, timeMs)
        if (config.vignette > 0.01f) drawVignette(canvas, config.vignette)
    }

    /* ------------------------------ layers -------------------------------- */

    private fun drawGradient(canvas: Canvas, config: VisualConfig, t: Float) {
        val colors = config.gradientColors.map { it.toInt() }.toIntArray()
        val angle = Math.toRadians(
            (config.gradientAngleDeg + if (config.gradientAnimated) sin(t * 0.08f) * 25f else 0f).toDouble()
        )
        val cx = width / 2f; val cy = height / 2f
        val r = (width + height) * 0.75f
        val dx = (cos(angle) * r).toFloat(); val dy = (sin(angle) * r).toFloat()
        paint.shader = LinearGradient(
            cx - dx, cy - dy, cx + dx, cy + dy, colors, null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        if (config.gradientAnimated) {
            // slow drifting glow
            val gx = cx + cos(t * 0.11f) * width * 0.3f
            val gy = cy + sin(t * 0.07f) * height * 0.25f
            paint.shader = RadialGradient(
                gx, gy, width * 0.9f,
                intArrayOf(0x33FFFFFF.toInt() and colors[colors.size / 2], 0x00000000),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
        }
    }

    private fun drawImage(canvas: Canvas, config: VisualConfig, progress: Float) {
        val bmp = sourceBitmap ?: run { canvas.drawColor(Color.BLACK); return }
        val motion = MotionEffect.valueOf(config.motion)
        val intensity = config.motionIntensity
        // cover-fit base scale
        val base = maxOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        var scale = base
        var tx = (width - bmp.width * base) / 2f
        var ty = (height - bmp.height * base) / 2f
        when (motion) {
            MotionEffect.SLOW_ZOOM -> {
                val z = 1f + 0.12f * intensity * progress
                scale = base * z
                tx = (width - bmp.width * scale) / 2f
                ty = (height - bmp.height * scale) / 2f
            }
            MotionEffect.KEN_BURNS -> {
                val z = 1.06f + 0.14f * intensity * progress
                scale = base * z
                // deterministic pan path derived from image identity
                val seed = (sourceBitmapUri ?: "").hashCode()
                val rnd = Random(seed)
                val fromX = rnd.nextFloat() - 0.5f; val fromY = rnd.nextFloat() - 0.5f
                val toX = rnd.nextFloat() - 0.5f; val toY = rnd.nextFloat() - 0.5f
                val px = fromX + (toX - fromX) * progress
                val py = fromY + (toY - fromY) * progress
                tx = (width - bmp.width * scale) / 2f + px * width * 0.12f * intensity
                ty = (height - bmp.height * scale) / 2f + py * height * 0.12f * intensity
            }
            MotionEffect.PARALLAX, MotionEffect.DRIFT -> {
                val z = 1.10f + 0.05f * intensity
                scale = base * z
                val t = progress * 6.283f
                tx = (width - bmp.width * scale) / 2f + sin(t * 0.5f) * width * 0.05f * intensity
                ty = (height - bmp.height * scale) / 2f + cos(t * 0.35f) * height * 0.04f * intensity
            }
            MotionEffect.NONE -> Unit
        }
        val m = Matrix()
        m.setScale(scale, scale)
        m.postTranslate(tx, ty)
        paint.isFilterBitmap = true
        canvas.drawBitmap(bmp, m, paint)
    }

    private fun drawParticles(canvas: Canvas, config: VisualConfig, t: Float) {
        val count = (140 * config.particleDensity).toInt().coerceIn(20, 300)
        val color = config.particleColorArgb.toInt()
        for (i in 0 until count) {
            val rnd = Random(i * 7919)
            val speed = 0.008f + rnd.nextFloat() * 0.02f
            val phase = rnd.nextFloat()
            val yy = 1f - ((phase + t * speed) % 1f)          // rise slowly
            val xx = rnd.nextFloat() + sin(t * 0.3f + i) * 0.015f
            val size = (1.5f + rnd.nextFloat() * 3.5f)
            val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (1f + rnd.nextFloat()) + i))
            paint.color = color
            paint.alpha = (twinkle * 160 * (0.4f + 0.6f * rnd.nextFloat())).toInt()
            canvas.drawCircle(xx * width, yy * height, size, paint)
        }
        paint.alpha = 255
    }

    private fun drawMotion(canvas: Canvas, config: VisualConfig, t: Float) {
        // layered drifting orbs over the base gradient — a soft "lava" motion bg
        drawGradient(canvas, config, t * 0.5f)
        val colors = config.gradientColors.map { it.toInt() }
        for (i in 0 until 5) {
            val rnd = Random(i * 104729)
            val cx = width * (0.5f + 0.42f * sin(t * (0.05f + rnd.nextFloat() * 0.06f) + i * 2.1f))
            val cy = height * (0.5f + 0.42f * cos(t * (0.04f + rnd.nextFloat() * 0.05f) + i * 1.3f))
            val r = width * (0.35f + 0.2f * rnd.nextFloat())
            val c = colors[i % colors.size]
            paint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf((c and 0x00FFFFFF) or 0x2E000000, c and 0x00FFFFFF),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, paint)
            paint.shader = null
        }
    }

    private fun drawGrain(canvas: Canvas, amount: Float, timeMs: Long) {
        val frames = grainFrames ?: buildGrain().also { grainFrames = it }
        val frame = frames[((timeMs / 42) % frames.size).toInt()]
        grainPaint.alpha = (amount * 70).toInt().coerceIn(0, 255)
        grainPaint.shader = BitmapShader(frame, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), grainPaint)
        grainPaint.shader = null
    }

    private fun buildGrain(): List<Bitmap> {
        val size = 256
        return (0 until 4).map { f ->
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
            val rnd = Random(f * 31337)
            val px = ByteArray(size * size)
            for (i in px.indices) {
                val v = rnd.nextInt(256)
                px[i] = if (v > 232) (v - 160).toByte() else 0
            }
            bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(px))
            bmp
        }
    }

    private fun drawVignette(canvas: Canvas, amount: Float) {
        paint.shader = RadialGradient(
            width / 2f, height / 2f, height * 0.72f,
            intArrayOf(0x00000000, (amount * 200).toInt().coerceIn(0, 255) shl 24),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }
}
