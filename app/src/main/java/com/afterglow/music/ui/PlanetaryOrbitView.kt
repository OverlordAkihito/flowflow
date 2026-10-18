package com.afterglow.music.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.InputDevice
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import com.afterglow.music.data.Track
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A small OpenGL ES 2 scene: a lit procedural Earth, real 3D orbital paths, and artwork billboards. */
internal class PlanetaryOrbitView(
    context: Context,
    private val onTrackTap: (Track) -> Unit,
    private val onPlanetTap: () -> Unit,
    private val onUnavailable: () -> Unit,
    private val onSceneReady: () -> Unit,
) : GLSurfaceView(context) {
    private val orbitRenderer = PlanetaryOrbitRenderer(context.applicationContext, ::postTrackTap, ::postPlanetTap, onUnavailable, ::postSceneReady) { requestRender() }
    private val uiHandler = Handler(Looper.getMainLooper())
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastTapAt = 0L
    private var dragged = false
    private var pinchDistance = 0f
    private var pinchCenterX = 0f
    private var pinchCenterY = 0f
    private var lastSceneTracks: List<Track> = emptyList()
    private var lastCurrentKey: String? = null
    private var lastPlaying = false
    private var lastReducedMotion = false
    private var hasSceneState = false

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(orbitRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        isClickable = true
        contentDescription = "Interactive three dimensional Earth music orbit. Drag to rotate, pinch to zoom, and tap a satellite to play its track."
    }

    private fun postTrackTap(track: Track) = uiHandler.post { onTrackTap(track) }
    private fun postPlanetTap() = uiHandler.post { onPlanetTap() }
    private fun postSceneReady() = uiHandler.post { onSceneReady() }

    fun updateScene(tracks: List<Track>, currentKey: String?, isPlaying: Boolean, reducedMotion: Boolean) {
        val changed = !hasSceneState || lastSceneTracks !== tracks || lastCurrentKey != currentKey || lastPlaying != isPlaying || lastReducedMotion != reducedMotion
        if (!changed) return
        hasSceneState = true
        lastSceneTracks = tracks
        lastCurrentKey = currentKey
        lastPlaying = isPlaying
        lastReducedMotion = reducedMotion
        orbitRenderer.updateScene(tracks, currentKey, isPlaying, reducedMotion)
        val wanted = if (reducedMotion) RENDERMODE_WHEN_DIRTY else RENDERMODE_CONTINUOUSLY
        if (renderMode != wanted) renderMode = wanted
        requestRender()
    }

    override fun onResume() {
        super.onResume()
        requestRender()
    }

    fun resetCamera() {
        orbitRenderer.resetCamera()
        requestRender()
    }

    fun zoomBy(factor: Float) {
        orbitRenderer.zoomBy(factor)
        requestRender()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                lastX = event.x; lastY = event.y
                dragged = false
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) {
                pinchDistance = pointerDistance(event)
                val center = pointerCenter(event)
                pinchCenterX = center.first; pinchCenterY = center.second
                dragged = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val distance = pointerDistance(event)
                    val center = pointerCenter(event)
                    if (pinchDistance > 0f && distance > 0f) orbitRenderer.zoomBy(pinchDistance / distance)
                    if (pinchDistance > 0f) orbitRenderer.panBy(center.first - pinchCenterX, center.second - pinchCenterY)
                    pinchDistance = distance
                    pinchCenterX = center.first; pinchCenterY = center.second
                    dragged = true
                } else {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (abs(event.x - downX) + abs(event.y - downY) > 9f) dragged = true
                    orbitRenderer.rotateCamera(dx, dy)
                    lastX = event.x; lastY = event.y
                }
                requestRender()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                pinchDistance = 0f
                return true
            }
            MotionEvent.ACTION_UP -> {
                pinchDistance = 0f
                if (!dragged) {
                    val now = SystemClock.uptimeMillis()
                    val hit = orbitRenderer.pick(event.x, event.y)
                    if (!hit) {
                        if (now - lastTapAt < 310L) resetCamera() else lastTapAt = now
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> { pinchDistance = 0f; return true }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        postPlanetTap()
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            val wheel = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (wheel != 0f) zoomBy(if (wheel > 0f) .90f else 1.11f)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onDetachedFromWindow() {
        orbitRenderer.close()
        super.onDetachedFromWindow()
    }

    private fun pointerDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    private fun pointerCenter(event: MotionEvent): Pair<Float, Float> =
        ((event.getX(0) + event.getX(1)) / 2f) to ((event.getY(0) + event.getY(1)) / 2f)
}

private class PlanetaryOrbitRenderer(
    context: Context,
    private val onTrackTap: (Track) -> Unit,
    private val onPlanetTap: () -> Unit,
    private val onUnavailable: () -> Unit,
    private val onSceneReady: () -> Unit,
    private val requestFrameCallback: () -> Unit,
) : GLSurfaceView.Renderer {
    private val appContext = context
    private val artworkExecutor = Executors.newSingleThreadExecutor { task -> Thread(task, "orbit-artwork").apply { isDaemon = true } }
    private val atlasGeneration = AtomicInteger(0)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val satelliteWorld = FloatArray(4)
    private val satelliteView = FloatArray(4)
    private val satelliteClip = FloatArray(4)
    private val earthViewCenter = FloatArray(4)
    private val cardVertexBuffer = floatBuffer(FloatArray(MAX_ORBIT_TRACKS * 4 * 6))
    private val cardIndexBuffer = shortBuffer(ShortArray(MAX_ORBIT_TRACKS * 6) { index ->
        val quadVertices = shortArrayOf(0, 1, 2, 0, 2, 3)
        (index / 6 * 4 + quadVertices[index % 6]).toShort()
    })
    private val projectedCards = mutableListOf<ProjectedCard>()
    private val stateLock = Any()
    @Volatile private var currentKey: String? = null
    @Volatile private var playing = false
    @Volatile private var reducedMotion = false
    @Volatile private var pendingScene: PendingScene? = null
    @Volatile private var disposed = false
    @Volatile private var cameraYaw = 0f
    @Volatile private var cameraPitch = .14f
    @Volatile private var cameraDistance = 8.6f
    @Volatile private var panX = 0f
    @Volatile private var panY = 0f
    @Volatile private var targetYaw = 0f
    @Volatile private var targetPitch = .14f
    @Volatile private var targetDistance = 8.6f
    @Volatile private var targetPanX = 0f
    @Volatile private var targetPanY = 0f
    private var width = 1
    private var height = 1
    private var earthProgram = 0
    private var atmosphereProgram = 0
    private var lineProgram = 0
    private var starProgram = 0
    private var cardProgram = 0
    private var earthVertices: FloatBuffer? = null
    private var earthIndices: ShortBuffer? = null
    private var earthIndexCount = 0
    private var starVertices: FloatBuffer? = null
    private var starCount = 0
    private var orbitBuffers: List<FloatBuffer> = emptyList()
    private var atlasTexture = 0
    @Volatile private var activeTracks: List<Track> = emptyList()
    private var requestedTracks: List<Track> = emptyList()
    private var requestedStart = 0
    private var sceneTime = 0f
    private var previousFrameNanos = 0L
    @Volatile private var earthCenterX = 0f
    @Volatile private var earthCenterY = 0f
    @Volatile private var earthRadiusPixels = 0f
    private var surfaceReady = false
    private val errorReported = AtomicBoolean(false)
    private var firstSceneReady = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        runCatching {
            GLES20.glClearColor(.008f, .014f, .031f, 1f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glDepthFunc(GLES20.GL_LEQUAL)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            earthProgram = createProgram(EARTH_VERTEX, EARTH_FRAGMENT)
            atmosphereProgram = createProgram(ATMOSPHERE_VERTEX, ATMOSPHERE_FRAGMENT)
            lineProgram = createProgram(LINE_VERTEX, LINE_FRAGMENT)
            starProgram = createProgram(STAR_VERTEX, STAR_FRAGMENT)
            cardProgram = createProgram(CARD_VERTEX, CARD_FRAGMENT)
            createEarthMesh()
            createStarField()
            createOrbitPaths()
            surfaceReady = true
            consumePendingScene()
        }.onFailure { reportUnavailable() }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = max(1, width)
        this.height = max(1, height)
        GLES20.glViewport(0, 0, this.width, this.height)
        Matrix.perspectiveM(projectionMatrix, 0, 44f, this.width.toFloat() / this.height, .1f, 60f)
        requestFrame()
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!surfaceReady || disposed) return
        runCatching {
            consumePendingScene()
            val now = System.nanoTime()
            val delta = if (previousFrameNanos == 0L) 0f else ((now - previousFrameNanos) / 1_000_000_000f).coerceIn(0f, .05f)
            previousFrameNanos = now
            val follow = if (reducedMotion) 1f else 1f - kotlin.math.exp(-delta * 7f)
            cameraYaw += shortestAngle(targetYaw - cameraYaw) * follow
            cameraPitch += (targetPitch - cameraPitch) * follow
            cameraDistance += (targetDistance - cameraDistance) * follow
            panX += (targetPanX - panX) * follow
            panY += (targetPanY - panY) * follow
            if (!reducedMotion) sceneTime += delta * if (playing) 1f else .27f

            val cosPitch = cos(cameraPitch)
            val eyeX = panX + sin(cameraYaw) * cosPitch * cameraDistance
            val eyeY = panY + sin(cameraPitch) * cameraDistance
            val eyeZ = cos(cameraYaw) * cosPitch * cameraDistance
            Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, panX, panY, 0f, 0f, 1f, 0f)
            Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            drawStars()
            drawOrbitPaths()
            drawEarth()
            drawSatellites()
        }.onFailure { reportUnavailable() }
    }

    fun updateScene(tracks: List<Track>, selectedKey: String?, isPlaying: Boolean, reduceMotion: Boolean) {
        currentKey = selectedKey
        playing = isPlaying
        reducedMotion = reduceMotion
        val selectedIndex = tracks.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
        val window = synchronized(stateLock) {
            val previous = pendingScene?.tracks ?: requestedTracks.ifEmpty { activeTracks }
            val previousStart = pendingScene?.start ?: requestedStart
            val previousEnd = previousStart + previous.size
            val start = when {
                tracks.size <= MAX_ORBIT_TRACKS -> 0
                selectedIndex in previousStart until previousEnd -> previousStart
                else -> (selectedIndex - MAX_ORBIT_TRACKS / 2).coerceIn(0, tracks.size - MAX_ORBIT_TRACKS)
            }
            val windowTracks = tracks.drop(start).take(MAX_ORBIT_TRACKS)
            requestedTracks = windowTracks
            requestedStart = start
            Triple(start, windowTracks, previous)
        }
        val start = window.first
        val shownTracks = window.second
        val priorTracks = window.third
        if (shownTracks == priorTracks) {
            requestFrame()
            return
        }
        val request = atlasGeneration.incrementAndGet()
        artworkExecutor.execute {
            if (disposed || request != atlasGeneration.get()) return@execute
            val bitmap = runCatching { createArtworkAtlas(shownTracks) }.getOrElse {
                reportUnavailable()
                null
            }
            if (bitmap == null) return@execute
            if (disposed || request != atlasGeneration.get()) {
                bitmap?.recycle()
                return@execute
            }
            synchronized(stateLock) { pendingScene = PendingScene(start, shownTracks, bitmap, request) }
            requestFrame()
        }
    }

    fun rotateCamera(dx: Float, dy: Float) {
        targetYaw = wrapAngle(targetYaw - dx * .006f)
        targetPitch = (targetPitch + dy * .005f).coerceIn(-1.18f, 1.18f)
    }

    fun zoomBy(factor: Float) {
        if (factor.isFinite() && factor > 0f) targetDistance = (targetDistance * factor).coerceIn(4.2f, 15f)
    }

    fun panBy(dx: Float, dy: Float) {
        val scale = targetDistance * .0017f
        targetPanX = (targetPanX - dx * scale).coerceIn(-4f, 4f)
        targetPanY = (targetPanY + dy * scale).coerceIn(-4f, 4f)
    }

    fun resetCamera() {
        targetYaw = 0f
        targetPitch = .14f
        targetDistance = 8.6f
        targetPanX = 0f
        targetPanY = 0f
    }

    fun pick(x: Float, y: Float): Boolean {
        val snapshot = synchronized(projectedCards) { projectedCards.toList() }
        val nearest = snapshot.asSequence()
            .filter { it.visible }
            .map { it to ((it.x - x) * (it.x - x) + (it.y - y) * (it.y - y)) }
            .filter { (card, distance) -> distance < card.radius * card.radius }
            .minByOrNull { it.second }
        if (nearest != null) {
            onTrackTap(nearest.first.track)
            return true
        }
        val dx = x - earthCenterX
        val dy = y - earthCenterY
        if (dx * dx + dy * dy < earthRadiusPixels * earthRadiusPixels * 1.15f) {
            onPlanetTap()
            return true
        }
        return false
    }

    fun close() {
        disposed = true
        atlasGeneration.incrementAndGet()
        synchronized(stateLock) {
            pendingScene?.bitmap?.takeIf { !it.isRecycled }?.recycle()
            pendingScene = null
        }
        artworkExecutor.shutdownNow()
    }

    private fun consumePendingScene() {
        val pending = synchronized(stateLock) { pendingScene.also { pendingScene = null } } ?: return
        if (pending.generation != atlasGeneration.get()) {
            pending.bitmap?.recycle()
            return
        }
        pending.bitmap?.let { bitmap ->
            uploadAtlas(bitmap)
            bitmap.recycle()
        }
        activeTracks = pending.tracks
        if (!firstSceneReady) {
            firstSceneReady = true
            onSceneReady()
        }
    }

    private fun uploadAtlas(bitmap: Bitmap) {
        if (atlasTexture != 0) GLES20.glDeleteTextures(1, intArrayOf(atlasTexture), 0)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        atlasTexture = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTexture)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun drawEarth() {
        drawAtmosphere()
        val program = earthProgram
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMvp"), 1, false, vpMatrix, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSpin"), sceneTime * .075f)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTime"), sceneTime)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, earthVertices)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, earthIndexCount, GLES20.GL_UNSIGNED_SHORT, earthIndices)
        GLES20.glDisableVertexAttribArray(position)
        val clip = FloatArray(4)
        Matrix.multiplyMV(earthViewCenter, 0, viewMatrix, 0, floatArrayOf(0f, 0f, 0f, 1f), 0)
        Matrix.multiplyMV(clip, 0, projectionMatrix, 0, earthViewCenter, 0)
        if (clip[3] > 0f) {
            earthCenterX = (clip[0] / clip[3] * .5f + .5f) * width
            earthCenterY = (1f - (clip[1] / clip[3] * .5f + .5f)) * height
            val edge = FloatArray(4)
            val worldEdge = floatArrayOf(1f, 0f, 0f, 1f)
            Matrix.multiplyMV(edge, 0, vpMatrix, 0, worldEdge, 0)
            earthRadiusPixels = abs(edge[0] / edge[3] - clip[0] / clip[3]) * width * .5f
        }
    }

    private fun drawAtmosphere() {
        val program = atmosphereProgram
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMvp"), 1, false, vpMatrix, 0)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uView"), 1, false, viewMatrix, 0)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_FRONT)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, earthVertices)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, earthIndexCount, GLES20.GL_UNSIGNED_SHORT, earthIndices)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun drawSatellites() {
        val tracks = activeTracks
        if (tracks.isEmpty() || atlasTexture == 0) {
            synchronized(projectedCards) { projectedCards.clear() }
            return
        }
        cardVertexBuffer.clear()
        cardIndexBuffer.position(0)
        val projected = ArrayList<ProjectedCard>(tracks.size)
        tracks.forEachIndexed { slot, track ->
            val orbit = orbitFor(track)
            val angle = orbit.phase + sceneTime * orbit.speed * if (playing) 1f else .27f
            val radial = orbit.radius * (1f - orbit.eccentricity * cos(angle))
            val cosA = cos(angle); val sinA = sin(angle)
            val worldX = radial * cosA
            val planeY = radial * sinA
            val worldY = planeY * sin(orbit.inclination)
            val worldZ = -planeY * cos(orbit.inclination)
            satelliteWorld[0] = worldX; satelliteWorld[1] = worldY; satelliteWorld[2] = worldZ; satelliteWorld[3] = 1f
            Matrix.multiplyMV(satelliteView, 0, viewMatrix, 0, satelliteWorld, 0)
            Matrix.multiplyMV(satelliteClip, 0, projectionMatrix, 0, satelliteView, 0)
            val isCurrent = track.key == currentKey
            val cardScale = if (isCurrent) 1.13f + if (playing && !reducedMotion) .035f * sin(sceneTime * 2.4f) else 0f else .78f
            val halfW = .55f * cardScale
            val halfH = .28f * cardScale
            val screenX = if (satelliteClip[3] > 0f) (satelliteClip[0] / satelliteClip[3] * .5f + .5f) * width else -1000f
            val screenY = if (satelliteClip[3] > 0f) (1f - (satelliteClip[1] / satelliteClip[3] * .5f + .5f)) * height else -1000f
            val radius = max(18f, width * .055f * cardScale)
            val onScreen = satelliteClip[3] > 0f && screenX in 0f..width.toFloat() && screenY in 0f..height.toFloat()
            projected += ProjectedCard(track, screenX, screenY, radius, onScreen && !isBehindEarth(satelliteView))

            val column = slot % ATLAS_COLUMNS
            val row = slot / ATLAS_COLUMNS
            val insetU = .5f / ATLAS_BITMAP_WIDTH
            val insetV = .5f / ATLAS_BITMAP_HEIGHT
            val u0 = column.toFloat() / ATLAS_COLUMNS + insetU
            val u1 = (column + 1f) / ATLAS_COLUMNS - insetU
            val v0 = row.toFloat() / ATLAS_ROWS + insetV
            val v1 = (row + 1f) / ATLAS_ROWS - insetV
            val selected = if (isCurrent) 1f else 0f
            putCardVertex(-halfW, -halfH, u0, v1, selected)
            putCardVertex(halfW, -halfH, u1, v1, selected)
            putCardVertex(halfW, halfH, u1, v0, selected)
            putCardVertex(-halfW, halfH, u0, v0, selected)
        }
        synchronized(projectedCards) { projectedCards.clear(); projectedCards.addAll(projected) }

        cardVertexBuffer.position(0)
        cardIndexBuffer.position(0)
        val program = cardProgram
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uProjection"), 1, false, projectionMatrix, 0)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uAtlas"), 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTexture)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val uv = GLES20.glGetAttribLocation(program, "aUv")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 6 * 4, cardVertexBuffer)
        cardVertexBuffer.position(3)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 6 * 4, cardVertexBuffer)
        val selectedAttribute = GLES20.glGetAttribLocation(program, "aSelected")
        GLES20.glEnableVertexAttribArray(selectedAttribute)
        cardVertexBuffer.position(5)
        GLES20.glVertexAttribPointer(selectedAttribute, 1, GLES20.GL_FLOAT, false, 6 * 4, cardVertexBuffer)
        cardIndexBuffer.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, tracks.size * 6, GLES20.GL_UNSIGNED_SHORT, cardIndexBuffer)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(uv)
        GLES20.glDisableVertexAttribArray(selectedAttribute)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun drawOrbitPaths() {
        val program = lineProgram
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMvp"), 1, false, vpMatrix, 0)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glDepthMask(false)
        orbitBuffers.forEachIndexed { index, buffer ->
            val color = if (index % 3 == 0) floatArrayOf(.28f, .85f, 1f, .12f) else floatArrayOf(.24f, .62f, .9f, .08f)
            GLES20.glUniform4fv(GLES20.glGetUniformLocation(program, "uColor"), 1, color, 0)
            GLES20.glLineWidth(1f)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, buffer)
            GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, ORBIT_SEGMENTS + 1)
            GLES20.glUniform4fv(GLES20.glGetUniformLocation(program, "uColor"), 1, color.copyOf().also { it[3] = .36f }, 0)
            GLES20.glLineWidth(1f)
            buffer.position(0)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, buffer)
            GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, ORBIT_SEGMENTS + 1)
            buffer.position(0)
        }
        GLES20.glDepthMask(true)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun putCardVertex(x: Float, y: Float, u: Float, v: Float, selected: Float) {
        cardVertexBuffer.put(satelliteView[0] + x)
        cardVertexBuffer.put(satelliteView[1] + y)
        cardVertexBuffer.put(satelliteView[2])
        cardVertexBuffer.put(u)
        cardVertexBuffer.put(v)
        cardVertexBuffer.put(selected)
    }

    private fun isBehindEarth(point: FloatArray): Boolean {
        val lengthSquared = point[0] * point[0] + point[1] * point[1] + point[2] * point[2]
        if (lengthSquared <= 0f) return false
        val projection = (earthViewCenter[0] * point[0] + earthViewCenter[1] * point[1] + earthViewCenter[2] * point[2]) / lengthSquared
        if (projection <= 0f || projection >= 1f) return false
        val dx = earthViewCenter[0] - projection * point[0]
        val dy = earthViewCenter[1] - projection * point[1]
        val dz = earthViewCenter[2] - projection * point[2]
        return dx * dx + dy * dy + dz * dz < 1.04f
    }

    private fun drawStars() {
        val program = starProgram
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMvp"), 1, false, vpMatrix, 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTime"), if (reducedMotion) 0f else sceneTime)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val size = GLES20.glGetAttribLocation(program, "aSize")
        starVertices?.let { buffer ->
            buffer.position(0)
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 4 * 4, buffer)
            buffer.position(3)
            GLES20.glEnableVertexAttribArray(size)
            GLES20.glVertexAttribPointer(size, 1, GLES20.GL_FLOAT, false, 4 * 4, buffer)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, starCount)
            GLES20.glDisableVertexAttribArray(position)
            GLES20.glDisableVertexAttribArray(size)
        }
    }

    private fun createEarthMesh() {
        val latitude = 42
        val longitude = 64
        val vertices = FloatArray((latitude + 1) * (longitude + 1) * 3)
        var at = 0
        for (y in 0..latitude) {
            val v = y.toFloat() / latitude
            val phi = v * PI.toFloat()
            for (x in 0..longitude) {
                val u = x.toFloat() / longitude
                val theta = u * PI.toFloat() * 2f
                vertices[at++] = -sin(phi) * cos(theta)
                vertices[at++] = cos(phi)
                vertices[at++] = sin(phi) * sin(theta)
            }
        }
        val indices = ShortArray(latitude * longitude * 6)
        at = 0
        for (y in 0 until latitude) for (x in 0 until longitude) {
            val topLeft = y * (longitude + 1) + x
            val bottomLeft = (y + 1) * (longitude + 1) + x
            indices[at++] = topLeft.toShort(); indices[at++] = bottomLeft.toShort(); indices[at++] = (topLeft + 1).toShort()
            indices[at++] = (topLeft + 1).toShort(); indices[at++] = bottomLeft.toShort(); indices[at++] = (bottomLeft + 1).toShort()
        }
        earthVertices = floatBuffer(vertices)
        earthIndices = shortBuffer(indices)
        earthIndexCount = indices.size
    }

    private fun createStarField() {
        val random = java.util.Random(904_2026L)
        val points = FloatArray(STAR_COUNT * 4)
        for (index in 0 until STAR_COUNT) {
            val theta = random.nextFloat() * 2f * PI.toFloat()
            val z = random.nextFloat() * 2f - 1f
            val ring = sqrt(1f - z * z)
            points[index * 4] = cos(theta) * ring * 25f
            points[index * 4 + 1] = z * 25f
            points[index * 4 + 2] = sin(theta) * ring * 25f
            points[index * 4 + 3] = if (index % 11 == 0) 3.4f else 1.4f + random.nextFloat() * 1.2f
        }
        starVertices = floatBuffer(points)
        starCount = STAR_COUNT
    }

    private fun createOrbitPaths() {
        orbitBuffers = (0 until ORBIT_COUNT).map { orbitIndex ->
            val vertices = FloatArray((ORBIT_SEGMENTS + 1) * 3)
            val radius = 1.55f + (orbitIndex % 4) * .40f
            val inclination = (-.63f + orbitIndex * .18f)
            for (step in 0..ORBIT_SEGMENTS) {
                val angle = step.toFloat() / ORBIT_SEGMENTS * 2f * PI.toFloat()
                val radial = radius * (1f - .07f * cos(angle + orbitIndex * 1.3f))
                vertices[step * 3] = radial * cos(angle)
                vertices[step * 3 + 1] = radial * sin(angle) * sin(inclination)
                vertices[step * 3 + 2] = -radial * sin(angle) * cos(inclination)
            }
            floatBuffer(vertices)
        }
    }

    private fun createArtworkAtlas(tracks: List<Track>): Bitmap {
        val atlas = Bitmap.createBitmap(ATLAS_BITMAP_WIDTH, ATLAS_BITMAP_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(atlas)
        canvas.drawColor(Color.TRANSPARENT)
        tracks.forEachIndexed { index, track ->
            val left = (index % ATLAS_COLUMNS) * CARD_WIDTH
            val top = (index / ATLAS_COLUMNS) * CARD_HEIGHT
            drawTrackCard(canvas, left, top, track)
        }
        return atlas
    }

    private fun drawTrackCard(canvas: Canvas, left: Int, top: Int, track: Track) {
        val rect = RectF(left + 1f, top + 1f, left + CARD_WIDTH - 1f, top + CARD_HEIGHT - 1f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, intArrayOf(0xFF152B3C.toInt(), 0xFF101522.toInt(), 0xFF1A2630.toInt()), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(rect, 20f, 20f, cardPaint)
        cardPaint.shader = null
        cardPaint.style = Paint.Style.STROKE
        cardPaint.strokeWidth = 2f
        cardPaint.color = 0x997BE5FF.toInt()
        canvas.drawRoundRect(rect, 20f, 20f, cardPaint)

        val artwork = loadArtwork(track)
        val artRect = RectF(left + 14f, top + 14f, left + 110f, top + 110f)
        val clip = Path().apply { addRoundRect(artRect, 14f, 14f, Path.Direction.CW) }
        val save = canvas.save()
        canvas.clipPath(clip)
        if (artwork != null && !artwork.isRecycled) {
            canvas.drawBitmap(artwork, null, artRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            artwork.recycle()
        } else {
            val fallback = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(artRect.left, artRect.top, artRect.right, artRect.bottom, 0xFF156A7B.toInt(), 0xFF552C70.toInt(), Shader.TileMode.CLAMP) }
            canvas.drawRect(artRect, fallback)
            fallback.shader = null
            fallback.color = Color.WHITE
            fallback.textSize = 52f
            fallback.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            canvas.drawText("♪", artRect.left + 27f, artRect.top + 67f, fallback)
        }
        canvas.restoreToCount(save)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 24f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD) }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC3D1DD.toInt(); textSize = 18f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val title = TextUtils.ellipsize(track.safeTitle, titlePaint, 126f, TextUtils.TruncateAt.END).toString()
        val artist = TextUtils.ellipsize(track.safeArtist, artistPaint, 126f, TextUtils.TruncateAt.END).toString()
        canvas.drawText(title, left + 122f, top + 52f, titlePaint)
        canvas.drawText(artist, left + 122f, top + 82f, artistPaint)
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF86E8E4.toInt(); textSize = 13f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        canvas.drawText("MUSIC SATELLITE", left + 122f, top + 105f, tagPaint)
    }

    private fun loadArtwork(track: Track): Bitmap? = runCatching {
        val uri = Uri.parse("content://media/external/audio/albumart/${track.albumId}")
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        val sample = max(1, min(bounds.outWidth, bounds.outHeight) / 180)
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtMost(8); inPreferredConfig = Bitmap.Config.RGB_565 }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()

    private fun orbitFor(track: Track): Orbit {
        val hash = (track.key.hashCode() xor (track.id * 31L).toInt()) and 0x7fffffff
        val orbit = hash % ORBIT_COUNT
        val angle = ((hash % 10_000) / 10_000f) * 2f * PI.toFloat()
        val radius = 1.52f + (orbit % 4) * .39f + ((hash / 31) % 13) * .012f
        val inclination = -.68f + ((hash / 131) % 136) / 100f
        val speed = .12f + ((hash / 17) % 17) * .006f
        val eccentricity = .035f + ((hash / 43) % 9) * .009f
        return Orbit(angle, radius, inclination, speed, eccentricity)
    }

    private fun requestFrame() = requestFrameCallback.invoke()

    private fun reportUnavailable() {
        if (!errorReported.compareAndSet(false, true)) return
        android.os.Handler(Looper.getMainLooper()).post { onUnavailable() }
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
    private fun shortBuffer(values: ShortArray): ShortBuffer = ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply { put(values); position(0) }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) error("OpenGL program link failed: ${GLES20.glGetProgramInfoLog(program)}")
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) error("OpenGL shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
        return shader
    }

    private data class PendingScene(val start: Int, val tracks: List<Track>, val bitmap: Bitmap?, val generation: Int)
    private data class ProjectedCard(val track: Track, val x: Float, val y: Float, val radius: Float, val visible: Boolean)
    private data class Orbit(val phase: Float, val radius: Float, val inclination: Float, val speed: Float, val eccentricity: Float)

    private companion object {
        const val MAX_ORBIT_TRACKS = 24
        const val ATLAS_COLUMNS = 6
        const val ATLAS_ROWS = 4
        const val CARD_WIDTH = 256
        const val CARD_HEIGHT = 128
        const val ATLAS_BITMAP_WIDTH = CARD_WIDTH * ATLAS_COLUMNS
        const val ATLAS_BITMAP_HEIGHT = CARD_HEIGHT * ATLAS_ROWS
        const val ORBIT_COUNT = 8
        const val ORBIT_SEGMENTS = 96
        const val STAR_COUNT = 140

        fun wrapAngle(angle: Float): Float {
            var value = angle
            while (value > PI.toFloat()) value -= (2f * PI.toFloat())
            while (value < -PI.toFloat()) value += (2f * PI.toFloat())
            return value
        }

        fun shortestAngle(value: Float): Float = wrapAngle(value)

        const val EARTH_VERTEX = """
            uniform mat4 uMvp;
            attribute vec3 aPosition;
            varying vec3 vSphere;
            void main() {
                vSphere = aPosition;
                gl_Position = uMvp * vec4(aPosition, 1.0);
            }
        """

        const val EARTH_FRAGMENT = """
            precision mediump float;
            varying vec3 vSphere;
            uniform float uSpin;
            uniform float uTime;
            float hash3(vec3 p) {
                p = fract(p * 0.3183099 + vec3(0.1));
                p *= 17.0;
                return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
            }
            float noise3(vec3 x) {
                vec3 i = floor(x);
                vec3 f = fract(x);
                f = f * f * (3.0 - 2.0 * f);
                float a = hash3(i);
                float b = hash3(i + vec3(1.0, 0.0, 0.0));
                float c = hash3(i + vec3(0.0, 1.0, 0.0));
                float d = hash3(i + vec3(1.0, 1.0, 0.0));
                float e = hash3(i + vec3(0.0, 0.0, 1.0));
                float f1 = hash3(i + vec3(1.0, 0.0, 1.0));
                float g = hash3(i + vec3(0.0, 1.0, 1.0));
                float h = hash3(i + vec3(1.0, 1.0, 1.0));
                return mix(mix(mix(a,b,f.x), mix(c,d,f.x), f.y), mix(mix(e,f1,f.x), mix(g,h,f.x), f.y), f.z);
            }
            float fbm(vec3 p) {
                float value = 0.0;
                float amplitude = 0.5;
                for (int octave = 0; octave < 4; octave++) {
                    value += noise3(p) * amplitude;
                    p *= 2.03;
                    amplitude *= 0.5;
                }
                return value / 0.9375;
            }
            void main() {
                vec3 normal = normalize(vSphere);
                vec3 p = normal;
                float c = cos(uSpin), s = sin(uSpin);
                p.xz = mat2(c, -s, s, c) * p.xz;
                float broad = fbm(p * 3.2 + vec3(2.4, 0.7, 1.3));
                float detail = fbm(p * 8.4 + vec3(5.2, 2.1, 3.7));
                float land = smoothstep(0.515, 0.55, broad + (detail - 0.5) * 0.17);
                vec3 ocean = mix(vec3(0.012,0.075,0.18), vec3(0.018,0.30,0.49), smoothstep(0.0,1.0,detail));
                vec3 terrain = mix(vec3(0.14,0.31,0.22), vec3(0.60,0.58,0.36), smoothstep(0.48,0.70,detail));
                terrain = mix(terrain, vec3(0.77,0.84,0.69), smoothstep(0.68,0.79,abs(p.y)) * 0.7);
                float cloudNoise = fbm(p * 13.0 + vec3(0.0, uTime * 0.012, 8.0));
                float cloud = smoothstep(0.66,0.78,cloudNoise) * 0.40;
                vec3 base = mix(ocean, terrain, land);
                base = mix(base, vec3(0.86,0.94,1.0), cloud);
                vec3 lightDir = normalize(vec3(-0.48,0.34,0.82));
                float diffuse = max(dot(normal, lightDir), 0.0);
                float day = smoothstep(-0.25,0.30,dot(normal,lightDir));
                vec3 color = base * (0.16 + diffuse * 0.92);
                color += vec3(0.12,0.48,0.88) * pow(1.0-max(dot(normal,vec3(0.0,0.0,1.0)),0.0), 3.5) * 0.48;
                color += vec3(0.02,0.08,0.20) * (1.0-day) * (1.0-land*0.4);
                gl_FragColor = vec4(color,1.0);
            }
        """

        const val LINE_VERTEX = """
            uniform mat4 uMvp;
            attribute vec3 aPosition;
            void main() { gl_Position = uMvp * vec4(aPosition,1.0); }
        """
        const val LINE_FRAGMENT = """
            precision mediump float;
            uniform vec4 uColor;
            void main() { gl_FragColor = uColor; }
        """
        const val ATMOSPHERE_VERTEX = """
            uniform mat4 uMvp;
            uniform mat4 uView;
            attribute vec3 aPosition;
            varying vec3 vNormal;
            varying vec3 vViewDirection;
            void main() {
                vec3 shellPosition = aPosition * 1.045;
                vec4 viewPosition = uView * vec4(shellPosition,1.0);
                vNormal = normalize(mat3(uView) * aPosition);
                vViewDirection = normalize(-viewPosition.xyz);
                gl_Position = uMvp * vec4(shellPosition,1.0);
            }
        """
        const val ATMOSPHERE_FRAGMENT = """
            precision mediump float;
            varying vec3 vNormal;
            varying vec3 vViewDirection;
            void main() {
                float rim = pow(1.0 - max(dot(normalize(vNormal),normalize(vViewDirection)),0.0),2.6);
                gl_FragColor = vec4(0.18,0.68,1.0,rim * 0.72);
            }
        """
        const val STAR_VERTEX = """
            uniform mat4 uMvp;
            attribute vec3 aPosition;
            attribute float aSize;
            varying float vFade;
            uniform float uTime;
            void main() {
                gl_Position = uMvp * vec4(aPosition,1.0);
                gl_PointSize = aSize;
                vFade = 0.62 + 0.38 * sin(uTime * 0.9 + aPosition.x * 2.0);
            }
        """
        const val STAR_FRAGMENT = """
            precision mediump float;
            varying float vFade;
            void main() {
                vec2 p = gl_PointCoord - vec2(0.5);
                float alpha = (1.0 - smoothstep(0.0,0.5,length(p))) * vFade;
                gl_FragColor = vec4(0.60,0.82,1.0,alpha);
            }
        """
        const val CARD_VERTEX = """
            uniform mat4 uProjection;
            attribute vec3 aPosition;
            attribute vec2 aUv;
            varying vec2 vUv;
            attribute float aSelected;
            varying float vSelected;
            void main() {
                vUv = aUv;
                vSelected = aSelected;
                gl_Position = uProjection * vec4(aPosition,1.0);
            }
        """
        const val CARD_FRAGMENT = """
            precision mediump float;
            uniform sampler2D uAtlas;
            varying vec2 vUv;
            varying float vSelected;
            void main() {
                vec4 color = texture2D(uAtlas,vUv);
                color.rgb = mix(color.rgb,vec3(0.55,0.98,1.0),vSelected * 0.18);
                gl_FragColor = color;
            }
        """
    }
}

internal fun reducedMotionEnabled(context: Context): Boolean =
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

internal fun supportsOpenGlEs2(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    manager.deviceConfigurationInfo.reqGlEsVersion >= 0x20000
}.getOrDefault(false)
