package it.libraccio.app.room3d

import android.graphics.Bitmap
import android.graphics.RectF
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Disegna la stanza. Tutto lo stato GL vive sul thread di rendering: dall'esterno si passa
 * solo da [post], le cui operazioni vengono eseguite all'inizio del frame successivo.
 */
class LibraryRenderer(private val callbacks: Callbacks) : GLSurfaceView.Renderer {

    interface Callbacks {
        fun onBookTapped(id: String)
        fun onPulledOut(id: String, coverRect: RectF)
        fun onReturned()
        fun requestRender()
    }

    private val tasks = ConcurrentLinkedQueue<() -> Unit>()
    fun post(task: () -> Unit) { tasks.add(task); callbacks.requestRender() }

    // --- risorse GL ---
    private var prog = 0
    private var depthProg = 0
    private val locs = HashMap<String, Int>()
    private val depthLocs = HashMap<String, Int>()
    private lateinit var meshes: Map<MeshKind, Mesh>
    private var shadowFbo = 0
    private var shadowTex = 0
    private val shadowSize = 2048
    private var pagesTex = 0
    private var whiteTex = 0
    private var anisotropy = false

    // --- dati della scena (bitmap conservate per ricaricarle se il contesto si perde) ---
    private var layout: SceneLayout? = null
    private var palette: RoomPalette? = null
    private val themeBitmaps = HashMap<String, Bitmap>()
    private val themeTex = HashMap<String, Int>()
    private val atlases = HashMap<String, BookAtlas>()
    private val bookTex = HashMap<String, Int>()
    private var night = false

    // --- camera ---
    private var width = 1
    private var height = 1
    private var yaw = 0.55f; private var pitch = 0.25f; private var dist = 6f
    private var target = floatArrayOf(1.2f, 1.0f, 0.3f)
    private var gYaw = 0f; private var gPitch = 0.1f; private var gDist = 3f
    private var gTarget = floatArrayOf(1.2f, 1.0f, 0.3f)
    private var fade = 0f
    private var initializedCamera = false
    private var eye = FloatArray(3)
    private var viewProj = M.identity()
    private var lightVP = M.identity()
    private var lastNanos = 0L

    // --- libro selezionato ---
    private var selected: BookDraw? = null
    private var selT = 0f
    private var selDir = 0
    private var notifiedOut = false
    private var frozenEye = FloatArray(3)
    private var frozenQ = Q.identity()
    private var frozenFwd = floatArrayOf(0f, 0f, -1f)

    // ---------------------------------------------------------------- API (via post)

    fun setScene(newLayout: SceneLayout, newPalette: RoomPalette, themeChanged: Map<String, Bitmap>?) = post {
        val first = layout == null
        layout = newLayout
        palette = newPalette
        if (themeChanged != null) {
            themeTex.values.forEach { GLES30.glDeleteTextures(1, intArrayOf(it), 0) }
            themeTex.clear()
            themeBitmaps.clear()
            themeBitmaps.putAll(themeChanged)
        }
        val ids = newLayout.books.map { it.book.id }.toSet()
        (bookTex.keys - ids).forEach { id -> bookTex.remove(id)?.let { GLES30.glDeleteTextures(1, intArrayOf(it), 0) } }
        (atlases.keys - ids).forEach { atlases.remove(it) }
        selected?.let { s -> if (s.book.id !in ids) { selected = null; selT = 0f; selDir = 0 } }
        clampGoals()
        if (first || !initializedCamera) {
            gTarget = newLayout.homeTarget.copyOf()
            gDist = newLayout.homeDistance
            gYaw = 0f
            gPitch = 0.08f
            target = floatArrayOf(gTarget[0] - 0.6f, gTarget[1] + 0.3f, gTarget[2])
            dist = gDist * 1.7f
            yaw = 0.6f
            pitch = 0.3f
            initializedCamera = true
        }
    }

    fun setBookAtlas(id: String, atlas: BookAtlas) = post {
        atlases[id] = atlas
        bookTex.remove(id)?.let { GLES30.glDeleteTextures(1, intArrayOf(it), 0) }
    }

    fun setNight(value: Boolean) = post { night = value }

