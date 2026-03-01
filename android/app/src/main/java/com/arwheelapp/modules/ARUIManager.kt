package com.arwheelapp.modules

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.arwheelapp.R
import com.arwheelapp.utils.ARMode
import com.arwheelapp.utils.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ARUIManager(
    private val context: Context,
    private val rootLayout: FrameLayout,
    private val overlayView: View,
    private val coroutineScope: CoroutineScope
) {
    // ── Callbacks ─────────────────────────────────────────────────────────────
    var onModeSelected: ((ARMode) -> Unit)? = null
    var onBackClicked: (() -> Unit)? = null
    var onCaptureClicked: (() -> Unit)? = null
    var onModelSelected: ((String) -> Unit)? = null
    var onSizeSelected: ((Float) -> Unit)? = null
    var onNudge: ((dx: Float, dy: Float, dz: Float) -> Unit)? = null
    var onAdjustConfirm: (() -> Unit)? = null
    var onAdjustCancel: (() -> Unit)? = null

    // ── UI refs ────────────────────────────────────────────────────────────────
    private var btnBack: AppCompatImageView? = null
    private var btnModeToggle: AppCompatImageView? = null
    private var controlsContainer: LinearLayout? = null
    private var selectionContainer: FrameLayout? = null
    private var tvSelectionTitle: TextView? = null
    private var selectionRecyclerView: RecyclerView? = null

    // Adjustment panel (shown after anchor placed / model tapped)
    private var adjustPanel: FrameLayout? = null
    private var adjustOverlay: View? = null         // dim background
    // ── State ──────────────────────────────────────────────────────────────────
    private var currentMode: ARMode = ARMode.DEFAULT
    var currentRotation = 0
        private set
    private var orientationListener: OrientationEventListener? = null
    private var currentOpenMenu: String? = null

    // Mock data
    private var modelList = listOf("wheel1", "wheel2", "wheel3", "wheel4", "wheel5")
    private var sizeList = listOf(14, 15, 16, 17, 18, 19, 20, 21, 22)

    fun setModels(models: List<String>) { modelList = models }

    // ═════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═════════════════════════════════════════════════════════════════════════
    fun setupInterface() {
        setupNavButtons()
        setupDebugPanel()
        setupControlsPanel()
        setupSelectionOverlay()
        setupAdjustmentPanel()
        setupOrientationListener()
    }

    fun onResume() = orientationListener?.enable()
    fun onPause() = orientationListener?.disable()

    // ═════════════════════════════════════════════════════════════════════════
    // Adjustment panel — PUBLIC entry: called from ARActivity (main thread)
    // ═════════════════════════════════════════════════════════════════════════
    fun showAdjustmentPanel(show: Boolean) {
        if (show) {
            closeSelectionMenu()
            adjustOverlay?.visibility = View.VISIBLE
            adjustPanel?.visibility = View.VISIBLE
            animatePanel(show = true)
            controlsContainer?.visibility = View.GONE
            btnModeToggle?.visibility = View.GONE
        } else {
            animatePanel(show = false) {
                adjustPanel?.visibility = View.GONE
                adjustOverlay?.visibility = View.GONE
                controlsContainer?.visibility = View.VISIBLE
                btnModeToggle?.visibility = View.VISIBLE
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Nav buttons
    // ═════════════════════════════════════════════════════════════════════════
    private fun setupNavButtons() {
        val sb = getStatusBarHeight()
        val m  = 16.dp
        btnBack = createIconButton(R.drawable.ic_arrow_back).apply {
            layoutParams = FrameLayout.LayoutParams(44.dp, 44.dp).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(m, sb + 8.dp, 0, 0)
            }
            setOnClickListener { onBackClicked?.invoke() }
        }
        btnModeToggle = createIconButton(R.drawable.ic_layers).apply {
            layoutParams = FrameLayout.LayoutParams(44.dp, 44.dp).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, sb + 8.dp, m, 0)
            }
            setOnClickListener { toggleMode() }
        }
        rootLayout.addView(btnBack)
        rootLayout.addView(btnModeToggle)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Bottom controls bar
    // ═════════════════════════════════════════════════════════════════════════
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

    // ═════════════════════════════════════════════════════════════════════════
    // Model/size selection overlay
    // ═════════════════════════════════════════════════════════════════════════
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

    // ═════════════════════════════════════════════════════════════════════════
    // Adjustment panel  ← MAIN FIX
    // ═════════════════════════════════════════════════════════════════════════
    private fun setupAdjustmentPanel() {
        // ── Semi-transparent dim overlay (full screen, does NOT steal taps
        //    so the user can still see the AR scene behind the controls) ─────
        adjustOverlay = View(context).apply {
            setBackgroundColor(Color.parseColor("#44000000"))
            visibility = View.GONE
            isClickable = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(adjustOverlay)

        // ── Card panel pinned to bottom-center ─────────────────────────────
        adjustPanel = FrameLayout(context).apply {
            visibility = View.GONE
            background = createRoundDrawable(Color.parseColor("#CC1A1A2E"), 28.dp.toFloat())
            setPadding(20.dp, 16.dp, 20.dp, 24.dp)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 0
                marginStart = 0
                marginEnd = 0
            }
            // intercept touches so they don't fall through to AR scene
            setOnTouchListener { _, _ -> true }
        }

        // ── Inner content (vertical stack) ────────────────────────────────
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ── Title row ─────────────────────────────────────────────────────
        val titleRow = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp }
        }
        val tvTitle = TextView(context).apply {
            text = "ปรับตำแหน่งล้อ"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        val btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 18f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(40.dp, 40.dp).apply {gravity = Gravity.END }
            setOnClickListener { onAdjustCancel?.invoke() }
        }
        titleRow.addView(tvTitle)
        titleRow.addView(btnClose)

        // ── Divider ───────────────────────────────────────────────────────
        val divider = View(context).apply {
            setBackgroundColor(Color.parseColor("#33FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply { bottomMargin = 16.dp }
        }

        // ── Controls row: D-pad + depth column ────────────────────────────
        val controlsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val step = 0.005f   // 5 mm per tick

        // D-Pad
        val dpad = buildDPad(step)

        // Spacer
        val spacer = Space(context).apply {
            layoutParams = LinearLayout.LayoutParams(24.dp, 1)
        }

        // Depth + OK column
        val rightCol = buildRightColumn(step)

        controlsRow.addView(dpad)
        controlsRow.addView(spacer)
        controlsRow.addView(rightCol)

        // ── Hint text ─────────────────────────────────────────────────────
        val tvHint = TextView(context).apply {
            text = "กดค้างเพื่อขยับ  •  OK เพื่อยืนยัน"
            setTextColor(Color.parseColor("#88FFFFFF"))
            textSize = 11f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp }
        }

        inner.addView(titleRow)
        inner.addView(divider)
        inner.addView(controlsRow)
        inner.addView(tvHint)

        adjustPanel?.addView(inner)
        rootLayout.addView(adjustPanel)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // D-Pad builder
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildDPad(step: Float): FrameLayout {
        val size = 160.dp
        val btnSz = 52.dp

        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)

            // UP
            addView(buildNudgeBtn("▲", Gravity.TOP or Gravity.CENTER_HORIZONTAL, btnSz) {
                onNudge?.invoke(0f, step, 0f)
            })
            // DOWN
            addView(buildNudgeBtn("▼", Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, btnSz) {
                onNudge?.invoke(0f, -step, 0f)
            })
            // LEFT
            addView(buildNudgeBtn("◀", Gravity.CENTER_VERTICAL or Gravity.START, btnSz) {
                onNudge?.invoke(-step, 0f, 0f)
            })
            // RIGHT
            addView(buildNudgeBtn("▶", Gravity.CENTER_VERTICAL or Gravity.END, btnSz) {
                onNudge?.invoke(step, 0f, 0f)
            })

            // Center dot
            addView(View(context).apply {
                layoutParams = FrameLayout.LayoutParams(12.dp, 12.dp, Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#44FFFFFF"))
                }
            })
        }
    }

    private fun buildNudgeBtn(
        label: String, gravity: Int, sizePx: Int, action: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            this.gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx, gravity)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#55FFFFFF"))
                setStroke(2, Color.parseColor("#80FFFFFF"))
            }
            setContinuousClickListener(coroutineScope, action)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Right column: Depth in/out + OK button
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildRightColumn(step: Float): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                160.dp
            )

            // Z- (towards screen / push in)
            addView(buildRectBtn("Z ◀", Color.parseColor("#55FFFFFF")) {
                onNudge?.invoke(0f, 0f, -step)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(80.dp, 48.dp).apply { bottomMargin = 8.dp }
            })

            // Z+ (away from screen / pull out)
            addView(buildRectBtn("Z ▶", Color.parseColor("#55FFFFFF")) {
                onNudge?.invoke(0f, 0f, step)
            }.apply {
                layoutParams = LinearLayout.LayoutParams(80.dp, 48.dp).apply { bottomMargin = 12.dp }
            })

            // OK / confirm
            addView(buildRectBtn("✔ OK", Color.parseColor("#2E7D32")) {
                onAdjustConfirm?.invoke()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(80.dp, 52.dp)
                (background as GradientDrawable).setStroke(2, Color.parseColor("#66A0FF66"))
            })
        }
    }

    private fun buildRectBtn(label: String, bgColor: Int, action: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundDrawable(bgColor, 12.dp.toFloat())
            setContinuousClickListener(coroutineScope, action)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Slide animation for the panel
    // ─────────────────────────────────────────────────────────────────────────
    private fun animatePanel(show: Boolean, onEnd: (() -> Unit)? = null) {
        val panel = adjustPanel ?: return
        val screenH = context.resources.displayMetrics.heightPixels.toFloat()
        if (show) {
            panel.translationY = screenH
            panel.alpha = 0f
            panel.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { onEnd?.invoke() }
                .start()
        } else {
            panel.animate()
                .translationY(screenH * 0.4f)
                .alpha(0f)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { onEnd?.invoke() }
                .start()
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Debug panel
    // ═════════════════════════════════════════════════════════════════════════
    private fun setupDebugPanel() {
        rootLayout.addView(Button(context).apply {
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
        })
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Menu logic
    // ═════════════════════════════════════════════════════════════════════════
    private fun toggleMenu(menu: String) {
        if (currentOpenMenu == menu) closeSelectionMenu()
        else { currentOpenMenu = menu
            if (menu == "MODEL") showModelSelector() else showSizeSelector()
        }
    }

    private fun closeSelectionMenu() {
        selectionContainer?.visibility = View.GONE
        currentOpenMenu = null
    }

    private fun showModelSelector() = updateSelectionMenu(modelList, isModel = true)
    private fun showSizeSelector() = updateSelectionMenu(sizeList.map { it.toString() }, isModel = false)

    private fun updateSelectionMenu(data: List<String>, isModel: Boolean) {
        selectionRecyclerView?.let { selectionContainer?.removeView(it) }
        selectionContainer?.visibility = View.VISIBLE
        tvSelectionTitle?.visibility = if (isModel) View.VISIBLE else View.GONE
        selectionRecyclerView = RecyclerView(context).apply {
            tag = "RECYCLER"
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = SelectionAdapter(data, isModel)
            clipToPadding = false
            val half = context.resources.displayMetrics.widthPixels / 2
            val itemHalf = 50.dp
            setPadding(half - itemHalf, 0, half - itemHalf, 0)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                100.dp
            ).apply { gravity = Gravity.BOTTOM }
        }

        val snap = LinearSnapHelper().also { it.attachToRecyclerView(selectionRecyclerView) }
        selectionRecyclerView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val cv = snap.findSnapView(rv.layoutManager) ?: return
                    val pos = rv.getChildAdapterPosition(cv).takeIf { it != -1 } ?: return
                    if (isModel) {
                        tvSelectionTitle?.text = data[pos].uppercase()
                        onModelSelected?.invoke(data[pos])
                    }
                    else { onSizeSelected?.invoke(data[pos].toFloat()) }
                }
            }
        })

        selectionContainer?.addView(selectionRecyclerView)
        if (isModel) tvSelectionTitle?.text = data.first().uppercase()
    }

    private fun toggleMode() {
        currentMode = if (currentMode == ARMode.MARKERLESS) ARMode.MARKER_BASED else ARMode.MARKERLESS
        onModeSelected?.invoke(currentMode)
        btnModeToggle?.setImageResource(
            if (currentMode == ARMode.MARKER_BASED) R.drawable.ic_qr_code else R.drawable.ic_layers
        )
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Orientation
    // ═════════════════════════════════════════════════════════════════════════
    private fun setupOrientationListener() {
        orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val newRot = when {
                    orientation >= 315 || orientation < 45 -> 0
                    orientation in 45..135 -> 90
                    orientation in 135..225 -> 180
                    orientation in 225..315 -> 270
                    else -> 0
                }
                if (newRot != currentRotation) {
                    currentRotation = newRot
                    rotateIcons(currentRotation)
                }
            }
        }
    }

    private fun rotateIcons(rotation: Int) {
        val r = when (rotation) {
            90 -> -90f
            270 -> 90f
            else -> 0f
        }
        btnBack?.animate()?.rotation(if (r == -90f) 90f else r)?.setDuration(300)?.start()
        btnModeToggle?.animate()?.rotation(r)?.setDuration(300)?.start()
        controlsContainer?.let {
            for (i in 0 until it.childCount) {
                (it.getChildAt(i) as? ViewGroup)?.getChildAt(0)
                    ?.animate()?.rotation(r)?.setDuration(300)?.start()
            }
        }
        selectionRecyclerView?.let { rv ->
            for (i in 0 until rv.childCount) rv.getChildAt(i)?.animate()?.rotation(r)?.setDuration(300)?.start()
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // View factories
    // ═════════════════════════════════════════════════════════════════════════
    private fun addControlItem(parent: LinearLayout, view: View) {
        parent.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(view)
        })
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
        ContextCompat.getDrawable(context, iconResId)?.apply {
            setBounds(0, 0, 22.dp, 22.dp)
            setTint(Color.WHITE)
        }.also { setCompoundDrawables(null, it, null, null) }
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

    private fun getStatusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 24.dp
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Selection adapter
    // ═════════════════════════════════════════════════════════════════════════
    private inner class SelectionAdapter(
        private val items: List<String>,
        private val isModel: Boolean
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
            object : RecyclerView.ViewHolder(if (isModel) makeModelIcon() else makeSizeCircle()) {}

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
            if (!isModel) (h.itemView as TextView).text = items[pos]
            val r = when (currentRotation) {
                90 -> -90f
                270 -> 90f
                else -> 0f
            }
            h.itemView.rotation = r
        }

        override fun getItemCount() = items.size

        private fun makeModelIcon() = ImageView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(80.dp, 80.dp).apply { setMargins(10.dp, 0, 10.dp, 0) }
            setImageResource(R.drawable.ic_cube)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(15.dp, 15.dp, 15.dp, 15.dp)
            background = createRoundDrawable(Color.parseColor("#66000000"), 16.dp.toFloat())
        }

        private fun makeSizeCircle() = TextView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(80.dp, 80.dp).apply { setMargins(10.dp, 0, 10.dp, 0) }
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#66000000"))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extension: hold-to-repeat button
// ─────────────────────────────────────────────────────────────────────────────
fun View.setContinuousClickListener(scope: CoroutineScope, action: () -> Unit) {
    var job: Job? = null
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).start()
                job = scope.launch {
                    while (isActive) {
                        action()
                        delay(16L)
                    }
                }
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                job?.cancel()
                v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                v.performClick()
                true
            }
            else -> false
        }
    }
}