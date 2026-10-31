//
//  MediaFitTests.swift
//  ShowUp · the media step's filled video card, measured (SHOWUP-161 / SHOWUP-191 item 4)
//
//  The reference's controls row is `alignItems: center; justifyContent: space-between`: `Your video`
//  on the Retake and Delete pills' centre line, the two pills together at the end, 8 apart. Android
//  drew the label ~7 above the pills (a FlowRow tops its items) -- found on a device, not by any test.
//  iOS's HStack centres by default; this measures it rather than trusting that.
//

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class MediaFitTests: XCTestCase {

    private final class Sink {
        var frames: [String: CGRect] = [:]
        func record(_ value: [String: CGRect]) -> Bool {
            frames = value
            return true
        }
    }

    /// Every anchor's frame, in the window's coordinates, for `view` on `device`.
    private func layout(_ view: some View, on device: FitDevice, type: DynamicTypeSize) -> [String: CGRect] {
        let sink = Sink()
        let root = view
            .environment(\.dynamicTypeSize, type)
            .overlayPreferenceValue(FitFrameKey.self) { frames in
                let _ = sink.record(frames)
                Color.clear
            }
        let controller = UIHostingController(rootView: root)
        controller.overrideUserInterfaceStyle = .light
        let window = FixedInsetWindow(frame: CGRect(x: 0, y: 0, width: device.width, height: device.height))
        window.fixedInsets = UIEdgeInsets(top: device.top, left: 0, bottom: device.bottom, right: 0)
        window.rootViewController = controller
        window.isHidden = false
        window.layoutIfNeeded()
        controller.view.setNeedsLayout()
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        controller.view.layoutIfNeeded()
        window.isHidden = true
        return sink.frames
    }

    /// A kept video, as ScreenFitTests' media sweep draws it (its own copy is file-private there).
    private let video = MediaArtefact(kind: .video, promptId: "relaxed_and_happy", durationMs: 9_400,
                                      localPath: nil, remoteId: "v1", url: nil, status: .confirmed)

    func testYourVideoSitsOnThePillsCentreLineAndThePillsAre8Apart() {
        var failures: [String] = []
        var sharedALine = 0
        for device in fitDevices {
            for type in [DynamicTypeSize.large, .xxxLarge, .accessibility3] {
                let f = layout(ProfileMediaView(state: MediaState(video: video)), on: device, type: type)
                guard let label = f[FitAnchorID.mediaRecordedLabel], let retake = f[FitAnchorID.mediaRetake],
                      let delete = f[FitAnchorID.mediaDelete] else {
                    failures.append("\(device.described) \(type): an anchor is missing"); continue
                }
                let at = "\(device.described) \(type)"
                if retake.minY < label.maxY && label.minY < retake.maxY {
                    sharedALine += 1
                    let off = label.midY - retake.midY
                    if abs(off) > 0.5 { failures.append("\(at): `Your video` is \(off) off the pills' centre") }
                    if abs(delete.minX - retake.maxX - 8) > 0.5 {
                        failures.append("\(at): Retake and Delete are \(delete.minX - retake.maxX) apart, not 8")
                    }
                }
                if abs(delete.minY - retake.minY) > 0.5 { failures.append("\(at): Retake and Delete are not level") }
            }
        }
        XCTAssertGreaterThan(sharedALine, fitDevices.count, "the label and the pills share a line on most phones")
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }
}
