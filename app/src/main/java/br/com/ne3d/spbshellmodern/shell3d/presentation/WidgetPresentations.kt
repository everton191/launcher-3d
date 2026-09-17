package br.com.ne3d.spbshellmodern.shell3d.presentation

import br.com.ne3d.spbshellmodern.shell3d.animation.PanelPresentationPhase
import br.com.ne3d.spbshellmodern.shell3d.animation.PanelPresentationState
import br.com.ne3d.spbshellmodern.shell3d.scene.MeshFactory
import br.com.ne3d.spbshellmodern.shell3d.scene.Mesh
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.WidgetSceneContext
import br.com.ne3d.spbshellmodern.shell3d.widgets.music.MusicScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.music.MusicSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.personal.CalendarEventValue
import br.com.ne3d.spbshellmodern.shell3d.widgets.personal.CalendarScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.personal.CalendarSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.scene.SceneNode
import br.com.ne3d.spbshellmodern.shell3d.widgets.weather.WeatherCondition
import br.com.ne3d.spbshellmodern.shell3d.widgets.weather.WeatherScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.weather.WeatherSnapshot
import br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime.MoonScene
import br.com.ne3d.spbshellmodern.shell3d.widgets.worldtime.WorldTimeScene

private val syntheticSceneContext =
    WidgetSceneContext(density = 2f, viewportWidth = 384, viewportHeight = 540, invalidateOnce = {})

/** Presentation driven by a real WidgetScene: same tick, same nodes, same
 * textures. Only a small node subset is exposed, fitted into card space, so
 * the card texture stays visible underneath and no full-scene backdrop
 * (sky, album) ever replaces it. The scene instance is private per run:
 * no shared state, no datasource needed.
 */
abstract class SceneBackedPresentation(
    id: String,
    private val fitScale: Float,
    private val fitX: Float,
    private val fitY: Float,
    private val fitZ: Float,
    /** Extra uniform growth at full envelope (globe comes toward the viewer). */
    private val depthGrowScale: Float = 0f,
    /** Extra forward push at full envelope. Vanishes exactly with the envelope. */
    private val depthPushZ: Float = 0f,
) : PanelPresentation {
    private val fit = FloatArray(16)
    protected var scene: WidgetScene? = null
    private var prepared = false
    protected var envelope = 0f
    protected var lastTimeSeconds = 0f
    private var lastPhase = PanelPresentationPhase.IDLE
    private var lastPhaseProgress = 0f

    override val presentationId: String = id

    protected abstract fun createScene(): WidgetScene

    /** Nodes to expose, in draw order. Sky/backdrop nodes must be excluded here. */
    protected abstract fun liveNodes(scene: WidgetScene): List<SceneNode>

    protected open fun onBegin(state: PanelPresentationState) {}
    protected open fun onPose(frame: PresentationFrame, state: PanelPresentationState) {}
    protected open fun onCollectExtras(out: MutableList<PresentationItem>, envelope: Float) {}

    /** Uniform growth applied to the fit each frame. Exact 1.0 when the envelope is 0. */
    protected open fun scaleMultiplier(
        phase: PanelPresentationPhase,
        phaseProgress: Float,
        envelope: Float,
    ): Float = 1f + depthGrowScale * envelope

    override fun prepare() {
        if (prepared) return
        prepared = true
        val s = createScene()
        s.prepare(syntheticSceneContext)
        scene = s
    }

    override fun start(state: PanelPresentationState) {
        prepare()
        envelope = 0f
        lastTimeSeconds = 0f
        lastPhase = PanelPresentationPhase.IDLE
        lastPhaseProgress = 0f
        onBegin(state)
    }

    override fun update(frame: PresentationFrame, state: PanelPresentationState): Boolean {
        if (state.phase == PanelPresentationPhase.IDLE) {
            envelope = 0f
            return false
        }
        val s = scene ?: return false
        val sceneActive = s.tick(frame.dtSeconds)
        lastTimeSeconds = frame.timeSeconds
        envelope = PresentationMotion.envelope(state.phase, frame.phaseProgress)
        lastPhase = state.phase
        lastPhaseProgress = frame.phaseProgress
        onPose(frame, state)
        return sceneActive || state.phase == PanelPresentationPhase.INTRO || state.phase == PanelPresentationPhase.OUTRO
    }

    override fun collectItems(out: MutableList<PresentationItem>) {
        val s = scene ?: return
        if (envelope <= 0.001f) return
        PresentationMatrices.fit(
            fitScale * scaleMultiplier(lastPhase, lastPhaseProgress, envelope),
            fitX, fitY, fitZ + depthPushZ * envelope, fit,
        )
        for (node in liveNodes(s)) {
            val mesh = node.mesh ?: continue
            val material = node.material ?: continue
            if (!node.worldVisible) continue
            // Behind the card face: depth-occluded anyway (globe far side, back markers).
            if (node.worldMatrix[14] < -0.05f) continue
            val alpha = (node.worldAlpha * material.alpha * envelope).coerceIn(0f, 1f)
            if (alpha <= 0.001f) continue
            val m = FloatArray(16)
            PresentationMatrices.multiply(m, fit, node.worldMatrix)
            out.add(PresentationItem(mesh, material.color, alpha, material.textureRef, m, true))
        }
        onCollectExtras(out, envelope)
    }

    override fun stop() {
        envelope = 0f
        lastTimeSeconds = 0f
        lastPhase = PanelPresentationPhase.IDLE
        lastPhaseProgress = 0f
    }

    override fun release() {
        scene?.release()
        scene = null
        prepared = false
    }
}

