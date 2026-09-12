package br.com.ne3d.spbshellmodern.shell3d.scene

import java.nio.ByteBuffer
import java.nio.ByteOrder

object MeshFactory {
    fun plane(halfHeight: Float): Mesh = mesh(1, 1, halfHeight)
    fun segmentedPlane(widthSegments: Int, heightSegments: Int, halfHeight: Float = 1f): Mesh {
        require(widthSegments > 0 && heightSegments > 0)
        val floats = FloatArray((widthSegments + 1) * (heightSegments + 1) * 4)
        var p = 0
        for (y in 0..heightSegments) for (x in 0..widthSegments) {
            floats[p++] = -1f + 2f * x / widthSegments; floats[p++] = -halfHeight + 2f * halfHeight * y / heightSegments
            floats[p++] = x.toFloat() / widthSegments; floats[p++] = 1f - y.toFloat() / heightSegments
        }
        return Mesh(ByteBuffer.allocateDirect(floats.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floats); position(0) }, (widthSegments + 1) * (heightSegments + 1))
    }
    private fun mesh(widthSegments: Int, heightSegments: Int, halfHeight: Float): Mesh = segmentedPlane(widthSegments, heightSegments, halfHeight)
}
