package com.quietstudio.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietstudio.transcription.whisper.WhisperModelManager
import com.quietstudio.transcription.whisper.WhisperModelManager.ModelSpec
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

    /** One row per catalog model, plus the imported file when present. */
    data class ModelRow(
        val spec: ModelSpec?,
        val fileName: String,
        val label: String,
        val note: String,
        val installed: Boolean,
        val active: Boolean,
        val recommended: Boolean,
        val approxSizeMb: Int,
    )

    data class UiState(
        val rows: List<ModelRow> = emptyList(),
        val modelInstalled: Boolean = false,
        /** File name currently downloading, or null. */
        val downloadingFile: String? = null,
        val downloadProgress: Float? = null,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    private fun refresh() {
        val installed = modelManager.installedModels().map { it.name }.toSet()
        val active = modelManager.activeModel()?.name
        val rows = WhisperModelManager.CATALOG.map { spec ->
            ModelRow(
                spec = spec,
                fileName = spec.fileName,
                label = spec.label,
                note = spec.note,
                installed = spec.fileName in installed,
                active = spec.fileName == active,
                recommended = spec.recommended,
                approxSizeMb = spec.approxSizeMb,
            )
        } + listOfNotNull(
            WhisperModelManager.IMPORTED_MODEL_NAME.takeIf { it in installed }?.let {
                ModelRow(
                    spec = null,
                    fileName = it,
                    label = "Imported model",
                    note = "Your own ggml file",
                    installed = true,
                    active = it == active,
                    recommended = false,
                    approxSizeMb = 0,
                )
            }
        )
        _ui.value = _ui.value.copy(rows = rows, modelInstalled = active != null)
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            modelManager.importModel(uri)
                .onSuccess { _ui.value = _ui.value.copy(error = null); refresh() }
                .onFailure { _ui.value = _ui.value.copy(error = it.message) }
        }
    }

    fun download(spec: ModelSpec) {
        if (_ui.value.downloadingFile != null) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(
                downloadingFile = spec.fileName, downloadProgress = 0f, error = null,
            )
            modelManager.download(spec) { p ->
                _ui.value = _ui.value.copy(downloadProgress = p)
            }
                .onSuccess {
                    _ui.value = _ui.value.copy(downloadingFile = null, downloadProgress = null)
                    refresh()
                }
                .onFailure {
                    _ui.value = _ui.value.copy(
                        downloadingFile = null,
                        downloadProgress = null,
                        error = it.message ?: "Download failed",
                    )
                    refresh()
                }
        }
    }

    /** Switch between already-installed models without re-downloading. */
    fun useModel(fileName: String) {
        modelManager.setActive(fileName)
        refresh()
    }
}
