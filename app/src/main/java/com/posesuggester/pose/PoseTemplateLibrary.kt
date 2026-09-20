package com.posesuggester.pose

import com.posesuggester.domain.model.BodyPart
import com.posesuggester.domain.model.Framing
import com.posesuggester.domain.model.Keypoint
import com.posesuggester.domain.model.PoseTemplate
import com.posesuggester.domain.model.SceneAffordance
import com.posesuggester.domain.model.SceneAffordance.GREENERY
import com.posesuggester.domain.model.SceneAffordance.OPEN_GROUND
import com.posesuggester.domain.model.SceneAffordance.SEAT
import com.posesuggester.domain.model.SceneAffordance.TABLE
import com.posesuggester.domain.model.SceneAffordance.VEHICLE

/**
 * The curated pose set. Every pose is authored in its own 0..1 box, head near the top and feet
 * at the bottom, so the selector can scale it into whatever space the scene leaves free.
 *
 * Descriptions are written as direct instructions because they are shown to the user verbatim.
 */
object PoseTemplateLibrary {

    /** Maps the COCO labels the detector emits onto the affordances poses care about. */
    fun affordancesOf(labels: Collection<String>): Set<SceneAffordance> = buildSet {
        for (label in labels) {
            when (label.lowercase()) {
                "chair", "couch", "bench", "bed" -> add(SEAT)
                "dining table" -> add(TABLE)
                "potted plant" -> add(GREENERY)
                "car", "bicycle", "motorcycle", "bus", "truck" -> add(VEHICLE)
            }
        }
    }

