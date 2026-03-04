// Modules/ModelManager.swift
import RealityKit
import Combine

/// Manages loading, building, and hot-swapping wheel 3D models.
/// Mirrors Android ModelManager exactly — same wheel assembly logic (front = +Y, backplate behind).
///
/// Resources live in ios/ARWheelApp/Resources/models/
class ModelManager {
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Public API

    /// Creates a root Entity and asynchronously loads the model into it.
    /// Entity starts invisible until ARRendering increments detectionHits ≥ 3.
    func createNewModel(modelPath: String, completion: @escaping (Entity) -> Void) {
        let rootNode = Entity()
        rootNode.isEnabled = false  // hidden by default

        loadModelEntity(path: modelPath) { [weak self] modelEntity in
            guard let self, let modelEntity else { return }
            self.setupWheelSystem(rootNode: rootNode, wheelNode: modelEntity)
            completion(rootNode)
        }
    }

    /// Hot-swaps the model on an existing root entity.
    func changeModel(rootNode: Entity, modelPath: String) {
        loadModelEntity(path: modelPath) { [weak self] modelEntity in
            guard let self, let modelEntity else { return }
            rootNode.children.forEach { $0.removeFromParent() }
            self.setupWheelSystem(rootNode: rootNode, wheelNode: modelEntity)
        }
    }

    /// Uniform scale: 18″ = scale 1.0
    func changeModelSize(rootNode: Entity, scaleFactor: Float) {
        rootNode.scale = SIMD3<Float>(repeating: scaleFactor)
    }

    // MARK: - Private

    /// Wheel assembly — same offset/rotation as Android ModelManager.
    ///
    /// Model coordinate system (from asset):
    ///   Front face → +Y axis
    ///   Up         → +Z axis
    ///   Right      → +X axis
    ///
    /// We offset the wheel back by halfThickness so the face sits at root origin.
    private func setupWheelSystem(rootNode: Entity, wheelNode: ModelEntity) {
        let bounds = wheelNode.model?.mesh.bounds ?? BoundingBox()
        let extents = bounds.extents  // SIMD3<Float>
        let diameter = max(extents.x, extents.y)
        let thickness = extents.z
        let halfThickness = thickness / 2

        // Shift wheel face to root origin, rotate model front (+Z) → +Y
        wheelNode.position = SIMD3<Float>(0, -halfThickness, 0)
        wheelNode.orientation = simd_quatf(angle: -.pi / 2, axis: SIMD3<Float>(1, 0, 0))

        // Backplate behind the wheel
        if let backplate = loadBackplate(diameter: diameter) {
            backplate.position = SIMD3<Float>(0, 0, -halfThickness)
            backplate.orientation = simd_quatf(angle: 0, axis: SIMD3<Float>(0, 1, 0))
            wheelNode.addChild(backplate)
        }
        rootNode.addChild(wheelNode)
    }

    private func loadBackplate(diameter: Float) -> ModelEntity? {
        guard let entity = try? Entity.load(named: "models/backplate", in: nil) as? ModelEntity
                       ?? loadSync(path: "models/backplate") else { return nil }
        entity.scale = SIMD3<Float>(diameter, diameter, 1)
        return entity
    }

    // MARK: - Loading helpers

    private func loadModelEntity(path: String, completion: @escaping (ModelEntity?) -> Void) {
        // Strip leading "models/" prefix if present — Resource name is relative to bundle
        let resourceName = path.hasPrefix("models/") ? String(path.dropFirst(7)) : path
        // Strip extension for Entity.loadAsync(named:)
        let nameNoExt = (resourceName as NSString).deletingPathExtension

        Entity.loadAsync(named: nameNoExt, in: nil)
            .receive(on: DispatchQueue.main)
            .sink(
                receiveCompletion: { result in
                    if case .failure(let err) = result {
                        print("[ModelManager] Load failed '\(path)': \(err)")
                        completion(nil)
                    }
                },
                receiveValue: { entity in
                    completion(entity as? ModelEntity ?? entity.children.first(where: { $0 is ModelEntity }) as? ModelEntity)
                }
            )
            .store(in: &cancellables)
    }

    /// Synchronous fallback (used for backplate loading on main thread).
    private func loadSync(path: String) -> ModelEntity? {
        let nameNoExt = (path as NSString).deletingPathExtension
        return try? Entity.load(named: nameNoExt, in: nil) as? ModelEntity
    }
}
