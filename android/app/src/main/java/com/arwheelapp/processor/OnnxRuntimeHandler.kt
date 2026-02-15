package com.arwheelapp.processor

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.RectF
import android.util.Log
import com.arwheelapp.utils.Detection
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnnxRuntimeHandler(private val context: Context) {
    companion object {
        private const val TAG = "OnnxRuntimeHandler"
        private const val MODEL_PATH = "yolov11n.onnx"
        private const val INPUT_SIZE = 320
        private const val CONFIDENCE_THRESHOLD = 0.4f
    }

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession by lazy { createSession() }
    private val inferenceScope = CoroutineScope(Dispatchers.Default)

    // Load model .onnx from assets
    private fun createSession(): OrtSession {
        val modelFile = File(context.filesDir, MODEL_PATH)

        val options = OrtSession.SessionOptions().apply {
            try {
                addConfigEntry("session.use_xnnpack", "1")
            } catch (e: Exception) {
                Log.e(TAG, "XNNPACK not supported on this device", e)
            }
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }

        if (!modelFile.exists()) {
            context.assets.open(MODEL_PATH).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        return env.createSession(modelFile.absolutePath, options)
    }

    // Run inference with ONNX Runtime with Coroutine
    fun runOnnxInferenceAsync(tensor: FloatArray, callback: (List<Detection>) -> Unit) {
        inferenceScope.launch {
            val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())

            val inputTensor = try {
                OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor), shape)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create input tensor", e)
                withContext(Dispatchers.Main) { callback(emptyList()) }
                return@launch
            }

            try {
                session.run(mapOf("images" to inputTensor)).use { result ->
                    val output = result[0].value
                    val finalDetections = if (output is Array<*>) parseOutput(output) else emptyList()
                    withContext(Dispatchers.Main) { callback(finalDetections) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Inference/Run Error", e)
            } finally {
                inputTensor.close()
            }
        }
    }

    // Parse output for [1, 300, 6] format
    private fun parseOutput(output: Array<*>): List<Detection> {
        val detections = mutableListOf<Detection>()

        try {
            val batchData = output as Array<Array<FloatArray>> // shape -> [1][300][6]
            val boxes = batchData[0] // extract first batch -> [300][6]

            // boxInfo have 6 values: [x1, y1, x2, y2, score, class_id]
            for (boxInfo in boxes) {
                val score = boxInfo[4]

                if (score > CONFIDENCE_THRESHOLD) {
                    // Normalize coordinates (0.0 - 1.0) to fit the overlay view
                    val left = (boxInfo[0] / INPUT_SIZE).coerceIn(0f, 1f)
                    val top = (boxInfo[1] / INPUT_SIZE).coerceIn(0f, 1f)
                    val right = (boxInfo[2] / INPUT_SIZE).coerceIn(0f, 1f)
                    val bottom = (boxInfo[3] / INPUT_SIZE).coerceIn(0f, 1f)

                    val rect = RectF(left, top, right, bottom)
                    if (rect.width() > 0 && rect.height() > 0) {
                        detections.add(Detection(boundingBox = rect, confidence = score))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing output: ${e.message}")
        }

        return detections
    }

    // close session and env
    fun close() {
        try {
            inferenceScope.cancel()
            session.close()
            env.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing resources", e)
        }
    }
}