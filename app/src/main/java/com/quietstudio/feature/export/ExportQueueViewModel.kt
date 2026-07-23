package com.quietstudio.feature.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quietstudio.core.database.ExportJobEntity
import com.quietstudio.data.ExportQueueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExportQueueViewModel @Inject constructor(
    private val repository: ExportQueueRepository,
) : ViewModel() {

    val jobs: StateFlow<List<ExportJobEntity>> =
        repository.observeJobs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearFinished() = viewModelScope.launch { repository.clearFinished() }
}
