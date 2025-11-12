package com.arwheelapp.modules

import android.content.Context
import android.util.Log
import android.view.View
import android.graphics.Color
import java.io.File
import kotlin.math.*
import dev.romainguy.kotlin.math.Float3
import com.arwheelapp.utils.FrameConverter
import com.arwheelapp.utils.PositionHandler
import com.arwheelapp.utils.OnnxRuntimeHandler
import com.google.ar.core.*
import com.google.ar.core.Session
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.core.AugmentedImage
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.*
import io.github.sceneview.node.Node
import io.github.sceneview.node.ModelNode
import io.github.sceneview.ar.node.TrackableNode
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.ar.node.HitResultNode
import io.github.sceneview.collision.CollisionSystem.hitTest

class ARRendering(private val context: Context) {
	private const val TAG_MARKER_BASED = "ARRendering-MarkerBased"
	private const val TAG_MARKERLESS = "ARRendering-Markerless"

    private lateinit var modelRendering: ModelRendering
    private lateinit var frameConverter: FrameConverter
    private lateinit var positionHandler: PositionHandler
    private lateinit var onnxRuntimeHandler: OnnxRuntimeHandler
    // private lateinit var onnxOverlay: OnnxOverlayView

    companion object {
		private val MARKER_DATABASES = mapOf(
			"marker" to "markers/front_marker.imgdb",
		)
		private val MODEL_PATHS = mapOf(
			"wheel1" to "models/wheel1.glb"
		)
    }

	data class Pose3D(
        val position: Float3,
        val rotation: Float3,
    )

	private var isMarkerDbSetup = false
	private var previousMode: ARActivity.ARMode? = null

	private var markerBasedNode: AugmentedImageNode? = null
	private var markerlessNode: HitResultNode? = null

	private var wheelNodes: Node = Node()
	private val updatedNodes = mutableSetOf<Node>()

	private var lastInferenceTime = 0L
	private var frameIntervalNs = 33_000_000L
	private var lastYoloDuration = 0L

	fun render(session: Session, arSceneView: ARSceneView, frame: Frame, currentMode: ARActivity.ARMode) {
		wheelNodes.takeIf { it.parent == null }?.let { arSceneView.addChildNode(it) }
		
		if (previousMode != currentMode) {
			wheelNodes.childNodes.forEach { it.isVisible = false }
			previousMode = currentMode
			updatedNodes.clear()
		}

		when (currentMode) {
			ARActivity.ARMode.MARKER_BASED -> {
				if (frame.camera.trackingState != TrackingState.TRACKING) return
				
				val updatedImages: Collection<AugmentedImage> = frame.getUpdatedTrackables(AugmentedImage::class.java)
				Log.d(TAG_MARKER_BASED, "Updated markers count=${updatedImages.size}")
				for (img in updatedImages) {
					Log.d(TAG_MARKER_BASED, "Marker: ${img.name}, state=${img.trackingState}")
					markerBasedNode = markerBasedNode ?: AugmentedImageNode()
					markerBasedNode!!.augmentedImage = img
					markerBasedNode!!.createAnchor()
					if (markerBasedNode!!.parent == null) arSceneView.addChildNode(markerBasedNode!!)

					if (img.trackingState == TrackingState.TRACKING) {
						updateOrAddWheelNode(MODEL_PATHS["wheel1"]!!, markerBasedNode).let { updatedNodes.add(it) }
					}
				}
				wheelNodes.childNodes.forEach { it.isVisible = it in updatedNodes }
			}

			ARActivity.ARMode.MARKERLESS -> {
				val currentTime = frame.timestamp
				if (currentTime - lastInferenceTime < frameIntervalNs) return
				lastInferenceTime = currentTime

				val start = System.nanoTime()
				val tensor = frameConverter.convertFrameToTensor(frame)
				onnxRuntimeHandler.runOnnxInferenceAsync(tensor) { detections ->
					val duration = System.nanoTime() - start
					lastYoloDuration = duration

					frameIntervalNs = when {
						duration < 25_000_000L -> 22_000_000L
						duration > 50_000_000L -> 50_000_000L
						else -> 33_000_000L
					}

					detections.forEach { bbox ->
						val centerX = bbox.x + bbox.w / 2f
						val centerY = bbox.y + bbox.h / 2f
						// val upX = centerX
						val upY = centerY - bbox.h * 0.1f
						val leftX = centerX - bbox.w * 0.1f
						// val leftY = centerY

						markerlessNode = markerlessNode ?: HitResultNode().apply{
							xPx = centerX
							yPx = centerY
						}
						markerlessNode!!.xPx = centerX
						markerlessNode!!.yPx = centerY
						if (markerlessNode!!.parent == null) arSceneView.addChildNode(markerlessNode!!)

						val upPosHits = frame.hitTest(centerX, upY)
						val leftPosHits = frame.hitTest(leftX, centerY)
						if (upPosHits.isNullOrEmpty() || leftPosHits.isNullOrEmpty()) return@forEach

						val centerPos: Vector3 = markerlessNode!!.worldPosition.toVector3()
						val upPos: Vector3 = upPosHits.first().hitPose.translation.toVector3()
						val leftPos: Vector3 = leftPosHits.first().hitPose.translation.toVector3()

						markerlessNode!!.rotation = getRotationFromThreeVector(centerPos, upPos, leftPos)
						updateOrAddWheelNode(MODEL_PATHS["wheel1"]!!, markerlessNode).let { updatedNodes.add(it) }
					}
					Log.d("AR_FPS", "YOLO took ${duration / 1_000_000}ms → interval=${frameIntervalNs / 1_000_000}ms")
					wheelNodes.childNodes.forEach { it.isVisible = it in updatedNodes }
				}
			}
		}
	}

