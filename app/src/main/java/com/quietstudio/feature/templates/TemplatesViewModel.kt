package com.quietstudio.feature.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietstudio.core.database.TemplateEntity
import com.quietstudio.core.model.TemplateContent
import com.quietstudio.data.TemplateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the template chosen for the next new project. RecordViewModel-created
 * projects check [PendingTemplate] and inherit its style.
 */
@Singleton
class PendingTemplate @Inject constructor() {
    @Volatile var content: TemplateContent? = null
}

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repository: TemplateRepository,
    private val pending: PendingTemplate,
) : ViewModel() {

    val templates: StateFlow<List<Pair<TemplateEntity, TemplateContent?>>> =
        repository.observeTemplates()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun useTemplate(id: String) {
        viewModelScope.launch { pending.content = repository.getTemplate(id) }
    }

    fun delete(id: String) = viewModelScope.launch { repository.deleteTemplate(id) }
}
