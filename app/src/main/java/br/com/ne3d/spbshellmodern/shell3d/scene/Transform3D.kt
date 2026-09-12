package br.com.ne3d.spbshellmodern.shell3d.scene

/** Mutable transform reused by the renderer; no copies are made in the frame loop. */
class Transform3D {
    var x = 0f; var y = 0f; var z = 0f
    var rotationX = 0f; var rotationY = 0f; var rotationZ = 0f
    var scaleX = 1f; var scaleY = 1f; var scaleZ = 1f
    var alpha = 1f; var visible = true
    fun setFrom(other: Transform3D) { x=other.x; y=other.y; z=other.z; rotationX=other.rotationX; rotationY=other.rotationY; rotationZ=other.rotationZ; scaleX=other.scaleX; scaleY=other.scaleY; scaleZ=other.scaleZ; alpha=other.alpha; visible=other.visible }
    fun reset() { x=0f; y=0f; z=0f; rotationX=0f; rotationY=0f; rotationZ=0f; scaleX=1f; scaleY=1f; scaleZ=1f; alpha=1f; visible=true }
}
