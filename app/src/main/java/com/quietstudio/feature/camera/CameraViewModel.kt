package com.quietstudio.feature.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val importer: RecordedClipImporter,
) : ViewModel() {

    data class UiState(
        val recording: Boolean = false,
        val elapsedMs: Long = 0,
        val lensFront: Boolean = false,
        val importing: Boolean = false,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun setRecording(on: Boolean) {
        _ui.value = _ui.value.copy(recording = on, elapsedMs = if (on) 0 else _ui.value.elapsedMs)
    }

    fun setElapsed(ms: Long) {
        _ui.value = _ui.value.copy(elapsedMs = ms)
    }

    fun setLensFront(front: Boolean) {
        _ui.value = _ui.value.copy(lensFront = front)
    }

    fun setError(message: String?) {
        _ui.value = _ui.value.copy(error = message)
    }

    /** Imports the recorded clip into a new project and hands back its id. */
    fun importClip(file: File, onCreated: (String) -> Unit) {
        if (_ui.value.importing) return
        _ui.value = _ui.value.copy(importing = true, recording = false)
        viewModelScope.launch {
            try {
                val project = importer.createProject(file)
                onCreated(project.id)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message ?: "Could not import the recording")
            } finally {
                _ui.value = _ui.value.copy(importing = false)
            }
        }
    }
}
