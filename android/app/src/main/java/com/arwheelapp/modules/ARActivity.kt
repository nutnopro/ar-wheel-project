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
    private val onnxOverlay: OnnxOverlayView by lazy { OnnxOverlayView(this) }
    private val rootLayout: FrameLayout by lazy { FrameLayout(this) }
    private val arRendering: ARRendering by lazy { ARRendering(this, onnxOverlay, arSceneView, lifecycleScope) }
    private val uiManager: ARUIManager by lazy { ARUIManager(this, rootLayout, onnxOverlay, lifecycleScope) }

    private var currentMode: ARMode = ARMode.DEFAULT
    private val mainHandler = Handler(Looper.getMainLooper())

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startARLoop()
        else Toast.makeText(this, "Camera permission required for AR", Toast.LENGTH_LONG).show()
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (OpenCVLoader.initLocal()) Log.i(TAG, "OpenCV loaded ✅")
        else {
            Log.e(TAG, "OpenCV init failed!")
            Toast.makeText(this, "Failed to load computer vision module", Toast.LENGTH_LONG).show()
        }
        initViews()
    }

    override fun onResume() {
        super.onResume()
        checkPermAndStartAR()
        uiManager.onResume()
    }

    override fun onPause() {
        super.onPause()
        uiManager.onPause()
    }

    // ── Views ─────────────────────────────────────────────────────────────────
    private fun initViews() {
        arSceneView.onSessionCreated = { configureARSession(it) }
        arSceneView.planeRenderer.isVisible = false
        val matchParent = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        rootLayout.addView(arSceneView, matchParent)
        rootLayout.addView(onnxOverlay, matchParent)

        uiManager.setupInterface()
        wireCallbacks()
        setContentView(rootLayout)
    }

    // ── Callback wiring ───────────────────────────────────────────────────────
    private fun wireCallbacks() {
        // ── UI → AR ──────────────────────────────────────────────────────────
        uiManager.apply {
            onBackClicked = { finish() }
            onModeSelected = { mode -> currentMode = mode }
            onCaptureClicked = { takePhoto() }
            onModelSelected = { path -> arRendering.updateNewModel("models/$path.glb") }
            onSizeSelected = { inch -> arRendering.updateModelSize(inch) }

            // Nudge: called from UI thread (button hold) → safe to forward directly
            onNudge = { dx, dy, dz -> arRendering.nudgeSelectedModel(dx, dy, dz) }
            // Confirm: bake offset into new anchor
            onAdjustConfirm = { arRendering.finishAdjusting() }

            // Cancel: discard offset, hide panel
            onAdjustCancel = { arRendering.cancelAdjusting() }
        }

        // ── AR → UI ───────────────────────────────────────────────────────────
        // onShowAdjustmentUI is triggered from onSingleTapConfirmed (main thread)
        // OR from mainHandler.post() in ARRendering — always main thread ✅
        arRendering.onShowAdjustmentUI = { show ->
            mainHandler.post { uiManager.showAdjustmentPanel(show) }
        }
    }

    // ── AR Session ────────────────────────────────────────────────────────────
    private fun configureARSession(session: Session) {
        try {
            configureCameraFPS(session)
            session.configure(session.config.apply {
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                focusMode = Config.FocusMode.AUTO
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
                    depthMode = Config.DepthMode.AUTOMATIC
            })
            arRendering.setupMarkerDatabase(session)
        } catch (e: Exception) {
            Log.e(TAG, "R session config error", e)
        }
    }

    private fun configureCameraFPS(session: Session) {
        val filter = CameraConfigFilter(session).apply {
            targetFps = EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_60)
            depthSensorUsage = EnumSet.of(CameraConfig.DepthSensorUsage.REQUIRE_AND_USE)
        }
        val configs = session.getSupportedCameraConfigs(filter)
        if (configs.isNotEmpty()) {
            session.cameraConfig = configs[0]
            Log.d(TAG, "60 FPS configured ✅")
        } else {
            Log.w(TAG, "60 FPS not supported, using default")
        }
    }

    // ── Permission & render loop ──────────────────────────────────────────────
    private fun checkPermAndStartAR() {
        val perm = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED)
            startARLoop()
        else cameraPermLauncher.launch(perm)
    }

    private fun startARLoop() {
        arSceneView.onFrame = { _ ->
            arSceneView.frame?.let { frame ->
                arRendering.render(arSceneView, frame, currentMode, uiManager.currentRotation)
            }
        }
    }

    // ── Photo capture ─────────────────────────────────────────────────────────
    private fun takePhoto() {
        val bmp = Bitmap.createBitmap(arSceneView.width, arSceneView.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(arSceneView, bmp, { result ->
            if (result == PixelCopy.SUCCESS) {
                lifecycleScope.launch {
                    val ok = saveBitmap(bmp)
                    Toast.makeText(
                        this@ARActivity,
                        if (ok) "Photo saved!" else "Save failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else Toast.makeText(this, "Capture failed", Toast.LENGTH_SHORT).show()
        }, Handler(Looper.getMainLooper()))
    }

    private suspend fun saveBitmap(bmp: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val name = "AR_WHEEL_${System.currentTimeMillis()}.jpg"
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AR WHEEL")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            ?: return@withContext false
        return@withContext try {
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cv.clear()
                cv.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, cv, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Save failed", e)
            contentResolver.delete(uri, null, null)
            false
        }
    }
}