package com.spoglyadayko.dashboard.ui.todaystats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoglyadayko.dashboard.data.api.DashboardApi
import com.spoglyadayko.dashboard.data.api.TodayStatsResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TodayStatsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: TodayStatsResponse? = null,
)

class TodayStatsViewModel(private val api: DashboardApi) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayStatsUiState())
    val uiState: StateFlow<TodayStatsUiState> = _uiState
    private var currentDay: String? = null

    init {
        load(null)
    }

    fun load(day: String?) {
        currentDay = day
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val data = api.getTodayStats(day = day)
                _uiState.value = TodayStatsUiState(loading = false, data = data)
            } catch (e: Exception) {
                _uiState.value = TodayStatsUiState(loading = false, error = e.message)
            }
        }
    }

    fun refresh() {
        load(currentDay)
    }
}
