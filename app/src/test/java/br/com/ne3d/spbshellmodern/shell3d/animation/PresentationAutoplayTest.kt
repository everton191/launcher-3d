package br.com.ne3d.spbshellmodern.shell3d.animation

import br.com.ne3d.spbshellmodern.engine.nearestPanel
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselCircularIndex
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselMotionSpec
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselPhysics
import br.com.ne3d.spbshellmodern.shell3d.core.ShellEngine
import br.com.ne3d.spbshellmodern.shell3d.scene.Panel3D
import org.junit.Assert.*
import org.junit.Test

class PresentationAutoplayTest {
    private val count = 5
    private fun spec() = CarouselMotionSpec(
        autoplayIdleDelayMs = 120L,
        autoplaySnapDurationMs = 120L,
        presentationIntroMs = 80L,
        presentationActiveMs = 200L,
        presentationOutroMs = 80L,
        betweenPanelsDelayMs = 60L,
    )

    private inner class Harness {
        var nowNanos = 0L
        val carousel = CarouselPhysics(CarouselMotionSpec())
        val idle = CarouselIdleController(spec()) { nowNanos }
        val settled = ArrayList<Pair<Long, Int>>()
        val dt = 1f / 60f

        init {
            carousel.setAngle(0f)
        }

        /** One frame: wall clock and frame time advance together. */
        fun frame(): Boolean {
            nowNanos += (dt * 1_000_000_000L).toLong()
            carousel.tick(dt, count)
            return idle.tickAutoplay(dt, carousel, count, { "panel-$it" }) { logical, physical ->
                settled.add(logical to physical)
            }
        }

        fun runFrames(n: Int) {
            repeat(n) { frame() }
        }

        fun settleCarousel() {
            repeat(240) {
                nowNanos += (dt * 1_000_000_000L).toLong()
                carousel.tick(dt, count)
                if (carousel.isSettled) return
            }
            fail("carousel did not settle")
        }
    }

    @Test fun `autoplay does not start before the idle delay`() {
        val h = Harness()
        h.runFrames(5)
        assertEquals(AutoplayPhase.IDLE_WAIT, h.idle.autoplayPhase)
        assertEquals(0f, h.carousel.angle, 0f)
        assertTrue(h.settled.isEmpty())
    }

    @Test fun `autoplay advances exactly one panel and snaps onto it`() {
        val h = Harness()
        // Pass the idle deadline, then let the snap run to completion.
        var guard = 0
        while (h.settled.isEmpty() && guard++ < 600) h.frame()
        assertEquals(
            "settled=${h.settled} phase=${h.idle.autoplayPhase} angle=${h.carousel.angle} now=${h.nowNanos}",
            listOf(1L to 1),
            h.settled,
        )
        assertEquals(1L, h.idle.logicalIndex)
        assertEquals(0f, h.carousel.velocity, 0f)
        assertTrue(h.carousel.isSettled)
        assertEquals(1, nearestPanel(h.carousel.angle, count))
    }

    @Test fun `presentation starts only after the snap stops`() {
        val h = Harness()
        var sawRotating = false
        var presentationDuringSnap = false
        repeat(600) {
            h.frame()
            if (h.idle.autoplayPhase == AutoplayPhase.ROTATING_TO_NEXT) {
                sawRotating = true
                if (h.idle.presentation.isRunning) presentationDuringSnap = true
            }
            if (h.idle.autoplayPhase == AutoplayPhase.PRESENTING) return
        }
        assertTrue(sawRotating)
        assertFalse(presentationDuringSnap)
        assertEquals(AutoplayPhase.PRESENTING, h.idle.autoplayPhase)
        assertEquals(PanelPresentationPhase.INTRO, h.idle.presentation.state.phase)
    }

    @Test fun `presentation runs intro active outro and returns to rest`() {
        val h = Harness()
        val seen = LinkedHashSet<PanelPresentationPhase>()
        var minEmphasis = 1f
        var maxEmphasis = 0f
        var progressOk = true
        var guard = 0
        // Run until a full presentation completes.
        while (guard++ < 3000) {
            h.frame()
            val state = h.idle.presentation.state
            if (state.phase != PanelPresentationPhase.IDLE) {
                seen.add(state.phase)
                if (state.progress !in 0f..1f || state.phaseProgress !in 0f..1f) progressOk = false
                minEmphasis = kotlin.math.min(minEmphasis, h.idle.presentationEmphasis)
                maxEmphasis = kotlin.math.max(maxEmphasis, h.idle.presentationEmphasis)
            } else if (seen.isNotEmpty()) break
        }
        assertEquals(
            listOf(PanelPresentationPhase.INTRO, PanelPresentationPhase.ACTIVE, PanelPresentationPhase.OUTRO),
            seen.toList(),
        )
        assertTrue(progressOk)
        assertTrue(maxEmphasis > 0.9f)
        assertEquals(0f, h.idle.presentationEmphasis, 0f)
        assertTrue("intro ramps up from rest, got $minEmphasis", minEmphasis < 0.5f)
    }

