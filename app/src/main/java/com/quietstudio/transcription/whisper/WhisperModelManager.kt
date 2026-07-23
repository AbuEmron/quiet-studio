package com.quietstudio.transcription.whisper

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the local Whisper ggml model file.
 *
 * Two paths, both explicit user actions:
 *  1. Import a ggml-*.bin the user already has (fully offline, recommended).
 *  2. One-time download of the small multilingual model from a public mirror.
 * The app never touches the network otherwise.
 */
@Singleton
class WhisperModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val DEFAULT_MODEL_NAME = "ggml-base.bin"
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
    }

    private val modelsDir: File get() = File(context.filesDir, "models").apply { mkdirs() }

    fun installedModel(): File? =
        modelsDir.listFiles { f -> f.name.startsWith("ggml-") && f.name.endsWith(".bin") && f.length() > 1_000_000 }
            ?.maxByOrNull { it.length() }

    suspend fun importModel(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(modelsDir, DEFAULT_MODEL_NAME)
            context.contentResolver.openInputStream(uri)!!.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            target
        }
    }

    suspend fun downloadDefaultModel(onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(modelsDir, DEFAULT_MODEL_NAME)
                val tmp = File(modelsDir, "$DEFAULT_MODEL_NAME.part")
                val conn = URL(DEFAULT_MODEL_URL).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(1 shl 16)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                    }
                }
                check(tmp.length() > 1_000_000) { "Download incomplete" }
                tmp.renameTo(target)
                target
            }
        }

    fun deleteModel() {
        modelsDir.listFiles()?.forEach { it.delete() }
    }
}
