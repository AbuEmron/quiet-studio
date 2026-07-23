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
 * Manages the local Whisper ggml model files.
 *
 * Two catalog models, both explicit user actions to fetch, plus a fully
 * offline "import a ggml-*.bin you already have" path that accepts any model
 * file. The one-time model fetch is the only network call the app can ever
 * make, always user-initiated.
 *
 * Several models may be installed side by side; a marker file records which
 * one is active. Downloading or importing a model makes it active, and the
 * user can switch in Settings without re-downloading.
 */
@Singleton
class WhisperModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** A model the app knows how to fetch. Sizes shown to the user honestly. */
    data class ModelSpec(
        val id: String,
        val label: String,
        val fileName: String,
        val url: String,
        val approxSizeMb: Int,
        val note: String,
        val recommended: Boolean,
    )

    companion object {
        /**
         * Quantized large-v3-turbo: near large-v3 accuracy at a fraction of
         * the decode cost — the practical accuracy ceiling for on-device.
         * q5_0 keeps the download and RAM footprint workable on a phone.
         */
        val TURBO = ModelSpec(
            id = "turbo",
            label = "Whisper large-v3-turbo (q5_0)",
            fileName = "ggml-large-v3-turbo-q5_0.bin",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
            approxSizeMb = 547,
            note = "Best accuracy · needs a reasonably recent phone",
            recommended = true,
        )

        val BASE = ModelSpec(
            id = "base",
            label = "Whisper base",
            fileName = "ggml-base.bin",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
            approxSizeMb = 148,
            note = "Light and fast · good for older phones",
            recommended = false,
        )

        val CATALOG = listOf(TURBO, BASE)

        const val IMPORTED_MODEL_NAME = "ggml-imported.bin"
        private const val ACTIVE_MARKER = "active-model.txt"
        private const val MIN_PLAUSIBLE_BYTES = 1_000_000L
    }

    private val modelsDir: File get() = File(context.filesDir, "models").apply { mkdirs() }

    fun installedModels(): List<File> =
        modelsDir.listFiles { f ->
            f.name.startsWith("ggml-") && f.name.endsWith(".bin") && f.length() > MIN_PLAUSIBLE_BYTES
        }?.sortedBy { it.name }.orEmpty()

    /**
     * The model transcription uses: the marked one when valid, else the
     * largest installed file (the pre-marker behaviour, so existing installs
     * keep working untouched).
     */
    fun activeModel(): File? {
        val marker = File(modelsDir, ACTIVE_MARKER)
        val marked = marker.takeIf { it.exists() }
            ?.let { runCatching { File(modelsDir, it.readText().trim()) }.getOrNull() }
            ?.takeIf { it.exists() && it.length() > MIN_PLAUSIBLE_BYTES }
        return marked ?: installedModels().maxByOrNull { it.length() }
    }

    fun setActive(fileName: String) {
        runCatching { File(modelsDir, ACTIVE_MARKER).writeText(fileName) }
    }

    /** Back-compat name used by readiness checks. */
    fun installedModel(): File? = activeModel()

    /** Fully offline path — any ggml model file the user already has. */
    suspend fun importModel(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(modelsDir, IMPORTED_MODEL_NAME)
            context.contentResolver.openInputStream(uri)!!.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            check(target.length() > MIN_PLAUSIBLE_BYTES) { "That file is too small to be a model" }
            setActive(target.name)
            target
        }
    }

    suspend fun download(spec: ModelSpec, onProgress: (Float) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(modelsDir, spec.fileName)
                val tmp = File(modelsDir, "${spec.fileName}.part")
                val conn = URL(spec.url).openConnection() as HttpURLConnection
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
                check(tmp.length() > MIN_PLAUSIBLE_BYTES) { "Download incomplete" }
                check(tmp.renameTo(target)) { "Could not finalise the model file" }
                setActive(target.name)
                target
            }
        }

    fun deleteModel() {
        modelsDir.listFiles()?.forEach { it.delete() }
    }
}
