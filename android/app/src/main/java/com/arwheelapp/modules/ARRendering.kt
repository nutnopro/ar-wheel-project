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
import android.graphics.PointF

class ARRendering(private val context: Context, private val onnxOverlayView: OnnxOverlayView) {
    private val modelManager = ModelManager()
    private val frameConverter = FrameConverter()
    private val onnxRuntimeHandler = OnnxRuntimeHandler(context)

    private val TAG = "ARRendering: "
	private val MARKER_DB_NAME = "markers/marker.jpg"
	private val MODEL_PATH = "models/wheel.glb"
    private val YOLO_INPUT_SIZE = 320f

    private var previousMode: ARActivity.ARMode? = null

    private val markerNodes = mutableMapOf<AugmentedImage, AugmentedImageNode>()
    private val markerlessNodePool = mutableListOf<HitResultNode>()
    private val wheelModelPool = mutableListOf<ModelNode>()

    private var lastInferenceTime = 0L
    private var frameIntervalNs = 33_000_000L
    private var isInferencing = false // ป้องกันการเรียกซ้อน

    @Volatile
    private var pendingDetections: List<OnnxRuntimeHandler.Detection>? = null


    fun render(session: Session, arSceneView: ARSceneView, frame: Frame, currentMode: ARActivity.ARMode) {
        // Log.d(TAG, "render: Frame update, Mode=$currentMode")
        if (previousMode != currentMode) {
            // Log.d(TAG, "render: Mode changed from $previousMode to $currentMode. Hiding all models.")
            hideAllModels()
            previousMode = currentMode
        }

        when (currentMode) {
            ARActivity.ARMode.MARKER_BASED -> processMarkerBased(arSceneView, frame)
            ARActivity.ARMode.MARKERLESS -> {
                processPendingMarkerlessDetections(arSceneView, frame)
                triggerMarkerlessInference(frame)
            }
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

    // private fun processMarkerless(arSceneView: ARSceneView, frame: Frame) {
    //     // Log.d(TAG, "processMarkerless: Processing markerless AR frame")
    //     val currentTime = frame.timestamp
    //     if (currentTime - lastInferenceTime < frameIntervalNs) return
    //     lastInferenceTime = currentTime

    //     // Log.d(TAG, "processMarkerless: Starting inference cycle")
    //     val start = System.nanoTime()
    //     val tensor = frameConverter.convertFrameToTensor(frame)

    //     onnxRuntimeHandler.runOnnxInferenceAsync(tensor) { detections ->
    //         // Log.d(TAG, "processMarkerless: Inference callback received with ${detections.size} detections")
    //         val inferenceDuration = (System.nanoTime() - start) / 1_000_000
    //         val fullFrameStart = System.nanoTime()

    //         if (onnxOverlayView.visibility == View.VISIBLE) {
    //             onnxOverlayView.updateDetections(detections)
    //         }

    //         // *prepare Nodes for YOLO (Reset Pool)
    //         markerlessNodePool.forEach { it.isVisible = false }
    //         val activeHitNodes = mutableListOf<PoseNode>()

    //         detections.forEachIndexed { index, bbox ->
    //             val centerX = bbox.x * arSceneView.width
    //             val centerY = bbox.y * arSceneView.height
    //             // Log.d(TAG, "Detection #$index: x=$centerX, y=$centerY, conf=${bbox.confidence}")

    //             // *extract Node from Pool (create new if exhausted)
    //             if (index >= markerlessNodePool.size) {
    //                 // Log.d(TAG, "processMarkerless: Pool exhausted. Creating new HitResultNode.")
    //                 val newNode = HitResultNode(arSceneView.engine, xPx = centerX, yPx = centerY)
    //                 arSceneView.addChildNode(newNode)
    //                 markerlessNodePool.add(newNode)
    //             }
                
    //             val node = markerlessNodePool[index]
                
    //             // *calculate HitTest to place on real surface
    //             val hitResult = frame.hitTest(centerX, centerY).firstOrNull()
    //             if (hitResult != null) {
    //                 // Log.d(TAG, "processMarkerless: HitTest success for detection #$index")
    //                 node.hitResult = hitResult
    //                 node.isVisible = true

    //                 // *calculate Rotation (optional)
    //                 val upPosHits = frame.hitTest(centerX, centerY * 0.9f).firstOrNull()
    //                 val leftPosHits = frame.hitTest(centerX * 0.9f, centerY).firstOrNull()

    //                 if (upPosHits != null && leftPosHits != null) {
    //                     val centerPos = node.worldPosition.toVector3()
    //                     val upPos = upPosHits.hitPose.toVector3()
    //                     val leftPos = leftPosHits.hitPose.toVector3()
    //                     node.rotation = getRotationFromThreeVector(centerPos, upPos, leftPos)
    //                     // Log.d(TAG, "processMarkerless: Rotation calculated for #$index")
    //                 }

    //                 activeHitNodes.add(node)
    //             }
    //         }

    //         // *use Object Pool to manage 3D Models
    //         updateModelPool(arSceneView, activeHitNodes)

    //         // *calculate frame interval dynamically
    //         val fullFrameDuration = System.nanoTime() - fullFrameStart
    //         frameIntervalNs = when {
    //             fullFrameDuration < 25_000_000L -> 22_000_000L
    //             fullFrameDuration > 50_000_000L -> 50_000_000L
    //             else -> 33_000_000L
    //         }
    //         // Log.d(TAG, "processMarkerless: Cycle complete. Next interval: ${frameIntervalNs/1_000_000}ms")
    //     }
    // }

    private fun triggerMarkerlessInference(frame: Frame) {
        val currentTime = frame.timestamp
        // เช็คเวลา และเช็คว่ากำลังรันอยู่หรือไม่ (ป้องกันคิวเต็ม)
        if (isInferencing || currentTime - lastInferenceTime < frameIntervalNs) return

        lastInferenceTime = currentTime
        isInferencing = true // ล็อก

        val start = System.nanoTime()

        // แปลงภาพ (ระวัง: ต้องแน่ใจว่า frameConverter จัดการปิด image ภายในแล้ว)
        val tensor = frameConverter.convertFrameToTensor(frame)

        // รัน AI ใน Background Thread
        onnxRuntimeHandler.runOnnxInferenceAsync(tensor) { detections ->
            // AI เสร็จแล้ว เก็บผลใส่ตัวแปรกลาง
            pendingDetections = detections
            isInferencing = false // ปลดล็อก

            // ปรับ interval ตามความเร็วเครื่อง
            val duration = (System.nanoTime() - start)
            frameIntervalNs = if (duration > 50_000_000L) 66_000_000L else 33_000_000L
        }
    }

    private fun processPendingMarkerlessDetections(arSceneView: ARSceneView, frame: Frame) {
        // ดึงค่าจากตัวแปรกลางมาใช้ แล้วเคลียร์ทิ้ง
        if (frame.camera.trackingState != TrackingState.TRACKING) return

        val detections = pendingDetections ?: return
        pendingDetections = null 

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
        updateModelPool(arSceneView, activeHitNodes)
    }

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
        
        // คำนวณ Ratio เมื่อภาพถูกหมุนเป็น Portrait (Height / Width)
        // เช่น 1920x1080 (16:9) -> Portrait คือ 1080/1920 = 0.5625
        val rawW = imageDimensions[0].toFloat()
        val rawH = imageDimensions[1].toFloat()

        if (rawW == 0f || rawH == 0f) return PointF(0f, 0f)

        val rotatedAspect = rawH / rawW 

        // 2. คำนวณ Un-letterbox ด้วย Ratio จริง
        val contentWidth = YOLO_INPUT_SIZE * rotatedAspect
        val padX = (YOLO_INPUT_SIZE - contentWidth) / 2f
        
        // แปลงเป็น 0..1 เทียบกับรูปภาพ Portrait
        val normX_Portrait = (detection.x * YOLO_INPUT_SIZE - padX) / contentWidth
        val normY_Portrait = detection.y 
        
        // 3. Un-rotate: แปลงกลับเป็น Sensor Coordinate (Landscape) เพื่อส่งให้ ARCore
        val sensorX = normY_Portrait
        val sensorY = 1.0f - normX_Portrait

        // 4. Transform: ให้ ARCore แปลงจาก Sensor -> Screen Pixel (View)
        // ฟังก์ชันนี้จะจัดการเรื่อง Zoom/Crop ของหน้าจอให้เอง
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