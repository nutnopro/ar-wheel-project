package com.arapp.modules

import android.content.Context
import android.util.Log
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.node.ModelNode
import dev.romainguy.kotlin.math.Float3
import com.google.ar.core.*
import java.io.File

class ARMarkerBased(private val context: Context) {
	private const val TAG = "ARMarkerBased"

    companion object {
        const val MARKER_DB = "markers/ar_marker_database.imgdb"
        const val MODEL_PATH = "models/wheel1.obj"
    }

    // assign session from ARSceneView
    private lateinit var session: Session
    private var wheelNode: ModelNode? = null
    var isModelLoaded = false
		private set
	
	fun render() {
		setupDatabase()

		if (!isModelLoaded) {
			Log.d(TAG, "Model not loaded yet, cannot render")
			loadModel()
		} else {
			Log.d(TAG, "Model already loaded, ready to render")
			updateModelPosition()
		}
	}

    private fun setupDatabase() {
        try {
            Log.d(TAG, "Setting up marker database...")
            val dbFile = File(context.filesDir, MARKER_DB)
            if (!dbFile.exists()) {
                dbFile.parentFile?.mkdirs()
                context.assets.open(MARKER_DB).use { input ->
                    dbFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Marker database copied to filesDir")
            }

            val imgDb = AugmentedImageDatabase.deserialize(session, dbFile.inputStream())
            val config = session.config.apply {
                augmentedImageDatabase = imgDb
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            }
            session.configure(config)
            Log.d(TAG, "AR Session configured with marker database")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup marker database", e)
        }
    }

    private fun loadModel(sceneView: ARSceneView) {
        if (isModelLoaded) {
            Log.d(TAG, "Model already loaded")
            return
        }
        try {
            Log.d(TAG, "Loading model from $MODEL_PATH")
            wheelNode = ModelNode(
                modelInstance = sceneView.modelLoader.createModelInstance(assetFileLocation = MODEL_PATH)
            ).apply {
                scale = Float3(0.5f, 0.5f, 0.5f)
                isVisible = false
            }
            sceneView.addChildNode(wheelNode!!)
            isModelLoaded = true
            Log.d(TAG, "ModelNode added to scene")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
        }
    }

    private fun updateModelPosition(augmentedImage: AugmentedImage) {
        if (!isModelLoaded) return
        val pose = augmentedImage.centerPose
        val position = Float3(pose.tx(), pose.ty(), pose.tz())
        wheelNode?.apply {
            this.position = position
            this.rotation = Float3(0f, 0f, 0f)
            this.isVisible = augmentedImage.trackingState == TrackingState.TRACKING
        }
        Log.d(TAG, "Wheel model position updated: $position, tracking=${augmentedImage.trackingState}")
    }

    fun cleanup() {
        wheelNode?.destroy()
        wheelNode = null
        isModelLoaded = false
        if (::session.isInitialized) session.close()
        Log.d(TAG, "Resources cleaned up")
    }
}
