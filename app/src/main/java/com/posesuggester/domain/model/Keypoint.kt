package com.posesuggester.domain.model

/**
 * A single anchor point of a suggested pose, in preview-relative coordinates.
 *
 * [x] and [y] are normalised to the 0..1 range against the *displayed preview*, with
 * (0, 0) at the top-left. Keeping them relative means the render layer can scale them to
 * any PreviewView size without the reasoning layer knowing anything about pixels.
 */
data class Keypoint(
    val part: BodyPart,
    val x: Float,
    val y: Float,
) {
    init {
        require(x in 0f..1f) { "x out of range: $x" }
        require(y in 0f..1f) { "y out of range: $y" }
    }
}

/** The body parts a suggested pose is expressed in. */
enum class BodyPart {
    HEAD,
    SHOULDERS,
    HIPS,
    HANDS,
    FEET,
}
