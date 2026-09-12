package br.com.ne3d.spbshellmodern.shell3d.scene

import java.nio.ByteBuffer
import java.nio.ByteOrder

object MeshFactory {
    fun plane(halfHeight: Float): Mesh = segmentedPlane(1, 1, halfHeight)
    fun segmentedPlane(widthSegments: Int, heightSegments: Int, halfHeight: Float = 1f): Mesh {
        require(widthSegments > 0 && heightSegments > 0)
        val vertexCount = (widthSegments + 1) * (heightSegments + 1)
        val vertices = FloatArray(vertexCount * 4)
        var p = 0
        for (y in 0..heightSegments) for (x in 0..widthSegments) {
            vertices[p++] = -1f + 2f * x / widthSegments; vertices[p++] = -halfHeight + 2f * halfHeight * y / heightSegments
            vertices[p++] = x.toFloat() / widthSegments; vertices[p++] = 1f - y.toFloat() / heightSegments
        }
        val indices = ShortArray(widthSegments * heightSegments * 6); p = 0
        for (y in 0 until heightSegments) for (x in 0 until widthSegments) {
            val bl = y * (widthSegments + 1) + x; val br = bl + 1; val tl = bl + widthSegments + 1; val tr = tl + 1
            indices[p++] = bl.toShort(); indices[p++] = br.toShort(); indices[p++] = tl.toShort()
            indices[p++] = br.toShort(); indices[p++] = tr.toShort(); indices[p++] = tl.toShort()
        }
        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(vertices); position(0) }
        val indexBuffer = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(indices); position(0) }
        return Mesh(vertexBuffer, indexBuffer, vertexCount, indices.size)
    }
}
