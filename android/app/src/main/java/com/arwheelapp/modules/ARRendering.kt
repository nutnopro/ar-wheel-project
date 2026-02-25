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
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point as OpenCVPoint
import org.opencv.core.Rect
import org.opencv.core.RotatedRect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class ARRendering(
    private val context: Context,
    private val onnxOverlayView: OnnxOverlayView,
    private val arSceneView: ARSceneView,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "ARRendering"

        // --- Dynamic Inference ---
        private const val BASE_FAST_INTERVAL = 40L      // ~25 FPS when moving
        private const val BASE_SLOW_INTERVAL = 250L     // ~4 FPS when idle (saves battery)
        private const val JUMP_THRESHOLD = 0.05f        // 5cm movement triggers fast mode
        private const val PROCESS_BUFFER_MS = 15L

        // --- HitTest & Depth ---
        private const val HITTEST_POINTS = 8
        private const val DEPTH_OUTLIER_THRESHOLD = 0.10f   // Discard points drifting > 10cm

        // --- Filtering & Stability ---
        private const val MAX_HISTORY_FRAMES = 15       // History window for BBox count
        private const val QUICK_OVERRIDE_FRAMES = 5
        private const val HISTORY_MAX_POSES = 8         // Keep 8 best poses to find the "roundest"
        private const val OUTLIER_ANGLE_DEG = 25f       // Filter sudden rotations
        private const val OUTLIER_POS_DIST = 0.4f       // Filter sudden position jumps

        // --- Dynamic Positioning ---
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

    fun render(arSceneView: ARSceneView, frame: Frame, currentMode: ARMode, deviceRotation: Int) {
        if (previousMode != currentMode) {
            handleModeSwitch()
            previousMode = currentMode
        }
        when (currentMode) {
            ARMode.MARKER_BASED -> processMarkerBased(arSceneView, frame)
            ARMode.MARKERLESS -> {
                processMarkerlessInference(frame, deviceRotation)
                processMarkerlessHitTest(arSceneView, frame, deviceRotation)
            }
        }
    }

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
            markerlessActiveModels.clear()
            onnxOverlayView.clear()
            bboxCountHistory.clear()
        }
    }

    // ==========================================
    // MARKER-BASED LOGIC
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
                TrackingState.STOPPED -> augmentedImageMap.remove(image)?.let { arSceneView.removeChildNode(it); it.destroy() }
                TrackingState.PAUSED -> augmentedImageMap[image]?.isVisible = false
            }
        }
    }

    private fun processMarkerlessInference(frame: Frame, deviceRotation: Int) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastInferenceTime < currentInferenceInterval) return
        lastInferenceTime = currentTime

        try {
            val tensor = frameConverter.convertFrameToTensor(frame, deviceRotation)
            val viewW = arSceneView.width.toFloat()
            val viewH = arSceneView.height.toFloat()
            val frameYData = extractYPlaneData(frame)

            val startTime = SystemClock.uptimeMillis()

            onnxRuntimeHandler.runOnnxInferenceAsync(tensor, deviceRotation, frameYData, viewW, viewH) { results ->
                latestInferenceDurationMs = SystemClock.uptimeMillis() - startTime
                latestProcessedDetections = results
                isNewDetectionAvailable = true
                onnxOverlayView.updateDetections(results.map { it.detection })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error running inference", e)
        }
    }

    private fun processMarkerlessHitTest(arSceneView: ARSceneView, frame: Frame, deviceRotation: Int) {
        if (!isNewDetectionAvailable) return
        isNewDetectionAvailable = false

        if (frame.camera.trackingState != TrackingState.TRACKING) return

        val hitTestStartTime = SystemClock.uptimeMillis()

        val cameraPose = frame.camera.pose
        val cameraPos = Float3(cameraPose.tx(), cameraPose.ty(), cameraPose.tz())

        bboxCountHistory.add(latestProcessedDetections.size)
        if (bboxCountHistory.size > MAX_HISTORY_FRAMES) bboxCountHistory.removeAt(0)

        val targetModelCount = getTargetBBoxCount()
        var filteredData = latestProcessedDetections
        if (latestProcessedDetections.size > targetModelCount) {
            filteredData = latestProcessedDetections.sortedByDescending { it.detection.confidence }.take(targetModelCount)
        }

        val claimedModels = mutableSetOf<Node>()
        if (filteredData.isEmpty()) {
            hideUnlockedModels(claimedModels, hitTestStartTime)
            return
        }

        val viewW = arSceneView.width.toFloat()
        val viewH = arSceneView.height.toFloat()
        var maxPosShift = 0f

        for (data in filteredData) {
            val det = data.detection
            val cvResult = data.cvResult
            val bbox = det.boundingBox

            val hitPoints = collectHitPoints(frame, cvResult, bbox, viewW, viewH)
            if (hitPoints.isEmpty()) continue

            val validPoints = filterDepthOutliers(hitPoints, cameraPos)
            if (validPoints.isEmpty()) continue

            var sumX = 0f; var sumY = 0f; var sumZ = 0f
            var avgNormal = Float3(0f, 0f, 0f)
            for (pose in validPoints) { 
                sumX += pose.tx()
                sumY += pose.ty()
                sumZ += pose.tz()
                avgNormal += extractNormal(pose)
            }

            val calculatedPos = Float3(
                sumX / validPoints.size.toFloat(), 
                sumY / validPoints.size.toFloat(), 
                sumZ / validPoints.size.toFloat()
            )

            avgNormal = normalize(avgNormal)
            if (length(avgNormal).isNaN() || length(avgNormal) < 0.001f) {
                avgNormal = Float3(0f, 0f, 1f)
            }

            val baseRot = lookRotation(forward = avgNormal, up = Float3(0f, 1f, 0f))
            val calculatedRot = if (cvResult.isFound) {
                val zRot = Quaternion.fromAxisAngle(Float3(0f, 0f, 1f), cvResult.angle)
                baseRot * zRot
            } else baseRot

            val closestModel = markerlessActiveModels.asSequence()
                .filter { it !in claimedModels }
                .minByOrNull { distance(it.position, calculatedPos) }
            val targetModel = if (closestModel != null && distance(closestModel.position, calculatedPos) < snapThreshold * 1.2f) {
                closestModel
            } else {
                getAvailableMarkerlessModel(claimedModels)
            }

            val state = modelStates.getOrPut(targetModel) { ModelState() }
            val absBbox = RectF(
                bbox.left * viewW,
                bbox.top * viewH,
                bbox.right * viewW,
                bbox.bottom * viewH
            )

            val pointToCheck = if (state.poseHistory.isNotEmpty()) state.bestPos else calculatedPos
            val screenPoint = arSceneView.view.worldToScreen(pointToCheck)
            val isInsideBbox = absBbox.contains(screenPoint.x, screenPoint.y)
            if (!isInsideBbox) {
                if (state.poseHistory.isEmpty()) {
                    continue 
                } else {
                    state.driftFrames++
                    if (state.driftFrames > 15) { 
                        state.poseHistory.clear()
                        state.anchor?.detach()
                        state.anchor = null
                        state.driftFrames = 0
                        continue
                    }
                }
            } else {
                state.driftFrames = 0 
            }

            claimedModels.add(targetModel)

            state.lastDetectionTime = hitTestStartTime
            state.detectionHits++
            if (state.detectionHits >= 3) targetModel.isVisible = true

            val shift = state.lastStablePos?.let { distance(it, calculatedPos) } ?: Float.MAX_VALUE
            maxPosShift = max(maxPosShift, shift)
            state.lastStablePos = calculatedPos

            val angleDiff = if (state.poseHistory.isNotEmpty()) angleBetweenQuaternions(state.bestRot, calculatedRot) else 0f
            val posDiff = if (state.poseHistory.isNotEmpty()) distance(state.bestPos, calculatedPos) else 0f
            val isOutlier = state.poseHistory.isNotEmpty() && (angleDiff > OUTLIER_ANGLE_DEG || posDiff > OUTLIER_POS_DIST)
            if (!isOutlier) {
                state.poseHistory.add(TrackedPose(calculatedPos, calculatedRot, cvResult.circularity))
                state.poseHistory.sortByDescending { it.circularity }
                if (state.poseHistory.size > HISTORY_MAX_POSES) state.poseHistory.removeLast()
                state.bestPos = state.poseHistory[0].pos
                state.bestRot = state.poseHistory[0].rot

                state.stableFrames++
                if (state.anchor == null && state.stableFrames >= 5) {
                    val poseToAnchor = Pose(
                        floatArrayOf(state.bestPos.x, state.bestPos.y, state.bestPos.z),
                        floatArrayOf(state.bestRot.x, state.bestRot.y, state.bestRot.z, state.bestRot.w)
                    )
                    state.anchor = arSceneView.session?.createAnchor(poseToAnchor)
                }
            } else {
                state.stableFrames = 0
            }

            val renderPos = state.anchor?.pose?.let { 
                Float3(it.tx(), it.ty(), it.tz()) 
            } ?: (if (state.poseHistory.isNotEmpty()) state.bestPos else calculatedPos)

            val renderRot = state.anchor?.pose?.let { 
                Quaternion(it.qx(), it.qy(), it.qz(), it.qw()) 
            } ?: (if (state.poseHistory.isNotEmpty()) state.bestRot else calculatedRot)

            val alpha = calculateDynamicAlpha(targetModel.position, renderPos)
            targetModel.position = mix(targetModel.position, renderPos, alpha)
            targetModel.quaternion = slerp(targetModel.quaternion, renderRot, alpha)
        }

        hideUnlockedModels(claimedModels, hitTestStartTime)

        val hitTestDurationMs = SystemClock.uptimeMillis() - hitTestStartTime
        val totalExecutionTimeMs = latestInferenceDurationMs + hitTestDurationMs

        if (maxPosShift > JUMP_THRESHOLD || bboxCountHistory.size < MAX_HISTORY_FRAMES) {
            targetInferenceInterval = BASE_FAST_INTERVAL
        } else {
            targetInferenceInterval = min(BASE_SLOW_INTERVAL, targetInferenceInterval + 10L)
        }

        currentInferenceInterval = max(targetInferenceInterval, totalExecutionTimeMs + PROCESS_BUFFER_MS)
    }

    // ==========================================
    // MATH & UTILITIES
    // ==========================================
    private fun extractYPlaneData(frame: Frame): FrameYData? {
        val image = try { frame.acquireCameraImage() } catch (e: Exception) { return null }
        try {
            val yPlane = image.planes[0].buffer.apply { rewind() }
            val width = image.width
            val height = image.height
            val yRowStride = image.planes[0].rowStride
            val requiredSize = width * height

            if (yPlaneBufferA == null || yPlaneBufferA!!.size != requiredSize) {
                yPlaneBufferA = ByteArray(requiredSize)
                yPlaneBufferB = ByteArray(requiredSize)
            }
            val currentBuffer = if (useBufferA) yPlaneBufferA!! else yPlaneBufferB!!
            useBufferA = !useBufferA

            if (yRowStride == width) {
                yPlane.get(currentBuffer)
            } else {
                for (row in 0 until height) {
                    yPlane.position(row * yRowStride)
                    yPlane.get(currentBuffer, row * width, width)
                }
            }

            return FrameYData(currentBuffer, width, height, width)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting Y-Plane bytes", e)
            return null
        } finally {
            image.close()
        }
    }

    private fun getTargetBBoxCount(): Int {
        if (bboxCountHistory.isEmpty()) return 0
        if (bboxCountHistory.size >= QUICK_OVERRIDE_FRAMES) {
            val last5 = bboxCountHistory.takeLast(QUICK_OVERRIDE_FRAMES)
            if (last5.all { it == last5.first() }) return last5.first()
        }
        return bboxCountHistory.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 0
    }

    private fun collectHitPoints(frame: Frame, cvRes: RefinedResult, bbox: RectF, viewW: Float, viewH: Float): List<Pose> {
        val validPoses = mutableListOf<Pose>()
        frame.hitTest(cvRes.cx, cvRes.cy).firstOrNull { it.trackable is Plane || it.trackable is Point }?.let {
            validPoses.add(it.hitPose)
        }
        val hitRadiusW = if (cvRes.isFound) cvRes.width / 2f else (bbox.width() * viewW * 0.64f) / 2f
        val hitRadiusH = if (cvRes.isFound) cvRes.height / 2f else (bbox.height() * viewH * 0.64f) / 2f
        val angleRad = Math.toRadians(cvRes.angle.toDouble()).toFloat()
        for (i in 0 until HITTEST_POINTS) {
            val t = (2f * Math.PI.toFloat() * i) / HITTEST_POINTS
            val dx = hitRadiusW * cos(t) * cos(angleRad) - hitRadiusH * sin(t) * sin(angleRad)
            val dy = hitRadiusW * cos(t) * sin(angleRad) + hitRadiusH * sin(t) * cos(angleRad)
            frame.hitTest(cvRes.cx + dx, cvRes.cy + dy).firstOrNull { it.trackable is Plane || it.trackable is Point }?.let {
                validPoses.add(it.hitPose)
            }
        }
        return validPoses
    }

    private fun filterDepthOutliers(poses: List<Pose>, cameraPos: Float3): List<Pose> {
        if (poses.size <= 2) return poses

        val depths = poses.map { distance(Float3(it.tx(), it.ty(), it.tz()), cameraPos) }.sorted()
        val medianDepth = depths[depths.size / 2]
        return poses.filter { abs(distance(Float3(it.tx(), it.ty(), it.tz()), cameraPos) - medianDepth) < DEPTH_OUTLIER_THRESHOLD }
    }

    private fun extractNormal(pose: Pose): Float3 {
        val zAxis = FloatArray(3)
        pose.getTransformedAxis(2, 0f, zAxis, 0)
        return Float3(zAxis[0], zAxis[1], zAxis[2])
    }

    private fun lookRotation(forward: Float3, up: Float3): Quaternion {
        val f = normalize(forward)
        val u = if (abs(dot(f, up)) > 0.99f) Float3(0f, 0f, 1f) else up
        val r = normalize(cross(u, f)) 
        val u2 = cross(f, r)
        val tr = r.x + u2.y + f.z
        return when {
            tr > 0 -> {
                val s = sqrt(tr + 1.0f) * 2
                Quaternion((u2.z - f.y) / s, (f.x - r.z) / s, (r.y - u2.x) / s, 0.25f * s)
            }
            r.x > u2.y && r.x > f.z -> {
                val s = sqrt(1.0f + r.x - u2.y - f.z) * 2
                Quaternion(0.25f * s, (u2.x + r.y) / s, (f.x + r.z) / s, (u2.z - f.y) / s)
            }
            u2.y > f.z -> {
                val s = sqrt(1.0f + u2.y - r.x - f.z) * 2
                Quaternion((u2.x + r.y) / s, 0.25f * s, (f.y + u2.z) / s, (f.x - r.z) / s)
            }
            else -> {
                val s = sqrt(1.0f + f.z - r.x - u2.y) * 2
                Quaternion((f.x + r.z) / s, (f.y + u2.z) / s, 0.25f * s, (r.y - u2.x) / s)
            }
        }
    }

    private fun angleBetweenQuaternions(q1: Quaternion, q2: Quaternion): Float {
        val dotProduct = abs(dot(q1, q2)).coerceIn(0.0f, 1.0f)
        return Math.toDegrees(2.0 * acos(dotProduct)).toFloat()
    }

    private fun calculateDynamicAlpha(currentPos: Float3, targetPos: Float3): Float {
        val t = ((distance(currentPos, targetPos) - MIN_DIST) / (MAX_DIST - MIN_DIST)).coerceIn(0f, 1f)
        return MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * t
    }

    private fun distance(p1: Float3, p2: Float3) = length(p1 - p2)
    private fun distance(pose1: Pose, pose2: Pose) = sqrt(
        (pose1.tx() - pose2.tx()).pow(2f) + 
        (pose1.ty() - pose2.ty()).pow(2f) + 
        (pose1.tz() - pose2.tz()).pow(2f)
    )

    private fun hideUnlockedModels(claimedModels: Set<Node>, currentTime: Long) {
        for (model in markerlessActiveModels) {
            if (model !in claimedModels) {
                val state = modelStates[model] ?: continue
                if (currentTime - state.lastDetectionTime > 1000L) {
                    model.isVisible = false
                    state.anchor?.detach()
                    state.anchor = null
                    state.detectionHits = 0
                    state.poseHistory.clear()
                }
            }
        }
    }

    // ==========================================
    // EXTERNAL APIs
    // ==========================================
    private fun getOrCreateModel(path: String): Node {
        return modelPool.find { it.parent == null || !it.isVisible }?.apply { isVisible = true }
            ?: modelManager.createNewModel(path, coroutineScope).also { modelPool.add(it) }
    }

    private fun getAvailableMarkerlessModel(claimedModels: Set<Node>): Node {
        val freeModel = markerlessActiveModels.find { it !in claimedModels }
        if (freeModel != null) return freeModel

        val newModel = modelManager.createNewModel(modelPath, coroutineScope)
        arSceneView.addChildNode(newModel)
        markerlessActiveModels.add(newModel)
        modelStates[newModel] = ModelState()
        return newModel
    }

    fun updateNewModel(path: String) {
        modelPath = path
        modelPool.forEach { modelManager.changeModel(it, path, coroutineScope) }
    }

    fun updateModelSize(sizeInch: Float) {
        val sizeCm = sizeInch * 2.54f
        snapThreshold = sizeCm / 100.0f
        val scaleFactor = sizeCm / 45.72f
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
                Log.d(TAG, "⏳ Starting background marker loading...")
                val database = AugmentedImageDatabase(session)
                val assetManager = context.assets
                assetManager.list("markers")?.filter { it.endsWith(".jpg", true) || it.endsWith(".png", true) }?.forEach { filename ->
                    try {
                        assetManager.open("markers/$filename").use { 
                            val bitmap = BitmapFactory.decodeStream(it)
                            database.addImage(filename.substringBeforeLast("."), bitmap, markerSize)
                            Log.d(TAG, "Loaded Marker: $filename ✅")
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
                    Log.d(TAG, "Marker Database Configured Successfully!")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in setupMarkerDatabase", e)
            }
        }
    }
}