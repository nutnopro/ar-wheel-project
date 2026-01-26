package com.arwheelapp.processor

import android.content.Context
import android.graphics.RectF
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlinx.coroutines.*
import ai.onnxruntime.*
import com.arwheelapp.utils.Detection

class OnnxRuntimeHandler(private val context: Context) {
    private val TAG = "OnnxRuntimeHandler: "
    private val MODEL_PATH = "yolov11n.onnx"
    private val INPUT_SIZE = 320
    private val CONFIDENCE_THRESHOLD = 0.4f

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession by lazy { createSession() }
    private val inferenceScope = CoroutineScope(Dispatchers.Default)

    // *Load model .onnx from assets
    private fun createSession(): OrtSession {
        val modelFile = File(context.filesDir, MODEL_PATH)
        val options = OrtSession.SessionOptions().apply {
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

    // *Run inference with ONNX Runtime with Coroutine
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

                    val finalDetections = if (output is Array<*>) {
                        parseOutput(output)
                    } else {
                        emptyList()
                    }

                    withContext(Dispatchers.Main) { callback(finalDetections) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Inference/Run Error", e)
            } finally {
                inputTensor.close()
            }
        }
    }

    // *Parse output for [1, 300, 6] format
    private fun parseOutput(output: Array<*>): List<Detection> {
        val detections = mutableListOf<Detection>()
        // Output Shape: [1, 300, 6]
        // Cast เป็น Array 3 มิติ (Java output ปกติจะเป็น float[][][])
        // แต่ถ้า Library ของคุณ return เป็น Object Array ต้อง cast ให้ถูก
        // โค้ดนี้รองรับ output มาตรฐานจาก onnxruntime-android
        
        try {
            // หมายเหตุ: output ใน Kotlin/Java มักจะเป็น array ของ primitive type หรือ Object array
            // ตรงนี้ต้องระวังเรื่อง Type Casting ขึ้นอยู่กับ Version ของ Library
            // สมมติว่าเป็น Array<Array<FloatArray>> ตามโค้ดเดิมของคุณ (หรือ float[][][])
            
            val batchData = output as Array<Array<FloatArray>> // [1][300][6]
            val boxes = batchData[0] // ดึง Batch แรกออกมา -> [300][6]

            // if (boxes.isNotEmpty()) {
            //     val f = boxes[0]
            //     Log.v(TAG, "📦 Top Box Raw: [x1=${f[0]}, y1=${f[1]}, x2=${f[2]}, y2=${f[3]}, conf=${f[4]}, class=${f[5]}]")
            // }

            // วนลูปตามจำนวน Box (300 ตัว)
            for ((index, boxInfo) in boxes.withIndex()) {
                // boxInfo มีขนาด 6 ตัว: [x1, y1, x2, y2, score, class_id]
                val score = boxInfo[4]

                if (score > CONFIDENCE_THRESHOLD) {
                    val x1 = boxInfo[0]
                    val y1 = boxInfo[1]
                    val x2 = boxInfo[2]
                    val y2 = boxInfo[3]

                    // Normalize coordinates (0.0 - 1.0) สำหรับวาดบนหน้าจอ
                    val left = (x1 / INPUT_SIZE).coerceIn(0f, 1f)
                    val top = (y1 / INPUT_SIZE).coerceIn(0f, 1f)
                    val right = (x2 / INPUT_SIZE).coerceIn(0f, 1f)
                    val bottom = (y2 / INPUT_SIZE).coerceIn(0f, 1f)

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

    // *close session and env
    fun close() {
        try {
            inferenceScope.cancel()
            session.close()
            env.close()
            Log.d(TAG, "Closing ONNX resources")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing resources", e)
        }
    }
}
