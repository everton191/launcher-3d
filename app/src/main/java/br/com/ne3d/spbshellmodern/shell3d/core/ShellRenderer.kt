package br.com.ne3d.spbshellmodern.shell3d.core

import android.opengl.*
import br.com.ne3d.spbshellmodern.shell3d.camera.ShellCamera
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselLayout
import br.com.ne3d.spbshellmodern.shell3d.carousel.MutablePanelTransform
import br.com.ne3d.spbshellmodern.shell3d.effects.EffectInput
import br.com.ne3d.spbshellmodern.shell3d.scene.MeshVertexLayout
import br.com.ne3d.spbshellmodern.shell3d.scene.MirrorFloor
import br.com.ne3d.spbshellmodern.shell3d.debug.FrameMetrics
import br.com.ne3d.spbshellmodern.shell3d.texture.TextureManager
import android.graphics.Bitmap
import android.util.Log
import android.os.Debug
import java.nio.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import java.util.concurrent.atomic.AtomicReference
import android.content.res.Resources
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneController
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetFramingProvider
import br.com.ne3d.spbshellmodern.shell3d.widgets.render.WidgetTextureRef

class ShellRenderer(
    val engine: ShellEngine,
    private val onFrameDrawn: () -> Unit,
    private val onAutoWakeNeeded: () -> Unit,
    private val onEngineIdle: () -> Unit,
    private val onSurfaceReady: () -> Unit,
    private val onExitFinished: () -> Unit,
    private val onTextureUploadsDrained: () -> Unit,
    private val pendingTextures: Array<PendingTexture>,
    private val widgetController: WidgetSceneController? = null,
    private val widgetDensity: Float = 1f,
    private val widgetResources: android.content.res.Resources? = null,
) : GLSurfaceView.Renderer {
    data class PendingTexture(val key: String, val bitmap: AtomicReference<Bitmap?>)
    private val camera = ShellCamera(engine.spec); private val textures = TextureManager(); private val vp = FloatArray(16); private val model = FloatArray(16); private val mvp = FloatArray(16)
    private val presentationWorld = FloatArray(16)
    private val layout = CarouselLayout(engine.spec); private val metrics = FrameMetrics()
    private var program = 0; private var width = 1; private var height = 1; private var lastNanos = 0L
    private var positionLocation = -1; private var uvLocation = -1; private var matrixLocation = -1; private var textureLocation = -1; private var alphaLocation = -1; private var mirrorPassLocation = -1; private var colorLocation = -1; private var useTextureLocation = -1; private var widgetPrepared = false; private var widgetAnimating = false
    private var panelHalfHeight = engine.spec.panelAspectRatio
    private val panelTransform = MutablePanelTransform(); private val widgetProjection = br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetProjection()
    private val effectInput = EffectInput()
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(.008f,.012f,.016f,1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST); GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA,GLES30.GL_ONE_MINUS_SRC_ALPHA)
        textures.resetForContext()
        program = program()
        positionLocation = GLES30.glGetAttribLocation(program, "aPosition")
        uvLocation = GLES30.glGetAttribLocation(program, "aUv")
        matrixLocation = GLES30.glGetUniformLocation(program, "uMvp")
        textureLocation = GLES30.glGetUniformLocation(program, "uTexture")
        alphaLocation = GLES30.glGetUniformLocation(program, "uAlpha")
        mirrorPassLocation = GLES30.glGetUniformLocation(program, "uMirrorPass")
        colorLocation = GLES30.glGetUniformLocation(program, "uColor")
        useTextureLocation = GLES30.glGetUniformLocation(program, "uUseTexture")
        engine.state.panels.forEach { metrics.textureUpload(textures.label(it.id, it.label, it.color).bytes) }
        lastNanos = System.nanoTime()
        onSurfaceReady()
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w; height = h
        // Cards are vertical 9:16 faces regardless of the phone viewport.
        panelHalfHeight = engine.spec.panelAspectRatio
        GLES30.glViewport(0, 0, w, h)
        if (!widgetPrepared) { widgetController?.prepare(br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext(widgetDensity, w, h, {})); widgetPrepared = widgetController != null }
    }
    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val frameCpuStart = Debug.threadCpuTimeNanos()
        val animationStart = Debug.threadCpuTimeNanos()
        // Entry/exit tracks are not active in this prototype yet; keep their cost separately visible.
        val animationNanos = Debug.threadCpuTimeNanos() - animationStart
        val physicsStart = Debug.threadCpuTimeNanos()
        val dtSeconds = ((now-lastNanos)/1_000_000_000f).coerceIn(0f,.05f)
        val active = engine.tick(dtSeconds)
        val physicsNanos = Debug.threadCpuTimeNanos() - physicsStart
        lastNanos=now
        val textureStart = Debug.threadCpuTimeNanos()
        var uploadedTexture = false
        for (pending in pendingTextures) {
            pending.bitmap.getAndSet(null)?.let { bitmap ->
                uploadedTexture = true
                metrics.textureUpload(textures.update(pending.key, bitmap).bytes)
                Log.i("Shell3D.Capture", "${pending.key} bitmap uploaded ${bitmap.width}x${bitmap.height}")
                bitmap.recycle()
            }
        }
        if (uploadedTexture && pendingTextures.none { it.bitmap.get() != null }) onTextureUploadsDrained()
        val textureNanos = Debug.threadCpuTimeNanos() - textureStart
        val matrixStart = Debug.threadCpuTimeNanos()
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val exiting = engine.exit.active
        val widgetSceneId = widgetController?.scene?.id
        val widgetFraming = widgetSceneId?.takeIf { WidgetFramingProvider.hasOverride(it) }?.let { WidgetFramingProvider.framingFor(it) }
        if (widgetFraming != null) {
            // Widget scenes own their framing; carousel camera motion stays untouched.
            camera.matrix(width, height, vp, widgetFraming.fov, widgetFraming.cameraZ, widgetFraming.cameraY, widgetFraming.lookAtY)
        } else if (widgetSceneId == "world-time") {
            camera.matrix(width, height, vp, 42f, 7.5f, 0f, 0f)
        } else {
            camera.matrix(width, height, vp, if (exiting) engine.exit.fov else engine.entry.fov, if (exiting) engine.exit.cameraZ else engine.entry.cameraZ, if (exiting) engine.exit.cameraY else engine.cameraY)
        }
        widgetProjection.update(vp, width, height); widgetController?.updateProjection(widgetProjection)
        val matrixNanos = Debug.threadCpuTimeNanos() - matrixStart
        GLES30.glUseProgram(program); val position=positionLocation; val uv=uvLocation; val matrix=matrixLocation; val texture=textureLocation; val alpha=alphaLocation
        GLES30.glUniform1i(useTextureLocation, 1); GLES30.glUniform4f(colorLocation,1f,1f,1f,1f)
        var drawn = 0
        var layoutNanos = 0L
        var panelMatrixNanos = 0L
        val drawStart = Debug.threadCpuTimeNanos()
        val count=engine.state.panels.size; engine.state.panels.forEachIndexed { index,panel ->
            val layoutStart = Debug.threadCpuTimeNanos()
            layout.transformInto(index, count, engine.carousel.angle, if (exiting) engine.exit.radius else engine.entry.radius, if (exiting) engine.exit.panelSpread else engine.entry.panelSpread, panelTransform)
            panel.baseTransform.x = panelTransform.x
            panel.baseTransform.y = panelTransform.y
            panel.baseTransform.z = panelTransform.z
            panel.baseTransform.rotationY = panelTransform.rotationY
            panel.baseTransform.scaleX = panelTransform.scale
            panel.baseTransform.scaleY = panelTransform.scale
            panel.baseTransform.scaleZ = panelTransform.scale
            panel.baseTransform.alpha = panelTransform.alpha
            panel.baseTransform.visible = panelTransform.visible
            effectInput.panelIndex = index
            effectInput.selectedIndex = engine.selectedIndex
            effectInput.angle = panelTransform.angle
            effectInput.velocity = engine.carousel.velocity
            effectInput.progress = if (exiting && index == engine.selectedIndex) engine.exit.progress else 0f
            effectInput.direction = 1f
            effectInput.phase = if (exiting && index == engine.selectedIndex) br.com.ne3d.spbshellmodern.shell3d.effects.EffectPhase.CLOSING else br.com.ne3d.spbshellmodern.shell3d.effects.EffectPhase.NONE
            panel.effectStack.apply(panel, effectInput)
            if (panel.deformerStack.isEmpty()) panel.resetRenderMesh() else panel.deformerStack.apply(panel, effectInput)
            layoutNanos += Debug.threadCpuTimeNanos() - layoutStart
            val transform = panel.renderTransform
            // Presentation proof-of-motion: front panel only, eases back to the exact base pose.
            val emphasis = engine.presentationEmphasis
            if (emphasis > 0f && widgetController == null && !exiting && index == engine.selectedIndex) {
                transform.z += emphasis * engine.spec.presentationZPush
                val boost = 1f + emphasis * engine.spec.presentationScaleBoost
                transform.scaleX *= boost; transform.scaleY *= boost; transform.scaleZ *= boost
            }
            if (!transform.visible || textures.textureId(panel.id) == 0) return@forEachIndexed
            panel.renderMesh.vertices.position(MeshVertexLayout.POSITION_FLOAT_OFFSET); GLES30.glVertexAttribPointer(position,MeshVertexLayout.POSITION_COMPONENTS,GLES30.GL_FLOAT,false,panel.renderMesh.strideBytes,panel.renderMesh.vertices); GLES30.glEnableVertexAttribArray(position)
            panel.renderMesh.vertices.position(MeshVertexLayout.UV_FLOAT_OFFSET); GLES30.glVertexAttribPointer(uv,MeshVertexLayout.UV_COMPONENTS,GLES30.GL_FLOAT,false,panel.renderMesh.strideBytes,panel.renderMesh.vertices); GLES30.glEnableVertexAttribArray(uv)
            val panelMatrixStart = Debug.threadCpuTimeNanos()
            val isExitTarget = exiting && index == engine.selectedIndex
            val entryAlpha = if (engine.entry.active && index != 0) engine.entry.sideAlpha else 1f
            panel.effectiveScale = EffectivePanelRender.scale(transform.scaleX, isExitTarget, engine.exit.progress)
            panel.effectiveAlpha = EffectivePanelRender.alpha(transform.alpha, isExitTarget, exiting, engine.exit.progress, entryAlpha)
            Matrix.setIdentityM(model,0); Matrix.translateM(model,0,transform.x,transform.y,transform.z); Matrix.rotateM(model,0,transform.rotationY,0f,1f,0f); Matrix.scaleM(model,0,panel.effectiveScale,panel.effectiveScale,panel.effectiveScale); Matrix.multiplyMM(mvp,0,vp,0,model,0)
            panelMatrixNanos += Debug.threadCpuTimeNanos() - panelMatrixStart
            GLES30.glUniformMatrix4fv(matrix,1,false,mvp,0); GLES30.glUniform1i(mirrorPassLocation,0); GLES30.glUniform1f(alpha,panel.effectiveAlpha); GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,textures.textureId(panel.id)); GLES30.glUniform1i(texture,0); panel.renderMesh.indices.position(0); GLES30.glDrawElements(GLES30.GL_TRIANGLES,panel.renderMesh.indexCount,GLES30.GL_UNSIGNED_SHORT,panel.renderMesh.indices); drawn++
            // Live presentation overlay: front panel only, panelModelMatrix x itemLocalMatrix.
            // The card texture stays: items draw additively on top of it, never as a replacement.
            val liveItems = engine.livePresentationItems
            if (liveItems.isNotEmpty() && widgetController == null && !exiting && index == engine.selectedIndex) {
                var li = 0
                while (li < liveItems.size) {
                    val item = liveItems[li++]
                    if (!item.visible || item.alpha <= 0f) continue
                    Matrix.multiplyMM(presentationWorld,0,model,0,item.localMatrix,0); Matrix.multiplyMM(mvp,0,vp,0,presentationWorld,0)
                    val mesh = item.mesh
                    mesh.vertices.position(MeshVertexLayout.POSITION_FLOAT_OFFSET); GLES30.glVertexAttribPointer(position,MeshVertexLayout.POSITION_COMPONENTS,GLES30.GL_FLOAT,false,mesh.strideBytes,mesh.vertices); GLES30.glEnableVertexAttribArray(position)
                    mesh.vertices.position(MeshVertexLayout.UV_FLOAT_OFFSET); GLES30.glVertexAttribPointer(uv,MeshVertexLayout.UV_COMPONENTS,GLES30.GL_FLOAT,false,mesh.strideBytes,mesh.vertices); GLES30.glEnableVertexAttribArray(uv)
                    var widgetTexture = 0
                    val textureRef = item.textureRef
                    if (textureRef != null) {
                        var textureId = textures.textureId(textureRef.key)
                        if (textureId == 0) {
                            val bitmap = textureRef.bitmapFactory(widgetResources ?: Resources.getSystem())
                            textures.update(textureRef.key, bitmap); bitmap.recycle()
                            textureId = textures.textureId(textureRef.key)
                        }
                        widgetTexture = textureId
                    }
                    val color = item.color
                    GLES30.glUniformMatrix4fv(matrix,1,false,mvp,0); GLES30.glUniform1i(mirrorPassLocation,0); GLES30.glUniform1i(useTextureLocation,if(widgetTexture!=0)1 else 0)
                    GLES30.glUniform4f(colorLocation,((color shr 16) and 255)/255f,((color shr 8) and 255)/255f,(color and 255)/255f,((color ushr 24) and 255)/255f)
                    GLES30.glUniform1f(alpha,item.alpha)
                    if (widgetTexture != 0) { GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,widgetTexture); GLES30.glUniform1i(texture,0) }
                    mesh.indices.position(0); GLES30.glDrawElements(GLES30.GL_TRIANGLES,mesh.indexCount,GLES30.GL_UNSIGNED_SHORT,mesh.indices); drawn++
                }
                GLES30.glUniform1i(useTextureLocation,1)
            }
        }; GLES30.glDisableVertexAttribArray(position); GLES30.glDisableVertexAttribArray(uv)
        // Mirror floor pass: every visible panel reuses the transform and mesh from the real pass.
        engine.state.panels.forEach { panel ->
            val transform = panel.renderTransform
            if (!transform.visible || textures.textureId(panel.id) == 0) return@forEach
            panel.renderMesh.vertices.position(MeshVertexLayout.POSITION_FLOAT_OFFSET); GLES30.glVertexAttribPointer(position,MeshVertexLayout.POSITION_COMPONENTS,GLES30.GL_FLOAT,false,panel.renderMesh.strideBytes,panel.renderMesh.vertices); GLES30.glEnableVertexAttribArray(position)
            panel.renderMesh.vertices.position(MeshVertexLayout.UV_FLOAT_OFFSET); GLES30.glVertexAttribPointer(uv,MeshVertexLayout.UV_COMPONENTS,GLES30.GL_FLOAT,false,panel.renderMesh.strideBytes,panel.renderMesh.vertices); GLES30.glEnableVertexAttribArray(uv)
            // Keep the floor separation equal to the ring's face-to-face gap.
            val floorY = -panelHalfHeight - engine.spec.panelGap
            Matrix.setIdentityM(model,0); Matrix.translateM(model,0,transform.x,MirrorFloor.mirroredY(floorY, transform.y),transform.z); Matrix.rotateM(model,0,transform.rotationY,0f,1f,0f); Matrix.scaleM(model,0,panel.effectiveScale,-panel.effectiveScale,panel.effectiveScale); Matrix.multiplyMM(mvp,0,vp,0,model,0)
            GLES30.glUniformMatrix4fv(matrix,1,false,mvp,0); GLES30.glUniform1i(mirrorPassLocation,1); GLES30.glUniform1f(alpha,panel.effectiveAlpha * .30f); GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,textures.textureId(panel.id)); GLES30.glUniform1i(texture,0); panel.renderMesh.indices.position(0); GLES30.glDrawElements(GLES30.GL_TRIANGLES,panel.renderMesh.indexCount,GLES30.GL_UNSIGNED_SHORT,panel.renderMesh.indices); drawn++
        }
        GLES30.glDisableVertexAttribArray(position); GLES30.glDisableVertexAttribArray(uv)
        val widgetActive = widgetController?.tick(dtSeconds) ?: false
        widgetAnimating = widgetActive
        widgetController?.scene?.graph?.collectRenderItems()
        var widgetIndex = 0
        val widgetCount = widgetController?.scene?.graph?.renderItemCount() ?: 0
        while (widgetIndex < widgetCount) {
            val item = widgetController!!.scene.graph.renderItemAt(widgetIndex); val node=item.node; val material=node.material ?: run { widgetIndex++; continue }; val mesh=node.mesh ?: run { widgetIndex++; continue }
            mesh.vertices.position(MeshVertexLayout.POSITION_FLOAT_OFFSET); GLES30.glVertexAttribPointer(position,MeshVertexLayout.POSITION_COMPONENTS,GLES30.GL_FLOAT,false,mesh.strideBytes,mesh.vertices); GLES30.glEnableVertexAttribArray(position)
            mesh.vertices.position(MeshVertexLayout.UV_FLOAT_OFFSET); GLES30.glVertexAttribPointer(uv,MeshVertexLayout.UV_COMPONENTS,GLES30.GL_FLOAT,false,mesh.strideBytes,mesh.vertices); GLES30.glEnableVertexAttribArray(uv)
            Matrix.multiplyMM(mvp,0,vp,0,node.worldMatrix,0); val color=material.color; val widgetTexture = material.textureRef?.let { ref ->
                var textureId = textures.textureId(ref.key)
                if (textureId == 0) { val bitmap = ref.bitmapFactory(widgetResources ?: android.content.res.Resources.getSystem()); textures.update(ref.key, bitmap); bitmap.recycle(); textureId = textures.textureId(ref.key) }
                textureId
            } ?: material.textureId
            GLES30.glUniformMatrix4fv(matrix,1,false,mvp,0); GLES30.glUniform1i(mirrorPassLocation,0); GLES30.glUniform1i(useTextureLocation,if(widgetTexture!=0)1 else 0); GLES30.glUniform4f(colorLocation,((color shr 16)and 255)/255f,((color shr 8)and 255)/255f,(color and 255)/255f,((color ushr 24)and 255)/255f); GLES30.glUniform1f(alpha,node.worldAlpha*material.alpha); if(widgetTexture!=0){GLES30.glActiveTexture(GLES30.GL_TEXTURE0);GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,widgetTexture);GLES30.glUniform1i(texture,0)};mesh.indices.position(0);GLES30.glDrawElements(GLES30.GL_TRIANGLES,mesh.indexCount,GLES30.GL_UNSIGNED_SHORT,mesh.indices);drawn++
            widgetIndex++
        }
        GLES30.glUniform1i(useTextureLocation, 1); GLES30.glDisableVertexAttribArray(position); GLES30.glDisableVertexAttribArray(uv)
        val drawNanos = Debug.threadCpuTimeNanos() - drawStart
        metrics.record(now, Debug.threadCpuTimeNanos() - frameCpuStart, animationNanos, physicsNanos, layoutNanos, matrixNanos + panelMatrixNanos, drawNanos, textureNanos, drawn, drawn)
        if (engine.consumeExitCompleted()) onExitFinished()
        if (engine.consumeAutoWakePending()) onAutoWakeNeeded()
        if (!active) onEngineIdle()
        onFrameDrawn()
    }
    fun beginMeasurement() = metrics.reset()
    fun publishMeasurement(label: String) = metrics.publish(label)
    fun release() { widgetController?.release(); engine.releaseEffects(); for (pending in pendingTextures) pending.bitmap.getAndSet(null)?.recycle(); textures.destroy() }
    private fun program(): Int {
        fun shader(type: Int, source: String): Int {
            return GLES30.glCreateShader(type).also { handle ->
                GLES30.glShaderSource(handle, source)
                GLES30.glCompileShader(handle)
            }
        }
        val vertex = shader(GLES30.GL_VERTEX_SHADER, """
            #version 300 es
            in vec3 aPosition; in vec2 aUv; uniform mat4 uMvp; out vec2 vUv;
            void main(){vUv=aUv;gl_Position=uMvp*vec4(aPosition,1.);}
        """.trimIndent())
        val fragment = shader(GLES30.GL_FRAGMENT_SHADER, """
            #version 300 es
            precision mediump float; in vec2 vUv; uniform sampler2D uTexture; uniform float uAlpha; uniform int uMirrorPass; uniform vec4 uColor; uniform int uUseTexture; out vec4 color;
            void main(){color=uUseTexture==1?texture(uTexture,vUv):uColor;if(color.a<=.001)discard;float fade=uMirrorPass==1?smoothstep(.20,1.,vUv.y):1.;color.a*=uAlpha*fade;}
        """.trimIndent())
        return GLES30.glCreateProgram().also { handle ->
            GLES30.glAttachShader(handle, vertex); GLES30.glAttachShader(handle, fragment)
            GLES30.glLinkProgram(handle); GLES30.glDeleteShader(vertex); GLES30.glDeleteShader(fragment)
        }
    }
}




