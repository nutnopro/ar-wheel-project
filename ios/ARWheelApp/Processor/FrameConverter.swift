// Processor/FrameConverter.swift
import ARKit
import Accelerate
import UIKit

/// Converts an ARFrame's camera image (CVPixelBuffer, YCbCr 4:2:0) into
/// a CHW float tensor [3 × 320 × 320] — same layout as Android FrameConverter.
class FrameConverter {
    private static let inputSize = 320

    private var resizeBuffer: [UInt8]?

    // MARK: - Public

    func convertFrameToTensor(_ frame: ARFrame, deviceOrientation: UIDeviceOrientation) -> [Float] {
        let pixelBuffer = frame.capturedImage
        let rotation = rotationAngle(for: deviceOrientation)
        return convertPixelBuffer(pixelBuffer, rotation: rotation)
    }

    // MARK: - Private

    private func convertPixelBuffer(_ pixelBuffer: CVPixelBuffer, rotation: Int) -> [Float] {
        let size = FrameConverter.inputSize

        // Lock base address
        CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly) }

        // Build a CGImage from the YCbCr pixel buffer via CIImage (handles YUV → RGB)
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        let context = CIContext(options: [.useSoftwareRenderer: false])
        guard let cgImage = context.createCGImage(ciImage, from: ciImage.extent) else {
            return [Float](repeating: 0, count: 3 * size * size)
        }

        // Rotate + resize into a square bitmap
        guard let rotatedResized = rotateAndResize(cgImage, rotation: rotation, targetSize: size) else {
            return [Float](repeating: 0, count: 3 * size * size)
        }

        return bitmapToFloatTensor(rotatedResized, size: size)
    }

    /// Returns CGImage rotated by `degrees` and scaled to `targetSize × targetSize`.
    private func rotateAndResize(_ source: CGImage, rotation: Int, targetSize: Int) -> CGImage? {
        let size = CGFloat(targetSize)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(
            data: nil,
            width: targetSize, height: targetSize,
            bitsPerComponent: 8,
            bytesPerRow: targetSize * 4,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
        ) else { return nil }

        ctx.setFillColor(UIColor.black.cgColor)
        ctx.fill(CGRect(x: 0, y: 0, width: targetSize, height: targetSize))

        let srcW = CGFloat(source.width)
        let srcH = CGFloat(source.height)
        let scale = size / max(srcW, srcH)
        let drawW = srcW * scale
        let drawH = srcH * scale

        ctx.translateBy(x: size / 2, y: size / 2)

        switch rotation {
        case 90:
            ctx.rotate(by: .pi / 2)
        case 180:
            ctx.rotate(by: .pi)
        case 270:
            ctx.rotate(by: -.pi / 2)
        default:
            break
        }

        ctx.translateBy(x: -drawW / 2, y: -drawH / 2)
        ctx.draw(source, in: CGRect(x: 0, y: 0, width: drawW, height: drawH))

        return ctx.makeImage()
    }

    /// Converts a `targetSize × targetSize` CGImage to a CHW float array, values 0–1.
    private func bitmapToFloatTensor(_ image: CGImage, size: Int) -> [Float] {
        let pixelCount = size * size
        var rawBytes = [UInt8](repeating: 0, count: pixelCount * 4)
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(
            data: &rawBytes,
            width: size, height: size,
            bitsPerComponent: 8,
            bytesPerRow: size * 4,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
        ) else { return [Float](repeating: 0, count: 3 * pixelCount) }
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))

        var floats = [Float](repeating: 0, count: 3 * pixelCount)
        for i in 0 ..< pixelCount {
            floats[i]                  = Float(rawBytes[i * 4])     / 255.0 // R
            floats[i + pixelCount]     = Float(rawBytes[i * 4 + 1]) / 255.0 // G
            floats[i + pixelCount * 2] = Float(rawBytes[i * 4 + 2]) / 255.0 // B
        }
        return floats
    }

    /// Maps UIDeviceOrientation to the rotation angle that should be applied to the raw camera frame.
    /// Mirrors Android FrameConverter rotation logic (device rotation → frame correction).
    private func rotationAngle(for orientation: UIDeviceOrientation) -> Int {
        switch orientation {
        case .portrait:            return 90
        case .landscapeLeft:       return 180
        case .portraitUpsideDown:  return 270
        case .landscapeRight:      return 0
        default:                   return 90
        }
    }
}
