package com.arwheelapp.utils

import android.content.res.Resources
import android.graphics.RectF
import com.google.ar.core.Anchor
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion

// AR Operation Modes
enum class ARMode {
    MARKER_BASED,
    MARKERLESS;

    companion object {
        val DEFAULT = MARKERLESS
    }
}
// Raw AI Detection Result

data class Detection(
    val boundingBox: RectF,
    val confidence: Float
)

// Stored Pose with Circularity Score for Ranking
data class TrackedPose(
    val pos: Float3,
    val rot: Quaternion,
    val circularity: Float
)

// Result from OpenCV Ellipse Fitting
data class RefinedResult(
    val cx: Float,
    val cy: Float,
    val width: Float,
    val height: Float,
    val angle: Float,
    val circularity: Float,
    val isFound: Boolean
)

// Model State & Smoothing Management
data class ModelState(
    var anchor: Anchor? = null,
    var lastDetectionTime: Long = 0L,
    var detectionHits: Int = 0,
    val poseHistory: MutableList<TrackedPose> = mutableListOf(),
    var bestPos: Float3 = Float3(0f, 0f, 0f),
    var bestRot: Quaternion = Quaternion(),
    var lastStablePos: Float3? = null
)

// --- Extensions ---
val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

val Float.dp: Float
    get() = (this * Resources.getSystem().displayMetrics.density)