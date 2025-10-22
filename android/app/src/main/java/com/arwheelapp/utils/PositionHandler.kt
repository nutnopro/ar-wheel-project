package com.arwheelapp.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.media.Image
import ai.onnxruntime.*
import java.io.File
import java.io.FileOutputStream
import com.google.ar.core.Frame
import java.nio.FloatBuffer
import android.util.Log
import io.github.sceneview.math.*
import io.github.sceneview.collision.Quaternion
import io.github.sceneview.collision.Vector3
import io.github.sceneview.math.toFloat3
import dev.romainguy.kotlin.math.Float3

class PositionHandler {
	private const val TAG = "PositionHandler"

	data class Pose3D(
        val position: Float3,
        val rotation: Float3,
        val scale: Float3 = Float3(1f, 1f, 1f)
    )

    data class Detection(
        val xCenter: Float,
        val yCenter: Float,
        val width: Float,
        val height: Float,
        val confidence: Float,
    )

    // Get 3D positions with hitTest
    fun get3DPosition(frame: Frame, detections: List<Detection>, confidenceThreshold: Float = 0.4f): List<Pose3D> {
        val poses = mutableListOf<Pose3D>()
        Log.d(TAG, "Start computing 3D poses from ${detections.size} detections")

        detections.forEachIndexed { index, det ->
            Log.d(TAG, "Processing detection #$index: x=${det.xCenter}, y=${det.yCenter}, w=${det.width}, h=${det.height}, conf=${det.confidence}")

            if (det.confidence < confidenceThreshold) {
                Log.d(TAG, "Detection #$index skipped due to confidence < $confidenceThreshold")
                return@forEachIndexed
            }

            val centerX = det.xCenter
            val centerY = det.yCenter
            val topX = centerX
            val topY = centerY - det.height * 0.1f
            val rightX = centerX + det.width * 0.1f
            val rightY = centerY

            val hitsCenter = frame.hitTest(centerX, centerY)
            val hitsTop = frame.hitTest(topX, topY)
            val hitsRight = frame.hitTest(rightX, rightY)

            Log.d(TAG, "Detection #$index hits: center=${hitsCenter.size}, top=${hitsTop.size}, right=${hitsRight.size}")

            if (hitsCenter.isEmpty() || hitsTop.isEmpty() || hitsRight.isEmpty()) {
                Log.d(TAG, "Detection #$index skipped due to missing hit test results")
                return@forEachIndexed
            }

            val p0 = hitsCenter[0].hitPose.toVector3()
            val p1 = hitsTop[0].hitPose.toVector3()
            val p2 = hitsRight[0].hitPose.toVector3()

            val forwardAxis = Vector3.subtract(p1, p0).normalized()
            val rightAxis = Vector3.subtract(p2, p0).normalized()
            val upAxis = Vector3.cross(forwardAxis, rightAxis).normalized()

            val rotation = Quaternion.lookRotation(forwardAxis, upAxis)
            val euler = rotation.getEulerAngles()

            Log.d(TAG, "Detection #$index pose: position=(${p0.x}, ${p0.y}, ${p0.z}), rotation Euler=(${euler.x}, ${euler.y}, ${euler.z})")

            poses.add(Pose3D(
                position = p0.toFloat3(),
                rotation = euler.toFloat3()
            ))
        }

        Log.d(TAG, "Finished computing 3D poses. Total valid poses: ${poses.size}")
        return poses
    }

    private fun com.google.ar.core.Pose.toVector3(): Vector3 {
        return Vector3(tx(), ty(), tz())
    }
}