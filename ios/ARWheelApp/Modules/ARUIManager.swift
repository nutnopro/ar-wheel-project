// Modules/ARUIManager.swift
import UIKit

/// UIKit UI panel — direct port of Android ARUIManager.
/// Same callbacks, same layout (back button, mode toggle, bottom controls,
/// horizontal selection picker, adjustment panel with D-pad & Z-slider).
class ARUIManager: NSObject {

    // MARK: - Callbacks
    var onModeSelected:    ((ARMode) -> Void)?
    var onBackClicked:     (() -> Void)?
    var onCaptureClicked:  (() -> Void)?
    var onModelSelected:   ((String) -> Void)?
    var onSizeSelected:    ((Float) -> Void)?
    var onNudge:           ((String, String) -> Void)?  // (editMode, direction)
    var onAdjustConfirm:   (() -> Void)?
    var onAdjustCancel:    (() -> Void)?
    var onZSliderChanged:  ((String, Float) -> Void)?   // (editMode, value –1…1)

    // MARK: - State
    private(set) var currentRotation: Int = 0
    private var currentARMode: ARMode = .default
    private var editMode: String = "POS"
    private var currentOpenMenu: String? = nil
    private var modelList: [String] = []
    private let sizeList = [14, 15, 16, 17, 18, 19, 20, 21, 22]

    // MARK: - UI Refs
    private weak var rootView: UIView?
    private weak var overlayView: UIView?   // OnnxOverlayView

    private var btnBack: UIButton?
    private var btnModeToggle: UIButton?
    private var controlsContainer: UIStackView?
    private var selectionContainer: UIView?
    private var selectionCollection: UICollectionView?
    private var adjustPanel: UIView?
    private var adjustOverlay: UIView?
    private var btnCenterMode: UIButton?
    private var zSlider: UISlider?

    private var nudgeTimers: [UIButton: Timer] = [:]

    // MARK: - Init
    init(rootView: UIView, overlayView: UIView) {
        self.rootView    = rootView
        self.overlayView = overlayView
        super.init()
    }

    func setModels(_ models: [String]) { modelList = models }

    // MARK: - Lifecycle
    func setupInterface() {
        setupNavButtons()
        setupDebugPanel()
        setupControlsPanel()
        setupSelectionOverlay()
        setupAdjustmentPanel()
        setupOrientationListener()
    }

    func onResume() {
        NotificationCenter.default.addObserver(
            self, selector: #selector(orientationDidChange),
            name: UIDevice.orientationDidChangeNotification, object: nil)
        UIDevice.current.beginGeneratingDeviceOrientationNotifications()
    }
    func onPause() {
        NotificationCenter.default.removeObserver(self, name: UIDevice.orientationDidChangeNotification, object: nil)
    }

    // MARK: - Show / hide adjustment panel
    func showAdjustmentPanel(_ show: Bool) {
        guard let panel = adjustPanel, let overlay = adjustOverlay else { return }
        if show {
            editMode = "POS"; zSlider?.value = 0.5; updateCenterModeBtn()
            closeSelectionMenu()
            overlay.isHidden = false; panel.isHidden = false
            animatePanel(show: true)
            controlsContainer?.isHidden = true; btnModeToggle?.isHidden = true
        } else {
            animatePanel(show: false) {
                panel.isHidden = true; overlay.isHidden = true
                self.controlsContainer?.isHidden = false; self.btnModeToggle?.isHidden = false
            }
        }
    }

    // MARK: - Nav buttons
    private func setupNavButtons() {
        guard let root = rootView else { return }
        let safeTop = UIApplication.shared.windows.first?.safeAreaInsets.top ?? 44

        btnBack = makeIconButton(systemName: "arrow.left") { [weak self] in self?.onBackClicked?() }
        btnBack?.frame = CGRect(x: 16, y: safeTop + 8, width: 44, height: 44)
        root.addSubview(btnBack!)

        btnModeToggle = makeIconButton(systemName: "square.3.layers.3d") { [weak self] in self?.toggleARMode() }
        btnModeToggle?.frame = CGRect(x: root.bounds.width - 60, y: safeTop + 8, width: 44, height: 44)
        btnModeToggle?.autoresizingMask = [.flexibleLeftMargin]
        root.addSubview(btnModeToggle!)
    }

