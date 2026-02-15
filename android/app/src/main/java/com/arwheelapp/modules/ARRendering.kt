package com.arwheelapp.modules

import android.content.Context
import android.graphics.RectF
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import com.google.ar.core.*
import com.arwheelapp.processor.FrameConverter
import com.arwheelapp.processor.OnnxRuntimeHandler
import com.arwheelapp.utils.ARMode
import com.arwheelapp.utils.Detection
import com.arwheelapp.utils.ModelState
import dev.romainguy.kotlin.math.*
import kotlin.math.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.node.Node

class ARRendering(
    private val context: Context,
    private val onnxOverlayView: OnnxOverlayView,
    private val arSceneView: ARSceneView
) {
    companion object {
        private const val TAG = "ARRendering"
        
        // Interval & Speed Constants
        private const val MIN_INFERENCE_INTERVAL = 200L     // 5 FPS
        private const val MAX_INFERENCE_INTERVAL = 50L      // 20 FPS
        private const val MIN_HITTEST_INTERVAL = 66L        // 15 FPS
        private const val MAX_HITTEST_INTERVAL = 33L        // 30 FPS
        private const val SPEED_CHECK_INTERVAL = 100L       // Check speed every 0.1s
        private const val MOVEMENT_THRESHOLD_HIGH = 0.3f    // 0.3 m/s considered "Fast"

        // Markerless Locking Constants
        private const val LOCK_DISTANCE_THRESHOLD = 0.05f
        private const val UNLOCK_DISTANCE_THRESHOLD = 0.20f
        private const val FRAMES_TO_LOCK = 15
        private const val DONUT_POINTS = 8
        private const val DONUT_RADIUS = 0.70f
        private const val ANGLE_TOLERANCE_DEG = 25f
        private const val RATIO_TOLERANCE = 0.01f

        // Dynamic Lerp Constants
        private const val MIN_ALPHA = 0.05f
        private const val MAX_ALPHA = 0.6f
        private const val MIN_DIST = 0.02f
        private const val MAX_DIST = 0.3f
    }

    private val modelManager = ModelManager(arSceneView)
    private val frameConverter = FrameConverter()
    private val onnxRuntimeHandler = OnnxRuntimeHandler(context)

    private var previousMode: ARMode? = null
    private var lastCameraPose: Pose? = null
    private var lastSpeedCheckTime = 0L
    private var lastInferenceTime = 0L
    private var lastHitTestTime = 0L

    private var currentInferenceInterval = MIN_INFERENCE_INTERVAL
    private var currentHitTestInterval = MIN_HITTEST_INTERVAL

    private val modelPool = mutableListOf<Node>()
    private val augmentedImageMap = mutableMapOf<AugmentedImage, AugmentedImageNode>()
    private val markerlessActiveModels = mutableListOf<Node>()
    private val modelStates = mutableMapOf<Node, ModelState>()

    @Volatile private var latestDetections: List<Detection> = emptyList()
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
            markerlessActiveModels.clear()
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

                TrackingState.STOPPED -> {
                    augmentedImageMap.remove(image)?.let { node ->
                        arSceneView.removeChildNode(node)
                        node.destroy()
                    }
                }

                TrackingState.PAUSED -> {
                    augmentedImageMap[image]?.isVisible = false
                }
            }
        }
    }

    // ==========================================
    // MARKERLESS LOGIC
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
            currentHitTestInterval = lerp(MIN_HITTEST_INTERVAL.toFloat(), MAX_HITTEST_INTERVAL.toFloat(), speedFactor).toLong()
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
                onnxOverlayView.updateDetections(latestDetections)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error running inference", e)
        }
    }

    private fun processMarkerlessHitTest(arSceneView: ARSceneView, frame: Frame) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastHitTestTime < currentHitTestInterval) return
        lastHitTestTime = currentTime

        val detections = latestDetections
        if (detections.isEmpty()) {
            hideUnlockedModels(emptySet())
            return
        }

        val claimedModels = mutableSetOf<Node>()
        val viewW = arSceneView.width.toFloat()
        val viewH = arSceneView.height.toFloat()
        val cameraPos = frame.camera.pose.let { Float3(it.tx(), it.ty(), it.tz()) }

        for (det in detections) {
            val hitPoints = collectHitPoints(frame, det.boundingBox, viewW, viewH)
            if (hitPoints.isEmpty()) continue

            val (bestPos, validPoints) = calculatePositionAndValidPoints(hitPoints)
            val planeNormal = calculatePlaneNormal(validPoints, bestPos, cameraPos)

            val bboxW = det.boundingBox.width() * viewW
            val bboxH = det.boundingBox.height() * viewH
            val currentAspectRatio = (min(bboxW, bboxH) / max(bboxW, bboxH)).toFloat()

            if (!isAngleValid(currentAspectRatio, planeNormal, bestPos, cameraPos)) continue

            val finalRot = lookRotation(forward = planeNormal, up = Float3(0f, 1f, 0f)) * Quaternion.fromAxisAngle(Float3(1f, 0f, 0f), -90f)

            val closestModel = markerlessActiveModels
                .asSequence()
                .filter { it !in claimedModels }
                .minByOrNull { distance(it.position, bestPos) }

            if (closestModel != null && distance(closestModel.position, bestPos) < snapThreshold) {
                processModelLocking(closestModel, bestPos, finalRot, currentAspectRatio)
                claimedModels.add(closestModel)
            } else {
                val newModel = createAndSetupNewModel(bestPos, finalRot, currentAspectRatio)
                claimedModels.add(newModel)
            }
        }

        hideUnlockedModels(claimedModels)
    }

    private fun collectHitPoints(frame: Frame, bbox: RectF, viewW: Float, viewH: Float): List<Float3> {
        val cx = bbox.centerX() * viewW
        val cy = bbox.centerY() * viewH
        val points = mutableListOf<Float3>()

        // Center Hit
        frame.hitTest(cx, cy).firstOrNull { it.trackable is Plane || it.trackable is Point }?.let {
            points.add(Float3(it.hitPose.tx(), it.hitPose.ty(), it.hitPose.tz()))
        }

        // Donut Hits
        val radiusX = (bbox.width() * viewW * DONUT_RADIUS) / 2f
        val radiusY = (bbox.height() * viewH * DONUT_RADIUS) / 2f
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

    private fun isAngleValid(aspectRatio: Float, normal: Float3, pos: Float3, camPos: Float3): Boolean {
        val expectedAngleDeg = Math.toDegrees(acos(aspectRatio.toDouble())).toFloat()
        val toCamera = normalize(camPos - pos)
        val dotNormal = dot(normal, toCamera).toDouble().coerceIn(-1.0, 1.0)
        val actualAngleDeg = Math.toDegrees(acos(abs(dotNormal))).toFloat()
        return abs(expectedAngleDeg - actualAngleDeg) <= ANGLE_TOLERANCE_DEG
    }

    private fun processModelLocking(model: Node, bestPos: Float3, finalRot: Quaternion, currentRatio: Float) {
        val state = modelStates.getOrPut(model) { ModelState() }
        val bestRatio = state.bestAspectRatio

        val (targetPos, targetRot) = if (currentRatio >= bestRatio - RATIO_TOLERANCE) {
            if (currentRatio > bestRatio) state.bestAspectRatio = currentRatio
            bestPos to finalRot
        } else {
            model.position to model.quaternion
        }

        val distFromTarget = distance(targetPos, model.position)

        if (state.isLocked) {
            if (distFromTarget > UNLOCK_DISTANCE_THRESHOLD) {
                state.isLocked = false
                state.stableFrameCount = 0
                updateModelTransform(model, targetPos, targetRot)
            }
        } else {
            if (distFromTarget < LOCK_DISTANCE_THRESHOLD) {
                state.stableFrameCount++
                if (state.stableFrameCount >= FRAMES_TO_LOCK) state.isLocked = true
            } else {
                state.stableFrameCount = 0
            }
            updateModelTransform(model, targetPos, targetRot)
        }
        model.isVisible = true
    }

    private fun createAndSetupNewModel(pos: Float3, rot: Quaternion, ratio: Float): Node {
        val newModel = getOrCreateModel(modelPath)
        modelStates[newModel] = ModelState().apply { bestAspectRatio = ratio }
        
        newModel.apply {
            position = pos
            quaternion = rot
            isVisible = true
            if (parent == null) arSceneView.addChildNode(this)
        }

        if (!markerlessActiveModels.contains(newModel)) markerlessActiveModels.add(newModel)
        return newModel
    }

    private fun hideUnlockedModels(claimedModels: Set<Node>) {
        for (model in markerlessActiveModels) {
            if (model !in claimedModels) {
                model.isVisible = (modelStates[model]?.isLocked == true)
            }
        }
    }

    // ==========================================
    // MATH & UTILITIES
    // ==========================================
    private fun updateModelTransform(model: Node, targetPos: Float3, targetRot: Quaternion) {
        val dynamicAlpha = calculateDynamicAlpha(model.position, targetPos)
        // model.position = dev.romainguy.kotlin.math.mix(model.position, targetPos, dynamicAlpha)
        model.position = mix(model.position, targetPos, dynamicAlpha)
        model.quaternion = slerp(model.quaternion, targetRot, dynamicAlpha / 2)
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

    private fun calculatePositionAndValidPoints(points: List<Float3>): Pair<Float3, List<Float3>> {
        if (points.size <= 1) return Pair(points.firstOrNull() ?: Float3(0f,0f,0f), points)

        val meanPos = Float3(
            points.map { it.x }.average().toFloat(),
            points.map { it.y }.average().toFloat(),
            points.map { it.z }.average().toFloat()
        )

        val distances = points.map { distance(it, meanPos) }
        val meanDist = distances.average()
        val stdDev = sqrt(distances.map { (it - meanDist).pow(2) }.average())
        val threshold = meanDist + (1.5 * stdDev)

        val validPoints = points.filterIndexed { index, _ -> distances[index] <= threshold }
        if (validPoints.isEmpty()) return Pair(meanPos, points)

        val finalAvg = Float3(
            validPoints.map { it.x }.average().toFloat(),
            validPoints.map { it.y }.average().toFloat(),
            validPoints.map { it.z }.average().toFloat()
        )

        return Pair(finalAvg, validPoints)
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

    private fun calculateDynamicAlpha(currentPos: Float3, targetPos: Float3): Float {
        val t = ((distance(currentPos, targetPos) - MIN_DIST) / (MAX_DIST - MIN_DIST)).coerceIn(0f, 1f)
        return MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * t
    }

    // private fun distance(p1: Float3, p2: Float3): Float {
    //     val dx = p1.x - p2.x
    //     val dy = p1.y - p2.y
    //     val dz = p1.z - p2.z
    //     return sqrt(dx * dx + dy * dy + dz * dz)
    // }
    //!!!!!!!!!!!!!!!!!
    private fun distance(p1: Float3, p2: Float3) = length(p1 - p2)
    private fun distance(pose1: Pose, pose2: Pose) = sqrt(
        (pose1.tx() - pose2.tx()).pow(2) + 
        (pose1.ty() - pose2.ty()).pow(2) + 
        (pose1.tz() - pose2.tz()).pow(2)
    )

    // ==========================================
    // EXTERNAL APIs
    // ==========================================
    private fun getOrCreateModel(path: String): Node {
        return modelPool.find { it.parent == null || !it.isVisible }?.apply { isVisible = true }
            ?: modelManager.createNewModel(path).also { modelPool.add(it) }
    }

    fun updateNewModel(path: String) {
        modelPath = path
        modelPool.forEach { modelManager.changeModel(it, path) }
    }

    fun updateModelSize(sizeInch: Float) {
        val sizeCm = sizeInch * 2.54f
        snapThreshold = sizeCm / 100.0f
        val scaleFactor = sizeCm / 45.72f   // 45.72cm = 18inch
        modelPool.forEach { modelManager.changeModelSize(it, scaleFactor) }
    }

    fun clear() {
        modelPool.forEach { it.destroy() }
        modelPool.clear()
        augmentedImageMap.values.forEach { it.destroy() }
        augmentedImageMap.clear()
        markerlessActiveModels.clear()
        modelStates.clear()
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
                            Log.d(TAG, "Loaded Marker: $filename.substringBeforeLast(".") ✅")
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