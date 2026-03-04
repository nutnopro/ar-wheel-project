// Modules/ARRendering.swift
import ARKit
import RealityKit
import UIKit
import simd

// ─────────────────────────────────────────────────────────────────────────────
// Quality score for anchor upgrade comparison
// ─────────────────────────────────────────────────────────────────────────────
private struct AnchorQuality {
    let bboxRatio: Float
    func score() -> Float { bboxRatio }
}

// ─────────────────────────────────────────────────────────────────────────────
// Per-wheel tracking state (mirrors Android WheelState)
// ─────────────────────────────────────────────────────────────────────────────
private class WheelState {
    // Anchor
    var anchor: AnchorEntity?
    var anchorQuality: AnchorQuality?
    var isFrozen: Bool = false
    var isManuallyLocked: Bool = false

    // Manual adjustment
    var manualOffsetRight: Float = 0
    var manualOffsetUp: Float = 0
    var manualOffsetForward: Float = 0
    var manualRotH: Float = 0   // degrees around planeUp
    var manualRotV: Float = 0   // degrees around planeRight
    var manualRotRoll: Float = 0

    // Plane axes stored when anchor placed
    var planeRight: SIMD3<Float> = SIMD3<Float>(1, 0, 0)
    var planeUp: SIMD3<Float>    = SIMD3<Float>(0, 1, 0)
    var planeDepth: SIMD3<Float> = SIMD3<Float>(0, 0, 1)

    // Position history (max 20, render uses avg of last 5)
    var posHistory: [SIMD3<Float>] = []
    // Rotation history (max 20, adaptive window 6..20)
    var rotHistory: [simd_quatf] = []
    var rotWindowSize: Int = 20

    var lastScreenCenter: CGPoint = .zero
    var lastScreenBounds: CGRect  = .zero

    // Detection bookkeeping
    var detectionHits: Int = 0
    var lastDetectionTime: TimeInterval = 0
    var missFrames: Int = 0

    // Pre-anchor stability
    var stableFrames: Int = 0
    var isReadyToAnchor: Bool = false
    var lastCenter: SIMD3<Float>?
    var lastRot: simd_quatf?
}

// ─────────────────────────────────────────────────────────────────────────────
class ARRendering {
    // MARK: - Constants (mirror Android)
    private static let donutPoints          = 8
    private static let donutRadiusPct: Float = 0.72
    private static let posHistorySize       = 20
    private static let posRenderSize        = 5
    private static let rotHistorySize       = 20
    private static let rotRecentSize        = 6
    private static let rotDivergeDeg: Float  = 20
    private static let bboxHistorySize      = 15
    private static let defaultWheelDiameterM: Float = 0.4572   // 18 inch
    private static let snapMultiplier: Float = 1.2
    private static let stableFramesRequired = 15
    private static let posStableM: Float    = 0.025
    private static let rotStableDeg: Float  = 4
    private static let minBboxRatio: Float  = 0.45
    private static let anchorUpgradeMargin: Float = 0.05
    private static let anchorMissLimit      = 30
    private static let unanchoredTimeoutS: TimeInterval = 1.5
    private static let infMinIntervalS: TimeInterval    = 0.050
    private static let infMaxIntervalS: TimeInterval    = 0.500
    private static let htTargetS: TimeInterval          = 0.033
    private static let htMinIntervalS: TimeInterval     = 0.016
    private static let htMaxIntervalS: TimeInterval     = 0.200
    private static let minAlpha: Float = 0.04
    private static let maxAlpha: Float = 0.60
    private static let minDist: Float  = 0.02
    private static let maxDist: Float  = 0.30

    // MARK: - Dependencies
    private weak var arView: ARView?
    private let onnxOverlayView: OnnxOverlayView
    private let modelManager = ModelManager()
    private let frameConverter = FrameConverter()
    private let mlHandler = MLHandler()

    // MARK: - State
    private var previousMode: ARMode?
    private var lastInferenceTime: TimeInterval = 0
    private var lastHitTestTime: TimeInterval = 0
    private var infInterval: TimeInterval = ARRendering.infMinIntervalS
    private var htInterval: TimeInterval = ARRendering.htMinIntervalS

