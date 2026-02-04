package com.arwheelapp.modules

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.OrientationEventListener
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.arwheelapp.utils.ARMode
import kotlin.math.abs

class ARUIManager(private val context: Context, private val rootLayout: FrameLayout, private val overlayView: View) {

    // Callbacks
    var onModeSelected: ((ARMode) -> Unit)? = null
    var onBackClicked: (() -> Unit)? = null
    var onCaptureClicked: (() -> Unit)? = null
    var onModelSelected: ((String) -> Unit)? = null
    var onSizeSelected: ((Float) -> Unit)? = null

    // UI Elements
    private var btnModeToggle: TextView? = null
    private var navContainer: LinearLayout? = null
    private var controlsContainer: LinearLayout? = null
    private var selectionContainer: LinearLayout? = null

    private var currentMode: ARMode = ARMode.MARKERLESS
    private var currentRotation = 0 // 0, 90, 180, 270

    // Listener for rotation
    private var orientationListener: OrientationEventListener? = null

    // Mock Data
    private var modelList = listOf("Wheel Type A", "Wheel Type B", "Wheel Type C", "Offroad")
    private var sizeList = listOf(15, 16, 17, 18, 19, 20, 21, 22)

    fun setupInterface() {

        // Setup Containers
        setupNavPanel()      // Top (Portrait) / Left (Landscape)
        setupDebugPanel()    // Overlay toggle
        setupControlsPanel() // Bottom (Portrait) / Right (Landscape)
        setupSelectionMenu() // Popup menu

        // Start Orientation Listener
        setupOrientationListener()
    }

