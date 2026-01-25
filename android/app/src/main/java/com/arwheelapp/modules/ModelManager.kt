package com.arwheelapp.modules

import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.collision.Box
import io.github.sceneview.math.Color
import dev.romainguy.kotlin.math.Float3
import kotlin.math.max

class ModelManager(private val arSceneView: ARSceneView) {
	private val modelLoader by lazy { ModelLoader(arSceneView.engine, arSceneView.context) }
	private val materialLoader by lazy { MaterialLoader(arSceneView.engine, arSceneView.context) }

	fun createModelNode(modelPath: String): Node {
		val rootNode = Node(arSceneView.engine).apply { isVisible = false }

        val instance = modelLoader.createModelInstance(assetFileLocation = modelPath)
		val model = ModelNode(
			modelInstance = instance
		).apply {
			isVisible = true
		}

		val boundingBox = model.collisionShape as Box
		val size = boundingBox.size

		val maxDimension = max(size.x, size.y)
        val radius = (maxDimension / 2.0f) * 0.98f
		val halfThickness = size.z / 2

		model.position = Float3(0f, 0f, -halfThickness + 0.01f)

        val backplate = createBackplate(radius)
        backplate.rotation = Float3(90f, 0f, 0f)
        backplate.position = Float3(0f, 0f, halfThickness - 0.005f)

        rootNode.addChildNode(model)
        rootNode.addChildNode(backplate)

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
            center = Float3(0f, 0f, 0f),
			materialInstance = blackMaterial
		).apply { isVisible = true }
	}
}
