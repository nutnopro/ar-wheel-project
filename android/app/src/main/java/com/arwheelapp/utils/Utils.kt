package com.arwheelapp.utils

import android.content.res.Resources
import android.graphics.RectF

// --- AR Types ---
enum class ARMode {
    MARKER_BASED,
    MARKERLESS;

    companion object {
        val DEFAULT = MARKERLESS
    }
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

// --- Extensions ---
val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

val Float.dp: Float
    get() = (this * Resources.getSystem().displayMetrics.density)