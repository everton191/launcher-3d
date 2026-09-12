package br.com.ne3d.spbshellmodern.shell3d.scene

import java.nio.FloatBuffer
import java.nio.ShortBuffer

interface RenderMesh { val vertices: FloatBuffer; val indices: ShortBuffer; val vertexCount: Int; val indexCount: Int; val strideBytes: Int }
class Mesh(override val vertices: FloatBuffer, override val indices: ShortBuffer, override val vertexCount: Int, override val indexCount: Int, override val strideBytes: Int = 20) : RenderMesh
class Material(var textureId: Int = 0, var alpha: Float = 1f)
class SceneNode(val transform: Transform3D = Transform3D())
