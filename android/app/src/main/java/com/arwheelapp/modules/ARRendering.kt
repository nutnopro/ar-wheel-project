package com.arwheelapp.modules

import android.content.Context
import android.util.Log
import android.view.View
import com.arwheelapp.utils.FrameConverter
import com.arwheelapp.utils.OnnxRuntimeHandler
import com.google.ar.core.*
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.ar.node.HitResultNode
import io.github.sceneview.ar.node.PoseNode
import io.github.sceneview.collision.Vector3
import io.github.sceneview.node.ModelNode
import kotlin.math.*

class ARRendering(private val context: Context, private val onnxOverlayView: OnnxOverlayView) {
    private val modelManager = ModelManager()
    private val frameConverter = FrameConverter()
    private val onnxRuntimeHandler = OnnxRuntimeHandler(context)

    private val TAG = "ARRendering: "

	private val MARKER_DB_NAME = "markers/marker.jpg"
	private val MODEL_PATH = "models/wheel.glb"

    private var previousMode: ARActivity.ARMode? = null

    private val markerNodes = mutableMapOf<AugmentedImage, AugmentedImageNode>()
    private val markerlessNodePool = mutableListOf<HitResultNode>()
    private val wheelModelPool = mutableListOf<ModelNode>()

    private var lastInferenceTime = 0L
    private var frameIntervalNs = 33_000_000L

    fun render(session: Session, arSceneView: ARSceneView, frame: Frame, currentMode: ARActivity.ARMode) {
        // Log.d(TAG, "render: Frame update, Mode=$currentMode")
        if (previousMode != currentMode) {
            // Log.d(TAG, "render: Mode changed from $previousMode to $currentMode. Hiding all models.")
            hideAllModels()
            previousMode = currentMode
        }

        when (currentMode) {
            ARActivity.ARMode.MARKER_BASED -> processMarkerBased(arSceneView, frame)
            ARActivity.ARMode.MARKERLESS -> processMarkerless(arSceneView, frame)
        }
    }

    private fun processMarkerBased(arSceneView: ARSceneView, frame: Frame) {
        // Log.d(TAG, "processMarkerBased: Processing marker-based AR frame")
        val updatedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)

        // 1. วนลูปจัดการ Marker แต่ละใบ
        for (img in updatedImages) {
            // val name = img.name
            val state = img.trackingState
            // Log.d(TAG, "Marker: $name, State: $state")

            if (state == TrackingState.STOPPED) {
                // Log.d(TAG, "processMarkerBased: Marker $name STOPPED. Removing node.")
                markerNodes[img]?.let { 
                    arSceneView.removeChildNode(it)
                    markerNodes.remove(img)
                }
                continue
            }

            if (!markerNodes.containsKey(img)) {
                // Log.d(TAG, "processMarkerBased: New marker detected ($name). Creating AugmentedImageNode.")
                val node = AugmentedImageNode(arSceneView.engine, img)
                arSceneView.addChildNode(node)
                markerNodes[img] = node
            }
        }

        // !ยังติดปัญหาเรื่องการซ่อน model ที่ตรวจไม่เจอ marker
        val activeMarkerNodes = markerNodes.values.filter { node ->
            val img = node.augmentedImage ?: return@filter false
            val isTracking = img.trackingState == TrackingState.TRACKING
            isTracking
        }

