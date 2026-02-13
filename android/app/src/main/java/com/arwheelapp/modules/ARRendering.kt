package com.arwheelapp.modules

import android.content.Context
import android.util.Log
import android.os.SystemClock
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.google.ar.core.*
import kotlin.math.*
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.normalize
import dev.romainguy.kotlin.math.length
import dev.romainguy.kotlin.math.slerp
import dev.romainguy.kotlin.math.cross
import dev.romainguy.kotlin.math.dot
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.node.Node
import io.github.sceneview.math.lerp
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.collision.MathHelper.lerp
import java.util.*
import com.arwheelapp.processor.OnnxRuntimeHandler
import com.arwheelapp.processor.FrameConverter
import com.arwheelapp.utils.Detection
import com.arwheelapp.utils.ARMode
import com.arwheelapp.utils.ModelLockState

class ARRendering(private val context: Context, private val onnxOverlayView: OnnxOverlayView, private val arSceneView: ARSceneView) {
    private val modelManager = ModelManager(arSceneView)
    private val frameConverter = FrameConverter()
    private val onnxRuntimeHandler = OnnxRuntimeHandler(context)

    private val TAG = "ARRendering: "

    private var previousMode: ARMode? = null

    // AI Inference FPS
    private val MIN_INFERENCE_INTERVAL = 200L   // 5 FPS
    private val MAX_INFERENCE_INTERVAL = 50L    // 20 FPS

    // HitTest FPS
    private val MIN_HITTEST_INTERVAL = 66L      // 15 FPS
    private val MAX_HITTEST_INTERVAL = 33L      // 30 FPS

    // Timestamps
    private var lastInferenceTime = 0L
    private var lastHitTestTime = 0L
    private var currentInferenceInterval = MIN_INFERENCE_INTERVAL
    private var currentHitTestInterval = MIN_HITTEST_INTERVAL

    // Camera Speed Calculation Variables
    private var lastCameraPose: Pose? = null
    private var lastSpeedCheckTime = 0L
    private val SPEED_CHECK_INTERVAL = 100L     // Check speed every 0.1s
    private val MOVEMENT_THRESHOLD_HIGH = 0.3f  // 0.3 meters/second considered "Fast"

    private val modelPool = mutableListOf<Node>()
    private val augmentedImageMap = mutableMapOf<AugmentedImage, AugmentedImageNode>()
    private val markerlessActiveModels = mutableListOf<Node>()

    // Model Lock States for Markerless
    private val modelLockStates = mutableMapOf<Node, ModelLockState>()
    private val LOCK_DISTANCE_THRESHOLD = 0.05f
    private val UNLOCK_DISTANCE_THRESHOLD = 0.20f
    private val FRAMES_TO_LOCK = 15

    // Dynamic lerp
    private val MIN_ALPHA = 0.05f
    private val MAX_ALPHA = 0.6f
    private val MIN_DIST = 0.02f
    private val MAX_DIST = 0.3f

    private val DONUT_POINTS = 8
    private val DONUT_RADIUS_FACTOR = 0.60f

    @Volatile
    private var latestDetections: List<Detection> = emptyList()

    @Volatile
    private var snapThreshold = 0.4572f

    @Volatile
	private var MODEL_PATH = "models/wheel1.glb"    // !!Change to ui

    fun render(arSceneView: ARSceneView, frame: Frame, currentMode: ARMode) {
        if (previousMode != currentMode) {
            handleModeSwitch()
            previousMode = currentMode
        }

        calculateDynamicIntervals(frame)

        when (currentMode) {
            ARMode.MARKER_BASED -> {
                processMarkerBased(arSceneView, frame)
            }
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

    private fun processMarkerBased(arSceneView: ARSceneView, frame: Frame) {
        val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)

        for (image in updatedAugmentedImages) {
            when (image.trackingState) {
                TrackingState.TRACKING -> {
                    if (!augmentedImageMap.containsKey(image)) {
                        val imageNode = AugmentedImageNode(arSceneView.engine, image)

                        val model = getOrCreateModel(MODEL_PATH)
                        model.isVisible = true 

                        imageNode.addChildNode(model)
                        arSceneView.addChildNode(imageNode)

                        augmentedImageMap[image] = imageNode
                    }

                    val node = augmentedImageMap[image]
                    node?.isVisible = (image.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING)
                }

                TrackingState.STOPPED -> {
                    val node = augmentedImageMap[image]
                    if (node != null) {
                        arSceneView.removeChildNode(node)
                        node.destroy()
                        augmentedImageMap.remove(image)
                    }
                }

                TrackingState.PAUSED -> {
                    augmentedImageMap[image]?.isVisible = false
                }
            }
        }
    }

    private fun calculateDynamicIntervals(frame: Frame) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastSpeedCheckTime < SPEED_CHECK_INTERVAL) return