    private fun setupNavPanel() {
        navContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = 40
                marginStart = 40
            }
        }

        // Back Button
        val btnBack = createIconButton("❮").apply {
            setOnClickListener { onBackClicked?.invoke() }
        }

        // Mode Button
        btnModeToggle = createIconButton("◱").apply {
            textSize = 20f
            setOnClickListener { toggleMode() }
        }
        updateModeIcon()

        navContainer?.addView(btnBack)
        navContainer?.addView(createSpacer(30))
        navContainer?.addView(btnModeToggle)

        rootLayout.addView(navContainer)
    }

    private fun setupControlsPanel() {
        controlsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            weightSum = 3f 
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 60
            }
        }

        // Model Button
        val btnModel = createMenuButton("📦 Model").apply {
            setOnClickListener { showModelSelector() }
        }

        // Capture Button
        val btnCapture = createCaptureButton().apply {
            setOnClickListener { onCaptureClicked?.invoke() }
        }

        // Settings Button
        val btnSettings = createMenuButton("⚙ Size").apply {
            setOnClickListener { showSizeSelector() }
        }

        addControlItem(controlsContainer!!, btnModel)
        addControlItem(controlsContainer!!, btnCapture)
        addControlItem(controlsContainer!!, btnSettings)

        rootLayout.addView(controlsContainer)
    }

    private fun addControlItem(parent: LinearLayout, view: View) {
        val wrapper = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            addView(view)
        }
        parent.addView(wrapper)
    }

    private fun setupSelectionMenu() {
        selectionContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#AA000000"))
            visibility = View.GONE
            setPadding(30, 30, 30, 30)
            
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 250
            }
        }
        rootLayout.addView(selectionContainer)
    }

    private fun setupDebugPanel() {
        val debugContainer = LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = 20
            }
        }
        val btnDebug = Button(context).apply {
            text = "DEBUG"
            textSize = 10f
            background = createRoundDrawable(Color.parseColor("#80000000"), 20f)
            setTextColor(Color.WHITE)
            setOnClickListener {
                overlayView.visibility = if (overlayView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        debugContainer.addView(btnDebug)
        rootLayout.addView(debugContainer)
    }

    // --- Dynamic Orientation Logic ---

    private fun setupOrientationListener() {
        orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                // Snap to 0, 90, 270 (Ignore 180/Upside down mostly)
                val newRotation = when {
                    orientation in 45..135 -> 270  // Landscape Right (Reverse)
                    orientation in 225..315 -> 90  // Landscape Left
                    else -> 0                      // Portrait
                }

                if (newRotation != currentRotation) {
                    currentRotation = newRotation
                    updateLayoutForRotation(currentRotation)
                }
            }
        }
    }

    private fun updateLayoutForRotation(rotation: Int) {
        // Rotation for Views (Counter-act device rotation)
        // Phone rotated 90 (Left) -> Views need -90
        // Phone rotated 270 (Right) -> Views need 90
        val viewRotation = when (rotation) {
            90 -> -90f
            270 -> 90f
            else -> 0f
        }

        // 1. Rotate All Icons/Buttons smoothly
        rotateView(btnModeToggle, viewRotation)
        rotateView(navContainer?.getChildAt(0), viewRotation) // Back Button
        
        // Rotate Control Buttons (Model, Capture, Settings)
        for (i in 0 until (controlsContainer?.childCount ?: 0)) {
            val wrapper = controlsContainer?.getChildAt(i) as? LinearLayout
            val button = wrapper?.getChildAt(0)
            rotateView(button, viewRotation)
        }

        // 2. Adjust Containers Layout (Horizontal <-> Vertical)
        val isPortrait = rotation == 0

        // Nav Container (Top-Left)
        navContainer?.apply {
            orientation = if (isPortrait) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            // Adjust gravity/margin if needed, but Top-Left works for both usually
        }

        // Controls Container (Bottom)
        controlsContainer?.apply {
            orientation = if (isPortrait) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            
            val params = layoutParams as FrameLayout.LayoutParams
            if (isPortrait) {
                // Bar at Bottom
                params.width = FrameLayout.LayoutParams.MATCH_PARENT
                params.height = FrameLayout.LayoutParams.WRAP_CONTENT
                params.gravity = Gravity.BOTTOM
                params.marginEnd = 0
                params.bottomMargin = 60
            } else {
                // Bar at Right Side
                params.width = FrameLayout.LayoutParams.WRAP_CONTENT
                params.height = FrameLayout.LayoutParams.MATCH_PARENT
                params.gravity = Gravity.END
                params.bottomMargin = 0
                params.marginEnd = 20
            }
            layoutParams = params
        }

        // Selection Menu Position
        selectionContainer?.apply {
            // Re-orient content inside scrollview if visible
            // (Simple reset for now)
            visibility = View.GONE 
        }
    }

    private fun rotateView(view: View?, rotation: Float) {
        view?.animate()?.rotation(rotation)?.setDuration(300)?.start()
    }

    // --- LifeCycle Methods (Must be called from Activity) ---
    fun onResume() {
        orientationListener?.enable()
    }

    fun onPause() {
        orientationListener?.disable()
    }

    // --- Actions & Helpers (Keep same logic) ---

    private fun showModelSelector() {
        toggleSelectionMenu {
            val isPortrait = currentRotation == 0
            val scrollView = android.widget.ScrollView(context) // Works for vertical list
            val container = LinearLayout(context).apply { 
                orientation = if (isPortrait) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL 
            }

            // Adjust container for scrollview
            if (isPortrait) {
               // Need HorizontalScrollView for Portrait
               // But to keep code simple, let's use Flow or just LinearLayout
               // Let's swap to HorizontalScrollView for Portrait logic if needed
            }

            // Simplified: Just use a list that flows based on orientation
            modelList.forEach { modelName ->
                val item = createChipButton(modelName)
                item.rotation = if (currentRotation == 90) -90f else if (currentRotation == 270) 90f else 0f
                item.setOnClickListener {
                    onModelSelected?.invoke(modelName)
                    selectionContainer?.visibility = View.GONE
                    Toast.makeText(context, "Selected: $modelName", Toast.LENGTH_SHORT).show()
                }
                container.addView(item)
                container.addView(createSpacer(30))
            }
            
            // Wrap in appropriate scrollview
            val scrollWrapper: View = if (isPortrait) {
                val hScroll = android.widget.HorizontalScrollView(context)
                hScroll.addView(container)
                hScroll
            } else {
                scrollView.addView(container)
                scrollView
            }
            
            selectionContainer?.addView(scrollWrapper)
            
            // Update Selection Container Layout Params based on rotation
            updateSelectionContainerParams()
        }
    }

    private fun showSizeSelector() {
        toggleSelectionMenu {
            val isPortrait = currentRotation == 0
            val container = LinearLayout(context).apply { 
                orientation = if (isPortrait) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL 
            }

            sizeList.forEach { size ->
                val item = createChipButton("$size\"")
                item.rotation = if (currentRotation == 90) -90f else if (currentRotation == 270) 90f else 0f
                item.setOnClickListener {
                    onSizeSelected?.invoke(size.toFloat())
                    selectionContainer?.visibility = View.GONE
                    Toast.makeText(context, "Size: $size inch", Toast.LENGTH_SHORT).show()
                }
                container.addView(item)
                container.addView(createSpacer(30))
            }

            val scrollWrapper: View = if (isPortrait) {
                val hScroll = android.widget.HorizontalScrollView(context)
                hScroll.addView(container)
                hScroll
            } else {
                val vScroll = android.widget.ScrollView(context)
                vScroll.addView(container)
                vScroll
            }
            selectionContainer?.addView(scrollWrapper)
            updateSelectionContainerParams()
        }
    }

    private fun updateSelectionContainerParams() {
        val isPortrait = currentRotation == 0
        selectionContainer?.apply {
            val params = layoutParams as FrameLayout.LayoutParams
            if (isPortrait) {
                params.width = FrameLayout.LayoutParams.MATCH_PARENT
                params.height = FrameLayout.LayoutParams.WRAP_CONTENT
                params.gravity = Gravity.BOTTOM
                params.bottomMargin = 250
                params.marginEnd = 0
                orientation = LinearLayout.HORIZONTAL
            } else {
                params.width = FrameLayout.LayoutParams.WRAP_CONTENT
                params.height = FrameLayout.LayoutParams.MATCH_PARENT
                params.gravity = Gravity.END
                params.marginEnd = 250 // Next to the vertical bar
                params.bottomMargin = 0
                orientation = LinearLayout.VERTICAL
            }
            layoutParams = params
        }
    }

    private fun toggleSelectionMenu(populateContent: () -> Unit) {
        if (selectionContainer?.visibility == View.VISIBLE) {
            selectionContainer?.visibility = View.GONE
            selectionContainer?.removeAllViews()
        } else {
            selectionContainer?.removeAllViews()
            populateContent()
            selectionContainer?.visibility = View.VISIBLE
        }
    }

    private fun toggleMode() {
        currentMode = if (currentMode == ARMode.MARKERLESS) ARMode.MARKER_BASED else ARMode.MARKERLESS
        onModeSelected?.invoke(currentMode)
        updateModeIcon()
    }

    private fun updateModeIcon() {
        val icon = if (currentMode == ARMode.MARKER_BASED) "◈" else "◱"
        btnModeToggle?.text = icon
    }

    // --- Helper UI Functions ---

    private fun createIconButton(icon: String): TextView {
        return TextView(context).apply {
            text = icon
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundDrawable(Color.parseColor("#66000000"), 100f)
            layoutParams = LinearLayout.LayoutParams(120, 120)
        }
    }

    private fun createMenuButton(label: String): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundDrawable(Color.parseColor("#99000000"), 30f)
            layoutParams = LinearLayout.LayoutParams(250, 100)
        }
    }

    private fun createChipButton(label: String): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.BLACK)
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundDrawable(Color.WHITE, 50f)
            setPadding(30, 15, 30, 15)
            layoutParams = LinearLayout.LayoutParams(300, 100)
        }
    }

    private fun createCaptureButton(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(180, 180)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(12, Color.WHITE)
            }
            isClickable = true
        }
    }

    private fun createRoundDrawable(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun createSpacer(size: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }
    
    fun setModels(models: List<String>) { this.modelList = models }
}