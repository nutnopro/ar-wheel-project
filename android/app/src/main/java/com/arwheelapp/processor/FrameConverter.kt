package com.arwheelapp.processor

import android.graphics.*
import android.media.Image
import android.util.Log
import com.google.ar.core.Frame
import java.io.ByteArrayOutputStream
import android.os.SystemClock

class FrameConverter {
    private val TAG = "FrameConverter: "
    private val INPUT_SIZE = 320  // YOLO input size

    private var nv21Buffer: ByteArray? = null
    private var resizedBitmap: Bitmap? = null
    private val matrix = Matrix() 
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val yuvStream = ByteArrayOutputStream()

    // *ARCore Frame -> FloatArray Tensor
    fun convertFrameToTensor(frame: Frame): FloatArray {
        val image = try {
            frame.acquireCameraImage()
        } catch (e: Exception) {
            null
        } ?: return FloatArray(3 * INPUT_SIZE * INPUT_SIZE)

        try {
            val width = image.width
            val height = image.height
            
            val requiredSize = width * height * 3 / 2
            if (nv21Buffer == null || nv21Buffer!!.size != requiredSize) {
                nv21Buffer = ByteArray(requiredSize)
            }

            val rawBitmap = yuvToBitmap(image, nv21Buffer!!)

            if (resizedBitmap == null) {
                resizedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
            }

            processBitmap(rawBitmap, 90f, resizedBitmap!!)

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
        
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        // --- จุดแก้ไข: Copy Y plane ทีละแถวเพื่อเลี่ยง Padding ---
        val yRowStride = yPlane.rowStride
        if (yRowStride == width) {
            yBuffer.get(outNv21, 0, width * height)
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(outNv21, row * width, width)
            }
        }

        // --- Copy UV (NV21 format: V-U-V-U) ---
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride

        var pos = width * height
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vIdx = row * vRowStride + col * vPixelStride
                val uIdx = row * uRowStride + col * uPixelStride
                outNv21[pos++] = vBuffer.get(vIdx)
                outNv21[pos++] = uBuffer.get(uIdx)
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
        
        // จัดกึ่งกลาง หมุน และย่อ
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

        val midIndex = (INPUT_SIZE * (INPUT_SIZE / 2)) + (INPUT_SIZE / 2)
        val p = intValues[midIndex]
        val r = (p shr 16 and 0xFF)
        val g = (p shr 8 and 0xFF)
        val b = (p and 0xFF)
        Log.v(TAG, "🎨 Center Pixel Color: R=$r, G=$g, B=$b")
        if (r == 0 && g == 0 && b == 0) {
            Log.e(TAG, "🚨 ALERT: Center of image is still BLACK!")
        }

        for (i in 0 until INPUT_SIZE * INPUT_SIZE) {
            val v = intValues[i]
            floatArray[i] = ((v shr 16 and 0xFF) / 255.0f)
            floatArray[i + channelSize] = ((v shr 8 and 0xFF) / 255.0f)
            floatArray[i + channelSize * 2] = ((v and 0xFF) / 255.0f)
        }
        return floatArray
    }
}






// class FrameConverter {
//     private val TAG = "FrameConverter: "
//     private val INPUT_SIZE = 320  // *YOLO input size

//     // *ARCore Frame -> FloatArray Tensor
//     fun convertFrameToTensor(frame: Frame): FloatArray {
//         return try {
//             val image = frame.acquireCameraImage() ?: return FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
//             // Log.d(TAG, "convertFrameToTensor: Acquired image size: ${image.width}x${image.height}")

//             val tensor = imageToLetterboxedTensor(image)

//             image.close()
//             // Log.d(TAG, "convertFrameToTensor: Conversion success. Tensor size: ${tensor.size}")

//             tensor
//         } catch (e: Exception) {
//             Log.e(TAG, "convertFrameToTensor: Error processing image", e)
//             FloatArray(3 * INPUT_SIZE * INPUT_SIZE) { 0f }
//         }
//     }

//     private fun imageToLetterboxedTensor(image: Image): FloatArray {
//         val width = image.width
//         val height = image.height

//         val yBuffer = image.planes[0].buffer
//         val uBuffer = image.planes[1].buffer
//         val vBuffer = image.planes[2].buffer

//         val yRowStride = image.planes[0].rowStride
//         val uvRowStride = image.planes[1].rowStride
//         val uvPixelStride = image.planes[1].pixelStride

//         val rotatedWidth = height 
//         val rotatedHeight = width

//         val tensor = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
//         val scale = min(INPUT_SIZE.toFloat() / rotatedWidth, INPUT_SIZE.toFloat() / rotatedHeight)

//         val newW = (rotatedWidth * scale).toInt()
//         val newH = (rotatedHeight * scale).toInt()
//         val padX = (INPUT_SIZE - newW) / 2
//         val padY = (INPUT_SIZE - newH) / 2

//         val yBytes = ByteArray(yBuffer.remaining())
//         yBuffer.get(yBytes)
//         val uBytes = ByteArray(uBuffer.remaining())
//         uBuffer.get(uBytes)
//         val vBytes = ByteArray(vBuffer.remaining())
//         vBuffer.get(vBytes)

//         val channelSize = INPUT_SIZE * INPUT_SIZE

//         // Loop only the pixels in the center of the image (scale + pad)
//         for (j in 0 until newH) {
//             for (i in 0 until newW) {
//                 val logicX = (i / scale).toInt()
//                 val logicY = (j / scale).toInt()

//                 // Rotate coordinates
//                 val srcX = logicY.coerceIn(0, width - 1)
//                 val srcY = (height - 1 - logicX).coerceIn(0, height - 1)

//                 // YUV indices calculation
//                 val yIdx = yRowStride * srcY + srcX
//                 val uvIdx = (srcY / 2) * uvRowStride + (srcX / 2) * uvPixelStride

//                 val yVal = (yBytes[yIdx].toInt() and 0xff)
//                 val uVal = (uBytes[uvIdx].toInt() and 0xff) - 128
//                 val vVal = (vBytes[uvIdx].toInt() and 0xff) - 128

//                 // YUV to RGB conversion
//                 var r = (yVal + 1.402f * vVal).coerceIn(0f, 255f)
//                 var g = (yVal - 0.344136f * uVal - 0.714136f * vVal).coerceIn(0f, 255f)
//                 var b = (yVal + 1.772f * uVal).coerceIn(0f, 255f)

//                 // Normalize 0..1
//                 val outX = padX + i
//                 val outY = padY + j
//                 val idx = outY * INPUT_SIZE + outX

//                 tensor[idx] = r / 255f
//                 tensor[idx + channelSize] = g / 255f
//                 tensor[idx + channelSize * 2] = b / 255f
//             }
//         }
//         return tensor
//     }
// }
