package com.arwheelapp.modules

import android.util.Log
import io.github.sceneview.node.ModelNode
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.ar.ARSceneView

class ModelManager {
	private val TAG = "ModelManager: "

	fun createModelNode(arSceneView: ARSceneView, modelPath: String): ModelNode {
		// Log.d(TAG, "Created ModelNode from $modelPath")
		val loader = ModelLoader(arSceneView.engine, arSceneView.context)
        val instance = loader.createModelInstance(assetFileLocation = modelPath)
		return ModelNode(modelInstance = instance).apply { isVisible = false }
	}
}
