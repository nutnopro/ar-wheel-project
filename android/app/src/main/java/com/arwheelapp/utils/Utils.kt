package com.arwheelapp.utils

import android.content.res.Resources
import android.graphics.RectF
import com.google.ar.core.Anchor
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion

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
    var isLocked: Boolean = false,
    var stableFrameCount: Int = 0,
    var lastDetectionTime: Long = 0L,
    var bestRatioError: Float = Float.MAX_VALUE,
    var bestPos: Float3 = Float3(0f, 0f, 0f),
    var bestRot: Quaternion = Quaternion(),
    var anchor: Anchor? = null
)

// --- Extensions ---
val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

val Float.dp: Float
    get() = (this * Resources.getSystem().displayMetrics.density)