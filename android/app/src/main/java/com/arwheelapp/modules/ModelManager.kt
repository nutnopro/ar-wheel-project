package com.arwheelapp.modules

import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.collision.Box
import io.github.sceneview.math.Color
import dev.romainguy.kotlin.math.*
import kotlin.math.max

class ModelManager(private val arSceneView: ARSceneView) {
	private val modelLoader by lazy { ModelLoader(arSceneView.engine, arSceneView.context) }
	private val materialLoader by lazy { MaterialLoader(arSceneView.engine, arSceneView.context) }

	fun createModelNode(modelPath: String): Node {
		val rootNode = Node(arSceneView.engine).apply { isVisible = false }

        modelLoader.createModelInstance(
			assetFileLocation = modelPath
		)?.let { modelInstance ->
			val modelNode = ModelNode(modelInstance = modelInstance).apply { isVisible = true }

			val box = modelNode.boundingBox
			val sizeX = box.halfExtent[0] * 2.0f
			val sizeY = box.halfExtent[1] * 2.0f
			val sizeZ = box.halfExtent[2] * 2.0f
			val maxDimension = max(sizeX, sizeY)
			val radius = (maxDimension / 2.0f) * 0.98f
			val halfThickness = sizeZ / 2

			modelNode.position = Float3(0f, 0f, -halfThickness + 0.01f)

			val backplate = createBackplate(radius)
			backplate.rotation = Float3(90f, 0f, 0f)
			backplate.position = Float3(0f, 0f, halfThickness - 0.005f)

			rootNode.addChildNode(backplate)
			rootNode.addChildNode(modelNode)
		}

		return rootNode
	}

	fun createBackplate(radius: Float): CylinderNode {
		val blackMaterial = materialLoader.createColorInstance(
            color = Color(0.1f, 0.1f, 0.1f),
            metallic = 0.0f,
            roughness = 0.9f,
            reflectance = 0.1f
        )

		return CylinderNode(
			engine = arSceneView.engine,
            radius = radius,
            height = 0.003f,
			materialInstance = blackMaterial
		).apply { isVisible = true }
	}
}
