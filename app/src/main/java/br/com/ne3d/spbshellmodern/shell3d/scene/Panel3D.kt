package br.com.ne3d.spbshellmodern.shell3d.scene

import br.com.ne3d.spbshellmodern.shell3d.effects.EffectStack

enum class PanelTextureKind { STATIC, REAL_SNAPSHOT }
data class Panel3D(val id: String, val label: String, val color: Int, val textureKind: PanelTextureKind = PanelTextureKind.STATIC) {
    val mesh: Mesh = MeshFactory.plane(16f / 9f)
    val material = Material()
    val baseTransform = Transform3D()
    val renderTransform = Transform3D()
    val effectStack = EffectStack()
}
