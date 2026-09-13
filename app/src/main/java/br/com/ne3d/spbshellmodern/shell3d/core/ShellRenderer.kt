package br.com.ne3d.spbshellmodern.shell3d.core

import android.opengl.*
import br.com.ne3d.spbshellmodern.shell3d.camera.ShellCamera
import br.com.ne3d.spbshellmodern.shell3d.carousel.CarouselLayout
import br.com.ne3d.spbshellmodern.shell3d.carousel.MutablePanelTransform
import br.com.ne3d.spbshellmodern.shell3d.carousel.ReflectionMath
import br.com.ne3d.spbshellmodern.shell3d.effects.EffectInput
import br.com.ne3d.spbshellmodern.shell3d.scene.MeshVertexLayout
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

class ShellRenderer(
    val engine: ShellEngine,
    private val onFrameDrawn: () -> Unit,
    private val onAutoWakeNeeded: () -> Unit,
    private val onEngineIdle: () -> Unit,
    private val onSurfaceReady: () -> Unit,
    private val onExitFinished: () -> Unit,
    private val onTextureUploadsDrained: () -> Unit,
    private val pendingTextures: Array<PendingTexture>,
) : GLSurfaceView.Renderer {
    data class PendingTexture(val key: String, val bitmap: AtomicReference<Bitmap?>)
    private val camera = ShellCamera(engine.spec); private val textures = TextureManager(); private val vp = FloatArray(16); private val model = FloatArray(16); private val mvp = FloatArray(16)
    private val layout = CarouselLayout(engine.spec); private val metrics = FrameMetrics()
    private var program = 0; private var width = 1; private var height = 1; private var lastNanos = 0L
    private var positionLocation = -1; private var uvLocation = -1; private var matrixLocation = -1; private var textureLocation = -1; private var alphaLocation = -1
    private var panelHalfHeight = engine.spec.panelAspectRatio
    private val panelTransform = MutablePanelTransform()
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
        engine.state.panels.forEach { metrics.textureUpload(textures.label(it.id, it.label, it.color).bytes) }
        lastNanos = System.nanoTime()
        onSurfaceReady()
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w; height = h
        // Cards are vertical 9:16 faces regardless of the phone viewport.
        panelHalfHeight = engine.spec.panelAspectRatio
        GLES30.glViewport(0, 0, w, h)
    }
    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val frameCpuStart = Debug.threadCpuTimeNanos()
        val animationStart = Debug.threadCpuTimeNanos()
        // Entry/exit tracks are not active in this prototype yet; keep their cost separately visible.
        val animationNanos = Debug.threadCpuTimeNanos() - animationStart
        val physicsStart = Debug.threadCpuTimeNanos()
        val active = engine.tick(((now-lastNanos)/1_000_000_000f).coerceIn(0f,.05f))
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
        camera.matrix(width, height, vp, if (exiting) engine.exit.fov else engine.entry.fov, if (exiting) engine.exit.cameraZ else engine.entry.cameraZ, if (exiting) engine.exit.cameraY else engine.cameraY)
        val matrixNanos = Debug.threadCpuTimeNanos() - matrixStart
        GLES30.glUseProgram(program); val position=positionLocation; val uv=uvLocation; val matrix=matrixLocation; val texture=textureLocation; val alpha=alphaLocation
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
            panel.effectStack.apply(panel, effectInput)
            if (panel.deformerStack.isEmpty()) panel.resetRenderMesh() else panel.deformerStack.apply(panel, effectInput)
            layoutNanos += Debug.threadCpuTimeNanos() - layoutStart
            val transform = panel.renderTransform
            if (!transform.visible || textures.textureId(panel.id) == 0) return@forEachIndexed
            panel.renderMesh.vertices.position(MeshVertexLayout.POSITION_FLOAT_OFFSET); GLES30.glVertexAttribPointer(position,MeshVertexLayout.POSITION_COMPONENTS,GLES30.GL_FLOAT,false,panel.renderMesh.strideBytes,panel.renderMesh.vertices); GLES30.glEnableVertexAttribArray(position)
            panel.renderMesh.vertices.position(MeshVertexLayout.UV_FLOAT_OFFSET); GLES30.glVertexAttribPointer(uv,MeshVertexLayout.UV_COMPONENTS,GLES30.GL_FLOAT,false,panel.renderMesh.strideBytes,panel.renderMesh.vertices); GLES30.glEnableVertexAttribArray(uv)
            val panelMatrixStart = Debug.threadCpuTimeNanos()
            val exitScale = transform.scaleX * if (exiting && index == engine.selectedIndex) 1f + engine.exit.progress * .12f else 1f
            val exitAlpha = if (exiting && index != engine.selectedIndex) 1f - engine.exit.progress else 1f
            val entryAlpha = if (engine.entry.active && index != 0) engine.entry.sideAlpha else 1f
            // The closest face fades its floor reflection in and out continuously.
            val reflectionFocus = ReflectionMath.focus(panelTransform.angle, count)
            if (reflectionFocus > 0f) {
                Matrix.setIdentityM(model,0); Matrix.translateM(model,0,transform.x,transform.y - panelHalfHeight * 1.47f * exitScale,transform.z); Matrix.rotateM(model,0,transform.rotationY,0f,1f,0f); Matrix.scaleM(model,0,exitScale,-exitScale,exitScale); Matrix.multiplyMM(mvp,0,vp,0,model,0)
                GLES30.glUniformMatrix4fv(matrix,1,false,mvp,0); GLES30.glUniform1f(alpha,transform.alpha * exitAlpha * entryAlpha * .16f * reflectionFocus); GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,textures.textureId(panel.id)); GLES30.glUniform1i(texture,0); panel.renderMesh.indices.position(0); GLES30.glDrawElements(GLES30.GL_TRIANGLES,panel.renderMesh.indexCount,GLES30.GL_UNSIGNED_SHORT,panel.renderMesh.indices); drawn++
            }
            Matrix.setIdentityM(model,0); Matrix.translateM(model,0,transform.x,transform.y,transform.z); Matrix.rotateM(model,0,transform.rotationY,0f,1f,0f); Matrix.scaleM(model,0,exitScale,exitScale,exitScale); Matrix.multiplyMM(mvp,0,vp,0,model,0)
            panelMatrixNanos += Debug.threadCpuTimeNanos() - panelMatrixStart
            GLES30.glUniformMatrix4fv(matrix,1,false,mvp,0); GLES30.glUniform1f(alpha,transform.alpha * exitAlpha * entryAlpha); GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D,textures.textureId(panel.id)); GLES30.glUniform1i(texture,0); panel.renderMesh.indices.position(0); GLES30.glDrawElements(GLES30.GL_TRIANGLES,panel.renderMesh.indexCount,GLES30.GL_UNSIGNED_SHORT,panel.renderMesh.indices); drawn++
        }; GLES30.glDisableVertexAttribArray(position); GLES30.glDisableVertexAttribArray(uv)
        val drawNanos = Debug.threadCpuTimeNanos() - drawStart
        metrics.record(now, Debug.threadCpuTimeNanos() - frameCpuStart, animationNanos, physicsNanos, layoutNanos, matrixNanos + panelMatrixNanos, drawNanos, textureNanos, drawn, drawn)
        if (engine.consumeExitCompleted()) onExitFinished()
        if (engine.consumeAutoWakePending()) onAutoWakeNeeded()
        if (!active) onEngineIdle()
        onFrameDrawn()
    }
    fun beginMeasurement() = metrics.reset()
    fun publishMeasurement(label: String) = metrics.publish(label)
    fun release() { engine.releaseEffects(); for (pending in pendingTextures) pending.bitmap.getAndSet(null)?.recycle(); textures.destroy() }
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
            precision mediump float; in vec2 vUv; uniform sampler2D uTexture; uniform float uAlpha; out vec4 color;
            void main(){color=texture(uTexture,vUv);color.a*=uAlpha;}
        """.trimIndent())
        return GLES30.glCreateProgram().also { handle ->
            GLES30.glAttachShader(handle, vertex); GLES30.glAttachShader(handle, fragment)
            GLES30.glLinkProgram(handle); GLES30.glDeleteShader(vertex); GLES30.glDeleteShader(fragment)
        }
    }
}




