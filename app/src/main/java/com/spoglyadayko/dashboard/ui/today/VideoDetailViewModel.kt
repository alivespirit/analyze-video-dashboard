package com.spoglyadayko.dashboard.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoglyadayko.dashboard.data.api.DashboardApi
import com.spoglyadayko.dashboard.data.api.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class VideoDetailUiState(
    val basename: String = "",
    val videoUrl: String = "",
    val highlightUrl: String? = null,
    val logs: List<LogEntry> = emptyList(),
    val crops: List<String> = emptyList(),
    val frames: List<String> = emptyList(),
    val logsLoading: Boolean = true,
    val cropsLoading: Boolean = true,
    val framesLoading: Boolean = true,
    val highlightLoading: Boolean = true,
    val error: String? = null,
    val copyResult: String? = null,
)

class VideoDetailViewModel(
    private val api: DashboardApi,
    basename: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VideoDetailUiState(
            basename = basename,
            videoUrl = api.videoUrl(basename),
        )
    )
    val uiState: StateFlow<VideoDetailUiState> = _uiState

    init {
        loadLogs()
        loadCrops()
        loadFrames()
        loadHighlight()
    }

    private fun loadLogs() {
        viewModelScope.launch {
            try {
                val resp = api.getVideoLogs(_uiState.value.basename)
                _uiState.value = _uiState.value.copy(logs = resp.entries, logsLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    logsLoading = false,
                    error = e.message,
                )
            }
        }
    }

    private fun loadCrops() {
        viewModelScope.launch {
            try {
                val resp = api.getVideoReidCrops(_uiState.value.basename)
                val urls = resp.crops.map { api.imageUrl(it) }
                _uiState.value = _uiState.value.copy(crops = urls, cropsLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(cropsLoading = false)
            }
        }
    }

    private fun loadFrames() {
        viewModelScope.launch {
            try {
                val resp = api.getVideoFrames(_uiState.value.basename)
                val urls = resp.frames.map { api.imageUrl(it) }
                _uiState.value = _uiState.value.copy(frames = urls, framesLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(framesLoading = false)
            }
        }
    }

    private fun loadHighlight() {
        viewModelScope.launch {
            try {
                val resp = api.getVideoHighlight(_uiState.value.basename)
                val url = resp.highlightUrl?.let { api.highlightUrl(it) }
                _uiState.value = _uiState.value.copy(highlightUrl = url, highlightLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(highlightLoading = false)
            }
        }
    }

    fun copyToGallery(cropUrl: String, target: String) {
        viewModelScope.launch {
            try {
                val imageName = cropUrl.substringAfterLast("/")
                api.copyReidCrop(imageName, target)
                _uiState.value = _uiState.value.copy(
                    copyResult = "Copied to $target gallery",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    copyResult = "Error: ${e.message}",
                )
            }
        }
    }

    fun clearCopyResult() {
        _uiState.value = _uiState.value.copy(copyResult = null)
    }
}
