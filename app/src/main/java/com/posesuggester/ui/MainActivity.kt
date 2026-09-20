package com.posesuggester.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.posesuggester.camera.CameraController
import com.posesuggester.databinding.ActivityMainBinding
import com.posesuggester.perception.ObjectDetectionAnalyzer
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The single camera screen: live preview, shutter, and the "Suggest pose" trigger.
 *
 * The overlay renderer is not wired up yet — for now the suggestion result is surfaced as text so
 * the reasoning path is observable end to end.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var analysisExecutor: ExecutorService
    private var cameraController: CameraController? = null
    private var analyzer: ObjectDetectionAnalyzer? = null

    private val viewModel: CameraViewModel by viewModels { CameraViewModel.Factory }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                binding.statusText.text = getString(com.posesuggester.R.string.permission_denied)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Detection runs on its own single thread so it can never stall the camera pipeline.
        analysisExecutor = Executors.newSingleThreadExecutor()

        binding.captureButton.setOnClickListener { takePhoto() }
        binding.suggestButton.setOnClickListener {
            viewModel.requestPoseSuggestion(analyzer?.latestFrame)
        }

        observeViewModel()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val detectionAnalyzer = ObjectDetectionAnalyzer(
            context = this,
            onSceneState = viewModel::onSceneState,
        )
        analyzer = detectionAnalyzer

        cameraController = CameraController(
            context = this,
            lifecycleOwner = this,
            previewView = binding.previewView,
            analysisExecutor = analysisExecutor,
            analyzer = detectionAnalyzer,
        ).also { controller ->
            controller.start(onError = {
                binding.statusText.text = getString(com.posesuggester.R.string.camera_error)
            })
        }
        binding.statusText.text = getString(com.posesuggester.R.string.status_ready)
    }

    private fun takePhoto() {
        cameraController?.takePhoto(
            onSaved = { _ ->
                Toast.makeText(this, getString(com.posesuggester.R.string.photo_saved), Toast.LENGTH_SHORT).show()
            },
            onError = {
                Toast.makeText(this, it.message ?: getString(com.posesuggester.R.string.camera_error), Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.poseSuggestion.collect { state ->
                        binding.statusText.text = when (state) {
                            is PoseSuggestionUiState.Idle -> getString(com.posesuggester.R.string.status_ready)
                            is PoseSuggestionUiState.Loading -> getString(com.posesuggester.R.string.status_thinking)
                            is PoseSuggestionUiState.Ready -> state.suggestion.poseDescription
                            is PoseSuggestionUiState.Error -> state.message
                        }
                        // The overlay only ever shows a settled suggestion, never a half-finished one.
                        if (state is PoseSuggestionUiState.Ready) {
                            val scene = viewModel.sceneState.value
                            binding.poseOverlay.setPose(
                                state.suggestion.keypoints,
                                scene.frameWidth,
                                scene.frameHeight,
                            )
                        } else {
                            binding.poseOverlay.clear()
                        }
                    }
                }
                launch {
                    viewModel.sceneState.collect { scene ->
                        // The overlay renderer will consume this; for now it drives the object count.
                        binding.sceneText.text = getString(
                            com.posesuggester.R.string.scene_summary,
                            scene.objects.size,
                            scene.openRegions.size,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraController?.stop()
        analyzer?.close()
        analysisExecutor.shutdown()
    }
}
