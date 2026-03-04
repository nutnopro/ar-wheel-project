// Utils/Utils.swift
import UIKit

// ──────────────────────────────────────────────
// AR Operation Modes
// ──────────────────────────────────────────────
enum ARMode {
    case markerBased
    case markerless

    static let `default`: ARMode = .markerless
}

// ──────────────────────────────────────────────
// Detection
// ──────────────────────────────────────────────
struct Detection {
    /// Normalized bounding box (0–1 in both axes)
    let boundingBox: CGRect
    let confidence: Float
}

// ──────────────────────────────────────────────
// Extensions
// ──────────────────────────────────────────────
extension Int {
    /// Points → pixels (same semantics as Android dp extension)
    var dp: CGFloat { CGFloat(self) }
}

extension CGFloat {
    var dp: CGFloat { self }
}
