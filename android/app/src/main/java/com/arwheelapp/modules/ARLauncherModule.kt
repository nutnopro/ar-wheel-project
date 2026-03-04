package com.arwheelapp.modules

import android.content.Intent
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise

class ARLauncherModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "ARLauncher"

    @ReactMethod
    fun openARActivity(initialModelPath: String, modelPathsJson: String, promise: Promise) {
        val activity = currentActivity
        if (activity != null) {
            try {
                val intent = Intent(activity, ARActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("initialModelPath", initialModelPath)
                    putExtra("modelPathsJson", modelPathsJson)
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
}