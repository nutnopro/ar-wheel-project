package com.arwheelapp.modules

import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ModelNode

class ModelManager(private val arSceneView: ARSceneView) {
	private val loader by lazy { ModelLoader(arSceneView.engine, arSceneView.context) }

	fun createModelNode(modelPath: String): ModelNode {
        val instance = loader.createModelInstance(assetFileLocation = modelPath)
		return ModelNode(modelInstance = instance).apply { isVisible = false }
	}
}
