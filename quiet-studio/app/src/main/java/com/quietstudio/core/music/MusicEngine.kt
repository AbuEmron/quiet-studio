package com.quietstudio.core.music

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.quietstudio.core.model.MusicTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Live music playback for browsing and editor preview.
 *
 * Two ExoPlayers alternate so track changes crossfade instead of cutting.
 * Seamless looping uses REPEAT_MODE_ONE over gapless OGG loops. Ducking is
 * driven externally (the editor feeds narration levels through [setDuckLevel])
 * with attack/release smoothing here, mirroring the offline export mix.
 */
@Singleton
class MusicEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null
    private var active = 0 // 0 -> A, 1 -> B
    private var fadeJob: Job? = null
    private var duckJob: Job? = null

    private val _nowPlaying = MutableStateFlow<MusicTrack?>(null)
    val nowPlaying: StateFlow<MusicTrack?> = _nowPlaying.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    var baseVolume: Float = 0.8f
        set(value) { field = value.coerceIn(0f, 1f); applyVolume() }

    private var duckTarget = 1f      // 1 = no duck
    private var duckCurrent = 1f
    private var duckGain = 0.25f     // -12 dB
    private var duckAttackMs = 120
    private var duckReleaseMs = 450

    private fun newPlayer(): ExoPlayer = ExoPlayer.Builder(context).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
    }

    private fun activePlayer(): ExoPlayer? = if (active == 0) playerA else playerB

    fun play(track: MusicTrack, crossfadeMs: Long = 1600) {
        scope.launch {
            val uri = if (track.isImported) "file://${track.file}"
            else "asset:///${track.file}"
            val incoming = newPlayer()
            incoming.setMediaItem(MediaItem.fromUri(uri))
            incoming.volume = 0f
            incoming.prepare()
            incoming.play()

            val outgoing = activePlayer()
            if (active == 0) { playerB?.release(); playerB = incoming; active = 1 }
            else { playerA?.release(); playerA = incoming; active = 0 }

            _nowPlaying.value = track
            _isPlaying.value = true

            fadeJob?.cancel()
            fadeJob = scope.launch {
                val steps = 24
                for (s in 1..steps) {
                    if (!isActive) break
                    val k = s / steps.toFloat()
                    incoming.volume = k * baseVolume * duckCurrent
                    outgoing?.volume = (1 - k) * baseVolume * duckCurrent
                    delay(crossfadeMs / steps)
                }
                outgoing?.pause()
                startDuckLoop()
            }
        }
    }

    fun pause() {
        scope.launch {
            activePlayer()?.pause()
            _isPlaying.value = false
        }
    }

    fun resume() {
        scope.launch {
            activePlayer()?.play()
            _isPlaying.value = _nowPlaying.value != null
        }
    }

    fun stop() {
        scope.launch {
            fadeJob?.cancel(); duckJob?.cancel()
            playerA?.release(); playerB?.release()
            playerA = null; playerB = null
            _nowPlaying.value = null
            _isPlaying.value = false
        }
    }

    /** Configure ducking behaviour (from the project's MusicSelection). */
    fun configureDucking(duckDb: Float, attackMs: Int, releaseMs: Int) {
        duckGain = 10f.pow(duckDb / 20f)
        duckAttackMs = attackMs
        duckReleaseMs = releaseMs
    }

    /** narrationLevel: 0 = silence, 1 = speaking. */
    fun setDuckLevel(narrationLevel: Float) {
        duckTarget = 1f + (duckGain - 1f) * narrationLevel.coerceIn(0f, 1f)
    }

    private fun startDuckLoop() {
        if (duckJob?.isActive == true) return
        duckJob = scope.launch {
            val tickMs = 30L
            while (isActive) {
                val rate = if (duckTarget < duckCurrent)
                    tickMs.toFloat() / duckAttackMs else tickMs.toFloat() / duckReleaseMs
                duckCurrent += (duckTarget - duckCurrent).coerceIn(-rate, rate)
                applyVolume()
                delay(tickMs)
            }
        }
    }

    private fun applyVolume() {
        activePlayer()?.volume = (baseVolume * duckCurrent).coerceIn(0f, 1f)
    }
}
