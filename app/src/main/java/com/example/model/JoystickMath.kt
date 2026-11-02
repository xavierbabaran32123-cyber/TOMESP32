package com.example.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class StickSample(
    val knobOffsetX: Float,
    val knobOffsetY: Float,
    val direction: CardinalDirection?,
)

data class PlacedControlBounds(
    val sizePx: Float,
    val leftPx: Float,
    val topPx: Float,
)

object JoystickMath {
    const val DEAD_ZONE_FRACTION = 0.28f
    const val MAX_KNOB_TRAVEL_FRACTION = 0.62f
    const val DIAGONAL_HYSTERESIS_RATIO = 1.25f

    /**
     * Evaluates knob visual displacement and dominant cardinal direction with diagonal hysteresis.
     */
    fun evaluateStick(
        dx: Float,
        dy: Float,
        radiusPx: Float,
        currentDirection: CardinalDirection?,
    ): StickSample {
        if (radiusPx <= 0f) {
            return StickSample(0f, 0f, null)
        }
        val dist = hypot(dx, dy)
        val maxKnobTravel = radiusPx * MAX_KNOB_TRAVEL_FRACTION
        val travelScale = if (dist > maxKnobTravel && dist > 0f) maxKnobTravel / dist else 1f
        val knobX = dx * travelScale
        val knobY = dy * travelScale

        if (dist < radiusPx * DEAD_ZONE_FRACTION) {
            return StickSample(
                knobOffsetX = knobX,
                knobOffsetY = knobY,
                direction = null,
            )
        }

        val ax = abs(dx)
        val ay = abs(dy)
        var wanted = if (ax > ay) {
            if (dx > 0f) CardinalDirection.RIGHT else CardinalDirection.LEFT
        } else {
            if (dy > 0f) CardinalDirection.DOWN else CardinalDirection.UP
        }

        // Hysteresis: near the 45-degree diagonal, hold current direction to avoid flicker
        if (currentDirection != null && wanted != currentDirection) {
            val curHoriz = currentDirection == CardinalDirection.LEFT ||
                currentDirection == CardinalDirection.RIGHT
            val wantHoriz = wanted == CardinalDirection.LEFT ||
                wanted == CardinalDirection.RIGHT
            if (curHoriz != wantHoriz) {
                val big = max(ax, ay)
                val small = min(ax, ay)
                if (big < small * DIAGONAL_HYSTERESIS_RATIO) {
                    wanted = currentDirection
                }
            }
        }

        return StickSample(
            knobOffsetX = knobX,
            knobOffsetY = knobY,
            direction = wanted,
        )
    }

    /**
     * Builds the Nordic UART command string when transitioning between stick directions.
     * Releases the previous direction (lowercase) before pressing the new one (uppercase).
     */
    fun buildDirectionTransitionCommand(
        previous: CardinalDirection?,
        next: CardinalDirection?,
    ): String {
        if (previous == next) return ""
        return buildString(2) {
            if (previous != null) append(previous.releaseCode)
            if (next != null) append(next.pressCode)
        }
    }

    /**
     * Clamps a control center coordinate so the control never clips outside the stage edges.
     */
    fun fitWithinStage(centerPx: Float, halfSizePx: Float, totalSpanPx: Float): Float {
        if (totalSpanPx <= halfSizePx * 2f) return totalSpanPx / 2f
        return centerPx.coerceIn(halfSizePx, totalSpanPx - halfSizePx)
    }

    /**
     * Computes pixel size and top-left offset for a control on the stage.
     */
    fun computeControlBounds(
        controlId: ControlId,
        placement: ControlPlacement,
        stageWidthPx: Float,
        stageHeightPx: Float,
        minTouchSizePx: Float,
    ): PlacedControlBounds {
        val shortSide = min(stageWidthPx, stageHeightPx)
        val rawSize = (controlId.baseFraction * shortSide * placement.scale).roundToInt().toFloat()
        val sizePx = max(minTouchSizePx, rawSize)
        val half = sizePx / 2f
        val leftPx = fitWithinStage(placement.x * stageWidthPx, half, stageWidthPx) - half
        val topPx = fitWithinStage(placement.y * stageHeightPx, half, stageHeightPx) - half
        return PlacedControlBounds(
            sizePx = sizePx,
            leftPx = leftPx,
            topPx = topPx,
        )
    }

    /**
     * Converts a screen-space vector into the rotated UI's local coordinate system.
     */
    fun toLocalVector(sx: Float, sy: Float, rotationDegrees: Int): Pair<Float, Float> {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        val rad = normalized * PI / 180.0
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        return Pair(sx * c + sy * s, -sx * s + sy * c)
    }

    /**
     * Steps a scale factor by [delta] (e.g. +/- 0.05) rounded to nearest hundredth in [0.5, 2.5].
     */
    fun stepScale(currentScale: Float, delta: Float): Float {
        val stepped = ((currentScale + delta) * 100f).roundToInt() / 100f
        return stepped.coerceIn(ControlPlacement.MIN_SCALE, ControlPlacement.MAX_SCALE)
    }
}
