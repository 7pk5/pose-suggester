package com.posesuggester.pose

import android.graphics.RectF
import com.posesuggester.domain.model.DepthBand
import com.posesuggester.domain.model.Framing
import com.posesuggester.domain.model.Keypoint
import com.posesuggester.domain.model.PoseTemplate
import com.posesuggester.domain.model.SceneAffordance
import com.posesuggester.domain.model.SceneState
import kotlin.random.Random

/**
 * Chooses a template for the current scene and places it in the frame.
 *
 * Selection is a small weighted score rather than a hard rule set, so a scene that satisfies
 * nothing still gets a sensible answer instead of no answer. Placement is kept here rather than
 * in the refiner because it is about *fitting* the figure to the space; the refiner's job is the
 * finer correction afterwards.
 */
class PoseTemplateSelector(
    private val library: List<PoseTemplate> = PoseTemplateLibrary.templates,
    private val random: Random = Random.Default,
) {

    /** Ids served recently, so the same pose is not suggested over and over. */
    private val recent = ArrayDeque<String>()

    data class Placement(val template: PoseTemplate, val keypoints: List<Keypoint>)

    fun select(scene: SceneState): Placement {
        val affordances = affordancesOf(scene)
        val candidates = library.filter { affordances.containsAll(it.requires) }
            .ifEmpty { library.filter { it.requires.isEmpty() } }

        val nearCoverage = scene.objects
            .filter { it.depth == DepthBand.NEAR }
            .sumOf { (it.box.width() * it.box.height()).toDouble() }
            .toFloat()
        val region = bestRegion(scene)

        val scored = candidates.map { it to score(it, affordances, nearCoverage, region) }
        val best = scored.maxOf { it.second }
        // Any template within a hair of the best is a fair choice; pick among them for variety.
        val winner = scored.filter { it.second >= best - 0.5f }.random(random).first

        remember(winner.id)
        return Placement(winner, place(winner, region, scene))
    }

    private fun affordancesOf(scene: SceneState): Set<SceneAffordance> = buildSet {
        addAll(PoseTemplateLibrary.affordancesOf(scene.objects.map { it.label }))
        val region = scene.openRegions.maxByOrNull { it.width() * it.height() }
        if (region != null && region.height() >= STANDING_ROOM && region.width() >= 0.18f) {
            add(SceneAffordance.OPEN_GROUND)
        }
    }

    private fun score(
        template: PoseTemplate,
        affordances: Set<SceneAffordance>,
        nearCoverage: Float,
        region: RectF,
    ): Float {
        var score = 1f
        score += 2f * template.prefers.count { it in affordances }
        score += 1.5f * template.requires.count { it in affordances }

        // Does the space actually fit this framing?
        score += when (template.framing) {
            Framing.FULL_BODY -> if (region.height() >= STANDING_ROOM) 1.5f else -1.5f
            Framing.HALF_BODY -> 0.5f
        }

        // A cluttered foreground favours a tighter crop over a whole standing figure.
        if (nearCoverage > CLUTTERED) {
            score += if (template.framing == Framing.HALF_BODY) 1.5f else -1f
        }

        if (template.id in recent) score -= 4f
        return score
    }

    /** The largest open region, or the whole frame inset a little when the scene offers none. */
    private fun bestRegion(scene: SceneState): RectF =
        scene.openRegions.maxByOrNull { it.width() * it.height() }
            ?: RectF(0.05f, 0.05f, 0.95f, 0.95f)

    /**
     * Scales the template's local box into [region], preserving its real on-screen proportions.
     *
     * Normalised coordinates are stretched by the frame's own aspect ratio, so a figure mapped
     * straight into a box would come out squashed or spindly — hence the frame ratio correction.
     */
    private fun place(template: PoseTemplate, region: RectF, scene: SceneState): List<Keypoint> {
        val frameRatio = if (scene.isEmpty) DEFAULT_FRAME_RATIO
        else scene.frameHeight.toFloat() / scene.frameWidth.toFloat()

        val height = when (template.framing) {
            // Leave HEAD_ROOM free at the top so the head, drawn as a ring above the keypoint,
            // is never clipped by the edge of the frame.
            Framing.FULL_BODY -> minOf(region.height() * 0.95f, 1f - 2f * HEAD_ROOM)
            Framing.HALF_BODY -> CROP_HEIGHT
        }
        val width = (height * template.aspect * frameRatio).coerceAtMost(0.98f)

        val bottom = when (template.framing) {
            // A standing figure has its feet on the floor of the open region it was placed in.
            Framing.FULL_BODY -> region.bottom - 0.02f
            // A close crop is somebody near the camera: it fills the lower frame regardless of
            // where the open space happens to be, otherwise the bust floats in mid-air.
            Framing.HALF_BODY -> 1f - HEAD_ROOM
        }
        val centreX = when (template.framing) {
            Framing.FULL_BODY -> region.centerX()
            // Keep a crop near the middle; the sitter is the subject, not the background.
            Framing.HALF_BODY -> region.centerX().coerceIn(0.35f, 0.65f)
        }
        val target = RectF(
            centreX - width / 2f,
            maxOf(bottom - height, HEAD_ROOM),
            centreX + width / 2f,
            bottom,
        ).also { it.nudgeInsideFrame() }

        val local = template.keypoints.localBounds()
        val localW = maxOf(local.width(), 1e-3f)
        val localH = maxOf(local.height(), 1e-3f)

        return template.keypoints.map { kp ->
            kp.copy(
                x = (target.left + (kp.x - local.left) / localW * target.width()).coerceIn(0f, 1f),
                y = (target.top + (kp.y - local.top) / localH * target.height()).coerceIn(0f, 1f),
            )
        }
    }

    /** Slides the box back on-screen rather than clipping it, so the figure keeps its shape. */
    private fun RectF.nudgeInsideFrame() {
        if (left < 0f) offset(-left, 0f)
        if (right > 1f) offset(1f - right, 0f)
        if (top < 0f) offset(0f, -top)
        if (bottom > 1f) offset(0f, 1f - bottom)
        left = left.coerceIn(0f, 1f)
        right = right.coerceIn(0f, 1f)
        top = top.coerceIn(0f, 1f)
        bottom = bottom.coerceIn(0f, 1f)
    }

    private fun List<Keypoint>.localBounds(): RectF = RectF(
        minOf { it.x }, minOf { it.y }, maxOf { it.x }, maxOf { it.y },
    )

    private fun remember(id: String) {
        recent.addLast(id)
        while (recent.size > RECENT_MEMORY) recent.removeFirst()
    }

    private companion object {
        /** Region height, as a fraction of the frame, needed to stand a whole figure. */
        const val STANDING_ROOM = 0.5f

        /** Near-object coverage above which the foreground counts as cluttered. */
        const val CLUTTERED = 0.35f

        const val RECENT_MEMORY = 3

        /** Portrait 3:4, matching the analysis stream, used before the first frame lands. */
        const val DEFAULT_FRAME_RATIO = 4f / 3f

        /** Margin kept clear at the top and bottom of the frame, so the head ring always fits. */
        const val HEAD_ROOM = 0.06f

        /** How much of the frame height a close crop occupies. */
        const val CROP_HEIGHT = 0.62f
    }
}
