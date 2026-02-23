package com.arwheelapp.modules

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.arwheelapp.utils.Detection

class OnnxOverlayView(context: Context) : View(context) {
    companion object {
        private const val TAG = "OnnxOverlayView"
        private const val INPUT_SIZE = 320f
    }

    private val detections: MutableList<Detection> = mutableListOf()

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
        val scale = maxOf(viewW, viewH) / INPUT_SIZE
        val offsetX = (viewW - (INPUT_SIZE * scale)) / 2
        val offsetY = (viewH - (INPUT_SIZE * scale)) / 2
        detections.forEach { det ->
            val bbox = det.boundingBox
            val left = (bbox.left * INPUT_SIZE * scale) + offsetX
            val top = (bbox.top * INPUT_SIZE * scale) + offsetY
            val right = (bbox.right * INPUT_SIZE * scale) + offsetX
            val bottom = (bbox.bottom * INPUT_SIZE * scale) + offsetY
            canvas.drawRect(left, top, right, bottom, boxPaint)
            canvas.drawText("${(det.confidence * 100).toInt()}%", left, top - 10, textPaint)
        }
    }

    fun clear() {
        detections.clear()
        invalidate()
    }
}