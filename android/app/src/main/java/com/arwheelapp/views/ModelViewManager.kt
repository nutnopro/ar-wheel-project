package com.arwheelapp.views

import io.github.sceneview.SceneView
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.bridge.ReactApplicationContext
import android.graphics.Color

class ModelViewManager(private val context: ReactApplicationContext) : SimpleViewManager<SceneView>() {
    override fun getName() = "ModelView"

    override fun createViewInstance(reactContext: ThemedReactContext): SceneView {
        // ใช้ SceneView ธรรมดา (ไม่ใช่ ArSceneView)
        return SceneView(reactContext).apply { setBackgroundColor = Color.Transparent.toArgb() 
            
            // โหลดโมเดล
            // (เขียน Logic รับ props 'modelSrc' มาโหลดตรงนี้)
        }
    }
    
    // ... expose props เช่น src, rotation, scale ...
}