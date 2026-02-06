package com.arwheelapp.modules

import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.CameraConfig
import com.google.ar.core.Session
import com.google.ar.core.Config
import java.util.EnumSet
import io.github.sceneview.ar.ARSceneView
import com.arwheelapp.utils.ARMode

class ARActivity : ComponentActivity() {
    private val arSceneView: ARSceneView by lazy { ARSceneView(this) }
    private val onnxOverlayView: OnnxOverlayView by lazy { OnnxOverlayView(this) }
    private val rootLayout: FrameLayout by lazy { FrameLayout(this) }

    private val arRendering: ARRendering by lazy { ARRendering(this, onnxOverlayView, arSceneView) }
    private val uiManager: ARUIManager by lazy { ARUIManager(this, rootLayout, onnxOverlayView) }

    private val TAG = "ARActivity: "
    private var currentMode: ARMode = ARMode.MARKERLESS

    // *Permission Handling
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkPermissionAndStartAR()
        } else {
            Toast.makeText(this, "Camera permission is needed for AR", Toast.LENGTH_LONG).show()
        }
    }

    // *Lifecycle Methods
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initViews()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndStartAR()
        uiManager.onResume()
    }

    override fun onPause() {
        super.onPause()
        uiManager.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    // *Initialization
    private fun initViews() {
        // arSceneView.arCore.cameraPermissionLauncher = cameraPermissionLauncher

        arSceneView.onSessionCreated = { session ->
            setupARSession(session)
        }

        arSceneView.planeRenderer.isVisible = false

        rootLayout.addView(arSceneView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        rootLayout.addView(onnxOverlayView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        uiManager.setupInterface()
        setupUICallbacks()

        setContentView(rootLayout)
    }

    private fun setupUICallbacks() {
        uiManager.onBackClicked = {
            finish()
        }

        uiManager.onModeSelected = { mode ->
            currentMode = mode
        }

        uiManager.onCaptureClicked = {
            // TODO: ใส่ Logic ถ่ายรูปตรงนี้

            // arSceneView.startMirroringToSurface(...)

            takePhoto()
        }

        uiManager.onSizeSelected = { sizeInch ->
            // TODO: ส่งค่า size ไป update ใน arRendering
            // arRendering.updateWheelSize(sizeInch) 
            Log.d(TAG, "User selected size: $sizeInch")

            // arRendering.updateWheelSize(sizeInch)
        }

        uiManager.onModelSelected = { modelName ->
            // TODO: ส่งค่า model path ไปเปลี่ยนโมเดล
            // arRendering.changeModel("models/$modelName.glb")
            Log.d(TAG, "User selected model: $modelName")

            // val fileName = when(modelName) {
            //     "Wheel Type A" -> "wheel_a.glb"
            //     "Wheel Type B" -> "wheel_b.glb" 
            //     "Offroad" -> "offroad_wheel.glb"
            //     else -> "wheel_default.glb"
            // }

            // arRendering.loadModel(fileName)
        }
    }

    private fun setupARSession(session: Session) {
        try {
            configureSessionFor60FPS(session)

            val config = Config(session).apply {
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                focusMode = Config.FocusMode.AUTO

                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    depthMode = Config.DepthMode.AUTOMATIC
                }
            }
            session.configure(config)
            arRendering.setupMarkerDatabase(session)
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring AR Session", e)
        }
    }

    private fun configureSessionFor60FPS(session: Session) {
        val filter = CameraConfigFilter(session)
        filter.targetFps = EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_60)

        val validConfigs = session.getSupportedCameraConfigs(filter)
        if (validConfigs.isNotEmpty()) {
            session.cameraConfig = validConfigs[0]
            Log.d(TAG, "ARCore configured for 60 FPS")
        } else {
            Log.w(TAG, "60 FPS not supported on this device, falling back to 30 FPS")
        }
    }

    private fun checkPermissionAndStartAR() {
        val permission = android.Manifest.permission.CAMERA
        
        if (ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                startARLoop()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AR Loop", e)
            }
        } else {
            cameraPermissionLauncher.launch(permission)
        }
    }

    // TODO: Implement actual photo capture logic
    private fun takePhoto() {
        // val bitmap = arSceneView.bitmap // sceneview function (อาจต้องปรับตาม version lib)
        Toast.makeText(this, "Photo Captured! (Mock)", Toast.LENGTH_SHORT).show()
        // Save bitmap logic here...
    }

    private fun startARLoop() {
        arSceneView.onFrame = onFrame@{ _ ->
            val frame = arSceneView.frame ?: return@onFrame
            arRendering.render(arSceneView, frame, currentMode)
        }
    }
}
