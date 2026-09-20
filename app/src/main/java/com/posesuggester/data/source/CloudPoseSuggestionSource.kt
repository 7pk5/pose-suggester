package com.posesuggester.data.source

import android.graphics.Bitmap
import android.util.Log
import com.posesuggester.domain.PoseSuggestionSource
import com.posesuggester.domain.model.PoseSuggestion
import com.posesuggester.domain.model.SceneState

/**
 * Cloud vision-LLM path — **stub**. The architecture diagram names Gemini Flash and Claude
 * as candidate endpoints.
 *
 * Replace [suggestPose] with a real HTTP call once the endpoint and key exist. The request
 * should carry the JPEG-encoded [frame] plus a compact serialisation of [scene] (labels, boxes,
 * open regions) and must return a payload that maps onto [PoseSuggestion].
 *
 * The 4s budget lives in the repository, not here — this implementation should simply run to
 * completion and let itself be cancelled.
 */
class CloudPoseSuggestionSource(
    private val endpoint: String? = null,
    private val apiKey: String? = null,
) : PoseSuggestionSource {

    override val name: String = "CloudVisionLLM"

    /** No credentials configured yet, so the repository will always fall through to on-device. */
    override suspend fun isAvailable(): Boolean = !endpoint.isNullOrBlank() && !apiKey.isNullOrBlank()

    override suspend fun suggestPose(frame: Bitmap, scene: SceneState): PoseSuggestion {
        Log.d(TAG, "suggestPose() called with ${scene.objects.size} objects — not implemented yet")
        throw NotImplementedError("Cloud vision LLM is not wired up yet")
    }

    private companion object {
        const val TAG = "CloudPoseSource"
    }
}
