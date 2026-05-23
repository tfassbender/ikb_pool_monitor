package com.tfassbender.ikbpool.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tfassbender.ikbpool.data.PoolRepository
import com.tfassbender.ikbpool.domain.RecommendationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PoolViewModel(
    private val repository: PoolRepository,
    private val engine: RecommendationEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(PoolUiState())
    val state: StateFlow<PoolUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val status = repository.load()
                val rec = engine.evaluate(status)
                _state.update {
                    PoolUiState(
                        isRefreshing = false,
                        hasLoadedOnce = true,
                        status = status,
                        recommendation = rec,
                        errorMessage = null,
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = t.message ?: t.javaClass.simpleName,
                    )
                }
            }
        }
    }
}