    @Test fun `second advance continues the circular walk`() {
        val h = Harness()
        var guard = 0
        while (h.settled.size < 2 && guard++ < 3000) h.frame()
        assertEquals(
            "settled=${h.settled} phase=${h.idle.autoplayPhase} angle=${h.carousel.angle} now=${h.nowNanos}",
            listOf(1L to 1, 2L to 2),
            h.settled,
        )
        assertEquals(2L, h.idle.logicalIndex)
    }

    @Test fun `drag interaction cancels autoplay and presentation`() {
        val h = Harness()
        var guard = 0
        while (h.idle.autoplayPhase != AutoplayPhase.PRESENTING && guard++ < 2000) h.frame()
        assertEquals(AutoplayPhase.PRESENTING, h.idle.autoplayPhase)
        h.idle.onInteraction()
        assertEquals(AutoplayPhase.IDLE_WAIT, h.idle.autoplayPhase)
        assertFalse(h.idle.presentation.isRunning)
        assertEquals(0f, h.idle.presentationEmphasis, 0f)
        assertEquals(1, h.settled.size)
        // A fresh full wait is required before anything moves again.
        h.runFrames(5)
        assertEquals(0f, h.carousel.velocity, 0f)
        assertEquals(AutoplayPhase.IDLE_WAIT, h.idle.autoplayPhase)
    }

    @Test fun `disabling autoplay stops all frame requests`() {
        val h = Harness()
        h.idle.setAutoplayEnabled(false)
        repeat(120) { assertFalse(h.frame()) }
        assertEquals(AutoplayPhase.INACTIVE, h.idle.autoplayPhase)
    }

    @Test fun `preview state becomes really active during presentation`() {
        val h = Harness()
        var guard = 0
        while (h.idle.autoplayPhase != AutoplayPhase.PRESENTING && guard++ < 2000) h.frame()
        h.idle.onIdle()
        assertEquals(CarouselIdleController.State.PANEL_PREVIEW_ACTIVE, h.idle.state)
    }

    @Test fun `engine starts on the requested panel and tracks the settled ring`() {
        val panels = (0 until 3).map { Panel3D("panel-$it", "Panel $it", 0) }
        val engine = ShellEngine(
            spec = spec().copy(autoplayIdleDelayMs = 5_000L),
            panels = panels,
            initialSelectedIndex = 2,
        )
        engine.setAutoplayEnabled(false)
        repeat(60) { engine.tick(1f / 60f) }
        assertEquals(2, engine.selectedIndex)
        assertEquals(2L, engine.logicalIndex)
        assertEquals(2, engine.physicalIndex())
        // Manual settle re-syncs the visible selection.
        engine.carousel.snapToIndex(0, 3)
        repeat(120) { engine.tick(1f / 60f) }
        assertEquals(0, engine.selectedIndex)
        assertEquals(0L, engine.logicalIndex)
    }

    @Test fun `engine autoplay advances one panel then yields idle frames`() {
        val panels = (0 until 3).map { Panel3D("panel-$it", "Panel $it", 0) }
        val engine = ShellEngine(
            spec = spec().copy(
                autoplayIdleDelayMs = 40L,
                presentationActiveMs = 60L,
                betweenPanelsDelayMs = 30L,
            ),
            panels = panels,
            initialSelectedIndex = 0,
        )
        repeat(60) { engine.tick(1f / 60f) }
        assertEquals(0, engine.selectedIndex)
        // Real time passes the idle deadline; ticks drive the snap + presentation.
        Thread.sleep(120L)
        var guard = 0
        while (engine.selectedIndex == 0 && guard++ < 600) engine.tick(1f / 60f)
        assertEquals(1, engine.selectedIndex)
        assertEquals(1L, engine.logicalIndex)
        assertEquals(1, engine.physicalIndex())
        assertEquals(0f, engine.carousel.velocity, 0f)
        // Cancel: interaction stops everything and frames go idle.
        engine.onGestureStart()
        engine.onGestureEnd()
        repeat(120) { engine.tick(1f / 60f) }
        assertFalse(engine.presentationState.let { it.phase != PanelPresentationPhase.IDLE })
        assertEquals(0f, engine.presentationEmphasis, 0f)
    }

    @Test fun `circular walk covers every panel across many loops`() {
        val seen = LinkedHashSet<Int>()
        var logical = 0L
        repeat(200) {
            logical = CarouselCircularIndex.next(logical)
            seen.add(CarouselCircularIndex.physicalIndex(logical, count))
        }
        assertEquals((0 until count).toSet(), seen)
        // And backwards.
        seen.clear()
        repeat(200) {
            logical = CarouselCircularIndex.previous(logical)
            seen.add(CarouselCircularIndex.physicalIndex(logical, count))
        }
        assertEquals((0 until count).toSet(), seen)
    }
}
