// Processor/MLHandler.swift
import Foundation
import UIKit
import onnxruntime_objc

/// Runs YOLOv11 inference via ONNX Runtime (onnxruntime-objc).
/// Mirrors Android MLHandler exactly — same confidence threshold, output shape, rotation correction.
class MLHandler {
    private static let modelName   = "Resources/yolov11n"
    private static let inputSize   = 320
    private static let confidence: Float = 0.4

    private let session: ORTSession
    private let env: ORTEnv
    private let inferenceQueue = DispatchQueue(label: "com.arwheelapp.inference", qos: .userInitiated)

    // MARK: - Init

    init() {
        env = try! ORTEnv(loggingLevel: .warning)
        guard let modelURL = Bundle.main.url(forResource: MLHandler.modelName, withExtension: "onnx") else {
            fatalError("yolov11n.onnx not found in bundle")
        }
        let options = try! ORTSessionOptions()
        try! options.setIntraOpNumThreads(2)
        try! options.setGraphOptimizationLevel(.all)
        session = try! ORTSession(env: env, modelPath: modelURL.path, sessionOptions: options)
    }

    // MARK: - Public

    /// Async inference — callback is delivered on main thread.
    func runInferenceAsync(
        tensor: [Float],
        deviceOrientation: UIDeviceOrientation,
        viewSize: CGSize,
        completion: @escaping ([Detection]) -> Void
    ) {
        inferenceQueue.async { [weak self] in
            guard let self else { return }
            let results = self.runSync(tensor: tensor, deviceOrientation: deviceOrientation)
            DispatchQueue.main.async { completion(results) }
        }
    }

    // MARK: - Private

    private func runSync(tensor: [Float], deviceOrientation: UIDeviceOrientation) -> [Detection] {
        let size = MLHandler.inputSize
        let shape: [NSNumber] = [1, 3, NSNumber(value: size), NSNumber(value: size)]

        do {
            let inputData = Data(bytes: tensor, count: tensor.count * MemoryLayout<Float>.size)
            let inputTensor = try ORTValue(
                tensorData: NSMutableData(data: inputData),
                elementType: .float,
                shape: shape
            )
            let outputs = try session.run(
                withInputs: ["images": inputTensor],
                outputNames: ["output0"],
                runOptions: nil
            )
            guard let outputValue = outputs["output0"] else { return [] }
            return try parseOutput(outputValue, deviceOrientation: deviceOrientation)
        } catch {
            print("[MLHandler] Inference error: \(error)")
            return []
        }
    }

    /// Parse [1, 300, 6] output — same logic as Android parseOutput.
    private func parseOutput(_ value: ORTValue, deviceOrientation: UIDeviceOrientation) throws -> [Detection] {
        let size = Float(MLHandler.inputSize)
        var detections: [Detection] = []

        // Get raw float data
        let tensorData = try value.tensorData() as Data
        let floats = tensorData.withUnsafeBytes { Array($0.bindMemory(to: Float.self)) }

        // Rotation to apply to normalised box coordinates
        let aiRotation = rotationDegrees(for: deviceOrientation)

        // floats shape: [1, 300, 6] → stride of 6 per box
        let numBoxes = floats.count / 6
        for i in 0 ..< numBoxes {
            let base = i * 6
            let score = floats[base + 4]
            guard score > MLHandler.confidence else { continue }

            var x1 = (floats[base + 0] / size).clamped(to: 0...1)
            var y1 = (floats[base + 1] / size).clamped(to: 0...1)
            var x2 = (floats[base + 2] / size).clamped(to: 0...1)
            var y2 = (floats[base + 3] / size).clamped(to: 0...1)

            if aiRotation != 0 {
                let pts = rotateNormRect(x1: x1, y1: y1, x2: x2, y2: y2, degrees: aiRotation)
                x1 = pts.minX; y1 = pts.minY; x2 = pts.maxX; y2 = pts.maxY
            }

            let w = x2 - x1
            let h = y2 - y1
            guard w > 0, h > 0 else { continue }

            detections.append(Detection(
                boundingBox: CGRect(x: CGFloat(x1), y: CGFloat(y1), width: CGFloat(w), height: CGFloat(h)),
                confidence: score
            ))
        }
        return detections
    }

    /// Rotate a normalised (0-1) rect around the centre 0.5, 0.5.
    private func rotateNormRect(x1: Float, y1: Float, x2: Float, y2: Float, degrees: Float) -> CGRect {
        let rad = degrees * .pi / 180
        let cos = Foundation.cos(rad)
        let sin = Foundation.sin(rad)
        let corners: [(Float, Float)] = [(x1, y1), (x2, y1), (x2, y2), (x1, y2)]
        var minX = Float.infinity, minY = Float.infinity
        var maxX = -Float.infinity, maxY = -Float.infinity
        for (cx, cy) in corners {
            let dx = cx - 0.5, dy = cy - 0.5
            let rx = cos * dx - sin * dy + 0.5
            let ry = sin * dx + cos * dy + 0.5
            minX = Swift.min(minX, rx); minY = Swift.min(minY, ry)
            maxX = Swift.max(maxX, rx); maxY = Swift.max(maxY, ry)
        }
        return CGRect(x: CGFloat(minX.clamped(to: 0...1)),
                      y: CGFloat(minY.clamped(to: 0...1)),
                      width: CGFloat((maxX - minX).clamped(to: 0...1)),
                      height: CGFloat((maxY - minY).clamped(to: 0...1)))
    }

    /// Maps device orientation to AI rotation correction angle (mirrors Android parseOutput matrix logic).
    private func rotationDegrees(for orientation: UIDeviceOrientation) -> Float {
        switch orientation {
        case .portrait:            return 0
        case .landscapeLeft:       return 270
        case .portraitUpsideDown:  return 180
        case .landscapeRight:      return 90
        default:                   return 0
        }
    }
}

// MARK: - Helpers
private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        Swift.max(range.lowerBound, Swift.min(range.upperBound, self))
    }
}
