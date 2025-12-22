package com.curvsurf.pointcloudrecorder_galaxyxr

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed class UIEvent {
    data object ExportPointCloud: UIEvent()
    data object ClearPoints: UIEvent()
}

enum class MapDisplayMode {
    DEPTH_MAP, CONFIDENCE_MAP
}

data class UIState(
    val exporting: Boolean = false,
    val mapDisplayMode: MapDisplayMode = MapDisplayMode.DEPTH_MAP,
    val recordingEnabled: Boolean = true,
    val pointsVisible: Boolean = true
)

class AppViewModel: ViewModel() {

    private val _uiEvent = MutableSharedFlow<UIEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    var uiEvent = _uiEvent.asSharedFlow()

    fun exportPointCloud() {
        _uiEvent.tryEmit(UIEvent.ExportPointCloud)
    }

    fun clearPoints() {
        _uiEvent.tryEmit(UIEvent.ClearPoints)
    }

    private val _uiState = MutableStateFlow(UIState())
    var uiState = _uiState.asStateFlow()

    fun setExporting(exporting: Boolean) {
        _uiState.update { it.copy(exporting = exporting) }
    }

    fun setMapDisplayMode(mapDisplayMode: MapDisplayMode) {
        _uiState.update { it.copy(mapDisplayMode = mapDisplayMode) }
    }

    fun setRecordingEnabled(recordingEnabled: Boolean) {
        _uiState.update { it.copy(recordingEnabled = recordingEnabled) }
    }

    fun setPointVisible(visible: Boolean) {
        _uiState.update { it.copy(pointsVisible = visible) }
    }

    private val _exportProgress = MutableStateFlow(0f)
    var exportProgress = _exportProgress.asStateFlow()
    fun setExportProgress(progress: Float) {
        _exportProgress.update { progress }
    }

    private val _pointCount = MutableStateFlow(0)
    var pointCount = _pointCount.asStateFlow()
    fun setPointCount(count: Int) {
        _pointCount.update { count }
    }
}