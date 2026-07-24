package com.quietstudio.feature.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.quietstudio.core.audio.WavIo
import com.quietstudio.core.model.MusicSelection
import com.quietstudio.core.model.MusicTrack
import com.quietstudio.core.model.ProjectContent
import com.quietstudio.core.model.SubtitleCue
import com.quietstudio.core.model.SubtitleStyle
import com.quietstudio.core.model.TemplateContent
import com.quietstudio.core.model.VisualConfig
import com.quietstudio.core.model.VisualPack
import com.quietstudio.core.music.MusicEngine
import com.quietstudio.core.music.MusicLibrary
import com.quietstudio.data.ExportQueueRepository
import com.quietstudio.core.media.scenes.AnimatedScenes
import com.quietstudio.data.MusicRepository
import com.quietstudio.data.Project
import com.quietstudio.data.ProjectRepository
import com.quietstudio.data.TemplateRepository
import com.quietstudio.feature.export.ExportWorker
import com.quietstudio.transcription.TranscriptionEngine
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

@OptIn(FlowPreview::class)
@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val projects: ProjectRepository,
    private val templates: TemplateRepository,
    private val exportQueue: ExportQueueRepository,
    private val transcription: TranscriptionEngine,
    val musicLibrary: MusicLibrary,
    val musicEngine: MusicEngine,
    val animatedScenes: AnimatedScenes,
    musicRepository: MusicRepository,
) : ViewModel() {

    val projectId: String = savedStateHandle["projectId"] ?: ""

    init {
        // Imported tracks must resolve here too — a project whose music was
        // imported has to play and label correctly even if the Music screen
        // was never opened this session.
        viewModelScope.launch {
            musicRepository.observeImported().collect { musicLibrary.registerImported(it) }
        }
    }

    data class UiState(
        val project: Project? = null,
        val playing: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 1,
        val transcribing: Boolean = false,
        val message: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val contentFlow = MutableStateFlow<ProjectContent?>(null)

    private var player: ExoPlayer? = null
    private var tickJob: Job? = null
    private var narrationEnvelope: FloatArray = FloatArray(0) // 20ms buckets

    /** Downsampled narration envelope for the timeline waveform. */
    var waveformPreview: List<Float> = emptyList()
        private set

    val content: ProjectContent get() = contentFlow.value ?: ProjectContent()

    init {
        viewModelScope.launch {
            val p = projects.get(projectId) ?: return@launch
            contentFlow.value = p.content
            _ui.value = _ui.value.copy(
                project = p,
                durationMs = p.content.narration.durationMs.coerceAtLeast(1),
            )
            p.content.narration.wavPath?.let { loadEnvelope(File(it)) }
            musicEngine.configureDucking(
                p.content.music.duckingDb, p.content.music.duckAttackMs, p.content.music.duckReleaseMs,
            )
        }
        // Autosave: any edit persists after 1.2s of quiet (opening isn't an edit)
        viewModelScope.launch {
            contentFlow.drop(1).debounce(1200).collect { c ->
                if (c != null && c != _ui.value.project?.content) persist(c)
            }
        }
    }

    private suspend fun persist(c: ProjectContent) {
        val p = _ui.value.project ?: return
        val updated = p.copy(content = c)
        projects.save(updated)
        _ui.value = _ui.value.copy(project = updated)
    }

    private fun mutate(transform: (ProjectContent) -> ProjectContent) {
        contentFlow.value = transform(content)
    }

    /* ------------------------------ edits -------------------------------- */

    fun updateCue(cue: SubtitleCue) =
        mutate { c -> c.copy(cues = c.cues.map { if (it.id == cue.id) cue else it }) }

    fun deleteCue(id: Long) = mutate { c -> c.copy(cues = c.cues.filterNot { it.id == id }) }

    fun addCue(afterId: Long?) = mutate { c ->
        val at = c.cues.indexOfFirst { it.id == afterId }
        val prev = c.cues.getOrNull(at)
        val start = prev?.endMs ?: 0
        val newCue = SubtitleCue(
            id = (c.cues.maxOfOrNull { it.id } ?: 0) + 1,
            startMs = start, endMs = start + 1500, text = "New line",
        )
        val list = c.cues.toMutableList()
        list.add(if (at < 0) list.size else at + 1, newCue)
        c.copy(cues = list)
    }

    fun updateStyle(style: SubtitleStyle) = mutate { it.copy(subtitleStyle = style) }

    fun updateVisual(visual: VisualConfig) = mutate { it.copy(visual = visual) }

    /**
     * Toggles the one-tap professional grade. Turning it on samples a frame of
     * the footage for auto exposure + white balance so the correction is
     * baked once and shared by preview and export.
     */
    fun setEnhanceEnabled(enabled: Boolean) {
        if (!enabled) {
            mutate { it.copy(enhance = it.enhance.copy(enabled = false)) }
            return
        }
        viewModelScope.launch {
            val (autoE, autoW) = com.quietstudio.feature.camera.FrameAnalyzer
                .analyze(context, content.visual.sourceUri)
            mutate {
                it.copy(enhance = it.enhance.copy(enabled = true, autoExposure = autoE, autoWarmth = autoW))
            }
        }
    }

    fun setEnhanceLook(look: String) = mutate {
        it.copy(enhance = it.enhance.copy(look = look, enabled = true))
    }

    fun setEnhanceLetterbox(on: Boolean) = mutate {
        it.copy(enhance = it.enhance.copy(letterbox = on))
    }

    /** Sets a bundled animated scene video as the project background. */
    fun setAnimatedScene(scene: com.quietstudio.core.media.scenes.AnimatedScene) = mutate {
        it.copy(
            visual = it.visual.copy(
                kind = com.quietstudio.core.model.BackgroundKind.ANIMATED.name,
                sourceUri = animatedScenes.uriFor(scene),
            )
        )
    }

    fun updateExportConfig(config: com.quietstudio.core.model.ExportConfig) =
        mutate { it.copy(export = config) }

    fun updateMusic(selection: MusicSelection) {
        mutate { it.copy(music = selection) }
        musicEngine.configureDucking(selection.duckingDb, selection.duckAttackMs, selection.duckReleaseMs)
        musicEngine.baseVolume = selection.volume
    }

    fun selectTrack(track: MusicTrack?) {
        updateMusic(content.music.copy(trackId = track?.id))
        if (track != null && _ui.value.playing) musicEngine.play(track)
        if (track == null) musicEngine.stop()
    }

    fun surpriseMe() {
        musicLibrary.random()?.let { selectTrack(it) }
    }

    fun transcribeNow() {
        val wav = content.narration.wavPath ?: return
        if (!transcription.isAvailable || !transcription.isModelReady()) {
            _ui.value = _ui.value.copy(
                message = "Add a Whisper model in Settings to auto-generate subtitles."
            )
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(transcribing = true)
            transcription.transcribe(File(wav), content.subtitleStyle.maxWordsPerLine)
                .onSuccess { cues -> mutate { it.copy(cues = cues) } }
                .onFailure { _ui.value = _ui.value.copy(message = it.message) }
            _ui.value = _ui.value.copy(transcribing = false)
        }
    }

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    /* ----------------------------- playback ------------------------------ */

    fun togglePlay() {
        if (_ui.value.playing) pause() else play()
    }

    private fun play() {
        val wav = content.narration.wavPath ?: return
        val p = player ?: ExoPlayer.Builder(context).build().also { player = it }
        if (p.mediaItemCount == 0) {
            p.setMediaItem(MediaItem.fromUri("file://$wav"))
            p.prepare()
        }
        p.seekTo(_ui.value.positionMs)
        p.play()
        musicLibrary.byId(content.music.trackId)?.let {
            musicEngine.baseVolume = content.music.volume
            if (musicEngine.nowPlaying.value?.id == it.id) musicEngine.resume()
            else musicEngine.play(it, crossfadeMs = content.music.fadeInMs.toLong())
        }
        _ui.value = _ui.value.copy(playing = true)
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val pos = player?.currentPosition ?: 0
                _ui.value = _ui.value.copy(positionMs = pos)
                // feed ducking from precomputed narration envelope
                val idx = (pos / 20).toInt()
                val level = narrationEnvelope.getOrNull(idx) ?: 0f
                musicEngine.setDuckLevel(if (level > 0.02f) 1f else 0f)
                if (player?.playbackState == Player.STATE_ENDED) {
                    pause(); seekTo(0)
                }
                delay(33)
            }
        }
    }

    fun pause() {
        player?.pause()
        musicEngine.pause()
        tickJob?.cancel()
        _ui.value = _ui.value.copy(playing = false)
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms)
        _ui.value = _ui.value.copy(positionMs = ms)
    }

    private suspend fun loadEnvelope(f: File) = withContext(Dispatchers.Default) {
        runCatching {
            val wav = WavIo.read(f)
            val bucket = wav.sampleRate / 50 // 20 ms
            val out = FloatArray(wav.samples.size / bucket + 1)
            var i = 0
            while (i < wav.samples.size) {
                var peak = 0
                var j = i
                val end = minOf(i + bucket, wav.samples.size)
                while (j < end) { val a = abs(wav.samples[j].toInt()); if (a > peak) peak = a; j++ }
                out[i / bucket] = peak / 32768f
                i = end
            }
            narrationEnvelope = out
            val buckets = 90
            waveformPreview = (0 until buckets).map { i ->
                val idx = (i.toLong() * out.size / buckets).toInt().coerceAtMost(out.size - 1)
                if (idx >= 0) out[idx] else 0f
            }
        }
    }

    /* ------------------------- toolbar actions --------------------------- */

    fun toast(message: String) {
        _ui.value = _ui.value.copy(message = message)
    }

    private fun cueAtPlayhead(): SubtitleCue? {
        val pos = _ui.value.positionMs
        return content.cues.firstOrNull { pos in it.startMs until it.endMs }
    }

    fun splitCueAtPlayhead() {
        val cue = cueAtPlayhead() ?: run { toast("No caption at the playhead"); return }
        val pos = _ui.value.positionMs
        val frac = (pos - cue.startMs).toFloat() / (cue.endMs - cue.startMs)
        val words = cue.text.split(" ")
        if (words.size < 2) { toast("Caption too short to split"); return }
        val cut = (words.size * frac).toInt().coerceIn(1, words.size - 1)
        mutate { c ->
            val maxId = c.cues.maxOfOrNull { it.id } ?: 0
            val first = cue.copy(endMs = pos, text = words.take(cut).joinToString(" "))
            val second = cue.copy(
                id = maxId + 1, startMs = pos, text = words.drop(cut).joinToString(" "),
            )
            c.copy(cues = c.cues.flatMap { if (it.id == cue.id) listOf(first, second) else listOf(it) })
        }
    }

    fun deleteCueAtPlayhead() {
        val cue = cueAtPlayhead() ?: run { toast("No caption at the playhead"); return }
        deleteCue(cue.id)
    }

    fun duplicateProject() {
        viewModelScope.launch {
            projects.duplicate(projectId)
            toast("Project duplicated")
        }
    }

    /* --------------------------- export & reuse --------------------------- */

    fun enqueueExport() {
        val p = _ui.value.project ?: return
        viewModelScope.launch {
            persist(content)
            val jobId = exportQueue.enqueue(p.id, p.title, content.export)
            val request = OneTimeWorkRequestBuilder<ExportWorker>()
                .setInputData(workDataOf(ExportWorker.KEY_JOB_ID to jobId))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("qs-export-$jobId", androidx.work.ExistingWorkPolicy.KEEP, request)
            _ui.value = _ui.value.copy(message = "Export queued — rendering in background.")
        }
    }

    fun saveAsTemplate(name: String) {
        viewModelScope.launch {
            templates.saveTemplate(
                name,
                TemplateContent(
                    subtitleStyle = content.subtitleStyle,
                    visual = content.visual,
                    music = content.music,
                    export = content.export,
                ),
            )
            _ui.value = _ui.value.copy(message = "Template saved.")
        }
    }

    fun saveVisualPack(name: String) {
        viewModelScope.launch {
            templates.saveVisualPack(
                VisualPack(UUID.randomUUID().toString(), name, content.visual, content.subtitleStyle)
            )
            _ui.value = _ui.value.copy(message = "Visual pack saved.")
        }
    }

    fun renameProject(title: String) {
        viewModelScope.launch {
            projects.rename(projectId, title)
            _ui.value = _ui.value.copy(project = _ui.value.project?.copy(title = title))
        }
    }

    override fun onCleared() {
        player?.release()
        musicEngine.stop()
        super.onCleared()
    }

    /** Expose content as state for Compose. */
    val contentState: StateFlow<ProjectContent?> = contentFlow.asStateFlow()
}
