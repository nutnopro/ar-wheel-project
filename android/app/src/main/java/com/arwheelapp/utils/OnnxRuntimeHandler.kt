package com.arwheelapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import com.google.ar.core.Frame
import kotlinx.coroutines.*
import ai.onnxruntime.*

class OnnxRuntimeHandler(private val context: Context) {
    companion object {
        private const val TAG = "OnnxRuntimeHandler"
        private const val MODEL_PATH = "ai/yolov11n.onnx"
        private const val INPUT_SIZE = 320
        private const val CONFIDENCE_THRESHOLD = 0.5f
    }

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
        val modelFile = File(context.filesDir, "yolov11n.onnx")
        if (!modelFile.exists()) {
            context.assets.open(MODEL_PATH).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }

        Log.d(TAG, "YOLOv11n model loaded: ${modelFile.absolutePath}")
        return env.createSession(modelFile.absolutePath, options)
    }

    // *Run inference with ONNX Runtime with Coroutine
    fun runOnnxInferenceAsync(tensor: FloatArray, callback: (List<Detection>) -> Unit) {
        inferenceScope.launch {
            val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor), shape)
            val detections = try {
                session.run(mapOf("images" to inputTensor)).use { result ->
                    val output = result[0].value
                    if (output is Array<*>) parseOutput(output) else emptyList()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Inference failed", e)
                emptyList()
            } finally {
                inputTensor.close()
            }
            withContext(Dispatchers.Main) { callback(detections) }
        }
    }

    // *Parse output array to Detection list
    private fun parseOutput(output: Array<*>): List<Detection> {
        val detections = mutableListOf<Detection>()
        if (output.isEmpty() || output[0] !is Array<*>) return detections

        val arr = output as Array<Array<FloatArray>>
        val boxes = arr[0]
        for (i in boxes[0].indices) {
            val conf = boxes[4][i]
            if (conf > CONFIDENCE_THRESHOLD) {
                detections.add(Detection(boxes[0][i], boxes[1][i], boxes[2][i], boxes[3][i], conf))
            }
        }

        Log.d(TAG, "Detections: ${detections.size}")
        return detections
    }

    // *close session and env
    fun close() {
        try {
            session.close()
            env.close()
            Log.d(TAG, "ONNX resources closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing resources", e)
        }
    }
}