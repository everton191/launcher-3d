package br.com.ne3d.spbshellmodern.shell3d.widgets

/** Reused screen projection state supplied by the renderer; no GL handles escape to scenes. */
class WidgetScreenPoint(var x: Float = 0f, var y: Float = 0f, var depth: Float = 0f, var visible: Boolean = false)
class WidgetProjection {
    private val vp = FloatArray(16)
    var width = 1; private set
    var height = 1; private set
    fun update(matrix: FloatArray, viewportWidth: Int, viewportHeight: Int) {
        var i = 0; while (i < 16) { vp[i] = matrix[i]; i++ }
        width = viewportWidth.coerceAtLeast(1); height = viewportHeight.coerceAtLeast(1)
    }
    fun projectOrigin(world: FloatArray, out: WidgetScreenPoint): Boolean {
        val x = world[12]; val y = world[13]; val z = world[14]
        val cx = vp[0]*x + vp[4]*y + vp[8]*z + vp[12]
        val cy = vp[1]*x + vp[5]*y + vp[9]*z + vp[13]
        val cz = vp[2]*x + vp[6]*y + vp[10]*z + vp[14]
        val cw = vp[3]*x + vp[7]*y + vp[11]*z + vp[15]
        if (cw <= 0f) { out.visible = false; return false }
        val nx = cx / cw; val ny = cy / cw; val nz = cz / cw
        out.x = (nx + 1f) * .5f * width; out.y = (1f - ny) * .5f * height; out.depth = nz
        out.visible = nx in -1f..1f && ny in -1f..1f && nz in -1f..1f
        return out.visible
    }
}
