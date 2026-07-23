package com.quietstudio.feature.music

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietstudio.core.model.MusicTrack
import com.quietstudio.core.music.MusicEngine
import com.quietstudio.core.music.MusicImporter
import com.quietstudio.core.music.MusicLibrary
import com.quietstudio.data.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MusicLibraryViewModel @Inject constructor(
    val library: MusicLibrary,
    val engine: MusicEngine,
    private val repository: MusicRepository,
    private val importer: MusicImporter,
) : ViewModel() {

    val favorites: StateFlow<Set<String>> =
        repository.observeFavorites()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Imported tracks, mirrored into the library so search/byId see them. */
    val imported: StateFlow<List<MusicTrack>> =
        repository.observeImported()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    init {
        viewModelScope.launch {
            imported.collect { library.registerImported(it) }
        }
    }

    /** Copies the picked file into app storage and adds it to the library. */
    fun importTrack(uri: Uri) {
        viewModelScope.launch {
            val track = withContext(Dispatchers.IO) { importer.import(uri) }
            if (track == null) {
                _importError.value = "That file couldn't be read as audio."
            } else {
                repository.addImported(track)
            }
        }
    }

    fun consumeImportError() {
        _importError.value = null
    }

    fun playOrPause(track: MusicTrack) {
        if (engine.nowPlaying.value?.id == track.id) {
            if (engine.isPlaying.value) engine.pause() else engine.resume()
        } else {
            engine.setDuckLevel(0f)
            engine.play(track)
            viewModelScope.launch { repository.notePlayed(track.id) }
        }
    }

    fun shuffle() {
        library.random()?.let { playOrPause(it) }
    }

    fun toggleFavorite(trackId: String) {
        viewModelScope.launch { repository.toggleFavorite(trackId) }
    }

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }
}