    // Marker-based: imageAnchor name → entity
    private var imageAnchorMap: [String: Entity] = [:]
    private var markerlessActiveModels: [Entity] = []
    private var wheelStates: [ObjectIdentifier: WheelState] = [:]

    var latestDetections: [Detection] = []
    var isNewDetection: Bool = false
    private var bboxCountHistory: [Int] = []
    private var anyMotionThisFrame: Bool = false

    var snapThreshold: Float = ARRendering.defaultWheelDiameterM
    var modelPath: String? = nil
    var selectedModel: Entity? = nil
    var onShowAdjustmentUI: ((Bool) -> Void)?

    // MARK: - Init
    init(arView: ARView, onnxOverlayView: OnnxOverlayView) {
        self.arView = arView
        self.onnxOverlayView = onnxOverlayView
    }

    // MARK: - Nudge (called from UI main thread)
    func startNudging(_ model: Entity) {
        let ws = wheelStates[ObjectIdentifier(model)]
        guard let ws else { return }
        // If not yet anchored but ready, create anchor now
        if ws.anchor == nil, ws.isReadyToAnchor {
            let recent = Array(ws.posHistory.suffix(ARRendering.posRenderSize))
            let pos = recent.isEmpty ? model.position(relativeTo: nil) : average3(recent)
            let rot = adaptiveRotAverage(ws)
            let transform = float4x4(rot, pos)
            let anchorEntity = AnchorEntity(world: transform)
            arView?.scene.addAnchor(anchorEntity)
            ws.anchor = anchorEntity
        }
        if ws.anchor != nil {
            ws.isManuallyLocked = true
            ws.isFrozen = false
            model.isEnabled = true
            selectedModel = model
            onShowAdjustmentUI?(true)
        }
    }

    func nudgeModel(editMode: String, direction: String) {
        guard let model = selectedModel,
              let ws = wheelStates[ObjectIdentifier(model)] else { return }
        let posStep: Float = 0.005
        let rotStep: Float = 1.5
        switch (editMode, direction) {
        case ("POS", "LEFT"):  ws.manualOffsetRight -= posStep
        case ("POS", "RIGHT"): ws.manualOffsetRight += posStep
        case ("POS", "UP"):    ws.manualOffsetUp    += posStep
        case ("POS", "DOWN"):  ws.manualOffsetUp    -= posStep
        case ("ROT", "LEFT"):  ws.manualRotH -= rotStep
        case ("ROT", "RIGHT"): ws.manualRotH += rotStep
        case ("ROT", "UP"):    ws.manualRotV -= rotStep
        case ("ROT", "DOWN"):  ws.manualRotV += rotStep
        default: break
        }
    }

    func updateZAxis(editMode: String, value: Float) {
        guard let model = selectedModel,
              let ws = wheelStates[ObjectIdentifier(model)] else { return }
        switch editMode {
        case "POS": ws.manualOffsetForward = value * 1.0
        case "ROT": ws.manualRotRoll       = value * 180.0
        default: break
        }
    }

    func finishAdjusting() {
        guard let model = selectedModel,
              let ws = wheelStates[ObjectIdentifier(model)] else { return }
        let worldPos = model.position(relativeTo: nil)
        let worldRot = model.orientation(relativeTo: nil)
        let transform = float4x4(worldRot, worldPos)
        ws.anchor?.removeFromParent()
        let newAnchor = AnchorEntity(world: transform)
        arView?.scene.addAnchor(newAnchor)
        ws.anchor = newAnchor
        ws.manualOffsetRight = 0; ws.manualOffsetUp = 0; ws.manualOffsetForward = 0
        ws.manualRotH = 0; ws.manualRotV = 0; ws.manualRotRoll = 0
        selectedModel = nil
        onShowAdjustmentUI?(false)
    }

    func cancelAdjusting() {
        guard let model = selectedModel,
              let ws = wheelStates[ObjectIdentifier(model)] else { return }
        ws.isManuallyLocked = false
        ws.manualOffsetRight = 0; ws.manualOffsetUp = 0; ws.manualOffsetForward = 0
        ws.manualRotH = 0; ws.manualRotV = 0; ws.manualRotRoll = 0
        selectedModel = nil
        onShowAdjustmentUI?(false)
    }

