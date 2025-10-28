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
import kotlin.math.sqrt
import com.arwheelapp.utils.FrameConverter
import com.arwheelapp.utils.PositionHandler
import com.arwheelapp.utils.OnnxRuntimeHandler
import io.github.sceneview.node.ModelNode
import com.google.ar.core.Session


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
			ARActivity.ARMode.MARKER_BASED -> {
				clearWheelNodes()
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
								val wheelNode = createNewWheelNode(MODEL_PATHS["wheel1"]!!, markerBasedNode)
								wheelNodes.add(wheelNode)
							}
							1 -> {
								val dist = distanceModelToMarker(wheelNodes[0].position, Float3(markerBasedNode.worldPosition))
								if (dist < 0.5f) {
									wheelNodes[0].position = Float3(markerBasedNode.worldPosition)
								} else {
									val wheelNode = createNewWheelNode(MODEL_PATHS["wheel1"]!!, markerBasedNode)
									wheelNodes.add(wheelNode)
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
									wheelNodes[nearest.second].position = Float3(markerBasedNode.worldPosition)
								} else {
									val wheelNode = createNewWheelNode(MODEL_PATHS["wheel1"]!!, markerBasedNode)
									wheelNodes.add(wheelNode)
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
									wheelNodes[nearest.second].position = Float3(markerBasedNode.worldPosition)
								} else if (furthest != null) {
									wheelNodes[furthest.second].position = Float3(markerBasedNode.worldPosition)
								}
							}
						}
						wheelNodes.forEach { wheelNode ->
							wheelNode.isVisible = true
						}
					}
				}
				wheelNodes.forEach { wheelNode ->
					markerBasedNode.addChildNode(wheelNode)
				}
				arSceneView.addChild(markerBasedNode)
			}

			// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			ARActivity.ARMode.MARKERLESS -> {
				clearWheelNodes()
				
				tensor = frameConverter.convertFrameToTensor(frame)
				detection = onnxRuntimeHandler.runOnnxInference(tensor)
				worldPos = positionHandler.getWorldPosition(frame, detection)
				markerlessNode = HitResultNode().apply {
					hitTest = 
				}

				when (wheelNodes.size) {
					0 -> {
						val wheelNode = createNewWheelNode(MODEL_PATHS["wheel1"]!!, markerlessNode)
						wheelNodes.add(wheelNode)
					}
					1 -> {
						val dist = distanceModelToMarker(wheelNodes[0].position, Float3(markerBasedNode.worldPosition))
						if (dist < 0.5f) {
							wheelNodes[0].position = Float3(markerBasedNode.worldPosition)
						} else {
							val wheelNode = createNewWheelNode(MODEL_PATHS["wheel1"]!!, markerlessNode)
							wheelNodes.add(wheelNode)
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
							wheelNodes[nearest.second].position = Float3(markerBasedNode.worldPosition)
						} else {
							val wheelNode = createNewWheelNode(MODEL_PATHS["wheel1"]!!, markerBasedNode)
							wheelNodes.add(wheelNode)
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
							wheelNodes[nearest.second].position = Float3(markerBasedNode.worldPosition)
						} else if (furthest != null) {
							wheelNodes[furthest.second].position = Float3(markerBasedNode.worldPosition)
						}
					}
				}
				wheelNodes.forEach { wheelNode ->
					wheelNode.isVisible = true
				}

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

	//? Clear wheelNodes
	fun clearWheelNodes() {
        wheelNodes?.clearChildNodes()
        wheelNodes?.destroy()
    }

}
