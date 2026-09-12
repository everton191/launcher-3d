package br.com.ne3d.spbshellmodern.shell3d

import br.com.ne3d.spbshellmodern.shell3d.core.FrameClock
import br.com.ne3d.spbshellmodern.shell3d.core.FrameReason
import br.com.ne3d.spbshellmodern.shell3d.core.FrameScheduler
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameSchedulerTest {
    @Test fun `active reason requests successive frames until removed`() {
        val clock = FakeClock(); var renders = 0; val scheduler = FrameScheduler(clock) { renders++ }
        scheduler.activate(FrameReason.PHYSICS); clock.runFrame(); assertEquals(1, renders)
        scheduler.onRenderConsumed(); clock.runFrame(); assertEquals(2, renders)
        scheduler.deactivate(FrameReason.PHYSICS); scheduler.onRenderConsumed(); clock.runFrame(); assertEquals(2, renders)
    }
    @Test fun `reasons combine one shot and delayed wake can be cancelled`() {
        val clock = FakeClock(); var renders = 0; var woke = 0; val scheduler = FrameScheduler(clock) { renders++ }
        scheduler.activate(FrameReason.PHYSICS); scheduler.activate(FrameReason.TEXTURE_UPLOAD); clock.runFrame(); assertEquals(1, renders)
        scheduler.deactivate(FrameReason.PHYSICS); scheduler.onRenderConsumed(); clock.runFrame(); assertEquals(2, renders)
        scheduler.deactivate(FrameReason.TEXTURE_UPLOAD); scheduler.onRenderConsumed(); scheduler.invalidateOnce(); clock.runFrame(); assertEquals(3, renders)
        scheduler.wakeOnceAfter(5) { woke++ }; scheduler.cancelDelayedWake(); clock.runDelayed(); assertEquals(0, woke)
        scheduler.shutdown(); scheduler.invalidateOnce(); clock.runFrame(); assertEquals(3, renders)
    }
    private class FakeClock : FrameClock {
        private val frames = ArrayDeque<() -> Unit>(); private val delayed = ArrayDeque<() -> Unit>()
        override fun postFrame(callback: () -> Unit) { frames.addLast(callback) }
        override fun postFrameDelayed(delayMillis: Long, callback: () -> Unit) { delayed.addLast(callback) }
        override fun remove(callback: () -> Unit) { frames.remove(callback); delayed.remove(callback) }
        fun runFrame() { if (frames.isNotEmpty()) frames.removeFirst()() }
        fun runDelayed() { while (delayed.isNotEmpty()) delayed.removeFirst()() }
    }
}