    // MARK: - Public model APIs
    func updateNewModel(path: String) {
        modelPath = path
        for model in markerlessActiveModels {
            modelManager.changeModel(rootNode: model, modelPath: path)
        }
    }

    func updateModelSize(sizeInch: Float) {
        let cm = sizeInch * 2.54
        snapThreshold = cm / 100
        let scale = cm / 45.72
        for model in markerlessActiveModels {
            modelManager.changeModelSize(rootNode: model, scaleFactor: scale)
        }
    }

    func clear() {
        imageAnchorMap.values.forEach { $0.removeFromParent() }
        imageAnchorMap.removeAll()
        for model in markerlessActiveModels { model.removeFromParent() }
        markerlessActiveModels.removeAll()
        wheelStates.removeAll()
        onnxOverlayView.clear()
    }

    // Image tracking setup — called by ARViewController when building config
    func makeReferenceImages(markerSize: Float = 0.15) -> Set<ARReferenceImage> {
        var set = Set<ARReferenceImage>()
        guard let markerURL = Bundle.main.resourceURL?.appendingPathComponent("Resources/markers") ??
              Bundle.main.url(forResource: "markers", withExtension: nil) else { return set }
        let files = (try? FileManager.default.contentsOfDirectory(at: markerURL, includingPropertiesForKeys: nil)) ?? []
        for url in files where url.pathExtension.lowercased() == "jpg" || url.pathExtension.lowercased() == "png" {
            if let img = UIImage(contentsOfFile: url.path)?.cgImage {
                let name = url.deletingPathExtension().lastPathComponent
                let refImg = ARReferenceImage(img, orientation: .up, physicalWidth: CGFloat(markerSize))
                refImg.name = name
                set.insert(refImg)
            }
        }
        return set
    }

    // MARK: - Render entry-point (called every ARFrame)
    func render(arView: ARView, frame: ARFrame, mode: ARMode, deviceOrientation: UIDeviceOrientation) {
        if previousMode.map({ different($0, mode) }) ?? true {
            handleModeSwitch()
            previousMode = mode
        }
        switch mode {
        case .markerBased:
            processMarkerBased(frame: frame)
        case .markerless:
            let t0 = Date()
            runInference(frame: frame, orientation: deviceOrientation, arView: arView)
            processHitTest(arView: arView, frame: frame)
            updateDynamicFPS(totalS: Date().timeIntervalSince(t0))
        }
    }

    // Called from ARSessionDelegate when anchors are updated (marker mode)
    func sessionDidUpdate(anchors: [ARAnchor]) {
        for anchor in anchors {
            guard let imageAnchor = anchor as? ARImageAnchor,
                  let name = imageAnchor.referenceImage.name else { continue }
            if imageAnchor.isTracked {
                if imageAnchorMap[name] == nil {
                    let anchorEntity = AnchorEntity(anchor: imageAnchor)
                    arView?.scene.addAnchor(anchorEntity)
                    guard let mp = modelPath else { continue }
                    modelManager.createNewModel(modelPath: mp) { [weak self] entity in
                        guard let self else { return }
                        entity.isEnabled = true
                        anchorEntity.addChild(entity)
                        self.imageAnchorMap[name] = anchorEntity
                    }
                }
            } else {
                if let entity = imageAnchorMap.removeValue(forKey: name) {
                    entity.removeFromParent()
                }
            }
        }
    }

    // MARK: - Mode switch
    private func handleModeSwitch() {
        selectedModel = nil
        DispatchQueue.main.async { self.onShowAdjustmentUI?(false) }
        if previousMode == .markerBased {
            imageAnchorMap.values.forEach { $0.removeFromParent() }
            imageAnchorMap.removeAll()
        }
        if previousMode == .markerless {
            wheelStates.values.forEach { $0.anchor?.removeFromParent() }
            wheelStates.removeAll()
            markerlessActiveModels.forEach { $0.removeFromParent() }
            markerlessActiveModels.removeAll()
            onnxOverlayView.clear()
            bboxCountHistory.removeAll()
        }
    }

    // MARK: - Marker-based (legacy, via session delegate)
    private func processMarkerBased(frame: ARFrame) {
        // Tracking handled in sessionDidUpdate(anchors:)
    }

