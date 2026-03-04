// Modules/ARLauncherModule.swift
// React Native native module — presents ARViewController modally.
// Mirrors Android ARLauncherModule.kt exactly (same method signature).

import Foundation
import React
import UIKit

@objc(ARLauncher)
class ARLauncherModule: NSObject {

    @objc static func requiresMainQueueSetup() -> Bool { true }

    @objc func openARActivity(
        _ initialModelPath: String,
        modelPathsJson: String,
        resolve: @escaping RCTPromiseResolveBlock,
        reject: @escaping RCTPromiseRejectBlock
    ) {
        DispatchQueue.main.async {
            guard let presentingVC = RCTPresentedViewController() else {
                reject("NO_CURRENT_ACTIVITY", "No presenting view controller", nil)
                return
            }
            let arVC = ARViewController.create(
                initialModelPath: initialModelPath,
                modelPathsJson: modelPathsJson
            )
            arVC.modalPresentationStyle = .fullScreen
            presentingVC.present(arVC, animated: true) {
                resolve(true)
            }
        }
    }
}
