package com.arwheelapp.modules

import android.content.Context
import android.view.View
import android.util.Log
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Canvas
import io.github.sceneview.ar.ARSceneView

class OnnxOverlayView(context: Context) : View(context) {
    private const val TAG = "OnnxOverlayView"

    data class Detection(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val confidence: Float,
        val className: String = "wheel"
    )

    private val detections: MutableList<Detection> = mutableListOf()
    private val inputSize: Int = 320
    private val confidenceThreshold: Float = 0.4f

    private val boxPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val textPaint = Paint().apply {
        color = Color.BLUE
        textSize = 32f
        style = Paint.Style.FILL
    }

    // !fix this
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty()) return

        val viewW = width.toFloat() 
        val viewH = height.toFloat()

        detections.forEachIndexed { index, det ->
            if (det.confidence < confidenceThreshold) return@forEachIndexed

            var left = (inputSize - (det.yCenter + det.height / 2f)) * scaleX
            var top = (det.xCenter - det.width / 2f) * scaleY
            var right = (inputSize - (det.yCenter - det.height / 2f)) * scaleX
            var bottom = (det.xCenter + det.width / 2f) * scaleY

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

            // left = left.coerceIn(0f, width.toFloat())
            // top = top.coerceIn(0f, height.toFloat())
            // right = right.coerceIn(0f, width.toFloat())
            // bottom = bottom.coerceIn(0f, height.toFloat())

            if (right > left && bottom > top) {
                canvas.drawRect(left, top, right, bottom, boxPaint)

                val label = "${det.className} ${"%.2f".format(det.confidence)}"
                canvas.drawText(label, left, maxOf(top - 8f, 32f), textPaint)
            } else {
                Log.d(TAG, "Detection #$index skipped - invalid rectangle dimensions")
            }
        }
    }
}