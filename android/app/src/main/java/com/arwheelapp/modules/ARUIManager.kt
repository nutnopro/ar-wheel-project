package com.arwheelapp.modules

import android.content.Context
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Button
import com.arwheelapp.utils.ArTypes.ARMode

class ARUIManager(private val context: Context, private val rootLayout: FrameLayout, private val overlayView: View) {
    var onModeSelected: ((ARMode) -> Unit)? = null
    var onBackClicked: (() -> Unit)? = null

	private var btnModeToggle: TextView? = null
	private var currentMode: ARMode = ARMode.MARKERLESS

    fun setupInterface() {
        val controlPanel = LinearLayout(context).apply {
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

        val overlayToggle = Button(context).apply {
            text = "Toggle Overlay"
            setOnClickListener {
                overlayView.visibility = if (overlayView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }

		btnModeToggle = createToggleButton()

		updateToggleUI()

		btnModeToggle?.setOnClickListener {
            toggleMode()
        }

		val itemParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 30 }

        controlPanel.addView(overlayToggle, itemParams)
        controlPanel.addView(btnModeToggle, itemParams)

        rootLayout.addView(controlPanel)

        setupBackButton()
    }

    private fun toggleMode() {
        currentMode = if (currentMode == ARMode.MARKERLESS) {
            ARMode.MARKER_BASED
        } else {
            ARMode.MARKERLESS
        }

        updateToggleUI()

        onModeSelected?.invoke(currentMode)
    }

	private fun updateToggleUI() {
        val modeText = if (currentMode == ARMode.MARKERLESS) "Mode: Markerless" else "Mode: Marker-based"
        
        val bgDrawable = GradientDrawable().apply {
            cornerRadius = 30f
            setColor(Color.parseColor("#1921C2FF")) 
        }

        btnModeToggle?.apply {
            text = modeText
            background = bgDrawable
        }
    }
	private fun createToggleButton(): TextView {
        return TextView(context).apply {
            setTextColor(Color.WHITE)
            setPadding(40, 20, 40, 20)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            elevation = 8f
        }
    }

    private fun setupBackButton() {
        val backButton = Button(context).apply {
            text = "Back to Home"
            setOnClickListener { onBackClicked?.invoke() }
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            val marginBottom = (context.resources.displayMetrics.heightPixels * 0.1f).toInt()
            setMargins(0, 0, 0, marginBottom)
        }
        rootLayout.addView(backButton, params)
    }
}
