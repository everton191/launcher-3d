package br.com.ne3d.spbshellmodern.shell3d.scene

enum class PanelTextureKind { STATIC, REAL_SNAPSHOT }
data class Panel3D(val id: String, val label: String, val color: Int, val textureKind: PanelTextureKind = PanelTextureKind.STATIC)
