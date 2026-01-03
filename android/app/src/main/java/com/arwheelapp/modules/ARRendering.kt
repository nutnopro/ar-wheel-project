package com.arwheelapp.modules

import android.content.Context
import android.graphics.PointF
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
import kotlinx.coroutines.*

class ARRendering(private val context: Context, private val onnxOverlayView: OnnxOverlayView) {
    private val modelManager = ModelManager()
    private val frameConverter = FrameConverter()
    private val onnxRuntimeHandler = OnnxRuntimeHandler(context)

    private val TAG = "ARRendering: "
	private val MARKER_DB_NAME = "markers/marker.jpg"
	private val MODEL_PATH = "models/wheel.glb"
    private val YOLO_INPUT_SIZE = 320f

    private var previousMode: ARActivity.ARMode? = null

    private val modelsPool = mutableListOf<ModelNode>()
    private val markerNodesPool = mutableMapOf<AugmentedImageNode>()

    @Volatile
    private var currentDetections: List<OnnxRuntimeHandler.Detection>? = null

    fun render(session: Session, arSceneView: ARSceneView, frame: Frame, currentMode: ARActivity.ARMode) {
        if (previousMode != currentMode) {
            hideAllModels()
            previousMode = currentMode
        }

        when (currentMode) {
            ARActivity.ARMode.MARKER_BASED -> processMarkerBased(arSceneView, frame)
            ARActivity.ARMode.MARKERLESS -> {
                processMarkerless(arSceneView, frame)
                triggerInference(frame)
            }
        }
    }

    // !Fix this
    private fun processMarkerBased(arSceneView: ARSceneView, frame: Frame) {
        val updatedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)

