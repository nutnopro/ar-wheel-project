package com.arwheelapp.modules

import android.content.Context
import android.util.Log
import android.view.View
import android.os.SystemClock
import com.google.ar.core.*
import dev.romainguy.kotlin.math.*
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.node.ModelNode
import kotlin.math.*
import java.util.*
import com.arwheelapp.processor.FrameConverter
import com.arwheelapp.processor.OnnxRuntimeHandler
import com.arwheelapp.utils.ArTypes.ARMode
import com.arwheelapp.utils.ArTypes.Detection

class ARRendering(private val context: Context, private val onnxOverlayView: OnnxOverlayView, private val arSceneView: ARSceneView) {
    private val modelManager = ModelManager(arSceneView)
    private val frameConverter = FrameConverter()
    private val onnxRuntimeHandler = OnnxRuntimeHandler(context)

    private val TAG = "ARRendering: "
	private val MARKER_DB_NAME = "markers/marker.jpg"
	private val MODEL_PATH = "models/wheel.glb"

    private val INFERENCE_INTERVAL_MS = 1000L / 15L // *~15 FPS
    private val HITTEST_INTERVAL_MS = 1000L / 20L   // *~20 FPS
    private val DONUT_POINTS = 8
    private val DONUT_RADIUS_FACTOR = 0.6f
    private val MIN_DISTANCE_THRESHOLD = 0.5f

    private val SNAP_DISTANCE_THRESHOLD = 0.4f

    private var previousMode: ARMode? = null
    private var lastInferenceTime = 0L
    private var lastHitTestTime = 0L

    private val modelPool = mutableListOf<ModelNode>()
    private val augmentedImageMap = mutableMapOf<AugmentedImage, AugmentedImageNode>()
    private val activeMarkerlessWheels = mutableListOf<MarkerlessWheel>()

    data class MarkerlessWheel(
        var modelNode: ModelNode,
        var lastUpdated: Long = 0L
    )

    @Volatile
    private var latestDetections: List<Detection> = emptyList()

