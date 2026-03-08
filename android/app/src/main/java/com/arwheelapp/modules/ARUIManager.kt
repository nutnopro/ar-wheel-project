// modules/ARUIManager.kt
package com.arwheelapp.modules

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
    // editMode = "POS" | "ROT", direction = "LEFT"|"RIGHT"|"UP"|"DOWN"
    var onNudge: ((editMode: String, dir: String) -> Unit)? = null
    var onAdjustConfirm: (() -> Unit)? = null
    var onAdjustCancel: (() -> Unit)? = null
    var onZSliderChanged: ((editMode: String, value: Float) -> Unit)? = null

    // ── UI refs ────────────────────────────────────────────────────────────────
    private var btnBack: AppCompatImageView? = null
    private var btnModeToggle: AppCompatImageView? = null
    private var controlsContainer: LinearLayout? = null
    private var selectionContainer: FrameLayout? = null
    private var tvSelectionTitle: TextView? = null
    private var selectionRecyclerView: RecyclerView? = null
    private var adjustPanel: FrameLayout? = null
    private var adjustOverlay: View? = null
    private var btnCenterMode: TextView? = null   // POS ↔ ROT toggle

    // ── State ──────────────────────────────────────────────────────────────────
    private var currentARMode: ARMode = ARMode.DEFAULT
    /** "POS" or "ROT" — which axis the d-pad controls */
    private var editMode: String = "POS"
    private var zSlider: SeekBar? = null
    var currentRotation = 0
        private set
    private var orientationListener: OrientationEventListener? = null
    private var currentOpenMenu: String? = null

    private var modelList: MutableList<String> = mutableListOf()
    private var sizeList = listOf(13, 14, 15, 16, 17, 18, 19, 20, 21, 22)

    fun setModels(models: List<String>) { modelList.clear(); modelList.addAll(models) }

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
    // Show / hide adjustment panel
    // ═════════════════════════════════════════════════════════════════════════
    fun showAdjustmentPanel(show: Boolean) {
        if (show) {
            editMode = "POS"
            zSlider?.progress = 50
            updateCenterModeBtn()
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
            setOnClickListener { toggleARMode() }
        }
        rootLayout.addView(btnBack)
        rootLayout.addView(btnModeToggle)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Bottom controls
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
    // Selection overlay
    // ═════════════════════════════════════════════════════════════════════════
    private fun setupSelectionOverlay() {
        selectionContainer = FrameLayout(context).apply {
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#01000000")) // Touch interceptor
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
            setShadowLayer(4f, 0f, 2f, Color.parseColor("#80000000"))
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
    // Adjustment panel
    // ═════════════════════════════════════════════════════════════════════════
    private fun setupAdjustmentPanel() {
        // Dim overlay (non-clickable, just visual)
        adjustOverlay = View(context).apply {
            setBackgroundColor(Color.parseColor("#44000000"))
            visibility = View.GONE
            isClickable = true
            setOnClickListener { onAdjustCancel?.invoke() }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        rootLayout.addView(adjustOverlay)

        // Panel card
        adjustPanel = FrameLayout(context).apply {
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6121212")) // โปร่งแสงนิดๆ สไตล์กระจก
                cornerRadii = floatArrayOf(
                    36.dp.toFloat(), 36.dp.toFloat(),   // Top left
                    36.dp.toFloat(), 36.dp.toFloat(),   // Top right
                    0f, 0f, 0f, 0f  // Bottom square
                )
            }
            setPadding(24.dp, 16.dp, 24.dp, 32.dp)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM }
            setOnTouchListener { _, _ -> true }   // intercept touches
        }

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Handle Bar
        val handleBar = View(context).apply {
            background = createRoundDrawable(Color.parseColor("#4DFFFFFF"), 4.dp.toFloat())
            layoutParams = LinearLayout.LayoutParams(40.dp, 5.dp).apply { bottomMargin = 16.dp }
        }
        inner.addView(handleBar)

        // Title row
        val titleRow = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dp }
        }
        titleRow.addView(TextView(context).apply {
            text = "ปรับแต่งตำแหน่ง"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        })

        titleRow.addView(AppCompatImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(Color.parseColor("#AAAAAA"))
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            layoutParams = FrameLayout.LayoutParams(40.dp, 40.dp).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
            background = createRoundDrawable(Color.parseColor("#22FFFFFF"), 20.dp.toFloat())
            setOnClickListener { onAdjustCancel?.invoke() }
        })

        // Controls row: D-pad | spacer | right column
        val controlsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        controlsRow.addView(buildDPad())
        controlsRow.addView(Space(context).apply {
            layoutParams = LinearLayout.LayoutParams(32.dp, 1)
        })
        controlsRow.addView(buildRightColumn())

        // Hint
        val tvHint = TextView(context).apply {
            text = "กดค้างที่ลูกศรเพื่อขยับ • กดปุ่มกลางเพื่อสลับโหมด"
            setTextColor(Color.parseColor("#88FFFFFF"))
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20.dp }
        }

        inner.addView(titleRow)
        inner.addView(controlsRow)
        inner.addView(buildZSlider())
        inner.addView(tvHint)
        adjustPanel?.addView(inner)
        rootLayout.addView(adjustPanel)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // D-Pad: ▲▼◀▶ + center POS/ROT toggle
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildDPad(): FrameLayout {
        val size = 180.dp
        val btnSz = 52.dp

        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)

            // UP
            addView(buildDirBtn(R.drawable.ic_arrow_up, Gravity.TOP or Gravity.CENTER_HORIZONTAL, btnSz) {
                onNudge?.invoke(editMode, "UP")
            })
            // DOWN
            addView(buildDirBtn(R.drawable.ic_arrow_down, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, btnSz) {
                onNudge?.invoke(editMode, "DOWN")
            })
            // LEFT
            addView(buildDirBtn(R.drawable.ic_arrow_left, Gravity.CENTER_VERTICAL or Gravity.START, btnSz) {
                onNudge?.invoke(editMode, "LEFT")
            })
            // RIGHT
            addView(buildDirBtn(R.drawable.ic_arrow_right, Gravity.CENTER_VERTICAL or Gravity.END, btnSz) {
                onNudge?.invoke(editMode, "RIGHT")
            })

            // Center mode toggle button
            btnCenterMode = TextView(context).apply {
                text = "POS"
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = FrameLayout.LayoutParams(56.dp, 56.dp, Gravity.CENTER)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#3478F6")) // iOS Blue for POS
                    setStroke(2, Color.parseColor("#80FFFFFF"))
                }
                setOnClickListener { toggleEditMode() }
            }
            addView(btnCenterMode)
        }
    }

    private fun buildZSlider(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 24.dp, 0, 0) // ห่างจาก D-pad ลงมานิดนึง
            }

            // ป้ายบอกชื่อ Slider
            val label = TextView(context).apply {
                text = "DEPTH / ROLL (Z-Axis)"
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 8.dp)
            }
            addView(label)

            // ตัวแถบเลื่อน
            zSlider = SeekBar(context).apply {
                layoutParams = LinearLayout.LayoutParams(250.dp, LinearLayout.LayoutParams.WRAP_CONTENT) // ความกว้าง Slider
                max = 100  // ค่า 0 ถึง 100
                progress = 50 // เริ่มต้นที่กึ่งกลาง (50)
                
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            // แปลงค่า 0-100 ให้กลายเป็น -1.0 ถึง 1.0
                            val normalizedValue = (progress - 50) / 50f
                            onZSliderChanged?.invoke(editMode, normalizedValue)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            addView(zSlider)
        }
    }

    private fun buildDirBtn(iconResId: Int, gravity: Int, sizePx: Int, action: () -> Unit): AppCompatImageView {
        return AppCompatImageView(context).apply {
            setImageResource(iconResId)
            setColorFilter(Color.WHITE)
            setPadding(10.dp, 10.dp, 10.dp, 10.dp)
            scaleType = ImageView.ScaleType.FIT_CENTER 
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx, gravity)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF"))
            }
            setContinuousClickListener(coroutineScope, action)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Right column: OK button only
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildRightColumn(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                164.dp
            )

            addView(buildConfirmBtn(R.drawable.ic_check, Color.parseColor("#34C759")) {
                onAdjustConfirm?.invoke()
            }.apply { layoutParams = LinearLayout.LayoutParams(64.dp, 64.dp) })

            addView(TextView(context).apply {
                text = "ยืนยัน"
                setTextColor(Color.parseColor("#34C759"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 8.dp }
            })

        }
    }

    private fun buildConfirmBtn(iconResId: Int, bgColor: Int, action: () -> Unit): AppCompatImageView {
        return AppCompatImageView(context).apply {
            setImageResource(iconResId)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(16.dp, 16.dp, 16.dp, 16.dp)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
                setStroke(3.dp, Color.parseColor("#4DFFFFFF"))
            }
            setContinuousClickListener(coroutineScope, action)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Toggle POS ↔ ROT
    // ─────────────────────────────────────────────────────────────────────────
    private fun toggleEditMode() {
        editMode = if (editMode == "POS") "ROT" else "POS"
        updateCenterModeBtn()
    }

    private fun updateCenterModeBtn() {
        if (editMode == "POS") {
            btnCenterMode?.text = "POS"
            (btnCenterMode?.background as? GradientDrawable)?.setColor(Color.parseColor("#3478F6"))
        } else {
            btnCenterMode?.text = "ROT"
            (btnCenterMode?.background as? GradientDrawable)?.setColor(Color.parseColor("#FF9500"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Slide animation
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
                .setDuration(350)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .withEndAction { onEnd?.invoke() }
                .start()
        } else {
            panel.animate()
                .translationY(screenH * 0.5f)
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator(1.5f))
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
        else {
            currentOpenMenu = menu
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
            setPadding(half - 50.dp, 0, half - 50.dp, 0)
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
                        val fullPath = data[pos]
                        val displayName = fullPath.substringAfterLast("/").substringBeforeLast(".")
                        tvSelectionTitle?.text = displayName.uppercase()
                        onModelSelected?.invoke(fullPath)
                    }
                    else { onSizeSelected?.invoke(data[pos].toFloat()) }
                }
            }
        })

        selectionContainer?.addView(selectionRecyclerView)
        if (isModel) {
            val firstItem = data.firstOrNull()
            val initialDisplayName = firstItem?.substringAfterLast("/")?.substringBeforeLast(".") ?: "NO MODELS"
            tvSelectionTitle?.text = initialDisplayName.uppercase()
        }
    }

    private fun toggleARMode() {
        currentARMode = if (currentARMode == ARMode.MARKERLESS) ARMode.MARKER_BASED else ARMode.MARKERLESS
        onModeSelected?.invoke(currentARMode)
        btnModeToggle?.setImageResource(
            if (currentARMode == ARMode.MARKER_BASED) R.drawable.ic_qr_code else R.drawable.ic_layers
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
                    rotateIcons(newRot)
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
            setColor(Color.parseColor("#F5F5F5"))
            setStroke(5.dp, Color.parseColor("#B3FFFFFF"))
        }
    }

    private fun createRoundDrawable(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        setStroke(1.dp, Color.parseColor("#4DFFFFFF"))
    }

    private fun getStatusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 24.dp
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Selection adapter
    // ═════════════════════════════════════════════════════════════════════════
    private inner class SelectionAdapter(
        private val items: List<String>, private val isModel: Boolean
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

        private fun makeModelIcon() = android.widget.ImageView(context).apply {
            layoutParams = RecyclerView.LayoutParams(80.dp, 80.dp).apply { setMargins(10.dp, 0, 10.dp, 0) }
            setImageResource(R.drawable.ic_cube)
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            setPadding(15.dp, 15.dp, 15.dp, 15.dp)
            background = createRoundDrawable(Color.parseColor("#80000000"), 20.dp.toFloat())
        }

        private fun makeSizeCircle() = TextView(context).apply {
            layoutParams = RecyclerView.LayoutParams(80.dp, 80.dp).apply { setMargins(10.dp, 0, 10.dp, 0) }
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#80000000"))
                setStroke(2.dp, Color.parseColor("#4DFFFFFF"))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hold-to-repeat button extension
// ─────────────────────────────────────────────────────────────────────────────
fun View.setContinuousClickListener(scope: CoroutineScope, action: () -> Unit) {
    var job: Job? = null
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
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
                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                v.performClick()
                true
            }
            else -> false
        }
    }
}