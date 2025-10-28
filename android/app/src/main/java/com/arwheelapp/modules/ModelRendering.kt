package com.arwheelapp.modules

import android.content.Context
import android.util.Log
import io.github.sceneview.ar.ARSceneView
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ModelNode

class ModelRendering {
	private const val TAG = "ModelRendering"

	fun createModelNode(modelPath: String): ModelNode {
		return ModelNode(
			modelInstance = ModelLoader.createModelInstance(assetFileLocation = modelPath)
		).apply {
			isVisible = false
		}
		Log.d(TAG, "Created ModelNode from $modelPath")
	}
}
