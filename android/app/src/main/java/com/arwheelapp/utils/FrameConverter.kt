package com.arwheelapp.utils

import android.util.Log
import android.media.Image
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.nio.ByteBuffer
import kotlin.math.min
import com.google.ar.core.Frame

class FrameConverter {
    companion object {
        private const val TAG = "FrameConverter"
        private const val INPUT_SIZE = 320  // *YOLO input size
    }

    // *ARCore Frame -> FloatArray Tensor
    fun convertFrameToTensor(frame: Frame): FloatArray {
        return try {
            // *Get camera image from frame - image is YUV_420_888
            val image = frame.acquireCameraImage()

            // *Convert and letterbox resize to tensor
            val tensor = imageToLetterboxedTensor(image)

            image.close()
            
            // *Return
            tensor
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ", e)
            // *Return default tensor if error
            FloatArray(3 * INPUT_SIZE * INPUT_SIZE) { 0f }
        }
    }

    private fun imageToLetterboxedTensor(image: Image): FloatArray {
        val width = image.width
        val height = image.height

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        val tensor = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val scale = min(INPUT_SIZE.toFloat() / width, INPUT_SIZE.toFloat() / height)
        val newW = (width * scale).toInt()
        val newH = (height * scale).toInt()
        val padX = (INPUT_SIZE - newW) / 2
        val padY = (INPUT_SIZE - newH) / 2

        val yBytes = ByteArray(yBuffer.remaining())
        yBuffer.get(yBytes)

        val uBytes = ByteArray(uBuffer.remaining())
        vBuffer.get(ByteArray(vBuffer.remaining())) // *skip old vBuffer
        uBuffer.get(uBytes)

        // *Loop only the pixels in the center of the image (scale + pad)
        for (j in 0 until newH) {
            val srcY = (j / scale).toInt().coerceIn(0, height - 1)
            val pY = yRowStride * srcY

            for (i in 0 until newW) {
                val srcX = (i / scale).toInt().coerceIn(0, width - 1)
                val yp = pY + srcX

                val yVal = (yBytes[yp].toInt() and 0xff) - 16
                val uVal = (uBytes[(srcY / 2) * uvRowStride + (srcX / 2) * uvPixelStride].toInt() and 0xff) - 128
                val vVal = (uBytes[(srcY / 2) * uvRowStride + (srcX / 2) * uvPixelStride].toInt() and 0xff) - 128

                var r = (1.164f * yVal + 1.596f * vVal)
                var g = (1.164f * yVal - 0.813f * vVal - 0.391f * uVal)
                var b = (1.164f * yVal + 2.018f * uVal)

                r = r.coerceIn(0f, 255f)
                g = g.coerceIn(0f, 255f)
                b = b.coerceIn(0f, 255f)

                val outX = padX + i
                val outY = padY + j

                val idx = outY * INPUT_SIZE + outX
                tensor[idx] = r / 255f
                tensor[idx + INPUT_SIZE * INPUT_SIZE] = g / 255f
                tensor[idx + 2 * INPUT_SIZE * INPUT_SIZE] = b / 255f
            }
        }

        return tensor
    }
}