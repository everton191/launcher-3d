package br.com.ne3d.spbshellmodern.shell3d.presentation

/** Allocation-light 4x4 helpers, column-major to match GLES.
 *
 * Same T·Rx·Ry·Rz·S convention as the widget scene graph, so wrapped scene
 * nodes and owned geometry compose identically.
 */
object PresentationMatrices {
    fun identity(): FloatArray =
        floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    /** out = a x b (column-major). Aliasing out with a or b is not allowed. */
    fun multiply(out: FloatArray, a: FloatArray, b: FloatArray) {
        var c = 0
        while (c < 4) {
            val b0 = b[c * 4]; val b1 = b[c * 4 + 1]; val b2 = b[c * 4 + 2]; val b3 = b[c * 4 + 3]
            out[c * 4] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3
            out[c * 4 + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3
            out[c * 4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
            out[c * 4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
            c++
        }
    }

    /** out = Translate(x, y, z) x Scale(sx, sy, sz). */
    fun composeTRS(
        out: FloatArray,
        x: Float, y: Float, z: Float,
        rotationX: Float, rotationY: Float, rotationZ: Float,
        scaleX: Float, scaleY: Float, scaleZ: Float,
    ) {
        out[0] = 1f; out[1] = 0f; out[2] = 0f; out[3] = 0f
        out[4] = 0f; out[5] = 1f; out[6] = 0f; out[7] = 0f
        out[8] = 0f; out[9] = 0f; out[10] = 1f; out[11] = 0f
        out[12] = x; out[13] = y; out[14] = z; out[15] = 1f
        rotateX(out, rotationX); rotateY(out, rotationY); rotateZ(out, rotationZ)
        out[0] *= scaleX; out[1] *= scaleX; out[2] *= scaleX
        out[4] *= scaleY; out[5] *= scaleY; out[6] *= scaleY
        out[8] *= scaleZ; out[9] *= scaleZ; out[10] *= scaleZ
    }

    /** out = Translate(ox, oy, oz) x Scale(scale). Presentation fit into card space. */
    fun fit(scale: Float, ox: Float, oy: Float, oz: Float, out: FloatArray) {
        composeTRS(out, ox, oy, oz, 0f, 0f, 0f, scale, scale, scale)
    }

    fun translationX(m: FloatArray): Float = m[12]
    fun translationY(m: FloatArray): Float = m[13]
    fun translationZ(m: FloatArray): Float = m[14]

    private fun rotateX(m: FloatArray, d: Float) {
        if (d == 0f) return
        val r = Math.toRadians(d.toDouble()); val c = kotlin.math.cos(r).toFloat(); val s = kotlin.math.sin(r).toFloat()
        val a4 = m[4]; val a5 = m[5]; val a6 = m[6]; val a7 = m[7]; val a8 = m[8]; val a9 = m[9]; val a10 = m[10]; val a11 = m[11]
        m[4] = a4 * c + a8 * s; m[5] = a5 * c + a9 * s; m[6] = a6 * c + a10 * s; m[7] = a7 * c + a11 * s
        m[8] = a8 * c - a4 * s; m[9] = a9 * c - a5 * s; m[10] = a10 * c - a6 * s; m[11] = a11 * c - a7 * s
    }

    private fun rotateY(m: FloatArray, d: Float) {
        if (d == 0f) return
        val r = Math.toRadians(d.toDouble()); val c = kotlin.math.cos(r).toFloat(); val s = kotlin.math.sin(r).toFloat()
        val a0 = m[0]; val a1 = m[1]; val a2 = m[2]; val a3 = m[3]; val a8 = m[8]; val a9 = m[9]; val a10 = m[10]; val a11 = m[11]
        m[0] = a0 * c - a8 * s; m[1] = a1 * c - a9 * s; m[2] = a2 * c - a10 * s; m[3] = a3 * c - a11 * s
        m[8] = a0 * s + a8 * c; m[9] = a1 * s + a9 * c; m[10] = a2 * s + a10 * c; m[11] = a3 * s + a11 * c
    }

    private fun rotateZ(m: FloatArray, d: Float) {
        if (d == 0f) return
        val r = Math.toRadians(d.toDouble()); val c = kotlin.math.cos(r).toFloat(); val s = kotlin.math.sin(r).toFloat()
        val a0 = m[0]; val a1 = m[1]; val a2 = m[2]; val a3 = m[3]; val a4 = m[4]; val a5 = m[5]; val a6 = m[6]; val a7 = m[7]
        m[0] = a0 * c + a4 * s; m[1] = a1 * c + a5 * s; m[2] = a2 * c + a6 * s; m[3] = a3 * c + a7 * s
        m[4] = a4 * c - a0 * s; m[5] = a5 * c - a1 * s; m[6] = a6 * c - a2 * s; m[7] = a7 * c - a3 * s
    }
}