    // MARK: - Bottom controls
    private func setupControlsPanel() {
        guard let root = rootView else { return }
        let stack = UIStackView()
        stack.axis = .horizontal
        stack.distribution = .fillEqually
        stack.alignment = .center
        let h: CGFloat = 90
        stack.frame = CGRect(x: 0, y: root.bounds.height - h - 30,
                             width: root.bounds.width, height: h)
        stack.autoresizingMask = [.flexibleTopMargin, .flexibleWidth]

        let btnModel   = makeMenuButton(label: "Model",   systemName: "cube")   { [weak self] in self?.toggleMenu("MODEL") }
        let btnCapture = makeCaptureButton { [weak self] in self?.onCaptureClicked?() }
        let btnSize    = makeMenuButton(label: "Size",    systemName: "gearshape") { [weak self] in self?.toggleMenu("SIZE") }

        [btnModel, btnCapture, btnSize].forEach {
            let wrapper = UIView(); wrapper.addSubview($0)
            $0.center = CGPoint(x: wrapper.bounds.midX, y: wrapper.bounds.midY)
            $0.autoresizingMask = [.flexibleLeftMargin, .flexibleRightMargin,
                                   .flexibleTopMargin, .flexibleBottomMargin]
            stack.addArrangedSubview(wrapper)
        }
        root.addSubview(stack)
        controlsContainer = stack
    }

    // MARK: - Selection overlay
    private func setupSelectionOverlay() {
        guard let root = rootView else { return }
        let container = UIView()
        container.isHidden = true
        container.backgroundColor = UIColor.black.withAlphaComponent(0.01)
        container.frame = CGRect(x: 0, y: root.bounds.height - 260,
                                 width: root.bounds.width, height: 160)
        container.autoresizingMask = [.flexibleTopMargin, .flexibleWidth]
        let tap = UITapGestureRecognizer(target: self, action: #selector(closeSelectionMenu))
        container.addGestureRecognizer(tap)
        root.addSubview(container)
        selectionContainer = container
    }

    // MARK: - Adjustment panel
    private func setupAdjustmentPanel() {
        guard let root = rootView else { return }

        // Dim overlay
        let overlay = UIView(frame: root.bounds)
        overlay.backgroundColor = UIColor.black.withAlphaComponent(0.27)
        overlay.isHidden = true
        overlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        overlay.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(didTapOverlay)))
        root.addSubview(overlay)
        adjustOverlay = overlay

        // Panel card
        let panelH: CGFloat = 320
        let panel = UIView(frame: CGRect(x: 0, y: root.bounds.height - panelH,
                                         width: root.bounds.width, height: panelH))
        panel.autoresizingMask = [.flexibleTopMargin, .flexibleWidth]
        panel.backgroundColor  = UIColor(red: 0.07, green: 0.07, blue: 0.07, alpha: 0.9)
        panel.layer.cornerRadius = 36
        panel.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
        panel.isHidden = true
        root.addSubview(panel)
        adjustPanel = panel

        // Handle bar
        let bar = UIView(frame: CGRect(x: panel.bounds.width / 2 - 20, y: 12, width: 40, height: 5))
        bar.autoresizingMask = [.flexibleLeftMargin, .flexibleRightMargin]
        bar.backgroundColor = UIColor.white.withAlphaComponent(0.3)
        bar.layer.cornerRadius = 2.5
        panel.addSubview(bar)

        // Title
        let title = UILabel(frame: CGRect(x: 0, y: 24, width: panel.bounds.width, height: 28))
        title.autoresizingMask = [.flexibleWidth]
        title.text = "ปรับแต่งตำแหน่ง"
        title.textColor = .white; title.font = .boldSystemFont(ofSize: 16); title.textAlignment = .center
        panel.addSubview(title)

