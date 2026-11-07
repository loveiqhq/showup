// ChatFitTests.swift
// ShowUp · the coordinate chat on every phone at every type size (SHOWUP-194). Android's `ChatFitTest.kt`.
//
// A to H as the spec sheet draws them, a long thread and D after Got it, on the 193 audit's phones (the
// shared list and `edgeFitDevices`) from the default size to AX5. The 193 rules -- every touch area 44 both
// ways and none partly on another -- and the chat's own: the dock under the thread, never over it; the
// tutorial's and the outcome's CTA on screen; and on the 375 x 667 phone D's guide and Got it inside the
// thread once revealed. The dock's chips are the one touch area allowed under 44: the ticket fixes them 36
// high, 6 apart.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class ChatFitTests: XCTestCase {

    private final class Sink {
        var frames: [String: CGRect] = [:]
        var taps: [FitTap] = []
        func record(_ value: [String: CGRect]) -> Bool {
            frames = value
            return true
        }
        func record(taps value: [FitTap]) -> Bool {
            taps = value
            return true
        }
    }

    private struct Shot {
        let frames: [String: CGRect]
        let taps: [FitTap]
    }

    /// `view` laid out on `device` at `type`, given time for the thread's reveal to run.
    private func layout(_ view: some View, on device: FitDevice, type: DynamicTypeSize, settle: TimeInterval = 0.05) -> Shot {
        let sink = Sink()
        let root = view
            .environment(\.dynamicTypeSize, type)
            .overlayPreferenceValue(FitFrameKey.self) { frames in
                let _ = sink.record(frames)
                Color.clear
            }
            .overlayPreferenceValue(FitTapKey.self) { taps in
                let _ = sink.record(taps: taps)
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
        RunLoop.main.run(until: Date().addingTimeInterval(settle))
        controller.view.layoutIfNeeded()
        window.isHidden = true
        return Shot(frames: sink.frames, taps: sink.taps)
    }

    private var phones: [FitDevice] { fitDevices + edgeFitDevices }
    private let types: [DynamicTypeSize] = [.large, .xxxLarge, .accessibility1, .accessibility3, .accessibility5]

    private var chats: [(String, ChatUi)] {
        var acked = ChatSamples.d
        acked.guide = .acked
        var long = ChatSamples.c
        long.messages = (1...9).map { i in
            ChatMessage(id: "l\(i)", reason: i % 3 == 0 ? .runningLate : nil,
                        body: i % 2 == 0 ? "Running a little late, the tram stopped twice and I am nearly at the door, sorry!"
                                         : "No worries, see you soon.",
                        fromYou: i % 2 == 1, sentAt: ChatSamples.now, read: i % 2 == 1 ? i < 8 : nil)
        } + [ChatSamples.cancelSent]
        return [("B start", ChatSamples.b), ("C thread", ChatSamples.c), ("D guide", ChatSamples.d),
                ("D after Got it", acked), ("E end card", ChatSamples.e), ("F end sheet", ChatSamples.f),
                ("long thread", long)]
    }

    /// Every touch area at least 44 both ways but the dock's chips, and no two partly on each other.
    private func tapFailures(_ shot: Shot, _ tag: String) -> [String] {
        var failures: [String] = []
        let dock = shot.frames["chat:dock"]
        let areas = shot.taps.filter { $0.frame.width > 0 && $0.frame.height > 0 }
        for tap in areas where tap.frame.width < 43.5 || tap.frame.height < 43.5 {
            let dockChip = dock.map { $0.insetBy(dx: -0.5, dy: -0.5).contains(tap.frame) } == true && tap.frame.height >= 35
            if !dockChip {
                failures.append("\(tag): a touch area \(tap.frame.width) x \(tap.frame.height) at \(tap.frame.origin), under 44")
            }
        }
        func holds(_ a: CGRect, _ b: CGRect) -> Bool { a.insetBy(dx: -0.5, dy: -0.5).contains(b) }
        for i in areas.indices {
            for j in areas.indices where j > i {
                if areas[i].pinned != areas[j].pinned { continue }
                let a = areas[i].frame, b = areas[j].frame
                if holds(a, b) || holds(b, a) { continue }
                let both = a.intersection(b)
                if !both.isNull, both.width > 0.5, both.height > 0.5 {
                    failures.append("\(tag): touch areas at \(a.origin) and \(b.origin) overlap by \(both.width) x \(both.height)")
                }
            }
        }
        return failures
    }

    func testEveryFrameOfTheChatIsCleanOnEveryPhoneAtEveryTypeSize() {
        var failures: [String] = []
        for type in types {
            for device in phones {
                let tutorial = layout(ChatTutorialScreen(name: "Leonie"), on: device, type: type)
                failures += tapFailures(tutorial, "A \(device.described) @\(type)")
                if let cta = tutorial.frames["chat:cta"], cta.maxY > device.height + 0.5 {
                    failures.append("A \(device.described) @\(type): the CTA runs off the bottom")
                }
                for (name, ui) in chats {
                    let shot = layout(ChatScreen(ui: ui, zone: DateSamples.zone), on: device, type: type)
                    let tag = "\(name) \(device.described) @\(type)"
                    failures += tapFailures(shot, tag)
                    if let thread = shot.frames["chat:thread"], let dock = shot.frames["chat:dock"] {
                        if thread.maxY > dock.minY + 0.5 { failures.append("\(tag): the thread runs \(thread.maxY - dock.minY) under the dock") }
                        if dock.maxY > device.height + 0.5 { failures.append("\(tag): the dock runs off the bottom") }
                    }
                }
                for outcome in [ChatSamples.g, ChatSamples.h] {
                    let shot = layout(DateEndedScreen(outcome: outcome), on: device, type: type)
                    let tag = "\(outcome.endedByYou ? "G" : "H") \(device.described) @\(type)"
                    failures += tapFailures(shot, tag)
                }
            }
        }
        XCTAssertTrue(failures.isEmpty, "\(failures.count) problems: " + failures.prefix(40).joined(separator: " · "))
    }

    /// The acceptance criteria's own check: D at 375 x 667, the guide's title and Got it visible once revealed.
    func testAt375x667TheGuideAndGotItAreInsideTheThreadOnceItAppears() {
        guard let phone = fitDevices.first(where: { $0.width == 375 && $0.height == 667 }) else {
            return XCTFail("no 375 x 667 phone in the list")
        }
        var failures: [String] = []
        for type in [DynamicTypeSize.large, .xxxLarge] {
            let shot = layout(ChatScreen(ui: ChatSamples.d, zone: DateSamples.zone), on: phone, type: type, settle: 0.4)
            guard let thread = shot.frames["chat:thread"], let guide = shot.frames["chat:guide"],
                  let gotIt = shot.frames["chat:got-it"] else {
                failures.append("@\(type): an anchor is missing"); continue
            }
            if guide.minY < thread.minY - 0.5 { failures.append("@\(type): the guide starts \(thread.minY - guide.minY) above the thread") }
            if gotIt.maxY > thread.maxY + 0.5 { failures.append("@\(type): Got it ends \(gotIt.maxY - thread.maxY) below the thread") }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }
}
