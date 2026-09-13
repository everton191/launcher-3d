package br.com.ne3d.spbshellmodern.shell3d.scene

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Per-panel working vertices; base shared geometry is never mutated. */
class MutableMesh(val baseMesh: Mesh) : RenderMesh {
    private val base = baseMesh
    private val data = FloatArray(base.vertexCount * 5)
    override val vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    override val indices = base.indices; override val vertexCount = base.vertexCount; override val indexCount = base.indexCount; override val strideBytes = base.strideBytes
    init { reset() }
    fun reset() { base.vertices.duplicate().apply { position(0); get(data) }; vertices.position(0); vertices.put(data); vertices.position(0) }
    fun setPosition(index: Int, x: Float, y: Float, z: Float) { val p=index*5; data[p]=x;data[p+1]=y;data[p+2]=z }
    fun x(index:Int)=data[index*5]; fun y(index:Int)=data[index*5+1]; fun z(index:Int)=data[index*5+2]
    fun commit() { vertices.position(0); vertices.put(data); vertices.position(0) }
}
