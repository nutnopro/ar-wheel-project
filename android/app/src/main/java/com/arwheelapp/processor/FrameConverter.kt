package com.arwheelapp.processor

import android.graphics.*
import android.media.Image
import android.util.Log
import com.google.ar.core.Frame
import java.io.ByteArrayOutputStream

class FrameConverter {
    private val TAG = "FrameConverter: "
    private val INPUT_SIZE = 320  // *YOLO input size

    private var nv21Buffer: ByteArray? = null
    private var resizedBitmap: Bitmap? = null
    private val matrix = Matrix() 
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val yuvStream = ByteArrayOutputStream()

    // *ARCore Frame -> FloatArray Tensor
    fun convertFrameToTensor(frame: Frame): FloatArray {
        val image = try {
            frame.acquireCameraImage()
        } catch {
            null
        } ?: return FloatArray(3 * INPUT_SIZE * INPUT_SIZE)

        try {
            val imageWidth = image.width
            val imageHeight = image.height
            val requiredSize = imageWidth * imageHeight * 3 / 2
            
            if (nv21Buffer == null || nv21Buffer!!.size != requiredSize) {
                nv21Buffer = ByteArray(requiredSize)
            }

            // 2. ส่ง Buffer เดิมเข้าไปเขียนทับ
            val rawBitmap = yuvToBitmap(image, nv21Buffer!!)

            // 3. ใช้ Bitmap ปลายทางตัวเดิม (320x320) วาดทับลงไป
            if (resizedBitmap == null || resizedBitmap?.width != INPUT_SIZE || resizedBitmap?.height != INPUT_SIZE) {
                resizedBitmap?.recycle()
                resizedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
            }

            // Logic การหมุน/ย่อขยาย
            processBitmap(rawBitmap, 90f, resizedBitmap!!)

            // *สำคัญ: rawBitmap ที่ได้จาก decodeByteArray มักต้อง recycle ทิ้งเองถ้าไม่ใช้แล้ว
            // เพราะมันสร้างใหม่ทุกรอบ (แก้จุดนี้ยากถ้าไม่ใช้ inBitmap)
            rawBitmap.recycle() 

            return bitmapToFloatArray(resizedBitmap!!)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting frame", e)
            return FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        } finally {
            image.close()
        }
    }

