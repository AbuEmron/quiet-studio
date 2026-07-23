package com.quietstudio.feature.record

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietstudio.core.audio.AudioDsp
import com.quietstudio.core.audio.AudioRecorderEngine
import com.quietstudio.core.audio.EditHistory
import com.quietstudio.core.audio.WavIo
import com.quietstudio.core.media.MediaDecode
import com.quietstudio.core.model.NarrationInfo
import com.quietstudio.core.model.ProjectContent
import com.quietstudio.core.model.SubtitleCue
import com.quietstudio.data.ProjectRepository
import com.quietstudio.transcription.TranscriptionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class RecordViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val recorder: AudioRecorderEngine,
    private val projects: ProjectRepository,
    private val transcription: TranscriptionEngine,
    private val pendingTemplate: com.quietstudio.feature.templates.PendingTemplate,
) : ViewModel() {

    data class UiState(
        val stage: Stage = Stage.READY,
        val busyLabel: String? = null,
        val durationMs: Long = 0,
        val waveform: List<Float> = emptyList(),
        val canUndo: Boolean = false,
        val canRedo: Boolean = false,
        val transcribing: Boolean = false,
        val transcriptionProgress: Float = 0f,
        val error: String? = null,
    )

    enum class Stage { READY, RECORDING, REVIEW }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val workDir = File(context.filesDir, "recordings").apply { mkdirs() }
    private var history = EditHistory(File(context.cacheDir, "history_${System.currentTimeMillis()}"))
    private var currentWav: File? = null

    init {
        viewModelScope.launch {
            recorder.state.collect { s ->
                when (s) {
                    is AudioRecorderEngine.State.Recording ->
                        _ui.value = _ui.value.copy(stage = Stage.RECORDING, durationMs = s.elapsedMs)
                    is AudioRecorderEngine.State.Done -> {
                        currentWav = s.file
                        history.init(s.file)
                        loadWaveform(s.file)
                        _ui.value = _ui.value.copy(
                            stage = Stage.REVIEW, durationMs = s.durationMs,
                            canUndo = false, canRedo = false,
                        )
                    }
                    is AudioRecorderEngine.State.Error ->
                        _ui.value = _ui.value.copy(error = s.message, stage = Stage.READY)
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            recorder.amplitudes.collect { amps ->
                if (_ui.value.stage == Stage.RECORDING) _ui.value = _ui.value.copy(waveform = amps)
            }
        }
    }

    fun startRecording() {
        recorder.start(File(workDir, "rec_${System.currentTimeMillis()}.wav"))
    }

    fun stopRecording() = recorder.stop()

    fun discard() {
        recorder.reset()
        history.clear()
        currentWav = null
        _ui.value = UiState()
    }

    /** From REVIEW: throw the take away and immediately arm a fresh one. */
    fun discardAndRerecord() {
        discard()
        startRecording()
    }

    fun importAudio(uri: Uri) {
        runDsp("Importing") { _ ->
            val pcm = MediaDecode.decodeFile(copyToCache(uri), WavIo.SAMPLE_RATE)
            pcm
        }
    }

    private fun copyToCache(uri: Uri): File {
        val f = File(context.cacheDir, "import_${System.currentTimeMillis()}")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            f.outputStream().use { input.copyTo(it) }
        }
        return f
    }

    fun applyNoiseReduction() = runDsp("Reducing noise") { AudioDsp.reduceNoise(it) }

    fun applyNormalize() = runDsp("Normalizing") { AudioDsp.normalizeLoudness(it) }

    fun applyTrimSilence() = runDsp("Trimming silence") {
        AudioDsp.trimSilence(it, WavIo.SAMPLE_RATE)
    }

    private fun runDsp(label: String, op: (FloatArray) -> FloatArray) {
        val src = currentWav
        viewModelScope.launch(Dispatchers.Default) {
            _ui.value = _ui.value.copy(busyLabel = label)
            try {
                val input: FloatArray = if (label == "Importing") {
                    op(FloatArray(0))
                } else {
                    if (src == null) return@launch
                    op(WavIo.toFloat(WavIo.read(src).samples))
                }
                val out = history.nextSnapshotFile()
                WavIo.write(out, WavIo.toShort(input))
                if (label == "Importing") history.init(out) else history.commit(out)
                currentWav = out
                loadWaveform(out)
                _ui.value = _ui.value.copy(
                    stage = Stage.REVIEW,
                    busyLabel = null,
                    durationMs = input.size.toLong() * 1000 / WavIo.SAMPLE_RATE,
                    canUndo = history.canUndo, canRedo = history.canRedo,
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busyLabel = null, error = e.message)
            }
        }
    }

    fun undo() {
        history.undo()?.let { switchTo(it) }
    }

    fun redo() {
        history.redo()?.let { switchTo(it) }
    }

    private fun switchTo(f: File) {
        currentWav = f
        viewModelScope.launch(Dispatchers.Default) {
            loadWaveform(f)
            val wav = WavIo.read(f)
            _ui.value = _ui.value.copy(
                durationMs = wav.durationMs,
                canUndo = history.canUndo, canRedo = history.canRedo,
            )
        }
    }

    private suspend fun loadWaveform(f: File) = withContext(Dispatchers.Default) {
        val wav = WavIo.read(f)
        val buckets = 160
        val step = (wav.samples.size / buckets).coerceAtLeast(1)
        val wf = ArrayList<Float>(buckets)
        var i = 0
        while (i < wav.samples.size && wf.size < buckets) {
            var peak = 0
            var j = i
            while (j < minOf(i + step, wav.samples.size)) {
                val a = Math.abs(wav.samples[j].toInt()); if (a > peak) peak = a; j++
            }
            wf.add(peak / 32768f)
            i += step
        }
        _ui.value = _ui.value.copy(waveform = wf)
    }

    /**
     * Creates the project (with auto-transcription when available) and
     * returns its id via [onCreated].
     */
    fun finish(onCreated: (String) -> Unit) {
        val wav = currentWav ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busyLabel = "Creating project", transcribing = true)
            // keep the final file out of cache
            val kept = File(workDir, "narration_${System.currentTimeMillis()}.wav")
            withContext(Dispatchers.IO) { wav.copyTo(kept, overwrite = true) }
            val duration = _ui.value.durationMs

            var cues: List<SubtitleCue> = emptyList()
            if (transcription.isAvailable && transcription.isModelReady()) {
                transcription.transcribe(kept, onProgress = {
                    _ui.value = _ui.value.copy(transcriptionProgress = it)
                }).onSuccess { cues = it }
            }

            val title = "Take " + java.text.SimpleDateFormat("MMM d · HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            val template = pendingTemplate.content.also { pendingTemplate.content = null }
            val project = projects.create(
                title,
                ProjectContent(
                    narration = NarrationInfo(kept.absolutePath, duration, processed = true),
                    cues = cues,
                    subtitleStyle = template?.subtitleStyle ?: com.quietstudio.core.model.SubtitleStyle(),
                    visual = template?.visual ?: com.quietstudio.core.model.VisualConfig(),
                    music = template?.music ?: com.quietstudio.core.model.MusicSelection(),
                    export = template?.export ?: com.quietstudio.core.model.ExportConfig(),
                ),
            )
            history.clear()
            _ui.value = _ui.value.copy(busyLabel = null, transcribing = false)
            onCreated(project.id)
        }
    }
}
