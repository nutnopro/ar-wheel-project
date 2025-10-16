package com.arwheelapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import ai.onnxruntime.*
import java.io.File
import java.io.FileOutputStream
import com.google.ar.core.Frame
import java.nio.FloatBuffer
import android.util.Log

class OnnxRuntimeHandler(private val context: Context) {
	private const val TAG = "OnnxRuntimeHandler"

    companion object {
        const val MODEL = "ai/yolov11n.onnx"
        const val INPUT_SIZE = 320
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

    // Load .onnx from assets
    private fun createSession(): OrtSession {
        val modelFile = File(context.filesDir, MODEL)
        if (!modelFile.exists()) {
            context.assets.open(MODEL).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return env.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
    }

    // Run inference with ONNX Runtime
    fun runOnnxInference(tensor: FloatArray): List<Detection> {
        Log.d(TAG, "Preparing input tensor for ONNX, shape = [1, 3, $INPUT_SIZE, $INPUT_SIZE]")
        
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        val floatBuffer = java.nio.FloatBuffer.wrap(tensor)
        val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)
        Log.d(TAG, "Input tensor created successfully")

        val result = session.run(mapOf("images" to inputTensor))

        val output = result[0].value
        Log.d(TAG, "Raw output type: ${output!!::class}, output = $output")

        if (output is Array<*>) {
            // output = [[[...]]]  shape (1,5,2100)
            val arr = output as Array<Array<FloatArray>>
            val detections = mutableListOf<Detection>()
            val boxes = arr[0]   // shape (5,2100)
            Log.d(TAG, "Processing output array, boxes shape: [${boxes.size}, ${boxes[0].size}]")

            for (i in 0 until boxes[0].size) {
                val x = boxes[0][i]
                val y = boxes[1][i]
                val w = boxes[2][i]
                val h = boxes[3][i]
                val conf = boxes[4][i]

                if (conf > 0.1f) { // threshold
                    detections.add(Detection(x, y, w, h, conf))
                    Log.d(TAG, "Detection added: x=$x, y=$y, w=$w, h=$h, conf=$conf")
                } else {
                    Log.d(TAG, "Detection skipped (conf too low): x=$x, y=$y, w=$w, h=$h, conf=$conf")
                }
            }

            Log.d(TAG, "Total valid detections: ${detections.size}")
            return detections

        } else {
            Log.e(TAG, "Unexpected output type: ${output!!::class}")
            throw IllegalArgumentException("Unexpected output type: ${output!!::class}")
        }
    }

    // ปิด resource เมื่อ Activity/Service ถูกทำลาย
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