package com.posesuggester.data.source

import android.graphics.Bitmap
import android.util.Log
import com.posesuggester.domain.PoseSuggestionSource
import com.posesuggester.domain.model.PoseSuggestion
import com.posesuggester.domain.model.SceneState
import com.posesuggester.pose.PoseTemplateSelector

/**
 * Picks a pose from the hand-authored library using plain Kotlin heuristics over [SceneState].
 *
 * No model, no network, no download — which makes it the one source that can always answer, so
 * the repository treats it as the floor beneath both VLM paths. It is also the shape the VLM
 * paths should eventually take: let the model choose a template, and reuse this placement.
 *
 * [frame] is ignored; everything this needs is already in the scene snapshot.
 */
class TemplatePoseSuggestionSource(
    private val selector: PoseTemplateSelector = PoseTemplateSelector(),
) : PoseSuggestionSource {

    override val name: String = "PoseTemplates"

    /** Always true — it is pure computation over data the perception layer already produced. */
    override suspend fun isAvailable(): Boolean = true

    override suspend fun suggestPose(frame: Bitmap, scene: SceneState): PoseSuggestion {
        val placement = selector.select(scene)
        Log.d(TAG, "Chose '${placement.template.id}' for ${scene.objects.size} object(s), " +
            "${scene.openRegions.size} open region(s)")
        return PoseSuggestion(
            poseDescription = placement.template.description,
            keypoints = placement.keypoints,
            origin = PoseSuggestion.Origin.ON_DEVICE,
        )
    }

    private companion object {
        const val TAG = "TemplatePoseSource"
    }
}
