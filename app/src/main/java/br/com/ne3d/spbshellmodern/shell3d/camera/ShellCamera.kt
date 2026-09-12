package br.com.ne3d.spbshellmodern.shell3d.camera

import android.opengl.Matrix
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselMotionSpec

class ShellCamera(private val spec: CarouselMotionSpec) {
    private val projection = FloatArray(16); private val view = FloatArray(16)
    fun matrix(width: Int, height: Int, out: FloatArray, fov: Float = spec.cameraFov, cameraZ: Float = spec.cameraZ, cameraY: Float = spec.cameraY, lookAtY: Float = spec.lookAtY) {
        Matrix.perspectiveM(projection, 0, fov, width.toFloat() / height.coerceAtLeast(1), .1f, 40f)
        Matrix.setLookAtM(view, 0, 0f, cameraY, cameraZ, 0f, lookAtY, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(out, 0, projection, 0, view, 0)
    }
}
