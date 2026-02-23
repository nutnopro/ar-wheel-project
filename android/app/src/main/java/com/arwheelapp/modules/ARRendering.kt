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
        private const val BBOX_PADDING = 0.15f
        private const val HITTEST_POINTS = 4
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

    private var yPlaneBytes: ByteArray? = null
    private val bboxCountHistory = mutableListOf<Int>()

    @Volatile private var latestDetections: List<Detection> = emptyList()
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
            val startTime = SystemClock.uptimeMillis()
            onnxRuntimeHandler.runOnnxInferenceAsync(tensor) { detections ->
                latestInferenceDurationMs = SystemClock.uptimeMillis() - startTime
                latestDetections = detections
                isNewDetectionAvailable = true
                onnxOverlayView.updateDetections(latestDetections)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error running inference", e)
        }
    }

    // Determine stable object count using Histogram/Mode
    private fun getTargetBBoxCount(): Int {
        if (bboxCountHistory.isEmpty()) return 0
        if (bboxCountHistory.size >= QUICK_OVERRIDE_FRAMES) {
            val last5 = bboxCountHistory.takeLast(QUICK_OVERRIDE_FRAMES)
            if (last5.all { it == last5.first() }) return last5.first()
        }
        return bboxCountHistory.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 0
    }

    private fun processMarkerlessHitTest(arSceneView: ARSceneView, frame: Frame, deviceRotation: Int) {
        if (!isNewDetectionAvailable) return
        isNewDetectionAvailable = false

        val hitTestStartTime = SystemClock.uptimeMillis()
        val cameraPose = frame.camera.pose
        val cameraPos = Float3(cameraPose.tx(), cameraPose.ty(), cameraPose.tz())

        bboxCountHistory.add(latestDetections.size)
        if (bboxCountHistory.size > MAX_HISTORY_FRAMES) bboxCountHistory.removeAt(0)

        val targetModelCount = getTargetBBoxCount()
        var filteredDetections = latestDetections
        if (latestDetections.size > targetModelCount) {
            filteredDetections = latestDetections.sortedByDescending { it.confidence }.take(targetModelCount)
        }

        val viewW = arSceneView.width.toFloat()
        val viewH = arSceneView.height.toFloat()
        val claimedModels = mutableSetOf<Node>()
        if (filteredDetections.isEmpty()) {
            hideUnlockedModels(claimedModels, hitTestStartTime)
            return
        }

        val currentFrameMat: Mat? = getGrayscaleMatFromFrame(frame, deviceRotation)
        var maxPosShift = 0f
        try {
            for (det in filteredDetections) {
                val bbox = det.boundingBox

                val cvResult = refineCenterWithOpenCV(currentFrameMat, bbox, viewW, viewH)
                val hitPoints = collectHitPoints(frame, cvResult, bbox, viewW, viewH)
                if (hitPoints.isEmpty()) continue

                val validPoints = filterDepthOutliers(hitPoints, cameraPos)
                if (validPoints.isEmpty()) continue

                var sumX = 0f; var sumY = 0f; var sumZ = 0f
                for (p in validPoints) { sumX += p.x; sumY += p.y; sumZ += p.z }
                val calculatedPos = Float3(sumX / validPoints.size.toFloat(), sumY / validPoints.size.toFloat(), sumZ / validPoints.size.toFloat())
                val planeNormal = calculatePlaneNormal(validPoints, calculatedPos, cameraPos)
                val baseRot = lookRotation(forward = Float3(0f, -1f, 0f), up = planeNormal)
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
                claimedModels.add(targetModel)

                val state = modelStates.getOrPut(targetModel) { ModelState() }
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
                    state.anchor?.detach()
                    val poseToAnchor = Pose(
                        floatArrayOf(state.bestPos.x, state.bestPos.y, state.bestPos.z),
                        floatArrayOf(state.bestRot.x, state.bestRot.y, state.bestRot.z, state.bestRot.w)
                    )
                    state.anchor = arSceneView.session?.createAnchor(poseToAnchor)
                }

                val renderPos = if (state.poseHistory.isNotEmpty()) calculatedPos else calculatedPos
                val renderRot = state.bestRot
                val alpha = calculateDynamicAlpha(targetModel.position, renderPos)
                targetModel.position = mix(targetModel.position, renderPos, alpha)
                targetModel.quaternion = slerp(targetModel.quaternion, renderRot, alpha)
            }
        } finally {
            currentFrameMat?.release()
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

    private fun refineCenterWithOpenCV(fullMat: Mat?, bbox: RectF, viewW: Float, viewH: Float): RefinedResult {
        val defaultCx = bbox.centerX() * viewW
        val defaultCy = bbox.centerY() * viewH
        if (fullMat == null || fullMat.empty()) return RefinedResult(defaultCx, defaultCy, 0f, 0f, 0f, 0f, false)

        var croppedMat: Mat? = null
        var edges: Mat? = null
        try {
            val matW = fullMat.cols()
            val matH = fullMat.rows()
            val padX = bbox.width() * BBOX_PADDING
            val padY = bbox.height() * BBOX_PADDING
            val left = max(0f, (bbox.left - padX) * matW).toInt()
            val top = max(0f, (bbox.top - padY) * matH).toInt()
            val right = min(matW.toFloat(), (bbox.right + padX) * matW).toInt()
            val bottom = min(matH.toFloat(), (bbox.bottom + padY) * matH).toInt()
            val cropW = right - left
            val cropH = bottom - top
            if (cropW <= 20 || cropH <= 20) return RefinedResult(defaultCx, defaultCy, 0f, 0f, 0f, 0f, false)

            croppedMat = Mat(fullMat, Rect(left, top, cropW, cropH))
            val centerInCrop = OpenCVPoint(cropW / 2.0, cropH / 2.0)
            val maskSize = Size(cropW * 0.25, cropH * 0.25)
            Imgproc.ellipse(croppedMat, centerInCrop, maskSize, 0.0, 0.0, 360.0, Scalar(0.0), -1)

            edges = Mat()
            Imgproc.GaussianBlur(croppedMat, croppedMat, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(croppedMat, edges, 40.0, 120.0)

            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            var bestEllipse: RotatedRect? = null
            var maxCircularity = 0f
            for (contour in contours) {
                if (contour.toArray().size >= 5) {
                    val points2f = MatOfPoint2f(*contour.toArray())
                    val ellipse = Imgproc.fitEllipse(points2f)
                    if (ellipse.size.width > cropW * 0.95 || ellipse.size.height > cropH * 0.95) continue
                    val circ = (min(ellipse.size.width, ellipse.size.height) / max(ellipse.size.width, ellipse.size.height)).toFloat()
                    if (circ > 0.4f && circ > maxCircularity) {
                        maxCircularity = circ
                        bestEllipse = ellipse
                    }
                }
            }
            if (bestEllipse != null) {
                val absCx = left + bestEllipse.center.x
                val absCy = top + bestEllipse.center.y
                val finalCx = (absCx / matW.toFloat()) * viewW
                val finalCy = (absCy / matH.toFloat()) * viewH
                val finalW = (bestEllipse.size.width / matW.toFloat()) * viewW
                val finalH = (bestEllipse.size.height / matH.toFloat()) * viewH
                return RefinedResult(finalCx.toFloat(), finalCy.toFloat(), finalW.toFloat(), finalH.toFloat(), bestEllipse.angle.toFloat(), maxCircularity, true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV Error", e)
        } finally {
            croppedMat?.release()
            edges?.release()
        }
        return RefinedResult(defaultCx, defaultCy, 0f, 0f, 0f, 0f, false)
    }

    // ==========================================
    // MATH & UTILITIES
    // ==========================================
    private fun collectHitPoints(frame: Frame, cvRes: RefinedResult, bbox: RectF, viewW: Float, viewH: Float): List<Float3> {
        val points = mutableListOf<Float3>()
        frame.hitTest(cvRes.cx, cvRes.cy).firstOrNull { it.trackable is Plane || it.trackable is Point }?.let {
            points.add(Float3(it.hitPose.tx(), it.hitPose.ty(), it.hitPose.tz()))
        }
        val hitRadiusW = if (cvRes.isFound) cvRes.width / 2f else (bbox.width() * viewW * 0.7f) / 2f
        val hitRadiusH = if (cvRes.isFound) cvRes.height / 2f else (bbox.height() * viewH * 0.7f) / 2f
        val angleRad = Math.toRadians(cvRes.angle.toDouble()).toFloat()
        for (i in 0 until HITTEST_POINTS) {
            val t = (2f * Math.PI.toFloat() * i) / HITTEST_POINTS
            val dx = hitRadiusW * cos(t) * cos(angleRad) - hitRadiusH * sin(t) * sin(angleRad)
            val dy = hitRadiusW * cos(t) * sin(angleRad) + hitRadiusH * sin(t) * cos(angleRad)
            frame.hitTest(cvRes.cx + dx, cvRes.cy + dy).firstOrNull { it.trackable is Plane || it.trackable is Point }?.let {
                points.add(Float3(it.hitPose.tx(), it.hitPose.ty(), it.hitPose.tz()))
            }
        }
        return points
    }

    private fun filterDepthOutliers(points: List<Float3>, cameraPos: Float3): List<Float3> {
        if (points.size <= 2) return points

        val depths = points.map { distance(it, cameraPos) }.sorted()
        val medianDepth = depths[depths.size / 2]
        return points.filter { abs(distance(it, cameraPos) - medianDepth) < DEPTH_OUTLIER_THRESHOLD }
    }

    private fun calculatePlaneNormal(points: List<Float3>, center: Float3, cameraPos: Float3): Float3 {
        if (points.size < 3) return normalize(cameraPos - center)

        var accumNormal = Float3(0f, 0f, 0f)
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            accumNormal += cross(p1 - center, p2 - center)
        }
        var finalNormal = normalize(accumNormal)
        val toCamera = normalize(cameraPos - center)
        if (dot(finalNormal, toCamera) < 0) finalNormal = -finalNormal
        return if (length(finalNormal).isNaN()) Float3(0f, 0f, 1f) else finalNormal
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
    private fun getGrayscaleMatFromFrame(frame: Frame, deviceRotation: Int): Mat? {
        val image = try { frame.acquireCameraImage() } catch (e: Exception) { return null }
        try {
            val yPlane = image.planes[0].buffer.apply { rewind() }
            val width = image.width
            val height = image.height
            val yRowStride = image.planes[0].rowStride
            val mat = Mat(height, width, CvType.CV_8UC1)
            val requiredSize = if (yRowStride == width) width * height else width
            if (yPlaneBytes == null || yPlaneBytes!!.size != requiredSize) yPlaneBytes = ByteArray(requiredSize)
            val bytes = yPlaneBytes!!
            if (yRowStride == width) {
                yPlane.get(bytes); mat.put(0, 0, bytes)
            } else {
                for (row in 0 until height) {
                    yPlane.position(row * yRowStride); yPlane.get(bytes); mat.put(row, 0, bytes)
                }
            }

            val finalRotation = (90 - deviceRotation + 360) % 360
            val rotateCode = when (finalRotation) {
                90 -> Core.ROTATE_90_CLOCKWISE
                180 -> Core.ROTATE_180
                270 -> Core.ROTATE_90_COUNTERCLOCKWISE
                else -> -1
            }
            if (rotateCode != -1) {
                val rotatedMat = Mat()
                Core.rotate(mat, rotatedMat, rotateCode)
                mat.release()
                return rotatedMat
            }
            return mat
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Y-Plane Mat", e)
            return null
        } finally {
            image.close()
        }
    }
    
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
                            val markerName = filename.substringBeforeLast(".")
                            Log.d(TAG, "Loaded Marker: $filename.substringBeforeLast(\".\") ✅")
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