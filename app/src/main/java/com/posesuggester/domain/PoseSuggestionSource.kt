package com.posesuggester.domain

import android.graphics.Bitmap
import com.posesuggester.domain.model.PoseSuggestion
import com.posesuggester.domain.model.SceneState

/**
 * The reasoning layer's single seam.
 *
 * Both the cloud vision LLM and the on-device VLM implement this, so [PoseSuggestionRepository]
 * can swap between them without knowing what is behind either. Implementations are expected to
 * be slow and are always called off the main thread.
 */
interface PoseSuggestionSource {

    /** Human-readable name, used only for logs and debug UI. */
    val name: String

    /**
     * Whether this source can serve a request right now — e.g. the cloud source reports false
     * when there is no network, the on-device source when its model has not been loaded yet.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Ask for a pose that suits [scene].
     *
     * @param frame the frame the user is currently looking at, already rotated upright.
     * @param scene the perception layer's snapshot for that same frame.
     * @throws Exception implementations may fail freely; the repository handles fallback.
     */
    suspend fun suggestPose(frame: Bitmap, scene: SceneState): PoseSuggestion
}
