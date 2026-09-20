package com.posesuggester.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.posesuggester.domain.model.BodyPart
import com.posesuggester.domain.model.Keypoint
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Draws the suggested pose as a hand-drawn stick figure over the camera preview.
 *
 * This is a plain [View] stacked on top of PreviewView, never part of the ImageCapture stream,
 * so the guide is visible to the user but absent from the saved photo.
 *
 * The sketch look comes from three things: every stroke wanders off its true line by a small
 * random amount, each stroke is drawn twice with different wander, and the limbs bend at a
 * mid-joint instead of running straight. The wander is seeded from the pose itself, so a given
 * suggestion always draws identically — otherwise the figure would shimmer on every redraw.
 */
class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var keypoints: List<Keypoint> = emptyList()
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var seed = 0L

    /** Rebuilt whenever the pose or the view size changes. */
    private var strokes: List<Path> = emptyList()

    private val density = resources.displayMetrics.density

    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
        strokeWidth = 3.5f * density
    }

    /** Drawn under the white ink so the figure stays legible against a bright scene. */
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0x80000000.toInt()
        strokeWidth = 7f * density
    }

    /**
     * @param sourceWidth/[sourceHeight] the analysis frame size the keypoints were normalised
     *   against. Needed because the preview centre-crops that frame to fill the screen, so 0..1
     *   across the frame is not 0..1 across the view.
     */
    fun setPose(keypoints: List<Keypoint>, sourceWidth: Int, sourceHeight: Int) {
        this.keypoints = keypoints
        this.sourceWidth = sourceWidth
        this.sourceHeight = sourceHeight
        this.seed = keypoints.fold(17L) { acc, kp ->
            acc * 31 + (kp.x * 10_000).toLong() * 31 + (kp.y * 10_000).toLong()
        }
        rebuild()
        invalidate()
    }

    fun clear() {
        keypoints = emptyList()
        strokes = emptyList()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    override fun onDraw(canvas: Canvas) {
        for (stroke in strokes) canvas.drawPath(stroke, shadowPaint)
        for (stroke in strokes) canvas.drawPath(stroke, inkPaint)
    }

    private fun rebuild() {
        if (keypoints.isEmpty() || width == 0 || height == 0) {
            strokes = emptyList()
            return
        }

        val head = keypoints.firstOrNull { it.part == BodyPart.HEAD }?.toPixels() ?: return
        val shoulders = keypoints.firstOrNull { it.part == BodyPart.SHOULDERS }?.toPixels() ?: return
        val hips = keypoints.firstOrNull { it.part == BodyPart.HIPS }?.toPixels() ?: return
        val hands = keypoints.filter { it.part == BodyPart.HANDS }.map { it.toPixels() }
        val feet = keypoints.filter { it.part == BodyPart.FEET }.map { it.toPixels() }

        val random = Random(seed)
        val torso = dist(shoulders, hips)
        val figureHeight = keypoints.map { it.toPixels().y }.let { (it.maxOrNull() ?: 0f) - (it.minOrNull() ?: 0f) }
        // Tie the head to the whole figure, not just to the neck gap: on a close crop the head
        // keypoint sits a long way above the shoulders and a pure neck-length rule balloons it.
        val headRadius = minOf(dist(head, shoulders) * 0.62f, figureHeight * 0.13f)
            .coerceAtLeast(8f * density)
        val paths = mutableListOf<Path>()

        // Head, drawn as a wobbling ring rather than a clean circle.
        paths += sketchCircle(head, headRadius, random)
        paths += sketchCircle(head, headRadius, random)

        // Neck runs from the bottom of the head, not its centre, so the ring stays open.
        val neckTop = PointF(head.x, head.y + headRadius)
        if (neckTop.y < shoulders.y) paths += sketchLine(neckTop, shoulders, 0f, random)

        // Spine.
        paths += sketchLine(shoulders, hips, 0f, random)
        paths += sketchLine(shoulders, hips, 0f, random)

        // Arms and legs bend at a mid-joint, alternating sides so the figure reads as a body.
        hands.forEachIndexed { i, hand ->
            val bend = if (i % 2 == 0) -ELBOW_BEND else ELBOW_BEND
            paths += sketchLine(shoulders, hand, bend * torso, random)
            paths += sketchLine(shoulders, hand, bend * torso, random)
        }
        feet.forEachIndexed { i, foot ->
            val bend = if (i % 2 == 0) -KNEE_BEND else KNEE_BEND
            paths += sketchLine(hips, foot, bend * torso, random)
            paths += sketchLine(hips, foot, bend * torso, random)
        }

        strokes = paths
    }

    /**
     * A line from [from] to [to] that bows out by [bend] pixels at its midpoint and wanders
     * slightly along its length, so it reads as drawn by hand rather than plotted.
     */
    private fun sketchLine(from: PointF, to: PointF, bend: Float, random: Random): Path {
        val length = dist(from, to)
        val segments = (length / (14f * density)).toInt().coerceIn(3, 14)
        val wander = (length * 0.015f).coerceIn(1f * density, 3f * density)

        // Unit vector perpendicular to the line, used for both the bend and the wander.
        val nx = -(to.y - from.y) / length.coerceAtLeast(1f)
        val ny = (to.x - from.x) / length.coerceAtLeast(1f)

        val path = Path()
        path.moveTo(from.x + nx * jitter(random, wander), from.y + ny * jitter(random, wander))
        for (i in 1..segments) {
            val t = i.toFloat() / segments
            // Bow peaks at the middle of the limb and vanishes at both joints.
            val bow = bend * (1f - (2f * t - 1f) * (2f * t - 1f))
            val offset = bow + if (i == segments) 0f else jitter(random, wander)
            path.lineTo(
                from.x + (to.x - from.x) * t + nx * offset,
                from.y + (to.y - from.y) * t + ny * offset,
            )
        }
        return path
    }

    /** A ring whose radius breathes slightly as it goes round, and that overshoots its start. */
    private fun sketchCircle(centre: PointF, radius: Float, random: Random): Path {
        val path = Path()
        val steps = 20
        val start = random.nextFloat() * TWO_PI
        // Overshooting past a full turn leaves the small crossed tail of a hand-drawn circle.
        val sweep = TWO_PI * (1.04f + random.nextFloat() * 0.06f)
        for (i in 0..steps) {
            val angle = start + sweep * i / steps
            val r = radius + jitter(random, radius * 0.06f)
            val x = centre.x + r * kotlin.math.cos(angle)
            val y = centre.y + r * kotlin.math.sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        return path
    }

    private fun jitter(random: Random, amount: Float): Float =
        (random.nextFloat() * 2f - 1f) * amount

    /**
     * Maps a preview-relative keypoint to a pixel in this view, repeating the letterboxing the
     * PreviewView applies when it fits a 4:3 frame into a taller view.
     */
    private fun Keypoint.toPixels(): PointF {
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return PointF(x * width, y * height)
        }
        val scale = minOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
        val drawnWidth = sourceWidth * scale
        val drawnHeight = sourceHeight * scale
        return PointF(
            (width - drawnWidth) / 2f + x * drawnWidth,
            (height - drawnHeight) / 2f + y * drawnHeight,
        )
    }

    private fun dist(a: PointF, b: PointF): Float = hypot(b.x - a.x, b.y - a.y)

    private data class PointF(val x: Float, val y: Float)

    private companion object {
        const val TWO_PI = (Math.PI * 2).toFloat()

        /**
         * How far a limb bows at its mid-joint, as a fraction of torso length. Small on purpose:
         * enough to suggest an elbow or knee, not enough to swing a raised arm out around the
         * head, which is what a larger value does.
         */
        const val ELBOW_BEND = 0.06f
        const val KNEE_BEND = 0.05f
    }
}