    fun orbit(dx: Float, dy: Float) = post {
        if (selected != null) return@post
        gYaw = (gYaw - dx * 0.0045f).coerceIn(-0.8f, 0.8f)
        gPitch = (gPitch + dy * 0.0035f).coerceIn(-0.2f, 0.65f)
    }

    fun pan(dx: Float, dy: Float) = post {
        if (selected != null) return@post
        val k = gDist * 0.0011f
        gTarget[0] += (dx * cos(gYaw)) * k
        gTarget[2] -= (dx * sin(gYaw)) * k * 0.3f
        gTarget[1] -= dy * k
        clampGoals()
    }

    fun zoom(factor: Float) = post {
        if (selected != null) return@post
        gDist = (gDist / factor).coerceIn(0.45f, 6.5f)
    }

    fun tap(x: Float, y: Float) = post {
        if (selected != null) { putBackNow(); return@post }
        val hit = pick(x, y) ?: return@post
        startPull(hit)
        callbacks.onBookTapped(hit.book.id)
    }

    fun doubleTap(x: Float, y: Float) = post {
        if (selected != null) return@post
        val l = layout ?: return@post
        val hit = pick(x, y)
        if (hit != null && gDist > 1.1f) {
            gTarget = floatArrayOf(hit.center[0], hit.center[1] + 0.04f, l.caseExtra[1])
            gDist = 0.8f
            gYaw = 0f
            gPitch = 0.04f
        } else {
            gTarget = l.homeTarget.copyOf()
            gDist = l.homeDistance
            gYaw = 0f
            gPitch = 0.08f
        }
    }

    fun pullOut(id: String) = post {
        if (selected != null) return@post
        layout?.books?.firstOrNull { it.book.id == id }?.let { startPull(it) }
    }

    fun putBack() = post { putBackNow() }

    fun home() = post {
        val l = layout ?: return@post
        gTarget = l.homeTarget.copyOf(); gDist = l.homeDistance; gYaw = 0f; gPitch = 0.08f
    }

    fun closeUp() = post {
        val l = layout ?: return@post
        gTarget = floatArrayOf(l.homeTarget[0] - 0.55f, l.caseParams[0] + l.caseParams[1] * 3.5f, l.caseExtra[1])
        gDist = 0.95f; gYaw = 0.28f; gPitch = 0.05f
    }

