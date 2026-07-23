package com.quietstudio.core.media

import android.content.Context
import com.quietstudio.core.audio.WavIo
import com.quietstudio.core.model.MusicSelection
import com.quietstudio.core.model.MusicTrack
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/**
 * Offline narration + music mixdown with sidechain ducking.
 *
 * The music bed is looped seamlessly to cover the narration length, faded
 * in/out, and ducked under speech using an envelope follower on the
 * narration with configurable attack/release. Output is a single stereo
 * WAV that the export pipeline muxes as the final audio track.
 */
object AudioMixdown {

    private const val SR = 48000

    fun render(
        context: Context,
        narrationWav: File,
        track: MusicTrack?,
        selection: MusicSelection,
        outFile: File,
        tailMs: Int = 1500,
    ): File {
        val narration = WavIo.read(narrationWav)
        val voice = WavIo.toFloat(narration.samples).let {
            if (narration.sampleRate == SR) it
            else MediaDecode.resampleLinear(it, narration.sampleRate, SR)
        }
        val totalLen = voice.size + tailMs * SR / 1000

        val mixL = FloatArray(totalLen)
        val mixR = FloatArray(totalLen)
        for (i in voice.indices) { mixL[i] += voice[i]; mixR[i] += voice[i] }

        if (track != null) {
            val music = if (track.isImported)
                MediaDecode.decodeFile(File(track.file), SR)
            else
                MediaDecode.decodeAsset(context, track.file, SR)
            if (music.isNotEmpty()) {
                val duckGain = duckEnvelope(voice, totalLen, selection)
                val fadeIn = selection.fadeInMs * SR / 1000
                val fadeOut = selection.fadeOutMs * SR / 1000
                for (i in 0 until totalLen) {
                    var g = selection.volume * duckGain[i]
                    if (i < fadeIn) g *= i / fadeIn.toFloat()
                    val fromEnd = totalLen - i
                    if (fromEnd < fadeOut) g *= fromEnd / fadeOut.toFloat()
                    val m = music[i % music.size] * g
                    mixL[i] += m
                    mixR[i] += m
                }
            }
        }

        // interleave + safety limit
        val stereo = ShortArray(totalLen * 2)
        for (i in 0 until totalLen) {
            stereo[i * 2] = soft(mixL[i])
            stereo[i * 2 + 1] = soft(mixR[i])
        }
        WavIo.write(outFile, stereo, SR, channels = 2)
        return outFile
    }

    /** Envelope follower over narration -> per-sample music gain. */
    private fun duckEnvelope(voice: FloatArray, totalLen: Int, sel: MusicSelection): FloatArray {
        val duckGain = 10.0.pow(sel.duckingDb / 20.0).toFloat() // e.g. -12dB -> 0.25
        val attack = (sel.duckAttackMs * SR / 1000).coerceAtLeast(1)
        val release = (sel.duckReleaseMs * SR / 1000).coerceAtLeast(1)
        val aCoef = 1f / attack
        val rCoef = 1f / release
        val out = FloatArray(totalLen)
        // block-wise speech detector (10 ms)
        val block = SR / 100
        var env = 0f          // 0 = no speech, 1 = speech
        var i = 0
        while (i < totalLen) {
            var peak = 0f
            val end = min(i + block, totalLen)
            for (j in i until min(end, voice.size)) peak = maxOf(peak, abs(voice[j]))
            val speaking = peak > 0.02f
            for (j in i until end) {
                env = if (speaking) min(1f, env + aCoef) else maxOf(0f, env - rCoef)
                out[j] = 1f + (duckGain - 1f) * env
            }
            i = end
        }
        return out
    }

    private fun soft(x: Float): Short {
        val y = if (abs(x) <= 0.9f) x else {
            val s = if (x > 0) 1f else -1f
            s * (0.9f + 0.1f * kotlin.math.tanh((abs(x) - 0.9f) / 0.1f))
        }
        return (y.coerceIn(-1f, 1f) * 32767).toInt().toShort()
    }
}
