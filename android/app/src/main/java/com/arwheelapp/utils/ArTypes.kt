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

data class ModelLockState(
    var stableFrameCount: Int = 0,
    var isLocked: Boolean = false,
    var lastLockedPos: Float3? = null
)