	// *Setup marker database
	fun setupMarkerDatabase(session: Session) {
		if (isMarkerDbSetup) return

		try {
			Log.d(TAG_MARKER_BASED, "Setting up marker database...")
			val dbFile = File(context.filesDir, MARKER_DATABASES["marker"]!!)
			if (!dbFile.exists()) {
				dbFile.parentFile?.mkdirs()
				context.assets.open(MARKER_DATABASES["marker"]!!).use { input ->
					dbFile.outputStream().use { output ->
						input.copyTo(output)
					}
				}
				Log.d(TAG_MARKER_BASED, "Marker database copied to filesDir")
			}

			val imgDb = AugmentedImageDatabase.deserialize(session, dbFile.inputStream())
			val config = session.config.apply {
				augmentedImageDatabase = imgDb
				updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
			}

			session.configure(config)
			isMarkerDbSetup = true
			Log.d(TAG_MARKER_BASED, "AR Session configured with marker database")
		} catch (e: Exception) {
			Log.e(TAG_MARKER_BASED, "Failed to setup marker database", e)
		}
	}

	// *Create new WheelNode
	private fun createNewWheelNode(modelPath: String, node: Node): ModelNode {
		return modelRendering.createModelNode(modelPath).apply {
			position = Float3(node.worldPosition)
		}
	}

	// *Distance between two positions in 3D space
	private fun distanceBetweenNode(pos1: Float3, pos2: Float3): Float {
		val dx = pos1.x - pos2.x
		val dy = pos1.y - pos2.y
		val dz = pos1.z - pos2.z
		return sqrt(dx*dx + dy*dy + dz*dz)
	}

	// *Update or add wheel node based on proximity
	private fun updateOrAddWheelNode(modelPath: String, baseNode: TrackableNode): ModelNode {
		val newPos = Float3(baseNode.worldPosition)

		val childNodes = wheelNodes.childNodes.toList()
		val nearest = childNodes
			.mapIndexed { i, node -> distanceBetweenNode(node.position, newPos) to i }
			.minByOrNull { it.first }

		val modelNode: ModelNode = when {
			childNodes.isEmpty() -> {
				wheelNodes.addChildNode(createNewWheelNode(modelPath, baseNode))
			}

			nearest != null && nearest.first < 0.5f -> {
				(childNodes[nearest.second] as ModelNode).apply { position = newPos }
			}

			childNodes.size < 3 -> {
				wheelNodes.addChildNode(createNewWheelNode(modelPath, baseNode))
			}

			else -> {
				val furthest = childNodes
					.mapIndexed { i, node -> distanceBetweenNode(node.position, newPos) to i }
					.maxByOrNull { it.first }
				(childNodes[furthest?.second ?: 0] as ModelNode).apply { position = newPos }
			}
		}
		modelNode.isVisible = true
		return modelNode
	}

	// *Get rotation with 3 vector points
	private fun getRotationFromThreeVector(center: Vector3, up: Vector3, left: Vector3): Float3 {
		val upVec = normalized(subtract(up, center))
		val leftVec = normalized(subtract(left, center))
		val forwardVec = normalized(cross(upVec, leftVec))

		// val m11 = leftVec.x
		val m12 = upVec.x
		// val m13 = forwardVec.x
		// val m21 = leftVec.y
		val m22 = upVec.y
		// val m23 = forwardVec.y
		val m31 = leftVec.z
		val m32 = upVec.z
		val m33 = forwardVec.z

		val pitch = atan2(-m32, sqrt(m31 * m31 + m33 * m33))
		val yaw = atan2(m31, m33)
		val roll = atan2(m12, m22)

		return Float3(
			roll * 180f / PI.toFloat(),
			pitch * 180f / PI.toFloat(),
			yaw * 180f / PI.toFloat()
		)
	}

	// *Clear wheelNodes
	fun clearWheelNodes() {
        wheelNodes.destroy()
    }
}
