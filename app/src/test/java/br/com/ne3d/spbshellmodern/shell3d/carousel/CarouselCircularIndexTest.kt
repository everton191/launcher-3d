package br.com.ne3d.spbshellmodern.shell3d.carousel

import br.com.ne3d.spbshellmodern.engine.nearestPanel
import org.junit.Assert.*
import org.junit.Test

class CarouselCircularIndexTest {
    @Test fun `physical index wraps forward past the last panel`() {
        assertEquals(0, CarouselCircularIndex.physicalIndex(5L, 5))
        assertEquals(1, CarouselCircularIndex.physicalIndex(6L, 5))
    }

    @Test fun `physical index wraps backward before the first panel`() {
        assertEquals(4, CarouselCircularIndex.physicalIndex(-1L, 5))
        assertEquals(3, CarouselCircularIndex.physicalIndex(-2L, 5))
    }

    @Test fun `ten positive loops stay valid`() {
        var logical = 0L
        repeat(50) {
            logical = CarouselCircularIndex.next(logical)
            val physical = CarouselCircularIndex.physicalIndex(logical, 5)
            assertTrue(CarouselCircularIndex.isValidPhysical(physical, 5))
            assertEquals((it + 1) % 5, physical)
        }
        assertEquals(50L, logical)
    }

    @Test fun `ten negative loops stay valid`() {
        var logical = 0L
        repeat(50) {
            logical = CarouselCircularIndex.previous(logical)
            val physical = CarouselCircularIndex.physicalIndex(logical, 5)
            assertTrue(CarouselCircularIndex.isValidPhysical(physical, 5))
        }
        assertEquals(-50L, logical)
        assertEquals(0, CarouselCircularIndex.physicalIndex(logical, 5))
    }

    @Test fun `large logical indexes always map to a valid slot`() {
        assertTrue(CarouselCircularIndex.isValidPhysical(CarouselCircularIndex.physicalIndex(1_000_000_007L, 7), 7))
        assertTrue(CarouselCircularIndex.isValidPhysical(CarouselCircularIndex.physicalIndex(-1_000_000_007L, 7), 7))
        assertEquals(1_000_000_007L % 7, CarouselCircularIndex.physicalIndex(1_000_000_007L, 7).toLong())
    }

    @Test fun `empty ring falls back to slot zero`() {
        assertEquals(0, CarouselCircularIndex.physicalIndex(3L, 0))
        assertFalse(CarouselCircularIndex.isValidPhysical(0, 0))
    }

    @Test fun `snap reaches the last panel from the first without teleport`() {
        val carousel = CarouselPhysics(CarouselMotionSpec())
        carousel.setAngle(0f)
        carousel.snapToIndex(4, 5)
        repeat(120) { carousel.tick(1f / 60f, 5) }
        assertTrue(carousel.isSettled)
        assertEquals(0f, carousel.velocity, 0f)
        assertEquals(4, nearestPanel(carousel.angle, 5))
    }

    @Test fun `snap reaches the first panel from the last without teleport`() {
        val carousel = CarouselPhysics(CarouselMotionSpec())
        carousel.setAngle(-4f * 360f / 5f)
        carousel.snapToIndex(0, 5)
        var maxStep = 0f
        var previous = carousel.angle
        repeat(120) {
            carousel.tick(1f / 60f, 5)
            maxStep = kotlin.math.max(maxStep, kotlin.math.abs(carousel.angle - previous))
            previous = carousel.angle
        }
        assertTrue(carousel.isSettled)
        assertEquals(0, nearestPanel(carousel.angle, 5))
        assertTrue(maxStep < 36f)
    }
}
