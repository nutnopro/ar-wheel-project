// Modules/OnnxOverlayView.swift
import UIKit

/// Transparent overlay view that draws detection bounding boxes.
/// Mirrors Android OnnxOverlayView exactly — same normalized-coord logic.
class OnnxOverlayView: UIView {
    private static let inputSize: CGFloat = 320

    private var detections: [Detection] = []
    private let boxColor = UIColor.cyan
    private let textColor = UIColor.blue

    // MARK: - Public

    func updateDetections(_ newDetections: [Detection]) {
        detections = newDetections
        DispatchQueue.main.async { self.setNeedsDisplay() }
    }

    func clear() {
        detections = []
        DispatchQueue.main.async { self.setNeedsDisplay() }
    }

    // MARK: - Drawing

    override func draw(_ rect: CGRect) {
        super.draw(rect)
        guard !detections.isEmpty, let ctx = UIGraphicsGetCurrentContext() else { return }

        let viewW = bounds.width
        let viewH = bounds.height

        let font = UIFont.systemFont(ofSize: 14)
        let textAttrs: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: textColor
        ]

        for det in detections {
            let b = det.boundingBox
            // boundingBox is normalized 0–1, convert to view coords
            let left   = b.minX * viewW
            let top    = b.minY * viewH
            let right  = b.maxX * viewW
            let bottom = b.maxY * viewH

            let boxRect = CGRect(x: left, y: top, width: right - left, height: bottom - top)

            ctx.setStrokeColor(boxColor.cgColor)
            ctx.setLineWidth(3)
            ctx.stroke(boxRect)

            let label = "\(Int(det.confidence * 100))%"
            let labelRect = CGRect(x: left, y: max(top - 20, 0), width: 50, height: 18)
            (label as NSString).draw(in: labelRect, withAttributes: textAttrs)
        }
    }
}