/** World time: the scene's own globe keeps spinning (6 deg/s), its own
 * markers pulse. The globe leans forward out of the card while presenting
 * and settles back exactly. No second globe is created anywhere. */
class WorldTimePresentation : SceneBackedPresentation("world-time", .45f, 0f, .1f, .18f, .60f, .24f) {
    override fun scaleMultiplier(
        phase: PanelPresentationPhase,
        phaseProgress: Float,
        envelope: Float,
    ): Float {
        // Base 1.00 -> ~1.64 overshoot at 75-85% INTRO -> 1.60 hold -> 1.00 back.
        val grow = when (phase) {
            PanelPresentationPhase.INTRO ->
                .6f * (PresentationMotion.smoothstep(phaseProgress) +
                    .29f * kotlin.math.sin(phaseProgress * Math.PI.toFloat()).toFloat())
            PanelPresentationPhase.ACTIVE -> .6f
            PanelPresentationPhase.OUTRO -> .6f * (1f - PresentationMotion.smoothstep(phaseProgress))
            PanelPresentationPhase.IDLE -> 0f
        }
        return 1f + grow
    }
    override fun createScene(): WidgetScene = WorldTimeScene()

    private fun markers(scene: WidgetScene): List<SceneNode> {
        val earth = scene.graph.find("earth") ?: return emptyList()
        return earth.children().filter { it.id.startsWith("marker-") }.take(3)
    }

    override fun liveNodes(scene: WidgetScene): List<SceneNode> {
        val out = ArrayList<SceneNode>()
        scene.graph.find("earth")?.let(out::add)
        scene.graph.find("cloud-layer")?.let(out::add)
        out.addAll(markers(scene))
        return out
    }

    override fun onPose(frame: PresentationFrame, state: PanelPresentationState) {
        val s = scene ?: return
        markers(s).forEachIndexed { i, marker ->
            marker.material?.alpha =
                (.55f + .45f * PresentationMotion.pulse(lastTimeSeconds, 1.1f, i * .37f)).coerceIn(0f, 1f)
        }
        // Gentle presentation lean. The scene only ever writes rotationY.
        s.graph.find("earth")?.local?.rotationX =
            (12f + 3f * kotlin.math.sin(lastTimeSeconds * .8f).toFloat()) * envelope
    }

    override fun start(state: PanelPresentationState) {
        super.start(state)
        val s = scene ?: return
        s.graph.find("earth")?.local?.rotationX = 0f
        markers(s).forEach { it.material?.alpha = 1f }
    }
}

/** Moon: o mesmo sistema do globo, com tema lunar. A esfera texturizada
 * gira (5 deg/s da cena), o halo pulsa discreto, o conjunto sai do card
 * para frente e retorna exatamente. Nenhum segundo motor é criado. */
