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

class FrameConverter {
	private const val TAG = "FrameConverter"

    // ARCore Frame -> FloatArray Tensor
    fun convertFrameToTensor(frame: Frame): FloatArray {
        var image: Image? = null
        return try {
            image = frame.acquireCameraImage()

            if (image.format == android.graphics.ImageFormat.YUV_420_888) {
                Log.d(TAG, "Image format is YUV_420_888 - CORRECT FORMAT!")
            } else {
                Log.e(TAG, "Wrong image format: ${image.format}")
            }

            // แปลง YUV -> RGB array
            val rgb = convertYUVToRGB(image)
            Log.d(TAG, "RGB array created, size: ${rgb.size}")

            // แปลง RGB -> Bitmap
            val bitmap = convertRGBToBitmap(rgb, image.width, image.height)
            Log.d(TAG, "Bitmap created, size: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")

            // Resize bitmap
            val resized = resizeBitmap(bitmap, INPUT_SIZE, INPUT_SIZE)
            Log.d(TAG, "Bitmap resized to: ${resized.width}x${resized.height}")

            // Convert bitmap -> tensor
            val tensor = convertBitmapToTensor(resized)
            Log.d(TAG, "Tensor created, length: ${tensor.size}")

            // Normalize tensor
            normalizeTensor(tensor)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing image: ", e)
            // Return default tensor if error
            FloatArray(3 * INPUT_SIZE * INPUT_SIZE) { 0f }
        } finally {
            image?.close()
            Log.v(TAG, "Image closed successfully")
        }
    }

    // YUV_420_888 -> RGB
    private fun convertYUVToRGB(image: Image): IntArray {
        val width = image.width
        val height = image.height
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        val rgbArray = IntArray(width * height)

        val y = ByteArray(yBuffer.remaining())
        val u = ByteArray(uBuffer.remaining())
        val v = ByteArray(vBuffer.remaining())
        yBuffer.get(y)
        uBuffer.get(u)
        vBuffer.get(v)

        var yp: Int
        for (j in 0 until height) {
            val pY = yRowStride * j
            val uvRow = uvRowStride * (j shr 1)

            for (i in 0 until width) {
                yp = pY + i
                val uvOffset = uvRow + (i shr 1) * uvPixelStride

                val yVal = (y[yp].toInt() and 0xff) - 16
                val uVal = (u[uvOffset].toInt() and 0xff) - 128
                val vVal = (v[uvOffset].toInt() and 0xff) - 128

                var r = (1.164f * yVal + 1.596f * vVal).toInt()
                var g = (1.164f * yVal - 0.813f * vVal - 0.391f * uVal).toInt()
                var b = (1.164f * yVal + 2.018f * uVal).toInt()

                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)

                rgbArray[j * width + i] = Color.rgb(r, g, b)
            }
        }
        return rgbArray
    }

    // RGB array -> Bitmap
    private fun convertRGBToBitmap(rgb: IntArray, width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(rgb, width, height, Bitmap.Config.ARGB_8888)
    }

    // Resize bitmap
    private fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    // Bitmap -> Tensor (FloatArray)
    private fun convertBitmapToTensor(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val floatArray = FloatArray(3 * width * height)

        var idx = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                floatArray[idx] = Color.red(pixel).toFloat()
                floatArray[idx + width * height] = Color.green(pixel).toFloat()
                floatArray[idx + 2 * width * height] = Color.blue(pixel).toFloat()
                idx++
            }
        }
        return floatArray
    }

    // Normalize tensor
    private fun normalizeTensor(tensor: FloatArray): FloatArray {
        return tensor.map { it / 255f }.toFloatArray()
    }
}
