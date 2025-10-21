package com.arapp.modules

import com.google.ar.core.Frame
import android.graphics.Color
import android.view.View
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.*
import io.github.sceneview.node.Node
import io.github.sceneview.node.PlaneNode
import dev.romainguy.kotlin.math.Float3
import kotlin.math.sqrt
import android.util.Log
import io.github.sceneview.node.ModelNode

class ARMarkerless {
	private const val TAG = "ARMarkerless"

    data class Pose3D(
        val position: Float3,
        val rotation: Float3,
        val scale: Float3 = Float3(1f, 1f, 1f)
    )

    private val modelNodes = mutableListOf<Node>()

    // Update / reuse model boxes
    fun render(sceneView: ARSceneView, pose3DList: List<Pose3D>, minDistance: Float = 0.25f) {
        Log.d(TAG, "Start rendering ${pose3DList.size} 3D poses")

        // ซ่อน node เก่า
        modelNodes.forEach { 
            it.isVisible = false 
        }
        Log.d(TAG, "All existing modelNodes hidden, count=${modelNodes.size}")

        for ((index, pose) in pose3DList.withIndex()) {
            Log.d(TAG, "Processing pose #$index at position=${pose.position}, rotation=${pose.rotation}")

            // หา node ที่ใกล้ที่สุด
            val closestNode = modelNodes.minByOrNull { distance(it.position, pose.position) }
            val dist = closestNode?.let { distance(it.position, pose.position) } ?: Float.MAX_VALUE
            Log.d(TAG, "Closest node distance=$dist")

            if (closestNode != null && dist < minDistance) {
                // Reuse node
                Log.d(TAG, "Reusing existing node for pose #$index")
                closestNode.position = pose.position
                closestNode.rotation = pose.rotation
                closestNode.scale = pose.scale
                closestNode.isVisible = true
            } else {
                // สร้าง PlaneNode ใหม่
                Log.d(TAG, "Creating new PlaneNode for pose #$index")
                val newNode = PlaneNode(
                    engine = sceneView.engine,
                    size = Float3(0.45f, 0.45f, 0f),
                    materialInstance = sceneView.materialLoader.createColorInstance(
                        Color.RED //argb((0.3f * 255).toInt(), 255, 0, 0)
                    )
                ).apply {
                    position = pose.position
                    rotation = pose.rotation
                    scale = pose.scale
                    isVisible = true
                }

                sceneView.addChildNode(newNode)
                modelNodes.add(newNode)
                Log.d(TAG, "New node added, total modelNodes=${modelNodes.size}")
            }
        }

        Log.d(TAG, "Finished rendering 3D model boxes")
    }

    private fun distance(p1: Float3, p2: Float3): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        val dz = p1.z - p2.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    // Clear nodes
    fun clearNodes(sceneView: ARSceneView) {
        modelNodes.forEach { sceneView.removeChildNode(it) }
        modelNodes.clear()
    }
}