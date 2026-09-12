package br.com.ne3d.spbshellmodern.shell3d.scene

import java.nio.FloatBuffer
import java.nio.ShortBuffer

/** CPU-side indexed geometry. No GL handle survives context loss. */
class Mesh(val vertices: FloatBuffer, val indices: ShortBuffer, val vertexCount: Int, val indexCount: Int, val strideBytes: Int = 16)
class Material(var textureId: Int = 0, var alpha: Float = 1f)
class SceneNode(val transform: Transform3D = Transform3D())
