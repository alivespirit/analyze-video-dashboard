package com.spoglyadayko.dashboard.ui.gatecrossings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoglyadayko.dashboard.data.api.DashboardApi
import com.spoglyadayko.dashboard.data.api.GateCrossingEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class GateCrossingItem(
    val entry: GateCrossingEntry,
    val cropUrls: List<String>, // Full URLs resolved via api.imageUrl()
)

data class GateCrossingsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val day: String? = null,
    val items: List<GateCrossingItem> = emptyList(),
    val copyResult: String? = null,
)

class GateCrossingsViewModel(
    private val api: DashboardApi,
    private val day: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GateCrossingsUiState())
    val uiState: StateFlow<GateCrossingsUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            try {
                val resp = api.getGateCrossings(day = day)
                val items = resp.gateCrossings.map { entry ->
                    GateCrossingItem(
                        entry = entry,
                        cropUrls = entry.crops.map { api.imageUrl(it) },
                    )
                }
                _uiState.value = GateCrossingsUiState(
                    loading = false,
                    day = resp.day,
                    items = items,
                )
            } catch (e: Exception) {
                _uiState.value = GateCrossingsUiState(loading = false, error = e.message)
            }
        }
    }

    fun copyToGallery(cropUrl: String, target: String) {
        viewModelScope.launch {
            try {
                val imageName = cropUrl.substringAfterLast("/")
                api.copyReidCrop(imageName, target)
                _uiState.value = _uiState.value.copy(copyResult = "Copied to $target gallery")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(copyResult = "Error: ${e.message}")
            }
        }
    }

    fun clearCopyResult() {
        _uiState.value = _uiState.value.copy(copyResult = null)
    }
}
