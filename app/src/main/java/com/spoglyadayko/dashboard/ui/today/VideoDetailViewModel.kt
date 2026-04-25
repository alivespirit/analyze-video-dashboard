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
    val poseClips: List<String> = emptyList(),
    val logsLoading: Boolean = true,
    val cropsLoading: Boolean = true,
    val framesLoading: Boolean = true,
    val poseLoading: Boolean = true,
    val highlightLoading: Boolean = true,
    val error: String? = null,
    val copyResult: String? = null,
)

class VideoDetailViewModel(
    private val api: DashboardApi,
    basename: String,
    private val day: String? = null,
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
        loadPoseClips()
        loadHighlight()
    }

    private fun loadLogs() {
        viewModelScope.launch {
            try {
                val resp = api.getVideoLogs(_uiState.value.basename, day = day)
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

    private fun loadPoseClips() {
        viewModelScope.launch {
            try {
                val resp = api.getVideoPoseClips(_uiState.value.basename)
                val urls = resp.clips.map { api.highlightUrl(it) }
                _uiState.value = _uiState.value.copy(poseClips = urls, poseLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(poseLoading = false)
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

    fun galleryImageUrl(target: String, filename: String): String =
        api.galleryImageUrl(target, filename)

    fun deleteGalleryCrop(target: String, filename: String) {
        viewModelScope.launch {
            try {
                api.deleteGalleryCrop(target, filename)
                _uiState.value = _uiState.value.copy(
                    copyResult = "Deleted $filename from $target gallery",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    copyResult = "Delete failed: ${e.message}",
                )
            }
        }
    }
}
