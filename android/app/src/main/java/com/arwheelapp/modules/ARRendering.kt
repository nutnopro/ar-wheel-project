package com.arwheelapp.modules

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.arwheelapp.processor.FrameConverter
import com.arwheelapp.processor.OnnxRuntimeHandler
import com.arwheelapp.utils.*
import com.google.ar.core.*
import dev.romainguy.kotlin.math.*
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.utils.worldToScreen
import io.github.sceneview.node.Node
import kotlin.math.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ARRendering(
    private val context: Context,
    private val onnxOverlayView: OnnxOverlayView,
    private val arSceneView: ARSceneView,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "ARRendering"

        // Inference timing
        private const val BASE_FAST_INTERVAL = 40L          // ~25 FPS when moving
        private const val BASE_SLOW_INTERVAL = 250L         // ~4 FPS when idle
        private const val JUMP_THRESHOLD = 0.05f            // 5 cm → switch to fast mode
        private const val PROCESS_BUFFER_MS = 15L

        // Hit-test
        private const val RING_POINTS = 8                   // 8 ring points + 1 center = 9 total
        private const val RING_RADIUS_FRACTION = 0.64f      // ring radius as fraction of half-bbox

        // Depth
        private const val DEPTH_OUTLIER_THRESHOLD = 0.10f   // discard points > 10 cm from median depth

        // Stability / history
        private const val MAX_BBOX_HISTORY = 15
        private const val QUICK_OVERRIDE_FRAMES = 5
        private const val HISTORY_MAX_POSES = 10            // keep N best poses per tracker
        private const val OUTLIER_ANGLE_DEG = 25f
        private const val OUTLIER_POS_DIST = 0.4f
        private const val FRAMES_TO_ANCHOR = 6              // stable frames required before anchoring
        private const val ANCHOR_CIRCULARITY_THRESHOLD = 0.95f  // wheel must look round enough
        private const val DETECTION_TIMEOUT_MS = 1200L

        // Lerp
        private const val MIN_DIST = 0.02f
        private const val MAX_DIST = 0.3f
        private const val MIN_ALPHA = 0.04f
        private const val MAX_ALPHA = 0.6f
        // Manual adjustment: nudge step in metres
        const val ADJUST_STEP_FINE = 0.003f     // 3 mm — fine
        const val ADJUST_STEP_MEDIUM = 0.008f   // 8 mm — medium
        const val ADJUST_STEP_COARSE = 0.020f   // 20 mm — coarse

        // Screen-space tap radius to count as "hit" on a model (dp-ish pixels)
        private const val TAP_HIT_RADIUS_PX = 120f
    }

    // ── Dependencies ─────────────────────────────────────────────────────
    private val modelManager = ModelManager(arSceneView)
    private val frameConverter = FrameConverter()
    private val onnxRuntimeHandler = OnnxRuntimeHandler(context)


    private var previousMode: ARMode? = null
    private var lastInferenceTime = 0L
    private var targetInferenceInterval = BASE_FAST_INTERVAL
    private var currentInferenceInterval = BASE_FAST_INTERVAL
    private var latestInferenceDurationMs = 0L

    // ── Model pools ───────────────────────────────────────────────────────
    private val modelPool = mutableListOf<Node>()
    private val augmentedImageMap = mutableMapOf<AugmentedImage, AugmentedImageNode>()
    private val markerlessActiveModels = mutableListOf<Node>()
    private val modelStates = mutableMapOf<Node, ModelState>()

    // ── Buffers ───────────────────────────────────────────────────────────
    private var yPlaneBufferA: ByteArray? = null
    private var yPlaneBufferB: ByteArray? = null
    private var useBufferA = true
    private val bboxCountHistory = mutableListOf<Int>()

    // ── Detection state ───────────────────────────────────────────────────
    @Volatile private var latestProcessedDetections: List<ProcessedDetection> = emptyList()
    @Volatile private var isNewDetectionAvailable = false

    // ── Config ────────────────────────────────────────────────────────────
    @Volatile private var snapThreshold = 0.4572f
    @Volatile private var modelPath = "models/wheel1.glb"   // !!Change to UI

    // ── Manual adjustment state ───────────────────────────────────────────
    @Volatile private var selectedModel: Node? = null

    // ═════════════════════════════════════════════════════════════════════
    // Public: Manual adjustment API
    // ═════════════════════════════════════════════════════════════════════
    fun findModelAtScreen(screenX: Float, screenY: Float): Node? {
        var best: Node? = null
        var bestDist = TAP_HIT_RADIUS_PX
        for (model in markerlessActiveModels) {
            if (!model.isVisible) continue
            val screenPt = arSceneView.view.worldToScreen(model.position)
            val d = hypot(screenPt.x - screenX, screenPt.y - screenY)
            if (d < bestDist) { bestDist = d; best = model }
        }
        return best
    }

    fun selectModel(node: Node) {
        selectedModel = node
        // Highlight: slightly scale up so user sees selection feedback
        node.scale = node.scale * 1.04f
    }

    fun deselectModel() {
        selectedModel?.let { node ->
            // Remove highlight scale
            node.scale = node.scale / 1.04f
        }
        selectedModel = null
    }

    fun adjustSelectedModel(dx: Float = 0f, dy: Float = 0f, dz: Float = 0f) {
        val model = selectedModel ?: return
        val state = modelStates[model] ?: return
        val cameraPose = arSceneView.frame?.camera?.pose ?: return

        // Build camera-space offset vector and rotate it into world space
        val camRight = Float3(cameraPose.xAxis[0], cameraPose.xAxis[1], cameraPose.xAxis[2])
        val camUp = Float3(cameraPose.yAxis[0], cameraPose.yAxis[1], cameraPose.yAxis[2])
        val camForward = Float3(-cameraPose.zAxis[0], -cameraPose.zAxis[1], -cameraPose.zAxis[2])

        val worldDelta = camRight * dx + camUp * dy + camForward * dz
        state.manualOffset = state.manualOffset + worldDelta

        // Apply immediately so the model moves without waiting for next frame
        model.position = resolvePosition(state) + state.manualOffset
    }

    fun resetSelectedModelOffset() {
        val model = selectedModel ?: return
        val state = modelStates[model] ?: return
        state.manualOffset = Float3(0f, 0f, 0f)
        model.position = resolvePosition(state)
    }

    // ═════════════════════════════════════════════════════════════════════
    // Render entry
    // ═════════════════════════════════════════════════════════════════════
    fun render(arSceneView: ARSceneView, frame: Frame, currentMode: ARMode, deviceRotation: Int) {
        if (previousMode != currentMode) {
            handleModeSwitch()
            previousMode = currentMode
        }
        when (currentMode) {
            ARMode.MARKER_BASED -> processMarkerBased(arSceneView, frame)
            ARMode.MARKERLESS -> {
                processMarkerlessInference(frame, deviceRotation)
                processMarkerlessHitTest(arSceneView, frame)
            }
        }
    }

    // ── Mode switch ───────────────────────────────────────────────────────
    private fun handleModeSwitch() {
        selectedModel = null
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
            modelStates.values.forEach { it.anchor?.detach() }
            modelStates.clear()
            markerlessActiveModels.clear()
            onnxOverlayView.clear()
            bboxCountHistory.clear()
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // MARKER-BASED
    // ═════════════════════════════════════════════════════════════════════
    private fun processMarkerBased(arSceneView: ARSceneView, frame: Frame) {
        for (image in frame.getUpdatedTrackables(AugmentedImage::class.java)) {
            when (image.trackingState) {
                TrackingState.TRACKING -> {
                    val node = augmentedImageMap.getOrPut(image) {
                        AugmentedImageNode(arSceneView.engine, image).apply {
                            addChildNode(getOrCreateModel(modelPath).apply { isVisible = true })
                            arSceneView.addChildNode(this)
                        }
                    }
                    node.isVisible = (image.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING)
                }
                TrackingState.STOPPED -> augmentedImageMap.remove(image)?.let {
                    arSceneView.removeChildNode(it)
                    it.destroy()
                }
                TrackingState.PAUSED -> augmentedImageMap[image]?.isVisible = false
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // MARKERLESS — inference
    // ═════════════════════════════════════════════════════════════════════
    private fun processMarkerlessInference(frame: Frame, deviceRotation: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastInferenceTime < currentInferenceInterval) return
        lastInferenceTime = now

        try {
            val tensor = frameConverter.convertFrameToTensor(frame, deviceRotation)
            val viewW = arSceneView.width.toFloat()
            val viewH = arSceneView.height.toFloat()
            val yData = extractYPlaneData(frame)

            val t0 = SystemClock.uptimeMillis()

            onnxRuntimeHandler.runOnnxInferenceAsync(tensor, deviceRotation, yData, viewW, viewH) { results ->
                latestInferenceDurationMs = SystemClock.uptimeMillis() - t0
                latestProcessedDetections = results
                isNewDetectionAvailable = true
                onnxOverlayView.updateDetections(results.map { it.detection })
            }
        } catch (e: Exception) { Log.e(TAG, "Inference error", e) }
    }

    // ═════════════════════════════════════════════════════════════════════
    // MARKERLESS — hit-test & pose tracking
    // ═════════════════════════════════════════════════════════════════════
    private fun processMarkerlessHitTest(arSceneView: ARSceneView, frame: Frame) {
        if (!isNewDetectionAvailable) return
        isNewDetectionAvailable = false

        if (frame.camera.trackingState != TrackingState.TRACKING) return

        val hitStart = SystemClock.uptimeMillis()

        val cameraPos = frame.camera.pose.let { Float3(it.tx(), it.ty(), it.tz()) }
        val viewW = arSceneView.width.toFloat()
        val viewH = arSceneView.height.toFloat()
        var maxShift = 0f

        bboxCountHistory.add(latestProcessedDetections.size)
        if (bboxCountHistory.size > MAX_BBOX_HISTORY) bboxCountHistory.removeAt(0)

        val data = latestProcessedDetections.let { list ->
            if (list.size > getTargetBBoxCount())
                list.sortedByDescending { it.detection.confidence }.take(getTargetBBoxCount())
            else list
        }

        val claimedModels = mutableSetOf<Node>()

        for (pd in data) {
            val det = pd.detection
            val cvRes = pd.cvResult
            val bbox = det.boundingBox

            val allPoses = collectHitPoses(frame, cvRes, bbox, viewW, viewH)
            if (allPoses.isEmpty()) continue

            val depthOk = filterDepthOutliers(allPoses, cameraPos)
            if (depthOk.isEmpty()) continue

            val trueCenter = allPoses.firstOrNull()?.let {
                Float3(it.tx(), it.ty(), it.tz()) }
                ?: depthOk.map { Float3(it.tx(), it.ty(), it.tz()) }.average3()

            val pts3D = depthOk.map { Float3(it.tx(), it.ty(), it.tz()) }

            val normal = fitPlaneNormalSVD(pts3D, trueCenter, cameraPos)
            val planeRot = buildRotationFromNormal(normal)
            val offsetRot = Quaternion.fromAxisAngle(Float3(1f, 0f, 0f), 90f)
            val calcRot = if (cvRes.isFound) {
                val zRot = Quaternion.fromAxisAngle(Float3(0f, 0f, 1f), cvRes.angle)
                planeRot * zRot * offsetRot
            } else planeRot * offsetRot

            val ellipseTiltDiffDeg: Float = if (cvRes.isFound && cvRes.width > 0f && cvRes.height > 0f) {
                val major = max(cvRes.width, cvRes.height)
                val minor = min(cvRes.width, cvRes.height)
                val eTilt = acos(minor / major)
                val sCos     = dot(normal, normalize(cameraPos - trueCenter)).coerceIn(-1f, 1f)
                val sTilt = acos(sCos)
                Math.toDegrees(abs(eTilt - sTilt).toDouble()).toFloat()
            } else 0f

            val circ = cvRes.circularity

            val closest = markerlessActiveModels.asSequence()
                .filter { it !in claimedModels }
                .minByOrNull { dist(it.position, trueCenter) }
            val model = if (closest != null && dist(closest.position, trueCenter) < snapThreshold * 1.2f)
                closest
            else getAvailableMarkerlessModel(claimedModels)

            claimedModels.add(model)
            val state = modelStates.getOrPut(model) { ModelState() }

            // Bbox drift check
            val absBbox = RectF(bbox.left * viewW, bbox.top * viewH, bbox.right * viewW, bbox.bottom * viewH)
            val checkPt = if (state.posHistory.isNotEmpty()) state.bestPos else trueCenter
            val screenPt = arSceneView.view.worldToScreen(checkPt)
            if (!absBbox.contains(screenPt.x, screenPt.y)) {
                if (state.posHistory.isEmpty()) continue
                if (++state.driftFrames > 15) { resetModelState(state); continue }
            } else state.driftFrames = 0

            state.lastDetectionTime = hitStart

            if (++state.detectionHits >= 3) model.isVisible = true

            val shift = state.lastStablePos?.let { dist(it, trueCenter) } ?: Float.MAX_VALUE
            maxShift = max(maxShift, shift)
            state.lastStablePos = trueCenter

            val angleDiff = if (state.rotHistory.isNotEmpty()) angleBetween(state.bestRot, calcRot) else 0f
            val posDiff = if (state.posHistory.isNotEmpty()) dist(state.bestPos, trueCenter) else 0f
            val isOutlier = (state.posHistory.isNotEmpty() && (angleDiff > OUTLIER_ANGLE_DEG || posDiff > OUTLIER_POS_DIST)) || ellipseTiltDiffDeg > 22f

            if (!isOutlier) {
                state.posHistory.add(TrackedPos(trueCenter, circ))
                state.posHistory.sortByDescending { it.circularity }
                if (state.posHistory.size > HISTORY_MAX_POSES) state.posHistory.removeLast()
                state.bestPos = state.posHistory[0].pos

                state.rotHistory.add(TrackedRot(calcRot, circ))
                state.rotHistory.sortByDescending { it.circularity }
                if (state.rotHistory.size > HISTORY_MAX_POSES) state.rotHistory.removeLast()
                state.bestRot = state.rotHistory[0].rot

                if (state.anchor == null) {
                    if (circ >= ANCHOR_CIRCULARITY_THRESHOLD) state.stableFrames++
                    else state.stableFrames = 0
                    if (state.stableFrames >= FRAMES_TO_ANCHOR) {
                        state.anchor = arSceneView.session?.createAnchor(
                            Pose(
                                floatArrayOf(state.bestPos.x, state.bestPos.y, state.bestPos.z),
                                floatArrayOf(state.bestRot.x, state.bestRot.y, state.bestRot.z, state.bestRot.w)
                            )
                        )
                    }
                }
            } else state.stableFrames = 0

            // ── Skip lerp update while user is manually adjusting this model ──
            if (model == selectedModel) continue

            val renderPos = resolvePosition(state)
            val renderRot = resolveRotation(state, calcRot)

            val alpha = dynamicAlpha(model.position, renderPos)
            model.position = mix(model.position, renderPos + state.manualOffset, alpha)
            model.quaternion = slerp(model.quaternion, renderRot, alpha)
        }

        hideStaleModels(claimedModels, hitStart)

        val totalMs = latestInferenceDurationMs + (SystemClock.uptimeMillis() - hitStart)
        targetInferenceInterval = if (maxShift > JUMP_THRESHOLD || bboxCountHistory.size < MAX_BBOX_HISTORY)
            BASE_FAST_INTERVAL
        else min(BASE_SLOW_INTERVAL, targetInferenceInterval + 10L)
        currentInferenceInterval = max(targetInferenceInterval, totalMs + PROCESS_BUFFER_MS)
    }

    // ═════════════════════════════════════════════════════════════════════
    // Pose resolution helpers
    // ═════════════════════════════════════════════════════════════════════
    private fun resolvePosition(state: ModelState): Float3 =
        state.anchor?.pose?.let { Float3(it.tx(), it.ty(), it.tz()) }
            ?: if (state.posHistory.isNotEmpty()) state.bestPos else Float3(0f, 0f, 0f)

    private fun resolveRotation(state: ModelState, fallback: Quaternion): Quaternion =
        state.anchor?.pose?.let { Quaternion(it.qx(), it.qy(), it.qz(), it.qw()) }
            ?: if (state.rotHistory.isNotEmpty()) state.bestRot else fallback

    // ═════════════════════════════════════════════════════════════════════
    // Plane fitting
    // ═════════════════════════════════════════════════════════════════════
    private fun fitPlaneNormalSVD(points: List<Float3>, center: Float3, cameraPos: Float3): Float3 {
        if (points.size < 3) return normalize(cameraPos - center)

        var nx = 0f; var ny = 0f; var nz = 0f
        val n = points.size
        for (i in 0 until n) {
            val c = points[i] - center
            val next = points[(i + 1) % n] - center
            nx += (c.y - next.y) * (c.z + next.z)
            ny += (c.z - next.z) * (c.x + next.x)
            nz += (c.x - next.x) * (c.y + next.y)
        }

        val raw = Float3(nx, ny, nz)
        val len = length(raw)
        if (len < 1e-6f || len.isNaN()) return normalize(cameraPos - center)
        val normal = normalize(raw)

        return if (dot(normal, normalize(cameraPos - center)) < 0f) -normal
        else normal
    }

    private fun buildRotationFromNormal(normal: Float3): Quaternion {
        val f = normalize(normal)
        val up = if (abs(dot(f, Float3(0f, 1f, 0f))) > 0.98f) Float3(0f, 0f, 1f)
        else Float3(0f, 1f, 0f)
        val r = normalize(cross(up, f))
        val u2 = cross(f, r)
        val tr = r.x + u2.y + f.z

        return when {
            tr > 0f -> {
                val s = sqrt(tr + 1f) * 2f
                Quaternion((u2.z - f.y)/s, (f.x - r.z)/s, (r.y - u2.x)/s, 0.25f*s)
            }
            r.x > u2.y && r.x > f.z -> {
                val s = sqrt(1f + r.x - u2.y - f.z) * 2f; Quaternion(0.25f*s, (u2.x+r.y)/s, (f.x+r.z)/s, (u2.z-f.y)/s)
            }
            u2.y > f.z -> {
                val s = sqrt(1f + u2.y - r.x - f.z) * 2f; Quaternion((u2.x+r.y)/s, 0.25f*s, (f.y+u2.z)/s, (f.x-r.z)/s)
            }
            else -> {
                val s = sqrt(1f + f.z - r.x - u2.y) * 2f; Quaternion((f.x+r.z)/s, (f.y+u2.z)/s, 0.25f*s, (r.y-u2.x)/s)
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Hit-test helpers
    // ═════════════════════════════════════════════════════════════════════
    private fun collectHitPoses(frame: Frame, cvRes: RefinedResult, bbox: RectF, viewW: Float, viewH: Float): List<Pose> {
        val poses = mutableListOf<Pose>()
        frame.hitTest(cvRes.cx, cvRes.cy).firstOrNull { it.trackable is Plane || it.trackable is Point }
            ?.let { poses.add(it.hitPose) }

        val rx = if (cvRes.isFound && cvRes.width  > 0f) cvRes.width  / 2f
        else bbox.width()  * viewW * RING_RADIUS_FRACTION / 2f
        val ry = if (cvRes.isFound && cvRes.height > 0f) cvRes.height / 2f
        else bbox.height() * viewH * RING_RADIUS_FRACTION / 2f
        val aRad = Math.toRadians(cvRes.angle.toDouble()).toFloat()
        val cosA = cos(aRad); val sinA = sin(aRad)

        for (i in 0 until RING_POINTS) {
            val t  = 2f * kotlin.math.PI.toFloat() * i / RING_POINTS
            val lx = rx * cos(t)
            val ly = ry * sin(t)

            frame.hitTest(cvRes.cx + lx * cosA - ly * sinA, cvRes.cy + lx * sinA + ly * cosA)
                .firstOrNull { it.trackable is Plane || it.trackable is Point }
                ?.let { poses.add(it.hitPose) }
        }

        return poses
    }

    private fun filterDepthOutliers(poses: List<Pose>, cam: Float3): List<Pose> {
        if (poses.size <= 2) return poses
        val depths = poses.map { dist(Float3(it.tx(), it.ty(), it.tz()), cam) }.sorted()
        val median = depths[depths.size / 2]
        return poses.filter { abs(dist(Float3(it.tx(), it.ty(), it.tz()), cam) - median) <= DEPTH_OUTLIER_THRESHOLD }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Model lifecycle
    // ═════════════════════════════════════════════════════════════════════
    private fun getOrCreateModel(path: String): Node =
        modelPool.find { it.parent == null || !it.isVisible }?.apply { isVisible = true }
            ?: modelManager.createNewModel(path, coroutineScope).also { modelPool.add(it) }

    private fun getAvailableMarkerlessModel(claimed: Set<Node>): Node {
        markerlessActiveModels.find { it !in claimed }?.let { return it }
        return modelManager.createNewModel(modelPath, coroutineScope).also { m ->
            m.isVisible = false
            arSceneView.addChildNode(m)
            markerlessActiveModels.add(m)
            modelStates[m] = ModelState()
        }
    }

    private fun resetModelState(state: ModelState) {
        state.anchor?.detach()
        state.anchor = null
        state.posHistory.clear()
        state.rotHistory.clear()
        state.driftFrames = 0
        state.stableFrames = 0
        state.detectionHits = 0
        state.manualOffset = Float3(0f, 0f, 0f)
    }

    private fun hideStaleModels(claimed: Set<Node>, now: Long) {
        for (m in markerlessActiveModels) {
            if (m in claimed) continue
            val s = modelStates[m] ?: continue
            if (now - s.lastDetectionTime > DETECTION_TIMEOUT_MS) {
                m.isVisible = false
                if (m == selectedModel) selectedModel = null
                resetModelState(s)
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    // Math
    // ═════════════════════════════════════════════════════════════════════
    private fun dynamicAlpha(cur: Float3, tgt: Float3): Float {
        val t = ((dist(cur, tgt) - MIN_DIST) / (MAX_DIST - MIN_DIST)).coerceIn(0f, 1f)
        return MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * t
    }

    private fun angleBetween(q1: Quaternion, q2: Quaternion) =
        Math.toDegrees(2.0 * acos(abs(dot(q1, q2)).coerceIn(0f, 1f).toDouble())).toFloat()

    private fun dist(a: Float3, b: Float3) = length(a - b)
    // private fun dist(p1: Pose, p2: Pose) = sqrt((p1.tx() - p2.tx()).pow(2) + (p1.ty() - p2.ty()).pow(2) + (p1.tz() - p2.tz()).pow(2))

    private fun List<Float3>.average3() = Float3(
        map { it.x }.average().toFloat(),
        map { it.y }.average().toFloat(),
        map { it.z }.average().toFloat()
    )

    // ═════════════════════════════════════════════════════════════════════
    // Y-plane
    // ═════════════════════════════════════════════════════════════════════
    private fun extractYPlaneData(frame: Frame): FrameYData? {
        val img = try { frame.acquireCameraImage() } catch (e: Exception) { return null }
        return try {
            val yPlane = img.planes[0].buffer.apply { rewind() }
            val w = img.width
            val h = img.height
            val stride = img.planes[0].rowStride
            val size = w * h

            if (yPlaneBufferA == null || yPlaneBufferA!!.size != size) {
                yPlaneBufferA = ByteArray(size)
                yPlaneBufferB = ByteArray(size)
            }
            val buf = if (useBufferA) yPlaneBufferA!! else yPlaneBufferB!!
            useBufferA = !useBufferA

            if (stride == w) yPlane.get(buf)
            else for (row in 0 until h) {
                yPlane.position(row * stride)
                yPlane.get(buf, row * w, w)
            }

            FrameYData(buf, w, h, w)
        } catch (e: Exception) {
            Log.e(TAG, "Y-plane error", e)
            null
        } finally { img.close() }
    }

    private fun getTargetBBoxCount(): Int {
        if (bboxCountHistory.isEmpty()) return 0
        if (bboxCountHistory.size >= QUICK_OVERRIDE_FRAMES) {
            val last = bboxCountHistory.takeLast(QUICK_OVERRIDE_FRAMES)
            if (last.all { it == last.first() }) return last.first()
        }
        return bboxCountHistory.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 0
    }

    // ═════════════════════════════════════════════════════════════════════
    // Public API
    // ═════════════════════════════════════════════════════════════════════
    fun updateNewModel(path: String) {
        modelPath = path
        modelPool.forEach { modelManager.changeModel(it, path, coroutineScope) }
    }

    fun updateModelSize(sizeInch: Float) {
        val cm = sizeInch * 2.54f
        snapThreshold = cm / 100f
        modelPool.forEach { modelManager.changeModelSize(it, cm / 45.72f) }
    }

    fun clear() {
        selectedModel = null
        modelPool.forEach { it.destroy() }
        modelPool.clear()
        augmentedImageMap.values.forEach { it.destroy() }
        augmentedImageMap.clear()
        modelStates.values.forEach { it.anchor?.detach() }
        markerlessActiveModels.clear()
        modelStates.clear()
        onnxOverlayView.clear()
    }

    fun setupMarkerDatabase(session: Session, markerSize: Float = 0.15f) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "⏳ Loading marker database…")
                val db = AugmentedImageDatabase(session)
                val assets = context.assets
                assets.list("markers")
                    ?.filter { it.endsWith(".jpg", true) || it.endsWith(".png", true) }
                    ?.forEach { fn ->
                        try { assets.open("markers/$fn").use { s ->
                            db.addImage(fn.substringBeforeLast("."), BitmapFactory.decodeStream(s), markerSize)
                            Log.d(TAG, "Marker: $fn ✅")
                        } } catch (e: Exception) {
                            Log.e(TAG, "Marker load failed: $fn", e)
                        }
                    }
                withContext(Dispatchers.Main) {
                    session.configure(session.config.apply {
                        augmentedImageDatabase = db
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        focusMode = Config.FocusMode.AUTO
                    })
                    Log.d(TAG, "Marker DB configured ✅")
                }
            } catch (e: Exception) {
                Log.e(TAG, "setupMarkerDatabase error", e)
            }
        }
    }
}