    // MARK: - Inference
    private func runInference(frame: ARFrame, orientation: UIDeviceOrientation, arView: ARView) {
        let now = Date().timeIntervalSinceReferenceDate
        guard now - lastInferenceTime >= infInterval else { return }
        lastInferenceTime = now

        let tensor = frameConverter.convertFrameToTensor(frame, deviceOrientation: orientation)
        let viewSize = arView.bounds.size
        mlHandler.runInferenceAsync(tensor: tensor, deviceOrientation: orientation, viewSize: viewSize) { [weak self] results in
            self?.latestDetections = results
            self?.isNewDetection = true
            self?.onnxOverlayView.updateDetections(results)
        }
    }

    // MARK: - Hit-test & pose
    private func processHitTest(arView: ARView, frame: ARFrame) {
        renderLockedModels()

        let now = Date().timeIntervalSinceReferenceDate
        guard now - lastHitTestTime >= htInterval else { return }
        lastHitTestTime = now
        guard isNewDetection else { return }
        isNewDetection = false

        let vw = Float(arView.bounds.width)
        let vh = Float(arView.bounds.height)
        let camTransform = frame.camera.transform
        let camPos = SIMD3<Float>(camTransform.columns.3.x, camTransform.columns.3.y, camTransform.columns.3.z)

        // Bootstrap bbox history
        if bboxCountHistory.isEmpty, !latestDetections.isEmpty {
            let half = ARRendering.bboxHistorySize / 2
            for _ in 0 ..< half { bboxCountHistory.append(latestDetections.count) }
        }
        if bboxCountHistory.count >= ARRendering.bboxHistorySize { bboxCountHistory.removeFirst() }
        bboxCountHistory.append(latestDetections.count)
        let targetCount = getModeBboxCount()

        let workDets = latestDetections.count > targetCount
            ? Array(latestDetections.sorted { $0.confidence > $1.confidence }.prefix(targetCount))
            : latestDetections

        var claimed = Set<ObjectIdentifier>()
        anyMotionThisFrame = false

        for det in workDets {
            let bbox = det.boundingBox

            // Hit points + outlier removal
            let rawPts = collectHitPoints(arView: arView, bbox: bbox, vw: vw, vh: vh)
            guard !rawPts.isEmpty else { continue }
            let hitPts = removeOutliers(rawPts)
            guard !hitPts.isEmpty else { continue }

            // Center
            let center = average3(hitPts)

            // Back-project → must be inside bbox
            let bboxAbs = CGRect(x: CGFloat(bbox.minX) * arView.bounds.width,
                                 y: CGFloat(bbox.minY) * arView.bounds.height,
                                 width: CGFloat(bbox.width) * arView.bounds.width,
                                 height: CGFloat(bbox.height) * arView.bounds.height)
            if let screenPt = arView.project(center) {
                guard bboxAbs.contains(screenPt) else { continue }
            }

            // Plane normal + rotation
            let normal   = planeNormal(pts: hitPts, center: center, camPos: camPos)
            let planeRot = lookRotationForward(normal: normal)
            let planeRight = buildPlaneRight(normal: normal)
            let planeUp    = simd_normalize(simd_cross(normal, planeRight))

            // Snap
            let minGap = snapThreshold * ARRendering.snapMultiplier
            guard let model = snapToModel(center: center, claimed: claimed, minGap: minGap)
                            ?? getOrCreateMarkerlessModel(targetCount: targetCount, claimed: claimed)
            else { continue }

            let id = ObjectIdentifier(model)
            claimed.insert(id)
            let ws = wheelStates[id] ?? { let s = WheelState(); wheelStates[id] = s; return s }()

            if ws.isManuallyLocked { continue }

            ws.lastDetectionTime = now
            ws.missFrames = 0
            ws.detectionHits += 1
            if ws.detectionHits >= 3 { model.isEnabled = true }

            let bboxW = Float(bbox.width)  * vw
            let bboxH = Float(bbox.height) * vh
            let ratio = max(bboxW, bboxH) > 0 ? min(bboxW, bboxH) / max(bboxW, bboxH) : 0

            if ws.posHistory.count >= ARRendering.posHistorySize { ws.posHistory.removeFirst() }
            ws.posHistory.append(center)

            if ws.rotHistory.count >= ARRendering.rotHistorySize { ws.rotHistory.removeFirst() }
            ws.rotHistory.append(planeRot)
            let bestRot = adaptiveRotAverage(ws)

            let renderPos = average3(Array(ws.posHistory.suffix(ARRendering.posRenderSize)))

            // worldToScreen visibility
            if let modelScreen = arView.project(renderPos) {
                ws.lastScreenCenter = modelScreen
                let halfW = CGFloat(bboxW / 2), halfH = CGFloat(bboxH / 2)
                ws.lastScreenBounds = CGRect(x: modelScreen.x - halfW, y: modelScreen.y - halfH,
                                             width: CGFloat(bboxW), height: CGFloat(bboxH))
                if !bboxAbs.contains(modelScreen), ws.anchor == nil {
                    model.isEnabled = false; continue
                }
            }

            // Stability
            let posStable = ws.lastCenter == nil || simd_length(ws.lastCenter! - center) < ARRendering.posStableM
            let rotStable = ws.lastRot == nil || angleBetween(ws.lastRot!, planeRot) < ARRendering.rotStableDeg
            if posStable && rotStable {
                ws.stableFrames += 1
                if ws.stableFrames >= ARRendering.stableFramesRequired { ws.isReadyToAnchor = true }
            } else {
                ws.stableFrames = 0; ws.isReadyToAnchor = false
            }
            ws.lastCenter = center; ws.lastRot = planeRot

            // Auto-upgrade anchor
            if confirmDetection(bboxRatio: ratio) {
                let quality = AnchorQuality(bboxRatio: ratio)
                if ws.anchorQuality == nil || quality.score() > ws.anchorQuality!.score() + ARRendering.anchorUpgradeMargin {
                    ws.anchor?.removeFromParent()
                    let anchorEntity = AnchorEntity(world: float4x4(bestRot, renderPos))
                    arView.scene.addAnchor(anchorEntity)
                    ws.anchor = anchorEntity
                    ws.anchorQuality = quality
                    ws.planeRight = planeRight
                    ws.planeUp    = planeUp
                    ws.isFrozen   = false
                    ws.missFrames = 0
                }
            }

            // Smooth render
            let alpha = dynamicAlpha(cur: model.position(relativeTo: nil), tgt: renderPos)
            let mixPos = mix(model.position(relativeTo: nil), renderPos, t: alpha)
            let mixRot = simd_slerp(model.orientation(relativeTo: nil), bestRot, alpha)
            model.setPosition(mixPos, relativeTo: nil)
            model.setOrientation(mixRot, relativeTo: nil)
            if simd_length(model.position(relativeTo: nil) - renderPos) > 0.02 { anyMotionThisFrame = true }
        }

        handleMissedModels(claimed: claimed, now: now)
    }

