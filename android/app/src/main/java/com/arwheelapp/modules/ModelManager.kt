package com.arwheelapp.modules

import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.collision.Box
import io.github.sceneview.math.Color
import dev.romainguy.kotlin.math.*
import kotlin.math.max

class ModelManager(private val arSceneView: ARSceneView) {
	private val modelLoader by lazy { ModelLoader(arSceneView.engine, arSceneView.context) }
	private val materialLoader by lazy { MaterialLoader(arSceneView.engine, arSceneView.context) }

	fun createNewModel(modelPath: String): Node {
		val rootNode = Node(arSceneView.engine).apply { isVisible = false }

        modelLoader.createModelInstance(
			assetFileLocation = modelPath
		)?.let { modelInstance ->
			val model = ModelNode(modelInstance = modelInstance).apply { isVisible = true }

			val box = model.boundingBox
			val sizeX = box.halfExtent[0] * 2.0f
			val sizeY = box.halfExtent[1] * 2.0f
			val sizeZ = box.halfExtent[2] * 2.0f
			val maxDimension = max(sizeX, sizeY)
			val radius = (maxDimension / 2.0f)
			val halfDimension = maxDimension / 2.0f
			val halfZ = sizeZ / 2.0f

			model.position = Float3(0f, 0, -halfZ + 0.1f)

			addTubeAndBackplate(rootNode, maxDimension, radius, sizeZ, halfZ)
			rootNode.addChildNode(model)
		}

		return rootNode
	}

	fun createBackplate(radius: Float): CylinderNode {
		val blackMaterial = materialLoader.createColorInstance(
            color = Color(0f, 0f, 0f),
            metallic = 0.0f,
            roughness = 1.0f,
            reflectance = 0.0f
        )

		return CylinderNode(
			engine = arSceneView.engine,
            radius = radius,
            height = 0.01f,
			materialInstance = blackMaterial
		).apply { isVisible = true }
	}

	fun createTube(maxDimension: Float, sizeZ: Float): ModelNode {
		val tubeInstance = modelLoader.createModelInstance(assetFileLocation = "models/tube.glb")
		return ModelNode(modelInstance = tubeInstance).apply {
			isVisible = true
			scale = Float3(maxDimension, maxDimension, sizeZ)
		}
	}

	fun addTubeAndBackplate(rootNode: Node, maxDimension: Float, radius: Float, sizeZ: Float, halfZ: Float) {
		val backplate = createBackplate(radius)
		backplate.position = Float3(0f, 0f, sizeZ)

		val tube = createTube(maxDimension, sizeZ)
		tube.position = Float3(0f, 0f, -halfZ + 0.1f)

		rootNode.addChildNode(tube)
		rootNode.addChildNode(backplate)
	}

	fun changeModel(rootNode: Node, modelPath: String) {
		rootNode.childNodes.toList().forEach { child ->
            rootNode.removeChildNode(child)
            child.destroy()
        }

        modelLoader.createModelInstance(
			assetFileLocation = modelPath
		)?.let { modelInstance ->
			val model = ModelNode(modelInstance = modelInstance).apply { isVisible = true }

			val box = model.boundingBox
			val sizeX = box.halfExtent[0] * 2.0f
			val sizeY = box.halfExtent[1] * 2.0f
			val sizeZ = box.halfExtent[2] * 2.0f
			val maxDimension = max(sizeX, sizeY)
			val radius = (maxDimension / 2.0f)
			val halfDimension = maxDimension / 2.0f
			val halfZ = sizeZ / 2.0f

			model.position = Float3(0f, 0, -halfZ + 0.1f)

			addTubeAndBackplate(rootNode, maxDimension, radius, sizeZ, halfZ)
			rootNode.addChildNode(model)
		}
	}

	fun changeModelSize(rootNode: Node, scaleFactor: Float) {
		rootNode.scale = Float3(scaleFactor, scaleFactor, scaleFactor)
	}
}