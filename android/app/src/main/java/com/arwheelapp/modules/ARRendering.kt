package com.arwheelapp.modules

import android.content.Context
import android.util.Log
import android.view.View
import android.os.SystemClock
import android.graphics.PointF
import android.graphics.BitmapFactory
import com.google.ar.core.*
import dev.romainguy.kotlin.math.*
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.node.ModelNode
import kotlin.math.*
import java.util.*
import com.arwheelapp.processor.FrameConverter
import com.arwheelapp.processor.OnnxRuntimeHandler
import com.arwheelapp.utils.ARMode
import com.arwheelapp.utils.Detection

class ARRendering(private val context: Context, private val onnxOverlayView: OnnxOverlayView, private val arSceneView: ARSceneView) {
    private val modelManager = ModelManager(arSceneView)
    private val frameConverter = FrameConverter()
    private val onnxRuntimeHandler = OnnxRuntimeHandler(context)

    private val TAG = "ARRendering: "

    // !!Change to ui later.
	private val MODEL_PATH = "models/wheel.glb"
    private val scaleFactor = 1f
    private val diameterFactor = 1f

    private val INFERENCE_INTERVAL_MS = 1000L / 15L // *~15 FPS
    private val HITTEST_INTERVAL_MS = 1000L / 20L   // *~20 FPS
    private val DONUT_POINTS = 8
    private val DONUT_RADIUS_FACTOR = 0.6f
    private val SNAP_THRESHOLD = 0.5f

    private var previousMode: ARMode? = null
    private var lastInferenceTime = 0L
    private var lastHitTestTime = 0L

    private val modelPool = mutableListOf<ModelNode>()
    private val augmentedImageMap = mutableMapOf<AugmentedImage, AugmentedImageNode>()
    private val markerlessActiveModels = mutableListOf<ModelNode>()

    // !Maybe timer to detach unused models
    // data class MarkerlessWheel(
    //     var modelNode: ModelNode,
    //     var lastUpdated: Long = 0L
    // )

    @Volatile
    private var latestDetections: List<Detection> = emptyList()

    fun render(arSceneView: ARSceneView, frame: Frame, currentMode: ARMode) {
        if (previousMode != currentMode) {
            handleModeSwitch()
            previousMode = currentMode
        }

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

        val visibleCount = updatedAugmentedImages.count { image ->
            image.trackingState == TrackingState.TRACKING
        }
        Log.d(TAG, "Frame นี้เจอ Marker จำนวน: $visibleCount ใบ")

        for (image in updatedAugmentedImages) {
            Log.d(TAG, "Marker: ${image.name} | ID: ${image.index} | State: ${image.trackingState}")
            when (image.trackingState) {
                TrackingState.TRACKING -> {
                    if (augmentedImageMap.containsKey(image)) {
                        augmentedImageMap[image]?.isVisible = true
                    } else {
                        Log.d(TAG, "Found new marker: ${image.name}")

                        val imageNode = AugmentedImageNode(arSceneView.engine, image)

                        val model = getOrCreateModel(MODEL_PATH)
                        model.scale = Float3(scaleFactor, scaleFactor, scaleFactor) 
                        model.isVisible = true 

                        imageNode.addChildNode(model)
                        arSceneView.addChildNode(imageNode)

                        augmentedImageMap[image] = imageNode
                        Log.d(TAG, "Attached model to Marker ID: ${image.index}")
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
                    val node = augmentedImageMap[image]
                    node?.isVisible = false
                }
            }
        }
    }

    // *Run Inference @ 15 FPS
    private fun processMarkerlessInference(frame: Frame) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastInferenceTime < INFERENCE_INTERVAL_MS) return

        lastInferenceTime = currentTime

        try {
            val tensor = frameConverter.convertFrameToTensor(frame)

            onnxRuntimeHandler.runOnnxInferenceAsync(tensor) { detections ->
                latestDetections = detections
                onnxOverlayView.updateDetections(detections)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting frame to tensor", e)
        }
    }

