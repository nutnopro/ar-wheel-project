// modules/ModelManager.kt
package com.arwheelapp.modules

import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import java.io.File
import java.lang.Float.max
import kotlinx.coroutines.*
import android.util.Log

class ModelManager(private val arSceneView: ARSceneView) {
    private val modelLoader: ModelLoader by lazy { ModelLoader(arSceneView.engine, arSceneView.context) }

    // ─────────────────────────────────────────────────────────────────────────
    // Create root node + async load model
    // Tap handler is NOT set here — ARRendering.wireTapHandler() handles it
    // ─────────────────────────────────────────────────────────────────────────
    fun createNewModel(modelPath: String, scope: CoroutineScope): Node {
        val rootNode = Node(arSceneView.engine).apply {
            isVisible = false       // hidden until detection hits >= 3
            isTouchable = true
        }
        scope.launch {
            val modelInstance = if (modelPath.startsWith("/")) {
                modelLoader.createModelInstance(File(modelPath))
            } else {
                modelLoader.createModelInstance(modelPath)
            }
            withContext(Dispatchers.Main) {
                modelInstance?.let { setupWheelSystem(rootNode, ModelNode(modelInstance = it)) }
            }
        }
        return rootNode
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wheel assembly
    //
    // Model coordinate system (from asset):
    //   Front face → +Y axis
    //   Up         → +Z axis
    //   Right      → +X axis
    //
    // ARRendering.lookRotationForward() builds a quaternion so that the root
    // node's +Y aligns with the plane normal (= faces camera).
    // Child nodes are offset/rotated relative to root, so we keep them as-is.
    // ─────────────────────────────────────────────────────────────────────────
    private fun setupWheelSystem(rootNode: Node, wheelNode: ModelNode) {
        val box = wheelNode.extents
        val diameter = maxOf(box.x, box.y)
        val thickness = box.z
        val halfThickness = thickness / 2f
        // Log.d("ModelManager", "box: ${box}, X: ${box.x}, Y: ${box.y}, Z: ${box.z}")
        // Log.d("ModelManager", "diameter: ${diameter}, thickness: ${thickness}, halfThickness: ${halfThickness}")
        // ? exmaple -> box: Float3(x=0.45720005, y=0.45719996, z=0.15214129), X: 0.45720005, Y: 0.45719996, Z: 0.15214129
        // ? exmaple -> diameter: 0.45720005, thickness: 0.15214129, halfThickness: 0.076070644
        // ? axis-> front of model facing toward +Y axis, UP -> +Z, Right -> +X
        // Shift wheel so its face sits at the root origin (facing +Y)
        wheelNode.position = Float3(0f, -halfThickness, 0f)
        // Model is authored front-face along +Z → rotate -90° around X to make front face +Y
        wheelNode.rotation = Float3(-90f, 0f, 0f)

        // Backplate behind the wheel
        loadBackplate(diameter)?.let { backplate ->
            backplate.position = Float3(0f, 0f, -halfThickness)
            backplate.rotation = Float3(0f, 0f, 0f)
            wheelNode.addChildNode(backplate)
        }
        rootNode.addChildNode(wheelNode)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Backplate — null-safe, blocking only the coroutine context it is called from
    // (setupWheelSystem is always called inside withContext(Dispatchers.Main))
    // ─────────────────────────────────────────────────────────────────────────
    private fun loadBackplate(diameter: Float): ModelNode? {
        val instance = modelLoader.createModelInstance("models/backplate.glb") ?: return null
        return ModelNode(modelInstance = instance).apply {
            scale = Float3(diameter, diameter, 1f)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hot-swap model on existing root node
    // ─────────────────────────────────────────────────────────────────────────
    fun changeModel(rootNode: Node, modelPath: String, scope: CoroutineScope) {
        scope.launch {
            val modelInstance = if (modelPath.startsWith("/")) {
                modelLoader.createModelInstance(File(modelPath))
            } else {
                modelLoader.createModelInstance(modelPath)
            }
            withContext(Dispatchers.Main) {
                rootNode.childNodes.forEach { it.destroy() }
                rootNode.clearChildNodes()
                modelInstance?.let { setupWheelSystem(rootNode, ModelNode(modelInstance = it)) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scale the root node uniformly (18-inch default = scale 1.0)
    // ─────────────────────────────────────────────────────────────────────────
    fun changeModelSize(rootNode: Node, scaleFactor: Float) {
        rootNode.scale = Float3(scaleFactor, scaleFactor, scaleFactor)
    }
}