package com.arwheelapp.modules

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import android.view.View
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.ar.core.Session
import com.google.ar.core.Config
import io.github.sceneview.ar.ARSceneView

class ARActivity : ComponentActivity() {
    private val arSceneView: ARSceneView by lazy { ARSceneView(this) }
    private val onnxOverlayView: OnnxOverlayView by lazy { OnnxOverlayView(this) }
    private val arRendering: ARRendering by lazy { ARRendering(this, onnxOverlayView) }
    private val rootLayout: FrameLayout by lazy { FrameLayout(this) }

    private val TAG = "ARActivity: "

    enum class ARMode {
        MARKER_BASED,
        MARKERLESS
    }
    private var currentMode: ARMode = ARMode.MARKERLESS

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Log.d(TAG, "Camera permission callback: granted = $isGranted")
        if (isGranted) {
            ensureARIsReady()
        } else {
            // Log.d(TAG, "Camera permission denied by user")
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Log.d(TAG, "onCreate: Activity started")
        initViews()
    }

    private fun initViews() {
        // Log.d(TAG, "initViews: Initializing views and callbacks")
        arSceneView.arCore.cameraPermissionLauncher = cameraPermissionLauncher

        arSceneView.onSessionCreated = { session ->
            // Log.d(TAG, "onSessionCreated: Callback triggered")
            if (session != null) {
                try {
                    // Log.d(TAG, "onSessionCreated: Session available, starting setup")
                    setupAR(session)
                    arRendering.setupMarkerDatabase(session)
                    // Log.d(TAG, "onSessionCreated: AR Session and Database configured successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting up marker db/config in onSessionCreated", e)
                }
            }
			// else {
            //     Log.d(TAG, "onSessionCreated: Session is NULL!")
            // }
        }

        rootLayout.addView(arSceneView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        rootLayout.addView(onnxOverlayView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        setupUIControls(rootLayout)

        setContentView(rootLayout)
        // Log.d(TAG, "initViews: ContentView set")
    }

    private fun setupUIControls(rootLayout: FrameLayout) {
		// *ui container
        val controlPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = 120  
                marginStart = 40
            }
            layoutParams = params
        }

        val overlayToggle = Button(this).apply {
            text = "Toggle Overlay"
            setOnClickListener {
                val newVisibility = if (onnxOverlayView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                onnxOverlayView.visibility = newVisibility
                // Log.d(TAG, "UI: Overlay toggled to ${if (newVisibility == View.VISIBLE) "VISIBLE" else "GONE"}")
            }
        }
        
        val itemParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 30
        }

        fun createModeButton(text: String, isActive: Boolean): TextView {
            return TextView(this).apply {
                this.text = text
                setTextColor(Color.WHITE)
                setPadding(40, 20, 40, 20)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 30f
                    setColor(if (isActive) Color.parseColor("#1921C2FF") else Color.parseColor("#0C1171FF"))
                }
                elevation = if (isActive) 8f else 2f
            }
        }

        fun updateButtonState(active: TextView, inactive: TextView) {
            active.background = GradientDrawable().apply {
                cornerRadius = 30f; setColor(Color.parseColor("#080D71FF"))
            }
            inactive.background = GradientDrawable().apply {
                cornerRadius = 30f; setColor(Color.parseColor("#15173DFF"))
            }
        }

        val btnMarkerless = createModeButton("Markerless", true)
        val btnMarkerBased = createModeButton("Marker-based", false)

        btnMarkerless.setOnClickListener {
            if (currentMode != ARMode.MARKERLESS) {
                // Log.d(TAG, "UI: Switched to MARKERLESS mode")
                currentMode = ARMode.MARKERLESS
                updateButtonState(btnMarkerless, btnMarkerBased)
            }
        }

        btnMarkerBased.setOnClickListener {
            if (currentMode != ARMode.MARKER_BASED) {
                // Log.d(TAG, "UI: Switched to MARKER_BASED mode")
                currentMode = ARMode.MARKER_BASED
                updateButtonState(btnMarkerBased, btnMarkerless)
            }
        }

        controlPanel.addView(overlayToggle, itemParams)
        controlPanel.addView(btnMarkerless, itemParams)
        controlPanel.addView(btnMarkerBased, itemParams)

        rootLayout.addView(controlPanel)

        val backButton = Button(this).apply {
            text = "Back to Home"
            setOnClickListener {
                // Log.d(TAG, "UI: Back button clicked")
                finish()
            }
        }
        val backButtonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, 
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            val marginBottom = (resources.displayMetrics.heightPixels * 0.1f).toInt()
            setMargins(0, 0, 0, marginBottom)
        }
        rootLayout.addView(backButton, backButtonParams)
    }

    private fun setupAR(session: Session) {
        // Log.d(TAG, "setupAR: Configuring session...")
        val config = Config(session).apply {
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
            focusMode = Config.FocusMode.AUTO
            if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                depthMode = Config.DepthMode.AUTOMATIC
                // Log.d(TAG, "setupAR: DepthMode AUTOMATIC supported and enabled")
            }
        }
        session.configure(config)
        // Log.d(TAG, "setupAR: Configuration applied")
    }

    private fun ensureARIsReady() {
        val permission = android.Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // Log.d(TAG, "ensureARIsReady: Permission not granted, launching request")
            cameraPermissionLauncher.launch(permission)
            return
        }

        try {
            // Log.d(TAG, "ensureARIsReady: Permission granted, calling startAR()")
            startAR()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AR", e)
        }
    }

    private fun startAR() {
        // Log.d(TAG, "startAR: Setting up onFrame callback")
        arSceneView.onFrame = ft@{ frametime ->
            if (frametime == null) return@ft

            val session = arSceneView.session

            val frame = try {
                session?.update()
            } catch (e: Exception) {
                Log.e(TAG, "onFrame: Error updating session", e)
                null
            }

            if (session != null && frame != null) {
                arRendering.render(session, arSceneView, frame, currentMode)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Log.d(TAG, "onResume called") 
        try {
            ensureARIsReady()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume", e) 
        }
    }

    override fun onPause() {
        super.onPause()
        // Log.d(TAG, "onPause called") 
    }

    override fun onDestroy() {
        super.onDestroy()
        // Log.d(TAG, "onDestroy called") 
    }
}
