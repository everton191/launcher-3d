package br.com.ne3d.spbshellmodern.shell3d.effects

import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.scene.Panel3D

/** Internal-only effect selection. The product remains on NONE until a transition owns it. */
object PanelEffectDebug {
    @Volatile var mode: PanelEffectMode = PanelEffectMode.NONE
}

fun Panel3D.configureEffect(mode: PanelEffectMode, spec: PanelEffectSpec = PanelEffectSpec()) {
    effectStack.clear()
    deformerStack.clear()
    when (mode) {
        PanelEffectMode.NONE -> resetRenderMesh()
        PanelEffectMode.STACK -> effectStack.add(StackEffector(spec))
        PanelEffectMode.FOLD -> {
            mesh = MeshFactory.segmentedPlane(8, 4, 16f / 9f)
            deformerStack.add(FoldEffector(spec))
        }
        PanelEffectMode.ORIGAMI -> {
            mesh = MeshFactory.segmentedPlane(8, 4, 16f / 9f)
            deformerStack.add(OrigamiEffector(spec))
        }
    }
}
