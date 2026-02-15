package com.arwheelapp.utils

import android.graphics.RectF
import dev.romainguy.kotlin.math.Float3

enum class ARMode {
    MARKER_BASED,
    MARKERLESS
}

data class Detection(
    val boundingBox: RectF,
    val confidence: Float
)

data class ModelState(
    var stableFrameCount: Int = 0,
    var isLocked: Boolean = false,
    var bestAspectRatio: Float = 0f
)