package com.posesuggester.domain.model

/**
 * The standardised output of the reasoning layer.
 *
 * Both the cloud and the on-device VLM path produce this exact shape, so the refinement and
 * render layers never need to know which one answered.
 */
data class PoseSuggestion(
    val poseDescription: String,
    val keypoints: List<Keypoint>,
    val origin: Origin = Origin.UNKNOWN,
) {
    /** Which reasoning path produced this suggestion. Useful for logging and debug UI. */
    enum class Origin { CLOUD, ON_DEVICE, UNKNOWN }
}
