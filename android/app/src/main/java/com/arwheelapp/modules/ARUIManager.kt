package com.arwheelapp.modules

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.arwheelapp.R
import com.arwheelapp.modules.ARRendering.Companion.ADJUST_STEP_FINE
import com.arwheelapp.modules.ARRendering.Companion.ADJUST_STEP_MEDIUM
import com.arwheelapp.utils.ARMode
import com.arwheelapp.utils.dp

class ARUIManager(
    private val context: Context,
    private val rootLayout: FrameLayout,
    private val overlayView: View
) {
    // ── Callbacks set by ARActivity ───────────────────────────────────────
    var onModeSelected: ((ARMode) -> Unit)? = null
    var onBackClicked: (() -> Unit)? = null
    var onCaptureClicked: (() -> Unit)? = null
    var onModelSelected: ((String) -> Unit)? = null
    var onSizeSelected: ((Float) -> Unit)? = null

    // Adjust-panel callbacks: each lambda receives the step size in metres
    var onAdjustUp : ((Float) -> Unit)? = null
    var onAdjustDown : ((Float) -> Unit)? = null
    var onAdjustLeft : ((Float) -> Unit)? = null
    var onAdjustRight : ((Float) -> Unit)? = null
    var onAdjustForward : ((Float) -> Unit)? = null
    var onAdjustBack : ((Float) -> Unit)? = null
    var onAdjustDone : (() -> Unit)? = null
    var onAdjustReset : (() -> Unit)? = null

    // ── UI element references ──────────────────────────────────────────────
    private var btnBack : AppCompatImageView? = null
    private var btnModeToggle : AppCompatImageView? = null
    private var controlsBar : LinearLayout? = null
    private var selectionPanel : FrameLayout? = null
    private var tvSelectionTitle: TextView? = null
    private var selectionRV : RecyclerView? = null
    private var adjustPanel     : FrameLayout?         = null

    // ── State ─────────────────────────────────────────────────────────────
    private var currentMode: ARMode = ARMode.DEFAULT
    var currentRotation = 0
        private set
    private var orientationListener: OrientationEventListener? = null

    // --- Menu Status ---
    private var currentOpenMenu : String? = null

    // Current adjust step size
    private var adjustStep = ADJUST_STEP_FINE

    // Hold-to-repeat infrastructure
    private val handler = Handler(Looper.getMainLooper())
    private val holdInitDelay = 400L // ms before repeat starts
    private val holdRepeatDelay = 80L // ms between repeats
    private var currentHoldRunnable: Runnable? = null

    // Mock data
    private var modelList = listOf("wheel1", "wheel2", "wheel3", "wheel4", "wheel5")
    private var sizeList = listOf(14, 15, 16, 17, 18, 19, 20, 21, 22)

    // fun setModels(models: List<String>) {
    //     this.modelList = models
    // }

    // ── Lifecycle ──────────────────────────────────────────────────────────
    fun setupInterface() {
        setupTopBar()
        setupDebugButton()
        setupControlsBar()
        setupSelectionPanel()
        setupAdjustPanel()
        setupOrientationListener()
    }

    fun onResume() = orientationListener?.enable()
    fun onPause() = orientationListener?.disable()

    // ══════════════════════════════════════════════════════════════════════
    // Top bar
    // ══════════════════════════════════════════════════════════════════════
    private fun setupTopBar() {
        val sbH    = getStatusBarHeight()
        val margin = 16.dp
        btnBack = makeIconBtn(R.drawable.ic_arrow_back).apply {
            layoutParams = FrameLayout.LayoutParams(48.dp, 48.dp).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(margin, sbH + 12.dp, 0, 0)
            }
            setOnClickListener { onBackClicked?.invoke() }
        }
        btnModeToggle = makeIconBtn(R.drawable.ic_layers).apply {
            layoutParams = FrameLayout.LayoutParams(48.dp, 48.dp).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, sbH + 12.dp, margin, 0)
            }
            setOnClickListener { toggleMode() }
        }
        rootLayout.addView(btnBack)
        rootLayout.addView(btnModeToggle)
    }

    // ── Debug button ────────────────────────────────────────────────────────
    private fun setupDebugButton() {
        val sbH = getStatusBarHeight()
        val btn = TextView(context).apply {
            text = "DEBUG"
            textSize = 9f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = pill(Color.parseColor("#99000000"))
            layoutParams = FrameLayout.LayoutParams(52.dp, 28.dp).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = sbH + 16.dp
            }
            setOnClickListener {
                overlayView.visibility =
                    if (overlayView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        rootLayout.addView(btn)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Bottom controls bar
    // ══════════════════════════════════════════════════════════════════════
    private fun setupControlsBar() {
        controlsBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 36.dp
            }
        }
        val btnModel = makeMenuBtn("Model", R.drawable.ic_cube).apply { setOnClickListener { toggleMenu("MODEL") } }
        val btnCapture = makeCaptureBtn().apply { setOnClickListener { onCaptureClicked?.invoke() } }
        val btnSize = makeMenuBtn("Size", R.drawable.ic_settings).apply { setOnClickListener { toggleMenu("SIZE") } }
        listOf(btnModel, btnCapture, btnSize).forEach { v ->
            controlsBar!!.addView(LinearLayout(context).apply {
                gravity     = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(v)
            })
        }
        rootLayout.addView(controlsBar)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Selection overlay (model / size picker)
    // ══════════════════════════════════════════════════════════════════════
    private fun setupSelectionPanel() {
        selectionPanel = FrameLayout(context).apply {
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#CC111111"))
            setOnClickListener { closeSelectionMenu() }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                180.dp
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 100.dp
            }
        }
        tvSelectionTitle = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setShadowLayer(3f, 0f, 2f, Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP
                topMargin = 12.dp
            }
        }
        selectionPanel!!.addView(tvSelectionTitle)
        rootLayout.addView(selectionPanel)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Adjust panel
    // ══════════════════════════════════════════════════════════════════════
    private fun setupAdjustPanel() {
        adjustPanel = FrameLayout(context).apply {
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = 110.dp
            }
        }

        // ── Container card ─────────────────────────────────────────────
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20.dp, 16.dp, 20.dp, 16.dp)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6111111"))
                cornerRadius = 24.dp.toFloat()
                setStroke(1.dp, Color.parseColor("#40FFFFFF"))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }
        // ── Title row ──────────────────────────────────────────────────
        card.addView(TextView(context).apply {
            text = "Adjust Position"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#AAFFFFFF"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10.dp }
        })

        // ── Step chips ─────────────────────────────────────────────────
        val chipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp }
        }

        data class Chip(val label: String, val step: Float)
        val chips = listOf(Chip("Fine", ADJUST_STEP_FINE), Chip("Med", ADJUST_STEP_MEDIUM), Chip("Coarse", ARRendering.ADJUST_STEP_COARSE))
        val chipViews = mutableListOf<TextView>()

        fun selectChip(idx: Int) {
            adjustStep = chips[idx].step
            chipViews.forEachIndexed { i, tv ->
                tv.background = if (i == idx) pill(Color.parseColor("#FF4444DD")) else pill(Color.parseColor("#66FFFFFF"))
                tv.setTextColor(if (i == idx) Color.WHITE else Color.parseColor("#CCFFFFFF"))
            }
        }

        chips.forEachIndexed { i, chip ->
            val tv = TextView(context).apply {
                text = chip.label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#CCFFFFFF"))
                background = pill(Color.parseColor("#66FFFFFF"))
                setPadding(14.dp, 6.dp, 14.dp, 6.dp)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4.dp, 0, 4.dp, 0) }
                setOnClickListener { selectChip(i) }
            }
            chipViews.add(tv)
            chipRow.addView(tv)
        }
        selectChip(0) // default = fine
        card.addView(chipRow)

        // ── D-pad (UP / LEFT-DONE-RIGHT / DOWN) ───────────────────────
        val dpad = GridLayout(context).apply {
            columnCount = 3
            rowCount = 3
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp }
        }

        fun gp(col: Int, row: Int) = GridLayout.LayoutParams(
            GridLayout.spec(row, GridLayout.CENTER),
            GridLayout.spec(col, GridLayout.CENTER)
        ).apply { setMargins(4.dp, 4.dp, 4.dp, 4.dp) }

        // Row 0: empty | UP | empty
        dpad.addView(View(context).apply { layoutParams = gp(0,0).also { it.width = 56.dp; it.height = 56.dp } })
        dpad.addView(makeAdjBtn("▲", gp(1, 0)) { onAdjustUp?.invoke(adjustStep) })
        dpad.addView(View(context).apply { layoutParams = gp(2,0).also { it.width = 56.dp; it.height = 56.dp } })

        // Row 1: LEFT | DONE | RIGHT
        dpad.addView(makeAdjBtn("◀", gp(0, 1)) { onAdjustLeft?.invoke(adjustStep) })
        dpad.addView(makeDoneBtn(gp(1, 1)))
        dpad.addView(makeAdjBtn("▶", gp(2, 1)) { onAdjustRight?.invoke(adjustStep) })

        // Row 2: empty | DOWN | empty
        dpad.addView(View(context).apply { layoutParams = gp(0,2).also { it.width = 56.dp; it.height = 56.dp } })
        dpad.addView(makeAdjBtn("▼", gp(1, 2)) { onAdjustDown?.invoke(adjustStep) })
        dpad.addView(View(context).apply { layoutParams = gp(2,2).also { it.width = 56.dp; it.height = 56.dp } })

        card.addView(dpad)

        // ── Depth row (BACK | FORWARD) ─────────────────────────────────
        val depthRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10.dp }
        }
        depthRow.addView(makeAdjBtn("⟵ Back", null, wide = true) { onAdjustBack?.invoke(adjustStep) })
        depthRow.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(12.dp, 1)
        })
        depthRow.addView(makeAdjBtn("Fwd ⟶", null, wide = true) { onAdjustForward?.invoke(adjustStep) })
        card.addView(depthRow)

        // ── Reset ──────────────────────────────────────────────────────
        card.addView(TextView(context).apply {
            text = "↺  Reset position"
            textSize = 12f
            setTextColor(Color.parseColor("#FFFF8844"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onAdjustReset?.invoke() }
        })

        adjustPanel!!.addView(card)
        rootLayout.addView(adjustPanel)
    }

    // ── Show / hide adjust panel with fade ─────────────────────────────────
    fun showAdjustPanel() {
        val panel = adjustPanel ?: return
        if (panel.visibility == View.VISIBLE) return
        panel.alpha = 0f
        panel.visibility = View.VISIBLE
        panel.animate().alpha(1f).setDuration(200).start()
        // Hide bottom bar while adjusting so it's less cluttered
        controlsBar?.animate()?.alpha(0f)?.setDuration(150)?.start()
    }

    fun hideAdjustPanel() {
        val panel = adjustPanel ?: return
        if (panel.visibility != View.VISIBLE) return
        panel.animate().alpha(0f).setDuration(180)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    panel.visibility = View.GONE
                    panel.animate().setListener(null)
                }
            }).start()
        controlsBar?.animate()?.alpha(1f)?.setDuration(200)?.start()
    }

    // ══════════════════════════════════════════════════════════════════════
    // Menu logic
    // ══════════════════════════════════════════════════════════════════════
    private fun toggleMenu(menu: String) {
        if (currentOpenMenu == menu) closeSelectionMenu()
        else { currentOpenMenu = menu; if (menu == "MODEL") showModelPicker() else showSizePicker() }
    }

    private fun closeSelectionMenu() {
        selectionPanel?.visibility = View.GONE
        currentOpenMenu = null
    }

    private fun showModelPicker() { updateSelectionMenu(modelList, isModel = true) }

    private fun showSizePicker() { updateSelectionMenu(sizeList.map { it.toString() }, isModel = false) }

    private fun updateSelectionMenu(data: List<String>, isModel: Boolean) {
        selectionRV?.let { selectionPanel?.removeView(it) }
        selectionPanel?.visibility = View.VISIBLE
        tvSelectionTitle?.visibility = if (isModel) View.VISIBLE else View.GONE
        selectionRV = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = SelectionAdapter(data, isModel)
            clipToPadding = false
            val itemW = 100.dp
            val padding = context.resources.displayMetrics.widthPixels / 2 - itemW / 2
            setPadding(padding, 0, padding, 0)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 110.dp).apply {
                gravity = Gravity.BOTTOM
            }
        }

        val snap = LinearSnapHelper().also { it.attachToRecyclerView(selectionRV) }
        selectionRV!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                val center = snap.findSnapView(rv.layoutManager) ?: return
                val pos = rv.getChildAdapterPosition(center); if (pos < 0) return
                val v = data[pos]
                if (isModel) { tvSelectionTitle?.text = v.uppercase(); onModelSelected?.invoke(v) }
                else onSizeSelected?.invoke(v.toFloat())
            }
        })

        selectionPanel!!.addView(selectionRV)
        if (isModel) tvSelectionTitle?.text = data.first().uppercase()
    }

    // ── Mode toggle ─────────────────────────────────────────────────────────
    private fun toggleMode() {
        currentMode = if (currentMode == ARMode.MARKERLESS) ARMode.MARKER_BASED else ARMode.MARKERLESS
        onModeSelected?.invoke(currentMode)
        btnModeToggle?.setImageResource(if (currentMode == ARMode.MARKER_BASED) R.drawable.ic_qr_code else R.drawable.ic_layers)
    }

    // ══════════════════════════════════════════════════════════════════════
    // Orientation
    // ══════════════════════════════════════════════════════════════════════
    private fun setupOrientationListener() {
        orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(o: Int) {
                if (o == ORIENTATION_UNKNOWN) return
                val r = when {
                    o >= 315 || o < 45 -> 0
                    o in 45..135 -> 90
                    o in 135..225 -> 180
                    o in 225..315 -> 270
                    else -> 0
                }
                if (r != currentRotation) { currentRotation = r; rotateIcons(r) }
            }
        }
    }

    private fun rotateIcons(rotation: Int) {
        val deg = when (rotation) {
            90 -> -90f
            270 -> 90f
            else -> 0f
        }
        val backDeg = if (deg == -90f) 90f else deg
        btnBack?.animate()?.rotation(backDeg)?.setDuration(300)?.start()
        btnModeToggle?.animate()?.rotation(deg)?.setDuration(300)?.start()
        controlsBar?.let {
            for (i in 0 until it.childCount)
                (it.getChildAt(i) as? ViewGroup)?.getChildAt(0)
                    ?.animate()?.rotation(deg)?.setDuration(300)?.start()
        }
        selectionRV?.let { rv ->
            for (i in 0 until rv.childCount)
                rv.getChildAt(i)?.animate()?.rotation(deg)?.setDuration(300)?.start()
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // View factory helpers
    // ══════════════════════════════════════════════════════════════════════
    private fun makeIconBtn(iconRes: Int) = AppCompatImageView(context).apply {
        setImageResource(iconRes)
        setColorFilter(Color.WHITE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#55000000"))
            setStroke(1.dp, Color.parseColor("#33FFFFFF"))
        }
        setPadding(10.dp, 10.dp, 10.dp, 10.dp)
    }

    private fun makeMenuBtn(label: String, iconRes: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(12.dp, 10.dp, 12.dp, 10.dp)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#99000000"))
            cornerRadius = 20.dp.toFloat()
            setStroke(1.dp, Color.parseColor("#33FFFFFF"))
        }
        layoutParams = LinearLayout.LayoutParams(70.dp, 70.dp)

        addView(AppCompatImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(24.dp, 24.dp)
        })
        addView(TextView(context).apply {
            text = label
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 4.dp }
        })
    }

    private fun makeCaptureBtn() = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(76.dp, 76.dp)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(5.dp, Color.parseColor("#DDDDDD"))
        }
    }

    private fun makeAdjBtn(
        label: String,
        gp: GridLayout.LayoutParams?,
        wide: Boolean = false,
        action: () -> Unit
    ): TextView = TextView(context).apply {
        text = label
        textSize = if (wide) 12f else 18f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)

        val w = if (wide) 100.dp else 56.dp
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#AA2244AA"))
            cornerRadius = 14.dp.toFloat()
            setStroke(1.dp, Color.parseColor("#66AACCFF"))
        }

        if (gp != null) {
            gp.width = w
            gp.height = 56.dp
            layoutParams = gp
        } else {
            layoutParams = LinearLayout.LayoutParams(w, 52.dp)
        }

        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    action()                          // fire immediately on press
                    scaleX = 0.91f; scaleY = 0.91f
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#CC3366CC"))
                        cornerRadius = 14.dp.toFloat()
                        setStroke(1.dp, Color.parseColor("#99AACCFF"))
                    }
                    // Schedule hold-repeat
                    currentHoldRunnable?.let { handler.removeCallbacks(it) }
                    val repeat = object : Runnable {
                        override fun run() {
                            action()
                            handler.postDelayed(this, holdRepeatDelay)
                        }
                    }
                    currentHoldRunnable = repeat
                    handler.postDelayed(repeat, holdInitDelay)
                    performClick()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    scaleX = 1f; scaleY = 1f
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#AA2244AA"))
                        cornerRadius = 14.dp.toFloat()
                        setStroke(1.dp, Color.parseColor("#66AACCFF"))
                    }
                    currentHoldRunnable?.let { handler.removeCallbacks(it) }
                    currentHoldRunnable = null
                    true
                }
                else -> false
            }
        }
    }

    private fun makeDoneBtn(gp: GridLayout.LayoutParams) = TextView(context).apply {
        text = "✕"
        textSize = 20f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#CC993333"))
            setStroke(1.dp, Color.parseColor("#66FFAAAA"))
        }
        gp.width  = 56.dp; gp.height = 56.dp
        layoutParams = gp
        setOnClickListener { onAdjustDone?.invoke() }
    }

    // ── Shared shape helpers ──────────────────────────────────────────────
    private fun pill(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = 100f
    }

    private fun getStatusBarHeight(): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else 24.dp
    }

    // ══════════════════════════════════════════════════════════════════════
    // RecyclerView adapter
    // ══════════════════════════════════════════════════════════════════════
    private inner class SelectionAdapter(
        private val items : List<String>,
        private val isModel: Boolean
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
            object : RecyclerView.ViewHolder(if (isModel) makeModelCell() else makeSizeCell()) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
            if (!isModel) (holder.itemView as TextView).text = items[pos]
            holder.itemView.rotation = when (currentRotation) { 90 -> -90f; 270 -> 90f; else -> 0f }
        }

        override fun getItemCount() = items.size

        private fun makeModelCell() = AppCompatImageView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(88.dp, 88.dp).apply { setMargins(8.dp, 0, 8.dp, 0) }
            setImageResource(R.drawable.ic_cube)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(16.dp, 16.dp, 16.dp, 16.dp)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#66000000"))
                cornerRadius = 18.dp.toFloat()
                setStroke(1.dp, Color.parseColor("#33FFFFFF"))
            }
        }

        private fun makeSizeCell() = TextView(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(88.dp, 88.dp).apply { setMargins(8.dp, 0, 8.dp, 0) }
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#66000000"))
                setStroke(1.dp, Color.parseColor("#33FFFFFF"))
            }
        }
    }
}