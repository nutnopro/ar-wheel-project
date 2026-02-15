package com.arwheelapp.modules

import android.content.Context
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.OrientationEventListener
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.AppCompatImageView
import com.arwheelapp.R
import com.arwheelapp.utils.ARMode
import com.arwheelapp.utils.dp

class ARUIManager(private val context: Context, private val rootLayout: FrameLayout, private val overlayView: View) {
    // Callbacks
    var onModeSelected: ((ARMode) -> Unit)? = null
    var onBackClicked: (() -> Unit)? = null
    var onCaptureClicked: (() -> Unit)? = null
    var onModelSelected: ((String) -> Unit)? = null
    var onSizeSelected: ((Float) -> Unit)? = null

    // UI Elements
    private var btnBack: AppCompatImageView? = null
    private var btnModeToggle: AppCompatImageView? = null
    private var controlsContainer: LinearLayout? = null
    private var selectionContainer: LinearLayout? = null

    private var currentMode: ARMode = ARMode.MARKERLESS
    private var currentRotation = 0
    private var orientationListener: OrientationEventListener? = null

    // Mock Data
    private var modelList = listOf("wheel1", "wheel2", "wheel3", "wheel4", "wheel5") // !Should be list of path of models
    private var sizeList = listOf(15, 16, 17, 18, 19)

    fun setModels(models: List<String>) {
        this.modelList = models
    }

    fun setupInterface() {
        // Setup Containers
        setupNavButtons()       // Top (Portrait) / Left (Landscape)
        setupDebugPanel()       // Overlay toggle
        setupControlsPanel()    // Bottom (Portrait) / Right (Landscape)
        setupSelectionMenu()    // Popup menu

        // Start Orientation Listener
        setupOrientationListener()
    }

