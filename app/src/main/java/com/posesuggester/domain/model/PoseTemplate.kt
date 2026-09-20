package com.posesuggester.domain.model

/**
 * One hand-authored pose the app can suggest.
 *
 * Templates exist because asking a small VLM for raw body-part coordinates is unreliable —
 * coordinate regression is one of the weakest things such models do. Selecting from a fixed
 * library is a classification problem instead, which they handle well, and it lets the app work
 * with no model at all. The reasoning layer picks a template; [com.posesuggester.refinement.PoseRefiner]
 * does the exact positioning.
 *
 * @param keypoints authored in the template's own 0..1 bounding box, not frame space. Repeated
 *   parts (HANDS, FEET) are ordered left-then-right from the viewer's side.
 * @param aspect the pose's on-screen width divided by its height, in *pixels*. Needed because
 *   normalised coordinates are distorted by the frame's own aspect ratio, so a figure placed
 *   without it comes out stretched.
 */
data class PoseTemplate(
    val id: String,
    val description: String,
    val keypoints: List<Keypoint>,
    val framing: Framing,
    val aspect: Float,
    /** The scene must offer all of these or the template is not a candidate at all. */
    val requires: Set<SceneAffordance> = emptySet(),
    /** Scored as a bonus when present, but never disqualifying. */
    val prefers: Set<SceneAffordance> = emptySet(),
)

/** How much of the body the pose covers, which drives how much room it needs. */
enum class Framing { FULL_BODY, HALF_BODY }

/**
 * Things a scene can offer a pose, inferred from COCO labels the detector actually emits.
 *
 * Deliberately short: walls, doorways and horizons are not in COCO, so the app cannot claim to
 * see them. Poses that want a wall are simply never gated on one.
 */
enum class SceneAffordance {
    /** A free region tall enough to stand a whole figure in. */
    OPEN_GROUND,

    /** chair, couch, bench, bed — something to sit on. */
    SEAT,

    /** dining table — something to lean against or perch at. */
    TABLE,

    /** potted plant — a hint that the setting is soft or outdoorsy. */
    GREENERY,

    /** car, bicycle, motorcycle, bus, truck — something to lean on. */
    VEHICLE,
}
