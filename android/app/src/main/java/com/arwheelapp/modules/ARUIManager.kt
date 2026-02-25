package com.arwheelapp.modules

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.arwheelapp.R
import com.arwheelapp.utils.ARMode
import com.arwheelapp.utils.dp

class ARUIManager(
    private val context: Context,
    private val rootLayout: FrameLayout,
    private val overlayView: View
) {
    // --- Callbacks ---
    var onModeSelected: ((ARMode) -> Unit)? = null
    var onBackClicked: (() -> Unit)? = null
    var onCaptureClicked: (() -> Unit)? = null
    var onModelSelected: ((String) -> Unit)? = null
    var onSizeSelected: ((Float) -> Unit)? = null

    // --- UI Elements ---
    private var btnBack: AppCompatImageView? = null
    private var btnModeToggle: AppCompatImageView? = null
    private var controlsContainer: LinearLayout? = null
    private var selectionContainer: FrameLayout? = null
    private var tvSelectionTitle: TextView? = null
    private var selectionRecyclerView: RecyclerView? = null

    // --- State & Data ---
    private var currentMode: ARMode = ARMode.DEFAULT
    var currentRotation = 0
        private set
    private var orientationListener: OrientationEventListener? = null

    // --- Menu Status ---
    private var currentOpenMenu: String? = null

    // Mock Data (Should be replaced with dynamic model paths/sizes later)
    private var modelList = listOf("wheel1", "wheel2", "wheel3", "wheel4", "wheel5")
    private var sizeList = listOf(14, 15, 16, 17, 18, 19, 20, 21, 22)

    fun setModels(models: List<String>) {
        this.modelList = models
    }

    // ==========================================
    // Lifecycle & Setup
    // ==========================================
    fun setupInterface() {
        setupNavButtons()
        setupDebugPanel()
        setupControlsPanel()
        setupSelectionOverlay()
        setupOrientationListener()
    }

    fun onResume() = orientationListener?.enable()
    fun onPause() = orientationListener?.disable()

    // ==========================================
    // UI Layout Setups
    // ==========================================
    private fun setupNavButtons() {
        val statusBarHeight = getStatusBarHeight()
        val margin = 16.dp
        btnBack = createIconButton(R.drawable.ic_arrow_back).apply {
            layoutParams = FrameLayout.LayoutParams(44.dp, 44.dp).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(margin, statusBarHeight + 8.dp, 0, 0)
            }
            setOnClickListener { onBackClicked?.invoke() }
        }
        btnModeToggle = createIconButton(R.drawable.ic_layers).apply {
            layoutParams = FrameLayout.LayoutParams(44.dp, 44.dp).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, statusBarHeight + 8.dp, margin, 0)
            }
            setOnClickListener { toggleMode() }
        }
        rootLayout.addView(btnBack)
        rootLayout.addView(btnModeToggle)
    }

    private fun setupControlsPanel() {
        controlsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 30.dp
            }
        }
        val btnModel = createMenuButton("Model", R.drawable.ic_cube).apply { setOnClickListener { toggleMenu("MODEL") } }
        val btnCapture = createCaptureButton().apply { setOnClickListener { onCaptureClicked?.invoke() } }
        val btnSize = createMenuButton("Size", R.drawable.ic_settings).apply { setOnClickListener { toggleMenu("SIZE") } }
        addControlItem(controlsContainer!!, btnModel)
        addControlItem(controlsContainer!!, btnCapture)
        addControlItem(controlsContainer!!, btnSize)
        rootLayout.addView(controlsContainer)
    }

    private fun setupSelectionOverlay() {
        selectionContainer = FrameLayout(context).apply {
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#01000000"))
            setOnClickListener { closeSelectionMenu() }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                160.dp
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 100.dp
            }
        }
        tvSelectionTitle = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(2f, 0f, 2f, Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                topMargin = 10.dp
            }
        }
        selectionContainer?.addView(tvSelectionTitle)
        rootLayout.addView(selectionContainer)
    }

    private fun setupDebugPanel() {
        val btnDebug = Button(context).apply {
            text = "DEBUG"
            textSize = 9f
            setTextColor(Color.WHITE)
            background = createRoundDrawable(Color.parseColor("#80000000"), 15.dp.toFloat())
            layoutParams = FrameLayout.LayoutParams(60.dp, 30.dp).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                marginStart = 8.dp
            }
            setOnClickListener {
                overlayView.visibility = if (overlayView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        rootLayout.addView(btnDebug)
    }

    // ==========================================
    // Menu Logic
    // ==========================================
    private fun toggleMenu(menu: String) {
        if (currentOpenMenu == menu) {
            closeSelectionMenu()
        } else {
            currentOpenMenu = menu
            if (menu == "MODEL") showModelSelector() else showSizeSelector()
        }
    }

    private fun closeSelectionMenu() {
        selectionContainer?.visibility = View.GONE
        currentOpenMenu = null
    }

    private fun showModelSelector() {
        updateSelectionMenu(modelList, isModel = true)
    }

    private fun showSizeSelector() {
        updateSelectionMenu(sizeList.map { it.toString() }, isModel = false)
    }

    private fun updateSelectionMenu(data: List<String>, isModel: Boolean) {
        selectionRecyclerView?.let { selectionContainer?.removeView(it) }
        selectionContainer?.visibility = View.VISIBLE
        tvSelectionTitle?.visibility = if (isModel) View.VISIBLE else View.GONE
        selectionRecyclerView = RecyclerView(context).apply {
            tag = "RECYCLER"
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = SelectionAdapter(data, isModel)
            clipToPadding = false
            val itemTotalWidthPx = 100.dp
            val padding = (context.resources.displayMetrics.widthPixels / 2) - (itemTotalWidthPx / 2)
            setPadding(padding, 0, padding, 0)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                100.dp
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }

        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(selectionRecyclerView)
        selectionRecyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val centerView = snapHelper.findSnapView(rv.layoutManager)
                    val pos = centerView?.let { rv.getChildAdapterPosition(it) } ?: -1
                    if (pos != -1) {
                        val selectedValue = data[pos]
                        if (isModel) {
                            tvSelectionTitle?.text = selectedValue.uppercase()
                            onModelSelected?.invoke(selectedValue)
                        } else {
                            onSizeSelected?.invoke(selectedValue.toFloat())
                        }
                    }
                }
            }
        })

        selectionContainer?.addView(selectionRecyclerView)
        if (isModel) {
            tvSelectionTitle?.text = data.first().uppercase()
        }
    }

    private fun toggleMode() {
        currentMode = if (currentMode == ARMode.MARKERLESS) ARMode.MARKER_BASED else ARMode.MARKERLESS
        onModeSelected?.invoke(currentMode)
        btnModeToggle?.setImageResource(if (currentMode == ARMode.MARKER_BASED) R.drawable.ic_qr_code else R.drawable.ic_layers)
    }

    // ==========================================
    // Orientation Logic
    // ==========================================
    private fun setupOrientationListener() {
        orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val newRotation = when {
                    orientation >= 315 || orientation < 45 -> 0
                    orientation in 45..135 -> 90
                    orientation in 135..225 -> 180
                    orientation in 225..315 -> 270
                    else -> 0
                }
                if (newRotation != currentRotation) {
                    currentRotation = newRotation
                    rotateIconsOnly(currentRotation)
                }
            }
        }
    }

    private fun rotateIconsOnly(rotation: Int) {
        val targetRot = when (rotation) {
            90 -> -90f
            270 -> 90f
            else -> 0f
        }
        btnBack?.animate()?.rotation(if (targetRot == -90f) 90f else targetRot)?.setDuration(300)?.start()
        btnModeToggle?.animate()?.rotation(targetRot)?.setDuration(300)?.start()
        controlsContainer?.let {
            for (i in 0 until it.childCount) {
                val wrapper = it.getChildAt(i) as? ViewGroup
                wrapper?.getChildAt(0)?.animate()?.rotation(targetRot)?.setDuration(300)?.start()
            }
        }
        selectionRecyclerView?.let { rv ->
            for (i in 0 until rv.childCount) {
                rv.getChildAt(i)?.animate()?.rotation(targetRot)?.setDuration(300)?.start()
            }
        }
    }

    // ==========================================
    // View Builders & Helpers
    // ==========================================
    private fun addControlItem(parent: LinearLayout, view: View) {
        val wrapper = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(view)
        }
        parent.addView(wrapper)
    }

    private fun createIconButton(iconResId: Int) = AppCompatImageView(context).apply {
        setImageResource(iconResId)
        setColorFilter(Color.WHITE)
        background = createRoundDrawable(Color.parseColor("#66000000"), 100f)
        setPadding(10.dp, 10.dp, 10.dp, 10.dp)
    }

    private fun createMenuButton(label: String, iconResId: Int) = TextView(context).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 12f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = createRoundDrawable(Color.parseColor("#99000000"), 20.dp.toFloat())
        layoutParams = LinearLayout.LayoutParams(64.dp, 64.dp)
        val drawable = ContextCompat.getDrawable(context, iconResId)?.apply {
            setBounds(0, 0, 22.dp, 22.dp)
            setTint(Color.WHITE)
        }
        setCompoundDrawables(null, drawable, null, null)
        compoundDrawablePadding = 4.dp
        setPadding(0, 8.dp, 0, 8.dp)
    }

    private fun createCaptureButton() = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(72.dp, 72.dp)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(4.dp, Color.parseColor("#DDDDDD"))
        }
    }

    private fun createRoundDrawable(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        setStroke(2.dp, Color.parseColor("#80FFFFFF"))
    }

    private fun createCircleDrawable(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(2.dp, Color.parseColor("#80FFFFFF"))
    }

    private fun getStatusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 24.dp
    }

    // ==========================================
    // Adapter
    // ==========================================
    private inner class SelectionAdapter(
        private val items: List<String>,
        private val isModel: Boolean
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = if (isModel) createModelIconView() else createSizeCircleView()
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (!isModel) {
                (holder.itemView as TextView).text = items[position]
            }
            val rot = when (currentRotation) {
                90 -> -90f
                270 -> 90f
                else -> 0f
            }
            holder.itemView.rotation = rot
        }

        override fun getItemCount() = items.size

        private fun createModelIconView() = ImageView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(80.dp, 80.dp).apply { setMargins(10.dp, 0, 10.dp, 0) }
            setImageResource(R.drawable.ic_cube)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(15.dp, 15.dp, 15.dp, 15.dp)
            background = createRoundDrawable(Color.parseColor("#66000000"), 16.dp.toFloat())
        }

        private fun createSizeCircleView() = TextView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(80.dp, 80.dp).apply { setMargins(10.dp, 0, 10.dp, 0) }
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            background = createCircleDrawable(Color.parseColor("#66000000"))
        }
    }
}