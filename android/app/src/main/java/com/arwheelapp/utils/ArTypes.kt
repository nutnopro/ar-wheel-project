package com.arwheelapp.utils

import android.graphics.RectF

enum class ARMode {
    MARKER_BASED,
    MARKERLESS
}

data class Detection(
    val boundingBox: RectF,
    val confidence: Float
)