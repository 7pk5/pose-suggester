package com.posesuggester.perception

import android.graphics.RectF
import com.posesuggester.domain.model.DepthBand
import com.posesuggester.domain.model.DetectedObject
import com.posesuggester.domain.model.PersonPresence
import com.posesuggester.domain.model.SceneState
import com.google.mediapipe.tasks.components.containers.Detection

/**
 * Turns a raw MediaPipe detection list into the [SceneState] the rest of the app consumes.
 *
 * Two things happen here that the detector does not do for us:
 *  - boxes are normalised from source pixels to the 0..1 space every other layer speaks, and
 *  - the negative space is worked out, since "where can this person stand" is the question the
 *    reasoning and refinement layers actually need answered.
 */
object SceneStateBuilder {

    /** Occupancy grid resolution used to find open space. Coarse on purpose — this runs per frame. */
    private const val GRID_COLS = 8
    private const val GRID_ROWS = 12

    /** Fraction of the frame a box must cover to be called near / mid. */
    private const val NEAR_AREA = 0.25f
    private const val MID_AREA = 0.06f

    /** Open regions smaller than this fraction of the frame are noise, not somewhere to stand. */
    private const val MIN_REGION_AREA = 0.04f

    fun build(
        detections: List<Detection>,
        frameWidth: Int,
        frameHeight: Int,
        timestampMs: Long,
    ): SceneState {
        if (frameWidth <= 0 || frameHeight <= 0) return SceneState()

        val objects = detections.mapNotNull { it.toDetectedObject(frameWidth, frameHeight) }
        return SceneState(
            objects = objects,
            openRegions = findOpenRegions(objects),
            // Filled in by the Pose Landmarker once that slice lands.
            person = PersonPresence.NONE,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            timestampMs = timestampMs,
        )
    }

    private fun Detection.toDetectedObject(frameWidth: Int, frameHeight: Int): DetectedObject? {
        val top = categories().maxByOrNull { it.score() } ?: return null
        val box = RectF(
            (boundingBox().left / frameWidth).coerceIn(0f, 1f),
            (boundingBox().top / frameHeight).coerceIn(0f, 1f),
            (boundingBox().right / frameWidth).coerceIn(0f, 1f),
            (boundingBox().bottom / frameHeight).coerceIn(0f, 1f),
        )
        return DetectedObject(
            label = top.categoryName(),
            confidence = top.score(),
            box = box,
            depth = depthOf(box),
        )
    }

    /**
     * Coarse near/mid/far bucket from how much of the frame a box covers. This is a stand-in for
     * real depth: a big box is usually close, a small one usually far. Good enough to decide
     * what the figure should avoid, not good enough for anything metric.
     */
    private fun depthOf(box: RectF): DepthBand {
        val area = box.width() * box.height()
        return when {
            area >= NEAR_AREA -> DepthBand.NEAR
            area >= MID_AREA -> DepthBand.MID
            else -> DepthBand.FAR
        }
    }

    /**
     * Marks every grid cell touched by a *near* object as occupied, then merges the free cells
     * back into rectangles — horizontally into runs, then vertically where consecutive rows share
     * the same run.
     *
     * Only near objects block. Mid and far ones are background a person can stand beside or in
     * front of, and counting them cost us every open region the moment anything sizeable was in
     * the foreground.
     */
    private fun findOpenRegions(objects: List<DetectedObject>): List<RectF> {
        val cellW = 1f / GRID_COLS
        val cellH = 1f / GRID_ROWS
        val occupied = Array(GRID_ROWS) { BooleanArray(GRID_COLS) }

        for (obj in objects) {
            if (obj.depth != DepthBand.NEAR) continue
            val colStart = (obj.box.left / cellW).toInt().coerceIn(0, GRID_COLS - 1)
            val colEnd = (obj.box.right / cellW).toInt().coerceIn(0, GRID_COLS - 1)
            val rowStart = (obj.box.top / cellH).toInt().coerceIn(0, GRID_ROWS - 1)
            val rowEnd = (obj.box.bottom / cellH).toInt().coerceIn(0, GRID_ROWS - 1)
            for (row in rowStart..rowEnd) {
                for (col in colStart..colEnd) occupied[row][col] = true
            }
        }

        // Horizontal runs of free cells, one list per row.
        val runsByRow = occupied.map { row ->
            buildList {
                var start = -1
                for (col in 0..GRID_COLS) {
                    val free = col < GRID_COLS && !row[col]
                    if (free && start == -1) start = col
                    if (!free && start != -1) {
                        add(start to col - 1)
                        start = -1
                    }
                }
            }
        }

        // Grow each run downward while the row below offers an identical run.
        val regions = mutableListOf<RectF>()
        val consumed = runsByRow.map { mutableSetOf<Pair<Int, Int>>() }
        for (row in runsByRow.indices) {
            for (run in runsByRow[row]) {
                if (run in consumed[row]) continue
                var lastRow = row
                while (lastRow + 1 < GRID_ROWS && run in runsByRow[lastRow + 1]) {
                    lastRow++
                    consumed[lastRow].add(run)
                }
                val rect = RectF(
                    run.first * cellW,
                    row * cellH,
                    (run.second + 1) * cellW,
                    (lastRow + 1) * cellH,
                )
                regions.add(rect)
            }
        }

        // Prefer regions worth standing in, but never report none while any free space exists —
        // the pose selector needs somewhere to put a figure.
        val roomy = regions.filter { it.width() * it.height() >= MIN_REGION_AREA }
        return roomy.ifEmpty { listOfNotNull(regions.maxByOrNull { it.width() * it.height() }) }
    }
}