    val templates: List<PoseTemplate> = listOf(
        full(
            "contrapposto", "Stand with your weight on one leg, the other knee soft, " +
                "hips angled slightly away from the camera.",
            0.50f, 0.06f, 0.50f, 0.22f, 0.53f, 0.50f,
            0.40f, 0.55f, 0.62f, 0.54f, 0.46f, 1.00f, 0.58f, 0.98f, aspect = 0.30f,
        ),
        full(
            "hands_on_hips", "Plant both feet, hands on your hips, shoulders back, chin level.",
            0.50f, 0.06f, 0.50f, 0.22f, 0.50f, 0.50f,
            0.34f, 0.50f, 0.66f, 0.50f, 0.42f, 1.00f, 0.58f, 1.00f, aspect = 0.40f,
        ),
        full(
            "arms_crossed", "Cross your arms loosely, weight even, look straight down the lens.",
            0.50f, 0.06f, 0.50f, 0.22f, 0.50f, 0.50f,
            0.58f, 0.36f, 0.42f, 0.36f, 0.44f, 1.00f, 0.56f, 1.00f, aspect = 0.30f,
        ),
        full(
            "hand_in_hair", "Raise one hand to your hair, tilt your head toward it, " +
                "let the other arm hang.",
            0.48f, 0.06f, 0.50f, 0.22f, 0.52f, 0.50f,
            0.40f, 0.10f, 0.60f, 0.52f, 0.47f, 1.00f, 0.57f, 0.99f, aspect = 0.32f,
        ),
        full(
            "walk_away_glance", "Walk away from the camera and glance back over your shoulder " +
                "mid-step.",
            0.46f, 0.06f, 0.50f, 0.22f, 0.50f, 0.50f,
            0.38f, 0.52f, 0.62f, 0.48f, 0.40f, 0.98f, 0.62f, 1.00f, aspect = 0.34f,
            prefers = setOf(OPEN_GROUND),
        ),
        full(
            "arms_raised", "Throw both arms up and look upward, feet apart.",
            0.50f, 0.10f, 0.50f, 0.26f, 0.50f, 0.54f,
            0.28f, 0.00f, 0.72f, 0.00f, 0.42f, 1.00f, 0.58f, 1.00f, aspect = 0.50f,
            prefers = setOf(OPEN_GROUND),
        ),
        full(
            "hands_in_pockets", "Hands in your pockets, shoulders relaxed, weight on one hip.",
            0.50f, 0.06f, 0.50f, 0.22f, 0.50f, 0.50f,
            0.40f, 0.52f, 0.60f, 0.52f, 0.45f, 1.00f, 0.55f, 1.00f, aspect = 0.28f,
        ),
        full(
            "lean_wall", "Lean a shoulder against the wall beside you, ankles crossed.",
            0.44f, 0.07f, 0.47f, 0.23f, 0.53f, 0.52f,
            0.36f, 0.55f, 0.58f, 0.50f, 0.62f, 1.00f, 0.56f, 0.99f, aspect = 0.32f,
        ),
        full(
            "side_profile", "Turn side-on to the camera and look away along your shoulder line.",
            0.52f, 0.06f, 0.50f, 0.22f, 0.50f, 0.50f,
            0.46f, 0.54f, 0.44f, 0.52f, 0.46f, 1.00f, 0.54f, 0.99f, aspect = 0.26f,
        ),
        full(
            "jump", "Jump straight up with your arms out and knees bent.",
            0.50f, 0.08f, 0.50f, 0.24f, 0.50f, 0.52f,
            0.26f, 0.10f, 0.74f, 0.10f, 0.34f, 0.96f, 0.66f, 0.96f, aspect = 0.60f,
            requires = setOf(OPEN_GROUND),
        ),
        full(
            "twirl", "Spin slowly with your arms swinging wide and let the motion carry.",
            0.50f, 0.07f, 0.50f, 0.23f, 0.50f, 0.52f,
            0.22f, 0.34f, 0.78f, 0.40f, 0.44f, 1.00f, 0.60f, 0.96f, aspect = 0.58f,
            requires = setOf(OPEN_GROUND), prefers = setOf(GREENERY),
        ),
        full(
            "reach_up", "Stretch both hands overhead as if reaching for something just out of range.",
            0.50f, 0.10f, 0.50f, 0.26f, 0.50f, 0.54f,
            0.44f, 0.00f, 0.60f, 0.02f, 0.45f, 1.00f, 0.56f, 1.00f, aspect = 0.34f,
        ),
        full(
            "crouch", "Drop into a low crouch, forearms resting on your knees, looking up.",
            0.50f, 0.18f, 0.50f, 0.34f, 0.50f, 0.66f,
            0.36f, 0.72f, 0.64f, 0.72f, 0.40f, 1.00f, 0.62f, 1.00f, aspect = 0.55f,
        ),
        full(
            "squat_low", "Squat right down on your heels, hands loose between your knees.",
            0.50f, 0.22f, 0.50f, 0.38f, 0.50f, 0.72f,
            0.40f, 0.80f, 0.60f, 0.80f, 0.36f, 1.00f, 0.66f, 1.00f, aspect = 0.62f,
        ),
        full(
            "sit_edge", "Sit on the edge, lean back on one hand, let your legs hang loose.",
            0.50f, 0.14f, 0.50f, 0.30f, 0.50f, 0.62f,
            0.38f, 0.66f, 0.62f, 0.66f, 0.44f, 1.00f, 0.58f, 1.00f, aspect = 0.50f,
            requires = setOf(SEAT),
        ),
        full(
            "sit_cross_legged", "Sit cross-legged, back straight, hands resting on your knees.",
            0.50f, 0.16f, 0.50f, 0.34f, 0.50f, 0.70f,
            0.34f, 0.76f, 0.66f, 0.76f, 0.40f, 0.92f, 0.60f, 0.92f, aspect = 0.70f,
        ),
        full(
            "sit_knees_up", "Sit with your knees pulled up and your arms wrapped around them.",
            0.50f, 0.14f, 0.50f, 0.32f, 0.52f, 0.68f,
            0.40f, 0.72f, 0.62f, 0.70f, 0.36f, 0.98f, 0.44f, 1.00f, aspect = 0.60f,
        ),
        full(
            "hands_on_knees", "Bend forward slightly with both hands on your knees, chin up.",
            0.50f, 0.12f, 0.50f, 0.28f, 0.50f, 0.56f,
            0.38f, 0.74f, 0.62f, 0.74f, 0.42f, 1.00f, 0.58f, 1.00f, aspect = 0.45f,
        ),
        full(
            "kneel", "Kneel on one knee, forearm across the raised thigh.",
            0.50f, 0.16f, 0.50f, 0.32f, 0.50f, 0.64f,
            0.40f, 0.68f, 0.60f, 0.68f, 0.44f, 1.00f, 0.60f, 0.98f, aspect = 0.48f,
        ),
        full(
            "recline_side", "Lie on your side propped on one elbow, legs stacked and slightly bent.",
            0.14f, 0.44f, 0.28f, 0.52f, 0.56f, 0.62f,
            0.22f, 0.70f, 0.40f, 0.44f, 0.88f, 0.78f, 0.92f, 0.66f, aspect = 1.90f,
            requires = setOf(OPEN_GROUND),
        ),
        full(
            "hands_behind_back", "Clasp your hands behind your back, shoulders open, weight even.",
            0.50f, 0.06f, 0.50f, 0.22f, 0.50f, 0.50f,
            0.44f, 0.52f, 0.56f, 0.52f, 0.45f, 1.00f, 0.56f, 1.00f, aspect = 0.26f,
        ),
        full(
            "point_away", "Extend one arm out to the side and follow it with your eyes.",
            0.48f, 0.06f, 0.50f, 0.22f, 0.50f, 0.50f,
            0.20f, 0.30f, 0.58f, 0.52f, 0.44f, 1.00f, 0.58f, 1.00f, aspect = 0.46f,
        ),
        full(
            "look_up_clasped", "Clasp your hands in front, look up and past the camera.",
            0.50f, 0.08f, 0.50f, 0.24f, 0.50f, 0.52f,
            0.46f, 0.40f, 0.54f, 0.40f, 0.45f, 1.00f, 0.56f, 1.00f, aspect = 0.28f,
        ),
        full(
            "laugh_hand_face", "Bring one hand near your face mid-laugh and let your head tip back.",
            0.50f, 0.07f, 0.50f, 0.23f, 0.50f, 0.51f,
            0.44f, 0.14f, 0.60f, 0.50f, 0.45f, 1.00f, 0.56f, 1.00f, aspect = 0.32f,
        ),
        full(
            "lean_railing", "Lean back against the rail with both elbows resting on it.",
            0.48f, 0.08f, 0.50f, 0.24f, 0.52f, 0.54f,
            0.30f, 0.58f, 0.70f, 0.58f, 0.48f, 1.00f, 0.58f, 0.98f, aspect = 0.48f,
            prefers = setOf(TABLE, VEHICLE),
        ),
        full(
            "tiptoe_stretch", "Rise onto your toes and stretch both arms straight overhead.",
            0.50f, 0.08f, 0.50f, 0.24f, 0.50f, 0.52f,
            0.38f, 0.02f, 0.62f, 0.02f, 0.46f, 1.00f, 0.56f, 1.00f, aspect = 0.36f,
            prefers = setOf(OPEN_GROUND),
        ),
        full(
            "back_to_camera", "Turn your back to the camera and face into the scene.",
            0.50f, 0.06f, 0.50f, 0.22f, 0.50f, 0.50f,
            0.36f, 0.50f, 0.64f, 0.50f, 0.45f, 1.00f, 0.56f, 1.00f, aspect = 0.34f,
            prefers = setOf(OPEN_GROUND),
        ),
        half(
            "bust_hand_chin", "Close crop: rest your chin lightly on one hand, elbow tucked in.",
            0.50f, 0.08f, 0.50f, 0.34f, 0.50f, 1.00f,
            0.42f, 0.30f, 0.64f, 0.72f, aspect = 0.45f,
        ),
        half(
            "bust_arms_crossed", "Close crop: arms folded low in frame, square to the camera.",
            0.50f, 0.08f, 0.50f, 0.34f, 0.50f, 1.00f,
            0.36f, 0.70f, 0.64f, 0.70f, aspect = 0.52f,
        ),
        half(
            "bust_hand_hair", "Close crop: one hand sweeping through your hair, head turned slightly.",
            0.52f, 0.10f, 0.50f, 0.34f, 0.50f, 1.00f,
            0.36f, 0.06f, 0.62f, 0.74f, aspect = 0.50f,
        ),
    )