    // !Maybe use opencv to find circle or ellipse and cernter point
    // *Hit Test & Update Models @ 20 FPS
    private fun processMarkerlessHitTest(arSceneView: ARSceneView, frame: Frame) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastHitTestTime < HITTEST_INTERVAL_MS) return
        lastHitTestTime = currentTime

        val detections = latestDetections
        if (detections.isEmpty()) {
            markerlessActiveModels.forEach { it.isVisible = false }
            return
        }

        val claimedModels = mutableSetOf<ModelNode>()

        val viewW = arSceneView.width
        val viewH = arSceneView.height

        for (det in detections) {
            val bbox = det.boundingBox

            val cx = bbox.centerX() * viewW
            val cy = bbox.centerY() * viewH
            val w = bbox.width() * viewW
            val h = bbox.height() * viewH

            val testPoints = mutableListOf<PointF>()
            testPoints.add(PointF(cx, cy))

            val r25W = w * 0.25f / 2f
            val r25H = h * 0.25f / 2f
            testPoints.add(PointF(cx, cy - r25H)) // N
            testPoints.add(PointF(cx, cy + r25H)) // S
            testPoints.add(PointF(cx - r25W, cy)) // W
            testPoints.add(PointF(cx + r25W, cy)) // E

            val r60W = w * 0.60f / 2f
            val r60H = h * 0.60f / 2f
            testPoints.add(PointF(cx + r60W, cy - r60H)) // NE
            testPoints.add(PointF(cx - r60W, cy - r60H)) // NW
            testPoints.add(PointF(cx + r60W, cy + r60H)) // SE
            testPoints.add(PointF(cx - r60W, cy + r60H)) // SW

            val validHits = mutableListOf<Float3>()
            for (pt in testPoints) {
                val hitList = frame.hitTest(pt.x, pt.y)

                val hit = hitList.firstOrNull { 
                    it.trackable is Plane && (it.trackable as Plane).isPoseInPolygon(it.hitPose) 
                }

                if (hit != null) {
                    val pose = hit.hitPose
                    validHits.add(Float3(pose.tx(), pose.ty(), pose.tz()))
                }
            }

            val bestPos = calculateBestPosition(validHits)

            if (bestPos != null) {
                val cameraPos = arSceneView.cameraNode.worldPosition
                val finalRot = calculateVerticalRotation(bestPos, cameraPos)

                val closestModel = markerlessActiveModels
                    .filter { !claimedModels.contains(it) } 
                    .minByOrNull { distance(it.position, bestPos) }
                
                val dist = if (closestModel != null) distance(closestModel.position, bestPos) else Float.MAX_VALUE

                if (closestModel != null && dist < SNAP_THRESHOLD) {
                    val model = closestModel

                    model.position = mix(model.position, bestPos, 0.4f) 
                    model.quaternion = slerp(model.quaternion, finalRot, 0.2f) 
                    model.isVisible = true

                    claimedModels.add(model) 
                } else {
                    val newModel = getOrCreateModel(MODEL_PATH)

                    newModel.position = bestPos
                    newModel.quaternion = finalRot
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
        }

        for (model in markerlessActiveModels) {
            if (!claimedModels.contains(model)) {
                model.isVisible = false
            }
        }
    }

    private fun calculateBestPosition(points: List<Float3>): Float3? {
        if (points.isEmpty()) return null
        if (points.size == 1) return points[0]

        val avgX = points.map { it.x }.average().toFloat()
        val avgY = points.map { it.y }.average().toFloat()
        val avgZ = points.map { it.z }.average().toFloat()
        val centroid = Float3(avgX, avgY, avgZ)

        val validPoints = points.filter { distance(it, centroid) < 0.2f }

        if (validPoints.isEmpty()) return centroid 

        return Float3(
            validPoints.map { it.x }.average().toFloat(),
            validPoints.map { it.y }.average().toFloat(),
            validPoints.map { it.z }.average().toFloat()
        )
    }

    private fun calculateVerticalRotation(objPos: Float3, cameraPos: Float3): Quaternion {
        val dx = cameraPos.x - objPos.x
        val dz = cameraPos.z - objPos.z

        val angleY = atan2(dx, dz)

        return Quaternion.fromAxisAngle(Float3(0f, 1f, 0f), angleY)
    }

    // private fun updateOrCreateModel(arSceneView: ARSceneView, position: Float3, rotation: Quaternion) {
    //     val existingWheel = markerlessActiveModels.find { wheel ->
    //         val dist = distance(wheel.position, position)
    //         dist < SNAP_THRESHOLD
    //     }

    //     if (existingWheel != null) {
    //         val model = existingWheel

    //         model.position = mix(model.position, position, 0.2f)
    //         model.quaternion = slerp(model.quaternion, rotation, 0.2f)
    //         model.isVisible = true

    //     } else {
    //         val model = getOrCreateModel(MODEL_PATH)

    //         model.position = position
    //         model.quaternion = rotation
    //         model.isVisible = true

    //         arSceneView.addChildNode(model) 
    //         markerlessActiveModels.add(model)
    //     }
    // }

    // private fun calculateAveragedPose(poses: List<Pose>): Pair<Float3, Quaternion> {
    //     var sumX = 0f;
    //     var sumY = 0f;
    //     var sumZ = 0f
    //     poses.forEach { 
    //         sumX += it.tx()
    //         sumY += it.ty()
    //         sumZ += it.tz()
    //     }
    //     val preAvgX = sumX / poses.size
    //     val preAvgY = sumY / poses.size
    //     val preAvgZ = sumZ / poses.size

    //     val distances = poses.map {
    //         val dx = it.tx() - preAvgX
    //         val dy = it.ty() - preAvgY
    //         val dz = it.tz() - preAvgZ
    //         sqrt(dx * dx + dy * dy + dz * dz)
    //     }

    //     val meanDist = distances.average()
    //     val variance = distances.map { (it - meanDist).pow(2) }.average()
    //     val stdDev = sqrt(variance)
    //     val threshold = meanDist + (1.5 * stdDev)

    //     val validPoses = mutableListOf<Pose>()
    //     for (i in poses.indices) {
    //         if (distances[i] <= threshold) {
    //             validPoses.add(poses[i])
    //         }
    //     }

    //     val finalPoses = if (validPoses.isEmpty()) poses else validPoses

    //     var finalSumX = 0f;
    //     var finalSumY = 0f;
    //     var finalSumZ = 0f
    //     finalPoses.forEach {
    //         finalSumX += it.tx()
    //         finalSumY += it.ty()
    //         finalSumZ += it.tz()
    //     }

    //     val avgPos = Float3(
    //         finalSumX / finalPoses.size,
    //         finalSumY / finalPoses.size,
    //         finalSumZ / finalPoses.size
    //     )

    //     val mainPose = poses[0]
    //     val q = mainPose.rotationQuaternion
    //     val targetRot = Quaternion(q[0], q[1], q[2], q[3])

    //     return Pair(avgPos, targetRot)
    // }

    private fun getOrCreateModel(path: String): ModelNode {
        val freeModel = modelPool.find { it.parent == null }

        return if (freeModel != null) {
            freeModel
        } else {
            var newModel = modelManager.createModelNode(path)

            modelPool.add(newModel)
            newModel
        }
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

    fun clear() {
        modelPool.forEach { it?.destroy() }
        modelPool.clear()

        augmentedImageMap.values.forEach { it?.destroy() }
        augmentedImageMap.clear()

        markerlessActiveModels.clear()
    }

    fun setupMarkerDatabase(session: Session) {
        try {
            val augmentedImageDatabase = AugmentedImageDatabase(session)
            val assetManager = context.assets
            val markerFolder = "markers"
            val fileNames = assetManager.list(markerFolder) ?: emptyArray()
            
            for (filename in fileNames) {
            if (filename.lowercase().endsWith(".jpg") || filename.lowercase().endsWith(".png")) {
                assetManager.open("$markerFolder/$filename").use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val markerName = filename.substringBeforeLast(".")
                    
                    augmentedImageDatabase.addImage(markerName, bitmap, 0.15f)
                    Log.d(TAG, "Loaded Marker: $markerName ✅")
                }
            }
        }
        
        val config = session.config.apply {
            this.augmentedImageDatabase = augmentedImageDatabase
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            focusMode = Config.FocusMode.AUTO
        }
        session.configure(config)
        } catch (e: Exception) {
            Log.e(TAG, "setupMarkerDatabase: Error", e)
        }
    }
}