    // MARK: - Apply manual pose
    private func applyManualPose(model: Entity, ws: WheelState) {
        guard let anchor = ws.anchor else { return }
        let ap = anchor.transform
        let basePos = SIMD3<Float>(ap.matrix.columns.3.x, ap.matrix.columns.3.y, ap.matrix.columns.3.z)
        let baseRot = ap.rotation

        let newPos = basePos
            + ws.planeRight  * ws.manualOffsetRight
            + ws.planeUp     * ws.manualOffsetUp
            + ws.planeDepth  * ws.manualOffsetForward

        let rotH = simd_quatf(angle: ws.manualRotH * .pi / 180, axis: ws.planeUp)
        let rotV = simd_quatf(angle: ws.manualRotV * .pi / 180, axis: ws.planeRight)
        let rotZ = simd_quatf(angle: ws.manualRotRoll * .pi / 180, axis: ws.planeDepth)

        model.setPosition(newPos, relativeTo: nil)
        model.children.forEach { child in
            child.setOrientation(simd_normalize(rotH * rotZ * rotV), relativeTo: nil)
        }
    }

    private func renderLockedModels() {
        for model in markerlessActiveModels {
            guard let ws = wheelStates[ObjectIdentifier(model)], ws.isManuallyLocked else { continue }
            applyManualPose(model: model, ws: ws)
        }
    }

