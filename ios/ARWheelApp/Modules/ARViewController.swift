// Modules/ARViewController.swift
import UIKit
import ARKit
import RealityKit
import AVFoundation

/// iOS equivalent of Android ARActivity.
/// Manages lifecycle, AR session, camera permission, UI wiring, photo capture.
class ARViewController: UIViewController {

    // MARK: - Dependencies
    private lazy var arView: ARView = {
        let v = ARView(frame: view.bounds, cameraMode: .ar, automaticallyConfigureSession: false)
        v.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        return v
    }()
    private lazy var onnxOverlay = OnnxOverlayView()
    private lazy var arRendering = ARRendering(arView: arView, onnxOverlayView: onnxOverlay)
    private lazy var uiManager   = ARUIManager(rootView: view, overlayView: onnxOverlay)

    private var currentMode: ARMode = .default
    private var initialModelPath: String = "models/default.glb"
    private var modelPaths: [String] = []

    // MARK: - Entry
    static func create(initialModelPath: String, modelPathsJson: String) -> ARViewController {
        let vc = ARViewController()
        vc.initialModelPath = initialModelPath
        vc.modelPaths = (try? JSONSerialization.jsonObject(with: modelPathsJson.data(using: .utf8) ?? Data()) as? [String]) ?? []
        return vc
    }

    // MARK: - Lifecycle
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        initViews()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        checkPermAndStartAR()
        uiManager.onResume()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        arView.session.pause()
        uiManager.onPause()
    }

    // MARK: - Init views
    private func initViews() {
        // ARView
        arView.frame = view.bounds
        view.addSubview(arView)

        // ONNX overlay (invisible by default)
        onnxOverlay.frame = view.bounds
        onnxOverlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        onnxOverlay.backgroundColor = .clear
        onnxOverlay.isUserInteractionEnabled = false
        onnxOverlay.isHidden = true
        view.addSubview(onnxOverlay)

        setupEnvironmentLighting()
        uiManager.setupInterface()
        wireCallbacks()
        setupTapGesture()
        setInitialModel()
    }

    // MARK: - Lighting (Resources/environments/studio_lighting.hdr)
    private func setupEnvironmentLighting() {
        do {
            let resource = try EnvironmentResource.load(named: "environments/studio_lighting")
            arView.environment.lighting = ARView.Environment.ImageBasedLight(source: .single(resource))
        } catch {
            print("[ARViewController] HDR environment load failed: \(error)")
        }
    }

    // MARK: - Callbacks
    private func wireCallbacks() {
        uiManager.onBackClicked = { [weak self] in
            self?.dismiss(animated: true)
        }
        uiManager.onModeSelected = { [weak self] mode in
            self?.currentMode = mode
            self?.reconfigureARSession(for: mode)
        }
        uiManager.onCaptureClicked = { [weak self] in self?.takePhoto() }
        uiManager.onModelSelected = { [weak self] path in
            self?.arRendering.updateNewModel(path: "models/\(path).glb")
        }
        uiManager.onSizeSelected = { [weak self] inch in
            self?.arRendering.updateModelSize(sizeInch: inch)
        }
        uiManager.onNudge = { [weak self] editMode, dir in
            self?.arRendering.nudgeModel(editMode: editMode, direction: dir)
        }
        uiManager.onZSliderChanged = { [weak self] editMode, value in
            self?.arRendering.updateZAxis(editMode: editMode, value: value)
        }
        uiManager.onAdjustConfirm = { [weak self] in self?.arRendering.finishAdjusting() }
        uiManager.onAdjustCancel  = { [weak self] in self?.arRendering.cancelAdjusting()  }

        arRendering.onShowAdjustmentUI = { [weak self] show in
            DispatchQueue.main.async { self?.uiManager.showAdjustmentPanel(show) }
        }
    }

    // MARK: - Tap to nudge
    private func setupTapGesture() {
        let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        arView.addGestureRecognizer(tap)
    }

    @objc private func handleTap(_ recognizer: UITapGestureRecognizer) {
        let loc = recognizer.location(in: arView)
        // Find nearest entity at tap point
        guard let entity = arView.entity(at: loc)?.anchor else { return }
        // Walk up to find root (markerlessActiveModel)
        arRendering.startNudging(entity)
        arRendering.onShowAdjustmentUI = { [weak self] show in
            DispatchQueue.main.async { self?.uiManager.showAdjustmentPanel(show) }
        }
    }

    // MARK: - Initial model
    private func setInitialModel() {
        arRendering.modelPath = initialModelPath
        uiManager.setModels(modelPaths)
    }

    // MARK: - Camera permission & AR start
    private func checkPermAndStartAR() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            startARSession()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted { self?.startARSession() }
                    else { self?.showCameraPermissionAlert() }
                }
            }
        default:
            showCameraPermissionAlert()
        }
    }

    private func showCameraPermissionAlert() {
        let alert = UIAlertController(title: "Camera Required",
                                      message: "Camera access is required for AR.",
                                      preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }

    // MARK: - AR Session
    private func startARSession() {
        let config = makeARConfig(for: currentMode)
        arView.session.delegate = self
        arView.session.run(config, options: [.resetTracking, .removeExistingAnchors])
        startARLoop()
    }

    private func reconfigureARSession(for mode: ARMode) {
        let config = makeARConfig(for: mode)
        arView.session.run(config, options: [.removeExistingAnchors])
    }

    private func makeARConfig(for mode: ARMode) -> ARConfiguration {
        let config = ARWorldTrackingConfiguration()
        config.planeDetection = [.horizontal, .vertical]
        config.environmentTexturing = .automatic
        config.isAutoFocusEnabled = true
        if ARWorldTrackingConfiguration.supportsSceneReconstruction(.mesh) {
            config.sceneReconstruction = .mesh
        }
        if mode == .markerBased {
            config.detectionImages = arRendering.makeReferenceImages()
            config.maximumNumberOfTrackedImages = 4
        }
        return config
    }

    // MARK: - Frame loop
    private func startARLoop() {
        // Handled via ARSessionDelegate.session(_:didUpdate:) below
    }

    // MARK: - Photo capture
    private func takePhoto() {
        arView.snapshot(saveToHDR: false) { [weak self] image in
            guard let image else { return }
            UIImageWriteToSavedPhotosAlbum(image, self, #selector(self?.photoSaved(_:didFinishSavingWithError:contextInfo:)), nil)
        }
    }

    @objc private func photoSaved(_ image: UIImage,
                                  didFinishSavingWithError error: Error?,
                                  contextInfo: UnsafeRawPointer) {
        let msg = error == nil ? "Photo saved!" : "Save failed"
        let alert = UIAlertController(title: nil, message: msg, preferredStyle: .alert)
        present(alert, animated: true)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { alert.dismiss(animated: true) }
    }
}

// MARK: - ARSessionDelegate
extension ARViewController: ARSessionDelegate {
    func session(_ session: ARSession, didUpdate frame: ARFrame) {
        let orientation = UIDevice.current.orientation
        arRendering.render(arView: arView, frame: frame, mode: currentMode, deviceOrientation: orientation)
    }

    func session(_ session: ARSession, didAdd anchors: [ARAnchor]) {
        if currentMode == .markerBased { arRendering.sessionDidUpdate(anchors: anchors) }
    }
    func session(_ session: ARSession, didUpdate anchors: [ARAnchor]) {
        if currentMode == .markerBased { arRendering.sessionDidUpdate(anchors: anchors) }
    }
    func session(_ session: ARSession, didRemove anchors: [ARAnchor]) {
        if currentMode == .markerBased { arRendering.sessionDidUpdate(anchors: anchors) }
    }
}
