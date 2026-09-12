package br.com.ne3d.spbshellmodern.shell3d

import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselPhysics
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselMotionSpec
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselLayout
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselMotionState
import org.junit.Assert.*
import org.junit.Test

class CarouselPhysicsTest {
    @Test fun dragChangesAngleAndRestSettlesToPanel() {
        val spec = CarouselMotionSpec()
        val physics = CarouselPhysics(spec)
        physics.beginDrag()
        physics.dragBy(100f)
        physics.endDrag()
        assertEquals(100f * spec.dragToAngleRatio, physics.angle, .01f)
        repeat(300) { physics.tick(.016f, 3) }
        assertEquals(0f, physics.angle, .1f)
    }
    @Test fun flingIsBounded() {
        val physics = CarouselPhysics(CarouselMotionSpec(maximumFlingVelocity = 120f))
        physics.beginDrag()
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
    @Test fun layoutSupportsOneTwoAndRegularPanelCounts() {
        val layout = CarouselLayout(CarouselMotionSpec())
        listOf(1, 2, 3, 6).forEach { count ->
            val transform = layout.transform(0, count, 0f)
            assertTrue(transform.x.isFinite())
            assertTrue(transform.z.isFinite())
            assertTrue(transform.visible)
        }
        assertEquals(0f, layout.closedRadius(1), 0f)
        assertTrue(layout.closedRadius(2) > 0f)
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
    @Test fun stateMachineCoversDragFlingSnapAndExactIdle() {
        val physics = CarouselPhysics(CarouselMotionSpec())
        assertEquals(CarouselMotionState.IDLE, physics.state)
        physics.beginDrag(); assertEquals(CarouselMotionState.DRAG, physics.state)
        physics.dragBy(70f); physics.endDrag(); assertEquals(CarouselMotionState.SNAP, physics.state)
        repeat(100) { physics.tick(.016f, 6) }
        assertEquals(CarouselMotionState.IDLE, physics.state)
        assertEquals(0f, physics.angle % 60f, .0001f)
    }
    @Test fun flingDeceleratesAndRefreshRateProducesEquivalentResult() {
        fun simulate(dt: Float, steps: Int): CarouselPhysics {
            val p = CarouselPhysics(CarouselMotionSpec()); p.beginDrag(); p.fling(1200f)
            val initial = p.velocity; p.tick(dt, 6); assertTrue(kotlin.math.abs(p.velocity) < kotlin.math.abs(initial))
            repeat(steps - 1) { p.tick(dt, 6) }; return p
        }
        val at60 = simulate(1f / 60f, 60)
        val at120 = simulate(1f / 120f, 120)
        assertEquals(at60.angle, at120.angle, 1.5f)
    }
    @Test fun snapCanBeInterruptedAndReverseDragIsImmediate() {
        val p = CarouselPhysics(CarouselMotionSpec()); p.beginDrag(); p.dragBy(50f); p.endDrag(); p.tick(.02f, 6)
        val duringSnap = p.angle; p.beginDrag(); assertEquals(CarouselMotionState.DRAG, p.state); assertEquals(duringSnap, p.angle, 0f)
        p.dragBy(10f); p.dragBy(-20f); assertTrue(p.angle < duringSnap)
    }
}