        updateModelPool(arSceneView, activeMarkerNodes)
    }

    private fun processMarkerless(arSceneView: ARSceneView, frame: Frame) {
        // Log.d(TAG, "processMarkerless: Processing markerless AR frame")
        val currentTime = frame.timestamp
        if (currentTime - lastInferenceTime < frameIntervalNs) return
        lastInferenceTime = currentTime

        // Log.d(TAG, "processMarkerless: Starting inference cycle")
        val start = System.nanoTime()
        val tensor = frameConverter.convertFrameToTensor(frame)

        onnxRuntimeHandler.runOnnxInferenceAsync(tensor) { detections ->
            // Log.d(TAG, "processMarkerless: Inference callback received with ${detections.size} detections")
            val inferenceDuration = (System.nanoTime() - start) / 1_000_000
            val fullFrameStart = System.nanoTime()

            if (onnxOverlayView.visibility == View.VISIBLE) {
                onnxOverlayView.updateDetections(detections)
            }

            // *prepare Nodes for YOLO (Reset Pool)
            markerlessNodePool.forEach { it.isVisible = false }
            val activeHitNodes = mutableListOf<PoseNode>()

            detections.forEachIndexed { index, bbox ->
                val centerX = bbox.x * arSceneView.width
                val centerY = bbox.y * arSceneView.height
                // Log.d(TAG, "Detection #$index: x=$centerX, y=$centerY, conf=${bbox.confidence}")

                // *extract Node from Pool (create new if exhausted)
                if (index >= markerlessNodePool.size) {
                    // Log.d(TAG, "processMarkerless: Pool exhausted. Creating new HitResultNode.")
                    val newNode = HitResultNode(arSceneView.engine, xPx = centerX, yPx = centerY)
                    arSceneView.addChildNode(newNode)
                    markerlessNodePool.add(newNode)
                }
                
                val node = markerlessNodePool[index]
                
                // *calculate HitTest to place on real surface
                val hitResult = frame.hitTest(centerX, centerY).firstOrNull()
                if (hitResult != null) {
                    // Log.d(TAG, "processMarkerless: HitTest success for detection #$index")
                    node.hitResult = hitResult
                    node.isVisible = true

                    // *calculate Rotation (optional)
                    val upPosHits = frame.hitTest(centerX, centerY * 0.9f).firstOrNull()
                    val leftPosHits = frame.hitTest(centerX * 0.9f, centerY).firstOrNull()

                    if (upPosHits != null && leftPosHits != null) {
                        val centerPos = node.worldPosition.toVector3()
                        val upPos = upPosHits.hitPose.toVector3()
                        val leftPos = leftPosHits.hitPose.toVector3()
                        node.rotation = getRotationFromThreeVector(centerPos, upPos, leftPos)
                        // Log.d(TAG, "processMarkerless: Rotation calculated for #$index")
                    }

                    activeHitNodes.add(node)
                }
            }

            // *use Object Pool to manage 3D Models
            updateModelPool(arSceneView, activeHitNodes)

            // *calculate frame interval dynamically
            val fullFrameDuration = System.nanoTime() - fullFrameStart
            frameIntervalNs = when {
                fullFrameDuration < 25_000_000L -> 22_000_000L
                fullFrameDuration > 50_000_000L -> 50_000_000L
                else -> 33_000_000L
            }
            // Log.d(TAG, "processMarkerless: Cycle complete. Next interval: ${frameIntervalNs/1_000_000}ms")
        }
    }

    private fun updateModelPool(arSceneView: ARSceneView, targets: List<PoseNode>) {
        targets.forEachIndexed { index, targetNode ->
            // *create new model if pool exhausted
            if (index >= wheelModelPool.size) {
                // Log.d(TAG, "updateModelPool: Model pool exhausted. Creating new ModelNode.")
                val newModel = modelManager.createModelNode(arSceneView, MODEL_PATH)
                wheelModelPool.add(newModel)
            }

            // *extract model from pool
            val modelNode = wheelModelPool[index]

            if (modelNode.parent != targetNode) {
                // Log.d(TAG, "updateModelPool: Re-parenting model #$index to new target.")
                modelNode.parent = targetNode
                modelNode.position = Float3(0f, 0f, 0f)
                modelNode.rotation = Float3(0f, 0f, 0f)
                modelNode.scale = Float3(1f, 1f, 1f)
            }

            modelNode.isVisible = true
        }

        // *hide unused models
        if (targets.size < wheelModelPool.size) {
            // Log.d(TAG, "updateModelPool: Hiding ${wheelModelPool.size - targets.size} unused models.")
            for (i in targets.size until wheelModelPool.size) {
                val unusedModel = wheelModelPool[i]
                unusedModel.isVisible = false
                unusedModel.parent = null 
            }
        }
    }

    private fun hideAllModels() {
        // Log.d(TAG, "hideAllModels: Clearing all models from scene.")
        wheelModelPool.forEach { 
            it.isVisible = false 
            it.parent = null
        }
    }

    fun setupMarkerDatabase(session: Session) {
        // Log.d(TAG, "setupMarkerDatabase: Starting setup...")
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
            // Log.d(TAG, "setupMarkerDatabase: Loaded JPG directly.")
        } catch (e: Exception) {
            Log.e(TAG, "setupMarkerDatabase: Error", e)
        }
    }

    // *Rotation Calc
    private fun getRotationFromThreeVector(center: Vector3, up: Vector3, left: Vector3): Float3 {
        // Log.d(TAG, "getRotationFromThreeVector: Calculating rotation...")
        val upVec = Vector3.subtract(up, center).normalized()
        val leftVec = Vector3.subtract(left, center).normalized()
        val forwardVec = Vector3.cross(upVec, leftVec).normalized()

        val m12 = upVec.x
        val m22 = upVec.y
        val m31 = leftVec.z
        val m32 = upVec.z
        val m33 = forwardVec.z

        val pitch = atan2(-m32, sqrt(m31 * m31 + m33 * m33)).toFloat()
        val yaw = atan2(m31, m33).toFloat()
        val roll = atan2(m12, m22).toFloat()

        return Float3(
            roll * 180f / PI.toFloat(),
            pitch * 180f / PI.toFloat(),
            yaw * 180f / PI.toFloat()
        )
    }

    // *Extension Functions
    private fun Float3.toVector3() = Vector3(x, y, z)
    private fun com.google.ar.core.Pose.toVector3() = Vector3(tx(), ty(), tz())

    private fun Vector3.normalized(): Vector3 {
        val len = sqrt(x * x + y * y + z * z)
        return if (len != 0.0f) Vector3(x / len, y / len, z / len) else Vector3(0.0f, 0.0f, 0.0f)
    }

    fun clear() {
        // Log.d(TAG, "clear: Destroying all nodes and clearing pools.")
        wheelModelPool.forEach { it.destroy() }
        wheelModelPool.clear()

        markerNodes.values.forEach { it.destroy() }
        markerNodes.clear()

        markerlessNodePool.forEach { it.destroy() }
        markerlessNodePool.clear()
    }
}