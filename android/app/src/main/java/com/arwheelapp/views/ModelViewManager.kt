package com.arwheelapp.views

import io.github.sceneview.SceneView

class ModelViewManager(private val context: ReactApplicationContext) : SimpleViewManager<SceneView>() {
    override fun getName() = "ModelView"

    override fun createViewInstance(reactContext: ThemedReactContext): SceneView {
        // ใช้ SceneView ธรรมดา (ไม่ใช่ ArSceneView)
        return SceneView(reactContext).apply {
            // ตั้งค่าพื้นหลัง (ใส หรือ สี)
            backgroundColor = Color.Transparent.toArgb() 
            
            // โหลดโมเดล
            // (เขียน Logic รับ props 'modelSrc' มาโหลดตรงนี้)
        }
    }
    
    // ... expose props เช่น src, rotation, scale ...
}