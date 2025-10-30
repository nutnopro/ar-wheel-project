package com.arwheelapp.modules

import android.content.Context
import android.util.Log
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.node.ModelNode
import dev.romainguy.kotlin.math.Float3
import com.google.ar.core.*
import java.io.File
import com.google.ar.core.TrackingState
import com.google.ar.core.AugmentedImage
import com.google.ar.core.Frame
import android.graphics.Color
import android.view.View
import io.github.sceneview.math.*
import kotlin.math.*
import com.arwheelapp.utils.FrameConverter
import com.arwheelapp.utils.PositionHandler
import com.arwheelapp.utils.OnnxRuntimeHandler
import io.github.sceneview.node.ModelNode
import com.google.ar.core.Session
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
		val MARKER_DATABASES = mapOf(
			"marker" to "markers/front_marker.imgdb",
		)
		val MODEL_PATHS = mapOf(
			"wheel1" to "models/wheel1.glb"
		)
    }

	data class Pose3D(
        val position: Float3,
        val rotation: Float3,
    )

	private var wheelNodes: MutableList<ModelNode> = mutableListOf()

	fun render(session: Session, arSceneView: ARSceneView, frame: Frame, currentMode: ARActivity.ARMode) {
		when (currentMode) {
			// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			// ! แก้การเคลื่อนที่ของไปตาม parent node --- ใช้ AugmentedImageNode หาตำแหน่งแล้วส่งให้ wheelNode แต่ละตัว
			ARActivity.ARMode.MARKER_BASED -> {
				wheelNodes?.forEach { wheelNode ->
					wheelNode.isVisible = false
				}

				markerBasedNode = AugmentedImageNode()
				if (frame.camera.trackingState != TrackingState.TRACKING) return
				setupMarkerDatabase(session)

				val updatedImages: Collection<AugmentedImage> = frame.getUpdatedTrackables(AugmentedImage::class.java)
				Log.d(TAG_MARKER_BASED, "Updated markers count=${updatedImages.size}")
				for (img in updatedImages) {
					markerBasedNode.apply {
						augmentedImage = img
						createAnchor(img.getcenterPose())
					}
					Log.d(TAG_MARKER_BASED, "Marker: ${img.name}, state=${img.trackingState}")
					if (img.trackingState == TrackingState.TRACKING) {
						when (wheelNodes.size) {
							0 -> {
								val wheelNode = createNewWheelNode(MODEL_PATHS["wheel1"]!!, markerBasedNode).apply {
									isVisible = true
								}
								wheelNodes.addChildNode(wheelNode)
							}
							1 -> {
								val dist = distanceModelToMarker(wheelNodes[0].position, Float3(markerBasedNode.worldPosition))
								if (dist < 0.5f) {
									wheelNodes[0].position = Float3(markerBasedNode.worldPosition).apply {
										isVisible = true
									}
								} else {
									val wheelNode = createNewWheelNode(MODEL_PATHS["wheel1"]!!, markerBasedNode).apply {
										isVisible = true
									}
									wheelNodes.addChildNode(wheelNode)
								}
							}
							2 -> {
								val dists = mutableListOf<Pair<Float, Int>>()
								wheelNodes.forEachIndexed { index, wheelNode ->
									val dist = distanceModelToMarker(wheelNode.position, Float3(markerBasedNode.worldPosition))
									dists.add(dist to index)
								}
								val nearest = dists.minByOrNull { it.first }
								if (nearest != null && nearest.first < 0.5f) {
									wheelNodes[nearest.second].position = Float3(markerBasedNode.worldPosition).apply {
										isVisible = true
									}
								} else {
									val wheelNode = createNewWheelNode(MODEL_PATHS["wheel1"]!!, markerBasedNode).apply {
										isVisible = true
									}
									wheelNodes.addChildNode(wheelNode)
								}
							}
							3 -> {
								val dists = mutableListOf<Pair<Float, Int>>()
								wheelNodes.forEachIndexed { index, wheelNode ->
									val dist = distanceModelToMarker(wheelNode.position, Float3(markerBasedNode.worldPosition))
									dists.add(dist to index)
								}
								val nearest = dists.minByOrNull { it.first }
								val furthest = dists.maxByOrNull { it.first }
								if (nearest != null && nearest.first < 0.5f) {
									wheelNodes[nearest.second].position = Float3(markerBasedNode.worldPosition).apply {
										isVisible = true
									}
								} else if (furthest != null) {
									wheelNodes[furthest.second].position = Float3(markerBasedNode.worldPosition).apply {
										isVisible = true
									}
								}
								wheelNode.isVisible = true
							}
						}
					}
				}
				arSceneView.addChildNodes(wheelNodes)
			}

			// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			// ! แก้การเคลื่อนที่ของไปตาม parent node --- ใช้ AugmentedImageNode หาตำแหน่งแล้วส่งให้ wheelNode แต่ละตัว 
			ARActivity.ARMode.MARKERLESS -> {
				wheelNodes?.forEach { wheelNode ->
					wheelNode.isVisible = false
				}

				tensor = frameConverter.convertFrameToTensor(frame)
				bbox = onnxRuntimeHandler.runOnnxInference(tensor)

				val centerX = bbox.x + bbox.w / 2f
                val centerY = bbox.y + bbox.h / 2f
                // val upX = centerX
                val upY = centerY - bbox.h * 0.1f
                val leftX = centerX - bbox.w * 0.1f
                // val leftY = centerY

				markerlessNode = HitResultNode().apply {
					xPx = centerX
					ypx = centerY
				}
				val upPosHits: Float3 = frame.hitTest(centerX, upY)
				val leftPosHits: Float3 = frame.hitTest(leftX, centerY)

				val centerPos: Vector3 = markerlessNode.worldPosition.toVector3()
				val upPos: Vector3 = upPosHits.toVector3()
				val leftPos: Vector3 = leftPosHits.toVector3()

				markerlessNode.rotation = getRotationFromThreeVector(centerPos, upPos, leftPos)

				when (wheelNodes.size) {
					0 -> {
						val wheelNode = createNewWheelNode(MODEL_PATHS["wheel1"]!!, markerlessNode).apply {
							isVisible = true
						}
						wheelNodes.addChildNode(wheelNode)
					}
					1 -> {
						val dist = distanceModelToMarker(wheelNodes[0].position, Float3(markerBasedNode.worldPosition))
						if (dist < 0.5f) {
							wheelNodes[0].position = Float3(markerBasedNode.worldPosition).apply {
								isVisible = true
							}
						} else {
							val wheelNode = createNewWheelNode(MODEL_PATHS["wheel1"]!!, markerlessNode).apply {
								isVisible = true
							}
							wheelNodes.addChildNode(wheelNode)
						}
					}
					2 -> {
						val dists = mutableListOf<Pair<Float, Int>>()
						wheelNodes.forEachIndexed { index, wheelNode ->
							val dist = distanceModelToMarker(wheelNode.position, Float3(markerBasedNode.worldPosition))
							dists.add(dist to index)
						}
						val nearest = dists.minByOrNull { it.first }
						if (nearest != null && nearest.first < 0.5f) {
							wheelNodes[nearest.second].position = Float3(markerBasedNode.worldPosition).apply {
								isVisible = true
							}
						} else {
							val wheelNode = createNewWheelNode(MODEL_PATHS["wheel1"]!!, markerBasedNode).apply {
								isVisible = true
							}
							wheelNodes.addChildNode(wheelNode)
						}
					}
					3 -> {
						val dists = mutableListOf<Pair<Float, Int>>()
						wheelNodes.forEachIndexed { index, wheelNode ->
							val dist = distanceModelToMarker(wheelNode.position, Float3(markerBasedNode.worldPosition))
							dists.add(dist to index)
						}
						val nearest = dists.minByOrNull { it.first }
						val furthest = dists.maxByOrNull { it.first }
						if (nearest != null && nearest.first < 0.5f) {
							wheelNodes[nearest.second].position = Float3(markerBasedNode.worldPosition).apply {
								isVisible = true
							}
						} else if (furthest != null) {
							wheelNodes[furthest.second].position = Float3(markerBasedNode.worldPosition).apply {
								isVisible = true
							}
						}
					}
				}
				arSceneView.addChildNodes(wheelNodes)
			}
		}
	}

	//? Setup marker database
	private fun setupMarkerDatabase(session: Session) {
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
			Log.d(TAG_MARKER_BASED, "AR Session configured with marker database")
		} catch (e: Exception) {
			Log.e(TAG_MARKER_BASED, "Failed to setup marker database", e)
		}
	}

	//? Create new WheelNode
	private fun createNewWheelNode(modelPath: String, node: Node): ModelNode {
		return modelRendering.createModelNode(modelPath).apply {
			position = Float3(node.worldPosition)
		}
	}

	//? Distance between two positions in 3D space
	private fun distanceBetween(pos1: Float3, pos2: Float3): Float {
		val dx = pos1.x - pos2.x
		val dy = pos1.y - pos2.y
		val dz = pos1.z - pos2.z
		return sqrt(dx*dx + dy*dy + dz*dz)
	}

	//? Get rotation with 3 vector points
	private fun getRotationFromThreeVector(center: Vector3, up: Vector3, left: Vector3): Quaternion {
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

	//? Clear wheelNodes
	fun clearWheelNodes() {
        wheelNodes?.clearChildNodes()
        wheelNodes?.destroy()
    }

}
