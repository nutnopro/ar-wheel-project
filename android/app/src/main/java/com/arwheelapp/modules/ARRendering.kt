package com.arwheelapp.modules

import android.content.Context
import android.widget.Toast
import android.util.Log
import android.view.View
import android.os.SystemClock
import android.graphics.BitmapFactory
import android.graphics.PointF
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

class ARRendering(private val context: Context, private val onnxOverlayView: OnnxOverlayView, private val arSceneView: ARSceneView) {
    private val modelManager = ModelManager(arSceneView)
    private val frameConverter = FrameConverter()
    private val onnxRuntimeHandler = OnnxRuntimeHandler(context)

    private val TAG = "ARRendering: "

    // --- Dynamic FPS Settings ---
    // AI Inference: 5 FPS -> 20 FPS
    private val MIN_INFERENCE_INTERVAL = 200L   // 5 FPS
    private val MAX_INFERENCE_INTERVAL = 50L    // 20 FPS

    // HitTest: 10 FPS -> 60 FPS
    private val MIN_HITTEST_INTERVAL = 66L      // 15 FPS
    private val MAX_HITTEST_INTERVAL = 33L      // 30 FPS

    private var currentInferenceInterval = MIN_INFERENCE_INTERVAL
    private var currentHitTestInterval = MIN_HITTEST_INTERVAL

    // Variables for Speed Calculation
    private var lastCameraPose: Pose? = null
    private var lastSpeedCheckTime = 0L
    private val SPEED_CHECK_INTERVAL = 100L // Check speed every 0.1s
    private val MOVEMENT_THRESHOLD_HIGH = 0.3f // 0.3 meters/second considered "Fast"

    // Timestamps
    private var previousMode: ARMode? = null
    private var lastInferenceTime = 0L
    private var lastHitTestTime = 0L

    private val modelPool = mutableListOf<Node>()
    private val augmentedImageMap = mutableMapOf<AugmentedImage, AugmentedImageNode>()
    private val markerlessActiveModels = mutableListOf<Node>()

    @Volatile
    private var latestDetections: List<Detection> = emptyList()

    // Dynamic lerp
    private val MIN_ALPHA = 0.05f
    private val MAX_ALPHA = 0.6f
    private val MIN_DIST = 0.02f
    private val MAX_DIST = 0.3f

    private val DONUT_POINTS = 8
    private val DONUT_RADIUS_FACTOR = 0.64f

    @Volatile
    private var snapThreshold = 0.457f

    @Volatile
	private var MODEL_PATH = "models/wheel1.glb"    // !!Change to ui

    fun render(arSceneView: ARSceneView, frame: Frame, currentMode: ARMode) {
        if (previousMode != currentMode) {
            handleModeSwitch()
            previousMode = currentMode
        }

        // Calculate Camera Speed to adjust FPS dynamically
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
                    if (image.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING) {
                        node?.isVisible = true
                    } else if (image.trackingMethod == AugmentedImage.TrackingMethod.LAST_KNOWN_POSE) {
                        node?.isVisible = false
                    }
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

    // *Run Inference @ Dynamic FPS
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
    // *Hit Test & Update Models @ 20 FPS
    private fun processMarkerlessHitTest(arSceneView: ARSceneView, frame: Frame) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastHitTestTime < currentHitTestInterval) return
        lastHitTestTime = currentTime

        val detections = latestDetections
        if (detections.isEmpty()) {
            markerlessActiveModels.forEach { it.isVisible = false }
            return
        }

        val claimedModels = mutableSetOf<Node>()
        val viewW = arSceneView.width.toFloat()
        val viewH = arSceneView.height.toFloat()

