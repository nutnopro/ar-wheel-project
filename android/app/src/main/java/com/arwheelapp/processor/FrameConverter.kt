package com.arwheelapp.processor

import android.media.Image
import android.util.Log
import com.google.ar.core.Frame
import kotlin.math.min

class FrameConverter {
    private val TAG = "FrameConverter: "
    private val INPUT_SIZE = 320  // *YOLO input size

    // *ARCore Frame -> FloatArray Tensor
    fun convertFrameToTensor(frame: Frame): FloatArray {
        return try {
            val image = frame.acquireCameraImage() ?: return FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
            // Log.d(TAG, "convertFrameToTensor: Acquired image size: ${image.width}x${image.height}")

            val tensor = imageToLetterboxedTensor(image)

            image.close()
            // Log.d(TAG, "convertFrameToTensor: Conversion success. Tensor size: ${tensor.size}")

            tensor
        } catch (e: Exception) {
            Log.e(TAG, "convertFrameToTensor: Error processing image", e)
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

        val rotatedWidth = height 
        val rotatedHeight = width

        val tensor = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val scale = min(INPUT_SIZE.toFloat() / rotatedWidth, INPUT_SIZE.toFloat() / rotatedHeight)

        val newW = (rotatedWidth * scale).toInt()
        val newH = (rotatedHeight * scale).toInt()
        val padX = (INPUT_SIZE - newW) / 2
        val padY = (INPUT_SIZE - newH) / 2

        val yBytes = ByteArray(yBuffer.remaining())
        yBuffer.get(yBytes)
        val uBytes = ByteArray(uBuffer.remaining())
        uBuffer.get(uBytes)
        val vBytes = ByteArray(vBuffer.remaining())
        vBuffer.get(vBytes)

        val channelSize = INPUT_SIZE * INPUT_SIZE

        // Loop only the pixels in the center of the image (scale + pad)
        for (j in 0 until newH) {
            for (i in 0 until newW) {
                val logicX = (i / scale).toInt()
                val logicY = (j / scale).toInt()

                // Rotate coordinates
                val srcX = logicY.coerceIn(0, width - 1)
                val srcY = (height - 1 - logicX).coerceIn(0, height - 1)

                // YUV indices calculation
                val yIdx = yRowStride * srcY + srcX
                val uvIdx = (srcY / 2) * uvRowStride + (srcX / 2) * uvPixelStride

                val yVal = (yBytes[yIdx].toInt() and 0xff)
                val uVal = (uBytes[uvIdx].toInt() and 0xff) - 128
                val vVal = (vBytes[uvIdx].toInt() and 0xff) - 128

                // YUV to RGB conversion
                var r = (yVal + 1.402f * vVal).coerceIn(0f, 255f)
                var g = (yVal - 0.344136f * uVal - 0.714136f * vVal).coerceIn(0f, 255f)
                var b = (yVal + 1.772f * uVal).coerceIn(0f, 255f)

                // Normalize 0..1
                val outX = padX + i
                val outY = padY + j
                val idx = outY * INPUT_SIZE + outX

                tensor[idx] = r / 255f
                tensor[idx + channelSize] = g / 255f
                tensor[idx + channelSize * 2] = b / 255f
            }
        }
        return tensor
    }
}
