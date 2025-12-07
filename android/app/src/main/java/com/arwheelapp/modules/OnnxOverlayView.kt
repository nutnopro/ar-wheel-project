package com.arwheelapp.modules

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.View
import com.arwheelapp.utils.OnnxRuntimeHandler.Detection

class OnnxOverlayView(context: Context) : View(context) {
    private val TAG = "OnnxOverlayView: "
    private val className = "wheel"

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
        // if (newDetections.isNotEmpty()) {
        //     Log.d(TAG, "updateDetections: Received ${newDetections.size} detections. Requesting redraw.")
        // } else {
        //     Log.d(TAG, "updateDetections: Received empty list. Clearing overlay.")
        // }

        detections.clear()
        detections.addAll(newDetections)

        // use postInvalidate for thread safety
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (detections.isEmpty()) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        // Log.d(TAG, "onDraw: Drawing ${detections.size} boxes on canvas ($viewW x $viewH)")

        detections.forEachIndexed { index, det ->
            if (det.confidence < CONFIDENCE_THRESHOLD) return@forEachIndexed

            // Convert Normalized coordinates (0..1) back to actual Pixels
            val centerX = det.x * viewW
            val centerY = det.y * viewH
            val boxWidth = det.w * viewW
            val boxHeight = det.h * viewH

            val left = centerX - boxWidth / 2
            val top = centerY - boxHeight / 2
            val right = centerX + boxWidth / 2
            val bottom = centerY + boxHeight / 2

            val label = "$className ${"%.2f".format(det.confidence)}"

            canvas.drawRect(left, top, right, bottom, boxPaint)
            canvas.drawText(label, left, maxOf(top - 8f, 32f), textPaint)
            // Log.d(TAG, "onDraw: Box #$index -> [L:$left, T:$top, R:$right, B:$bottom]")
        }
    }
}
