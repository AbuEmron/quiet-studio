package com.quietstudio.transcription.whisper

/**
 * Thin JNI surface over vendored whisper.cpp. Loaded lazily so devices
 * without the native lib (or builds with quietstudio.enableWhisper=false)
 * degrade gracefully.
 */
object WhisperBridge {

    @Volatile private var loaded = false

    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("whisper_jni")
            loaded = true
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** @return native context pointer, or 0 on failure */
    external fun initContext(modelPath: String): Long

    external fun freeContext(ctx: Long)

    /**
     * Runs full transcription over 16 kHz mono float PCM.
     * @return segments encoded as lines: "startMs\tendMs\ttext"
     */
    external fun transcribe(ctx: Long, pcm: FloatArray, language: String, threads: Int): String
}
