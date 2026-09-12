package br.com.ne3d.spbshellmodern.shell3d.debug

import android.util.Log
import android.os.Debug

/** Fixed-size sampler: frame recording allocates nothing; sorting happens only when published. */
class FrameMetrics(private val capacity: Int = 2_048) {
    private val frames = LongArray(capacity)
    private val cpu = LongArray(capacity)
    private val animation = LongArray(capacity)
    private val physics = LongArray(capacity)
    private val layout = LongArray(capacity)
    private val matrix = LongArray(capacity)
    private val draw = LongArray(capacity)
    private val texture = LongArray(capacity)
    private var count = 0
    private var previousStart = 0L
    private var over8 = 0; private var over11 = 0; private var over16 = 0; private var over33 = 0
    private var draws = 0; private var panels = 0; private var uploads = 0; private var uploadBytes = 0L
    private var gcAtStart = 0L

    fun record(startNanos: Long, cpuNanos: Long, animationNanos: Long, physicsNanos: Long, layoutNanos: Long, matrixNanos: Long, drawNanos: Long, textureNanos: Long, drawCalls: Int, drawnPanels: Int) {
        if (previousStart != 0L && count < capacity) {
            val frame = startNanos - previousStart
            frames[count] = frame; cpu[count] = cpuNanos; animation[count] = animationNanos; physics[count] = physicsNanos
            layout[count] = layoutNanos; matrix[count] = matrixNanos; draw[count] = drawNanos; texture[count] = textureNanos; count++
            if (frame > 8_330_000L) over8++
            if (frame > 11_110_000L) over11++
            if (frame > 16_670_000L) over16++
            if (frame > 33_330_000L) over33++
        }
        previousStart = startNanos; draws += drawCalls; panels += drawnPanels
    }
    fun textureUpload(bytes: Int) { uploads++; uploadBytes += bytes }
    fun reset() {
        count = 0; previousStart = 0L; over8 = 0; over11 = 0; over16 = 0; over33 = 0
        draws = 0; panels = 0; uploads = 0; uploadBytes = 0L
        gcAtStart = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: 0L
    }
    fun publish(label: String) {
        if (count == 0) return
        val sorted = frames.copyOf(count).also { it.sort() }
        fun percentile(values: LongArray, value: Float): Float {
            val sortedValues = values.copyOf(count).also { it.sort() }
            return sortedValues[((count - 1) * value).toInt()] / 1_000_000f
        }
        fun percentile(value: Float) = sorted[((count - 1) * value).toInt()] / 1_000_000f
        val total = frames.copyOf(count).sum() / 1_000_000f
        val fps = count * 1_000f / total
        val gcDelta = (Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull() ?: gcAtStart) - gcAtStart
        Log.i("Shell3D.Metrics", "$label frames=$count fps=$fps frameMs[mean=${total / count},p50=${percentile(.50f)},p90=${percentile(.90f)},p95=${percentile(.95f)},p99=${percentile(.99f)},worst=${percentile(1f)}] over8=${over8 * 100f / count}% over11=${over11 * 100f / count}% over16=${over16 * 100f / count}% over33=${over33 * 100f / count}% drawCallsAvg=${draws.toFloat() / count} panelsAvg=${panels.toFloat() / count} uploads=$uploads uploadBytes=$uploadBytes gc=$gcDelta phasesMs[p95 animation=${percentile(animation,.95f)},physics=${percentile(physics,.95f)},layout=${percentile(layout,.95f)},matrix=${percentile(matrix,.95f)},draw=${percentile(draw,.95f)},texture=${percentile(texture,.95f)},cpu=${percentile(cpu,.95f)}]")
        reset()
    }
}
