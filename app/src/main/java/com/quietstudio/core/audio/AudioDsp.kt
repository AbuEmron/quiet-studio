package com.quietstudio.core.audio

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.PI
import kotlin.math.sin

/**
 * Offline voice DSP: spectral-subtraction noise reduction, loudness
 * normalization, and silence trimming. Pure Kotlin, no native deps —
 * fast enough for narration-length clips and fully on-device.
 */
object AudioDsp {

    /* ------------------------------ FFT ---------------------------------- */

    private fun fft(re: FloatArray, im: FloatArray, invert: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j or bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val ang = 2.0 * PI / len * (if (invert) 1 else -1)
            val wr = cos(ang).toFloat(); val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curR = 1f; var curI = 0f
                for (k in 0 until len / 2) {
                    val uR = re[i + k]; val uI = im[i + k]
                    val vR = re[i + k + len / 2] * curR - im[i + k + len / 2] * curI
                    val vI = re[i + k + len / 2] * curI + im[i + k + len / 2] * curR
                    re[i + k] = uR + vR; im[i + k] = uI + vI
                    re[i + k + len / 2] = uR - vR; im[i + k + len / 2] = uI - vI
                    val nR = curR * wr - curI * wi
                    curI = curR * wi + curI * wr; curR = nR
                }
                i += len
            }
            len = len shl 1
        }
        if (invert) for (i in 0 until n) { re[i] /= n; im[i] /= n }
    }

    /* --------------------- spectral noise reduction ---------------------- */

    /**
     * Classic spectral subtraction with over-subtraction + floor. The noise
     * profile is learned from the quietest 10% of frames, so no separate
     * "record room tone" step is needed.
     */
    fun reduceNoise(input: FloatArray, strength: Float = 1.0f): FloatArray {
        val frame = 1024
        val hop = frame / 4
        if (input.size < frame * 4) return input.copyOf()
        val window = FloatArray(frame) { (0.5 - 0.5 * cos(2.0 * PI * it / frame)).toFloat() }

        val frames = (input.size - frame) / hop + 1
        val mags = Array(frames) { FloatArray(frame / 2 + 1) }
        val energies = FloatArray(frames)

        // Pass 1: measure per-frame spectra
        val re = FloatArray(frame); val im = FloatArray(frame)
        for (f in 0 until frames) {
            val off = f * hop
            for (i in 0 until frame) { re[i] = input[off + i] * window[i]; im[i] = 0f }
            fft(re, im, false)
            var e = 0f
            for (k in 0..frame / 2) {
                val m = sqrt(re[k] * re[k] + im[k] * im[k])
                mags[f][k] = m; e += m * m
            }
            energies[f] = e
        }

        // Noise profile = mean magnitude of quietest decile
        val sorted = energies.sortedArray()
        val cut = sorted[max(1, frames / 10) - 1]
        val noise = FloatArray(frame / 2 + 1)
        var count = 0
        for (f in 0 until frames) if (energies[f] <= cut) {
            for (k in noise.indices) noise[k] += mags[f][k]
            count++
        }
        if (count == 0) return input.copyOf()
        for (k in noise.indices) noise[k] /= count

        // Pass 2: subtract & resynthesize (overlap-add)
        val out = FloatArray(input.size)
        val norm = FloatArray(input.size)
        val alpha = 1.6f * strength   // over-subtraction
        val floor = 0.06f             // spectral floor
        for (f in 0 until frames) {
            val off = f * hop
            for (i in 0 until frame) { re[i] = input[off + i] * window[i]; im[i] = 0f }
            fft(re, im, false)
            for (k in 0..frame / 2) {
                val m = sqrt(re[k] * re[k] + im[k] * im[k])
                val g = if (m > 1e-9f) max(floor, (m - alpha * noise[k]) / m) else 0f
                val kk = if (k == 0 || k == frame / 2) k else frame - k
                re[k] *= g; im[k] *= g
                if (kk != k) { re[kk] *= g; im[kk] *= g }
            }
            fft(re, im, true)
            for (i in 0 until frame) {
                out[off + i] += re[i] * window[i]
                norm[off + i] += window[i] * window[i]
            }
        }
        for (i in out.indices) if (norm[i] > 1e-6f) out[i] /= norm[i]
        return out
    }

    /* ----------------------- loudness normalize -------------------------- */

    /** RMS-based normalize to ~-19 dBFS RMS (≈ -16 LUFS for speech) with a
     *  soft-knee peak limiter. */
    fun normalizeLoudness(input: FloatArray, targetRmsDb: Float = -19f): FloatArray {
        var sum = 0.0
        var n = 0
        // gate: only count frames above -45 dB so silence doesn't skew RMS
        val frame = 2400
        var i = 0
        while (i + frame <= input.size) {
            var e = 0.0
            for (j in i until i + frame) e += (input[j] * input[j]).toDouble()
            val rms = sqrt(e / frame)
            if (rms > 10.0.pow(-45.0 / 20.0)) { sum += e; n += frame }
            i += frame
        }
        if (n == 0) return input.copyOf()
        val rms = sqrt(sum / n)
        val gain = (10.0.pow(targetRmsDb / 20.0) / max(rms, 1e-9)).toFloat()
        val out = FloatArray(input.size)
        for (k in input.indices) {
            val x = input[k] * gain
            // soft limiter
            out[k] = if (abs(x) <= 0.95f) x else {
                val s = if (x > 0) 1f else -1f
                s * (0.95f + 0.05f * kotlin.math.tanh((abs(x) - 0.95f) / 0.05f))
            }
        }
        return out
    }

    /* -------------------------- silence trim ------------------------------ */

    /**
     * Trims leading/trailing silence and shortens internal pauses longer
     * than [maxPauseMs] down to [maxPauseMs], with short crossfades.
     */
    fun trimSilence(
        input: FloatArray,
        sampleRate: Int,
        thresholdDb: Float = -40f,
        maxPauseMs: Int = 550,
        padMs: Int = 120,
    ): FloatArray {
        val win = sampleRate / 100 // 10ms
        val thr = 10.0.pow(thresholdDb / 20.0).toFloat()
        val frames = input.size / win
        if (frames == 0) return input.copyOf()
        val voiced = BooleanArray(frames)
        for (f in 0 until frames) {
            var peak = 0f
            for (j in f * win until min((f + 1) * win, input.size)) peak = max(peak, abs(input[j]))
            voiced[f] = peak > thr
        }
        val pad = padMs / 10
        val keep = BooleanArray(frames)
        for (f in 0 until frames) if (voiced[f]) {
            for (p in max(0, f - pad)..min(frames - 1, f + pad)) keep[p] = true
        }
        // shorten long internal gaps
        val maxGap = maxPauseMs / 10
        val out = ArrayList<Float>(input.size)
        var gapLen = 0
        var started = false
        val fade = win // 10 ms crossfade
        for (f in 0 until frames) {
            if (keep[f]) {
                started = true; gapLen = 0
                for (j in f * win until min((f + 1) * win, input.size)) out.add(input[j])
            } else if (started) {
                gapLen++
                if (gapLen <= maxGap) {
                    for (j in f * win until min((f + 1) * win, input.size)) out.add(input[j])
                }
            }
        }
        // remove trailing kept-gap
        var end = out.size
        var trailingPeak: Float
        while (end > win) {
            trailingPeak = 0f
            for (j in end - win until end) trailingPeak = max(trailingPeak, abs(out[j]))
            if (trailingPeak > thr) break
            end -= win
        }
        val result = FloatArray(min(end + pad * win, out.size))
        for (k in result.indices) result[k] = out[k]
        // fade edges
        for (k in 0 until min(fade, result.size)) {
            result[k] *= k / fade.toFloat()
            result[result.size - 1 - k] *= k / fade.toFloat()
        }
        return result
    }

    fun peakDb(input: FloatArray): Float {
        var p = 0f
        for (x in input) p = max(p, abs(x))
        return 20f * log10(max(p, 1e-9f))
    }
}
