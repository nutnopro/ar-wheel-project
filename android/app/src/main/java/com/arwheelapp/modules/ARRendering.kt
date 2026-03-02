// utils/ARRendering.kt
package com.arwheelapp.modules

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.arwheelapp.processor.FrameConverter
import com.arwheelapp.processor.MLHandler
import com.arwheelapp.utils.*
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
// Quality score for anchor upgrade comparison
// ─────────────────────────────────────────────────────────────────────────────
private data class AnchorQuality(val circularity: Float, val bboxRatio: Float) {
    fun score() = circularity * 0.6f + bboxRatio * 0.4f
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-wheel tracking state
// ─────────────────────────────────────────────────────────────────────────────
private class WheelState {
    // Anchor
    var anchor: Anchor? = null
    var anchorQuality: AnchorQuality? = null
    var isFrozen: Boolean = false           // anchor exists but model hidden (missed 30 frames)
    var isManuallyLocked: Boolean = false   // user opened adjust UI

    // Manual adjustment (in plane-local space)
    var manualOffsetRight: Float = 0f
    var manualOffsetUp: Float = 0f
    var manualRotH: Float = 0f              // degrees around plane up axis
    var manualRotV: Float = 0f              // degrees around plane right axis

    // Plane axes stored when anchor placed, used for nudge
    var planeRight: Float3 = Float3(1f, 0f, 0f)
    var planeUp: Float3 = Float3(0f, 1f, 0f)

    // Position history (max 20, render uses avg of last 5)
    val posHistory: ArrayDeque<Float3> = ArrayDeque(20)

    // Rotation history (max 20, adaptive window 6..20)
    val rotHistory: ArrayDeque<Quaternion> = ArrayDeque(20)
    var rotWindowSize: Int = 20             // grows back to 20 after divergence

    // Screen-space (updated every processed frame)
    var lastScreenCenter: Float2 = Float2(0f, 0f)
    var lastScreenBounds: RectF = RectF()

    // Detection bookkeeping
    var detectionHits: Int = 0
    var lastDetectionTime: Long = 0L
    var missFrames: Int = 0                 // consecutive misses while anchored

    // Pre-anchor stability
    var stableFrames: Int = 0
    var isReadyToAnchor: Boolean = false
    var lastCenter: Float3? = null
    var lastRot: Quaternion? = null
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

        private const val DONUT_POINTS = 8
        private const val DONUT_RADIUS_FALLBACK = 0.70f

        private const val POS_HISTORY_SIZE = 20
        private const val POS_RENDER_SIZE = 5
        private const val ROT_HISTORY_SIZE = 20
        private const val ROT_RECENT_SIZE = 6
        private const val ROT_DIVERGE_DEG = 20f
        private const val BBOX_HISTORY_SIZE = 15

        private const val DEFAULT_WHEEL_DIAMETER_M = 0.4572f    // 18 inch
        private const val SNAP_MULTIPLIER = 1.2f

        private const val STABLE_FRAMES_REQUIRED = 15
        private const val POS_STABLE_M = 0.025f
        private const val ROT_STABLE_DEG = 4f

        private const val MIN_CIRCULARITY = 0.45f
        private const val MIN_BBOX_RATIO = 0.45f
        private const val ANCHOR_UPGRADE_MARGIN = 0.05f

        private const val ANCHOR_MISS_LIMIT = 30
        private const val UNANCHORED_TIMEOUT_MS = 1500L

        // Inference FPS (independent)
        private const val INF_MIN_INT_MS = 50L
        private const val INF_MAX_INT_MS = 500L

        // HitTest FPS (independent)
        private const val HT_TARGET_MS = 33L
        private const val HT_MIN_INT_MS = 16L
        private const val HT_MAX_INT_MS = 200L

        private const val MIN_ALPHA = 0.04f
        private const val MAX_ALPHA = 0.60f
        private const val MIN_DIST = 0.02f
        private const val MAX_DIST = 0.30f
    }

