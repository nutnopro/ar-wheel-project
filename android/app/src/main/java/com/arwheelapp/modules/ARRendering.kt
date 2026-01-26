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
import io.github.sceneview.node.Node
import io.github.sceneview.math.*
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

    private val INFERENCE_INTERVAL_MS = 1000L / 15L // ~15 FPS
    private val HITTEST_INTERVAL_MS = 1000L / 20L   // ~20 FPS

    private var previousMode: ARMode? = null
    private var lastInferenceTime = 0L
    private var lastHitTestTime = 0L

    private val modelPool = mutableListOf<Node>()
    private val augmentedImageMap = mutableMapOf<AugmentedImage, AugmentedImageNode>()
    private val markerlessActiveModels = mutableListOf<Node>()

    @Volatile
    private var latestDetections: List<Detection> = emptyList()

    // *Dynamic lerp
    private val MIN_ALPHA = 0.05f
    private val MAX_ALPHA = 0.6f
    private val MIN_DIST = 0.02f
    private val MAX_DIST = 0.30f

    // !!Change to ui later
	private val MODEL_PATH = "models/wheel.glb"
    private val scaleFactor = 1f
    private val diameterFactor = 1f
    private val SNAP_THRESHOLD = 0.5f // !chang to model size later

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

        // val visibleCount = updatedAugmentedImages.count { image ->
        //     image.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING
        // }
        // Log.d(TAG, "Frame founded marker: $visibleCount markers")

        for (image in updatedAugmentedImages) {
            // Log.d(TAG, "Marker: ${image.name} | ID: ${image.index} | State: ${image.trackingState} | Method: ${image.trackingMethod}")
            when (image.trackingState) {
                TrackingState.TRACKING -> {
                    if (!augmentedImageMap.containsKey(image)) {
                        // Log.d(TAG, "Found new marker: ${image.name}")
                        val imageNode = AugmentedImageNode(arSceneView.engine, image)

                        val model = getOrCreateModel(MODEL_PATH)
                        model.scale = Float3(scaleFactor, scaleFactor, scaleFactor) 
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

    // *Run Inference @ 15 FPS
    private fun processMarkerlessInference(frame: Frame) {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastInferenceTime < INFERENCE_INTERVAL_MS) return

        lastInferenceTime = currentTime

        try {
            val tensor = frameConverter.convertFrameToTensor(frame)

            onnxRuntimeHandler.runOnnxInferenceAsync(tensor) { detections ->
                latestDetections = detections
                onnxOverlayView.updateDetections(latestDetections)
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

        val claimedModels = mutableSetOf<Node>()
        val viewW = arSceneView.width
        val viewH = arSceneView.height

        for (det in detections) {
            val bbox = det.boundingBox

            val cx = bbox.centerX() * viewW
            val cy = bbox.centerY() * viewH
            val w = bbox.width() * viewW
            val h = bbox.height() * viewH

            val hitPoints = mutableListOf<PointF>()
            hitPoints.add(PointF(cx, cy))

            val r25W = w * 0.25f / 2f
            val r25H = h * 0.25f / 2f
            hitPoints.add(PointF(cx, cy - r25H)) // UP
            hitPoints.add(PointF(cx, cy + r25H)) // DOWN
            hitPoints.add(PointF(cx - r25W, cy)) // LEFT
            hitPoints.add(PointF(cx + r25W, cy)) // RIGHT

            val r60W = w * 0.60f / 2f
            val r60H = h * 0.60f / 2f
            hitPoints.add(PointF(cx + r60W, cy - r60H)) // UP-RIGHT
            hitPoints.add(PointF(cx - r60W, cy - r60H)) // UP-LEFT
            hitPoints.add(PointF(cx + r60W, cy + r60H)) // LOW-RIGHT
            hitPoints.add(PointF(cx - r60W, cy + r60H)) // LOW-LEFT

            val validPoses = mutableListOf<Pose>()
            for (pt in hitPoints) {
                val hitList = frame.hitTest(pt.x, pt.y)
                val hit = hitList.firstOrNull {
                    val trackable = it.trackable
                    trackable is Plane && trackable.isPoseInPolygon(it.hitPose)
                }
                if (hit != null) {
                    validPoses.add(hit.hitPose)
                }
            }

            if (validPoses.isEmpty()) continue

            val wallPose = validPoses.firstOrNull { !isUpwardSurface(it) }

            val validPoints = validPoses.map { Float3(it.tx(), it.ty(), it.tz()) }
            val bestPos = calculateBestPosition(validPoints)

            if (bestPos != null && wallPose != null) {
                val q = wallPose.rotationQuaternion
                val finalRot = Quaternion(q[0], q[1], q[2], q[3])

                val closestModel = markerlessActiveModels
                    .filter { !claimedModels.contains(it) } 
                    .minByOrNull { distance(it.position, bestPos) }

                val dist = if (closestModel != null) distance(closestModel.position, bestPos) else Float.MAX_VALUE

                if (closestModel != null && dist < SNAP_THRESHOLD) {
                    val model = closestModel

                    val dynamicAlpha = calculateDynamicAlpha(model.position, bestPos)
                    
                    model.position = lerp(model.position, bestPos, dynamicAlpha) 
                    model.quaternion = slerp(model.quaternion, finalRot, dynamicAlpha / 2) 
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

    private fun isUpwardSurface(pose: Pose): Boolean {
        val axisY = FloatArray(3)
        pose.getTransformedAxis(1, 0f, axisY, 0)
        
        // *0.7f-45degrees, 0.866f-30degrees, 0.5f-60degrees 
        return axisY[1] > 0.7f
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

    private fun calculateDynamicAlpha(currentPos: Float3, targetPos: Float3): Float {
        val dist = distance(currentPos, targetPos)
        val t = ((dist - MIN_DIST) / (MAX_DIST - MIN_DIST)).coerceIn(0f, 1f)
        return MIN_ALPHA + (MAX_ALPHA - MIN_ALPHA) * t
    }

    private fun getOrCreateModel(path: String): Node {
        val freeModel = modelPool.find { it.parent == null || !it.isVisible }

        return if (freeModel != null) {
            freeModel.isVisible = true
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