package com.posesuggester.perception

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.posesuggester.domain.model.SceneState
import java.io.Closeable

/**
 * Streams CameraX analysis frames into the MediaPipe Object Detector and publishes the resulting
 * [SceneState].
 *
 * Runs entirely on the analysis executor plus MediaPipe's own callback thread, so nothing here
 * touches the camera or main thread. Frames arrive faster than the detector can consume them;
 * [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST] on the use case means we simply drop the backlog
 * rather than queueing latency.
 *
 * @param onSceneState called from a background thread every time a detection completes.
 */
class ObjectDetectionAnalyzer(
    context: Context,
    private val onSceneState: (SceneState) -> Unit,
) : ImageAnalysis.Analyzer, Closeable {

    /**
     * The most recent upright frame, kept so the reasoning layer has something to send when the
     * user taps "Suggest pose". Written from the analysis thread, read from the ViewModel.
     */
    @Volatile
    var latestFrame: Bitmap? = null
        private set

    /** MediaPipe requires strictly increasing timestamps; frame timestamps can repeat. */
    private var lastTimestampMs = 0L

    private val detector: ObjectDetector? = try {
        ObjectDetector.createFromOptions(
            context,
            ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET)
                        .setDelegate(Delegate.CPU)
                        .build()
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setScoreThreshold(SCORE_THRESHOLD)
                .setMaxResults(MAX_RESULTS)
                .setResultListener { result, input ->
                    onSceneState(
                        SceneStateBuilder.build(
                            detections = result.detections(),
                            frameWidth = input.width,
                            frameHeight = input.height,
                            timestampMs = result.timestampMs(),
                        )
                    )
                }
                .setErrorListener { e -> Log.e(TAG, "Detector error", e) }
                .build()
        )
    } catch (e: Exception) {
        // A missing or corrupt model must not take the camera down with it.
        Log.e(TAG, "Failed to create ObjectDetector — detection disabled", e)
        null
    }

    override fun analyze(image: ImageProxy) {
        val detector = detector
        if (detector == null) {
            image.close()
            return
        }
        try {
            val upright = image.toUprightBitmap()
            latestFrame = upright

            // Close as early as possible — MediaPipe works off our own bitmap copy, not the proxy.
            image.close()

            val timestamp = nextTimestamp()
            detector.detectAsync(BitmapImageBuilder(upright).build(), timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze frame", e)
            // close() is idempotent, so this is safe even if we already closed above.
            image.close()
        }
    }

    /**
     * Converts the RGBA_8888 analysis frame to a bitmap rotated to match what the user sees.
     * Detection boxes are only meaningful against the upright image.
     */
    private fun ImageProxy.toUprightBitmap(): Bitmap {
        val bitmap = toBitmap()
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    @Synchronized
    private fun nextTimestamp(): Long {
        lastTimestampMs = maxOf(lastTimestampMs + 1, System.currentTimeMillis())
        return lastTimestampMs
    }

    override fun close() {
        detector?.close()
        latestFrame = null
    }

    private companion object {
        const val TAG = "ObjectDetectionAnalyzer"
        const val MODEL_ASSET = "efficientdet_lite0.tflite"
        const val SCORE_THRESHOLD = 0.5f
        const val MAX_RESULTS = 8
    }
}
