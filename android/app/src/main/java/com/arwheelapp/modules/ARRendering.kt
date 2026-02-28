package com.arwheelapp.modules

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.arwheelapp.processor.FrameConverter
import com.arwheelapp.processor.OnnxRuntimeHandler
import com.arwheelapp.utils.ARMode
import com.arwheelapp.utils.FrameYData
import com.arwheelapp.utils.ProcessedDetection
import com.arwheelapp.utils.RefinedResult
import com.google.ar.core.*
import dev.romainguy.kotlin.math.*
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.node.Node
import io.github.sceneview.utils.worldToScreen
import kotlin.math.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// Per-wheel tracking state
// ─────────────────────────────────────────────────────────────────────────────
private class WheelState {
    // Anchor & manual adjustment
    var anchor: Anchor? = null
    var isManuallyLocked: Boolean = false
    var manualOffset: Float3 = Float3(0f, 0f, 0f)

    // Position & rotation history (max 30 entries each)
    val posHistory: ArrayDeque<Float3> = ArrayDeque(30)
    val rotHistory: ArrayDeque<Quaternion> = ArrayDeque(30)

    // Whether the model is stable enough to be anchor-placed on tap
    var isReadyToAnchor: Boolean = false
    var stableFrames: Int = 0

    // Smoothed render values
    var renderPos: Float3 = Float3(0f, 0f, 0f)
    var renderRot: Quaternion = Quaternion()

    // Detection bookkeeping
    var detectionHits: Int = 0
    var lastDetectionTime: Long = 0L

    // 2D screen tracking (for overlap check & bbox validation)
    var lastScreen2D: Float2 = Float2(0f, 0f)
    var screenRadius2D: Float = 0f

    // useRecentAvg transition
    var useRecentAvg: Boolean = false
}

