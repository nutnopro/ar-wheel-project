package com.arwheelapp.modules

import android.content.Intent
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.Arguments
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream

class ARLauncherModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "ARLauncher"

    @ReactMethod
    fun openARActivity(initialModelPath: String, modelPathsJson: String, markerSize: Double, promise: Promise) {
        val activity = currentActivity
        if (activity != null) {
            try {
                val intent = Intent().apply {
                    setClassName(reactApplicationContext, "com.arwheelapp.modules.ARActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("initialModelPath", initialModelPath)
                    putExtra("modelPathsJson", modelPathsJson)
                    putExtra("markerSize", markerSize)
                }
                activity.startActivity(intent)
                promise.resolve(true)
            } catch (e: Exception) {
                promise.reject("AR_OPEN_FAILED", e)
            }
        } else {
            promise.reject("NO_CURRENT_ACTIVITY", "Current activity is null")
        }
    }

    @ReactMethod
    fun getMarkerImages(promise: Promise) {
        try {
            val context = reactApplicationContext
            val assets = context.assets.list("markers")
            val markersArray = Arguments.createArray()

            assets?.filter { it.endsWith(".jpg", true) || it.endsWith(".png", true) }
                ?.forEach { fn ->
                val map = Arguments.createMap()
                map.putString("name", fn)
                
                // Read as base64 to display in RN
                context.assets.open("markers/$fn").use { s ->
                    val bytes = s.readBytes()
                    val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)
                    map.putString("base64", "data:image/jpeg;base64,$base64")
                }
                markersArray.pushMap(map)
            }
            promise.resolve(markersArray)
        } catch (e: Exception) {
            promise.reject("FETCH_MARKERS_FAILED", e)
        }
    }

    @ReactMethod
    fun saveMarkerImage(filename: String, promise: Promise) {
        val context = reactApplicationContext
        try {
            val bmp = context.assets.open("markers/$filename").use { s ->
                BitmapFactory.decodeStream(s)
            }
            
            val cr = context.contentResolver
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AR WHEEL Markers")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            
            val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            if (uri != null) {
                cr.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cv.clear()
                    cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    cr.update(uri, cv, null, null)
                }
                promise.resolve("Saved successfully to Gallery")
            } else {
                promise.reject("SAVE_FAILED", "Could not create media store entry")
            }
        } catch (e: Exception) {
            promise.reject("SAVE_FAILED", e)
        }
    }
}