        val camera = frame.camera
        val currentPose = camera.pose

        if (lastCameraPose != null) {
            // Calculate distance moved
            val distance = distance(currentPose, lastCameraPose!!)

            // Calculate speed (meters per second)
            val timeDeltaSeconds = (currentTime - lastSpeedCheckTime) / 1000f
            val speed = if (timeDeltaSeconds > 0) distance / timeDeltaSeconds else 0f

            // Normalize speed to 0.0 - 1.0 range (based on threshold)
            val speedFactor = (speed / MOVEMENT_THRESHOLD_HIGH).coerceIn(0f, 1f)

            // Linear Interpolation (Lerp) for Intervals
            currentInferenceInterval = lerp(MIN_INFERENCE_INTERVAL.toFloat(), MAX_INFERENCE_INTERVAL.toFloat(), speedFactor).toLong()
            currentHitTestInterval = lerp(MIN_HITTEST_INTERVAL.toFloat(), MAX_HITTEST_INTERVAL.toFloat(), speedFactor).toLong()

            // Log.d(TAG, "Speed: $speed m/s | AI Interval: $currentInferenceInterval ms | HitTest Interval: $currentHitTestInterval ms")
        }

        lastCameraPose = currentPose
        lastSpeedCheckTime = currentTime
    }

    // *Run Inference @ 5-20 FPS
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

    // !Maybe use bbox ratio to confirm rotation
    // *Hit Test & Update Models @ 15-30 FPS
    private fun processMarkerlessHitTest(arSceneView: ARSceneView, frame: Frame) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastHitTestTime < currentHitTestInterval) return
        lastHitTestTime = currentTime

        val detections = latestDetections

        if (detections.isEmpty()) {
            for (model in markerlessActiveModels) {
                val state = modelLockStates[model]
                model.isVisible = (state != null && state.isLocked)
            }
            return
        }

        val claimedModels = mutableSetOf<Node>()
        val viewW = arSceneView.width.toFloat()
        val viewH = arSceneView.height.toFloat()
        val cameraPose = frame.camera.pose
        val cameraPos = Float3(cameraPose.tx(), cameraPose.ty(), cameraPose.tz())

        for (det in detections) {
            val bbox = det.boundingBox
            val cx = bbox.centerX() * viewW
            val cy = bbox.centerY() * viewH

            val collectedPoints = mutableListOf<Float3>()

            val centerHits = frame.hitTest(cx, cy)
            val centerHit = centerHits.firstOrNull { it.trackable is Plane || it.trackable is Point }
            if (centerHit != null) {
                val p = centerHit.hitPose
                collectedPoints.add(Float3(p.tx(), p.ty(), p.tz()))
            }

            val radiusX = (bbox.width() * viewW * DONUT_RADIUS_FACTOR) / 2f
            val radiusY = (bbox.height() * viewH * DONUT_RADIUS_FACTOR) / 2f
            for (i in 0 until DONUT_POINTS) {
                val angle = (2 * Math.PI * i) / DONUT_POINTS
                val dx = (cos(angle) * radiusX).toFloat()
                val dy = (sin(angle) * radiusY).toFloat()

                val donutHits = frame.hitTest(cx + dx, cy + dy)
                val hit = donutHits.firstOrNull { it.trackable is Plane || it.trackable is Point }
                if (hit != null) {
                    val p = hit.hitPose
                    collectedPoints.add(Float3(p.tx(), p.ty(), p.tz()))
                }
            }

            if (collectedPoints.isEmpty()) continue

            val (bestPos, validPoints) = calculatePositionAndValidPoints(collectedPoints)
            val planeNormal = calculatePlaneNormal(validPoints, bestPos, cameraPos)

            val baseRot = lookRotation(forward = planeNormal, up = Float3(0f, 1f, 0f))
            val correctionRot = Quaternion.fromAxisAngle(Float3(1f, 0f, 0f), -90f) 
            val finalRot = baseRot * correctionRot

            val closestModel = markerlessActiveModels
                .filter { !claimedModels.contains(it) }
                .minByOrNull { distance(it.position, bestPos) }

            val dist = if (closestModel != null) distance(closestModel.position, bestPos) else Float.MAX_VALUE

            if (closestModel != null && dist < snapThreshold) {
                val model = closestModel
                val state = modelLockStates.getOrPut(model) { ModelLockState() }

                if (state.isLocked) {
                    val distFromLock = distance(bestPos, model.position)
                    if (distFromLock > UNLOCK_DISTANCE_THRESHOLD) {
                        state.isLocked = false
                        state.stableFrameCount = 0
                        updateModelTransform(model, bestPos, finalRot)
                    }
                } 
                else {
                    val distFromLastFrame = distance(bestPos, model.position)

                    if (distFromLastFrame < LOCK_DISTANCE_THRESHOLD) {
                        state.stableFrameCount++
                        if (state.stableFrameCount >= FRAMES_TO_LOCK) {
                            state.isLocked = true
                        }
                    } else {
                        state.stableFrameCount = 0
                    }

                    updateModelTransform(model, bestPos, finalRot)
                }

                model.isVisible = true

                claimedModels.add(model)
            } else {
                val newModel = getOrCreateModel(MODEL_PATH)
                modelLockStates[newModel] = ModelLockState() 
                newModel.position = bestPos
                newModel.quaternion = finalRot
                newModel.isVisible = true

                if (newModel.parent == null) arSceneView.addChildNode(newModel)
                if (!markerlessActiveModels.contains(newModel)) markerlessActiveModels.add(newModel)
                claimedModels.add(newModel)
            }
        }

        for (model in markerlessActiveModels) {
            if (!claimedModels.contains(model)) {
                val state = modelLockStates[model]
                model.isVisible = (state != null && state.isLocked)
            }
        }
    }

    private fun updateModelTransform(model: Node, targetPos: Float3, targetRot: Quaternion) {
        val dynamicAlpha = calculateDynamicAlpha(model.position, targetPos)
        model.position = lerp(model.position, targetPos, dynamicAlpha)
        model.quaternion = slerp(model.quaternion, targetRot, dynamicAlpha / 2)
    }

    private fun calculatePlaneNormal(points: List<Float3>, center: Float3, cameraPos: Float3): Float3 {
        if (points.size < 3) return normalize(cameraPos - center)

        var accumNormal = Float3(0f, 0f, 0f)

        // ใช้เทคนิค Triangle Fan รอบจุด Center เพื่อหา Normal เฉลี่ย
        // (สมมติว่า points[0] ใกล้เคียงจุดศูนย์กลาง เพราะเราหา bestPos มาแล้ว)
        // หรือใช้วิธีวนลูปเทียบกับจุด bestPos
        for (i in 0 until points.size) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size] // จุดถัดไป (วนกลับมาจุดแรก)

            // สร้างเวกเตอร์จาก Center ไปยังจุดรอบๆ
            val v1 = p1 - center
            val v2 = p2 - center

            // Cross Product เพื่อหา Normal ย่อยของสามเหลี่ยมนี้
            accumNormal += cross(v1, v2)
        }

        var finalNormal = normalize(accumNormal)

        // Check Direction: Normal ต้องพุ่งหา Camera (User) เสมอ
        // ถ้า Dot Product ติดลบ แปลว่ามันพุ่งหนี ให้กลับด้าน
        val toCamera = normalize(cameraPos - center)
        if (dot(finalNormal, toCamera) < 0) finalNormal = -finalNormal

        if (length(finalNormal).isNaN()) return Float3(0f, 0f, 1f)

        return finalNormal
    }

    private fun calculatePositionAndValidPoints(points: List<Float3>): Pair<Float3, List<Float3>> {
        if (points.isEmpty()) return Pair(Float3(0f,0f,0f), emptyList())
        if (points.size == 1) return Pair(points[0], points)

        val avgX = points.map { it.x }.average()
        val avgY = points.map { it.y }.average()
        val avgZ = points.map { it.z }.average()
        val meanPos = Float3(avgX.toFloat(), avgY.toFloat(), avgZ.toFloat())

        val distances = points.map { distance(it, meanPos) }
        val meanDist = distances.average()
        val variance = distances.map { (it - meanDist).pow(2) }.average()
        val stdDev = sqrt(variance)

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

        val m00 = r.x; val m01 = u2.x; val m02 = f.x
        val m10 = r.y; val m11 = u2.y; val m12 = f.y
        val m20 = r.z; val m21 = u2.z; val m22 = f.z

        val tr = m00 + m11 + m22
        var qw: Float; var qx: Float; var qy: Float; var qz: Float

        if (tr > 0) {
            val s = sqrt(tr + 1.0f) * 2
            qw = 0.25f * s
            qx = (m21 - m12) / s
            qy = (m02 - m20) / s
            qz = (m10 - m01) / s
        } else if ((m00 > m11) && (m00 > m22)) {
            val s = sqrt(1.0f + m00 - m11 - m22) * 2
            qw = (m21 - m12) / s
            qx = 0.25f * s
            qy = (m01 + m10) / s
            qz = (m02 + m20) / s
        } else if (m11 > m22) {
            val s = sqrt(1.0f + m11 - m00 - m22) * 2
            qw = (m02 - m20) / s
            qx = (m01 + m10) / s
            qy = 0.25f * s
            qz = (m12 + m21) / s
        } else {
            val s = sqrt(1.0f + m22 - m00 - m11) * 2
            qw = (m10 - m01) / s
            qx = (m02 + m20) / s
            qy = (m12 + m21) / s
            qz = 0.25f * s
        }

        return Quaternion(qx, qy, qz, qw)
    }

    private fun calculateDynamicAlpha(currentPos: Float3, targetPos: Float3): Float {
        val dist = distance(currentPos, targetPos)
        val t = ((dist - MIN_DIST) / (MAX_DIST - MIN_DIST)).coerceIn(0f, 1f)
        return MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * t
    }

    private fun distance(p1: Float3, p2: Float3): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        val dz = p1.z - p2.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun distance(pose1: Pose, pose2: Pose): Float {
        val dx = pose1.tx() - pose2.tx()
        val dy = pose1.ty() - pose2.ty()
        val dz = pose1.tz() - pose2.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun getOrCreateModel(modelPath: String): Node {
        val freeModel = modelPool.find { it.parent == null || !it.isVisible }

        return if (freeModel != null) {
            freeModel.isVisible = true
            freeModel
        } else {
            val newModel = modelManager.createModelNode(modelPath)
            modelPool.add(newModel)
            newModel
        }
    }

    fun updateNewModel(modelPath: String) {
        MODEL_PATH = modelPath
        modelPool.forEach { rootNode ->
            modelManager.changeModel(rootNode, modelPath)
        }
    }

    fun updateModelSize(sizeInch: Float) {
        val sizeCm = sizeInch * 2.54f
        snapThreshold = sizeCm / 100.0f
        val scaleFactor = sizeCm / 45.72f   // 45.72cm = 18inch
        modelPool.forEach { modelManager.changeModelSize(it, scaleFactor) }
    }

    fun clear() {
        modelPool.forEach { it?.destroy() }
        modelPool.clear()

        augmentedImageMap.values.forEach { it?.destroy() }
        augmentedImageMap.clear()

        markerlessActiveModels.clear()
        modelLockStates.clear()
    }

    fun setupMarkerDatabase(session: Session, markerSize: Float = 0.15f) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "⏳ Starting background marker loading...")
                val augmentedImageDatabase = AugmentedImageDatabase(session)
                val assetManager = context.assets
                val markerFolder = "markers"
                val fileNames = assetManager.list(markerFolder) ?: emptyArray()

                for (filename in fileNames) {
                    if (filename.lowercase().endsWith(".jpg") || filename.lowercase().endsWith(".png")) {
                        try {
                            assetManager.open("$markerFolder/$filename").use { inputStream ->
                                val bitmap = BitmapFactory.decodeStream(inputStream)
                                val markerName = filename.substringBeforeLast(".")
                                augmentedImageDatabase.addImage(markerName, bitmap, markerSize)
                                Log.d(TAG, "Loaded Marker: $markerName ✅")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load marker: $filename", e)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    try {
                        val config = session.config.apply {
                            this.augmentedImageDatabase = augmentedImageDatabase
                            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                            focusMode = Config.FocusMode.AUTO
                        }
                        session.configure(config)
                        Log.d(TAG, "Marker Database Configured Successfully!")
                    } catch (e: Exception) {
                        Log.e(TAG, "Session configuration failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in setupMarkerDatabase", e)
            }
        }
    }
}