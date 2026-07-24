package com.quietstudio.core.media

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.quietstudio.R
import com.quietstudio.core.model.SubtitleAnimation
import com.quietstudio.core.model.SubtitleCue
import com.quietstudio.core.model.SubtitlePosition
import com.quietstudio.core.model.SubtitleStyle
import kotlin.math.min

/**
 * Paints styled subtitle cues onto a Canvas at a given playback time.
 * Shared by editor preview and export, so burned-in captions match the
 * preview pixel-for-pixel (modulo resolution).
 *
 * Visual language follows the reference design: Poppins captions, rounded
 * backdrop card, gold emphasis word, alignment + margin controls.
 */
class SubtitlePainter(
    private val width: Int,
    private val height: Int,
    private val context: Context? = null,
) {

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val typefaceCache = HashMap<String, Typeface>()

    fun typefaceFor(fontId: String): Typeface = typefaceCache.getOrPut(fontId) {
        val res = context?.let {
            when (fontId) {
                "poppins_bold" -> ResourcesCompat.getFont(it, R.font.poppins_bold)
                "poppins_semibold" -> ResourcesCompat.getFont(it, R.font.poppins_semibold)
                "poppins_medium" -> ResourcesCompat.getFont(it, R.font.poppins_medium)
                "inter" -> ResourcesCompat.getFont(it, R.font.inter_variable)
                else -> null
            }
        }
        res ?: when (fontId) {
            "serif" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
            "mono" -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            "condensed" -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
            "handwritten" -> Typeface.create("cursive", Typeface.BOLD)
            "display" -> Typeface.create("sans-serif-black", Typeface.BOLD)
            "light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.BOLD)
        }
    }

    fun draw(canvas: Canvas, cues: List<SubtitleCue>, style: SubtitleStyle, timeMs: Long) {
        val cue = cues.firstOrNull { timeMs in it.startMs until it.endMs } ?: return
        val elapsed = timeMs - cue.startMs
        val remaining = cue.endMs - timeMs
        val animation = runCatching { SubtitleAnimation.valueOf(style.animation) }
            .getOrDefault(SubtitleAnimation.FADE)

        var alpha = 1f
        var scale = 1f
        var dy = 0f
        var visibleChars = Int.MAX_VALUE
        var karaokeFrac = 1f
        when (animation) {
            SubtitleAnimation.NONE -> Unit
            SubtitleAnimation.FADE ->
                alpha = min(1f, elapsed / 220f) * min(1f, remaining / 220f)
            SubtitleAnimation.POP -> {
                val k = min(1f, elapsed / 180f)
                scale = 0.7f + 0.3f * overshoot(k)
                alpha = min(1f, elapsed / 120f)
            }
            SubtitleAnimation.SLIDE_UP -> {
                val k = min(1f, elapsed / 260f)
                dy = (1f - ease(k)) * 60f
                alpha = k
            }
            SubtitleAnimation.KARAOKE ->
                karaokeFrac = (elapsed.toFloat() / (cue.endMs - cue.startMs)).coerceIn(0f, 1f)
            SubtitleAnimation.TYPEWRITER ->
                visibleChars = (elapsed / 1000f * 30f).toInt().coerceAtLeast(1)
        }

        var fullText = if (style.allCaps) cue.text.uppercase() else cue.text
        if (visibleChars < fullText.length) fullText = fullText.substring(0, visibleChars)
        val emphasized = if (style.highlightEveryNthWord > 0)
            fullText.split(Regex("\\s+")).maxByOrNull { it.length } else null

        val sizePx = style.sizeSp / 24f * (width * 0.05f)
        textPaint.apply {
            typeface = typefaceFor(style.fontId)
            textSize = sizePx * scale
            textAlign = Paint.Align.LEFT
            if (style.shadow) setShadowLayer(
                style.shadowRadius * width / 1080f, 0f, sizePx * 0.06f, 0xCC000000.toInt()
            ) else clearShadowLayer()
        }

        val marginX = width * (style.marginXPct / 100f)
        val maxLineWidth = width - marginX * 2 - sizePx
        val lines = wrap(fullText, style.maxWordsPerLine, maxLineWidth)
        if (lines.isEmpty()) return
        val lineH = textPaint.fontSpacing * 1.08f
        val blockH = lineH * lines.size
        val marginY = height * (style.marginYPct / 100f)

        var maxW = 0f
        for (l in lines) maxW = maxOf(maxW, textPaint.measureText(l))
        val padX = sizePx * 0.7f
        val padY = sizePx * 0.5f

        // ── Placement ───────────────────────────────────────────────────────
        // Custom (posX, posY) is the block's CENTRE in frame fractions; the
        // legacy enum path is untouched so old projects render byte-for-byte
        // where they always did. Both preview and export call this method with
        // their own width/height, so the fraction maths agrees by construction.
        var cx = width / 2f
        var baseY: Float
        if (style.hasCustomPosition) {
            // Never let the block leave the frame: clamp the centre so text
            // (plus the pill, when shown) stays inside a safe margin.
            val safe = width * SAFE_MARGIN_FRAC
            val halfW = maxW / 2f + if (style.backgroundPill) padX else 0f
            cx = if (halfW * 2f > width - 2f * safe) width / 2f
            else (style.posX * width).coerceIn(safe + halfW, width - safe - halfW)

            val vPad = if (style.backgroundPill) padY * 0.4f else 0f
            val topLimit = height * SAFE_MARGIN_FRAC + vPad + blockH / 2f
            val bottomLimit = height - height * SAFE_MARGIN_FRAC - vPad - blockH / 2f
            val cy = (style.posY * height).coerceIn(
                topLimit, maxOf(topLimit, bottomLimit),
            )
            baseY = cy - blockH / 2f + lineH * 0.8f + dy * height / 1920f
        } else {
            baseY = when (
                runCatching { SubtitlePosition.valueOf(style.position) }.getOrDefault(SubtitlePosition.CENTER)
            ) {
                SubtitlePosition.TOP -> marginY + lineH
                SubtitlePosition.CENTER -> height / 2f - blockH / 2f + lineH * 0.8f
                SubtitlePosition.BOTTOM -> height - marginY - blockH + lineH * 0.8f
            } + dy * height / 1920f
        }

        val a255 = (alpha * 255).toInt().coerceIn(0, 255)

        if (style.backgroundPill) {
            pillPaint.color = 0xFF17171E.toInt()
            pillPaint.alpha = (alpha * style.backgroundOpacity.coerceIn(0f, 1f) * 255).toInt()
            canvas.drawRoundRect(
                RectF(
                    cx - maxW / 2f - padX, baseY - lineH * 0.95f - padY * 0.4f,
                    cx + maxW / 2f + padX, baseY + blockH - lineH * 0.6f + padY * 0.4f,
                ),
                sizePx * 0.45f, sizePx * 0.45f, pillPaint,
            )
        }

        val outlineW = when (style.outlineMode) {
            "LIGHT" -> width / 540f * 1.2f
            "BOLD" -> width / 540f * 3f
            else -> 0f
        }

        lines.forEachIndexed { li, line ->
            val y = baseY + li * lineH
            val words = line.split(" ")
            val spaceW = textPaint.measureText(" ")
            val lineW = textPaint.measureText(line)
            val x = when (style.justify) {
                "LEFT" -> cx - maxW / 2f
                "RIGHT" -> cx + maxW / 2f - lineW
                else -> cx - lineW / 2f
            }

            if (animation == SubtitleAnimation.KARAOKE) {
                // dim pass then clipped bright pass over the whole line
                drawLineWords(canvas, words, x, y, spaceW, style, emphasized, (a255 * 0.45f).toInt(), outlineW)
                canvas.save()
                canvas.clipRect(x, y - lineH, x + lineW * karaokeFrac, y + lineH * 0.4f)
                drawLineWords(canvas, words, x, y, spaceW, style, emphasized, a255, outlineW)
                canvas.restore()
            } else {
                drawLineWords(canvas, words, x, y, spaceW, style, emphasized, a255, outlineW)
            }
        }
        textPaint.alpha = 255
    }

    private fun drawLineWords(
        canvas: Canvas,
        words: List<String>,
        startX: Float,
        y: Float,
        spaceW: Float,
        style: SubtitleStyle,
        emphasized: String?,
        alpha: Int,
        outlineW: Float,
    ) {
        var x = startX
        for (w in words) {
            if (outlineW > 0f) {
                outlinePaint.set(textPaint)
                outlinePaint.style = Paint.Style.STROKE
                outlinePaint.strokeWidth = outlineW
                outlinePaint.color = style.outlineColorArgb.toInt()
                outlinePaint.alpha = alpha
                outlinePaint.clearShadowLayer()
                canvas.drawText(w, x, y, outlinePaint)
            }
            textPaint.color =
                if (emphasized != null && w == emphasized) style.highlightColorArgb.toInt()
                else style.colorArgb.toInt()
            textPaint.alpha = alpha
            canvas.drawText(w, x, y, textPaint)
            x += textPaint.measureText(w) + spaceW
        }
    }

    /** Word-count wrap, then split any line that still overflows the width. */
    private fun wrap(text: String, maxWords: Int, maxWidth: Float): List<String> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val byCount = words.chunked(maxWords.coerceAtLeast(1)).map { it.joinToString(" ") }
        val out = ArrayList<String>()
        for (line in byCount) {
            if (textPaint.measureText(line) <= maxWidth) { out.add(line); continue }
            var cur = StringBuilder()
            for (w in line.split(" ")) {
                val candidate = if (cur.isEmpty()) w else "$cur $w"
                if (textPaint.measureText(candidate) > maxWidth && cur.isNotEmpty()) {
                    out.add(cur.toString()); cur = StringBuilder(w)
                } else cur = StringBuilder(candidate)
            }
            if (cur.isNotEmpty()) out.add(cur.toString())
        }
        return out
    }

    companion object {
        /** Fraction of the frame kept clear around a custom-placed block. */
        const val SAFE_MARGIN_FRAC = 0.03f
    }

    private fun ease(k: Float) = 1f - (1f - k) * (1f - k) * (1f - k)
    private fun overshoot(k: Float): Float {
        val t = k - 1f
        return t * t * (2.7f * t + 1.7f) + 1f
    }
}
