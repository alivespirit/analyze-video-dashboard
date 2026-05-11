package com.spoglyadayko.dashboard.ui.overallstats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoglyadayko.dashboard.data.api.DashboardApi
import com.spoglyadayko.dashboard.data.api.OverallStatsResponse
import com.spoglyadayko.dashboard.data.api.ReIDStatsResponse
import com.spoglyadayko.dashboard.data.preferences.SettingsStore
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OverallStatsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val data: OverallStatsResponse? = null,
    val reid: ReIDStatsResponse? = null,
)

class OverallStatsViewModel(
    private val api: DashboardApi,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverallStatsUiState())
    val uiState: StateFlow<OverallStatsUiState> = _uiState

    val reidRange: StateFlow<String> = settingsStore.overallRangeReid.stateIn(
        viewModelScope, SharingStarted.Eagerly, SettingsStore.DEFAULT_OVERALL_RANGE,
    )
    val perDayRange: StateFlow<String> = settingsStore.overallRangePerDay.stateIn(
        viewModelScope, SharingStarted.Eagerly, SettingsStore.DEFAULT_OVERALL_RANGE,
    )
    val processingRange: StateFlow<String> = settingsStore.overallRangeProcessing.stateIn(
        viewModelScope, SharingStarted.Eagerly, SettingsStore.DEFAULT_OVERALL_RANGE,
    )

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val overallDeferred = async { api.getOverallStats() }
                val reidDeferred = async {
                    runCatching { api.getReIDStats() }.getOrNull()
                }
                val overall = overallDeferred.await()
                val reid = reidDeferred.await()
                _uiState.value = OverallStatsUiState(
                    loading = false,
                    data = overall,
                    reid = reid,
                )
            } catch (e: Exception) {
                _uiState.value = OverallStatsUiState(loading = false, error = e.message)
            }
        }
    }

    fun setReidRange(label: String) {
        viewModelScope.launch { settingsStore.setOverallRangeReid(label) }
    }

    fun setPerDayRange(label: String) {
        viewModelScope.launch { settingsStore.setOverallRangePerDay(label) }
    }

    fun setProcessingRange(label: String) {
        viewModelScope.launch { settingsStore.setOverallRangeProcessing(label) }
    }
}