class MoonPresentation : SceneBackedPresentation("moon", .45f, 0f, .1f, .18f, .60f, .24f) {
    private var glowBaseAlpha = .55f
    override fun scaleMultiplier(
        phase: PanelPresentationPhase,
        phaseProgress: Float,
        envelope: Float,
    ): Float {
        // Base 1.00 -> ~1.64 overshoot at 75-85% INTRO -> 1.60 hold -> 1.00 back.
        val grow = when (phase) {
            PanelPresentationPhase.INTRO ->
                .6f * (PresentationMotion.smoothstep(phaseProgress) +
                    .29f * kotlin.math.sin(phaseProgress * Math.PI.toFloat()).toFloat())
            PanelPresentationPhase.ACTIVE -> .6f
            PanelPresentationPhase.OUTRO -> .6f * (1f - PresentationMotion.smoothstep(phaseProgress))
            PanelPresentationPhase.IDLE -> 0f
        }
        return 1f + grow
    }
    override fun createScene(): WidgetScene = MoonScene()

    override fun liveNodes(scene: WidgetScene): List<SceneNode> =
        listOfNotNull(scene.graph.find("moon"), scene.graph.find("moon-glow"))

    override fun onPose(frame: PresentationFrame, state: PanelPresentationState) {
        val s = scene ?: return
        // Gentle presentation lean. The scene only ever writes rotationY.
        s.graph.find("moon")?.local?.rotationX =
            (12f + 3f * kotlin.math.sin(lastTimeSeconds * .8f).toFloat()) * envelope
        s.graph.find("moon-glow")?.let {
            it.material?.alpha =
                (glowBaseAlpha * (1f + .18f * PresentationMotion.pulse(lastTimeSeconds, .9f))).coerceIn(0f, 1f)
        }
    }

    override fun start(state: PanelPresentationState) {
        super.start(state)
        val s = scene ?: return
        s.graph.find("moon")?.local?.rotationX = 0f
        s.graph.find("moon-glow")?.let {
            glowBaseAlpha = it.material?.alpha ?: .55f
            it.material?.alpha = glowBaseAlpha
        }
    }
}

/** Weather: the scene's own entry run (sun scale, halo fade) on a neutral
 * partly-cloudy snapshot, retriggered every cycle. Sky backdrop excluded. */
class WeatherPresentation : SceneBackedPresentation("weather", .9f, 0f, .05f, .12f) {
    private var sunBaseX = .6f
    private var sunBaseY = .46f
    private var haloBaseScale = .42f
    private val cloudBaseX = FloatArray(4)
    override fun createScene(): WidgetScene = WeatherScene()

    override fun onBegin(state: PanelPresentationState) {
        scene?.update(
            WeatherSnapshot(
                revision = 1L, available = true, city = "", temperature = null,
                description = "", code = null, condition = WeatherCondition.PARTLY_CLOUDY,
                forecast = emptyList(), updatedAtMillis = 0L,
            )
        )
        val s = scene ?: return
        s.graph.find("sun")?.let { sunBaseX = it.local.x; sunBaseY = it.local.y }
        s.graph.find("sun-halo")?.let { haloBaseScale = it.local.scaleX }
        for (i in 0 until 4) s.graph.find("cloud-$i")?.let { cloudBaseX[i] = it.local.x }
    }

    override fun onPose(frame: PresentationFrame, state: PanelPresentationState) {
        // The scene writes sun scale/alpha and halo alpha only; positions and
        // halo size are free for presentation drift. All offsets vanish with the envelope.
        val s = scene ?: return
        val t = lastTimeSeconds
        val e = envelope
        s.graph.find("sun")?.let {
            it.local.x = sunBaseX + .05f * kotlin.math.sin(t * .6f).toFloat() * e
            it.local.y = sunBaseY + .05f * kotlin.math.cos(t * .45f).toFloat() * e
            it.local.z = (.02f + .03f * kotlin.math.sin(t * .8f).toFloat()) * e
        }
        s.graph.find("sun-halo")?.let {
            val pulse = haloBaseScale * (1f + .1f * PresentationMotion.pulse(t, .9f)) * e + haloBaseScale * (1f - e)
            it.local.scaleX = pulse
            it.local.scaleY = pulse
        }
        for (i in 0 until 4) {
            s.graph.find("cloud-$i")?.let {
                it.local.x = cloudBaseX[i] + .25f * kotlin.math.sin(t * .5f + i * 1.7f).toFloat() * e
            }
        }
    }

