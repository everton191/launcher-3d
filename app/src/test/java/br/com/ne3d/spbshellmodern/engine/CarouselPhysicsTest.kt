package br.com.ne3d.spbshellmodern.engine

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CarouselPhysicsTest {
    @Test fun completeTurnsDoNotChangeSelectionOrSnap() {
        for (count in listOf(6, 10, 12)) for (turn in -20..20) for (index in 0 until count) {
            val angle = targetRotationForPanel(index, count) + turn * 360f
            assertEquals(index, nearestPanel(angle, count))
            assertEquals(angle, nearestEquivalentTarget(index, count, angle), .001f)
        }
    }
    @Test fun closestTargetNeverJumpsMoreThanHalfStep() {
        for (count in listOf(6, 10, 12)) for (angle in -7200..7200) {
            val index = nearestPanel(angle.toFloat(), count)
            assertTrue(index in 0 until count)
            assertTrue(abs(nearestEquivalentTarget(index, count, angle.toFloat()) - angle) <= stepAngle(count) / 2f + .001f)
        }
    }
    @Test fun zeroAndNegativeAnglesAreSafe() {
        assertEquals(0f, stepAngle(0), 0f)
        assertEquals(359f, normalizeAngle(-1f), 0f)
        assertEquals(0f, normalizeAngle(-7200f), 0f)
        assertEquals(0, nearestPanel(40f, 0))
    }
}