    // MARK: - Missed models
    private func handleMissedModels(claimed: Set<ObjectIdentifier>, now: TimeInterval) {
        for model in markerlessActiveModels {
            let id = ObjectIdentifier(model)
            if claimed.contains(id) { continue }
            guard let ws = wheelStates[id] else { continue }

            if let anchor = ws.anchor {
                if !ws.isFrozen && !ws.isManuallyLocked {
                    ws.missFrames += 1
                    if ws.missFrames >= ARRendering.anchorMissLimit {
                        ws.isFrozen = true; model.isEnabled = false
                    }
                }
                if !ws.isManuallyLocked {
                    let ap = anchor.transform.matrix
                    let anchorPos = SIMD3<Float>(ap.columns.3.x, ap.columns.3.y, ap.columns.3.z)
                    model.setPosition(anchorPos, relativeTo: nil)
                    model.setOrientation(anchor.transform.rotation, relativeTo: nil)
                }
            } else {
                if now - ws.lastDetectionTime > ARRendering.unanchoredTimeoutS {
                    model.isEnabled = false
                    if model === selectedModel {
                        selectedModel = nil
                        DispatchQueue.main.async { self.onShowAdjustmentUI?(false) }
                    }
                    resetUnanchoredState(ws)
                }
            }
        }
    }

    // MARK: - Snap helper
    private func snapToModel(center: SIMD3<Float>, claimed: Set<ObjectIdentifier>, minGap: Float) -> Entity? {
        func hasRealPos(_ e: Entity) -> Bool { wheelStates[ObjectIdentifier(e)]?.posHistory.isEmpty == false }

        let anchored = markerlessActiveModels
            .filter { !claimed.contains(ObjectIdentifier($0)) && wheelStates[ObjectIdentifier($0)]?.anchor != nil && hasRealPos($0) }
            .min { simd_length($0.position(relativeTo: nil) - center) < simd_length($1.position(relativeTo: nil) - center) }
        if let a = anchored, simd_length(a.position(relativeTo: nil) - center) < minGap { return a }

        let free = markerlessActiveModels
            .filter { !claimed.contains(ObjectIdentifier($0)) && wheelStates[ObjectIdentifier($0)]?.anchor == nil && hasRealPos($0) }
            .min { simd_length($0.position(relativeTo: nil) - center) < simd_length($1.position(relativeTo: nil) - center) }
        if let f = free, simd_length(f.position(relativeTo: nil) - center) < minGap { return f }
        return nil
    }

    // MARK: - Outlier removal (2σ)
    private func removeOutliers(_ pts: [SIMD3<Float>]) -> [SIMD3<Float>] {
        guard pts.count > 3 else { return pts }
        let centroid = average3(pts)
        let dists = pts.map { simd_length($0 - centroid) }
        let mean = dists.reduce(0, +) / Float(dists.count)
        let sd  = sqrt(dists.map { ($0 - mean) * ($0 - mean) }.reduce(0, +) / Float(dists.count))
        let threshold = mean + 2 * sd
        let filtered = pts.filter { simd_length($0 - centroid) <= threshold }
        return filtered.isEmpty ? pts : filtered
    }

    // MARK: - Adaptive rotation average
    private func adaptiveRotAverage(_ ws: WheelState) -> simd_quatf {
        guard !ws.rotHistory.isEmpty else { return simd_quatf(angle: 0, axis: SIMD3<Float>(0, 1, 0)) }
        if ws.rotHistory.count < ARRendering.rotRecentSize { return averageQuaternions(ws.rotHistory) }

        let fullAvg   = averageQuaternions(ws.rotHistory)
        let recentAvg = averageQuaternions(Array(ws.rotHistory.suffix(ARRendering.rotRecentSize)))
        let diver = angleBetween(fullAvg, recentAvg)

        if diver > ARRendering.rotDivergeDeg {
            if ws.rotWindowSize > ARRendering.rotRecentSize { ws.rotWindowSize -= 1 }
            else { ws.rotWindowSize = ARRendering.rotRecentSize }
        } else {
            if ws.rotWindowSize < ARRendering.rotHistorySize { ws.rotWindowSize += 1 }
        }
        return averageQuaternions(Array(ws.rotHistory.suffix(ws.rotWindowSize)))
    }

