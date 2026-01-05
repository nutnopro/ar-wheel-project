package com.arwheelapp.processor

import android.content.Context
import android.graphics.RectF
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.PriorityQueue
import kotlinx.coroutines.*
import ai.onnxruntime.*

class OnnxRuntimeHandler(private val context: Context) {
    private val TAG = "OnnxRuntimeHandler: "
    private val MODEL_PATH = "ai/yolov11n.onnx"
    private val INPUT_SIZE = 320
    private val CONFIDENCE_THRESHOLD = 0.5f
    private val IOU_THRESHOLD = 0.4f

    data class Detection(
        val boundingBox: RectF,
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

        return env.createSession(modelFile.absolutePath, options)
    }

    // *Run inference with ONNX Runtime with Coroutine
    fun runOnnxInferenceAsync(tensor: FloatArray, callback: (List<Detection>) -> Unit) {
        inferenceScope.launch {
            val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            var finalDetections: List<Detection> = emptyList()

            val inputTensor = try {
                OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor), shape)
            } catch (e: Exception) {
                Log.e(TAG, "runOnnxInferenceAsync: Failed to create input tensor", e)
                withContext(Dispatchers.Main) { callback(emptyList()) }
                return@launch
            }

            try {
                session.run(mapOf("images" to inputTensor)).use { result ->
                    val output = result[0].value
                    if (output is Array<*>) {
                        val rawDetections = parseOutput(output)
                        finalDetections = nms(rawDetections)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "runOnnxInferenceAsync: Inference failed", e)
            } finally {
                inputTensor.close()
            }

            withContext(Dispatchers.Main) { callback(finalDetections) }
        }
    }

    // *Parse output array to Detection list
    private fun parseOutput(output: Array<*>): List<Detection> {
        val detections = mutableListOf<Detection>()
        if (output.isEmpty()) return detections

        // *YOLO Shape: [1, 5, 8400] (Batch, [cx,cy,w,h,conf], Anchors)
        val arr = output as Array<Array<FloatArray>>
        val batch0 = arr[0]

        val numAnchors = batch0[0].size

        for (i in 0 until numAnchors) {
            val confidence = batch0[4][i]

            if (confidence > CONFIDENCE_THRESHOLD) {
                val cx = batch0[0][i]
                val cy = batch0[1][i]
                val w = batch0[2][i]
                val h = batch0[3][i]

                //* Convert BBox to RectF
                val left = (cx - w / 2) / INPUT_SIZE
                val top = (cy - h / 2) / INPUT_SIZE
                val right = (cx + w / 2) / INPUT_SIZE
                val bottom = (cy + h / 2) / INPUT_SIZE

                val rect = RectF(
                    left.coerceIn(0f, 1f),
                    top.coerceIn(0f, 1f),
                    right.coerceIn(0f, 1f),
                    bottom.coerceIn(0f, 1f)
                )

                detections.add(Detection(boundingBox = rect, confidence = confidence))
            }
        }

        return detections
    }

    private fun nms(detections: List<Detection>): List<Detection> {
        val finalDetections = mutableListOf<Detection>()
        
        val pq = PriorityQueue<Detection> { a, b -> b.confidence.compareTo(a.confidence) }
        pq.addAll(detections)

        while (pq.isNotEmpty()) {
            val best = pq.poll()
            finalDetections.add(best)

            val iterator = pq.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                if (calculateIoU(best.boundingBox, other.boundingBox) > IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }

        return finalDetections
    }

    private fun calculateIoU(boxA: RectF, boxB: RectF): Float {
        val intersectLeft = maxOf(boxA.left, boxB.left)
        val intersectTop = maxOf(boxA.top, boxB.top)
        val intersectRight = minOf(boxA.right, boxB.right)
        val intersectBottom = minOf(boxA.bottom, boxB.bottom)

        if (intersectRight < intersectLeft || intersectBottom < intersectTop) return 0f

        val intersectionArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val boxAArea = (boxA.right - boxA.left) * (boxA.bottom - boxA.top)
        val boxBArea = (boxB.right - boxB.left) * (boxB.bottom - boxB.top)

        return intersectionArea / (boxAArea + boxBArea - intersectionArea)
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
