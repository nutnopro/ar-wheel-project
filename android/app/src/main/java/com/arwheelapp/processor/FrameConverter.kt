package com.arwheelapp.processor

import android.graphics.*
import android.media.Image
import android.util.Log
import com.google.ar.core.Frame
import java.io.ByteArrayOutputStream

class FrameConverter {
    companion object {
        private const val TAG = "FrameConverter"
        private const val INPUT_SIZE = 320  // YOLO input size
    }

    private var nv21Buffer: ByteArray? = null
    private var resizedBitmap: Bitmap? = null
    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val yuvStream = ByteArrayOutputStream()

    // ARCore Frame -> FloatArray Tensor
    fun convertFrameToTensor(frame: Frame, deviceRotation: Int): FloatArray {
        val image = try {
            frame.acquireCameraImage()
        } catch (e: Exception) {
            null
        } ?: return FloatArray(3 * INPUT_SIZE * INPUT_SIZE)

        try {
            val width = image.width
            val height = image.height
            val requiredSize = width * height * 3 / 2
            if (nv21Buffer == null || nv21Buffer!!.size != requiredSize) nv21Buffer = ByteArray(requiredSize)

            val rawBitmap = yuvToBitmap(image, nv21Buffer!!)

            if (resizedBitmap == null) {
                resizedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
            }

            val finalRotation = when (deviceRotation) {
                0 -> 90
                90 -> 180
                180 -> 270
                270 -> 0
                else -> 90
            }
            processBitmap(rawBitmap, finalRotation.toFloat(), resizedBitmap!!)
            rawBitmap.recycle()

            return bitmapToFloatArray(resizedBitmap!!)
        } catch (e: Exception) {
            Log.e(TAG, "Error in convertFrameToTensor", e)
            return FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        } finally {
            image.close()
        }
    }

    private fun yuvToBitmap(image: Image, outNv21: ByteArray): Bitmap {
        val width = image.width
        val height = image.height
        val yBuffer = image.planes[0].buffer.apply { rewind() }
        val uBuffer = image.planes[1].buffer.apply { rewind() }
        val vBuffer = image.planes[2].buffer.apply { rewind() }
        val yRowStride = image.planes[0].rowStride
        if (yRowStride == width) {
            yBuffer.get(outNv21, 0, width * height)
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(outNv21, row * width, width)
            }
        }

        val vRowStride = image.planes[2].rowStride
        val vPixelStride = image.planes[2].pixelStride
        val uRowStride = image.planes[1].rowStride
        val uPixelStride = image.planes[1].pixelStride
        var pos = width * height
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                outNv21[pos++] = vBuffer.get(row * vRowStride + col * vPixelStride)
                outNv21[pos++] = uBuffer.get(row * uRowStride + col * uPixelStride)
            }
        }

        yuvStream.reset()
        val yuvImage = YuvImage(outNv21, ImageFormat.NV21, width, height, null)
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, yuvStream)
        val bytes = yuvStream.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun processBitmap(source: Bitmap, rotateDegrees: Float, destBitmap: Bitmap) {
        val canvas = Canvas(destBitmap)
        canvas.drawColor(Color.BLACK) 
        matrix.reset()
        val scale = INPUT_SIZE.toFloat() / maxOf(source.width, source.height)
        matrix.postTranslate(-source.width / 2f, -source.height / 2f)
        matrix.postRotate(rotateDegrees)
        matrix.postScale(scale, scale)
        matrix.postTranslate(INPUT_SIZE / 2f, INPUT_SIZE / 2f)
        canvas.drawBitmap(source, matrix, paint)
    }

    private fun bitmapToFloatArray(bitmap: Bitmap): FloatArray {
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        val floatArray = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val channelSize = INPUT_SIZE * INPUT_SIZE
        for (i in 0 until channelSize) {
            val v = intValues[i]
            floatArray[i] = ((v shr 16 and 0xFF) / 255.0f)
            floatArray[i + channelSize] = ((v shr 8 and 0xFF) / 255.0f)
            floatArray[i + channelSize * 2] = ((v and 0xFF) / 255.0f)
        }
        return floatArray
    }
}