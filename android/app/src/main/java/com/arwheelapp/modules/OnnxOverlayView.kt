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
        textSize = 40f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun updateDetections(newDetections: List<Detection>) {
        detections.clear()
        detections.addAll(newDetections)
        
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (detections.isEmpty()) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        detections.forEach { det ->
            if (det.confidence < CONFIDENCE_THRESHOLD) return@forEach
            val bbox = det.boundingBox

            val left = bbox.left * viewW
            val top = bbox.top * viewH
            val right = bbox.right * viewW
            val bottom = bbox.bottom * viewH

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val label = "$className ${(det.confidence * 100).toInt()}%"
            
            val textX = left
            val textY = if (top - 10f < 40f) top + 40f else top - 10f

            canvas.drawText(label, textX, textY, textPaint)
        }
    }
}
