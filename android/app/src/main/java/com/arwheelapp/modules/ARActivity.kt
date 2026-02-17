package com.arwheelapp.modules

import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.PixelCopy
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arwheelapp.utils.ARMode
import com.google.ar.core.Config
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Session
import io.github.sceneview.ar.ARSceneView
import java.io.OutputStream
import java.util.EnumSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

            onModelSelected = { modelPath ->
                arRendering.updateNewModel("models/$modelPath.glb")
                Log.d(TAG, "User selected model: $modelPath")
            }

            onSizeSelected = { sizeInch ->
                arRendering.updateModelSize(sizeInch)
                Log.d(TAG, "User selected size: $sizeInch")
            }
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
        val filter = CameraConfigFilter(session).apply {
            targetFps = EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_60)
        }
        val validConfigs = session.getSupportedCameraConfigs(filter)
        if (validConfigs.isNotEmpty()) {
            session.cameraConfig = validConfigs[0]
            Log.d(TAG, "ARCore configured for 60 FPS")
        } else {
            Log.w(TAG, "60 FPS not supported on this device, falling back to 30 FPS")
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
                lifecycleScope.launch(Dispatchers.IO) { saveBitmapToGallery(bitmap) }
            } else {
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show()
            }
        }, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    private suspend fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "AR_IMG_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AR WHEEL")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        try {
            imageUri?.let { uri ->
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ARActivity, "Saved image to gallery", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image", e)
            imageUri?.let { resolver.delete(it, null, null) }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ARActivity, "Failed to save image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startARLoop() {
        arSceneView.onFrame = onFrame@ { _ ->
            val frame = arSceneView.frame ?: return@onFrame
            arRendering.render(arSceneView, frame, currentMode)
        }
    }
}