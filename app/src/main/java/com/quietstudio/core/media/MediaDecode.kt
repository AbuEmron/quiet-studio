package com.quietstudio.core.media

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * Decodes any platform-supported compressed audio (OGG, AAC, MP3, FLAC…)
 * to mono float PCM at a caller-chosen sample rate. Used to pull bundled
 * music tracks and imported audio into the offline mixdown.
 */
object MediaDecode {

    fun decodeAsset(context: Context, assetPath: String, targetRate: Int): FloatArray {
        val afd = context.assets.openFd(assetPath)
        return decode({ ex -> ex.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }, targetRate)
            .also { afd.close() }
    }

    fun decodeFile(file: File, targetRate: Int): FloatArray =
        decode({ ex -> ex.setDataSource(file.absolutePath) }, targetRate)

    private fun decode(setSource: (MediaExtractor) -> Unit, targetRate: Int): FloatArray {
        val extractor = MediaExtractor()
        setSource(extractor)
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                trackIndex = i; format = f; break
            }
        }
        require(trackIndex >= 0 && format != null) { "No audio track" }
        extractor.selectTrack(trackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val out = ArrayList<Short>(1 shl 20)
        var srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIdx >= 0 -> {
                    val buf = codec.getOutputBuffer(outIdx)!!
                    val sb: ShortBuffer = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val chunk = ShortArray(sb.remaining())
                    sb.get(chunk)
                    // downmix to mono on the fly
                    if (channels == 1) chunk.forEach { out.add(it) }
                    else {
                        var i = 0
                        while (i + channels <= chunk.size) {
                            var acc = 0
                            for (c in 0 until channels) acc += chunk[i + c].toInt()
                            out.add((acc / channels).toShort())
                            i += channels
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true
                }
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val nf = codec.outputFormat
                    srcRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        }
        codec.stop(); codec.release(); extractor.release()

        val mono = FloatArray(out.size) { out[it] / 32768f }
        return if (srcRate == targetRate) mono else resampleLinear(mono, srcRate, targetRate)
    }

    fun resampleLinear(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || input.isEmpty()) return input
        val outLen = (input.size.toLong() * to / from).toInt()
        val out = FloatArray(outLen)
        val ratio = from.toDouble() / to
        for (i in 0 until outLen) {
            val pos = i * ratio
            val i0 = pos.toInt().coerceAtMost(input.size - 1)
            val i1 = (i0 + 1).coerceAtMost(input.size - 1)
            val frac = (pos - i0).toFloat()
            out[i] = input[i0] * (1 - frac) + input[i1] * frac
        }
        return out
    }
}
