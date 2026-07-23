package com.quietstudio.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietstudio.transcription.whisper.WhisperModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val modelManager: WhisperModelManager,
) : ViewModel() {

    data class UiState(
        val modelInstalled: Boolean = false,
        val downloadProgress: Float? = null,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(UiState(modelInstalled = modelManager.installedModel() != null))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            modelManager.importModel(uri)
                .onSuccess { _ui.value = UiState(modelInstalled = true) }
                .onFailure { _ui.value = _ui.value.copy(error = it.message) }
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(downloadProgress = 0f, error = null)
            modelManager.downloadDefaultModel { p ->
                _ui.value = _ui.value.copy(downloadProgress = p)
            }
                .onSuccess { _ui.value = UiState(modelInstalled = true) }
                .onFailure {
                    _ui.value = UiState(
                        modelInstalled = modelManager.installedModel() != null,
                        error = it.message ?: "Download failed",
                    )
                }
        }
    }
}
