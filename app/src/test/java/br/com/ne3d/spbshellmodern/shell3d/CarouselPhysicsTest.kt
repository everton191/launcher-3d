package br.com.ne3d.spbshellmodern.shell3d

import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselPhysics
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselMotionSpec
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselLayout
import org.junit.Assert.*
import org.junit.Test

class CarouselPhysicsTest {
    @Test fun dragChangesAngleAndRestSettlesToPanel() {
        val spec = CarouselMotionSpec()
        val physics = CarouselPhysics(spec)
        physics.dragBy(100f)
        assertEquals(100f * spec.dragToAngleRatio, physics.angle, .01f)
        repeat(300) { physics.tick(.016f, 3) }
        assertEquals(0f, physics.angle, .1f)
    }
    @Test fun flingIsBounded() {
        val physics = CarouselPhysics(CarouselMotionSpec(maximumFlingVelocity = 120f))
        physics.fling(100_000f)
        physics.tick(.1f, 3)
        assertTrue(kotlin.math.abs(physics.angle) <= 12.1f)
    }
    @Test fun closedEightPanelWheelUsesTheFaceApothem() {
        val spec = CarouselMotionSpec(centerScale = .9f, panelFaceWidth = 2f)
        val radius = CarouselLayout(spec).closedRadius(8)
        assertEquals(((.9 + spec.panelGap / 2f) / kotlin.math.tan(Math.PI / 8.0)).toFloat(), radius, .0001f)
    }
    @Test fun fewerPanelsUseALargerCarouselScale() {
        val layout = CarouselLayout(CarouselMotionSpec())
        assertTrue(layout.sizeScale(3) > layout.sizeScale(8))
        assertTrue(layout.sizeScale(8) > layout.sizeScale(16))
    }
    @Test fun sustainedManualRotationStaysFiniteAndResponsive() {
        val physics = CarouselPhysics(CarouselMotionSpec())
        physics.beginDrag()
        repeat(100_000) { physics.dragBy(if (it % 2 == 0) 42f else -17f) }
        physics.endDrag()
        assertTrue(physics.angle.isFinite())
        assertTrue(kotlin.math.abs(physics.angle) <= 720f)
        physics.dragBy(120f)
        assertTrue(physics.angle.isFinite())
    }
}
