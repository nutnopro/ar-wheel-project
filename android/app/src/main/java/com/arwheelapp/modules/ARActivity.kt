package com.arwheelapp.modules

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.util.Log
// import com.google.ar.core.Frame
// import com.google.ar.core.Session
import com.google.ar.core.Config
import com.google.ar.core.AugmentedImage
import com.google.ar.core.TrackingState

import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.arcore
import com.arapp.utils.OnnxRuntimeHandler
import com.arapp.utils.FrameConverter
import com.arapp.utils.PositionHandler

class ARActivity : ComponentActivity() {

    private lateinit var arSceneView: ARSceneView
    private lateinit var onnxHandler: OnnxRuntimeHandler
    private lateinit var boxOverlay: OnnxOverlayView
    private lateinit var frameConverter: FrameConverter
    private lateinit var positionHandler: PositionHandler
    private lateinit var markerBased: ARMarkerBased
    private lateinit var markerless: ARMarkerless

    private var arSession: Session? = null
    private var isARSessionStarted = false

	enum class ARMode {
		MARKERLESS,
		MARKER_BASED
	}
	private var currentMode: ARMode = ARMode.MARKERLESS

	private const val TAG = "ARActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

		initViews()
		setupAR()

		startAR()
    }

    private fun initViews() {
        val rootLayout = FrameLayout(this)

        arSceneView.apply {
			arCore.cameraPermissionLauncher = registerForActivityResult(
				androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
			)
        }

        rootLayout.addView(
            arSceneView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        Log.d(TAG, "ARSceneView added to layout")

        val backButton = Button(this).apply {
            text = "Back to Home"
            setOnClickListener { finish() }
        }
        val buttonParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            val marginBottom = (resources.displayMetrics.heightPixels * 0.2f).toInt()
            setMargins(0, 0, 0, marginBottom)
        }
        rootLayout.addView(backButton, buttonParams)

		val modeToggle = LinearLayout(this).apply {
			orientation = LinearLayout.HORIZONTAL
			val params = FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.WRAP_CONTENT,
				FrameLayout.LayoutParams.WRAP_CONTENT
			).apply {
				gravity = Gravity.TOP or Gravity.START
				topMargin = 60
				marginStart = 30
			}
			layoutParams = params
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
					setColor(if (isActive) Color.parseColor("#FF4F4F") else Color.parseColor("#FF7B7B"))
				}
				elevation = if (isActive) 8f else 2f
			}
		}

		val btnMarkerless = createModeButton("Markerless", true)
		val btnMarkerBased = createModeButton("Marker-based", false)

		btnMarkerless.setOnClickListener {
			if (currentMode != ARMode.MARKERLESS) {
				currentMode = ARMode.MARKERLESS
				updateButtonState(btnMarkerless, btnMarkerBased)
				restartAR()
			}
		}

		btnMarkerBased.setOnClickListener {
			if (currentMode != ARMode.MARKER_BASED) {
				currentMode = ARMode.MARKER_BASED
				updateButtonState(btnMarkerless, btnMarkerBased)
				restartAR()
			}
		}

		modeToggle.addView(btnMarkerless)
		modeToggle.addView(btnMarkerBased)
		rootLayout.addView(modeToggle)

        setContentView(rootLayout)
        Log.d(TAG, "Views set")
    }

	private fun setupAR() {
		arSession = Session(this)
		val config = Config(arSession).apply {
			updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
			planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
			lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
			instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
			focusMode = Config.FocusMode.AUTO

			if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
				depthMode = Config.DepthMode.AUTOMATIC
			}
		}

		try {
			session.configure(config)
			Log.d(TAG, "AR Session configured successfully")
		} catch (e: Exception) {
			Log.e(TAG, "Failed to configure AR session: ", e)
			throw e
		}

		arSceneView.session(arSession)
		arSceneView.apply {
			lightEstimator.isEnabled = true
		}
	}

	// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    private fun startAR() {
		arSceneView.onFrame = { frame ->
			Log.d(TAG, "Frame: timestamp=${frame.timestamp}")
			
			when (currentMode) {
				ARMode.MARKERLESS -> {
					tensor = frameConverter(frame)
					detection = onnxHandler.runOnnxInference(tensor)
					pos3d = positionHandler.getPos3d(detection)
					markerless.render(pos3d)
					boxOverlay(detection)
				}
				ARMode.MARKER_BASED -> {
					markerBased.render(frame)
				}
			}
		}
    }

	private fun restartAR() {
		try {
			arSceneView.session.pause()
			startAR()
			arSceneView.session.resume()
		} catch (e: Exception) {
			Log.e(TAG, "Failed to restart AR session", e)
		}
	}

	private fun updateButtonState(active: TextView, inactive: TextView) {
		active.background = GradientDrawable().apply {
			cornerRadius = 30f; setColor(Color.parseColor("#FF4F4F"))
		}
		inactive.background = GradientDrawable().apply {
			cornerRadius = 30f; setColor(Color.parseColor("#FF7B7B"))
		}
	}

    override fun onResume() {
        super.onResume()

		if (arSceneView.session == null) {
			setupAR()
		}

		try {
			arSceneView.session.resume()
		} catch (e: Exception) {
			Log.e(TAG, "Failed to resume ArSceneView: ", e)
		}
    }

    override fun onPause() {
		super.onPause()
		try {
			arSceneView.session.pause()
		} catch (e: Exception) {
			Log.e(TAG, "Failed to pause ArSceneView: ", e)
		}
    }

    override fun onDestroy() {
		arSession?.close()
		arSession = null
		try {
			arSceneView.session.destroy()
		} catch (e: Exception) {
			Log.e(TAG, "Failed to destroy ArSceneView: ", e)
		}
		super.onDestroy()
    }
}