        // Close
        let closeBtn = UIButton(type: .system)
        closeBtn.frame = CGRect(x: panel.bounds.width - 50, y: 16, width: 36, height: 36)
        closeBtn.autoresizingMask = [.flexibleLeftMargin]
        closeBtn.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeBtn.tintColor = UIColor.white.withAlphaComponent(0.7)
        closeBtn.addTarget(self, action: #selector(didTapCancel), for: .touchUpInside)
        panel.addSubview(closeBtn)

        // Controls row
        let dpad = buildDPad()
        dpad.frame = CGRect(x: 30, y: 60, width: 180, height: 180)
        panel.addSubview(dpad)

        let confirmBtn = buildConfirmButton()
        confirmBtn.frame = CGRect(x: panel.bounds.width - 110, y: 100, width: 64, height: 64)
        confirmBtn.autoresizingMask = [.flexibleLeftMargin]
        panel.addSubview(confirmBtn)

        let confirmLabel = UILabel(frame: CGRect(x: panel.bounds.width - 120, y: 168, width: 84, height: 20))
        confirmLabel.autoresizingMask = [.flexibleLeftMargin]
        confirmLabel.text = "ยืนยัน"
        confirmLabel.textColor = UIColor(red: 0.2, green: 0.78, blue: 0.35, alpha: 1)
        confirmLabel.font = .boldSystemFont(ofSize: 12); confirmLabel.textAlignment = .center
        panel.addSubview(confirmLabel)

        // Z-Slider
        let sliderLabel = UILabel(frame: CGRect(x: 0, y: 252, width: panel.bounds.width, height: 16))
        sliderLabel.autoresizingMask = [.flexibleWidth]
        sliderLabel.text = "DEPTH / ROLL (Z-Axis)"
        sliderLabel.textColor = .white; sliderLabel.font = .systemFont(ofSize: 11); sliderLabel.textAlignment = .center
        panel.addSubview(sliderLabel)

        let slider = UISlider(frame: CGRect(x: panel.bounds.width / 2 - 125, y: 270, width: 250, height: 30))
        slider.autoresizingMask = [.flexibleLeftMargin, .flexibleRightMargin]
        slider.minimumValue = 0; slider.maximumValue = 1; slider.value = 0.5
        slider.addTarget(self, action: #selector(sliderChanged(_:)), for: .valueChanged)
        panel.addSubview(slider)
        zSlider = slider

        // Hint
        let hint = UILabel(frame: CGRect(x: 0, y: 302, width: panel.bounds.width, height: 14))
        hint.autoresizingMask = [.flexibleWidth]
        hint.text = "กดค้างที่ลูกศรเพื่อขยับ • กดปุ่มกลางเพื่อสลับโหมด"
        hint.textColor = UIColor.white.withAlphaComponent(0.53)
        hint.font = .systemFont(ofSize: 10); hint.textAlignment = .center
        panel.addSubview(hint)
    }

    // MARK: - D-Pad
    private func buildDPad() -> UIView {
        let size: CGFloat = 180
        let btnSz: CGFloat = 52
        let container = UIView(frame: CGRect(x: 0, y: 0, width: size, height: size))

        let up    = buildDirButton(systemName: "arrow.up")   { [weak self] in self?.onNudge?(self!.editMode, "UP")    }
        let down  = buildDirButton(systemName: "arrow.down") { [weak self] in self?.onNudge?(self!.editMode, "DOWN")  }
        let left  = buildDirButton(systemName: "arrow.left") { [weak self] in self?.onNudge?(self!.editMode, "LEFT")  }
        let right = buildDirButton(systemName: "arrow.right"){ [weak self] in self?.onNudge?(self!.editMode, "RIGHT") }

        up.frame    = CGRect(x: (size - btnSz) / 2, y: 0,              width: btnSz, height: btnSz)
        down.frame  = CGRect(x: (size - btnSz) / 2, y: size - btnSz,   width: btnSz, height: btnSz)
        left.frame  = CGRect(x: 0, y: (size - btnSz) / 2,              width: btnSz, height: btnSz)
        right.frame = CGRect(x: size - btnSz, y: (size - btnSz) / 2,   width: btnSz, height: btnSz)

        [up, down, left, right].forEach { container.addSubview($0) }

        // Centre POS/ROT toggle
        let centerBtn = UIButton(type: .custom)
        centerBtn.frame = CGRect(x: (size - 56) / 2, y: (size - 56) / 2, width: 56, height: 56)
        centerBtn.setTitle("POS", for: .normal)
        centerBtn.titleLabel?.font = .boldSystemFont(ofSize: 13)
        centerBtn.backgroundColor = UIColor(red: 0.204, green: 0.471, blue: 0.965, alpha: 1) // iOS blue
        centerBtn.layer.cornerRadius = 28
        centerBtn.layer.borderWidth = 2; centerBtn.layer.borderColor = UIColor.white.withAlphaComponent(0.5).cgColor
        centerBtn.addTarget(self, action: #selector(toggleEditMode), for: .touchUpInside)
        container.addSubview(centerBtn)
        btnCenterMode = centerBtn

        return container
    }

    private func buildConfirmButton() -> UIButton {
        let btn = UIButton(type: .system)
        btn.setImage(UIImage(systemName: "checkmark"), for: .normal)
        btn.tintColor = .white
        btn.backgroundColor = UIColor(red: 0.204, green: 0.78, blue: 0.349, alpha: 1) // iOS green
        btn.layer.cornerRadius = 32
        btn.layer.borderWidth = 3; btn.layer.borderColor = UIColor.white.withAlphaComponent(0.3).cgColor
        btn.addTarget(self, action: #selector(didTapConfirm), for: .touchUpInside)
        return btn
    }

    // MARK: - Orientation
    private func setupOrientationListener() {
        UIDevice.current.beginGeneratingDeviceOrientationNotifications()
    }

    @objc private func orientationDidChange() {
        let o = UIDevice.current.orientation
        let newRot: Int
        switch o {
        case .portrait:            newRot = 0
        case .landscapeLeft:       newRot = 90
        case .portraitUpsideDown:  newRot = 180
        case .landscapeRight:      newRot = 270
        default: return
        }
        guard newRot != currentRotation else { return }
        currentRotation = newRot
        rotateIcons(newRot)
    }

    private func rotateIcons(_ rotation: Int) {
        let r: CGFloat
        switch rotation {
        case 90:  r = -.pi / 2
        case 270: r = .pi / 2
        default:  r = 0
        }
        UIView.animate(withDuration: 0.3) {
            self.btnBack?.transform = rotation == 90 ? CGAffineTransform(rotationAngle: .pi / 2) : CGAffineTransform(rotationAngle: r)
            self.btnModeToggle?.transform = CGAffineTransform(rotationAngle: r)
            self.controlsContainer?.arrangedSubviews.forEach { $0.subviews.first?.transform = CGAffineTransform(rotationAngle: r) }
        }
    }

    // MARK: - Menu
    private func toggleMenu(_ menu: String) {
        if currentOpenMenu == menu { closeSelectionMenu() }
        else {
            currentOpenMenu = menu
            if menu == "MODEL" { showModelSelector() } else { showSizeSelector() }
        }
    }

    @objc private func closeSelectionMenu() {
        selectionContainer?.isHidden = true
        currentOpenMenu = nil
    }

    private func showModelSelector()  { updateSelectionMenu(data: modelList, isModel: true)  }
    private func showSizeSelector()   { updateSelectionMenu(data: sizeList.map { "\($0)" }, isModel: false) }

    private func updateSelectionMenu(data: [String], isModel: Bool) {
        guard let container = selectionContainer else { return }
        selectionCollection?.removeFromSuperview()

        let layout = UICollectionViewFlowLayout()
        layout.scrollDirection = .horizontal
        layout.itemSize = CGSize(width: 80, height: 80)
        layout.minimumLineSpacing = 20

        let cv = UICollectionView(frame: container.bounds, collectionViewLayout: layout)
        cv.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        cv.backgroundColor = .clear
        cv.showsHorizontalScrollIndicator = false
        cv.dataSource = selectionDataSource(data: data, isModel: isModel)
        cv.delegate   = selectionDelegate(isModel: isModel, data: data)
        cv.register(UICollectionViewCell.self, forCellWithReuseIdentifier: "cell")

        // Centre padding so first / last item snap to centre
        let margin = (container.bounds.width - 80) / 2
        cv.contentInset = UIEdgeInsets(top: 0, left: margin, bottom: 0, right: margin)

        container.addSubview(cv)
        selectionCollection = cv
        container.isHidden = false
    }

    // Lightweight inline data-source/delegate objects
    private func selectionDataSource(data: [String], isModel: Bool) -> SelectionDataSource {
        SelectionDataSource(items: data, isModel: isModel, rotation: currentRotation)
    }
    private func selectionDelegate(isModel: Bool, data: [String]) -> SelectionDelegate {
        SelectionDelegate(isModel: isModel, items: data, onModel: { [weak self] s in
            self?.onModelSelected?(s)
        }, onSize: { [weak self] f in
            self?.onSizeSelected?(f)
        })
    }

    // MARK: - AR mode toggle
    private func toggleARMode() {
        currentARMode = currentARMode == .markerless ? .markerBased : .markerless
        onModeSelected?(currentARMode)
        let icon = currentARMode == .markerBased ? "qrcode" : "square.3.layers.3d"
        btnModeToggle?.setImage(UIImage(systemName: icon), for: .normal)
    }

    // MARK: - Edit mode toggle
    @objc private func toggleEditMode() {
        editMode = editMode == "POS" ? "ROT" : "POS"
        zSlider?.value = 0.5
        updateCenterModeBtn()
    }
    private func updateCenterModeBtn() {
        btnCenterMode?.setTitle(editMode, for: .normal)
        let color: UIColor = editMode == "POS"
            ? UIColor(red: 0.204, green: 0.471, blue: 0.965, alpha: 1)
            : UIColor(red: 1.0, green: 0.584, blue: 0, alpha: 1)
        btnCenterMode?.backgroundColor = color
    }

    // MARK: - Panel animation
    private func animatePanel(show: Bool, completion: (() -> Void)? = nil) {
        guard let panel = adjustPanel, let root = rootView else { completion?(); return }
        if show {
            panel.transform = CGAffineTransform(translationX: 0, y: root.bounds.height)
            panel.alpha = 0
            UIView.animate(withDuration: 0.35, delay: 0, usingSpringWithDamping: 0.8,
                           initialSpringVelocity: 0, options: .curveEaseOut) {
                panel.transform = .identity; panel.alpha = 1
            } completion: { _ in completion?() }
        } else {
            UIView.animate(withDuration: 0.25, options: .curveEaseIn) {
                panel.transform = CGAffineTransform(translationX: 0, y: root.bounds.height * 0.5)
                panel.alpha = 0
            } completion: { _ in completion?() }
        }
    }

    // MARK: - Debug panel
    private func setupDebugPanel() {
        guard let root = rootView else { return }
        let btn = UIButton(type: .system)
        btn.frame = CGRect(x: 8, y: root.bounds.height / 2 - 15, width: 60, height: 30)
        btn.autoresizingMask = [.flexibleTopMargin, .flexibleBottomMargin]
        btn.setTitle("DEBUG", for: .normal)
        btn.titleLabel?.font = .systemFont(ofSize: 9)
        btn.tintColor = .white
        btn.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        btn.layer.cornerRadius = 10
        btn.addTarget(self, action: #selector(toggleDebug), for: .touchUpInside)
        root.addSubview(btn)
    }
    @objc private func toggleDebug() {
        overlayView?.isHidden = !(overlayView?.isHidden ?? true)
    }

    // MARK: - Actions
    @objc private func sliderChanged(_ slider: UISlider) {
        let norm = (slider.value - 0.5) * 2  // –1…1
        onZSliderChanged?(editMode, norm)
    }
    @objc private func didTapConfirm() { onAdjustConfirm?() }
    @objc private func didTapCancel()  { onAdjustCancel?()  }
    @objc private func didTapOverlay() { onAdjustCancel?()  }

    // MARK: - Helpers
    private func makeIconButton(systemName: String, action: @escaping () -> Void) -> UIButton {
        let btn = UIButton(type: .system)
        btn.setImage(UIImage(systemName: systemName), for: .normal)
        btn.tintColor = .white
        btn.backgroundColor = UIColor.black.withAlphaComponent(0.4)
        btn.layer.cornerRadius = 22
        btn.addAction(UIAction { _ in action() }, for: .touchUpInside)
        return btn
    }

    private func makeCaptureButton(action: @escaping () -> Void) -> UIView {
        let btn = UIButton(frame: CGRect(x: 0, y: 0, width: 72, height: 72))
        btn.backgroundColor = UIColor(white: 0.96, alpha: 1)
        btn.layer.cornerRadius = 36
        btn.layer.borderWidth = 5; btn.layer.borderColor = UIColor.white.withAlphaComponent(0.7).cgColor
        btn.addAction(UIAction { _ in action() }, for: .touchUpInside)
        return btn
    }

    private func makeMenuButton(label: String, systemName: String, action: @escaping () -> Void) -> UIView {
        let btn = UIButton(frame: CGRect(x: 0, y: 0, width: 64, height: 64))
        btn.setTitle(label, for: .normal)
        btn.setImage(UIImage(systemName: systemName), for: .normal)
        btn.tintColor = .white; btn.setTitleColor(.white, for: .normal)
        btn.titleLabel?.font = .boldSystemFont(ofSize: 11)
        btn.backgroundColor = UIColor.black.withAlphaComponent(0.6)
        btn.layer.cornerRadius = 20
        btn.imageView?.contentMode = .scaleAspectFit
        // icon above label
        btn.semanticContentAttribute = .forceLeftToRight
        btn.contentVerticalAlignment = .center
        btn.addAction(UIAction { _ in action() }, for: .touchUpInside)
        return btn
    }

    private func buildDirButton(systemName: String, action: @escaping () -> Void) -> UIButton {
        let btn = UIButton(type: .system)
        btn.setImage(UIImage(systemName: systemName), for: .normal)
        btn.tintColor = .white
        btn.backgroundColor = UIColor.white.withAlphaComponent(0.2)
        btn.layer.cornerRadius = 26
        // Long-press to repeat
        btn.addTarget(self, action: #selector(nudgeBtnDown(_:)), for: .touchDown)
        btn.addTarget(self, action: #selector(nudgeBtnUp(_:)), for: [.touchUpInside, .touchUpOutside, .touchCancel])
        // Store the action in tag... we use a closure map instead
        holdActions[btn] = action
        return btn
    }

    // Hold-to-repeat infrastructure (mirrors Android setContinuousClickListener)
    private var holdActions: [UIButton: () -> Void] = [:]

    @objc private func nudgeBtnDown(_ sender: UIButton) {
        UIView.animate(withDuration: 0.1) { sender.transform = CGAffineTransform(scaleX: 0.9, y: 0.9) }
        holdActions[sender]?()
        let timer = Timer.scheduledTimer(withTimeInterval: 0.016, repeats: true) { [weak self, weak sender] _ in
            guard let sender else { return }
            self?.holdActions[sender]?()
        }
        nudgeTimers[sender] = timer
    }

    @objc private func nudgeBtnUp(_ sender: UIButton) {
        nudgeTimers[sender]?.invalidate()
        nudgeTimers[sender] = nil
        UIView.animate(withDuration: 0.1) { sender.transform = .identity }
    }
}

// MARK: - Inline CollectionView support
private class SelectionDataSource: NSObject, UICollectionViewDataSource {
    let items: [String]; let isModel: Bool; let rotation: Int
    init(items: [String], isModel: Bool, rotation: Int) {
        self.items = items; self.isModel = isModel; self.rotation = rotation
    }
    func collectionView(_ cv: UICollectionView, numberOfItemsInSection s: Int) -> Int { items.count }
    func collectionView(_ cv: UICollectionView, cellForItemAt ip: IndexPath) -> UICollectionViewCell {
        let cell = cv.dequeueReusableCell(withReuseIdentifier: "cell", for: ip)
        cell.subviews.forEach { $0.removeFromSuperview() }
        let size: CGFloat = 80
        if isModel {
            let iv = UIImageView(frame: CGRect(x: 0, y: 0, width: size, height: size))
            iv.image = UIImage(systemName: "cube"); iv.tintColor = .white
            iv.contentMode = .scaleAspectFit; iv.backgroundColor = UIColor.black.withAlphaComponent(0.5)
            iv.layer.cornerRadius = 20; iv.clipsToBounds = true
            let r: CGFloat = rotation == 90 ? -.pi/2 : rotation == 270 ? .pi/2 : 0
            iv.transform = CGAffineTransform(rotationAngle: r)
            cell.addSubview(iv)
        } else {
            let lbl = UILabel(frame: CGRect(x: 0, y: 0, width: size, height: size))
            lbl.text = items[ip.item]; lbl.textColor = .white
            lbl.font = .boldSystemFont(ofSize: 20); lbl.textAlignment = .center
            lbl.layer.cornerRadius = size/2; lbl.clipsToBounds = true
            lbl.backgroundColor = UIColor.black.withAlphaComponent(0.5)
            lbl.layer.borderWidth = 2; lbl.layer.borderColor = UIColor.white.withAlphaComponent(0.3).cgColor
            cell.addSubview(lbl)
        }
        return cell
    }
}

private class SelectionDelegate: NSObject, UICollectionViewDelegate {
    let isModel: Bool; let items: [String]
    let onModel: (String) -> Void; let onSize: (Float) -> Void
    init(isModel: Bool, items: [String], onModel: @escaping (String) -> Void, onSize: @escaping (Float) -> Void) {
        self.isModel = isModel; self.items = items; self.onModel = onModel; self.onSize = onSize
    }
    func collectionView(_ cv: UICollectionView, didSelectItemAt ip: IndexPath) {
        let item = items[ip.item]
        if isModel { onModel(item) } else { onSize(Float(item) ?? 18) }
    }
}