    private fun full(
        id: String, description: String,
        headX: Float, headY: Float, shX: Float, shY: Float, hipX: Float, hipY: Float,
        lhX: Float, lhY: Float, rhX: Float, rhY: Float,
        lfX: Float, lfY: Float, rfX: Float, rfY: Float,
        aspect: Float,
        requires: Set<SceneAffordance> = emptySet(),
        prefers: Set<SceneAffordance> = emptySet(),
    ) = PoseTemplate(
        id = id,
        description = description,
        keypoints = listOf(
            Keypoint(BodyPart.HEAD, headX, headY),
            Keypoint(BodyPart.SHOULDERS, shX, shY),
            Keypoint(BodyPart.HIPS, hipX, hipY),
            Keypoint(BodyPart.HANDS, lhX, lhY),
            Keypoint(BodyPart.HANDS, rhX, rhY),
            Keypoint(BodyPart.FEET, lfX, lfY),
            Keypoint(BodyPart.FEET, rfX, rfY),
        ),
        framing = Framing.FULL_BODY,
        aspect = aspect,
        requires = requires,
        prefers = prefers,
    )

    private fun half(
        id: String, description: String,
        headX: Float, headY: Float, shX: Float, shY: Float, hipX: Float, hipY: Float,
        lhX: Float, lhY: Float, rhX: Float, rhY: Float,
        aspect: Float,
        requires: Set<SceneAffordance> = emptySet(),
        prefers: Set<SceneAffordance> = emptySet(),
    ) = PoseTemplate(
        id = id,
        description = description,
        keypoints = listOf(
            Keypoint(BodyPart.HEAD, headX, headY),
            Keypoint(BodyPart.SHOULDERS, shX, shY),
            Keypoint(BodyPart.HIPS, hipX, hipY),
            Keypoint(BodyPart.HANDS, lhX, lhY),
            Keypoint(BodyPart.HANDS, rhX, rhY),
        ),
        framing = Framing.HALF_BODY,
        aspect = aspect,
        requires = requires,
        prefers = prefers,
    )
}
