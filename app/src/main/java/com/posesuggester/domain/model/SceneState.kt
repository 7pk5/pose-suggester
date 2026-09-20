package com.posesuggester.domain.model

import android.graphics.RectF

/**
 * The perception layer's rolling in-memory view of what the camera is looking at.
 *
 * Rebuilt from every analysed frame on the analysis thread and published as an immutable
 * snapshot, so readers (reasoning, refinement) never see a half-updated scene.
 *
 * All rectangles are normalised to the 0..1 preview space, matching [Keypoint].
 */
data class SceneState(
    val objects: List<DetectedObject> = emptyList(),
    val openRegions: List<RectF> = emptyList(),
    val person: PersonPresence = PersonPresence.NONE,
    /** Source frame size in pixels, after rotation. Zero until the first frame lands. */
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    /** Wall-clock time of the frame this state was built from. */
    val timestampMs: Long = 0L,
) {
    val isEmpty: Boolean get() = frameWidth == 0 || frameHeight == 0
}

/**
 * One object reported by MediaPipe Object Detection.
 *
 * [depth] is a coarse near/mid/far bucket inferred from how much of the frame the box covers —
 * it is a heuristic, not metric depth, and is only meant to help the refinement layer decide
 * what a pose should stand in front of or behind.
 */
data class DetectedObject(
    val label: String,
    val confidence: Float,
    val box: RectF,
    val depth: DepthBand,
)

enum class DepthBand { NEAR, MID, FAR }

/** Whether a person is currently in frame, per the Pose Landmarker. */
enum class PersonPresence { NONE, PRESENT }
