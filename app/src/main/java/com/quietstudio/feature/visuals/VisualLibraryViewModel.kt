package com.quietstudio.feature.visuals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietstudio.core.media.scenes.AnimatedScene
import com.quietstudio.core.media.scenes.AnimatedScenes
import com.quietstudio.core.model.VisualPack
import com.quietstudio.data.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VisualLibraryViewModel @Inject constructor(
    private val repository: TemplateRepository,
    animatedScenes: AnimatedScenes,
) : ViewModel() {

    /** Bundled animated scene videos, for the Animated tab. */
    val animated: List<AnimatedScene> = animatedScenes.scenes
    fun animatedUri(scene: AnimatedScene): String = AnimatedScenes.ASSET_PREFIX + scene.assetPath

    val packs: StateFlow<List<VisualPack>> =
        repository.observeVisualPacks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: String) = viewModelScope.launch { repository.deleteVisualPack(id) }

    fun savePack(pack: VisualPack) = viewModelScope.launch { repository.saveVisualPack(pack) }
}