// ─────────────────────────────────────────────────────────────────────────────
class ARRendering(
    private val context: Context,
    private val onnxOverlayView: OnnxOverlayView,
    private val arSceneView: ARSceneView,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "ARRendering"

        // Hit-test donut
        private const val DONUT_POINTS = 8
        private const val DONUT_RADIUS_FALLBACK = 0.70f // 70% bbox when no opencv

        // History
        private const val HISTORY_SIZE = 30
        private const val RECENT_SIZE = 10
        private const val ROT_DIVERGE_DEG = 15f     // switch to recent avg above this

        // Stability — MUST be stable before user can tap to place
        private const val STABLE_FRAMES_REQUIRED = 20
        private const val POS_STABLE_M = 0.02f      // 2 cm
        private const val ROT_STABLE_DEG = 3f

        // Confirm criteria
        private const val MIN_CIRCULARITY = 0.50f
        private const val MIN_BBOX_RATIO = 0.50f    // min(w,h)/max(w,h)
        private const val MAX_TILT_DEG = 55f

        // Bbox-count consistency
        private const val BBOX_HISTORY_SIZE = 15
        private const val BBOX_MATCH_FRAMES = 5

        // Detection timeout
        private const val DETECTION_TIMEOUT_MS = 1500L

        // Dynamic FPS
        private const val TARGET_MS = 40L       // target ≤40 ms/frame
        private const val MIN_INT_MS = 33L      // max ~30 fps
        private const val MAX_INT_MS = 500L     // min ~2 fps when idle
        private const val IDLE_THRESH = 60      // frames before idle slow-down
        private const val IDLE_STEP = 20L       // ms added per idle frame-batch

        // Lerp
        private const val MIN_ALPHA = 0.04f
        private const val MAX_ALPHA = 0.60f
        private const val MIN_DIST = 0.02f
        private const val MAX_DIST = 0.30f
    }

    // ── Dependencies ─────────────────────────────────────────────────────────
    private val modelManager = ModelManager(arSceneView)
    private val frameConverter = FrameConverter()
    private val onnxHandler = OnnxRuntimeHandler(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Runtime state ─────────────────────────────────────────────────────────
    private var previousMode: ARMode? = null
    private var lastInferenceTime = 0L
    private var currentInterval = MIN_INT_MS
    private var idleFrames = 0

    private val modelPool = mutableListOf<Node>()
    private val augmentedImageMap = mutableMapOf<AugmentedImage, AugmentedImageNode>()
    private val markerlessActiveModels = mutableListOf<Node>()
    private val wheelStates = mutableMapOf<Node, WheelState>()

    @Volatile private var latestDetections: List<ProcessedDetection> = emptyList()
    @Volatile private var isNewDetection = false

    // Bbox count
    private val bboxCountHistory = ArrayDeque<Int>(BBOX_HISTORY_SIZE)

    // Motion tracking for dynamic FPS (reset each hit-test frame)
    private var anyMotionThisFrame = false

    // Y-plane double-buffer
    private var yBufA: ByteArray? = null
    private var yBufB: ByteArray? = null
    private var useYBufA = true

    // ── Public config ─────────────────────────────────────────────────────────
    @Volatile var snapThreshold = 0.4572f
    @Volatile var modelPath = "models/wheel1.glb"   // !!Change to UI

    // ── Callbacks (always called on Main thread) ───────────────────────────────
    var selectedModel: Node? = null
    var onShowAdjustmentUI: ((Boolean) -> Unit)? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Manual nudge / confirm  (called from UI thread)
    // ─────────────────────────────────────────────────────────────────────────
    fun nudgeSelectedModel(dx: Float, dy: Float, dz: Float) {
        val ws = selectedModel?.let { wheelStates[it] } ?: return
        ws.manualOffset = Float3(
            ws.manualOffset.x + dx,
            ws.manualOffset.y + dy,
            ws.manualOffset.z + dz
        )
    }

    fun finishAdjusting() {
        val m  = selectedModel ?: return
        val ws = wheelStates[m] ?: return
        // Bake current position into a new anchor
        val pose = Pose(
            floatArrayOf(m.position.x, m.position.y, m.position.z),
            floatArrayOf(m.quaternion.x, m.quaternion.y, m.quaternion.z, m.quaternion.w)
        )
        ws.anchor?.detach()
        ws.anchor = arSceneView.session?.createAnchor(pose)
        ws.manualOffset = Float3(0f, 0f, 0f)
        selectedModel = null
        onShowAdjustmentUI?.invoke(false)   // already on main thread (called from UI)
    }

    fun cancelAdjusting() {
        val m = selectedModel ?: return
        val ws = wheelStates[m] ?: return
        ws.isManuallyLocked = false
        ws.manualOffset = Float3(0f, 0f, 0f)
        selectedModel = null
        onShowAdjustmentUI?.invoke(false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public model APIs
    // ─────────────────────────────────────────────────────────────────────────
    fun updateNewModel(path: String) {
        modelPath = path
        modelPool.forEach { modelManager.changeModel(it, path, coroutineScope) }
    }

    fun updateModelSize(sizeInch: Float) {
        val cm = sizeInch * 2.54f
        snapThreshold = cm / 100f
        val scale = cm / 45.72f
        modelPool.forEach { modelManager.changeModelSize(it, scale) }
    }

    fun clear() {
        modelPool.forEach { it.destroy() }
        modelPool.clear()
        augmentedImageMap.values.forEach { it.destroy() }
        augmentedImageMap.clear()
        markerlessActiveModels.clear()
        wheelStates.clear()
        onnxOverlayView.clear()
    }

    fun setupMarkerDatabase(session: Session, markerSize: Float = 0.15f) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AugmentedImageDatabase(session)
                val assets = context.assets
                assets.list("markers")
                    ?.filter { it.endsWith(".jpg", true) || it.endsWith(".png", true) }
                    ?.forEach { fn ->
                        runCatching {
                            assets.open("markers/$fn").use { s ->
                                db.addImage(fn.substringBeforeLast("."), BitmapFactory.decodeStream(s), markerSize)
                                Log.d(TAG, "Marker loaded: $fn ✅")
                            }
                        }
                    }
                withContext(Dispatchers.Main) {
                    session.configure(session.config.apply {
                        augmentedImageDatabase = db
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        focusMode = Config.FocusMode.AUTO
                    })
                }
            } catch (e: Exception) { Log.e(TAG, "setupMarkerDatabase", e) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render entry-point  (called every frame, on GL/main thread)
    // ─────────────────────────────────────────────────────────────────────────
    fun render(arSceneView: ARSceneView, frame: Frame, mode: ARMode, deviceRotation: Int) {
        if (previousMode != mode) {
            handleModeSwitch()
            previousMode = mode
        }
        when (mode) {
            ARMode.MARKER_BASED -> processMarkerBased(arSceneView, frame)
            ARMode.MARKERLESS -> {
                val t0 = SystemClock.uptimeMillis()
                runInference(frame, deviceRotation)
                processHitTest(arSceneView, frame)
                updateDynamicFPS(SystemClock.uptimeMillis() - t0)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mode switch
    // ─────────────────────────────────────────────────────────────────────────
    private fun handleModeSwitch() {
        selectedModel = null
        onShowAdjustmentUI?.invoke(false)
        modelPool.forEach {
            it.parent = null
            it.isVisible = false
            arSceneView.removeChildNode(it)
        }
        if (previousMode == ARMode.MARKER_BASED) {
            augmentedImageMap.values.forEach { it?.destroy() }
            augmentedImageMap.clear()
        }
        if (previousMode == ARMode.MARKERLESS) {
            wheelStates.values.forEach { it.anchor?.detach() }
            wheelStates.clear()
            markerlessActiveModels.clear()
            onnxOverlayView.clear()
            bboxCountHistory.clear()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Marker-based mode
    // ─────────────────────────────────────────────────────────────────────────
    private fun processMarkerBased(arSceneView: ARSceneView, frame: Frame) {
        for (img in frame.getUpdatedTrackables(AugmentedImage::class.java)) {
            when (img.trackingState) {
                TrackingState.TRACKING -> {
                    augmentedImageMap.getOrPut(img) {
                        AugmentedImageNode(arSceneView.engine, img).apply {
                            addChildNode(getOrCreatePoolModel(modelPath).apply { isVisible = true })
                            arSceneView.addChildNode(this)
                        }
                    }.isVisible = (img.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING)
                }
                TrackingState.STOPPED -> augmentedImageMap.remove(img)?.let {
                    arSceneView.removeChildNode(it)
                    it.destroy()
                }
                TrackingState.PAUSED  -> augmentedImageMap[img]?.isVisible = false
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Inference — rate-limited
    // ─────────────────────────────────────────────────────────────────────────
    private fun runInference(frame: Frame, deviceRotation: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastInferenceTime < currentInterval) return
        lastInferenceTime = now

        try {
            val tensor = frameConverter.convertFrameToTensor(frame, deviceRotation)
            val yData = extractYPlane(frame)
            val vw = arSceneView.width.toFloat()
            val vh = arSceneView.height.toFloat()
            onnxHandler.runOnnxInferenceAsync(tensor, deviceRotation, yData, vw, vh) { results ->
                latestDetections = results
                isNewDetection = true
                onnxOverlayView.updateDetections(results.map { it.detection })
            }
        } catch (e: Exception) { Log.e(TAG, "Inference error", e) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hit-test & pose tracking
    // ─────────────────────────────────────────────────────────────────────────
    private fun processHitTest(arSceneView: ARSceneView, frame: Frame) {
        if (!isNewDetection) return
        isNewDetection = false

        if (frame.camera.trackingState != TrackingState.TRACKING) return

        val now = SystemClock.uptimeMillis()

        val vw = arSceneView.width.toFloat()
        val vh = arSceneView.height.toFloat()
        val camPos = frame.camera.pose.let { Float3(it.tx(), it.ty(), it.tz()) }

        // Bbox count history
        if (bboxCountHistory.size >= BBOX_HISTORY_SIZE) bboxCountHistory.removeFirst()
        bboxCountHistory.addLast(latestDetections.size)
        val targetCount = getTargetBboxCount()

        val workDets = if (latestDetections.size > targetCount)
            latestDetections.sortedByDescending { it.detection.confidence }.take(targetCount)
        else latestDetections

        val claimed = mutableSetOf<Node>()

        for (pd in workDets) {
            val det = pd.detection
            val cv = pd.cvResult
            val bbox = det.boundingBox

            // ── 1. Collect hit points ─────────────────────────────────────
            val hitPts = collectHitPoints(frame, cv, bbox, vw, vh)
            if (hitPts.isEmpty()) continue

            // ── 2. Center = average of all hit points ─────────────────────
            val center = hitPts.average3()

            // ── 3. Plane normal (must face camera) ────────────────────────
            val normal = planeNormal(hitPts, center, camPos)

            // ── 4. Rotation (model forward = normal = facing camera) ──────
            val planeRot = lookRotationForward(normal)

            // ── 5. Camera tilt from plane ─────────────────────────────────
            val tiltDeg = Math.toDegrees(
                acos(dot(normal, normalize(camPos - center)).coerceIn(-1f, 1f)).toDouble()
            ).toFloat()

            // ── 6. Snap to closest model or create new ────────────────────
            val closest = markerlessActiveModels
                .filter { it !in claimed }
                .minByOrNull { dist(it.position, center) }
            val model = if (closest != null && dist(closest.position, center) < snapThreshold * 1.2f)
                closest
            else getOrCreateMarkerlessModel()

            claimed.add(model)
            val ws = wheelStates.getOrPut(model) { WheelState() }

            // ── 7. If manually locked → servo the anchor + offset ─────────
            if (ws.isManuallyLocked) {
                val ap = ws.anchor?.pose ?: continue
                model.position = Float3(
                    ap.tx() + ws.manualOffset.x,
                    ap.ty() + ws.manualOffset.y,
                    ap.tz() + ws.manualOffset.z
                )
                model.quaternion = Quaternion(ap.qx(), ap.qy(), ap.qz(), ap.qw())
                continue
            }

            ws.lastDetectionTime = now
            ws.detectionHits++
            if (ws.detectionHits >= 3) model.isVisible = true

            // ── 8. Update position history ────────────────────────────────
            if (ws.posHistory.size >= HISTORY_SIZE) ws.posHistory.removeFirst()
            ws.posHistory.addLast(center)

            // ── 9. Update rotation history ────────────────────────────────
            if (ws.rotHistory.size >= HISTORY_SIZE) ws.rotHistory.removeFirst()
            ws.rotHistory.addLast(planeRot)

            // ── 10. Compute best rotation (full avg vs recent 10) ─────────
            val fullAvg = averageQuaternions(ws.rotHistory.toList())
            val recentAvg = averageQuaternions(ws.rotHistory.takeLast(RECENT_SIZE))
            val diverge = angleBetween(fullAvg, recentAvg)
            val bestRot: Quaternion = when {
                diverge > ROT_DIVERGE_DEG -> {
                    ws.useRecentAvg = true
                    recentAvg
                }
                ws.useRecentAvg && diverge > 2f -> recentAvg    // still transitioning
                else -> {
                    ws.useRecentAvg = false
                    fullAvg
                }
            }

            // ── 11. Confirm criteria ──────────────────────────────────────
            val bboxW = bbox.width() * vw
            val bboxH = bbox.height() * vh
            val ratio = if (max(bboxW, bboxH) > 0f) min(bboxW, bboxH) / max(bboxW, bboxH) else 0f
            val screenPt = arSceneView.view.worldToScreen(center)
            val bboxAbs = RectF(bbox.left * vw, bbox.top * vh, bbox.right * vw, bbox.bottom * vh)
            val inBbox = bboxAbs.contains(screenPt.x, screenPt.y)

            val confirmed = confirmDetection(
                circularity = cv.circularity,
                bboxRatio = ratio,
                posInBbox = inBbox,
                tiltDeg = tiltDeg,
                hasOpenCV = cv.isFound
            )

            // ── 12. Stability counter ─────────────────────────────────────
            val prevPos = if (ws.posHistory.size >= 2) ws.posHistory[ws.posHistory.size - 2] else null
            val prevRot = if (ws.rotHistory.size >= 2) ws.rotHistory[ws.rotHistory.size - 2] else null
            val posStable = prevPos == null || dist(prevPos, center) < POS_STABLE_M
            val rotStable = prevRot == null || angleBetween(prevRot, planeRot) < ROT_STABLE_DEG

            if (confirmed && posStable && rotStable) {
                ws.stableFrames++
                if (ws.stableFrames >= STABLE_FRAMES_REQUIRED) {
                    ws.isReadyToAnchor = true
                    // Visual hint: brighter / ready indicator (overlay handles this via OnnxOverlayView if needed)
                }
            } else {
                ws.stableFrames = 0
                ws.isReadyToAnchor = false
            }

            // ── 13. Render (lerp to best pos/rot) ─────────────────────────
            val renderPos = center   // pos always = center
            val alpha = dynamicAlpha(model.position, renderPos)
            model.position = mix(model.position, renderPos, alpha)
            model.quaternion = slerp(model.quaternion, bestRot, alpha)

            if (dist(model.position, renderPos) > 0.02f) anyMotionThisFrame = true

            // Update 2D tracking info
            ws.lastScreen2D = Float2(screenPt.x, screenPt.y)
            ws.screenRadius2D = max(bboxW, bboxH) / 2f
        }

        hideStaleModels(claimed, now)

        if (anyMotionThisFrame) idleFrames = 0 else idleFrames++
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hit-test helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun collectHitPoints(
        frame: Frame, cv: RefinedResult, bbox: RectF, vw: Float, vh: Float
    ): List<Float3> {
        val pts = mutableListOf<Float3>()

        // Center hit (opencv cx/cy, or bbox center)
        frame.hitTest(cv.cx, cv.cy)
            .firstOrNull { it.trackable is Plane || it.trackable is Point }
            ?.let { pts.add(Float3(it.hitPose.tx(), it.hitPose.ty(), it.hitPose.tz())) }

        // Donut: radius = max(ellipse w, h)/2 from opencv, else 70% bbox
        val radius: Float = if (cv.isFound && cv.width > 0f && cv.height > 0f) {
            max(cv.width, cv.height) / 2f
        } else {
            max(bbox.width() * vw, bbox.height() * vh) * DONUT_RADIUS_FALLBACK / 2f
        }
        val aRad = Math.toRadians(cv.angle.toDouble()).toFloat()
        val cosA = cos(aRad)
        val sinA = sin(aRad)

        for (i in 0 until DONUT_POINTS) {
            val t = (2f * kotlin.math.PI.toFloat() * i) / DONUT_POINTS
            val lx = radius * cos(t)
            val ly = radius * sin(t)
            frame.hitTest(cv.cx + lx * cosA - ly * sinA, cv.cy + lx * sinA + ly * cosA)
                .firstOrNull { it.trackable is Plane || it.trackable is Point }
                ?.let { pts.add(Float3(it.hitPose.tx(), it.hitPose.ty(), it.hitPose.tz())) }
        }
        return pts
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Plane normal – always points toward camera (user side)
    // ─────────────────────────────────────────────────────────────────────────
    private fun planeNormal(pts: List<Float3>, center: Float3, camPos: Float3): Float3 {
        if (pts.size < 3) return normalize(camPos - center)
        var accum = Float3(0f, 0f, 0f)
        for (i in pts.indices) {
            accum += cross(pts[i] - center, pts[(i + 1) % pts.size] - center)
        }
        val len = length(accum)
        if (len < 1e-6f || len.isNaN()) return normalize(camPos - center)
        val n = normalize(accum)
        return if (dot(n, normalize(camPos - center)) < 0f) -n else n
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rotation: model FORWARD (Z+) = plane normal = faces camera
    // ─────────────────────────────────────────────────────────────────────────
    private fun lookRotationForward(normal: Float3): Quaternion {
        val f  = normalize(normal)
        val up = if (abs(dot(f, Float3(0f, 1f, 0f))) > 0.99f) Float3(0f, 0f, 1f) else Float3(0f, 1f, 0f)
        val r  = normalize(cross(up, f))
        val u2 = cross(f, r)
        val tr = r.x + u2.y + f.z
        return when {
            tr > 0f -> { val s = sqrt(tr + 1f) * 2f
                Quaternion((u2.z - f.y)/s, (f.x - r.z)/s, (r.y - u2.x)/s, 0.25f*s) }
            r.x > u2.y && r.x > f.z -> { val s = sqrt(1f + r.x - u2.y - f.z) * 2f
                Quaternion(0.25f*s, (u2.x+r.y)/s, (f.x+r.z)/s, (u2.z-f.y)/s) }
            u2.y > f.z -> { val s = sqrt(1f + u2.y - r.x - f.z) * 2f
                Quaternion((u2.x+r.y)/s, 0.25f*s, (f.y+u2.z)/s, (f.x-r.z)/s) }
            else -> { val s = sqrt(1f + f.z - r.x - u2.y) * 2f
                Quaternion((f.x+r.z)/s, (f.y+u2.z)/s, 0.25f*s, (r.y-u2.x)/s) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Confirmation criteria
    // ─────────────────────────────────────────────────────────────────────────
    private fun confirmDetection(
        circularity: Float, bboxRatio: Float, posInBbox: Boolean, tiltDeg: Float, hasOpenCV: Boolean
    ): Boolean {
        val circOk = if (hasOpenCV) circularity >= MIN_CIRCULARITY else bboxRatio >= MIN_CIRCULARITY
        val ratioOk = bboxRatio >= MIN_BBOX_RATIO
        val tiltOk = tiltDeg <= MAX_TILT_DEG
        return circOk && ratioOk && posInBbox && tiltOk
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Model pool
    // ─────────────────────────────────────────────────────────────────────────
    private fun getOrCreatePoolModel(path: String): Node =
        modelPool.find { it.parent == null || !it.isVisible }?.apply { isVisible = true }
            ?: modelManager.createNewModel(path, coroutineScope).also { modelPool.add(it) }

    /** Creates or recycles a markerless model node, wires the tap handler. */
    private fun getOrCreateMarkerlessModel(): Node {
        // Recycle first invisible un-anchored model
        markerlessActiveModels.firstOrNull { !it.isVisible }?.let { m ->
            val ws = wheelStates.getOrPut(m) { WheelState() }
            ws.stableFrames = 0
            ws.isReadyToAnchor = false
            ws.detectionHits = 0
            return m
        }
        // Create new
        return modelManager.createNewModel(modelPath, coroutineScope).also { m ->
            m.isVisible = false
            arSceneView.addChildNode(m)
            markerlessActiveModels.add(m)
            val ws = WheelState()
            wheelStates[m] = ws

            // ── Tap handler: place anchor and show adjustment UI ──────────
            // onSingleTapConfirmed fires on the MAIN thread (touch event)
            m.onSingleTapConfirmed = { _ ->
                val state = wheelStates[m]
                if (state != null) {
                    if (state.anchor != null) {
                        // Re-open adjustment for already-anchored model
                        state.isManuallyLocked = true
                        selectedModel = m
                        onShowAdjustmentUI?.invoke(true)    // main thread ✅
                    } else if (state.isReadyToAnchor) {
                        // Place anchor NOW at latest tracked position
                        val bestPos = state.posHistory.lastOrNull() ?: m.position
                        val bestRot = averageQuaternions(state.rotHistory.toList())
                            .takeIf { state.rotHistory.isNotEmpty() } ?: m.quaternion
                        state.anchor = arSceneView.session?.createAnchor(
                            Pose(
                                floatArrayOf(bestPos.x, bestPos.y, bestPos.z),
                                floatArrayOf(bestRot.x, bestRot.y, bestRot.z, bestRot.w)
                            )
                        )
                        state.isManuallyLocked = true
                        selectedModel = m
                        onShowAdjustmentUI?.invoke(true)    // main thread ✅
                    }
                    // Not ready yet → ignore tap (model hasn't been stable 20 frames)
                }
                true
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bbox count helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun getTargetBboxCount(): Int {
        if (bboxCountHistory.isEmpty()) return 1
        val sorted = bboxCountHistory.sorted()
        return sorted[sorted.size / 2]
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stale model cleanup
    // ─────────────────────────────────────────────────────────────────────────
    private fun hideStaleModels(claimed: Set<Node>, now: Long) {
        for (m in markerlessActiveModels) {
            if (m in claimed) continue
            val ws = wheelStates[m] ?: continue
            if (ws.anchor != null) continue     // anchored → keep visible always
            if (now - ws.lastDetectionTime > DETECTION_TIMEOUT_MS) {
                m.isVisible = false
                if (m == selectedModel) {
                    selectedModel = null
                    mainHandler.post { onShowAdjustmentUI?.invoke(false) }
                }
                resetState(ws)
            }
        }
    }

    private fun resetState(ws: WheelState) {
        ws.anchor?.detach()
        ws.anchor = null
        ws.posHistory.clear()
        ws.rotHistory.clear()
        ws.stableFrames = 0
        ws.isReadyToAnchor = false
        ws.detectionHits = 0
        ws.isManuallyLocked = false
        ws.manualOffset = Float3(0f, 0f, 0f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dynamic FPS — based on total process time + idle detection
    // ─────────────────────────────────────────────────────────────────────────
    private fun updateDynamicFPS(totalMs: Long) {
        currentInterval = when {
            totalMs > TARGET_MS -> (totalMs + 10L).coerceIn(MIN_INT_MS, MAX_INT_MS)
            idleFrames > IDLE_THRESH -> (currentInterval + IDLE_STEP).coerceAtMost(MAX_INT_MS)
            else -> MIN_INT_MS
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Math utilities
    // ─────────────────────────────────────────────────────────────────────────
    private fun dist(a: Float3, b: Float3) = length(a - b)

    private fun dynamicAlpha(cur: Float3, tgt: Float3): Float {
        val t = ((dist(cur, tgt) - MIN_DIST) / (MAX_DIST - MIN_DIST)).coerceIn(0f, 1f)
        return MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * t
    }

    private fun angleBetween(q1: Quaternion, q2: Quaternion) =
        Math.toDegrees(2.0 * acos(abs(dot(q1, q2)).coerceIn(0f, 1f).toDouble())).toFloat()

    private fun List<Float3>.average3() = Float3(
        map { it.x }.average().toFloat(),
        map { it.y }.average().toFloat(),
        map { it.z }.average().toFloat()
    )

    /** Iterative slerp-chain average — accurate for clustered rotations (N≤30). */
    private fun averageQuaternions(qs: List<Quaternion>): Quaternion {
        if (qs.isEmpty()) return Quaternion()
        var avg = qs[0]
        for (i in 1 until qs.size) avg = slerp(avg, qs[i], 1f / (i + 1f))
        return avg
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Y-plane extraction (double-buffered)
    // ─────────────────────────────────────────────────────────────────────────
    private fun extractYPlane(frame: Frame): FrameYData? {
        val img = try { frame.acquireCameraImage() } catch (e: Exception) { return null }
        return try {
            val yp = img.planes[0].buffer.apply { rewind() }
            val w = img.width
            val h = img.height
            val stride = img.planes[0].rowStride
            val size = w * h

            if (yBufA == null || yBufA!!.size != size) {
                yBufA = ByteArray(size)
                yBufB = ByteArray(size)
            }
            val buf = if (useYBufA) yBufA!! else yBufB!!
            useYBufA = !useYBufA

            if (stride == w) yp.get(buf)
            else for (row in 0 until h) {
                yp.position(row * stride)
                yp.get(buf, row * w, w)
            }
            FrameYData(buf, w, h, w)
        } catch (e: Exception) { null }
        finally { img.close() }
    }
}