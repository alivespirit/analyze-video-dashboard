package com.spoglyadayko.dashboard.ui.overallstats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoglyadayko.dashboard.data.api.DashboardApi
import com.spoglyadayko.dashboard.data.api.OverallStatsResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class OverallStatsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: OverallStatsResponse? = null,
)

class OverallStatsViewModel(private val api: DashboardApi) : ViewModel() {

    private val _uiState = MutableStateFlow(OverallStatsUiState())
    val uiState: StateFlow<OverallStatsUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val data = api.getOverallStats()
                _uiState.value = OverallStatsUiState(loading = false, data = data)
            } catch (e: Exception) {
                _uiState.value = OverallStatsUiState(loading = false, error = e.message)
            }
        }
    }
}