        for (det in detections) {
            val bbox = det.boundingBox

            val cx = bbox.centerX() * viewW
            val cy = bbox.centerY() * viewH

            val validPoses = mutableListOf<Pose>()
            val validNormals = mutableListOf<Float3>()

            fun extractNormal(pose: Pose): Float3 {
                val zAxis = FloatArray(3)
                pose.getTransformedAxis(2, 0f, zAxis, 0) 
                return Float3(zAxis[0], zAxis[1], zAxis[2])
            }

            val centerHits = frame.hitTest(cx, cy)
            var centerPose: Pose? = null

            val validCenterHit = centerHits.firstOrNull { hit ->
                (hit.trackable is Plane || hit.trackable is Point) && !isUpwardSurface(hit.hitPose)
            }

            if (validCenterHit != null) {
                centerPose = validCenterHit.hitPose
                validPoses.add(centerPose)
                validNormals.add(extractNormal(centerPose))
            }

            // Donut sampling
            val radiusX = (bbox.width() * viewW * DONUT_RADIUS_FACTOR) / 2f
            val radiusY = (bbox.height() * viewH * DONUT_RADIUS_FACTOR) / 2f

            for (i in 0 until DONUT_POINTS) {
                val angle = (2 * Math.PI * i) / DONUT_POINTS
                val dx = (cos(angle) * radiusX).toFloat()
                val dy = (sin(angle) * radiusY).toFloat()

                val donutHits = frame.hitTest(cx + dx, cy + dy)
                val hit = donutHits.firstOrNull { 
                    (it.trackable is Plane || it.trackable is Point) && !isUpwardSurface(it.hitPose)
                }

                if (hit != null) {
                    validPoses.add(hit.hitPose)
                    validNormals.add(extractNormal(hit.hitPose))
                }
            }

            if (validPoses.isEmpty() || validPoses.size < 3) continue

            // Statistical Outlier Removal
            val bestPos = calculateAveragePositionWithOutlierRemoval(validPoses) ?: continue

            // Calculate Rotation
            // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

            var avgNormal = Float3(0f, 0f, 0f)
            validNormals.forEach { normal -> avgNormal += normal }
            
            avgNormal = normalize(avgNormal)

            if (length(avgNormal) < 0.001f) avgNormal = Float3(0f, 0f, 1f)

            // บังคับให้ขนานพื้นโลก (ล้อรถไม่ควรเงยหน้า/ก้มหน้า)
            // ถ้าอยากให้ล้อเอียงตามแก้มยางได้ ให้ Comment บรรทัดนี้ทิ้ง
            // avgNormal.y = 0f 
            avgNormal = normalize(avgNormal)

            // สร้าง Rotation จากเวกเตอร์ Normal
            // LookRotation ปกติจะใช้ (Forward, Up)
            // แต่สำหรับโมเดลล้อที่แบนๆ เราต้องการให้หน้าล้อ (Z) หันไปตาม Normal
            val targetRot = lookRotation(forward = avgNormal, up = Float3(0f, 1f, 0f))

            // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

            // val refPose = centerPose ?: validPoses.first()
            // val q = refPose.rotationQuaternion
            // val targetRot = Quaternion(x = q[0], y = q[1], z = q[2], w = q[3])

            // Associate Models
            val closestModel = markerlessActiveModels
                .filter { !claimedModels.contains(it) }
                .minByOrNull { distance(it.position, bestPos) }

            val dist = if (closestModel != null) distance(closestModel.position, bestPos) else Float.MAX_VALUE

            if (closestModel != null && dist < snapThreshold) {
                val model = closestModel
                val dynamicAlpha = calculateDynamicAlpha(model.position, bestPos)

                model.position = lerp(model.position, bestPos, dynamicAlpha)
                model.quaternion = slerp(model.quaternion, targetRot, dynamicAlpha / 2)
                model.isVisible = true

                claimedModels.add(model)
            } else {
                val newModel = getOrCreateModel(MODEL_PATH)
                newModel.position = bestPos
                newModel.quaternion = targetRot
                newModel.isVisible = true

                if (newModel.parent == null) {
                    arSceneView.addChildNode(newModel)
                }
                if (!markerlessActiveModels.contains(newModel)) {
                    markerlessActiveModels.add(newModel)
                }
                claimedModels.add(newModel)
            }
        }

        for (model in markerlessActiveModels) {
            if (!claimedModels.contains(model)) {
                model.isVisible = false
            }
        }
    }

    private fun lookRotation(forward: Float3, up: Float3): Quaternion {
        val z = normalize(forward)
        val x = normalize(cross(up, z))
        val y = cross(z, x)

        // Convert Rotation Matrix to Quaternion
        val m00 = x.x; val m01 = y.x; val m02 = z.x
        val m10 = x.y; val m11 = y.y; val m12 = z.y
        val m20 = x.z; val m21 = y.z; val m22 = z.z

        val tr = m00 + m11 + m22
        val qw: Float
        val qx: Float
        val qy: Float
        val qz: Float

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

    private fun calculateAveragePositionWithOutlierRemoval(poses: List<Pose>): Float3? {
        if (poses.isEmpty()) return null
        if (poses.size == 1) return Float3(poses[0].tx(), poses[0].ty(), poses[0].tz())

        // Initial Mean
        val avgX = poses.map { it.tx() }.average()
        val avgY = poses.map { it.ty() }.average()
        val avgZ = poses.map { it.tz() }.average()

        // Distance from Mean
        val distances = poses.map {
            val dx = it.tx() - avgX
            val dy = it.ty() - avgY
            val dz = it.tz() - avgZ
            sqrt(dx * dx + dy * dy + dz * dz)
        }

        // Mean Distance and Standard Deviation
        val meanDist = distances.average()
        val variance = distances.map { (it - meanDist).pow(2) }.average()
        val stdDev = sqrt(variance)

        val threshold = meanDist + (1.5 * stdDev)

        // Filter Poses within Threshold
        val finalPoses = poses.filterIndexed { index, _ ->
            distances[index] <= threshold 
        }

        if (finalPoses.isEmpty()) return Float3(avgX.toFloat(), avgY.toFloat(), avgZ.toFloat())

        // Final Average
        return Float3(
            finalPoses.map { it.tx() }.average().toFloat(),
            finalPoses.map { it.ty() }.average().toFloat(),
            finalPoses.map { it.tz() }.average().toFloat()
        )
    }

    private fun isUpwardSurface(pose: Pose): Boolean {
        val axisY = FloatArray(3)
        pose.getTransformedAxis(1, 0f, axisY, 0)

        // 0.7f-45degrees, 0.866f-30degrees, 0.5f-60degrees
        return axisY[1] > 0.7f
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
            var newModel = modelManager.createModelNode(modelPath)

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

        modelPool.forEach { rootNode ->
            modelManager.changeModelSize(rootNode, scaleFactor)
        }
    }

    fun clear() {
        modelPool.forEach { it?.destroy() }
        modelPool.clear()

        augmentedImageMap.values.forEach { it?.destroy() }
        augmentedImageMap.clear()

        markerlessActiveModels.clear()
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