    // ---------------------------------------------------------------- GL

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        prog = Gl.program(Shaders.VERTEX, Shaders.FRAGMENT)
        depthProg = Gl.program(Shaders.DEPTH_VERTEX, Shaders.DEPTH_FRAGMENT)
        locs.clear(); depthLocs.clear()
        meshes = mapOf(
            MeshKind.BOX to Meshes.box(),
            MeshKind.CYL to Meshes.cylinder(),
            MeshKind.CONE to Meshes.cylinder(topRadius = 0.45f, caps = false),
            MeshKind.POT to Meshes.cylinder(topRadius = 1.3f),
            MeshKind.SPHERE to Meshes.sphere(),
        )
        anisotropy = (GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: "").contains("GL_EXT_texture_filter_anisotropic")
        // contesto nuovo: le texture vanno ricaricate dalle bitmap conservate
        themeTex.clear()
        bookTex.clear()
        pagesTex = upload(TextureGen.pages(7), repeat = true)
        whiteTex = upload(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(-1) }, repeat = true)
        createShadowMap()
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        lastNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = max(1, w); height = max(1, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        while (true) { val t = tasks.poll() ?: break; t() }
        val now = System.nanoTime()
        val dt = ((now - lastNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastNanos = now

        val l = layout
        val p = palette
        GLES30.glClearColor(0.02f, 0.015f, 0.01f, 1f)
        if (l == null || p == null) {
            GLES30.glViewport(0, 0, width, height)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
            return
        }
        var animating = updateCamera(dt)
        animating = updateSelection(dt) || animating
        if (fade < 1f) { fade = min(1f, fade + dt * 1.1f); animating = true }

        val uploads = ensureTextures(l)
        computeMatrices(l)
        shadowPass(l)
        mainPass(l, p)
        if (animating || uploads) callbacks.requestRender()
    }

    private fun updateCamera(dt: Float): Boolean {
        val k = 1f - Math.pow(0.0015, dt.toDouble()).toFloat()
        val before = abs(gYaw - yaw) + abs(gPitch - pitch) + abs(gDist - dist) +
            abs(gTarget[0] - target[0]) + abs(gTarget[1] - target[1]) + abs(gTarget[2] - target[2])
        yaw = M.lerp(yaw, gYaw, k); pitch = M.lerp(pitch, gPitch, k); dist = M.lerp(dist, gDist, k)
        target = M.lerp3(target, gTarget, k)
        return before > 0.0008f
    }

    private fun clampGoals() {
        val l = layout ?: return
        val minX = -1.2f
        val maxX = l.boundsMax[0] - 1.0f
        gTarget[0] = gTarget[0].coerceIn(minX, maxX)
        gTarget[1] = gTarget[1].coerceIn(0.25f, 2.1f)
        gTarget[2] = gTarget[2].coerceIn(0.1f, 1.2f)
    }

    private fun cameraQuat(y: Float, pch: Float) =
        Q.mul(Q.axisAngle(0f, 1f, 0f, Math.toDegrees(y.toDouble()).toFloat()), Q.axisAngle(1f, 0f, 0f, -Math.toDegrees(pch.toDouble()).toFloat()))

    private fun computeMatrices(l: SceneLayout) {
        eye = floatArrayOf(
            target[0] + dist * sin(yaw) * cos(pitch),
            target[1] + dist * sin(pitch),
            target[2] + dist * cos(yaw) * cos(pitch),
        )
        eye[1] = eye[1].coerceAtLeast(0.15f)
        val view = M.lookAt(eye, target)
        val proj = M.perspective(48f, width.toFloat() / height, 0.05f, 30f)
        viewProj = M.mul(proj, view)

        // luce principale: dalla finestra (giorno) o dalla luna (notte)
        val kd = keyDir()
        val cx = (l.cases.first()[0] + l.boundsMax[0] - 1.4f) / 2f
        val center = floatArrayOf(cx, 1.1f, 0.6f)
        val lightEye = floatArrayOf(center[0] - kd[0] * 6f, center[1] - kd[1] * 6f, center[2] - kd[2] * 6f)
        val lv = M.lookAt(lightEye, center)
        val xs = floatArrayOf(-1.9f, l.boundsMax[0] - 1.2f)
        val ys = floatArrayOf(-0.05f, 2.6f)
        val zs = floatArrayOf(-0.1f, 1.8f)
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (x in xs) for (y in ys) for (z in zs) {
            val q = M.point(lv, x, y, z)
            minX = min(minX, q[0]); maxX = max(maxX, q[0])
            minY = min(minY, q[1]); maxY = max(maxY, q[1])
            minZ = min(minZ, q[2]); maxZ = max(maxZ, q[2])
        }
        lightVP = M.mul(M.ortho(minX, maxX, minY, maxY, -maxZ - 0.5f, -minZ + 0.5f), lv)
    }

    private fun keyDir(): FloatArray {
        val d = if (night) floatArrayOf(0.35f, -0.62f, -0.7f) else floatArrayOf(0.62f, -0.55f, -0.56f)
        val n = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
        return floatArrayOf(d[0] / n, d[1] / n, d[2] / n)
    }

    // ---------------------------------------------------------------- selezione

    private fun startPull(b: BookDraw) {
        selected = b
        selT = 0f
        selDir = 1
        notifiedOut = false
        frozenEye = eye.copyOf()
        frozenQ = cameraQuat(yaw, pitch)
        frozenFwd = floatArrayOf(target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]).let {
            val n = sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]); floatArrayOf(it[0] / n, it[1] / n, it[2] / n)
        }
    }

    private fun putBackNow() {
        if (selected == null) return
        selDir = -1
    }

    private fun updateSelection(dt: Float): Boolean {
        val s = selected ?: return false
        if (selDir == 0) return false
        selT = (selT + selDir * dt / 0.85f).coerceIn(0f, 1f)
        if (selDir > 0 && selT >= 1f) {
            selDir = 0
            if (!notifiedOut) {
                notifiedOut = true
                computeMatrices(layout!!)
                callbacks.onPulledOut(s.book.id, coverRect(s))
            }
        } else if (selDir < 0 && selT <= 0f) {
            selected = null
            selDir = 0
            callbacks.onReturned()
        }
        return true
    }

    private fun selectedModel(s: BookDraw): FloatArray {
        val t = selT
        val c0 = s.center
        val slide = s.size[2] * 0.75f + 0.02f
        val c1 = floatArrayOf(c0[0], c0[1] + 0.006f, c0[2] + slide)
        if (t < 0.3f) {
            val u = M.smooth(t / 0.3f)
            return s.model(M.lerp3(c0, c1, u), s.rot)
        }
        val u = M.smooth((t - 0.3f) / 0.7f)
        val distance = 0.6f * (s.size[1] / 0.215f).coerceIn(0.8f, 1.3f)
        val cF = floatArrayOf(frozenEye[0] + frozenFwd[0] * distance, frozenEye[1] + frozenFwd[1] * distance, frozenEye[2] + frozenFwd[2] * distance)
        val qF = Q.mul(frozenQ, Q.axisAngle(0f, 1f, 0f, -90f))
        val c = M.lerp3(c1, cF, u)
        c[1] += sin(u * Math.PI.toFloat()) * 0.06f
        return s.model(c, Q.slerp(s.rot, qF, u))
    }

    private fun coverRect(s: BookDraw): RectF {
        val m = selectedModel(s)
        var l = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var t = Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (y in floatArrayOf(0f, 1f)) for (z in floatArrayOf(0f, 1f)) {
            val w = M.point(m, 1f, y, z)
            val clip = M.point(viewProj, w[0], w[1], w[2])
            val sx = (clip[0] * 0.5f + 0.5f) * width
            val sy = (1f - (clip[1] * 0.5f + 0.5f)) * height
            l = min(l, sx); r = max(r, sx); t = min(t, sy); b = max(b, sy)
        }
        return RectF(l, t, r, b)
    }

    private fun pick(x: Float, y: Float): BookDraw? {
        val l = layout ?: return null
        val inv = M.invert(viewProj)
        val nx = 2f * x / width - 1f
        val ny = 1f - 2f * y / height
        val near = M.point(inv, nx, ny, -1f)
        val far = M.point(inv, nx, ny, 1f)
        val dir = floatArrayOf(far[0] - near[0], far[1] - near[1], far[2] - near[2])
        var best: BookDraw? = null
        var bestT = Float.MAX_VALUE
        for (b in l.books) {
            val im = M.invert(b.model())
            val o = M.point(im, near[0], near[1], near[2])
            val d = M.dir(im, dir[0], dir[1], dir[2])
            val t = slab(o, d) ?: continue
            if (t < bestT) { bestT = t; best = b }
        }
        return best
    }

    private fun slab(o: FloatArray, d: FloatArray): Float? {
        var tmin = -Float.MAX_VALUE
        var tmax = Float.MAX_VALUE
        for (i in 0 until 3) {
            if (abs(d[i]) < 1e-9f) {
                if (o[i] < 0f || o[i] > 1f) return null
            } else {
                var t1 = (0f - o[i]) / d[i]
                var t2 = (1f - o[i]) / d[i]
                if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
                tmin = max(tmin, t1); tmax = min(tmax, t2)
                if (tmin > tmax) return null
            }
        }
        return if (tmax < 0f) null else max(tmin, 0f)
    }

    // ---------------------------------------------------------------- texture

    private fun upload(bmp: Bitmap, repeat: Boolean): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        val wrap = if (repeat) GLES30.GL_REPEAT else GLES30.GL_CLAMP_TO_EDGE
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        if (anisotropy) GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, 0x84FE, 4f)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        return ids[0]
    }

    /** Carica le texture mancanti, al massimo qualche decina per frame. Ritorna true se ne restano. */
    private fun ensureTextures(l: SceneLayout): Boolean {
        for ((k, bmp) in themeBitmaps) if (!themeTex.containsKey(k) && !bmp.isRecycled) themeTex[k] = upload(bmp, repeat = true)
        var budget = 24
        var pending = false
        for (b in l.books) {
            val id = b.book.id
            if (bookTex.containsKey(id)) continue
            val atlas = atlases[id] ?: continue
            if (budget-- <= 0) { pending = true; break }
            if (!atlas.bitmap.isRecycled) bookTex[id] = upload(atlas.bitmap, repeat = false)
        }
        return pending
    }

    private fun createShadowMap() {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        shadowTex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24, shadowSize, shadowSize, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_COMPARE_REF_TO_TEXTURE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_FUNC, GLES30.GL_LEQUAL)
        GLES30.glGenFramebuffers(1, ids, 0)
        shadowFbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, shadowTex, 0)
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
        GLES30.glReadBuffer(GLES30.GL_NONE)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    // ---------------------------------------------------------------- passate di disegno

    private fun loc(name: String) = locs.getOrPut(name) { GLES30.glGetUniformLocation(prog, name) }
    private fun dloc(name: String) = depthLocs.getOrPut(name) { GLES30.glGetUniformLocation(depthProg, name) }

    private fun shadowPass(l: SceneLayout) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
        GLES30.glViewport(0, 0, shadowSize, shadowSize)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(1.6f, 3f)
        GLES30.glUseProgram(depthProg)
        GLES30.glUniformMatrix4fv(dloc("uLightVP"), 1, false, lightVP, 0)
        for (d in l.props) {
            if (!d.mat.castShadow) continue
            GLES30.glUniformMatrix4fv(dloc("uModel"), 1, false, d.model, 0)
            meshes.getValue(d.mesh).draw()
        }
        val box = meshes.getValue(MeshKind.BOX)
        for (b in l.books) {
            val m = if (b === selected) selectedModel(b) else b.model()
            GLES30.glUniformMatrix4fv(dloc("uModel"), 1, false, m, 0)
            box.draw()
        }
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun mainPass(l: SceneLayout, p: RoomPalette) {
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(prog)
        GLES30.glUniformMatrix4fv(loc("uViewProj"), 1, false, viewProj, 0)
        GLES30.glUniformMatrix4fv(loc("uLightVP"), 1, false, lightVP, 0)
        GLES30.glUniform3f(loc("uCamPos"), eye[0], eye[1], eye[2])
        val kd = keyDir()
        GLES30.glUniform3f(loc("uKeyDir"), kd[0], kd[1], kd[2])
        if (night) {
            GLES30.glUniform3f(loc("uKeyColor"), 0.16f, 0.2f, 0.36f)
            GLES30.glUniform3f(loc("uSkyColor"), 0.05f, 0.055f, 0.09f)
            GLES30.glUniform3f(loc("uGroundColor"), 0.045f, 0.03f, 0.02f)
            GLES30.glUniform3fv(loc("uLampPos"), 1, l.deskLamp, 0)
            GLES30.glUniform3f(loc("uLampColor"), 1.5f, 1.0f, 0.5f)
            GLES30.glUniform3fv(loc("uLamp2Pos"), 1, l.floorLamp, 0)
            GLES30.glUniform3f(loc("uLamp2Color"), 2.6f, 1.75f, 0.9f)
            GLES30.glUniform1f(loc("uExposure"), 1.35f)
        } else {
            GLES30.glUniform3f(loc("uKeyColor"), 3.0f, 2.78f, 2.45f)
            GLES30.glUniform3f(loc("uSkyColor"), 0.5f, 0.55f, 0.66f)
            GLES30.glUniform3f(loc("uGroundColor"), 0.26f, 0.2f, 0.15f)
            GLES30.glUniform3fv(loc("uLampPos"), 1, l.floorLamp, 0)
            GLES30.glUniform3f(loc("uLampColor"), 0.0f, 0.0f, 0.0f)
            // luce di riempimento morbida dal lato della stanza
            GLES30.glUniform3f(loc("uLamp2Pos"), l.homeTarget[0] + 1.2f, 2.2f, 2.6f)
            GLES30.glUniform3f(loc("uLamp2Color"), 1.6f, 1.5f, 1.4f)
            GLES30.glUniform1f(loc("uExposure"), 1.0f)
        }
        val cases = FloatArray(16)
        for ((i, c) in l.cases.take(4).withIndex()) { cases[i * 4] = c[0]; cases[i * 4 + 1] = c[1] }
        GLES30.glUniform4fv(loc("uCases"), 4, cases, 0)
        GLES30.glUniform1i(loc("uCaseCount"), min(4, l.cases.size))
        GLES30.glUniform4f(loc("uCaseParams"), l.caseParams[0], l.caseParams[1], l.caseParams[2], l.caseParams[3])
        GLES30.glUniform4f(loc("uCaseExtra"), l.caseExtra[0], l.caseExtra[1], l.caseExtra[2], 0f)
        GLES30.glUniform2f(loc("uResolution"), width.toFloat(), height.toFloat())
        GLES30.glUniform1f(loc("uFade"), M.smooth(fade))
        GLES30.glUniform1i(loc("uTex"), 0)
        GLES30.glUniform1i(loc("uPages"), 1)
        GLES30.glUniform1i(loc("uShadow"), 2)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, pagesTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        val dimOthers = if (selected != null) M.lerp(1f, 0.32f, M.smooth(selT)) else 1f
        GLES30.glUniform1f(loc("uDim"), dimOthers)
        GLES30.glUniform1i(loc("uHasTex"), 1)

        for (d in l.props) {
            val m = d.mat
            setModel(d.model)
            var tint = m.tint
            if (m.mode == 2) {
                tint = when (m.glow) {
                    1 -> if (night) m.tint else floatArrayOf(m.tint[0] * 0.12f, m.tint[1] * 0.12f, m.tint[2] * 0.12f)
                    2 -> if (night) floatArrayOf(0.025f, 0.035f, 0.09f) else m.tint
                    3 -> if (night) m.tint else floatArrayOf(0f, 0f, 0f)
                    else -> m.tint
                }
                if (m.glow == 3 && !night) continue
            }
            GLES30.glUniform1i(loc("uMode"), m.mode)
            GLES30.glUniform3f(loc("uTint"), tint[0], tint[1], tint[2])
            GLES30.glUniform1f(loc("uTexScale"), m.texScale)
            GLES30.glUniform1i(loc("uGrainVertical"), if (m.grainVertical) 1 else 0)
            GLES30.glUniform1f(loc("uSpec"), m.spec)
            GLES30.glUniform1f(loc("uShininess"), m.shininess)
            val tex = m.tex?.let { themeTex[it] } ?: whiteTex
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
            meshes.getValue(d.mesh).draw()
        }

        val box = meshes.getValue(MeshKind.BOX)
        GLES30.glUniform1i(loc("uMode"), 1)
        for (b in l.books) {
            val isSel = b === selected
            GLES30.glUniform1f(loc("uDim"), if (isSel) 1f else dimOthers)
            setModel(if (isSel) selectedModel(b) else b.model())
            val atlas = atlases[b.book.id]
            val tex = bookTex[b.book.id]
            if (atlas != null && tex != null) {
                GLES30.glUniform1i(loc("uHasTex"), 1)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
                GLES30.glUniform4fv(loc("uSpineRect"), 1, atlas.spine, 0)
                GLES30.glUniform4fv(loc("uFrontRect"), 1, atlas.front, 0)
                GLES30.glUniform4fv(loc("uBackRect"), 1, atlas.back, 0)
            } else {
                GLES30.glUniform1i(loc("uHasTex"), 0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, whiteTex)
                val c = Gl.srgbToLinear(b.book.spineColor)
                GLES30.glUniform3f(loc("uTint"), c[0], c[1], c[2])
            }
            GLES30.glUniform3f(loc("uBookDims"), b.size[0], b.size[1], b.size[2])
            GLES30.glUniform1f(loc("uSpec"), if (isSel) 0.35f else 0.16f)
            GLES30.glUniform1f(loc("uShininess"), 36f)
            box.draw()
        }
    }

    private fun setModel(m: FloatArray) {
        GLES30.glUniformMatrix4fv(loc("uModel"), 1, false, m, 0)
        GLES30.glUniformMatrix4fv(loc("uNormalMat"), 1, false, M.normalMatrix(m), 0)
    }
}
