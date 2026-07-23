package com.quietstudio.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietstudio.data.Project
import com.quietstudio.data.ProjectRepository
import com.quietstudio.feature.templates.BuiltInTemplate
import com.quietstudio.feature.templates.PendingTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val pendingTemplate: PendingTemplate,
) : ViewModel() {
    val recentProjects: StateFlow<List<Project>> =
        projectRepository.observeProjects()
            .map { it.take(10) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun duplicate(id: String) = viewModelScope.launch { projectRepository.duplicate(id) }
    fun delete(id: String) = viewModelScope.launch { projectRepository.delete(id) }
    fun useBuiltIn(template: BuiltInTemplate) { pendingTemplate.content = template.content }
}
