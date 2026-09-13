package br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime

object WorldTimeClockSchedule {
    fun delayToNextMinute(nowMillis: Long): Long {
        val next = (nowMillis / MINUTE_MILLIS + 1L) * MINUTE_MILLIS
        return (next - nowMillis).coerceAtLeast(1L)
    }
    const val MINUTE_MILLIS = 60_000L
}
