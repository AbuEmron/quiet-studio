package com.quietstudio.feature.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietstudio.core.model.MusicTrack
import com.quietstudio.core.music.MusicEngine
import com.quietstudio.core.music.MusicLibrary
import com.quietstudio.data.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicLibraryViewModel @Inject constructor(
    val library: MusicLibrary,
    val engine: MusicEngine,
    private val repository: MusicRepository,
) : ViewModel() {

    val favorites: StateFlow<Set<String>> =
        repository.observeFavorites()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

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