    private fun yuvToBitmap(image: Image, outNv21: ByteArray): Bitmap {
        // Logic การดึงข้อมูล YUV ลง Array (NV21 Format)
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        // Copy Y
        yBuffer.get(outNv21, 0, ySize)

        // Copy UV (Interleaved)
        // หมายเหตุ: เราต้องจัดเรียง V และ U สลับกันเพื่อให้ได้ NV21 ที่ถูกต้อง
        val uvHeight = image.height / 2
        val uvWidth = image.width / 2
        val uPixelStride = image.planes[1].pixelStride
        val vPixelStride = image.planes[2].pixelStride
        val uRowStride = image.planes[1].rowStride
        val vRowStride = image.planes[2].rowStride

        var pos = ySize
        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val vIndex = row * vRowStride + col * vPixelStride
                val uIndex = row * uRowStride + col * uPixelStride

                // NV21 ต้องเรียงเป็น V แล้วตามด้วย U
                outNv21[pos++] = vBuffer.get(vIndex)
                outNv21[pos++] = uBuffer.get(uIndex)
            }
        }

        // แปลง NV21 Bytes -> JPEG -> Bitmap
        yuvStream.reset() // ล้าง Stream เดิมเพื่อใช้ใหม่
        val yuvImage = YuvImage(outNv21, ImageFormat.NV21, image.width, image.height, null)

        // Quality 80 ก็พอสำหรับ AI (ช่วยให้เร็วขึ้น)
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, yuvStream)

        val imageBytes = yuvStream.toByteArray()
        // decodeByteArray สร้าง Bitmap ใหม่ทุกครั้ง (นี่คือเหตุผลที่เราต้อง recycle rawBitmap ข้างบน)
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun processBitmap(source: Bitmap, rotateDegrees: Float, destBitmap: Bitmap) {
        val canvas = Canvas(destBitmap)
        // ล้างภาพเก่าทิ้งด้วยสีดำ (ป้องกันภาพซ้อน)
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)
        canvas.drawColor(Color.BLACK) 

        matrix.reset()

        // 1. คำนวณ Scale
        val isPortrait = rotateDegrees % 180 != 0f
        val rotatedSourceWidth = if (isPortrait) source.height else source.width
        val rotatedSourceHeight = if (isPortrait) source.width else source.height

        // maxOf = ให้ภาพเต็มขอบ (Aspect Fill) - อาจโดนตัดบางส่วน
        // minOf = ให้เห็นภาพครบ (Aspect Fit) - มีขอบดำ
        val scale = INPUT_SIZE.toFloat() / maxOf(rotatedSourceWidth, rotatedSourceHeight)

        // 2. Matrix Transformation Stack (อ่านจากล่างขึ้นบน)

        // D: ย้ายกลับไปที่กึ่งกลางของภาพปลายทาง (160, 160)
        matrix.postTranslate(INPUT_SIZE / 2f, INPUT_SIZE / 2f)

        // C: ขยายภาพ
        matrix.postScale(scale, scale)

        // B: หมุนภาพ
        matrix.postRotate(rotateDegrees)

        // A: ย้ายจุดกึ่งกลางภาพต้นฉบับมาที่ (0,0) เพื่อให้หมุนรอบจุดศูนย์กลาง
        matrix.postTranslate(-source.width / 2f, -source.height / 2f)

        // 3. วาดลง Bitmap ปลายทาง
        canvas.drawBitmap(source, matrix, paint)
    }

    private fun bitmapToFloatArray(bitmap: Bitmap): FloatArray {
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val floatArray = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val channelSize = INPUT_SIZE * INPUT_SIZE

        for (i in 0 until INPUT_SIZE * INPUT_SIZE) {
            val value = intValues[i]
            // Extract & Normalize (0..255 -> 0..1)
            floatArray[i] = ((value shr 16 and 0xFF) / 255.0f)              // R
            floatArray[i + channelSize] = ((value shr 8 and 0xFF) / 255.0f) // G
            floatArray[i + channelSize * 2] = ((value and 0xFF) / 255.0f)   // B
        }
        return floatArray
    }






    // private fun imageToLetterboxedTensor(image: Image): FloatArray {
    //     val width = image.width
    //     val height = image.height

    //     val yBuffer = image.planes[0].buffer
    //     val uBuffer = image.planes[1].buffer
    //     val vBuffer = image.planes[2].buffer

    //     val yRowStride = image.planes[0].rowStride
    //     val uvRowStride = image.planes[1].rowStride
    //     val uvPixelStride = image.planes[1].pixelStride

    //     val rotatedWidth = height 
    //     val rotatedHeight = width

    //     val tensor = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
    //     val scale = min(INPUT_SIZE.toFloat() / rotatedWidth, INPUT_SIZE.toFloat() / rotatedHeight)

    //     val newW = (rotatedWidth * scale).toInt()
    //     val newH = (rotatedHeight * scale).toInt()
    //     val padX = (INPUT_SIZE - newW) / 2
    //     val padY = (INPUT_SIZE - newH) / 2

    //     val yBytes = ByteArray(yBuffer.remaining())
    //     yBuffer.get(yBytes)
    //     val uBytes = ByteArray(uBuffer.remaining())
    //     uBuffer.get(uBytes)
    //     val vBytes = ByteArray(vBuffer.remaining())
    //     vBuffer.get(vBytes)

    //     val channelSize = INPUT_SIZE * INPUT_SIZE

    //     // Loop only the pixels in the center of the image (scale + pad)
    //     for (j in 0 until newH) {
    //         for (i in 0 until newW) {
    //             val logicX = (i / scale).toInt()
    //             val logicY = (j / scale).toInt()

    //             // Rotate coordinates
    //             val srcX = logicY.coerceIn(0, width - 1)
    //             val srcY = (height - 1 - logicX).coerceIn(0, height - 1)

    //             // YUV indices calculation
    //             val yIdx = yRowStride * srcY + srcX
    //             val uvIdx = (srcY / 2) * uvRowStride + (srcX / 2) * uvPixelStride

    //             val yVal = (yBytes[yIdx].toInt() and 0xff)
    //             val uVal = (uBytes[uvIdx].toInt() and 0xff) - 128
    //             val vVal = (vBytes[uvIdx].toInt() and 0xff) - 128

    //             // YUV to RGB conversion
    //             var r = (yVal + 1.402f * vVal).coerceIn(0f, 255f)
    //             var g = (yVal - 0.344136f * uVal - 0.714136f * vVal).coerceIn(0f, 255f)
    //             var b = (yVal + 1.772f * uVal).coerceIn(0f, 255f)

    //             // Normalize 0..1
    //             val outX = padX + i
    //             val outY = padY + j
    //             val idx = outY * INPUT_SIZE + outX

    //             tensor[idx] = r / 255f
    //             tensor[idx + channelSize] = g / 255f
    //             tensor[idx + channelSize * 2] = b / 255f
    //         }
    //     }
    //     return tensor
    // }
}
