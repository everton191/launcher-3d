package br.com.ne3d.spbshellmodern.shell3d.presentation

import br.com.ne3d.spbshellmodern.shell3d.animation.AutoplayPhase
import br.com.ne3d.spbshellmodern.shell3d.animation.CarouselIdleController
import br.com.ne3d.spbshellmodern.shell3d.animation.PanelPresentationPhase
import br.com.ne3d.spbshellmodern.shell3d.animation.PanelPresentationState
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselMotionSpec
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselPhysics
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PanelPresentationLayerTest {
    private fun spec() = CarouselMotionSpec(
        autoplayIdleDelayMs = 60L,
        autoplaySnapDurationMs = 60L,
        presentationIntroMs = 60L,
        presentationActiveMs = 120L,
        presentationOutroMs = 60L,
        betweenPanelsDelayMs = 40L,
    )

    private inner class LiveHarness(val ids: (Int) -> String = { "world_time-p$it" }) {
        var now = 0L
        val carousel = CarouselPhysics(CarouselMotionSpec())
        val idle = CarouselIdleController(spec()) { now }
        val dt = 1f / 60f

        init {
            carousel.setAngle(0f)
        }

        fun frame(): Boolean {
            now += (dt * 1_000_000_000L).toLong()
            carousel.tick(dt, 5)
            return idle.tickAutoplay(dt, carousel, 5, ids) { _, _ -> Unit }
        }

        fun runUntil(phase: AutoplayPhase, max: Int = 3000) {
            var n = 0
            while (idle.autoplayPhase != phase && n++ < max) frame()
            assertEquals(phase, idle.autoplayPhase)
        }
    }

    @Test fun `registry returns the right presentation per panel id`() {
        val registry = CarouselPresentationRegistry()
        registerLauncherPresentations(registry)
        assertTrue(registry.presentationFor("world_time-022b1825-ec9f") is WorldTimePresentation)
        assertTrue(registry.presentationFor("world_time") is WorldTimePresentation)
        assertTrue(registry.presentationFor("moon") is MoonPresentation)
        assertTrue(registry.presentationFor("moon-928a601d") is MoonPresentation)
        assertTrue(registry.presentationFor("weather") is WeatherPresentation)
        assertTrue(registry.presentationFor("weather_current-abc") is WeatherPresentation)
        assertTrue(registry.presentationFor("agenda") is CalendarPresentation)
        assertTrue(registry.presentationFor("calendar-9f2c") is CalendarPresentation)
        assertTrue(registry.presentationFor("media-f64e797a") is MediaPresentation)
        assertTrue(registry.presentationFor("indicators-2f67bfe4") is SystemPresentation)
        assertTrue(registry.presentationFor("system") is SystemPresentation)
    }

    @Test fun `fallback returns NoPresentation for plain panels`() {
        val registry = CarouselPresentationRegistry()
        registerLauncherPresentations(registry)
        assertSame(NoPresentation, registry.presentationFor("home"))
        assertSame(NoPresentation, registry.presentationFor("apps-page-0"))
        assertSame(NoPresentation, registry.presentationFor(""))
    }

    @Test fun `presentation starts in intro on the settled panel only`() {
        val h = LiveHarness()
        h.runUntil(AutoplayPhase.PRESENTING)
        val state = h.idle.presentation.state
        assertEquals(PanelPresentationPhase.INTRO, state.phase)
        assertEquals("world_time-p1", state.panelId)
        // Items are collected on presentation ticks following the begin frame.
        h.frame()
        assertTrue(h.idle.liveItems.isNotEmpty())
    }

    @Test fun `outro returns exactly to base and clears the layer`() {
        val h = LiveHarness()
        h.runUntil(AutoplayPhase.PRESENTING)
        h.runUntil(AutoplayPhase.BETWEEN_PANELS)
        assertEquals(PanelPresentationPhase.IDLE, h.idle.presentation.state.phase)
        assertTrue(h.idle.liveItems.isEmpty())
        assertEquals(0f, h.idle.presentationEmphasis, 0f)
    }

    @Test fun `cancel clears the presentation immediately`() {
        val h = LiveHarness()
        h.runUntil(AutoplayPhase.PRESENTING)
        h.frame()
        assertTrue(h.idle.liveItems.isNotEmpty())
        h.idle.onInteraction()
        assertEquals(AutoplayPhase.IDLE_WAIT, h.idle.autoplayPhase)
        assertEquals(PanelPresentationPhase.IDLE, h.idle.presentation.state.phase)
        assertTrue(h.idle.liveItems.isEmpty())
        assertEquals(0f, h.idle.presentationEmphasis, 0f)
    }

    @Test fun `switching panels never carries the previous presentation`() {
        val h = LiveHarness()
        h.runUntil(AutoplayPhase.PRESENTING)
        h.frame()
        val firstId = h.idle.presentation.state.panelId
        assertTrue(h.idle.liveItems.isNotEmpty())
        h.idle.onInteraction()
        assertTrue(h.idle.liveItems.isEmpty())
        h.runUntil(AutoplayPhase.PRESENTING)
        h.frame()
        val secondId = h.idle.presentation.state.panelId
        assertTrue(firstId.isNotBlank() && secondId.isNotBlank())
        assertTrue(firstId != secondId)
        assertTrue(h.idle.liveItems.isNotEmpty())
    }

    @Test fun `disabling autoplay stops the layer and yields idle`() {
        val h = LiveHarness()
        h.runUntil(AutoplayPhase.PRESENTING)
        h.idle.setAutoplayEnabled(false)
        repeat(60) { assertFalse(h.frame()) }
        assertTrue(h.idle.liveItems.isEmpty())
        assertEquals(PanelPresentationPhase.IDLE, h.idle.presentation.state.phase)
    }

    @Test fun `world globe grows to sixty percent with a small overshoot then holds`() {
        val presentation = WorldTimePresentation()
        val id = "world_time-x"
        presentation.start(PanelPresentationState(0L, 0, id, PanelPresentationPhase.INTRO, 0f, 0f))
        fun globeScaleAt(pp: Float): Float {
            val items = drive(presentation, id, PanelPresentationPhase.INTRO, pp = pp, seconds = .05f)
            assertTrue(items.isNotEmpty())
            return abs(items[0].localMatrix[0]) / .45f
        }
        // Base 1.00 -> ~1.64 overshoot near 80% INTRO -> 1.60 at the end.
        assertEquals(1.64f, globeScaleAt(.8f), .03f)
        assertEquals(1.60f, globeScaleAt(1f), .02f)
        // ACTIVE holds 1.60 regardless of frozen phase progress.
        val held = drive(presentation, id, PanelPresentationPhase.ACTIVE, pp = 0f, seconds = .5f)
        assertTrue(held.isNotEmpty())
        assertEquals(1.60f, abs(held[0].localMatrix[0]) / .45f, .02f)
        // OUTRO returns exactly to 1.00, then nothing is drawn.
        val back = drive(presentation, id, PanelPresentationPhase.OUTRO, pp = 1f, seconds = .5f)
        assertTrue(back.isEmpty())
        presentation.stop()
        val out = ArrayList<PresentationItem>()
        presentation.collectItems(out)
        assertTrue(out.isEmpty())
        presentation.release()
    }

    @Test fun `world globe comes forward out of the card and returns exactly`() {
        val presentation = WorldTimePresentation()
        val id = "world_time-x"
        presentation.start(PanelPresentationState(0L, 0, id, PanelPresentationPhase.INTRO, 0f, 0f))
        // Early intro: near base fit (z ~0.18, scale ~0.45).
        val early = drive(presentation, id, PanelPresentationPhase.INTRO, pp = .1f, seconds = .2f)
        assertTrue(early.isNotEmpty())
        val earlyGlobe = early[0]
        // Full active: pushed forward and grown.
        val late = drive(presentation, id, PanelPresentationPhase.ACTIVE, pp = 1f, seconds = 1f)
        val lateGlobe = late[0]
        assertTrue("globe must advance in z, ${earlyGlobe.localMatrix[14]} -> ${lateGlobe.localMatrix[14]}",
            lateGlobe.localMatrix[14] > earlyGlobe.localMatrix[14] + .1f)
        assertTrue("globe must grow, ${earlyGlobe.localMatrix[0]} -> ${lateGlobe.localMatrix[0]}",
            abs(lateGlobe.localMatrix[0]) > abs(earlyGlobe.localMatrix[0]) + .05f)
        assertTrue("globe stays inside the card width", abs(lateGlobe.localMatrix[12]) < 1.05f)
        // Outro end + stop: nothing drawn, next run restarts from base.
        drive(presentation, id, PanelPresentationPhase.OUTRO, pp = 1f, seconds = .5f)
        presentation.stop()
        val out = ArrayList<PresentationItem>()
        presentation.collectItems(out)
        assertTrue(out.isEmpty())
        presentation.release()
    }

    @Test fun `world time reuses the scene globe motion`() {
        val presentation = WorldTimePresentation()
        val id = "world_time-x"
        presentation.start(PanelPresentationState(0L, 0, id, PanelPresentationPhase.ACTIVE, .5f, 1f))
        val first = drive(presentation, id, PanelPresentationPhase.ACTIVE, pp = 1f, seconds = .3f)
        assertTrue("globe plus markers expected, got ${first.size}", first.size >= 3)
        val second = drive(presentation, id, PanelPresentationPhase.ACTIVE, pp = 1f, seconds = 2f)
        assertTrue(second.size >= 3)
        // Earth spins 6 deg/s: every marker travels; match loosely across limb culling.
        val ta = first.map { Triple(translationX(it), translationY(it), translationZ(it)) }
        val tb = second.map { Triple(translationX(it), translationY(it), translationZ(it)) }
        var best = 0f
        for (a in ta) {
            var nearest = Float.MAX_VALUE
            for (b in tb) {
                nearest = kotlin.math.min(nearest, abs(a.first - b.first) + abs(a.second - b.second))
            }
            best = kotlin.math.max(best, nearest)
        }
        assertTrue("scene markers must travel, moved=$best", best > .01f)
        presentation.release()
    }

    @Test fun `weather reuses the scene sun entry`() {
        val presentation = WeatherPresentation()
        val id = "weather"
        presentation.start(PanelPresentationState(0L, 0, id, PanelPresentationPhase.INTRO, 0f, 0f))
        val early = drive(presentation, id, PanelPresentationPhase.INTRO, pp = .1f, seconds = .2f)
        val late = drive(presentation, id, PanelPresentationPhase.INTRO, pp = .9f, seconds = 3f)
        assertTrue(early.isNotEmpty() && late.isNotEmpty())
        // Sun scale (.65 -> 1 across the scene entry) is matrix element 0.
        val earlySun = early.maxByOrNull { it.mesh.vertexCount }!!
        val lateSun = late.maxByOrNull { it.mesh.vertexCount }!!
        assertTrue("sun must grow, ${earlySun.localMatrix[0]} -> ${lateSun.localMatrix[0]}",
            lateSun.localMatrix[0] > earlySun.localMatrix[0] + .05f)
        presentation.release()
    }

    @Test fun `music reuses the disc rotation in demo playing mode`() {
        val presentation = MediaPresentation()
        val id = "media-uuid"
        presentation.start(PanelPresentationState(0L, 0, id, PanelPresentationPhase.ACTIVE, .5f, 1f))
        val first = drive(presentation, id, PanelPresentationPhase.ACTIVE, pp = 1f, seconds = .2f)
        val second = drive(presentation, id, PanelPresentationPhase.ACTIVE, pp = 1f, seconds = 2f)
        // Disc spins 24 deg/s: some matrix element must change.
        var changed = 0f
        for (i in first.indices) {
            for (e in 0 until 16) {
                changed = kotlin.math.max(changed, abs(first[i].localMatrix[e] - second[i].localMatrix[e]))
            }
        }
        assertTrue("disc must turn, changed=$changed", changed > .01f)
        presentation.release()
    }

    @Test fun `system meters rise and settle`() {
        val presentation = SystemPresentation()
        val id = "indicators-uuid"
        presentation.start(PanelPresentationState(0L, 0, id, PanelPresentationPhase.INTRO, 0f, 0f))
        val low = drive(presentation, id, PanelPresentationPhase.INTRO, pp = .2f, seconds = .2f)
        val high = drive(presentation, id, PanelPresentationPhase.ACTIVE, pp = 1f, seconds = 1f)
        // Meters are items 0, 2, 4 (each followed by its glow cap); dots use unit scale.
        val lowMeter = listOf(low[0], low[2], low[4]).maxOf { it.localMatrix[5] }
        val highMeter = listOf(high[0], high[2], high[4]).maxOf { it.localMatrix[5] }
        assertTrue("meters must rise, $lowMeter -> $highMeter", highMeter > lowMeter + .2f)
        presentation.release()
    }

    @Test fun `every widget animation stays inside the card bounds`() {
        val ids = listOf("world_time-x", "moon-x", "weather", "agenda", "media-uuid", "indicators-uuid")
        for (id in ids) {
            val registry = CarouselPresentationRegistry()
            registerLauncherPresentations(registry)
            val presentation = registry.presentationFor(id)
            assertTrue(presentation !is NoPresentation)
            val out = ArrayList<PresentationItem>()
            var time = 0f
            presentation.start(PanelPresentationState(0L, 0, id, PanelPresentationPhase.INTRO, 0f, 0f))
            for (step in 0..4) {
                val pp = step / 4f
                val state = PanelPresentationState(0L, 0, id, PanelPresentationPhase.INTRO, pp / 3f, pp)
                time += 1f / 60f
                presentation.update(PresentationFrame(state.progress, pp, 1f / 60f, time), state)
                out.clear()
                presentation.collectItems(out)
                assertItemsContained(id, out)
            }
            repeat(8) {
                time += .5f
                val state = PanelPresentationState(0L, 0, id, PanelPresentationPhase.ACTIVE, .5f, .5f)
                presentation.update(PresentationFrame(.5f, .5f, 1f / 60f, time), state)
                out.clear()
                presentation.collectItems(out)
                assertItemsContained(id, out)
            }
            for (step in 0..4) {
                val pp = step / 4f
                val state = PanelPresentationState(0L, 0, id, PanelPresentationPhase.OUTRO, 2f / 3f + pp / 3f, pp)
                time += 1f / 60f
                presentation.update(PresentationFrame(state.progress, pp, 1f / 60f, time), state)
                out.clear()
                presentation.collectItems(out)
                assertItemsContained(id, out)
            }
            presentation.stop()
            out.clear()
            presentation.collectItems(out)
            assertTrue("$id must draw nothing after stop", out.isEmpty())
            presentation.release()
        }
    }

    @Test fun `moon reuses the globe pipeline with its own texture`() {
        val presentation = MoonPresentation()
        val id = "moon-x"
        presentation.start(PanelPresentationState(0L, 0, id, PanelPresentationPhase.INTRO, 0f, 0f))
        val items = drive(presentation, id, PanelPresentationPhase.ACTIVE, pp = 1f, seconds = 1f)
        assertTrue("moon plus glow expected, got ${items.size}", items.size >= 2)
        assertTrue("every moon item must be textured", items.all { it.textureRef != null })
        val keys = items.mapNotNull { it.textureRef?.key }
        assertTrue("moon surface texture missing: $keys", keys.any { it.startsWith("moon-surface-") })
        assertTrue("moon glow texture missing: $keys", keys.any { it.startsWith("moon-glow-") })
        // Same forward push as the globe: active z must exceed early-intro z.
        presentation.start(PanelPresentationState(0L, 0, id, PanelPresentationPhase.INTRO, 0f, 0f))
        val early = drive(presentation, id, PanelPresentationPhase.INTRO, pp = .1f, seconds = .2f)
        assertTrue(early.isNotEmpty())
        assertTrue(items[0].localMatrix[14] > early[0].localMatrix[14] + .1f)
        presentation.release()
    }

    @Test fun `moon spins like the globe and returns exactly`() {
        val presentation = MoonPresentation()
        val id = "moon-x"
        presentation.start(PanelPresentationState(0L, 0, id, PanelPresentationPhase.ACTIVE, .5f, 1f))
        val first = drive(presentation, id, PanelPresentationPhase.ACTIVE, pp = 1f, seconds = .3f)
        val second = drive(presentation, id, PanelPresentationPhase.ACTIVE, pp = 1f, seconds = 2f)
        var changed = 0f
        for (i in first.indices) {
            for (e in 0 until 16) {
                changed = kotlin.math.max(changed, abs(first[i].localMatrix[e] - second[i].localMatrix[e]))
            }
        }
        assertTrue("moon must turn, changed=$changed", changed > .01f)
        drive(presentation, id, PanelPresentationPhase.OUTRO, pp = 1f, seconds = .5f)
        presentation.stop()
        val out = ArrayList<PresentationItem>()
        presentation.collectItems(out)
        assertTrue(out.isEmpty())
        presentation.release()
    }

    @Test fun `calendar brings textured cards plus header, never bare cubes`() {
        val presentation = CalendarPresentation()
        val id = "agenda"
        presentation.start(PanelPresentationState(0L, 0, id, PanelPresentationPhase.ACTIVE, .5f, 1f))
        val items = drive(presentation, id, PanelPresentationPhase.ACTIVE, pp = 1f, seconds = .5f)
        // 3 demo events + header.
        assertEquals("header plus 3 cards expected, got ${items.size}", 4, items.size)
        assertTrue("every calendar item must be textured", items.all { it.textureRef != null })
        val keys = items.map { it.textureRef!!.key }
        assertTrue("header texture missing: $keys", keys.any { it.startsWith("agenda-header-") })
        assertTrue("card textures missing: $keys", keys.count { it.startsWith("agenda-card-") } == 3)
        assertTrue("selected card must lead: $keys", keys.any { it.contains("selected") })
        drive(presentation, id, PanelPresentationPhase.OUTRO, pp = 1f, seconds = .5f)
        presentation.stop()
        val out = ArrayList<PresentationItem>()
        presentation.collectItems(out)
        assertTrue(out.isEmpty())
        presentation.release()
    }

    @Test fun `weather sun and clouds carry original textures`() {
        val presentation = WeatherPresentation()
        val id = "weather"
        presentation.start(PanelPresentationState(0L, 0, id, PanelPresentationPhase.ACTIVE, .5f, 1f))
        val items = drive(presentation, id, PanelPresentationPhase.ACTIVE, pp = 1f, seconds = .5f)
        val keys = items.mapNotNull { it.textureRef?.key }
        assertTrue("sun texture missing: $keys", keys.any { it.startsWith("weather-sun-") })
        assertTrue("halo texture missing: $keys", keys.any { it.startsWith("weather-halo-") })
        assertTrue("cloud texture missing: $keys", keys.any { it.startsWith("weather-cloud-") })
        presentation.release()
    }

    @Test fun `plain card layer draws nothing and needs no frames`() {
        val out = ArrayList<PresentationItem>()
        val state = PanelPresentationState(0L, 0, "home", PanelPresentationPhase.ACTIVE, .5f, .5f)
        NoPresentation.start(state)
        assertFalse(NoPresentation.update(PresentationFrame(.5f, .5f, 1f / 60f, 1f), state))
        NoPresentation.collectItems(out)
        assertTrue(out.isEmpty())
    }

    @Test fun `matrix helpers compose panel-local transforms`() {
        val fit = FloatArray(16)
        PresentationMatrices.fit(.5f, .1f, .2f, .3f, fit)
        assertEquals(.1f, fit[12], 0f)
        assertEquals(.2f, fit[13], 0f)
        assertEquals(.3f, fit[14], 0f)
        assertEquals(.5f, fit[0], 0f)
        val point = floatArrayOf(1f, 0f, 0f, 1f)
        val out = FloatArray(4)
        for (row in 0 until 4) {
            out[row] = fit[row] * point[0] + fit[row + 4] * point[1] + fit[row + 8] * point[2] + fit[row + 12] * point[3]
        }
        assertEquals(.6f, out[0], .0001f)
        assertEquals(.2f, out[1], .0001f)
        val id = PresentationMatrices.identity()
        val copy = FloatArray(16)
        PresentationMatrices.multiply(copy, fit, id)
        for (i in 0 until 16) assertEquals(fit[i], copy[i], 0f)
    }

    /** Ticks the presentation forward in scene time, then collects. */
    private fun drive(
        presentation: PanelPresentation,
        id: String,
        phase: PanelPresentationPhase,
        pp: Float,
        seconds: Float,
        dt: Float = 1f / 60f,
    ): List<PresentationItem> {
        var time = 0f
        val progress = when (phase) {
            PanelPresentationPhase.INTRO -> pp / 3f
            PanelPresentationPhase.ACTIVE -> .5f
            PanelPresentationPhase.OUTRO -> 2f / 3f + pp / 3f
            PanelPresentationPhase.IDLE -> 0f
        }
        val state = PanelPresentationState(0L, 0, id, phase, progress, pp)
        val steps = (seconds / dt).toInt().coerceAtLeast(1)
        repeat(steps) {
            time += dt
            presentation.update(PresentationFrame(progress, pp, dt, time), state)
        }
        val out = ArrayList<PresentationItem>()
        presentation.collectItems(out)
        return out
    }

    private fun translationX(item: PresentationItem): Float = item.localMatrix[12]
    private fun translationY(item: PresentationItem): Float = item.localMatrix[13]
    private fun translationZ(item: PresentationItem): Float = item.localMatrix[14]

    private fun assertItemsContained(id: String, items: List<PresentationItem>) {
        for (item in items) {
            val m = item.localMatrix
            for (e in m) assertTrue("$id matrix not finite", e.isFinite())
            assertTrue("$id x out of bounds: ${m[12]}", m[12] in -1.05f..1.05f)
            assertTrue("$id y out of bounds: ${m[13]}", m[13] in -1.85f..1.85f)
            assertTrue("$id z out of bounds: ${m[14]}", m[14] in -.1f..1.2f)
            assertTrue("$id alpha out of bounds: ${item.alpha}", item.alpha in 0f..1f)
            assertTrue("$id mesh empty", item.mesh.vertexCount > 0 && item.mesh.indexCount > 0)
        }
    }
}
