package com.spoglyadayko.dashboard.ui.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoglyadayko.dashboard.data.api.DashboardApi
import com.spoglyadayko.dashboard.data.api.MonitoringResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MonitoringUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val data: MonitoringResponse? = null,
)

class MonitoringViewModel(private val api: DashboardApi) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitoringUiState())
    val uiState: StateFlow<MonitoringUiState> = _uiState

    init {
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                load(showLoading = false)
                delay(15_000)
            }
        }
    }

    private suspend fun load(showLoading: Boolean) {
        if (showLoading) {
            _uiState.value = _uiState.value.copy(loading = true)
        }
        try {
            val data = api.getMonitoring()
            _uiState.value = MonitoringUiState(loading = false, data = data)
        } catch (e: Exception) {
            _uiState.value = MonitoringUiState(
                loading = false,
                error = e.message,
                data = _uiState.value.data,
            )
        }
    }

    fun refresh() {
        viewModelScope.launch { load(showLoading = true) }
    }
}