    private fun setupNavButtons() {
        // Back Button
        btnBack = createIconButton(R.drawable.ic_arrow_back).apply {
            setOnClickListener { onBackClicked?.invoke() }
        }

        // Mode Button
        btnModeToggle = createIconButton(R.drawable.ic_layers).apply {
            setOnClickListener { toggleMode() }
        }

        rootLayout.addView(btnBack)
        rootLayout.addView(btnModeToggle)

        updateNavPosition(0)
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
                bottomMargin = 30.dp
            }
        }

        // Model Button
        val btnModel = createMenuButton("Model", R.drawable.ic_cube).apply {
            setOnClickListener { showModelSelector() }
        }

        // Capture Button
        val btnCapture = createCaptureButton().apply {
            setOnClickListener { onCaptureClicked?.invoke() }
        }

        // Settings Button
        val btnSettings = createMenuButton("Size", R.drawable.ic_settings).apply {
            setOnClickListener { showSizeSelector() }
        }

        addControlItem(controlsContainer!!, btnModel)
        addControlItem(controlsContainer!!, btnCapture)
        addControlItem(controlsContainer!!, btnSettings)

        rootLayout.addView(controlsContainer)

        // controlsContainer?.viewTreeObserver?.addOnGlobalLayoutListener {
        //     val currentHeight = controlsContainer?.height ?: 0
        //     val currentWidth = controlsContainer?.width ?: 0
        //     if (currentHeight > 0) {
        //         onControlsSizeChanged(currentWidth, currentHeight)
        //     }
        // }
    }

    // !!!!!!!!!!!!!! Fix selection menu
    // private fun onControlsSizeChanged(w: Int, h: Int) {
    //     if (currentRotation == 0) {
    //         val selectionParams = selectionContainer?.layoutParams as? FrameLayout.LayoutParams
    //         if (selectionParams != null) {
    //             selectionParams.bottomMargin = h + 6.dp
    //             selectionContainer?.layoutParams = selectionParams
    //         }
    //     }
    // }

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
            setPadding(10.dp, 10.dp, 10.dp, 10.dp)
            
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 125.dp
            }
        }
        rootLayout.addView(selectionContainer)
    }

    private fun setupDebugPanel() {
        val debugContainer = LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = 6.dp
                topMargin = 66.dp
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
                    orientation >= 315 || orientation < 45 -> 0 // Portrait
                    orientation in 45..135 -> 90                // Landscape Right
                    orientation in 225..315 -> 270              // Landscape Left
                    else -> 0
                }

                if (newRotation != currentRotation) {
                    currentRotation = newRotation
                    rotateUIComponents(currentRotation)
                    updateNavPosition(currentRotation)
                }
            }
        }
    }

    private fun updateNavPosition(rotation: Int) {
        val statusBarHeight = getStatusBarHeight()
        val baseMargin = 12.dp
        val buttonSize = 40.dp
        val spacing = 10.dp

        val backParams = btnBack?.layoutParams as? FrameLayout.LayoutParams ?: return
        val modeParams = btnModeToggle?.layoutParams as? FrameLayout.LayoutParams ?: return

        backParams.setMargins(0, 0, 0, 0)
        modeParams.setMargins(0, 0, 0, 0)

        when (rotation) {
            // Portrait
            0 -> {
                backParams.gravity = Gravity.TOP or Gravity.START
                modeParams.gravity = Gravity.TOP or Gravity.END

                backParams.setMargins(baseMargin, statusBarHeight + 6.dp, 0, 0)
                modeParams.setMargins(0, statusBarHeight + 6.dp, baseMargin, 0)
            }

            // Landscape - Camera Left
            270 -> {
                backParams.gravity = Gravity.TOP or Gravity.END
                modeParams.gravity = Gravity.TOP or Gravity.END

                backParams.setMargins(0, statusBarHeight + 6.dp, baseMargin, 0)
                modeParams.setMargins(0, statusBarHeight + buttonSize + spacing, baseMargin, 0)
            }

            // Landscape - Camera Right
            90 -> {
                backParams.gravity = Gravity.TOP or Gravity.START
                modeParams.gravity = Gravity.TOP or Gravity.START

                backParams.setMargins(baseMargin, statusBarHeight + 6.dp, 0, 0)
                modeParams.setMargins(baseMargin, statusBarHeight + buttonSize + spacing, 0, 0)
            }
        }

        btnBack?.layoutParams = backParams
        btnModeToggle?.layoutParams = modeParams
    }

    private fun rotateUIComponents(rotation: Int) {
        val targetRotation = when (rotation) {
            90 -> -90f
            270 -> 90f
            else -> 0f
        }

        // Rotate All Icons/Buttons smoothly
        rotateView(btnModeToggle, targetRotation)
        val backRotation = when (rotation) {
            90 -> 90f
            270 -> 90f
            else -> 0f
        }
        rotateView(btnBack, backRotation)
        
        // Rotate Control Buttons (Model, Capture, Settings)
        for (i in 0 until (controlsContainer?.childCount ?: 0)) {
            val wrapper = controlsContainer?.getChildAt(i) as? LinearLayout
            val button = wrapper?.getChildAt(0)
            rotateView(button, targetRotation)
        }

        if (selectionContainer?.visibility == View.VISIBLE) {
            val scrollView = selectionContainer?.getChildAt(0) as? android.widget.HorizontalScrollView
            val itemContainer = scrollView?.getChildAt(0) as? LinearLayout
            if (itemContainer != null) {
                for (i in 0 until itemContainer.childCount) {
                    val itemWrapper = itemContainer.getChildAt(i)
                    rotateView(itemWrapper, targetRotation)
                }
            }
        }
    }

    private fun rotateView(view: View?, rotation: Float) {
        view?.animate()?.rotation(rotation)?.setDuration(300)?.start()
    }

    // --- LifeCycle Methods (Must be called from Activity) ---

    fun onResume() { orientationListener?.enable() }

    fun onPause() { orientationListener?.disable() }

    // --- Actions & Helpers (Keep same logic) ---

    private fun showModelSelector() {
        toggleSelectionMenu {
            val scrollView = android.widget.HorizontalScrollView(context).apply {
                isVerticalScrollBarEnabled = false
            }
            val container = LinearLayout(context).apply { 
                orientation = LinearLayout.HORIZONTAL 
                gravity = Gravity.CENTER
            }

            val currentTargetRotation = when (currentRotation) {
                90 -> -90f; 270 -> 90f; else -> 0f
            }

            modelList.forEach { modelName ->
                val item = createModelItem(modelName)
                item.rotation = currentTargetRotation
                item.setOnClickListener {
                    onModelSelected?.invoke(modelName)
                    selectionContainer?.visibility = View.GONE
                }
                container.addView(item)
                container.addView(createSpacer(10.dp))
            }
            
            scrollView.addView(container)
            selectionContainer?.addView(scrollView)
        }
    }

    private fun showSizeSelector() {
        toggleSelectionMenu {
            val scrollView = android.widget.HorizontalScrollView(context)
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val currentTargetRotation = when (currentRotation) {
                90 -> -90f; 270 -> 90f; else -> 0f
            }

            sizeList.forEach { size ->
                val item = createChipButton("$size Inches")
                item.rotation = currentTargetRotation
                item.setOnClickListener {
                    onSizeSelected?.invoke(size.toFloat())
                    selectionContainer?.visibility = View.GONE
                }
                container.addView(item)
                container.addView(createSpacer(10.dp))
            }
            scrollView.addView(container)
            selectionContainer?.addView(scrollView)
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
        val iconRes = if (currentMode == ARMode.MARKER_BASED) R.drawable.ic_qr_code else R.drawable.ic_layers
        btnModeToggle?.setImageResource(iconRes)
    }

    // --- Helper UI Functions ---

    private fun createIconButton(iconResId: Int): AppCompatImageView {
        return AppCompatImageView(context).apply {
            setImageResource(iconResId)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(10.dp, 10.dp, 10.dp, 10.dp)
            background = createRoundDrawable(Color.parseColor("#66000000"), 100f)
            layoutParams = FrameLayout.LayoutParams(40.dp, 40.dp)
        }
    }

    private fun createMenuButton(label: String, iconResId: Int): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundDrawable(Color.parseColor("#99000000"), 30f)
            layoutParams = LinearLayout.LayoutParams(66.dp, 66.dp)

            // insert Icon on the left
            val drawable = ContextCompat.getDrawable(context, iconResId)
            drawable?.setBounds(0, 0, 20.dp, 20.dp)
            drawable?.setTint(Color.WHITE)
            
            // (Left, Top, Right, Bottom)
            setCompoundDrawables(null, drawable, null, null)
            compoundDrawablePadding = 6.dp
            setPadding(3.dp, 6.dp, 3.dp, 6.dp)
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
            setPadding(16.dp, 8.dp, 16.dp, 8.dp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun createModelItem(modelName: String): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(12.dp, 12.dp, 12.dp, 12.dp)
            background = createRoundDrawable(Color.parseColor("#4DFFFFFF"), 20f)
        }

        // !Placeholder ImageView for Model Icon
        val imageView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(60.dp, 60.dp)
            setImageResource(R.drawable.ic_cube)
            setColorFilter(Color.WHITE)
        }

        val modelName = modelName
        val textView = TextView(context).apply {
            text = modelName
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 8.dp, 0, 0)
        }

        container.addView(imageView)
        container.addView(textView)

        return container
    }

    // private fun createModelItem(modelName: String): View {
    //     val container = LinearLayout(context).apply {
    //         orientation = LinearLayout.VERTICAL
    //         gravity = Gravity.CENTER
    //         layoutParams = LinearLayout.LayoutParams(
    //             LinearLayout.LayoutParams.WRAP_CONTENT,
    //             LinearLayout.LayoutParams.WRAP_CONTENT
    //         )
    //         setPadding(12.dp, 12.dp, 12.dp, 12.dp)
    //         background = createRoundDrawable(Color.parseColor("#4DFFFFFF"), 20f)
    //     }

    //     val imageView = ImageView(context).apply {
    //         // ปรับขนาดรูปให้ใหญ่ขึ้นนิดนึง เพื่อให้เห็นลายล้อชัดๆ
    //         layoutParams = LinearLayout.LayoutParams(60.dp, 60.dp)
    //         scaleType = ImageView.ScaleType.FIT_CENTER 
            
    //         // -----------------------------------------------------
    //         // ค้นหารูปภาพในโฟลเดอร์ drawable ที่ชื่อตรงกับ modelName
    //         // เช่น ถ้า modelName คือ "wheel1" มันจะหาไฟล์ wheel1.png/jpg
    //         // -----------------------------------------------------
    //         val resourceId = context.resources.getIdentifier(
    //             modelName.lowercase(), // บังคับตัวเล็กให้ตรงกับกฎของ drawable
    //             "drawable",
    //             context.packageName
    //         )

    //         if (resourceId != 0) {
    //             // ถ้ารูปมีอยู่จริง โหลดมาแสดงเลย และไม่ต้องใส่สีทับ (clearColorFilter)
    //             setImageResource(resourceId)
    //             clearColorFilter()
    //         } else {
    //             // ถ้ายังไม่ได้ใส่รูปล้อนั้นๆ ไว้ ให้ใช้ไอคอนกล่องเป็น Placeholder
    //             setImageResource(R.drawable.ic_cube)
    //             setColorFilter(Color.WHITE)
    //         }
    //     }

    //     val textView = TextView(context).apply {
    //         // ทำให้ตัวอักษรตัวแรกพิมพ์ใหญ่ เช่น wheel1 -> Wheel1
    //         text = modelName.replaceFirstChar { it.uppercase() } 
    //         setTextColor(Color.WHITE)
    //         textSize = 12f
    //         gravity = Gravity.CENTER
    //         setPadding(0, 8.dp, 0, 0)
    //     }

    //     container.addView(imageView)
    //     container.addView(textView)

    //     return container
    // }

    private fun createCaptureButton(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(80.dp, 80.dp)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
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

    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return if (result > 0) result else 26.dp
    }
}