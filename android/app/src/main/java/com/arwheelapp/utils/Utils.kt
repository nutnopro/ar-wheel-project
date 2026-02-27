package com.arwheelapp.utils

import android.content.res.Resources
import android.graphics.RectF
import com.google.ar.core.Anchor
import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.Quaternion

// ──────────────────────────────────────────────
// AR Operation Modes
// ──────────────────────────────────────────────
enum class ARMode {
    MARKER_BASED,
    MARKERLESS;

    companion object {
        val DEFAULT = MARKERLESS
    }
}

// ──────────────────────────────────────────────
// Detection & CV data
// ──────────────────────────────────────────────
data class Detection(
    val boundingBox: RectF,
    val confidence: Float
)

class FrameYData(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val rowStride: Int
)

data class ProcessedDetection(
    val detection: Detection,
    val cvResult: RefinedResult
)

// Result from OpenCV ellipse fitting
data class RefinedResult(
    val cx: Float,          // refined center X in screen pixels
    val cy: Float,          // refined center Y in screen pixels
    val width: Float,       // ellipse major axis in screen pixels
    val height: Float,      // ellipse minor axis in screen pixels
    val angle: Float,       // ellipse rotation angle in degrees
    val circularity: Float, // minor/major ratio → 1.0 = perfect circle
    val isFound: Boolean
)

// single candidate world position ranked by circularity
data class TrackedPos(
    val pos: Float3,
    val circularity: Float
)

// A single candidate world rotation ranked by circularity
data class TrackedRot(
    val rot: Quaternion,
    val circularity: Float
)

// ──────────────────────────────────────────────
// Per-model tracking state
// ──────────────────────────────────────────────
data class ModelState(
    var anchor: Anchor? = null,
    var lastDetectionTime: Long = 0L,
    var detectionHits: Int = 0,
    val posHistory: MutableList<TrackedPos> = mutableListOf(),
    var bestPos: Float3 = Float3(0f, 0f, 0f),
    val rotHistory: MutableList<TrackedRot> = mutableListOf(),
    var bestRot: Quaternion = Quaternion(),
    var lastStablePos: Float3? = null,
    var driftFrames: Int = 0,
    var stableFrames: Int = 0,      // consecutive high-circularity frames → triggers anchor creation
    var manualOffset: Float3 = Float3(0f, 0f, 0f)
)

// ──────────────────────────────────────────────
// Extensions
// ──────────────────────────────────────────────
val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

val Float.dp: Float
    get() = (this * Resources.getSystem().displayMetrics.density)