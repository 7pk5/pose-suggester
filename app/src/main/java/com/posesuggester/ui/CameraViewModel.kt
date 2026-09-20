package com.posesuggester.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.posesuggester.data.source.CloudPoseSuggestionSource
import com.posesuggester.data.source.OnDevicePoseSuggestionSource
import com.posesuggester.data.source.TemplatePoseSuggestionSource
import com.posesuggester.domain.PoseSuggestionRepository
import com.posesuggester.domain.model.PoseSuggestion
import com.posesuggester.domain.model.SceneState
import com.posesuggester.util.ConnectivityMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the perception snapshot and the current pose suggestion, and mediates between them.
 *
 * Scene updates arrive continuously from the analysis thread; suggestions are requested only on
 * an explicit tap. Both are exposed as [StateFlow] so the view layer just collects and redraws.
 */
class CameraViewModel(
    private val repository: PoseSuggestionRepository,
) : ViewModel() {

    private val _sceneState = MutableStateFlow(SceneState())
    val sceneState: StateFlow<SceneState> = _sceneState.asStateFlow()

    private val _poseSuggestion = MutableStateFlow<PoseSuggestionUiState>(PoseSuggestionUiState.Idle)
    val poseSuggestion: StateFlow<PoseSuggestionUiState> = _poseSuggestion.asStateFlow()

    /**
     * Called from the analysis thread on every completed detection. [MutableStateFlow] is safe to
     * write from any thread, and conflation means a slow collector just sees the newest scene.
     */
    fun onSceneState(scene: SceneState) {
        _sceneState.value = scene
        if (scene.objects.isNotEmpty()) {
            Log.d(
                TAG,
                "Scene: " + scene.objects.joinToString {
                    "${it.label}(${"%.2f".format(it.confidence)}, ${it.depth})"
                } + " | ${scene.openRegions.size} open region(s)",
            )
        }
    }

    /**
     * Runs the reasoning + refinement pipeline against [frame] and the latest scene.
     * Ignored while a request is already in flight, so repeated taps cannot stack up.
     */
    fun requestPoseSuggestion(frame: Bitmap?) {
        if (_poseSuggestion.value is PoseSuggestionUiState.Loading) return
        if (frame == null) {
            _poseSuggestion.value = PoseSuggestionUiState.Error("No camera frame yet")
            return
        }
        _poseSuggestion.value = PoseSuggestionUiState.Loading
        viewModelScope.launch {
            val result = repository.suggestPose(frame, _sceneState.value)
            _poseSuggestion.value = result.fold(
                onSuccess = { PoseSuggestionUiState.Ready(it) },
                onFailure = { PoseSuggestionUiState.Error(it.message ?: "Could not suggest a pose") },
            )
        }
    }

    fun clearSuggestion() {
        _poseSuggestion.value = PoseSuggestionUiState.Idle
    }

    companion object {
        private const val TAG = "CameraViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                CameraViewModel(
                    PoseSuggestionRepository(
                        cloudSource = CloudPoseSuggestionSource(),
                        onDeviceSource = OnDevicePoseSuggestionSource(),
                        fallbackSource = TemplatePoseSuggestionSource(),
                        connectivity = ConnectivityMonitor(app),
                        ioDispatcher = Dispatchers.IO,
                    )
                )
            }
        }
    }
}

/** What the view layer needs to know about the suggestion request. */
sealed interface PoseSuggestionUiState {
    data object Idle : PoseSuggestionUiState
    data object Loading : PoseSuggestionUiState
    data class Ready(val suggestion: PoseSuggestion) : PoseSuggestionUiState
    data class Error(val message: String) : PoseSuggestionUiState
}
