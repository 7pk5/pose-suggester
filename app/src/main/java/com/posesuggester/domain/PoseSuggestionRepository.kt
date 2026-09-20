package com.posesuggester.domain

import android.graphics.Bitmap
import android.util.Log
import com.posesuggester.domain.model.PoseSuggestion
import com.posesuggester.domain.model.SceneState
import com.posesuggester.refinement.PoseRefiner
import com.posesuggester.util.ConnectivityMonitor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Owns the online/offline decision for the reasoning layer.
 *
 * Tries the cloud source first when the device is online, giving it [CLOUD_TIMEOUT_MS] to
 * answer; on timeout, failure, or no connectivity it falls back to the on-device source, and
 * finally to [fallbackSource], which needs no model and so can always answer. The winning
 * suggestion is handed to the refinement layer before it leaves the repository, so callers only
 * ever see scene-corrected keypoints.
 */
class PoseSuggestionRepository(
    private val cloudSource: PoseSuggestionSource,
    private val onDeviceSource: PoseSuggestionSource,
    private val fallbackSource: PoseSuggestionSource,
    private val connectivity: ConnectivityMonitor,
    private val refiner: PoseRefiner = PoseRefiner(),
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @return the refined suggestion, or a [Result] failure if *both* paths were unusable.
     */
    suspend fun suggestPose(frame: Bitmap, scene: SceneState): Result<PoseSuggestion> =
        withContext(ioDispatcher) {
            val raw = requestFromCloud(frame, scene)
                ?: requestFromDevice(frame, scene)
                ?: requestFrom(fallbackSource, frame, scene)
            if (raw == null) {
                Result.failure(NoPoseSourceAvailableException())
            } else {
                Result.success(raw.copy(keypoints = refiner.refine(raw.keypoints, scene)))
            }
        }

    private suspend fun requestFromCloud(frame: Bitmap, scene: SceneState): PoseSuggestion? {
        if (!connectivity.isOnline()) {
            Log.i(TAG, "Offline — skipping cloud source")
            return null
        }
        if (!cloudSource.isAvailable()) {
            Log.i(TAG, "${cloudSource.name} reports unavailable — skipping")
            return null
        }
        return try {
            withTimeout(CLOUD_TIMEOUT_MS) {
                cloudSource.suggestPose(frame, scene)
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "${cloudSource.name} timed out after ${CLOUD_TIMEOUT_MS}ms")
            null
        } catch (e: Exception) {
            Log.w(TAG, "${cloudSource.name} failed", e)
            null
        }
    }

    private suspend fun requestFromDevice(frame: Bitmap, scene: SceneState): PoseSuggestion? =
        requestFrom(onDeviceSource, frame, scene)

    private suspend fun requestFrom(
        source: PoseSuggestionSource,
        frame: Bitmap,
        scene: SceneState,
    ): PoseSuggestion? {
        if (!source.isAvailable()) {
            Log.i(TAG, "${source.name} reports unavailable — skipping")
            return null
        }
        return try {
            source.suggestPose(frame, scene)
        } catch (e: Exception) {
            Log.e(TAG, "${source.name} failed", e)
            null
        }
    }

    class NoPoseSourceAvailableException :
        IllegalStateException("Neither the cloud nor the on-device pose source could answer")

    private companion object {
        const val TAG = "PoseSuggestionRepo"
        const val CLOUD_TIMEOUT_MS = 4_000L
    }
}
