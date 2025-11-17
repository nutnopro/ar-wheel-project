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
    private val CONFIDENCE_THRESHOLD: Float = 0.5f

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

    fun updateDetections(newDetections: List<Detection>) {
        detections.clear()
        detections.addAll(newDetections)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty()) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        detections.forEachIndexed { index, det ->
            if (det.confidence < CONFIDENCE_THRESHOLD) return@forEachIndexed
            val centerX = det.x * width
            val centerY = det.y * height
            val boxWidth = det.w * width
            val boxHeight = det.h * height

            val left = centerX - boxWidth / 2
            val top = centerY - boxHeight / 2
            val right = centerX + boxWidth / 2
            val bottom = centerY + boxHeight / 2

            val label = "${det.className} ${"%.2f".format(det.confidence)}"
            canvas.drawRect(left, top, right, bottom, boxPaint)
            canvas.drawText(label, left, maxOf(top - 8f, 32f), textPaint)
        }
    }
}