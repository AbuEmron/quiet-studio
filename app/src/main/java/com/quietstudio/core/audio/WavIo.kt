package com.quietstudio.core.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal 16-bit PCM WAV reader/writer (mono or stereo). */
object WavIo {

    const val SAMPLE_RATE = 48000

    data class Wav(val sampleRate: Int, val channels: Int, val samples: ShortArray) {
        val durationMs: Long get() = samples.size.toLong() * 1000 / (sampleRate * channels)
    }

    fun read(file: File): Wav {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(12)
            raf.readFully(header)
            require(String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE") {
                "Not a WAV file: ${file.name}"
            }
            var sampleRate = SAMPLE_RATE
            var channels = 1
            var bits = 16
            var data: ShortArray? = null
            val chunk = ByteArray(8)
            while (raf.filePointer < raf.length() - 8) {
                raf.readFully(chunk)
                val id = String(chunk, 0, 4)
                val size = ByteBuffer.wrap(chunk, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(size)
                        raf.readFully(fmt)
                        val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        bb.short // audio format
                        channels = bb.short.toInt()
                        sampleRate = bb.int
                        bb.int; bb.short
                        bits = bb.short.toInt()
                    }
                    "data" -> {
                        val raw = ByteArray(size)
                        raf.readFully(raw)
                        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
                        val out = ShortArray(size / 2)
                        for (i in out.indices) out[i] = bb.short
                        data = out
                    }
                    else -> raf.seek(raf.filePointer + size)
                }
            }
            require(bits == 16) { "Only 16-bit PCM supported" }
            return Wav(sampleRate, channels, data ?: ShortArray(0))
        }
    }

    fun write(file: File, samples: ShortArray, sampleRate: Int = SAMPLE_RATE, channels: Int = 1) {
        val dataSize = samples.size * 2
        val bb = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(channels.toShort()).putInt(sampleRate)
            .putInt(sampleRate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        bb.put("data".toByteArray()).putInt(dataSize)
        for (s in samples) bb.putShort(s)
        file.writeBytes(bb.array())
    }

    fun toFloat(samples: ShortArray): FloatArray =
        FloatArray(samples.size) { samples[it] / 32768f }

    fun toShort(samples: FloatArray): ShortArray =
        ShortArray(samples.size) {
            (samples[it].coerceIn(-1f, 1f) * 32767).toInt().toShort()
        }
}
