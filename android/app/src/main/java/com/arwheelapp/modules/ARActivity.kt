// modules/ARActivity.kt
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
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Session
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.loaders.EnvironmentLoader
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import java.util.EnumSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ARActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ARActivity"
    }

    private val arSceneView: ARSceneView by lazy { ARSceneView(this) }
    private val onnxOverlay: OnnxOverlayView by lazy { OnnxOverlayView(this) }
    private val rootLayout: FrameLayout by lazy { FrameLayout(this) }
    private val arRendering: ARRendering by lazy { ARRendering(this, onnxOverlay, arSceneView, lifecycleScope) }
    private val uiManager: ARUIManager by lazy { ARUIManager(this, rootLayout, onnxOverlay, lifecycleScope) }
    private val environmentLoader: EnvironmentLoader by lazy { EnvironmentLoader(arSceneView.engine, arSceneView.context) }

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

    // ── Init ──────────────────────────────────────────────────────────────────
    private fun initViews() {
        arSceneView.onSessionCreated = { configureARSession(it) }
        arSceneView.planeRenderer.isVisible = false
        setupLighting()
        val mp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        rootLayout.addView(arSceneView, mp)
        rootLayout.addView(onnxOverlay, mp)

        uiManager.setupInterface()
        wireCallbacks()
        setNudgeListeners()
        setInitialModel()
        setContentView(rootLayout)
    }

    private fun setupLighting() {
        lifecycleScope.launch {
            try {
                arSceneView.environment = environmentLoader.createHDREnvironment("environments/studio_lighting.hdr")!!
                Log.d(TAG, "Environment loaded ✅")
            } catch (e: Exception) { Log.e(TAG, "Failed to load environment", e) }

            arSceneView.addChildNode(
                LightNode(arSceneView.engine, com.google.android.filament.EntityManager.get().create())
                .apply {
                    intensity = 30_000f
                    color = dev.romainguy.kotlin.math.Float4(1.0f, 1.0f, 1.0f, 1.0f)
                    rotation = Rotation(x = -45f, y = 45f, z = 0f)
                }
            )
        }
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────
    private fun wireCallbacks() {
        // ── UI → AR ──────────────────────────────────────────────────────────
        uiManager.apply {
            onBackClicked = { finish() }
            onModeSelected = { mode -> currentMode = mode }
            onCaptureClicked = { takePhoto() }
            onModelSelected = { path -> arRendering.updateNewModel("$path.glb") }
            onSizeSelected = { inch -> arRendering.updateModelSize(inch) }

            onNudge = { editMode, dir -> arRendering.nudgeModel(editMode, dir) }
            onZSliderChanged = { editMode, value -> arRendering.updateZAxis(editMode, value) }
            onAdjustConfirm = { arRendering.finishAdjusting() }
            onAdjustCancel = { arRendering.cancelAdjusting() }
        }
    }

    private fun setNudgeListeners() {
        arSceneView.setOnGestureListener(
            onSingleTapUp = { e, node ->
                if (node is ModelNode) {
                    arRendering.startNudging(node.parent!!)
                    arRendering.onShowAdjustmentUI = { show ->
                        mainHandler.post { uiManager.showAdjustmentPanel(show) }
                    }
                }
            }
        )
    }

    private fun setInitialModel() {
        val pathStr = intent.getStringExtra("initialModelPath")
        val path = if (pathStr.isNullOrEmpty()) "models/default.glb" else pathStr
        arRendering.modelPath = path

        val jsonStr = intent.getStringExtra("modelPathsJson")
        val json = jsonStr?.takeIf { it.isNotEmpty() } ?: "[]"

        uiManager.setModelsFromJson(json)
    }

    // ── AR Session ────────────────────────────────────────────────────────────
    private fun configureARSession(session: Session) {
        try {
            configureCameraFPS(session)
            session.configure(session.config.apply {
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                focusMode = Config.FocusMode.AUTO
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
                    depthMode = Config.DepthMode.AUTOMATIC
            })
            val markerSizeCm = intent.getDoubleExtra("markerSize", 15.0)
            val markerSizeMeters = (markerSizeCm / 100.0).toFloat()
            arRendering.setupMarkerDatabase(session, markerSizeMeters)
        } catch (e: Exception) { Log.e(TAG, "AR session config error", e) }
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
        } else Log.w(TAG, "60 FPS not supported")
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