    // MARK: - Hit points (centre + donut ring)
    private func collectHitPoints(arView: ARView, bbox: CGRect, vw: Float, vh: Float) -> [SIMD3<Float>] {
        var pts: [SIMD3<Float>] = []
        let cx = Float(bbox.midX) * vw
        let cy = Float(bbox.midY) * vh

        if let hit = arView.raycast(from: CGPoint(x: CGFloat(cx), y: CGFloat(cy)),
                                    allowing: .estimatedPlane, alignment: .any).first {
            pts.append(SIMD3<Float>(hit.worldTransform.columns.3.x,
                                    hit.worldTransform.columns.3.y,
                                    hit.worldTransform.columns.3.z))
        }

        let radius = min(Float(bbox.width) * vw, Float(bbox.height) * vh) * ARRendering.donutRadiusPct / 2
        let n = ARRendering.donutPoints
        for i in 0 ..< n {
            let t = 2 * Float.pi * Float(i) / Float(n)
            let px = cx + radius * cos(t)
            let py = cy + radius * sin(t)
            if let hit = arView.raycast(from: CGPoint(x: CGFloat(px), y: CGFloat(py)),
                                        allowing: .estimatedPlane, alignment: .any).first {
                pts.append(SIMD3<Float>(hit.worldTransform.columns.3.x,
                                        hit.worldTransform.columns.3.y,
                                        hit.worldTransform.columns.3.z))
            }
        }
        return pts
    }

    // MARK: - Plane normal (facing camera)
    private func planeNormal(pts: [SIMD3<Float>], center: SIMD3<Float>, camPos: SIMD3<Float>) -> SIMD3<Float> {
        guard pts.count >= 3 else { return simd_normalize(camPos - center) }
        var accum = SIMD3<Float>(0, 0, 0)
        for i in pts.indices {
            accum += simd_cross(pts[i] - center, pts[(i + 1) % pts.count] - center)
        }
        let len = simd_length(accum)
        guard len > 1e-6, !len.isNaN else { return simd_normalize(camPos - center) }
        let n = simd_normalize(accum)
        return simd_dot(n, simd_normalize(camPos - center)) < 0 ? -n : n
    }

    // MARK: - lookRotationForward: +Y aligns with plane normal
    private func lookRotationForward(normal: SIMD3<Float>) -> simd_quatf {
        let yAxis = simd_normalize(normal)
        let ref: SIMD3<Float> = abs(simd_dot(yAxis, SIMD3<Float>(0, 0, 1))) < 0.99
                                ? SIMD3<Float>(0, 0, 1) : SIMD3<Float>(1, 0, 0)
        let xAxis = simd_normalize(simd_cross(ref, yAxis))
        let zAxis = simd_normalize(simd_cross(yAxis, xAxis))
        // Build from 3×3 rotation matrix columns: [xAxis | yAxis | zAxis]
        let m = float3x3(xAxis, yAxis, zAxis)
        return simd_quaternion(m)
    }

    private func buildPlaneRight(normal: SIMD3<Float>) -> SIMD3<Float> {
        let up: SIMD3<Float> = abs(simd_dot(normal, SIMD3<Float>(0, 1, 0))) > 0.99
                               ? SIMD3<Float>(0, 0, 1) : SIMD3<Float>(0, 1, 0)
        return simd_normalize(simd_cross(up, normal))
    }

    // MARK: - Confirmation
    private func confirmDetection(bboxRatio: Float) -> Bool { bboxRatio >= ARRendering.minBboxRatio }

    // MARK: - Model pool
    private func getOrCreateMarkerlessModel(targetCount: Int, claimed: Set<ObjectIdentifier>) -> Entity? {
        // Reuse invisible, unanchored slot
        if let recycled = markerlessActiveModels.first(where: {
            !$0.isEnabled && wheelStates[ObjectIdentifier($0)]?.anchor == nil
        }) {
            let ws = wheelStates[ObjectIdentifier(recycled)] ?? { let s = WheelState(); wheelStates[ObjectIdentifier(recycled)] = s; return s }()
            resetUnanchoredState(ws)
            return recycled
        }
        // Hard cap
        let existingActive = markerlessActiveModels.filter { e in
            let id = ObjectIdentifier(e)
            return !claimed.contains(id) && (e.isEnabled || wheelStates[id]?.anchor != nil)
        }.count
        guard claimed.count + existingActive < targetCount else { return nil }

        guard let mp = modelPath else { return nil }
        // Create placeholder synchronously, model loads async into it
        let placeholder = Entity(); placeholder.isEnabled = false
        modelManager.createNewModel(modelPath: mp) { [weak self] loaded in
            guard let self else { return }
            placeholder.addChild(loaded)
        }
        arView?.scene.addAnchor(AnchorEntity(world: .identity))  // anchor so it lives in the scene
        // We keep the entity as a free-floating entity parented to a throwaway world anchor
        markerlessActiveModels.append(placeholder)
        wheelStates[ObjectIdentifier(placeholder)] = WheelState()
        return placeholder
    }

