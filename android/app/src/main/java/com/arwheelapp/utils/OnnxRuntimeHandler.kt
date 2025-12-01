package com.arwheelapp.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlinx.coroutines.*
import ai.onnxruntime.*

class OnnxRuntimeHandler(private val context: Context) {
    private val TAG = "OnnxRuntimeHandler: "
    private val MODEL_PATH = "ai/yolov11n.onnx"
    private val INPUT_SIZE = 320
    private val CONFIDENCE_THRESHOLD = 0.5f

    data class Detection(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val confidence: Float
    )

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession by lazy { createSession() }
    private val inferenceScope = CoroutineScope(Dispatchers.Default)

    // *Load model .onnx from assets
    private fun createSession(): OrtSession {
        // Log.d(TAG, "createSession: Initializing ONNX session...")
        val modelFile = File(context.filesDir, "yolov11n.onnx")
        if (!modelFile.exists()) {
            // Log.d(TAG, "createSession: Model file not found in filesDir, copying from assets...")
            context.assets.open(MODEL_PATH).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        // else {
        //     Log.d(TAG, "createSession: Model file found in filesDir.")
        // }

        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }

        // Log.d(TAG, "createSession: Creating session with model: ${modelFile.absolutePath}")
        return env.createSession(modelFile.absolutePath, options)
    }

    // *Run inference with ONNX Runtime with Coroutine
    fun runOnnxInferenceAsync(tensor: FloatArray, callback: (List<Detection>) -> Unit) {
        inferenceScope.launch {
            // Log.d(TAG, "runOnnxInferenceAsync: Starting inference...")
            val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())

            var detections: List<Detection> = emptyList()

            val inputTensor = try {
                OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor), shape)
            } catch (e: Exception) {
                Log.e(TAG, "runOnnxInferenceAsync: Failed to create input tensor", e)
                withContext(Dispatchers.Main) { callback(emptyList()) }
                return@launch
            }

            try {
                session.run(mapOf("images" to inputTensor)).use { result ->
                    // Log.d(TAG, "runOnnxInferenceAsync: Inference run complete. Processing output...")
                    val output = result[0].value
                    if (output is Array<*>) {
                        detections = parseOutput(output)
                    } else {
                        Log.w(TAG, "runOnnxInferenceAsync: Unexpected output type")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "runOnnxInferenceAsync: Inference failed", e)
            } finally {
                inputTensor.close()
            }
            // Log.d(TAG, "runOnnxInferenceAsync: Callback with ${detections.size} detections")
            withContext(Dispatchers.Main) { callback(detections) }
        }
    }

    // *Parse output array to Detection list
    private fun parseOutput(output: Array<*>): List<Detection> {
        val detections = mutableListOf<Detection>()
        if (output.isEmpty()) return detections

        // *YOLOv8/v11 Output Shape: [Batch, Channels, Anchors] -> [1, 5, 8400]
        val arr = output as Array<Array<FloatArray>>
        val batch0 = arr[0]

        val numAnchors = batch0[0].size
        // Log.d(TAG, "parseOutput: Parsing $numAnchors anchors...")

        for (i in 0 until numAnchors) {
            val conf = batch0[4][i]
            if (conf > CONFIDENCE_THRESHOLD) {
                // Normalize by INPUT_SIZE (IMPORTANT for OverlayView)
                val x = batch0[0][i] / INPUT_SIZE
                val y = batch0[1][i] / INPUT_SIZE
                val w = batch0[2][i] / INPUT_SIZE
                val h = batch0[3][i] / INPUT_SIZE

                detections.add(Detection(x, y, w, h, conf))
            }
        }
        return detections
    }

    // *close session and env
    fun close() {
        try {
            Log.d(TAG, "close: Closing ONNX resources")
            session.close()
            env.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing resources", e)
        }
    }
}