        for (img in updatedImages) {
            when (img.trackingState) {
                TrackingState.TRACKING -> {
                    var node = AugmentedImageNode(arSceneView.engine, img).apply {
                        val modelNode = modelManager.createModelNode(arSceneView, MODEL_PATH)
                        addChildNode(modelNode)
                    }
                    arSceneView.addChildNode(node)
                }
                TrackingState.PAUSED -> {
                    markerNodesPool.forEach { it.isVisible = false }
                }
                TrackingState.STOPPED -> {
                    modelsPool.forEach { it.parent = null }
                    markerNodesPool.forEach { it.destroy() }
                }
            }
        }
    }

    private fun triggerInference(frame: Frame) = CoroutineScope {
        // *~15fps
        delay(250.milliseconds)

        val tensor = frameConverter.convertFrameToTensor(frame)
        onnxRuntimeHandler.runOnnxInferenceAsync(tensor) { detections ->
            currentDetections = detections
        }
    }

    // !Fix this
    private fun processMarkerless(arSceneView: ARSceneView, frame: Frame) {
        // ดึงค่าจากตัวแปรกลางมาใช้ แล้วเคลียร์ทิ้ง
        if (frame.camera.trackingState != TrackingState.TRACKING) return

        val detections = currentDetections ?: return
        currentDetections = null 

        // อัปเดต UI Overlay (ทำงานบน UI Thread ปลอดภัยแน่นอน)
        if (onnxOverlayView.visibility == View.VISIBLE) {
            onnxOverlayView.updateDetections(detections)
        }

        // ซ่อน Node เดิมก่อน
        markerlessNodePool.forEach { it.isVisible = false }
        val activeHitNodes = mutableListOf<PoseNode>()

        detections.forEachIndexed { index, detection ->
            // *แก้ BUG: แปลงพิกัดจาก YOLO (Square+Pad) -> Screen (Rect)
            val screenPoint = mapBoundingBoxToScreen(frame, detection)
            
            // ใช้ค่าที่แปลงแล้ว
            val centerX = screenPoint.x
            val centerY = screenPoint.y

            if (centerX < 0 || centerX > arSceneView.width || centerY < 0 || centerY > arSceneView.height) {
                return@forEachIndexed
            }

            // เตรียม Node
            if (index >= markerlessNodePool.size) {
                val newNode = HitResultNode(arSceneView.engine, xPx = centerX, yPx = centerY)
                arSceneView.addChildNode(newNode)
                markerlessNodePool.add(newNode)
            }
            
            val node = markerlessNodePool[index]
            
            try {
                val hitResult = frame.hitTest(centerX, centerY).firstOrNull()
                
                if (hitResult != null) {
                    node.hitResult = hitResult
                    node.isVisible = true

                    // คำนวณ Rotation
                    val upPosHits = frame.hitTest(centerX, centerY * 0.9f).firstOrNull()
                    val leftPosHits = frame.hitTest(centerX * 0.9f, centerY).firstOrNull()

                    if (upPosHits != null && leftPosHits != null) {
                        val centerPos = node.worldPosition.toVector3()
                        val upPos = upPosHits.hitPose.toVector3()
                        val leftPos = leftPosHits.hitPose.toVector3()
                        node.rotation = getRotationFromThreeVector(centerPos, upPos, leftPos)
                    }

                    activeHitNodes.add(node)
                }
            } catch (e: Exception) {
                // Log.e(TAG, "HitTest failed: ${e.message}")
            }

            // *สำคัญ: ใช้ frame ปัจจุบัน (ที่ยังไม่ตาย) ทำ HitTest
            // val hitResult = frame.hitTest(centerX, centerY).firstOrNull()
            
            // if (hitResult != null) {
            //     node.hitResult = hitResult
            //     node.isVisible = true

            //     // Calculate Rotation
            //     val upPosHits = frame.hitTest(centerX, centerY * 0.9f).firstOrNull()
            //     val leftPosHits = frame.hitTest(centerX * 0.9f, centerY).firstOrNull()

            //     if (upPosHits != null && leftPosHits != null) {
            //         val centerPos = node.worldPosition.toVector3()
            //         val upPos = upPosHits.hitPose.toVector3()
            //         val leftPos = leftPosHits.hitPose.toVector3()
            //         node.rotation = getRotationFromThreeVector(centerPos, upPos, leftPos)
            //     }

            //     activeHitNodes.add(node)
            // }
        }
        updatemodelsPool(arSceneView, activeHitNodes)
    }

    // !Fix this
    private fun mapBoundingBoxToScreen(frame: Frame, detection: OnnxRuntimeHandler.Detection): PointF {
        // val inputSize = YOLO_INPUT_SIZE // ต้องตรงกับใน OnnxRuntimeHandler

        // // 1. คำนวณ Scale ว่าภาพถูกย่อลงไปเท่าไหร่ (ยึดด้านที่ยาวที่สุดเป็นหลัก)
        // // หน้าจอ AR แนวตั้ง: ความสูงจะเป็นด้านยาว (Height > Width)
        // // ดังนั้น scale จะถูกคิดจากความสูงหน้าจอ เทียบกับ inputSize
        // val scale = inputSize / viewH.toFloat() 

        // // 2. คำนวณขนาดจริงของภาพเมื่ออยู่ในกล่อง 320
        // val scaledWidthInBox = viewW * scale
        // val scaledHeightInBox = viewH * scale // จะเท่ากับ 320 พอดี

        // // 3. คำนวณขอบดำ (Padding) ที่เกิดขึ้นในกล่อง 320
        // // ปกติภาพแนวตั้งจะมีขอบดำซ้าย-ขวา (Pillarbox)
        // val xPadding = (inputSize - scaledWidthInBox) / 2f
        // val yPadding = (inputSize - scaledHeightInBox) / 2f // ปกติจะเป็น 0 ถ้าเต็มความสูง
        
        // // 4. แปลง Normalized (0..1) กลับเป็น Pixel ในกล่อง 320 ก่อน
        // val xInBox = detection.x * inputSize
        // val yInBox = detection.y * inputSize
        
        // // 5. ลบขอบดำออก (Un-pad)
        // val xNoPad = xInBox - xPadding
        // val yNoPad = yInBox - yPadding
        
        // // 6. ขยายกลับให้เต็มหน้าจอ (Un-scale)
        // // ระวัง: ต้อง Clamp ค่าไม่ให้น้อยกว่า 0 หรือเกินหน้าจอ
        // val finalX = (xNoPad / scale).coerceIn(0f, viewW.toFloat())
        // val finalY = (yNoPad / scale).coerceIn(0f, viewH.toFloat())

        // return PointF(finalX, finalY)
        

        // val contentWidth = YOLO_INPUT_SIZE * 0.75f // 320 * (3/4) = 240
        // val padX = (YOLO_INPUT_SIZE - contentWidth) / 2f // (320-240)/2 = 40
        
        // // แปลงเป็น 0..1 เทียบกับรูปภาพ Portrait จริงๆ
        // val normX_Portrait = (detection.x * YOLO_INPUT_SIZE - padX) / contentWidth
        // val normY_Portrait = detection.y // ความสูงเต็มพอดี ไม่ต้องตัด Padding
        
        // // 2. Un-rotate: แปลงจาก Portrait (AI เห็น) กลับไปเป็น Sensor Coordinate (Landscape)
        // // ตาม Logic ใน FrameConverter: SensorX = PortraitY, SensorY = 1 - PortraitX
        // val sensorX = normY_Portrait
        // val sensorY = 1.0f - normX_Portrait

        // // 3. Transform: ให้ ARCore แปลงจาก Sensor 0..1 ไปเป็น Screen Pixel (View)
        // // วิธีนี้จะแก้ปัญหา ARCore Crop ภาพ (Zoom) ทำให้ตำแหน่งแม่นยำขึ้น
        // val inputCoords = floatArrayOf(sensorX, sensorY)
        // val outputCoords = floatArrayOf(0f, 0f)

        // try {
        //     frame.transformCoordinates2d(
        //         Coordinates2d.IMAGE_NORMALIZED, inputCoords,
        //         Coordinates2d.VIEW, outputCoords
        //     )
        // } catch (e: Exception) {
        //     // กรณี Error ให้คืนค่าตรงกลางจอไปก่อน
        //     return PointF(0f, 0f) 
        // }
        // return PointF(outputCoords[0], outputCoords[1])


        val intrinsics = frame.camera.imageIntrinsics
        val imageDimensions = intrinsics.imageDimensions // [Width, Height] ของภาพดิบ (Landscape)
        
        val rawW = imageDimensions[0].toFloat()
        val rawH = imageDimensions[1].toFloat()

        if (rawW == 0f || rawH == 0f) return PointF(0f, 0f)

        val rotatedAspect = rawH / rawW 

        val contentWidth = YOLO_INPUT_SIZE * rotatedAspect
        val padX = (YOLO_INPUT_SIZE - contentWidth) / 2f
        
        val normX_Portrait = (detection.x * YOLO_INPUT_SIZE - padX) / contentWidth
        val normY_Portrait = detection.y 
        
        val sensorX = normY_Portrait
        val sensorY = 1.0f - normX_Portrait

        val inputCoords = floatArrayOf(sensorX, sensorY)
        val outputCoords = floatArrayOf(0f, 0f)

        try {
            frame.transformCoordinates2d(
                Coordinates2d.IMAGE_NORMALIZED, inputCoords,
                Coordinates2d.VIEW, outputCoords
            )
        } catch (e: Exception) {
            return PointF(0f, 0f) 
        }

        return PointF(outputCoords[0], outputCoords[1])
    }

    // !Fix this
    private fun updatemodelsPool(arSceneView: ARSceneView, targets: List<PoseNode>) {
        targets.forEachIndexed { index, targetNode ->
            // *create new model if pool exhausted
            if (index >= modelsPool.size) {
                val newModel = modelManager.createModelNode(arSceneView, MODEL_PATH)
                modelsPool.add(newModel)
            }

            // *extract model from pool
            val modelNode = modelsPool[index]

            if (modelNode.parent != targetNode) {
                modelNode.parent = targetNode
                modelNode.position = Float3(0f, 0f, 0f)
                modelNode.rotation = Float3(0f, 0f, 0f)
                modelNode.scale = Float3(1f, 1f, 1f)
            }

            modelNode.isVisible = true
        }

        // *hide unused models
        if (targets.size < modelsPool.size) {
            for (i in targets.size until modelsPool.size) {
                val unusedModel = modelsPool[i]
                unusedModel.isVisible = false
                unusedModel.parent = null 
            }
        }
    }

    private fun hideAllModels() {
        modelsPool.forEach { 
            it.parent = null
            it.isVisible = false
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

    // *Rotation Calc
    private fun getRotationFromThreeVector(center: Vector3, up: Vector3, left: Vector3): Float3 {
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
        modelsPool.forEach { it.destroy() }
        modelsPool.clear()

        markerNodesPool.values.forEach { it.destroy() }
        markerNodesPool.clear()

        markerlessNodePool.forEach { it.destroy() }
        markerlessNodePool.clear()
    }
}