    // MARK: - Bbox mode (median)
    private func getModeBboxCount() -> Int {
        guard !bboxCountHistory.isEmpty else { return 1 }
        let sorted = bboxCountHistory.sorted()
        return max(1, sorted[sorted.count / 2])
    }

    // MARK: - State reset
    private func resetUnanchoredState(_ ws: WheelState) {
        ws.posHistory.removeAll(); ws.rotHistory.removeAll()
        ws.stableFrames = 0; ws.isReadyToAnchor = false
        ws.detectionHits = 0; ws.lastCenter = nil; ws.lastRot = nil
        ws.missFrames = 0
        ws.manualOffsetRight = 0; ws.manualOffsetUp = 0
        ws.manualRotH = 0; ws.manualRotV = 0
        ws.rotWindowSize = ARRendering.rotHistorySize
    }

    // MARK: - Dynamic FPS
    private func updateDynamicFPS(totalS: TimeInterval) {
        infInterval = (totalS * 2).clamped(to: ARRendering.infMinIntervalS...ARRendering.infMaxIntervalS)
        if totalS > ARRendering.htTargetS {
            htInterval = (totalS + 0.01).clamped(to: ARRendering.htMinIntervalS...ARRendering.htMaxIntervalS)
        } else if !anyMotionThisFrame {
            htInterval = min(htInterval + 0.008, ARRendering.htMaxIntervalS)
        } else {
            htInterval = ARRendering.htMinIntervalS
        }
    }

    // MARK: - Math utilities
    private func dist(_ a: SIMD3<Float>, _ b: SIMD3<Float>) -> Float { simd_length(a - b) }

    private func dynamicAlpha(cur: SIMD3<Float>, tgt: SIMD3<Float>) -> Float {
        let t = ((dist(cur, tgt) - ARRendering.minDist) / (ARRendering.maxDist - ARRendering.minDist)).clamped(to: 0...1)
        return ARRendering.minAlpha + (ARRendering.maxAlpha - ARRendering.minAlpha) * t
    }

    private func angleBetween(_ q1: simd_quatf, _ q2: simd_quatf) -> Float {
        let d = abs(simd_dot(q1, q2)).clamped(to: 0...1)
        return Float(2.0 * acos(Double(d))) * 180 / .pi
    }

    private func average3(_ pts: [SIMD3<Float>]) -> SIMD3<Float> {
        guard !pts.isEmpty else { return .zero }
        let sum = pts.reduce(SIMD3<Float>.zero, +)
        return sum / Float(pts.count)
    }

    private func averageQuaternions(_ qs: [simd_quatf]) -> simd_quatf {
        guard !qs.isEmpty else { return simd_quatf(angle: 0, axis: SIMD3<Float>(0, 1, 0)) }
        var avg = qs[0]
        for i in 1 ..< qs.count { avg = simd_slerp(avg, qs[i], 1.0 / Float(i + 1)) }
        return avg
    }

    private func mix(_ a: SIMD3<Float>, _ b: SIMD3<Float>, t: Float) -> SIMD3<Float> {
        a + (b - a) * t
    }

    private func different(_ a: ARMode, _ b: ARMode) -> Bool {
        switch (a, b) {
        case (.markerBased, .markerBased), (.markerless, .markerless): return false
        default: return true
        }
    }
}

// MARK: - float4x4 helper
private func float4x4(_ q: simd_quatf, _ t: SIMD3<Float>) -> simd_float4x4 {
    var m = simd_float4x4(q)
    m.columns.3 = SIMD4<Float>(t.x, t.y, t.z, 1)
    return m
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        Swift.max(range.lowerBound, Swift.min(range.upperBound, self))
    }
}
