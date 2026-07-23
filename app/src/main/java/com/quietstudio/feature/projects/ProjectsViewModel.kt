package com.quietstudio.feature.projects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietstudio.core.database.FolderEntity
import com.quietstudio.core.database.ProjectVersionEntity
import com.quietstudio.data.Project
import com.quietstudio.data.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository,
) : ViewModel() {

    var query by mutableStateOf("")
        private set
    var folderId by mutableStateOf<String?>(null)
        private set

    private val filter = MutableStateFlow(Pair<String?, String>(null, ""))

    val projects: StateFlow<List<Project>> = filter
        .flatMapLatest { (folder, q) -> repository.observeProjects(folder, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> =
        repository.observeFolders()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _versionsFor =
        MutableStateFlow<Pair<String, List<ProjectVersionEntity>>?>(null)
    val versionsFor: StateFlow<Pair<String, List<ProjectVersionEntity>>?> = _versionsFor

    fun setSearch(q: String) {
        query = q
        filter.value = folderId to q
    }

    fun setFolder(id: String?) {
        folderId = id
        filter.value = id to query
    }

    fun duplicate(id: String) = viewModelScope.launch { repository.duplicate(id) }
    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }
    fun createFolder(name: String) = viewModelScope.launch { repository.createFolder(name) }

    fun showVersions(projectId: String) = viewModelScope.launch {
        _versionsFor.value = projectId to repository.observeVersions(projectId).first()
    }

    fun hideVersions() {
        _versionsFor.value = null
    }

    fun restore(projectId: String, version: ProjectVersionEntity) = viewModelScope.launch {
        repository.restoreVersion(projectId, version)
        hideVersions()
    }
}