    override fun liveNodes(scene: WidgetScene): List<SceneNode> {
        val out = ArrayList<SceneNode>()
        scene.graph.find("sun")?.let(out::add)
        scene.graph.find("sun-halo")?.let(out::add)
        for (i in 0 until 4) scene.graph.find("cloud-$i")?.let(out::add)
        scene.graph.find("lightning")?.let(out::add)
        for (i in 0 until 5) scene.graph.find("forecast-$i")?.let(out::add)
        return out
    }
}

/** Agenda: os cartões texturizados da cena vêm para frente em pilha
 * (2-4 eventos + cabeçalho), com flutuação leve que some com o envelope.
 * A base texture do card continua por baixo; nada vira bloco sólido. */
class CalendarPresentation : SceneBackedPresentation("calendar", .95f, 0f, -.05f, .12f, .15f, .18f) {
    private val cardBaseY = FloatArray(4)

    override fun createScene(): WidgetScene = CalendarScene()

    override fun onBegin(state: PanelPresentationState) {
        scene?.update(
            CalendarSnapshot(
                revision = 1L, available = true,
                events = listOf(
                    CalendarEventValue("demo-1", "Daily sync", 0L),
                    CalendarEventValue("demo-2", "Design review", 3_600_000L),
                    CalendarEventValue("demo-3", "Launch retro", 7_200_000L),
                ),
            )
        )
        val s = scene ?: return
        for (i in 0 until 4) s.graph.find("card-$i")?.let { cardBaseY[i] = it.local.y }
    }

    override fun liveNodes(scene: WidgetScene): List<SceneNode> {
        val out = ArrayList<SceneNode>()
        scene.graph.find("agenda-header")?.let(out::add)
        for (i in 0 until 4) scene.graph.find("card-$i")?.let(out::add)
        return out
    }

    override fun onPose(frame: PresentationFrame, state: PanelPresentationState) {
        // A cena escreve local.z dos cartões; o balanço vertical é da apresentação.
        // Todos os offsets somem com o envelope.
        val s = scene ?: return
        val t = lastTimeSeconds
        val e = envelope
        for (i in 0 until 4) {
            s.graph.find("card-$i")?.let {
                it.local.y = cardBaseY[i] + .03f * kotlin.math.sin(t * 1.1f + i * .9f).toFloat() * e
            }
        }
    }
}

/** Media: the scene's own disc keeps spinning in visual-demo playing mode,
 * plus a small owned equalizer so the motion reads at card scale. */
class MediaPresentation : SceneBackedPresentation("media", .8f, -.05f, .1f, .05f, .2f, .15f) {
    private var eqMesh: Mesh? = null
    private var discBaseY = 0f

    override fun createScene(): WidgetScene = MusicScene()

    override fun onBegin(state: PanelPresentationState) {
        scene?.update(
            MusicSnapshot(
                revision = 1L, playing = true, title = "", artist = "",
                durationMillis = 0L, positionMillis = 0L, packageName = "",
            )
        )
        if (eqMesh == null) eqMesh = MeshFactory.cube(.16f)
        discBaseY = scene?.graph?.find("disc")?.local?.y ?: 0f
    }

    override fun liveNodes(scene: WidgetScene): List<SceneNode> =
        listOfNotNull(scene.graph.find("disc"))

    override fun onPose(frame: PresentationFrame, state: PanelPresentationState) {
        // The scene writes disc rotationZ only; a gentle float is presentation-owned.
        scene?.graph?.find("disc")?.let {
            it.local.y = discBaseY + .06f * kotlin.math.sin(lastTimeSeconds * 1.1f).toFloat() * envelope
        }
    }

    override fun onCollectExtras(out: MutableList<PresentationItem>, envelope: Float) {
        val mesh = eqMesh ?: return
        for (i in 0 until 4) {
            val h = (.15f + .50f * PresentationMotion.pulse(lastTimeSeconds, 2.2f, i * .31f)) * envelope
            val m = FloatArray(16)
            PresentationMatrices.composeTRS(
                m, -.36f + i * .24f, -.75f + h / 2f, .10f,
                0f, 0f, 0f, 1f, (h / .16f).coerceAtLeast(.05f), 1f,
            )
            out.add(PresentationItem(mesh, 0xFFB6D64A.toInt(), .95f * envelope, null, m, true))
        }
    }

