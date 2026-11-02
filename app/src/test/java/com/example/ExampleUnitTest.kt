package com.example

import com.example.model.CardinalDirection
import com.example.model.JoystickMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun deadZoneReturnsNullDirection() {
        val sample = JoystickMath.evaluateStick(
            dx = 20f,
            dy = -10f,
            radiusPx = 100f,
            currentDirection = null,
        )
        assertNull(sample.direction)
        assertEquals(20f, sample.knobOffsetX, 0.01f)
        assertEquals(-10f, sample.knobOffsetY, 0.01f)
    }

    @Test
    fun dominantAxisPicksCardinalAndClampsKnobTravel() {
        val sample = JoystickMath.evaluateStick(
            dx = 120f,
            dy = 10f,
            radiusPx = 100f,
            currentDirection = null,
        )
        assertEquals(CardinalDirection.RIGHT, sample.direction)
        val dist = kotlin.math.hypot(sample.knobOffsetX, sample.knobOffsetY)
        assertEquals(62f, dist, 0.05f)
    }

    @Test
    fun diagonalHysteresisPreventsDirectionFlicker() {
        // Currently UP; moving slightly past 45 degrees toward RIGHT (ax=55, ay=-50 -> 55 < 50*1.25)
        val heldSample = JoystickMath.evaluateStick(
            dx = 55f,
            dy = -50f,
            radiusPx = 100f,
            currentDirection = CardinalDirection.UP,
        )
        assertEquals(CardinalDirection.UP, heldSample.direction)

        // Moving decisively RIGHT (ax=70, ay=-50 -> 70 >= 50*1.25)
        val switchedSample = JoystickMath.evaluateStick(
            dx = 70f,
            dy = -50f,
            radiusPx = 100f,
            currentDirection = CardinalDirection.UP,
        )
        assertEquals(CardinalDirection.RIGHT, switchedSample.direction)
    }

    @Test
    fun directionTransitionCommandsMatchNordicUartProtocol() {
        assertEquals("U", JoystickMath.buildDirectionTransitionCommand(null, CardinalDirection.UP))
        assertEquals(
            "uR",
            JoystickMath.buildDirectionTransitionCommand(
                CardinalDirection.UP,
                CardinalDirection.RIGHT,
            ),
        )
        assertEquals("r", JoystickMath.buildDirectionTransitionCommand(CardinalDirection.RIGHT, null))
        assertEquals(
            "",
            JoystickMath.buildDirectionTransitionCommand(
                CardinalDirection.LEFT,
                CardinalDirection.LEFT,
            ),
        )
    }
}
