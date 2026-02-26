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

        // --- Inference timing ---
        private const val BASE_FAST_INTERVAL = 40L          // ~25 FPS when moving
        private const val BASE_SLOW_INTERVAL = 250L         // ~4 FPS when idle
        private const val JUMP_THRESHOLD = 0.05f            // 5 cm → switch to fast mode
        private const val PROCESS_BUFFER_MS = 15L

        // --- Hit-test layout ---
        private const val RING_POINTS = 8                   // 8 ring points + 1 center = 9 total
        private const val RING_RADIUS_FRACTION = 0.64f      // ring radius as fraction of half-bbox

        // --- Depth filtering ---
        private const val DEPTH_OUTLIER_THRESHOLD = 0.10f   // discard points > 10 cm from median depth

        // --- History / stability ---
        private const val MAX_BBOX_HISTORY = 15
        private const val QUICK_OVERRIDE_FRAMES = 5
        private const val HISTORY_MAX_POSES = 10            // keep N best poses per tracker
        private const val OUTLIER_ANGLE_DEG = 25f
        private const val OUTLIER_POS_DIST = 0.4f
        private const val FRAMES_TO_ANCHOR = 6              // stable frames required before anchoring
        private const val ANCHOR_CIRCULARITY_THRESHOLD = 0.95f  // wheel must look round enough
        private const val DETECTION_TIMEOUT_MS = 1200L

        // --- Dynamic lerp ---
        private const val MIN_DIST = 0.02f
        private const val MAX_DIST = 0.3f
        private const val MIN_ALPHA = 0.04f
        private const val MAX_ALPHA = 0.6f
    }

    private val modelManager = ModelManager(arSceneView)
    private val frameConverter = FrameConverter()
    private val onnxRuntimeHandler = OnnxRuntimeHandler(context)

    private var previousMode: ARMode? = null

    private var lastInferenceTime = 0L
    private var targetInferenceInterval = BASE_FAST_INTERVAL
    private var currentInferenceInterval = BASE_FAST_INTERVAL
    private var latestInferenceDurationMs = 0L

    private val modelPool = mutableListOf<Node>()
    private val augmentedImageMap = mutableMapOf<AugmentedImage, AugmentedImageNode>()
    private val markerlessActiveModels = mutableListOf<Node>()
    private val modelStates = mutableMapOf<Node, ModelState>()

    private var yPlaneBufferA: ByteArray? = null
    private var yPlaneBufferB: ByteArray? = null
    private var useBufferA = true
    private val bboxCountHistory = mutableListOf<Int>()

    @Volatile private var latestProcessedDetections: List<ProcessedDetection> = emptyList()
    @Volatile private var isNewDetectionAvailable = false

    @Volatile private var snapThreshold = 0.4572f
    @Volatile private var modelPath = "models/wheel1.glb"   // !!Change to UI

    // ==========================================
    // Public entry point
    // ==========================================
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

    // ==========================================
    // Mode switching
    // ==========================================
    private fun handleModeSwitch() {
        modelPool.forEach { model ->
            arSceneView.removeChildNode(model)
            model.parent = null
            model.isVisible = false
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

    // ==========================================
    // MARKER-BASED
    // ==========================================
    private fun processMarkerBased(arSceneView: ARSceneView, frame: Frame) {
        val updatedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)
        for (image in updatedImages) {
            when (image.trackingState) {
                TrackingState.TRACKING -> {
                    val imageNode = augmentedImageMap.getOrPut(image) {
                        AugmentedImageNode(arSceneView.engine, image).apply {
                            val model = getOrCreateModel(modelPath).apply { isVisible = true }
                            addChildNode(model)
                            arSceneView.addChildNode(this)
                        }
                    }
                    imageNode.isVisible = (image.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING)
                }
                TrackingState.STOPPED -> {
                    augmentedImageMap.remove(image)?.let {
                        arSceneView.removeChildNode(it)
                        it.destroy()
                    }
                }
                TrackingState.PAUSED -> augmentedImageMap[image]?.isVisible = false
            }
        }
    }

    // ==========================================
    // MARKERLESS — inference
    // ==========================================
    private fun processMarkerlessInference(frame: Frame, deviceRotation: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastInferenceTime < currentInferenceInterval) return
        lastInferenceTime = now

        try {
            val tensor = frameConverter.convertFrameToTensor(frame, deviceRotation)
            val viewW = arSceneView.width.toFloat()
            val viewH = arSceneView.height.toFloat()
            val frameYData = extractYPlaneData(frame)

            val inferStart = SystemClock.uptimeMillis()

            onnxRuntimeHandler.runOnnxInferenceAsync(tensor, deviceRotation, frameYData, viewW, viewH) { results ->
                latestInferenceDurationMs = SystemClock.uptimeMillis() - inferStart
                latestProcessedDetections = results
                isNewDetectionAvailable = true
                onnxOverlayView.updateDetections(results.map { it.detection })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
        }
    }

    // ==========================================
    // MARKERLESS — hit-test & model update
    // ==========================================
    private fun processMarkerlessHitTest(arSceneView: ARSceneView, frame: Frame) {
        if (!isNewDetectionAvailable) return
        isNewDetectionAvailable = false

        if (frame.camera.trackingState != TrackingState.TRACKING) return

        val hitStart = SystemClock.uptimeMillis()

        val cameraPose = frame.camera.pose
        val cameraPos = Float3(cameraPose.tx(), cameraPose.ty(), cameraPose.tz())

        bboxCountHistory.add(latestProcessedDetections.size)
        if (bboxCountHistory.size > MAX_BBOX_HISTORY) bboxCountHistory.removeAt(0)

        val targetCount = getTargetBBoxCount()
        val filteredData = if (latestProcessedDetections.size > targetCount)
            latestProcessedDetections.sortedByDescending { it.detection.confidence }.take(targetCount)
        else
            latestProcessedDetections

        val claimedModels = mutableSetOf<Node>()
        val viewW = arSceneView.width.toFloat()
        val viewH = arSceneView.height.toFloat()
        var maxPosShift = 0f

        for (data in filteredData) {
            val det = data.detection
            val cvRes = data.cvResult
            val bbox = det.boundingBox

            val allPoses = collectHitPoses(frame, cvRes, bbox, viewW, viewH)
            if (allPoses.isEmpty()) continue

            val depthFiltered = filterDepthOutliers(allPoses, cameraPos)
            if (depthFiltered.isEmpty()) continue

            val centerHit = allPoses.firstOrNull()
            val trueCenter: Float3 = if (centerHit != null)
                Float3(centerHit.tx(), centerHit.ty(), centerHit.tz())
            else
                depthFiltered.map { Float3(it.tx(), it.ty(), it.tz()) }.average3()

            val allPoints3D = depthFiltered.map { Float3(it.tx(), it.ty(), it.tz()) }
            val planeNormal = fitPlaneNormalSVD(allPoints3D, trueCenter, cameraPos)
            val planeRot = buildRotationFromNormal(planeNormal)
            val modelOffsetRot = Quaternion.fromAxisAngle(Float3(1f, 0f, 0f), 90f)
            val calculatedRot = if (cvRes.isFound) {
                val zRot = Quaternion.fromAxisAngle(Float3(0f, 0f, 1f), cvRes.angle)
                planeRot * zRot * modelOffsetRot
            } else {
                planeRot * modelOffsetRot
            }

            val circularity = cvRes.circularity

            val closestModel = markerlessActiveModels.asSequence()
                .filter { it !in claimedModels }
                .minByOrNull { dist(it.position, trueCenter) }
            val model = if (closestModel != null && dist(closestModel.position, trueCenter) < snapThreshold * 1.2f) {
                closestModel
            } else {
                getAvailableMarkerlessModel(claimedModels)
            }

            claimedModels.add(model)
            val state = modelStates.getOrPut(model) { ModelState() }

            val absBbox = RectF(bbox.left * viewW, bbox.top * viewH, bbox.right * viewW, bbox.bottom * viewH)
            val checkPoint = if (state.posHistory.isNotEmpty()) state.bestPos else trueCenter
            val screenPt = arSceneView.view.worldToScreen(checkPoint)
            if (!absBbox.contains(screenPt.x, screenPt.y)) {
                if (state.posHistory.isEmpty()) continue
                state.driftFrames++
                if (state.driftFrames > 15) {
                    resetModelState(state)
                    continue
                }
            } else {
                state.driftFrames = 0
            }

            state.lastDetectionTime = hitStart
            state.detectionHits++
            if (state.detectionHits >= 3) model.isVisible = true

            val posShift = state.lastStablePos?.let { dist(it, trueCenter) } ?: Float.MAX_VALUE
            maxPosShift = max(maxPosShift, posShift)
            state.lastStablePos = trueCenter

            val angleDiff = if (state.rotHistory.isNotEmpty()) angleBetween(state.bestRot, calculatedRot) else 0f
            val posDiff = if (state.posHistory.isNotEmpty()) dist(state.bestPos, trueCenter) else 0f
            val isOutlier = state.posHistory.isNotEmpty() &&
                (angleDiff > OUTLIER_ANGLE_DEG || posDiff > OUTLIER_POS_DIST)
            if (!isOutlier) {
                state.posHistory.add(TrackedPos(trueCenter, circularity))
                state.posHistory.sortByDescending { it.circularity }
                if (state.posHistory.size > HISTORY_MAX_POSES) state.posHistory.removeLast()
                state.bestPos = state.posHistory[0].pos

                state.rotHistory.add(TrackedRot(calculatedRot, circularity))
                state.rotHistory.sortByDescending { it.circularity }
                if (state.rotHistory.size > HISTORY_MAX_POSES) state.rotHistory.removeLast()
                state.bestRot = state.rotHistory[0].rot

                if (state.anchor == null) {
                    if (circularity >= ANCHOR_CIRCULARITY_THRESHOLD) {
                        state.stableFrames++
                    } else {
                        state.stableFrames = 0
                    }
                    if (state.stableFrames >= FRAMES_TO_ANCHOR) {
                        val anchorPose = Pose(
                            floatArrayOf(state.bestPos.x, state.bestPos.y, state.bestPos.z),
                            floatArrayOf(state.bestRot.x, state.bestRot.y, state.bestRot.z, state.bestRot.w)
                        )
                        state.anchor = arSceneView.session?.createAnchor(anchorPose)
                    }
                }
            } else {
                state.stableFrames = 0
            }

            val renderPos = state.anchor?.pose?.let { Float3(it.tx(), it.ty(), it.tz()) }
                ?: if (state.posHistory.isNotEmpty()) state.bestPos else trueCenter
            val renderRot = state.anchor?.pose?.let { Quaternion(it.qx(), it.qy(), it.qz(), it.qw()) }
                ?: if (state.rotHistory.isNotEmpty()) state.bestRot else calculatedRot

            val alpha = dynamicAlpha(model.position, renderPos)
            model.position = mix(model.position, renderPos, alpha)
            model.quaternion = slerp(model.quaternion, renderRot, alpha)
        }

        hideStaleModels(claimedModels, hitStart)

        val totalMs = latestInferenceDurationMs + (SystemClock.uptimeMillis() - hitStart)
        targetInferenceInterval = if (maxPosShift > JUMP_THRESHOLD || bboxCountHistory.size < MAX_BBOX_HISTORY)
            BASE_FAST_INTERVAL
        else
            min(BASE_SLOW_INTERVAL, targetInferenceInterval + 10L)
        currentInferenceInterval = max(targetInferenceInterval, totalMs + PROCESS_BUFFER_MS)
    }

    // ==========================================
    // Plane fitting (SVD / Newell's method)
    // ==========================================
    private fun fitPlaneNormalSVD(points: List<Float3>, center: Float3, cameraPos: Float3): Float3 {
        if (points.size < 3) return normalize(cameraPos - center)

        var nx = 0f; var ny = 0f; var nz = 0f
        val n = points.size
        for (i in 0 until n) {
            val curr = points[i] - center
            val next = points[(i + 1) % n] - center
            nx += (curr.y - next.y) * (curr.z + next.z)
            ny += (curr.z - next.z) * (curr.x + next.x)
            nz += (curr.x - next.x) * (curr.y + next.y)
        }

        var normal = Float3(nx, ny, nz)
        val len = length(normal)
        if (len < 1e-6f || len.isNaN()) {
            return normalize(cameraPos - center)
        }
        normal = normalize(normal)

        val toCamera = normalize(cameraPos - center)
        if (dot(normal, toCamera) < 0f) {
            normal = -normal
        }

        return normal
    }

    private fun buildRotationFromNormal(normal: Float3): Quaternion {
        val f = normalize(normal)
        val worldUp = Float3(0f, 1f, 0f)
        val up = if (abs(dot(f, worldUp)) > 0.98f) Float3(0f, 0f, 1f) else worldUp
        val right = normalize(cross(up, f))
        val correctedUp = cross(f, right)
        val tr = right.x + correctedUp.y + f.z

        return when {
            tr > 0f -> {
                val s = sqrt(tr + 1f) * 2f
                Quaternion((correctedUp.z - f.y) / s, (f.x - right.z) / s, (right.y - correctedUp.x) / s, 0.25f * s)
            }
            right.x > correctedUp.y && right.x > f.z -> {
                val s = sqrt(1f + right.x - correctedUp.y - f.z) * 2f
                Quaternion(0.25f * s, (correctedUp.x + right.y) / s, (f.x + right.z) / s, (correctedUp.z - f.y) / s)
            }
            correctedUp.y > f.z -> {
                val s = sqrt(1f + correctedUp.y - right.x - f.z) * 2f
                Quaternion((correctedUp.x + right.y) / s, 0.25f * s, (f.y + correctedUp.z) / s, (f.x - right.z) / s)
            }
            else -> {
                val s = sqrt(1f + f.z - right.x - correctedUp.y) * 2f
                Quaternion((f.x + right.z) / s, (f.y + correctedUp.z) / s, 0.25f * s, (right.y - correctedUp.x) / s)
            }
        }
    }

    // ==========================================
    // Hit-test helpers
    // ==========================================
    private fun collectHitPoses(
        frame: Frame,
        cvRes: RefinedResult,
        bbox: RectF,
        viewW: Float,
        viewH: Float
    ): List<Pose> {
        val poses = mutableListOf<Pose>()
        val cx = cvRes.cx
        val cy = cvRes.cy

        frame.hitTest(cx, cy)
            .firstOrNull { it.trackable is Plane || it.trackable is Point }
            ?.let { poses.add(it.hitPose) }

        val ringRadiusX = if (cvRes.isFound && cvRes.width > 0f)
            cvRes.width / 2f
        else
            bbox.width() * viewW * RING_RADIUS_FRACTION / 2f

        val ringRadiusY = if (cvRes.isFound && cvRes.height > 0f)
            cvRes.height / 2f
        else
            bbox.height() * viewH * RING_RADIUS_FRACTION / 2f

        val angleRad = Math.toRadians(cvRes.angle.toDouble()).toFloat()
        val cosA = cos(angleRad); val sinA = sin(angleRad)

        for (i in 0 until RING_POINTS) {
            val t = (2f * kotlin.math.PI.toFloat() * i) / RING_POINTS
            val localX = ringRadiusX * cos(t)
            val localY = ringRadiusY * sin(t)
            // Rotate by ellipse angle
            val dx = localX * cosA - localY * sinA
            val dy = localX * sinA + localY * cosA

            frame.hitTest(cx + dx, cy + dy)
                .firstOrNull { it.trackable is Plane || it.trackable is Point }
                ?.let { poses.add(it.hitPose) }
        }

        return poses
    }

    private fun filterDepthOutliers(poses: List<Pose>, cameraPos: Float3): List<Pose> {
        if (poses.size <= 2) return poses
        val depths = poses.map { dist(Float3(it.tx(), it.ty(), it.tz()), cameraPos) }.sorted()
        val median = depths[depths.size / 2]
        return poses.filter { abs(dist(Float3(it.tx(), it.ty(), it.tz()), cameraPos) - median) <= DEPTH_OUTLIER_THRESHOLD }
    }

    // ==========================================
    // Model lifecycle helpers
    // ==========================================
    private fun getAvailableMarkerlessModel(claimedModels: Set<Node>): Node {
        val free = markerlessActiveModels.find { it !in claimedModels }
        if (free != null) return free
        val newModel = modelManager.createNewModel(modelPath, coroutineScope)
        newModel.isVisible = false
        arSceneView.addChildNode(newModel)
        markerlessActiveModels.add(newModel)
        modelStates[newModel] = ModelState()
        return newModel
    }

    private fun resetModelState(state: ModelState) {
        state.anchor?.detach()
        state.anchor = null
        state.posHistory.clear()
        state.rotHistory.clear()
        state.driftFrames = 0
        state.stableFrames = 0
        state.detectionHits = 0
    }

    private fun hideStaleModels(claimedModels: Set<Node>, now: Long) {
        for (model in markerlessActiveModels) {
            if (model in claimedModels) continue
            val state = modelStates[model] ?: continue
            if (now - state.lastDetectionTime > DETECTION_TIMEOUT_MS) {
                model.isVisible = false
                resetModelState(state)
            }
        }
    }

    // ==========================================
    // Math utilities
    // ==========================================
    private fun dynamicAlpha(current: Float3, target: Float3): Float {
        val t = ((dist(current, target) - MIN_DIST) / (MAX_DIST - MIN_DIST)).coerceIn(0f, 1f)
        return MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * t
    }

    private fun angleBetween(q1: Quaternion, q2: Quaternion): Float {
        val d = abs(dot(q1, q2)).coerceIn(0f, 1f)
        return Math.toDegrees(2.0 * acos(d)).toFloat()
    }

    private fun dist(a: Float3, b: Float3) = length(a - b)

    private fun dist(p1: Pose, p2: Pose) = sqrt((p1.tx() - p2.tx()).pow(2) + (p1.ty() - p2.ty()).pow(2) + (p1.tz() - p2.tz()).pow(2))

    private fun List<Float3>.average3() = Float3(
        map { it.x }.average().toFloat(),
        map { it.y }.average().toFloat(),
        map { it.z }.average().toFloat()
    )

    // ==========================================
    // Y-plane extraction
    // ==========================================
    private fun extractYPlaneData(frame: Frame): FrameYData? {
        val image = try { frame.acquireCameraImage() } catch (e: Exception) { return null }
        return try {
            val yPlane = image.planes[0].buffer.apply { rewind() }
            val width = image.width
            val height = image.height
            val yRowStride = image.planes[0].rowStride
            val requiredSize = width * height

            if (yPlaneBufferA == null || yPlaneBufferA!!.size != requiredSize) {
                yPlaneBufferA = ByteArray(requiredSize)
                yPlaneBufferB = ByteArray(requiredSize)
            }
            val buf = if (useBufferA) yPlaneBufferA!! else yPlaneBufferB!!
            useBufferA = !useBufferA

            if (yRowStride == width) {
                yPlane.get(buf)
            } else {
                for (row in 0 until height) {
                    yPlane.position(row * yRowStride)
                    yPlane.get(buf, row * width, width)
                }
            }

            FrameYData(buf, width, height, width)
        } catch (e: Exception) {
            Log.e(TAG, "Y-plane extraction error", e)
            null
        } finally {
            image.close()
        }
    }

    // ==========================================
    // Bbox count (modal vote)
    // ==========================================
    private fun getTargetBBoxCount(): Int {
        if (bboxCountHistory.isEmpty()) return 0
        if (bboxCountHistory.size >= QUICK_OVERRIDE_FRAMES) {
            val last = bboxCountHistory.takeLast(QUICK_OVERRIDE_FRAMES)
            if (last.all { it == last.first() }) return last.first()
        }
        return bboxCountHistory.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 0
    }

    // ==========================================
    // Public API
    // ==========================================
    private fun getOrCreateModel(path: String): Node =
        modelPool.find { it.parent == null || !it.isVisible }?.apply { isVisible = true }
            ?: modelManager.createNewModel(path, coroutineScope).also { modelPool.add(it) }

    fun updateNewModel(path: String) {
        modelPath = path
        modelPool.forEach { modelManager.changeModel(it, path, coroutineScope) }
    }

    fun updateModelSize(sizeInch: Float) {
        val sizeCm = sizeInch * 2.54f
        snapThreshold = sizeCm / 100.0f
        val scaleFactor = sizeCm / 45.72f   // baseline = 18-inch wheel
        modelPool.forEach { modelManager.changeModelSize(it, scaleFactor) }
    }

    fun clear() {
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
                val database = AugmentedImageDatabase(session)
                val assets = context.assets
                assets.list("markers")
                    ?.filter { it.endsWith(".jpg", true) || it.endsWith(".png", true) }
                    ?.forEach { filename ->
                        try {
                            assets.open("markers/$filename").use { stream ->
                                val bitmap = BitmapFactory.decodeStream(stream)
                                database.addImage(filename.substringBeforeLast("."), bitmap, markerSize)
                                Log.d(TAG, "Marker loaded: $filename ✅")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load marker: $filename", e)
                        }
                    }
                withContext(Dispatchers.Main) {
                    session.configure(session.config.apply {
                        augmentedImageDatabase = database
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        focusMode = Config.FocusMode.AUTO
                    })
                    Log.d(TAG, "Marker database configured ✅")
                }
            } catch (e: Exception) {
                Log.e(TAG, "setupMarkerDatabase error", e)
            }
        }
    }
}