    override fun release() {
        super.release()
        eqMesh = null
    }
}

/** System: the scene has no temporal animation yet, so this entry motion
 * (meters rise sequentially, caps glow, status dots light up) is the
 * presentation added for it. Fixed demo levels; the motion is the proof. */
class SystemPresentation : PanelPresentation {
    override val presentationId: String = "system"
    private val levels = floatArrayOf(.8f, .65f, .9f)
    private val colors = intArrayOf(0xFF53AE70.toInt(), 0xFF4B89C8.toInt(), 0xFFB6D64A.toInt())
    private var meterMesh: Mesh? = null
    private var capMesh: Mesh? = null
    private var dotMesh: Mesh? = null
    private var prepared = false
    private var envelope = 0f
    private var timeSeconds = 0f
    /** Highest phase progress seen: INTRO ramps it, ACTIVE holds it, OUTRO fades opacity. */
    private var peakPhaseProgress = 0f

    override fun prepare() {
        if (prepared) return
        prepared = true
        meterMesh = MeshFactory.cube(.22f)
        capMesh = MeshFactory.hexTile(.13f)
        dotMesh = MeshFactory.uvSphere(8, 6, .06f)
    }

    override fun start(state: PanelPresentationState) {
        prepare()
        envelope = 0f
        timeSeconds = 0f
        peakPhaseProgress = 0f
    }

    override fun update(frame: PresentationFrame, state: PanelPresentationState): Boolean {
        if (state.phase == PanelPresentationPhase.IDLE) {
            envelope = 0f
            return false
        }
        timeSeconds = frame.timeSeconds
        peakPhaseProgress = kotlin.math.max(peakPhaseProgress, frame.phaseProgress)
        envelope = PresentationMotion.envelope(state.phase, frame.phaseProgress)
        return true
    }

    override fun collectItems(out: MutableList<PresentationItem>) {
        if (envelope <= 0.001f) return
        val meter = meterMesh ?: return
        val cap = capMesh ?: return
        val dot = dotMesh ?: return
        // Group pushes forward out of the card while rising; caps slowly turn.
        val pushZ = .15f * envelope
        for (i in 0 until 3) {
            val grow = PresentationMotion.stagger(peakPhaseProgress, i, 3) * envelope
            val h = (grow * levels[i] * 1.4f).coerceAtLeast(.02f)
            out.add(item(meter, colors[i], envelope, -.5f + i * .5f, -.7f + h / 2f, .10f + pushZ,
                0f, 0f, 0f, 1f, h / .22f, 1f))
            out.add(item(cap, colors[i], (.25f + .55f * PresentationMotion.pulse(timeSeconds, 2f, i * .4f)) * grow,
                -.5f + i * .5f, -.7f + h + .1f, .14f + pushZ,
                0f, 0f, timeSeconds * 30f * envelope, 1f, 1f, 1f))
        }
        for (i in 0 until 3) {
            val lit = ((timeSeconds * 1.2f + (2 - i)) % 3f) < 1f
            out.add(item(dot, 0xFFFFFFFF.toInt(), (if (lit) 1f else .25f) * envelope,
                -.3f + i * .3f, .95f, .10f + pushZ, 0f, 0f, 0f, 1f, 1f, 1f))
        }
    }

    private fun item(
        mesh: Mesh, color: Int, alpha: Float,
        x: Float, y: Float, z: Float, rx: Float, ry: Float, rz: Float, sx: Float, sy: Float, sz: Float,
    ): PresentationItem {
        val m = FloatArray(16)
        PresentationMatrices.composeTRS(m, x, y, z, rx, ry, rz, sx, sy, sz)
        return PresentationItem(mesh, color, alpha.coerceIn(0f, 1f), null, m, true)
    }

    override fun stop() {
        envelope = 0f
        timeSeconds = 0f
        peakPhaseProgress = 0f
    }

    override fun release() {
        meterMesh = null
        capMesh = null
        dotMesh = null
        prepared = false
    }
}
