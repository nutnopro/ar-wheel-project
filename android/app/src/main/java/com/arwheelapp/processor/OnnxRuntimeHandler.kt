package com.arwheelapp.processor

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.RectF
import android.graphics.Matrix
import android.util.Log
import com.arwheelapp.utils.Detection
import com.arwheelapp.utils.FrameYData
import com.arwheelapp.utils.ProcessedDetection
import com.arwheelapp.utils.RefinedResult
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point as OpenCVPoint
import org.opencv.core.Rect
import org.opencv.core.RotatedRect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class OnnxRuntimeHandler(private val context: Context) {
    companion object {
        private const val TAG = "OnnxRuntimeHandler"
        private const val MODEL_PATH = "yolov11n.onnx"
        private const val INPUT_SIZE = 320
        private const val CONFIDENCE_THRESHOLD = 0.4f
        private const val BBOX_PADDING = 0.15f
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
    fun runOnnxInferenceAsync(
        tensor: FloatArray,
        deviceRotation: Int,
        frameData: FrameYData?,
        viewW: Float,
        viewH: Float,
        callback: (List<ProcessedDetection>) -> Unit
    ) {
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
                    val detections = if (output is Array<*>) parseOutput(output, deviceRotation) else emptyList()
                    val processedResults = mutableListOf<ProcessedDetection>()

                    if (detections.isNotEmpty() && frameData != null) {
                        val fullMat = createMatFromYData(frameData, deviceRotation)
                        for (det in detections) {
                            val cvRes = refineCenterWithOpenCV(fullMat, det.boundingBox, viewW, viewH)
                            processedResults.add(ProcessedDetection(det, cvRes))
                        }
                        fullMat?.release()
                    } else {
                        for (det in detections) {
                            val defaultCx = det.boundingBox.centerX() * viewW
                            val defaultCy = det.boundingBox.centerY() * viewH
                            processedResults.add(ProcessedDetection(det, RefinedResult(defaultCx, defaultCy, 0f, 0f, 0f, 0f, false)))
                        }
                    }

                    withContext(Dispatchers.Main) { callback(processedResults) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Inference/Run Error", e)
            } finally {
                inputTensor.close()
            }
        }
    }

    private fun createMatFromYData(frameData: FrameYData, deviceRotation: Int): Mat? {
        try {
            val mat = Mat(frameData.height, frameData.width, CvType.CV_8UC1)
            mat.put(0, 0, frameData.bytes)
            val finalRotation = (90 - deviceRotation + 360) % 360
            val rotateCode = when (finalRotation) {
                90 -> Core.ROTATE_90_CLOCKWISE
                180 -> Core.ROTATE_180
                270 -> Core.ROTATE_90_COUNTERCLOCKWISE
                else -> -1
            }
            if (rotateCode != -1) {
                val rotatedMat = Mat()
                Core.rotate(mat, rotatedMat, rotateCode)
                mat.release()
                return rotatedMat
            }
            return mat
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Mat", e)
            return null
        }
    }

    private fun refineCenterWithOpenCV(fullMat: Mat?, bbox: RectF, viewW: Float, viewH: Float): RefinedResult {
        val defaultCx = bbox.centerX() * viewW
        val defaultCy = bbox.centerY() * viewH
        if (fullMat == null || fullMat.empty()) return RefinedResult(defaultCx, defaultCy, 0f, 0f, 0f, 0f, false)

        var croppedMat: Mat? = null
        var edges: Mat? = null

        try {
            val matW = fullMat.cols()
            val matH = fullMat.rows()
            val padX = bbox.width() * BBOX_PADDING
            val padY = bbox.height() * BBOX_PADDING
            val left = max(0f, (bbox.left - padX) * matW).toInt()
            val top = max(0f, (bbox.top - padY) * matH).toInt()
            val right = min(matW.toFloat(), (bbox.right + padX) * matW).toInt()
            val bottom = min(matH.toFloat(), (bbox.bottom + padY) * matH).toInt()
            val cropW = right - left
            val cropH = bottom - top
            if (cropW <= 20 || cropH <= 20) return RefinedResult(defaultCx, defaultCy, 0f, 0f, 0f, 0f, false)

            croppedMat = Mat(fullMat, Rect(left, top, cropW, cropH))
            val centerInCrop = OpenCVPoint(cropW / 2.0, cropH / 2.0)
            val maskSize = Size(cropW * 0.25, cropH * 0.25)
            Imgproc.ellipse(croppedMat, centerInCrop, maskSize, 0.0, 0.0, 360.0, Scalar(0.0), -1)

            edges = Mat()
            Imgproc.GaussianBlur(croppedMat, croppedMat, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(croppedMat, edges, 40.0, 120.0)

            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            var bestEllipse: RotatedRect? = null
            var maxCircularity = 0f

            for (contour in contours) {
                if (contour.toArray().size >= 5) {
                    val points2f = MatOfPoint2f(*contour.toArray())
                    val ellipse = Imgproc.fitEllipse(points2f)
                    if (ellipse.size.width > cropW * 0.95 || ellipse.size.height > cropH * 0.95) continue
                    val circ = (min(ellipse.size.width, ellipse.size.height) / max(ellipse.size.width, ellipse.size.height)).toFloat()
                    if (circ > 0.4f && circ > maxCircularity) {
                        maxCircularity = circ
                        bestEllipse = ellipse
                    }
                }
            }

            if (bestEllipse != null) {
                val absCx = left + bestEllipse.center.x
                val absCy = top + bestEllipse.center.y
                val finalCx = (absCx / matW.toFloat()) * viewW
                val finalCy = (absCy / matH.toFloat()) * viewH
                val finalW = (bestEllipse.size.width / matW.toFloat()) * viewW
                val finalH = (bestEllipse.size.height / matH.toFloat()) * viewH
                return RefinedResult(finalCx.toFloat(), finalCy.toFloat(), finalW.toFloat(), finalH.toFloat(), bestEllipse.angle.toFloat(), maxCircularity, true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV Error", e)
        } finally {
            croppedMat?.release()
            edges?.release()
        }
        return RefinedResult(defaultCx, defaultCy, 0f, 0f, 0f, 0f, false)
    }

    // Parse output for [1, 300, 6] format
    private fun parseOutput(output: Array<*>, deviceRotation: Int): List<Detection> {
        val detections = mutableListOf<Detection>()

        val aiRotation = when (deviceRotation) {
            0 -> 0f
            90 -> 270f
            180 -> 180f
            270 -> 90f
            else -> 0f
        }
        val matrix = Matrix()
        if (aiRotation != 0f) {
            matrix.postTranslate(-0.5f, -0.5f)
            matrix.postRotate(aiRotation)
            matrix.postTranslate(0.5f, 0.5f)
        }

        try {
            val batchData = output as Array<Array<FloatArray>>  // shape -> [1][300][6]
            val boxes = batchData[0]    // extract first batch -> [300][6]

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

                    if (aiRotation != 0f) {
                        matrix.mapRect(rect)
                    }

                    if (rect.width() > 0 && rect.height() > 0) {
                        detections.add(Detection(boundingBox = rect, confidence = score))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing output", e)
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