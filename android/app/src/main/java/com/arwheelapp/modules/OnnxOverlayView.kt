package com.arapp.modules

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import io.github.sceneview.ar.ARSceneView
import android.util.Log

class OnnxOverlayView {
	private const val TAG = "OnnxOverlayView"

	data class Detection(
		val xCenter: Float,
		val yCenter: Float,
		val width: Float,
		val height: Float,
		val confidence: Float,
		val className: String = "wheel"
	)

    var detections: List<Detection> = emptyList()
    var modelInputSize: Int = 320
    var confidenceThreshold: Float = 0.4f

    private val boxPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val textPaint = Paint().apply {
        color = Color.BLUE
        textSize = 32f
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.d(TAG, "onDraw called. Canvas size: ${canvas.width}x${canvas.height}, total detections: ${detections.size}")
        drawBoundingBoxes(canvas)
    }

    private fun drawBoundingBoxes(canvas: Canvas) {
        val scaleX = width.toFloat() / modelInputSize
        val scaleY = height.toFloat() / modelInputSize
        Log.d(TAG, "Drawing bounding boxes with scaleX=$scaleX, scaleY=$scaleY")

        detections.forEachIndexed { index, det ->
            if (det.confidence < confidenceThreshold) {
                Log.d(TAG, "Detection #$index skipped due to confidence ${det.confidence} < $confidenceThreshold")
                return@forEachIndexed
            }

            // Update bounding box calculation
            // inverse only Y (top/bottom)
            var left = (modelInputSize - (det.yCenter + det.height / 2f)) * scaleX
            var top = (det.xCenter - det.width / 2f) * scaleY
            var right = (modelInputSize - (det.yCenter - det.height / 2f)) * scaleX
            var bottom = (det.xCenter + det.width / 2f) * scaleY

            // Check and swap if left > right or top > bottom
            if (left > right) {
                val temp = left
                left = right
                right = temp
            }
            if (top > bottom) {
                val temp = top
                top = bottom
                bottom = temp
            }

            // Clipping to canvas bounds
            left = left.coerceIn(0f, width.toFloat())
            top = top.coerceIn(0f, height.toFloat())
            right = right.coerceIn(0f, width.toFloat())
            bottom = bottom.coerceIn(0f, height.toFloat())

            Log.d(TAG, "Detection #$index bounding box: left=$left, top=$top, right=$right, bottom=$bottom, class=${det.className}, conf=${det.confidence}")
            Log.d(TAG, "Canvas bounds: width=$width, height=$height")

            // draw rectangle and label only if dimensions are valid
            if (right > left && bottom > top) {
                canvas.drawRect(left, top, right, bottom, boxPaint)

                // draw label + confidence
                val label = "${det.className} ${"%.2f".format(det.confidence)}"
                canvas.drawText(label, left, maxOf(top - 8f, 32f), textPaint)
            } else {
                Log.d(TAG, "Detection #$index skipped - invalid rectangle dimensions")
            }
        }
    }
}