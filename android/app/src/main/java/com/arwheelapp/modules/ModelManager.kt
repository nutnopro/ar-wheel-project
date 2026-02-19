package com.arwheelapp.modules

import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import java.lang.Float.max
import kotlinx.coroutines.*

class ModelManager(private val arSceneView: ARSceneView) {
    private val modelLoader by lazy { ModelLoader(arSceneView.engine, arSceneView.context) }

    fun createNewModel(modelPath: String, scope: CoroutineScope): Node {
        val rootNode = Node(arSceneView.engine).apply { isVisible = false }

        scope.launch {
            val modelInstance = modelLoader.createModelInstance(modelPath)

            withContext(Dispatchers.Main) {
                modelInstance?.let {
                    setupWheelSystem(rootNode, ModelNode(modelInstance = it))
                    rootNode.isVisible = true
                }
            }
        }

        return rootNode
    }

    private fun setupWheelSystem(rootNode: Node, wheelNode: ModelNode) {
        val box = wheelNode.boundingBox
        val diameter = max(box.halfExtent[0] * 2.0f, box.halfExtent[1] * 2.0f)
        val halfThickness =  box.halfExtent[2]
        val thickness = halfThickness * 2.0f
        // ? exmaple -> X: 0.45720005, Y: 0.45719996, Z: 0.15214129
        // ? exmaple -> diameter: 0.45720005, thickness: 0.15214129, halfThickness: 0.076070644

        wheelNode.position = Float3(0f, -halfThickness, 0f)
        wheelNode.rotation = Float3(-90f, 0f, 0f)

        createComponent("models/backplate.glb", diameter, 1.0f)?.let { backplate ->
            backplate.position = Float3(0f, -thickness, 0f)
            backplate.rotation = Float3(-90f, 0f, 0f)
            rootNode.addChildNode(backplate)
        }

        rootNode.addChildNode(wheelNode)
    }

    private fun createComponent(assetPath: String, diameter: Float, thickness: Float): ModelNode {
        val instance = modelLoader.createModelInstance(assetPath)
        return ModelNode(modelInstance = instance).apply {
            scale = Float3(diameter, diameter, thickness)
        }
    }

    fun changeModel(rootNode: Node, modelPath: String, scope: CoroutineScope) {
        scope.launch {
            val modelInstance = modelLoader.createModelInstance(modelPath)
            withContext(Dispatchers.Main) {
                rootNode.childNodes.forEach { it.destroy() }
                rootNode.clearChildNodes()

                modelInstance?.let {
                    setupWheelSystem(rootNode, ModelNode(modelInstance = it))
                }
            }
        }
    }

    fun changeModelSize(rootNode: Node, scaleFactor: Float) {
        rootNode.scale = Float3(scaleFactor, scaleFactor, scaleFactor)
    }
}