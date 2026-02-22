package com.arwheelapp.modules

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.arwheelapp.processor.FrameConverter
import com.arwheelapp.processor.OnnxRuntimeHandler
import com.arwheelapp.utils.ARMode
import com.arwheelapp.utils.Detection
import com.arwheelapp.utils.ModelState
import com.google.ar.core.*
import dev.romainguy.kotlin.math.*
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.collision.MathHelper.lerp
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
import org.opencv.core.Rect
import org.opencv.core.RotatedRect
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

        // Interval & Speed Constants
        private const val MIN_INFERENCE_INTERVAL = 200L     // 5 FPS
        private const val MAX_INFERENCE_INTERVAL = 50L      // 20 FPS
        private const val SPEED_CHECK_INTERVAL = 100L       // Check speed every 0.1s
        private const val MOVEMENT_THRESHOLD_HIGH = 0.3f    // 0.3 m/s considered "Fast"

        // Hittest Constants
        private const val DONUT_POINTS = 8
        private const val DONUT_RADIUS = 0.50f
        private const val BBOX_PADDING = 0.15f

        // Dynamic Lerp Constants
        private const val MIN_ALPHA = 0.04f
        private const val MAX_ALPHA = 0.6f
        private const val MIN_DIST = 0.02f
        private const val MAX_DIST = 0.3f

        // Flow Constants
        private const val FREEZE_DISTANCE = 0.01f           // 1 cm (stop if move less than this)
        private const val MIN_DEPTH_SANITY = 0.2f           // min depth 20 cm (prevent hits on screen)
        private const val MAX_DEPTH_SANITY = 10.0f          // max depth 10 m (prevent hits too far)
        private const val HIDE_TIMEOUT_MS = 1000L
        private const val CAMERA_IDLE_THRESHOLD = 0.02f     // camera barely moves if less than 2 cm
        private const val RATIO_ERROR_THRESHOLD = 0.20f     // 10% (0.90 - 1.10) bbox ratio for anchor
        private const val STABILITY_THRESHOLD = 0.03f
        private const val REQUIRED_STABLE_FRAMES = 8
    }

    private val modelManager = ModelManager(arSceneView)
    private val frameConverter = FrameConverter()
    private val onnxRuntimeHandler = OnnxRuntimeHandler(context)

    private var previousMode: ARMode? = null
    private var lastCameraPose: Pose? = null
    private var lastSpeedCheckTime = 0L
    private var lastInferenceTime = 0L
    private var currentInferenceInterval = MIN_INFERENCE_INTERVAL

    private val modelPool = mutableListOf<Node>()
    private val augmentedImageMap = mutableMapOf<AugmentedImage, AugmentedImageNode>()
    private val markerlessActiveModels = mutableListOf<Node>()
    private val modelStates = mutableMapOf<Node, ModelState>()

    @Volatile private var latestDetections: List<Detection> = emptyList()
    @Volatile private var isNewDetectionAvailable = false

    @Volatile private var snapThreshold = 0.4572f
    @Volatile private var modelPath = "models/wheel1.glb"   // !!Change to UI

    fun render(arSceneView: ARSceneView, frame: Frame, currentMode: ARMode) {
        if (previousMode != currentMode) {
            handleModeSwitch()
            previousMode = currentMode
        }

        calculateDynamicIntervals(frame)

        when (currentMode) {
            ARMode.MARKER_BASED -> processMarkerBased(arSceneView, frame)
            ARMode.MARKERLESS -> {
                processMarkerlessInference(frame)
                processMarkerlessHitTest(arSceneView, frame)
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

    // ==========================================
    // MARKERLESS LOGIC (AI + OpenCV + ARCore)
    // ==========================================
    private fun calculateDynamicIntervals(frame: Frame) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastSpeedCheckTime < SPEED_CHECK_INTERVAL) return

        val currentPose = frame.camera.pose
        lastCameraPose?.let { prevPose ->
            val dist = distance(currentPose, prevPose)
            val timeDeltaSecs = (currentTime - lastSpeedCheckTime) / 1000f
            val speed = if (timeDeltaSecs > 0) dist / timeDeltaSecs else 0f
            val speedFactor = (speed / MOVEMENT_THRESHOLD_HIGH).coerceIn(0f, 1f)

            currentInferenceInterval = lerp(MIN_INFERENCE_INTERVAL.toFloat(), MAX_INFERENCE_INTERVAL.toFloat(), speedFactor).toLong()
        }

        lastCameraPose = currentPose
        lastSpeedCheckTime = currentTime
    }

    private fun processMarkerlessInference(frame: Frame) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastInferenceTime < currentInferenceInterval) return
        lastInferenceTime = currentTime

        try {
            val tensor = frameConverter.convertFrameToTensor(frame)
            onnxRuntimeHandler.runOnnxInferenceAsync(tensor) { detections ->
                latestDetections = detections
                isNewDetectionAvailable = true
                onnxOverlayView.updateDetections(latestDetections)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error running inference", e)
        }
    }

    private fun processMarkerlessHitTest(arSceneView: ARSceneView, frame: Frame) {
        if (!isNewDetectionAvailable) return
        isNewDetectionAvailable = false

        val detections = latestDetections 
        val cameraPose = frame.camera.pose
        val cameraPos = Float3(cameraPose.tx(), cameraPose.ty(), cameraPose.tz())
        val currentTime = SystemClock.uptimeMillis()

        if (detections.isEmpty()) {
            hideUnlockedModels(emptySet(), currentTime, cameraPos)
            return
        }

        val claimedModels = mutableSetOf<Node>()
        val viewW = arSceneView.width.toFloat()
        val viewH = arSceneView.height.toFloat()
        val currentFrameMat: Mat? = getGrayscaleMatFromFrame(frame)

        for (det in detections) {
            val bbox = det.boundingBox

            val (trueCx, trueCy) = refineCenterWithOpenCV(currentFrameMat, bbox, viewW, viewH)

            val hitPoints = collectHitPoints(frame, trueCx, trueCy, bbox.width() * viewW, bbox.height() * viewH)
            if (hitPoints.isEmpty()) continue

            val (calculatedPos, validPoints) = calculatePositionAndValidPoints(hitPoints, cameraPos)

            val distToCamera = distance(cameraPos, calculatedPos)
            if (distToCamera < MIN_DEPTH_SANITY || distToCamera > MAX_DEPTH_SANITY) continue 

            val planeNormal = calculatePlaneNormal(validPoints, calculatedPos, cameraPos)
            val calculatedRot = lookRotation(forward = Float3(0f, -1f, 0f), up = planeNormal)
            val minDistanceAllowed = snapThreshold * 2.5f

            val closestModel = markerlessActiveModels
                .asSequence()
                .filter { it !in claimedModels }
                .minByOrNull { distance(it.position, calculatedPos) }

            val targetModel = if (closestModel != null && distance(closestModel.position, calculatedPos) < minDistanceAllowed) {
                closestModel
            } else {
                getAvailableMarkerlessModel(claimedModels)
            }

            claimedModels.add(targetModel)
            targetModel.isVisible = true

            val state = modelStates.getOrPut(targetModel) { ModelState() }
            state.lastDetectionTime = currentTime

            if (state.anchor != null && state.anchor?.trackingState == TrackingState.TRACKING) {
                val p = state.anchor!!.pose
                updateModelTransform(targetModel, Float3(p.tx(), p.ty(), p.tz()), state.bestRot)
                continue
            }

            val isStable = state.lastStablePos?.let { lastPos ->
                distance(lastPos, calculatedPos) < STABILITY_THRESHOLD
            } ?: false

            if (isStable) state.consecutiveStableFrames++ else state.consecutiveStableFrames = 0

            state.lastStablePos = calculatedPos

            val ratioError = abs(1.0f - (bbox.width() / bbox.height()))

            if (ratioError <= RATIO_ERROR_THRESHOLD && state.consecutiveStableFrames >= REQUIRED_STABLE_FRAMES) {
                if (ratioError < state.bestRatioError) {
                    state.bestRatioError = ratioError
                    state.bestPos = calculatedPos
                    state.bestRot = calculatedRot

                    state.anchor?.detach()
                    val poseToAnchor = Pose(
                        floatArrayOf(calculatedPos.x, calculatedPos.y, calculatedPos.z),
                        floatArrayOf(calculatedRot.x, calculatedRot.y, calculatedRot.z, calculatedRot.w)
                    )
                    state.anchor = arSceneView.session?.createAnchor(poseToAnchor)
                }
            }

            val renderPos = if (state.bestRatioError < Float.MAX_VALUE) state.bestPos else calculatedPos
            val renderRot = if (state.bestRatioError < Float.MAX_VALUE) state.bestRot else calculatedRot

            if (distance(targetModel.position, renderPos) > FREEZE_DISTANCE) {
                updateModelTransform(targetModel, renderPos, renderRot)
            }
        }

        currentFrameMat?.release()
        hideUnlockedModels(claimedModels, currentTime, cameraPos)
    }

    private fun getGrayscaleMatFromFrame(frame: Frame): Mat? {
        val image = try {
            frame.acquireCameraImage()
        } catch (e: Exception) {
            return null
        }

        try {
            val yPlane = image.planes[0].buffer.apply { rewind() }
            val width = image.width
            val height = image.height
            val yRowStride = image.planes[0].rowStride

            val mat = Mat(height, width, CvType.CV_8UC1)

            if (yRowStride == width) {
                val bytes = ByteArray(width * height)
                yPlane.get(bytes)
                mat.put(0, 0, bytes)
            } else {
                val bytes = ByteArray(width)
                for (row in 0 until height) {
                    yPlane.position(row * yRowStride)
                    yPlane.get(bytes)
                    mat.put(row, 0, bytes)
                }
            }

            val rotatedMat = Mat()
            Core.rotate(mat, rotatedMat, Core.ROTATE_90_CLOCKWISE)
            mat.release()

            return rotatedMat
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Y-Plane Mat", e)
            return null
        } finally {
            image.close()
        }
    }

    private fun refineCenterWithOpenCV(fullMat: Mat?, bbox: RectF, viewW: Float, viewH: Float): Pair<Float, Float> {
        val defaultCx = bbox.centerX() * viewW
        val defaultCy = bbox.centerY() * viewH

        if (fullMat == null || fullMat.empty()) return Pair(defaultCx, defaultCy)

        try {
            val matW = fullMat.cols()
            val matH = fullMat.rows()

            val padX = bbox.width() * BBOX_PADDING
            val padY = bbox.height() * BBOX_PADDING

            val left = max(0f, (bbox.left - padX) * matW).toInt()
            val top = max(0f, (bbox.top - padY) * matH).toInt()
            val right = min(matW.toFloat(), (bbox.right + padX) * matW).toInt()
            val bottom = min(matH.toFloat(), (bbox.bottom + padY) * matH).toInt()

            val cropRect = Rect(left, top, right - left, bottom - top)
            if (cropRect.width <= 20 || cropRect.height <= 20) return Pair(defaultCx, defaultCy)

            val croppedMat = Mat(fullMat, cropRect)

            val edges = Mat()
            Imgproc.GaussianBlur(croppedMat, croppedMat, Size(7.0, 7.0), 1.5)
            Imgproc.Canny(croppedMat, edges, 40.0, 120.0)

            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            var bestEllipse: RotatedRect? = null
            var maxArea = 0.0

            for (contour in contours) {
                if (contour.toArray().size >= 5) {
                    val points2f = MatOfPoint2f(*contour.toArray())
                    val ellipse = Imgproc.fitEllipse(points2f)

                    val area = Math.PI * (ellipse.size.width / 2) * (ellipse.size.height / 2)
                    val ratio = min(ellipse.size.width, ellipse.size.height) / max(ellipse.size.width, ellipse.size.height)

                    if (ratio > 0.4 && area > maxArea) {
                        maxArea = area
                        bestEllipse = ellipse
                    }
                }
            }

            croppedMat.release()
            edges.release()
            hierarchy.release()

            if (bestEllipse != null) {
                val absoluteCxInMat = left + bestEllipse.center.x
                val absoluteCyInMat = top + bestEllipse.center.y

                val finalCxInView = (absoluteCxInMat / matW.toFloat()) * viewW
                val finalCyInView = (absoluteCyInMat / matH.toFloat()) * viewH

                return Pair(finalCxInView.toFloat(), finalCyInView.toFloat())
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV Ellipse Fitting Failed", e)
        }
        return Pair(defaultCx, defaultCy)
    }

    // ==========================================
    // MATH & UTILITIES
    // ==========================================
    private fun collectHitPoints(frame: Frame, cx: Float, cy: Float, width: Float, height: Float): List<Float3> {
        val points = mutableListOf<Float3>()

        // Center Hit
        frame.hitTest(cx, cy).firstOrNull { it.trackable is Plane || it.trackable is Point }?.let {
            points.add(Float3(it.hitPose.tx(), it.hitPose.ty(), it.hitPose.tz()))
        }

        // Donut Hits
        val radiusX = (width * DONUT_RADIUS) / 2f
        val radiusY = (height * DONUT_RADIUS) / 2f
        for (i in 0 until DONUT_POINTS) {
            val angle = (2 * Math.PI * i) / DONUT_POINTS
            val dx = (cos(angle) * radiusX).toFloat()
            val dy = (sin(angle) * radiusY).toFloat()
            frame.hitTest(cx + dx, cy + dy).firstOrNull { it.trackable is Plane || it.trackable is Point }?.let {
                points.add(Float3(it.hitPose.tx(), it.hitPose.ty(), it.hitPose.tz()))
            }
        }
        return points
    }

    private fun calculatePositionAndValidPoints(points: List<Float3>, cameraPos: Float3): Pair<Float3, List<Float3>> {
        if (points.size <= 1) return Pair(points.firstOrNull() ?: Float3(0f,0f,0f), points)

        val sortedPoints = points.sortedBy { distance(it, cameraPos) }
        val takeCount = max(1, sortedPoints.size / 2)
        val closestPoints = sortedPoints.take(takeCount)

        val finalAvg = Float3(
            closestPoints.map { it.x }.average().toFloat(),
            closestPoints.map { it.y }.average().toFloat(),
            closestPoints.map { it.z }.average().toFloat()
        )

        return Pair(finalAvg, closestPoints)
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

    private fun updateModelTransform(model: Node, targetPos: Float3, targetRot: Quaternion) {
        val dynamicAlpha = calculateDynamicAlpha(model.position, targetPos)
        model.position = mix(model.position, targetPos, dynamicAlpha)
        model.quaternion = slerp(model.quaternion, targetRot, dynamicAlpha)
    }

    private fun calculateDynamicAlpha(currentPos: Float3, targetPos: Float3): Float {
        val t = ((distance(currentPos, targetPos) - MIN_DIST) / (MAX_DIST - MIN_DIST)).coerceIn(0f, 1f)
        return MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * t
    }

    private fun distance(p1: Float3, p2: Float3) = length(p1 - p2)
    private fun distance(pose1: Pose, pose2: Pose) = sqrt(
        (pose1.tx() - pose2.tx()).pow(2) + 
        (pose1.ty() - pose2.ty()).pow(2) + 
        (pose1.tz() - pose2.tz()).pow(2)
    )

    private fun hideUnlockedModels(claimedModels: Set<Node>, currentTime: Long, currentCameraPos: Float3) {
        val isCameraIdle = lastCameraPose?.let { prevPose ->
            val prevCameraPos = Float3(prevPose.tx(), prevPose.ty(), prevPose.tz())
            distance(currentCameraPos, prevCameraPos) < CAMERA_IDLE_THRESHOLD
        } ?: false

        for (model in markerlessActiveModels) {
            if (model !in claimedModels) {
                val state = modelStates[model] ?: continue
                val timeSinceLastDet = currentTime - state.lastDetectionTime

                if (timeSinceLastDet > HIDE_TIMEOUT_MS && !isCameraIdle) {
                    model.isVisible = false
                    state.bestRatioError = Float.MAX_VALUE
                    state.anchor?.detach()
                    state.anchor = null
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
                val folder = "markers"

                assetManager.list(folder)?.filter { it.endsWith(".jpg", true) || it.endsWith(".png", true) }?.forEach { filename ->
                    try {
                        assetManager.open("$folder/$filename").use { 
                            val bitmap = BitmapFactory.decodeStream(it)
                            database.addImage(filename.substringBeforeLast("."), bitmap, markerSize)
                            val markerName = filename.substringBeforeLast(".")
                            Log.d(TAG, "Loaded Marker: $markerName ✅")
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