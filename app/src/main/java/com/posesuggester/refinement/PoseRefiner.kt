package com.posesuggester.refinement

import android.graphics.RectF
import com.posesuggester.domain.model.DepthBand
import com.posesuggester.domain.model.Keypoint
import com.posesuggester.domain.model.SceneState

/**
 * Plain-Kotlin correction pass over raw keypoints from the reasoning layer.
 *
 * Its only job is *positioning*: push points out of detected objects and pull the figure toward
 * the largest patch of open space. It never adds, removes or reinterprets keypoints, so the
 * pose the VLM described is still the pose that gets drawn.
 */
class PoseRefiner(
    /** How far past an object's edge a nudged point is placed, in preview-relative units. */
    private val clearance: Float = 0.02f,
    /** Fraction of the distance to the open-space centre the whole figure is shifted. */
    private val snapStrength: Float = 0.35f,
) {

    fun refine(keypoints: List<Keypoint>, scene: SceneState): List<Keypoint> {
        if (keypoints.isEmpty() || scene.isEmpty) return keypoints
        // Only pull the figure across the frame when it is genuinely in the wrong place. A pose
        // that already sits in open space has been positioned by whoever produced it, and
        // shifting it again just drags it off its mark.
        val snapped = if (isInOpenSpace(keypoints, scene)) keypoints
        else snapTowardOpenSpace(keypoints, scene)
        return snapped.map { nudgeAwayFromObjects(it, scene) }
    }

    /** True when the figure's centre already falls inside one of the scene's open regions. */
    private fun isInOpenSpace(keypoints: List<Keypoint>, scene: SceneState): Boolean {
        if (scene.openRegions.isEmpty()) return true
        val cx = keypoints.map { it.x }.average().toFloat()
        val cy = keypoints.map { it.y }.average().toFloat()
        return scene.openRegions.any { it.containsPoint(cx, cy) }
    }

    /**
     * Translates the figure as a whole toward the centre of the largest open region, rather than
     * moving points independently — that keeps limb proportions intact.
     */
    private fun snapTowardOpenSpace(keypoints: List<Keypoint>, scene: SceneState): List<Keypoint> {
        val target = scene.openRegions.maxByOrNull { it.width() * it.height() } ?: return keypoints
        val currentX = keypoints.map { it.x }.average().toFloat()
        val currentY = keypoints.map { it.y }.average().toFloat()
        val dx = (target.centerX() - currentX) * snapStrength
        val dy = (target.centerY() - currentY) * snapStrength
        return keypoints.map {
            it.copy(x = (it.x + dx).clamp01(), y = (it.y + dy).clamp01())
        }
    }

    /**
     * Pushes a point clear of any near/mid object whose box it falls inside, along whichever
     * axis needs the smaller correction so the figure deforms as little as possible.
     *
     * Far objects are treated as background and ignored — standing "in front of" a distant tree
     * is fine, standing inside a nearby bench is not.
     */
    private fun nudgeAwayFromObjects(keypoint: Keypoint, scene: SceneState): Keypoint {
        var x = keypoint.x
        var y = keypoint.y
        for (obj in scene.objects) {
            if (obj.depth == DepthBand.FAR) continue
            if (!obj.box.containsPoint(x, y)) continue

            val toLeft = x - obj.box.left
            val toRight = obj.box.right - x
            val toTop = y - obj.box.top
            val toBottom = obj.box.bottom - y

            when (minOf(toLeft, toRight, toTop, toBottom)) {
                toLeft -> x = obj.box.left - clearance
                toRight -> x = obj.box.right + clearance
                toTop -> y = obj.box.top - clearance
                else -> y = obj.box.bottom + clearance
            }
            x = x.clamp01()
            y = y.clamp01()
        }
        return keypoint.copy(x = x, y = y)
    }

    private fun RectF.containsPoint(px: Float, py: Float): Boolean =
        px >= left && px <= right && py >= top && py <= bottom

    private fun Float.clamp01(): Float = coerceIn(0f, 1f)
}