    private val modelManager = ModelManager(arSceneView)
    private val frameConverter = FrameConverter()
    private val onnxHandler = MLHandler(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var previousMode: ARMode? = null
    private var lastInferenceTime = 0L
    private var lastHitTestTime = 0L
    private var infInterval = INF_MIN_INT_MS
    private var htInterval = HT_MIN_INT_MS

    private val augmentedImageMap = mutableMapOf<AugmentedImage, AugmentedImageNode>()
    private val markerlessActiveModels = mutableListOf<Node>()
    private val wheelStates = mutableMapOf<Node, WheelState>()

    @Volatile private var latestDetections: List<ProcessedDetection> = emptyList()
    @Volatile private var isNewDetection = false

    private val bboxCountHistory = ArrayDeque<Int>(BBOX_HISTORY_SIZE)
    private var anyMotionThisFrame = false

    private var yBufA: ByteArray? = null
    private var yBufB: ByteArray? = null
    private var useYBufA = true

    @Volatile var snapThreshold = DEFAULT_WHEEL_DIAMETER_M
    @Volatile var modelPath = "models/wheel1.glb"   // !!Change to UI

    var selectedModel: Node? = null
    var onShowAdjustmentUI: ((Boolean) -> Unit)? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Nudge (called from UI main thread)
    // editMode = "POS" | "ROT",  direction = "LEFT"|"RIGHT"|"UP"|"DOWN"
    // ─────────────────────────────────────────────────────────────────────────
    fun nudgeModel(editMode: String, direction: String) {
        val ws = selectedModel?.let { wheelStates[it] } ?: return
        val posStep = 0.005f
        val rotStep = 1.5f
        when (editMode) {
            "POS" -> when (direction) {
                "LEFT" -> ws.manualOffsetRight -= posStep
                "RIGHT" -> ws.manualOffsetRight += posStep
                "UP" -> ws.manualOffsetUp += posStep
                "DOWN" -> ws.manualOffsetUp -= posStep
            }
            "ROT" -> when (direction) {
                "LEFT" -> ws.manualRotH -= rotStep
                "RIGHT" -> ws.manualRotH += rotStep
                "UP" -> ws.manualRotV -= rotStep
                "DOWN" -> ws.manualRotV += rotStep
            }
        }
    }

    fun finishAdjusting() {
        val m  = selectedModel ?: return
        val ws = wheelStates[m] ?: return
        val pose = Pose(
            floatArrayOf(m.position.x, m.position.y, m.position.z),
            floatArrayOf(m.quaternion.x, m.quaternion.y, m.quaternion.z, m.quaternion.w)
        )
        ws.anchor?.detach()
        ws.anchor = arSceneView.session?.createAnchor(pose)
        ws.manualOffsetRight = 0f
        ws.manualOffsetUp = 0f
        ws.manualRotH = 0f
        ws.manualRotV = 0f
        selectedModel = null
        onShowAdjustmentUI?.invoke(false)
    }

    fun cancelAdjusting() {
        val ws = selectedModel?.let { wheelStates[it] } ?: return
        ws.isManuallyLocked = false
        ws.manualOffsetRight = 0f; ws.manualOffsetUp = 0f
        ws.manualRotH = 0f; ws.manualRotV = 0f
        selectedModel = null
        onShowAdjustmentUI?.invoke(false)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public model APIs
    // ─────────────────────────────────────────────────────────────────────────
    fun updateNewModel(path: String) {
        modelPath = path
        markerlessActiveModels.forEach { modelManager.changeModel(it, path, coroutineScope) }
    }

    fun updateModelSize(sizeInch: Float) {
        val cm = sizeInch * 2.54f
        snapThreshold = cm / 100f
        val scale = cm / 45.72f
        markerlessActiveModels.forEach { modelManager.changeModelSize(it, scale) }
    }

    fun clear() {
        augmentedImageMap.values.forEach { it.destroy() }
        augmentedImageMap.clear()
        markerlessActiveModels.forEach {
            arSceneView.removeChildNode(it)
            it.destroy()
        }
        markerlessActiveModels.clear()
        wheelStates.clear()
        onnxOverlayView.clear()
    }

    fun setupMarkerDatabase(session: Session, markerSize: Float = 0.15f) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AugmentedImageDatabase(session)
                context.assets.list("markers")
                    ?.filter { it.endsWith(".jpg", true) || it.endsWith(".png", true) }
                    ?.forEach { fn -> runCatching {
                        context.assets.open("markers/$fn").use { s ->
                            db.addImage(fn.substringBeforeLast("."), BitmapFactory.decodeStream(s), markerSize)
                        }
                    }}
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
    // Render entry-point
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
        mainHandler.post { onShowAdjustmentUI?.invoke(false) }
        if (previousMode == ARMode.MARKER_BASED) {
            augmentedImageMap.values.forEach { it?.destroy() }
            augmentedImageMap.clear()
        }
        if (previousMode == ARMode.MARKERLESS) {
            wheelStates.values.forEach { it.anchor?.detach() }
            wheelStates.clear()
            markerlessActiveModels.forEach {
                arSceneView.removeChildNode(it)
                it.destroy()
            }
            markerlessActiveModels.clear()
            onnxOverlayView.clear()
            bboxCountHistory.clear()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Marker-based
    // ─────────────────────────────────────────────────────────────────────────
    private fun processMarkerBased(arSceneView: ARSceneView, frame: Frame) {
        for (img in frame.getUpdatedTrackables(AugmentedImage::class.java)) {
            when (img.trackingState) {
                TrackingState.TRACKING -> {
                    augmentedImageMap.getOrPut(img) {
                        AugmentedImageNode(arSceneView.engine, img).apply {
                            addChildNode(modelManager.createNewModel(modelPath, coroutineScope)
                                .apply { isVisible = true })
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
    // Inference — rate-limited independently
    // ─────────────────────────────────────────────────────────────────────────
    private fun runInference(frame: Frame, deviceRotation: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastInferenceTime < infInterval) return
        lastInferenceTime = now

        try {
            val tensor = frameConverter.convertFrameToTensor(frame, deviceRotation)
            val yData = extractYPlane(frame)
            val vw = arSceneView.width.toFloat()
            val vh = arSceneView.height.toFloat()
            onnxHandler.runInferenceAsync(tensor, deviceRotation, yData, vw, vh) { results ->
                latestDetections = results
                isNewDetection = true
                onnxOverlayView.updateDetections(results.map { it.detection })
            }
        } catch (e: Exception) { Log.e(TAG, "Inference error", e) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hit-test & pose — rate-limited independently
    // ─────────────────────────────────────────────────────────────────────────
    private fun processHitTest(arSceneView: ARSceneView, frame: Frame) {
        // Always keep locked models at correct pose, even if we skip this frame
        renderLockedModels()

        val now = SystemClock.uptimeMillis()
        if (now - lastHitTestTime < htInterval) return
        lastHitTestTime = now

        if (!isNewDetection) return
        isNewDetection = false
        if (frame.camera.trackingState != TrackingState.TRACKING) return

        val vw = arSceneView.width.toFloat()
        val vh = arSceneView.height.toFloat()
        val camPos = frame.camera.pose.let { Float3(it.tx(), it.ty(), it.tz()) }

        // Bbox count → mode (median)
        if (bboxCountHistory.size >= BBOX_HISTORY_SIZE) bboxCountHistory.removeFirst()
        bboxCountHistory.addLast(latestDetections.size)
        val targetCount = getModeBboxCount()

        // Anchored models don't count toward the free slot limit
        val frozenAnchoredCount = markerlessActiveModels.count {
            val ws = wheelStates[it]; ws?.anchor != null && !ws.isFrozen
        }
        val freeSlots = (targetCount - frozenAnchoredCount).coerceAtLeast(0)

        val workDets = if (latestDetections.size > targetCount)
            latestDetections.sortedByDescending { it.detection.confidence }.take(targetCount)
        else latestDetections

        val claimed = mutableSetOf<Node>()
        anyMotionThisFrame = false

        for (pd in workDets) {
            val det = pd.detection
            val cv = pd.cvResult
            val bbox = det.boundingBox

            // 1. Hit points + outlier removal
            val rawPts = collectHitPoints(frame, cv, bbox, vw, vh)
            if (rawPts.isEmpty()) continue
            val hitPts = removeOutliers(rawPts)
            if (hitPts.isEmpty()) continue

            // 2. Center
            val center = hitPts.average3()

            // 3. Back-project → must be inside bbox
            val screenPt = arSceneView.view.worldToScreen(center)
            val bboxAbs = RectF(bbox.left * vw, bbox.top * vh, bbox.right * vw, bbox.bottom * vh)
            if (!bboxAbs.contains(screenPt.x, screenPt.y)) continue

            // 4. Plane normal + rotation
            val normal = planeNormal(hitPts, center, camPos)
            val planeRot = lookRotationForward(normal)
            val planeRight = buildPlaneRight(normal)
            val planeUp = normalize(cross(normal, planeRight))

            // 5. Snap: anchored models first, then free (min gap = snapThreshold × 1.2)
            val minGap = snapThreshold * SNAP_MULTIPLIER
            val model = snapToModel(center, claimed, minGap)
                ?: run {
                    if (claimed.count { wheelStates[it]?.anchor == null } >= freeSlots &&
                        freeSlots > 0
                    ) null
                    else getOrCreateMarkerlessModel()
                } ?: continue

            claimed.add(model)
            val ws = wheelStates.getOrPut(model) { WheelState() }

            // 6. Manually locked → apply manual pose, skip AI
            if (ws.isManuallyLocked) continue   // renderLockedModels() handles it

            ws.lastDetectionTime = now
            ws.missFrames = 0
            ws.detectionHits++
            if (ws.detectionHits >= 3) model.isVisible = true

            // 7. Bbox screen dimensions for visibility check
            val bboxW = bbox.width() * vw
            val bboxH = bbox.height() * vh
            val ratio = if (max(bboxW, bboxH) > 0f) min(bboxW, bboxH) / max(bboxW, bboxH) else 0f

            // 8. Update position history
            if (ws.posHistory.size >= POS_HISTORY_SIZE) ws.posHistory.removeFirst()
            ws.posHistory.addLast(center)

            // 9. Update rotation history + adaptive window
            if (ws.rotHistory.size >= ROT_HISTORY_SIZE) ws.rotHistory.removeFirst()
            ws.rotHistory.addLast(planeRot)
            val bestRot = adaptiveRotAverage(ws)

            // 10. Render position = avg of last 5
            val renderPos = ws.posHistory.takeLast(POS_RENDER_SIZE).average3()

            // 11. worldToScreen visibility: model must be inside some bbox
            val modelScreen = arSceneView.view.worldToScreen(renderPos)
            ws.lastScreenCenter = Float2(modelScreen.x, modelScreen.y)
            ws.lastScreenBounds = RectF(
                modelScreen.x - bboxW / 2f, modelScreen.y - bboxH / 2f,
                modelScreen.x + bboxW / 2f, modelScreen.y + bboxH / 2f
            )
            if (!bboxAbs.contains(modelScreen.x, modelScreen.y) && ws.anchor == null) {
                model.isVisible = false
                continue
            }

            // 12. Stability counter (for tap-to-anchor eligibility)
            val posStable = ws.lastCenter == null || dist(ws.lastCenter!!, center) < POS_STABLE_M
            val rotStable = ws.lastRot == null || angleBetween(ws.lastRot!!, planeRot) < ROT_STABLE_DEG
            if (posStable && rotStable) {
                ws.stableFrames++
                if (ws.stableFrames >= STABLE_FRAMES_REQUIRED) ws.isReadyToAnchor = true
            } else {
                ws.stableFrames = 0
                ws.isReadyToAnchor = false
            }
            ws.lastCenter = center; ws.lastRot = planeRot

            // 13. Auto-upgrade anchor when quality improves
            val confirmed = confirmDetection(cv.circularity, ratio, cv.isFound)
            if (confirmed) {
                val quality = AnchorQuality(cv.circularity, ratio)
                val existingQ = ws.anchorQuality
                if (existingQ == null || quality.score() > existingQ.score() + ANCHOR_UPGRADE_MARGIN) {
                    ws.anchor?.detach()
                    ws.anchor = arSceneView.session?.createAnchor(
                        Pose(
                            floatArrayOf(renderPos.x, renderPos.y, renderPos.z),
                            floatArrayOf(bestRot.x, bestRot.y, bestRot.z, bestRot.w)
                        )
                    )
                    ws.anchorQuality = quality
                    ws.planeRight = planeRight
                    ws.planeUp = planeUp
                    ws.isFrozen = false
                    ws.missFrames = 0
                }
            }

            // 14. Smooth render
            val alpha = dynamicAlpha(model.position, renderPos)
            model.position = mix(model.position, renderPos, alpha)
            model.quaternion = slerp(model.quaternion, bestRot, alpha)
            if (dist(model.position, renderPos) > 0.02f) anyMotionThisFrame = true
        }

        handleMissedModels(claimed, now)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Apply manual pose along plane axes
    // ─────────────────────────────────────────────────────────────────────────
    private fun applyManualPose(model: Node, ws: WheelState) {
        val ap = ws.anchor?.pose ?: return
        val basePos = Float3(ap.tx(), ap.ty(), ap.tz())
        val baseRot = Quaternion(ap.qx(), ap.qy(), ap.qz(), ap.qw())

        val finalPos = basePos +
            ws.planeRight * ws.manualOffsetRight +
            ws.planeUp * ws.manualOffsetUp

        val rotH = Quaternion.fromAxisAngle(ws.planeUp, ws.manualRotH)
        val rotV = Quaternion.fromAxisAngle(ws.planeRight, ws.manualRotV)
        val finalRot = normalize(rotH * rotV * baseRot)

        model.position = finalPos
        model.quaternion = finalRot
    }

    private fun renderLockedModels() {
        for (m in markerlessActiveModels) {
            val ws = wheelStates[m] ?: continue
            if (!ws.isManuallyLocked) continue
            applyManualPose(m, ws)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Missed models
    // ─────────────────────────────────────────────────────────────────────────
    private fun handleMissedModels(claimed: Set<Node>, now: Long) {
        for (m in markerlessActiveModels) {
            if (m in claimed) continue
            val ws = wheelStates[m] ?: continue

            if (ws.anchor != null) {
                if (!ws.isFrozen && !ws.isManuallyLocked) {
                    ws.missFrames++
                    if (ws.missFrames >= ANCHOR_MISS_LIMIT) {
                        ws.isFrozen = true
                        m.isVisible = false
                    }
                }
                // Keep frozen model at anchor pose (visible only if manually unlocked)
                if (!ws.isManuallyLocked) {
                    val ap = ws.anchor?.pose ?: continue
                    m.position = Float3(ap.tx(), ap.ty(), ap.tz())
                    m.quaternion = Quaternion(ap.qx(), ap.qy(), ap.qz(), ap.qw())
                }
            } else {
                if (now - ws.lastDetectionTime > UNANCHORED_TIMEOUT_MS) {
                    m.isVisible = false
                    if (m == selectedModel) {
                        selectedModel = null
                        mainHandler.post { onShowAdjustmentUI?.invoke(false) }
                    }
                    resetUnanchoredState(ws)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Snap helper: anchored/frozen > free
    // ─────────────────────────────────────────────────────────────────────────
    private fun snapToModel(center: Float3, claimed: Set<Node>, minGap: Float): Node? {
        val anchored = markerlessActiveModels
            .filter { it !in claimed && wheelStates[it]?.anchor != null }
            .minByOrNull { dist(it.position, center) }
        if (anchored != null && dist(anchored.position, center) < minGap) return anchored

        val free = markerlessActiveModels
            .filter { it !in claimed && wheelStates[it]?.anchor == null }
            .minByOrNull { dist(it.position, center) }
        if (free != null && dist(free.position, center) < minGap) return free
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Outlier removal: discard points > 2σ from centroid
    // ─────────────────────────────────────────────────────────────────────────
    private fun removeOutliers(pts: List<Float3>): List<Float3> {
        if (pts.size <= 3) return pts
        val centroid = pts.average3()
        val dists = pts.map { dist(it, centroid) }
        val mean = dists.average().toFloat()
        val sd = sqrt(dists.map { (it - mean) * (it - mean) }.average()).toFloat()
        val threshold = mean + 2f * sd
        return pts.filter { dist(it, centroid) <= threshold }.ifEmpty { pts }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Adaptive rotation average (6..20 window)
    // ─────────────────────────────────────────────────────────────────────────
    private fun adaptiveRotAverage(ws: WheelState): Quaternion {
        if (ws.rotHistory.isEmpty()) return Quaternion()
        if (ws.rotHistory.size < ROT_RECENT_SIZE) return averageQuaternions(ws.rotHistory.toList())

        val fullAvg = averageQuaternions(ws.rotHistory.toList())
        val recentAvg = averageQuaternions(ws.rotHistory.takeLast(ROT_RECENT_SIZE))
        val diverge = angleBetween(fullAvg, recentAvg)

        return if (diverge > ROT_DIVERGE_DEG) {
            // Reduce window, grow by 1 each subsequent call until back to 20
            if (ws.rotWindowSize > ROT_RECENT_SIZE) ws.rotWindowSize--
            else ws.rotWindowSize = ROT_RECENT_SIZE
            averageQuaternions(ws.rotHistory.takeLast(ws.rotWindowSize))
        } else {
            if (ws.rotWindowSize < ROT_HISTORY_SIZE) ws.rotWindowSize++
            averageQuaternions(ws.rotHistory.takeLast(ws.rotWindowSize))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hit points (center + donut ring)
    // ─────────────────────────────────────────────────────────────────────────
    private fun collectHitPoints(
        frame: Frame, cv: RefinedResult, bbox: RectF, vw: Float, vh: Float
    ): List<Float3> {
        val pts = mutableListOf<Float3>()
        frame.hitTest(cv.cx, cv.cy)
            .firstOrNull { it.trackable is Plane || it.trackable is Point }
            ?.let { pts.add(Float3(it.hitPose.tx(), it.hitPose.ty(), it.hitPose.tz())) }
        val radius = if (cv.isFound && cv.width > 0f && cv.height > 0f)
            max(cv.width, cv.height) / 2f
        else max(bbox.width() * vw, bbox.height() * vh) * DONUT_RADIUS_FALLBACK / 2f
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
    // Plane normal facing camera
    // ─────────────────────────────────────────────────────────────────────────
    private fun planeNormal(pts: List<Float3>, center: Float3, camPos: Float3): Float3 {
        if (pts.size < 3) return normalize(camPos - center)
        var accum = Float3(0f, 0f, 0f)
        for (i in pts.indices) accum += cross(pts[i] - center, pts[(i + 1) % pts.size] - center)
        val len = length(accum)
        if (len < 1e-6f || len.isNaN()) return normalize(camPos - center)
        val n = normalize(accum)
        return if (dot(n, normalize(camPos - center)) < 0f) -n else n
    }

    // ─────────────────────────────────────────────────────────────────────────
    // lookRotationForward: build rotation so model +Y aligns with normal
    // (ModelManager sets wheel front = +Y, so +Y must point along plane normal)
    // ─────────────────────────────────────────────────────────────────────────
    private fun lookRotationForward(normal: Float3): Quaternion {
        val yAxis = normalize(normal)
        val ref = if (abs(dot(yAxis, Float3(0f, 0f, 1f))) < 0.99f) Float3(0f, 0f, 1f)
            else Float3(1f, 0f, 0f)
        val xAxis = normalize(cross(ref, yAxis))
        val zAxis = normalize(cross(yAxis, xAxis))
        // Matrix: col0=xAxis, col1=yAxis, col2=zAxis
        val tr = xAxis.x + yAxis.y + zAxis.z
        return when {
            tr > 0f -> {
                val s = sqrt(tr + 1f) * 2f
                Quaternion((yAxis.z - zAxis.y) / s, (zAxis.x - xAxis.z) / s, (xAxis.y - yAxis.x) / s, 0.25f * s)
            }
            xAxis.x > yAxis.y && xAxis.x > zAxis.z -> {
                val s = sqrt(1f + xAxis.x - yAxis.y - zAxis.z) * 2f
                Quaternion(0.25f * s, (xAxis.y + yAxis.x) / s, (zAxis.x + xAxis.z) / s, (yAxis.z - zAxis.y) / s)
            }
            yAxis.y > zAxis.z -> {
                val s = sqrt(1f + yAxis.y - xAxis.x - zAxis.z) * 2f
                Quaternion((xAxis.y + yAxis.x) / s, 0.25f * s, (yAxis.z + zAxis.y) / s, (zAxis.x - xAxis.z) / s)
            }
            else -> {
                val s = sqrt(1f + zAxis.z - xAxis.x - yAxis.y) * 2f
                Quaternion((zAxis.x + xAxis.z) / s, (yAxis.z + zAxis.y) / s, 0.25f * s, (xAxis.y - yAxis.x) / s)
            }
        }
    }

private fun buildPlaneRight(normal: Float3): Float3 {
        val up = if (abs(dot(normal, Float3(0f, 1f, 0f))) > 0.99f) Float3(0f, 0f, 1f)
            else Float3(0f, 1f, 0f)
        return normalize(cross(up, normal))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Confirmation
    // ─────────────────────────────────────────────────────────────────────────
    private fun confirmDetection(circularity: Float, bboxRatio: Float, hasOpenCV: Boolean): Boolean {
        val circOk  = if (hasOpenCV) circularity >= MIN_CIRCULARITY else bboxRatio >= MIN_CIRCULARITY
        return circOk && bboxRatio >= MIN_BBOX_RATIO
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Model pool + tap handler
    // ─────────────────────────────────────────────────────────────────────────
    private fun getOrCreateMarkerlessModel(): Node {
        markerlessActiveModels.firstOrNull { !it.isVisible && wheelStates[it]?.anchor == null }
            ?.let { m ->
                val ws = wheelStates.getOrPut(m) { WheelState() }
                resetUnanchoredState(ws)
                return m
            }
        return modelManager.createNewModel(modelPath, coroutineScope).also { m ->
            m.isVisible = false
            arSceneView.addChildNode(m)
            markerlessActiveModels.add(m)
            wheelStates[m] = WheelState()
            wireTapHandler(m)
        }
    }

    private fun wireTapHandler(m: Node) {
        m.onSingleTapConfirmed = { _ ->
            val ws = wheelStates[m]
            if (ws != null) {
                // Place anchor immediately on tap
                if (ws.anchor == null && ws.isReadyToAnchor) {
                    val pos = ws.posHistory.takeLast(POS_RENDER_SIZE)
                        .takeIf { it.isNotEmpty() }?.average3() ?: m.position
                    val rot = adaptiveRotAverage(ws)
                    ws.anchor = arSceneView.session?.createAnchor(
                        Pose(floatArrayOf(pos.x, pos.y, pos.z),
                            floatArrayOf(rot.x, rot.y, rot.z, rot.w))
                    )
                }
                if (ws.anchor != null) {
                    ws.isManuallyLocked = true
                    ws.isFrozen = false
                    m.isVisible = true
                    selectedModel = m
                    onShowAdjustmentUI?.invoke(true)   // main thread ✅
                }
            }
            true
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bbox mode
    // ─────────────────────────────────────────────────────────────────────────
    private fun getModeBboxCount(): Int {
        if (bboxCountHistory.isEmpty()) return 1
        val sorted = bboxCountHistory.sorted()
        return sorted[sorted.size / 2].coerceAtLeast(1)
    }

    private fun resetUnanchoredState(ws: WheelState) {
        ws.posHistory.clear()
        ws.rotHistory.clear()
        ws.stableFrames = 0
        ws.isReadyToAnchor = false
        ws.detectionHits = 0
        ws.lastCenter = null
        ws.lastRot = null
        ws.missFrames = 0
        ws.manualOffsetRight = 0f
        ws.manualOffsetUp = 0f
        ws.manualRotH = 0f
        ws.manualRotV = 0f
        ws.rotWindowSize = ROT_HISTORY_SIZE
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dynamic FPS — inference and hitTest updated independently
    // ─────────────────────────────────────────────────────────────────────────
    private fun updateDynamicFPS(totalMs: Long) {
        // Inference: allow slower rate to reduce heat
        infInterval = (totalMs * 2L).coerceIn(INF_MIN_INT_MS, INF_MAX_INT_MS)
        // HitTest: keep smooth, back off when idle
        htInterval = when {
            totalMs > HT_TARGET_MS -> (totalMs + 10L).coerceIn(HT_MIN_INT_MS, HT_MAX_INT_MS)
            !anyMotionThisFrame -> (htInterval + 8L).coerceAtMost(HT_MAX_INT_MS)
            else -> HT_MIN_INT_MS
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

    private fun averageQuaternions(qs: List<Quaternion>): Quaternion {
        if (qs.isEmpty()) return Quaternion()
        var avg = qs[0]
        for (i in 1 until qs.size) avg = slerp(avg, qs[i], 1f / (i + 1f))
        return avg
    }

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
            else for (r in 0 until h) {
                yp.position(r * stride)
                yp.get(buf, r * w, w)
            }
            FrameYData(buf, w, h, w)
        } catch (e: Exception) { null } finally { img.close() }
    }
}