    fun render(arSceneView: ARSceneView, frame: Frame, currentMode: ARMode) {
        if (previousMode != currentMode) {
            handleModeSwitch(currentMode)
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

    private fun handleModeSwitch(newMode: ARMode) {
        Log.d(TAG, "Switching to mode: $newMode")

        modelPool.forEach { model ->
            model.parent = null
            model.isVisible = false
        }
        
        if (previousMode == ARMode.MARKER_BASED) {
            augmentedImageMap.values.forEach { it.destroy() }
            augmentedImageMap.clear()
        }

        if (previousMode == ARMode.MARKERLESS) {
            activeMarkerlessWheels.clear()
        }
    }

    private fun processMarkerBased(arSceneView: ARSceneView, frame: Frame) {
        val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)

        val visibleCount = updatedAugmentedImages.count { image ->
            image.trackingState == TrackingState.TRACKING
        }
        Log.d(TAG, "Frame นี้เจอ Marker จำนวน: $visibleCount ใบ")

        val claimedModels = mutableSetOf<ModelNode>()

        for (image in updatedAugmentedImages) {
            Log.d(TAG, "Marker: ${image.name} | ID: ${image.index} | State: ${image.trackingState}")
            when (image.trackingState) {
                TrackingState.TRACKING -> {
                    val imageNode = augmentedImageMap.getOrPut(image) {
                        AugmentedImageNode(arSceneView.engine, image).apply {
                            arSceneView.addChildNode(this)
                        }
                    }

                    val hasModel = imageNode.childNodes.any { it is ModelNode }

                    if (!hasModel) {
                        val model = getOrCreateModel(arSceneView)

                        model.position = Float3(0f, 0f, 0f)
                        model.rotation = Float3(0f, 0f, 0f)
                        model.isVisible = true 

                        imageNode.addChildNode(model)
                        Log.d(TAG, "Attached model to Marker ID: ${image.index}")
                    }





                    val markerPose = image.centerPose
                    val markerPos = Float3(markerPose.tx(), markerPose.ty(), markerPose.tz())
                    val markerRot = Quaternion(markerPose.qx(), markerPose.qy(), markerPose.qz(), markerPose.qw())

                    // --- ขั้นตอนการจับคู่ (Matching) ---
                    
                    // หาโมเดลที่:
                    // 1. ยังไม่ถูกใครจองในเฟรมนี้ ( !claimedModels.contains )
                    // 2. อยู่ใกล้ Marker นี้ที่สุด
                    val closestWheel = activeVirtualWheels
                        .filter { !claimedModels.contains(it.modelNode) }
                        .minByOrNull { wheel ->
                            distance(wheel.modelNode.position, markerPos)
                        }

                    val dist = if (closestWheel != null) distance(closestWheel.modelNode.position, markerPos) else Float.MAX_VALUE

                    if (closestWheel != null && dist < SNAP_DISTANCE_THRESHOLD) {
                        // CASE A: เจอคู่เก่า (โมเดลเดิมที่อยู่ใกล้ๆ) -> ดึงมาอัปเดต
                        val model = closestWheel.modelNode
                        
                        // ขยับตำแหน่ง (Lerp ให้ดูนุ่มนวล)
                        model.position = mix(model.position, markerPos, 0.4f) // เพิ่มความไวเป็น 0.4 ให้ทันมือ
                        model.quaternion = slerp(model.quaternion, markerRot, 0.4f)
                        
                        model.isVisible = true
                        closestWheel.lastUpdated = SystemClock.uptimeMillis()
                        
                        // จอง! ห้าม Marker อื่นมาแย่งตัวนี้ไปใช้ในรอบนี้
                        claimedModels.add(model)
                        
                    } else {
                        // CASE B: ไม่เจอคู่ หรืออยู่ไกลเกินไป -> สร้างใหม่
                        val newModel = getOrCreateModel(arSceneView)
                        
                        // ต้องแน่ใจว่า add เข้า scene โดยตรง (ไม่ผ่าน ImageNode)
                        if (newModel.parent != arSceneView) {
                            newModel.parent = arSceneView
                        }

                        newModel.position = markerPos
                        newModel.quaternion = markerRot
                        newModel.isVisible = true
                        
                        val newWheel = MarkerlessWheel(newModel, SystemClock.uptimeMillis())
                        activeVirtualWheels.add(newWheel)
                        
                        // จองตัวใหม่นี้ไว้เลย
                        claimedModels.add(newModel)
                    }





                }
                TrackingState.STOPPED -> {
                    val node = augmentedImageMap.remove(image)
                    node?.let { imgNode ->
                        imgNode.childNodes.filterIsInstance<ModelNode>().forEach { model ->
                            model.parent = null
                            model.isVisible = false
                        }
                    }

                    arSceneView.removeChildNode(imgNode)
                    imgNode.destroy()
                }
                TrackingState.STOPPED -> {
                    val node = augmentedImageMap[image]
                    node?.childNodes?.forEach { it.isVisible = false }
                }
            }
        }
        




        activeVirtualWheels.forEach { wheel ->
            if (!claimedModels.contains(wheel.modelNode)) {
                // ซ่อนโมเดลที่ไม่มีเจ้าของ (Marker หาย/หลุดเฟรม)
                wheel.modelNode.isVisible = false
                
                // Optional: ถ้าอยาก destroy ทิ้งเลยเมื่อไม่ใช้นานๆ
                // if (SystemClock.uptimeMillis() - wheel.lastUpdated > 5000) { ... }
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

    // *Hit Test & Update Models @ 20 FPS
    private fun processMarkerlessHitTest(arSceneView: ARSceneView, frame: Frame) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastHitTestTime < HITTEST_INTERVAL_MS) return
        lastHitTestTime = currentTime

        val detections = latestDetections
        if (detections.isEmpty()) return

        for (det in detections) {
            val bbox = det.boundingBox

            val centerX = bbox.centerX() * arSceneView.width
            val centerY = bbox.centerY() * arSceneView.height

            val centerHits = frame.hitTest(centerX, centerY)
            val centerPose = centerHits.firstOrNull { 
                it.trackable is Plane || it.trackable is Point 
            }?.hitPose ?: continue

            val radiusX = (bbox.width() * arSceneView.width * DONUT_RADIUS_FACTOR) / 2
            val radiusY = (bbox.height() * arSceneView.height * DONUT_RADIUS_FACTOR) / 2

            val validPoses = mutableListOf<Pose>()
            validPoses.add(centerPose)

            for (i in 0 until DONUT_POINTS) {
                val angle = (2 * Math.PI * i) / DONUT_POINTS
                val dx = (cos(angle) * radiusX).toFloat()
                val dy = (sin(angle) * radiusY).toFloat()

                val donutHits = frame.hitTest(centerX + dx, centerY + dy)
                val hit = donutHits.firstOrNull { it.trackable is Plane }
                hit?.let { validPoses.add(it.hitPose) }
            }
            
            if (validPoses.size < 3) continue

            val (finalPosition, finalRotation) = calculateAveragedPose(validPoses)

            updateOrCreateModel(arSceneView, finalPosition, finalRotation)
        }
    }

    private fun updateOrCreateModel(arSceneView: ARSceneView, position: Float3, rotation: Quaternion) {
        val existingWheel = activeMarkerlessWheels.find { wheel ->
            val dist = distance(wheel.modelNode.position, position)
            dist < MIN_DISTANCE_THRESHOLD
        }

        if (existingWheel != null) {
            val model = existingWheel.modelNode

            model.position = mix(model.position, position, 0.2f)
            model.quaternion = slerp(model.quaternion, rotation, 0.2f)
            // model.rotation = rotation

            model.isVisible = true
            existingWheel.lastUpdated = SystemClock.uptimeMillis()

        } else {
            val model = getOrCreateModel(arSceneView)

            model.position = position
            model.quaternion = rotation
            model.isVisible = true

            arSceneView.addChildNode(model) 
            activeMarkerlessWheels.add(MarkerlessWheel(modelNode = model))
        }
    }

    private fun calculateAveragedPose(poses: List<Pose>): Pair<Float3, Quaternion> {
        var sumX = 0f;
        var sumY = 0f;
        var sumZ = 0f
        poses.forEach { 
            sumX += it.tx()
            sumY += it.ty()
            sumZ += it.tz()
        }
        val preAvgX = sumX / poses.size
        val preAvgY = sumY / poses.size
        val preAvgZ = sumZ / poses.size

        val distances = poses.map {
            val dx = it.tx() - preAvgX
            val dy = it.ty() - preAvgY
            val dz = it.tz() - preAvgZ
            sqrt(dx * dx + dy * dy + dz * dz)
        }

        val meanDist = distances.average()
        val variance = distances.map { (it - meanDist).pow(2) }.average()
        val stdDev = sqrt(variance)
        val threshold = meanDist + (1.5 * stdDev)

        val validPoses = mutableListOf<Pose>()
        for (i in poses.indices) {
            if (distances[i] <= threshold) {
                validPoses.add(poses[i])
            }
        }

        val finalPoses = if (validPoses.isEmpty()) poses else validPoses

        var finalSumX = 0f;
        var finalSumY = 0f;
        var finalSumZ = 0f
        finalPoses.forEach {
            finalSumX += it.tx()
            finalSumY += it.ty()
            finalSumZ += it.tz()
        }
        
        val avgPos = Float3(
            finalSumX / finalPoses.size,
            finalSumY / finalPoses.size,
            finalSumZ / finalPoses.size
        )

        val mainPose = poses[0]
        val q = mainPose.rotationQuaternion
        val targetRot = Quaternion(q[0], q[1], q[2], q[3])

        return Pair(avgPos, targetRot)
    }

    private fun getOrCreateModel(arSceneView: ARSceneView): ModelNode {
        val freeModel = modelPool.find { it.parent == null }

        return if (freeModel != null) {
            freeModel
        } else {
            var newModel = modelManager.createModelNode(MODEL_PATH)

            modelPool.add(newModel)
            newModel
        }
    }

    fun setupMarkerDatabase(session: Session) {
        try {
            val augmentedImageDatabase = AugmentedImageDatabase(session)

            val inputStream = context.assets.open(MARKER_DB_NAME)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)

            augmentedImageDatabase.addImage("marker", bitmap, 0.15f)

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

        activeMarkerlessWheels.clear()
    }
}