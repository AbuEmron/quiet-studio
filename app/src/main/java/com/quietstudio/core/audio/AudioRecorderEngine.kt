package com.quietstudio.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * High-quality voice recorder: 48 kHz mono 16-bit PCM straight to WAV,
 * VOICE_RECOGNITION source (unprocessed, close-mic friendly), with the
 * platform NoiseSuppressor attached when available. Emits live amplitude
 * buckets for the waveform display.
 */
@Singleton
class AudioRecorderEngine @Inject constructor() {

    sealed interface State {
        data object Idle : State
        data class Recording(val elapsedMs: Long) : State
        data class Done(val file: File, val durationMs: Long) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    /** Rolling per-40ms peak levels 0..1 for live waveform. */
    val amplitudes: StateFlow<List<Float>> = _amplitudes.asStateFlow()

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    fun start(outFile: File) {
        if (job != null) return
        _amplitudes.value = emptyList()
        job = scope.launch {
            val sr = WavIo.SAMPLE_RATE
            val minBuf = AudioRecord.getMinBufferSize(
                sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf * 4, sr) // ≥0.5 s buffer
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                _state.value = State.Error("Microphone unavailable")
                job = null
                return@launch
            }
            if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(record.audioSessionId) }
            if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(record.audioSessionId) }

            val bucket = sr * 40 / 1000 // 40 ms
            val buf = ShortArray(bucket)
            var total = 0L
            val amps = ArrayList<Float>()

            record.startRecording()
            _state.value = State.Recording(0)
            DataOutputStream(FileOutputStream(outFile).buffered()).use { out ->
                // placeholder header, patched on stop
                out.write(ByteArray(44))
                while (isActive) {
                    val n = record.read(buf, 0, bucket)
                    if (n == 0) continue
                    if (n < 0) {
                        _state.value = State.Error("Recording failed ($n)")
                        break
                    }
                    var peak = 0
                    for (i in 0 until n) {
                        val s = buf[i].toInt()
                        out.writeByte(s and 0xFF)
                        out.writeByte((s shr 8) and 0xFF)
                        val a = abs(s)
                        if (a > peak) peak = a
                    }
                    total += n
                    amps.add(peak / 32768f)
                    if (amps.size > 400) amps.subList(0, amps.size - 400).clear()
                    _amplitudes.value = amps.toList()
                    _state.value = State.Recording(total * 1000 / sr)
                }
            }
            record.stop(); record.release()
            patchWavHeader(outFile, total.toInt(), sr)
            _state.value = State.Done(outFile, total * 1000 / sr)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun reset() {
        stop()
        _state.value = State.Idle
        _amplitudes.value = emptyList()
    }

    private fun patchWavHeader(file: File, sampleCount: Int, sr: Int) {
        val dataSize = sampleCount * 2
        java.io.RandomAccessFile(file, "rw").use { raf ->
            val bb = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            bb.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
            bb.put("fmt ".toByteArray()).putInt(16)
                .putShort(1).putShort(1).putInt(sr)
                .putInt(sr * 2).putShort(2).putShort(16)
            bb.put("data".toByteArray()).putInt(dataSize)
            raf.seek(0)
            raf.write(bb.array())
        }
    }
}
