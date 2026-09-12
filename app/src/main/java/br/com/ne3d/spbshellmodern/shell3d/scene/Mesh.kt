package br.com.ne3d.spbshellmodern.shell3d.scene

import java.nio.FloatBuffer

/** CPU-side immutable geometry; GL handles intentionally stay out of the scene model. */
class Mesh(val vertices: FloatBuffer, val vertexCount: Int, val strideBytes: Int = 16)
class Material(var textureId: Int = 0, var alpha: Float = 1f)
class SceneNode(val transform: Transform3D = Transform3D())
