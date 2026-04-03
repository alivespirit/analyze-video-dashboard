package com.spoglyadayko.dashboard.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoglyadayko.dashboard.data.api.DashboardApi
import com.spoglyadayko.dashboard.data.api.TodayVideosResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TodayUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: TodayVideosResponse? = null,
)

class TodayViewModel(private val api: DashboardApi) : ViewModel() {

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState
    private var currentDay: String? = null

    init {
        load(null)
    }

    fun load(day: String?) {
        currentDay = day
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val data = api.getTodayVideos(day = day)
                _uiState.value = TodayUiState(loading = false, data = data)
            } catch (e: Exception) {
                _uiState.value = TodayUiState(loading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun refresh() {
        load(currentDay)
    }
}
