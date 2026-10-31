//
//  MediaFitTests.swift
//  ShowUp · the media step's filled video card, measured (SHOWUP-161 / SHOWUP-191 item 4)
//
//  The reference's controls row is `alignItems: center; justifyContent: space-between`: `Your video`
//  on the Retake and Delete pills' centre line, the two pills together at the end, 8 apart. Android
//  drew the label 13 above the pills (a FlowRow tops its items) -- found on a device, not by any test.
//  iOS's HStack centres by default, but its stack spacing made the one-line form need 24 between label
//  and pills, so it wrapped on phones that had room -- found by this test's first CI run.
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

    /// The one-line form wraps only when it does not fit: the status group, 8, the two pills.
    ///
    /// Not a count of phones that happen to share a line -- that was a guess, and a guess cannot
    /// tell "wraps on narrow phones" from "wraps on phones with room", which is what iOS did while
    /// its stack spacing made the gap 24. This is the rule Android's `ControlsLayout` is built from.
    func testYourVideoSitsOnThePillsCentreLineAndWrapsOnlyWhenItDoesNotFit() {
        var failures: [String] = []
        var referenceFrameShared = false
        for device in fitDevices {
            for type in [DynamicTypeSize.large, .xxxLarge, .accessibility3] {
                let f = layout(ProfileMediaView(state: MediaState(video: video)), on: device, type: type)
                guard let row = f[FitAnchorID.mediaRecordedRow], let label = f[FitAnchorID.mediaRecordedLabel],
                      let retake = f[FitAnchorID.mediaRetake], let delete = f[FitAnchorID.mediaDelete] else {
                    failures.append("\(device.described) \(type): an anchor is missing"); continue
                }
                let at = "\(device.described) \(type)"
                // The status group starts at the row's edge; the label ends it.
                let needed = (label.maxX - row.minX) + Spacing.md + (delete.maxX - retake.minX)
                let fits = needed <= row.width + 0.5
                let shared = retake.minY < label.maxY && label.minY < retake.maxY
                if fits && !shared {
                    failures.append("\(at): wrapped though one line needs \(needed) of \(row.width)")
                }
                if shared && !fits {
                    failures.append("\(at): shares a line it needs \(needed) for, in \(row.width)")
                }
                if shared {
                    if device.width == 390 && type == .large { referenceFrameShared = true }
                    let off = label.midY - retake.midY
                    if abs(off) > 0.5 { failures.append("\(at): `Your video` is \(off) off the pills' centre") }
                    if abs(delete.minX - retake.maxX - Spacing.md) > 0.5 {
                        failures.append("\(at): Retake and Delete are \(delete.minX - retake.maxX) apart, not 8")
                    }
                    if abs(row.maxX - delete.maxX) > 0.5 {
                        failures.append("\(at): Delete ends \(row.maxX - delete.maxX) short of the row's end")
                    }
                }
                if abs(delete.minY - retake.minY) > 0.5 { failures.append("\(at): Retake and Delete are not level") }
            }
        }
        // The reference is drawn at 390 at the default size, on one line.
        XCTAssertTrue(referenceFrameShared, "at 390 and the default type the row is on one line, as drawn")
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }
}
