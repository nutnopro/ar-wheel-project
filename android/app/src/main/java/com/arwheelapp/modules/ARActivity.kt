package com.arwheelapp.modules

import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arwheelapp.utils.ARMode
import com.google.ar.core.Config
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Session
import io.github.sceneview.ar.ARSceneView
import java.util.EnumSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader

class ARActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ARActivity"
    }

    private val arSceneView: ARSceneView by lazy { ARSceneView(this) }
    private val onnxOverlayView: OnnxOverlayView by lazy { OnnxOverlayView(this) }
    private val rootLayout: FrameLayout by lazy { FrameLayout(this) }
    private val arRendering: ARRendering by lazy { ARRendering(this, onnxOverlayView, arSceneView, lifecycleScope) }
    private val uiManager: ARUIManager by lazy { ARUIManager(this, rootLayout, onnxOverlayView) }

    private var currentMode: ARMode = ARMode.DEFAULT

    // Permission Handling
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) startARLoop()
        else Toast.makeText(this, "Camera permission is needed for AR", Toast.LENGTH_LONG).show()
    }

    // Lifecycle Methods
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (OpenCVLoader.initLocal()) {
            Log.i(TAG, "OpenCV loaded successfully!")
        } else {
            Log.e(TAG, "OpenCV initialization failed!")
            Toast.makeText(this, "Failed to load computer vision module", Toast.LENGTH_LONG).show()
        }
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

    // Initialization
    private fun initViews() {
        arSceneView.onSessionCreated = { setupARSession(it) }
        arSceneView.planeRenderer.isVisible = false
        val layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        rootLayout.addView(arSceneView, layoutParams)
        rootLayout.addView(onnxOverlayView, layoutParams)

        uiManager.setupInterface()
        setupUICallbacks()
        setContentView(rootLayout)
    }

    private fun setupUICallbacks() {
        uiManager.apply {
            onBackClicked = { finish() }
            onModeSelected = { currentMode = it }
            onCaptureClicked = { takePhoto() }
            onModelSelected = { modelPath -> arRendering.updateNewModel("models/$modelPath.glb") }
            onSizeSelected = { sizeInch -> arRendering.updateModelSize(sizeInch) }
        }
    }

    private fun setupARSession(session: Session) {
        try {
            configureCameraSession(session)
            session.configure(session.config.apply {
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                focusMode = Config.FocusMode.AUTO
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    depthMode = Config.DepthMode.AUTOMATIC
                }
            })
            arRendering.setupMarkerDatabase(session)
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring AR Session", e)
        }
    }

    private fun configureCameraSession(session: Session) {
        val filter = CameraConfigFilter(session).apply {
            targetFps = EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_60)
            depthSensorUsage = EnumSet.of(CameraConfig.DepthSensorUsage.REQUIRE_AND_USE)
        }
        val validConfigs = session.getSupportedCameraConfigs(filter)
        if (validConfigs.isNotEmpty()) {
            session.cameraConfig = validConfigs[0]
            Log.d(TAG, "ARCore configured for 60 FPS")
        } else {
            Log.w(TAG, "60 FPS not supported, falling back to 30 FPS")
        }
    }

    private fun checkPermissionAndStartAR() {
        val permission = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            startARLoop()
        } else {
            cameraPermissionLauncher.launch(permission)
        }
    }

    private fun takePhoto() {
        val bitmap = Bitmap.createBitmap(arSceneView.width, arSceneView.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(arSceneView, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                lifecycleScope.launch {
                    val isSuccess = saveBitmapToGallery(bitmap)
                    Toast.makeText(this@ARActivity, if (isSuccess) "Saved image" else "Failed to save", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        }, Handler(Looper.getMainLooper()))
    }

    private suspend fun saveBitmapToGallery(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val filename = "AR_WHEEL_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AR WHEEL")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return@withContext false
        return@withContext try {
            resolver.openOutputStream(imageUri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image", e)
            resolver.delete(imageUri, null, null)
            false
        }
    }

    private fun startARLoop() {
        arSceneView.onFrame = { _ ->
            arSceneView.frame?.let { frame ->
                val deviceRotation = uiManager.currentRotation
                arRendering.render(arSceneView, frame, currentMode, deviceRotation)
            }
        }
    }
}