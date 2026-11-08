// ChatFitTests.swift
// ShowUp · the coordinate chat on every phone at every type size (SHOWUP-194). Android's `ChatFitTest.kt`.
//
// A to H as the spec sheet draws them, a long thread and D after Got it, on every phone (the shared list
// and `edgeFitDevices`) at all twelve Dynamic Type sizes, xSmall to AX5; and the chat with the keyboard up
// on every full-screen phone. The 193 rules -- every touch area 44 both ways and none partly on another --
// and the chat's own: the dock under the thread, never over it, and above the keys; the tutorial's and the
// outcome's CTA on screen; and on the 375 x 667 phone D's guide and Got it inside the thread once
// revealed. The dock's chips are the one touch area allowed under 44: the ticket fixes them 36 high, 6
// apart.

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

    /// `view` laid out on `device` at `type`, given time for the thread's reveal to run. `keyboard`: the keys'
    /// height, laid in as the bottom inset, as the system lays them.
    private func layout(_ view: some View, on device: FitDevice, type: DynamicTypeSize, settle: TimeInterval = 0.05,
                        keyboard: CGFloat = 0) -> Shot {
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
        window.fixedInsets = UIEdgeInsets(top: device.top, left: 0, bottom: max(device.bottom, keyboard), right: 0)
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
    /// Every Dynamic Type size there is, xSmall to AX5.
    private let types = DynamicTypeSize.allCases

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

    /// Every touch area at least 44 both ways but the dock's chips, and no two partly on each other. Where
    /// the chips scroll (a split screen) the last rows sit below the dock's foot, out of sight.
    private func tapFailures(_ shot: Shot, _ tag: String) -> [String] {
        var failures: [String] = []
        let dock = shot.frames["chat:dock"]
        let areas = shot.taps.filter { $0.frame.width > 0 && $0.frame.height > 0 }
        for tap in areas where tap.frame.width < 43.5 || tap.frame.height < 43.5 {
            let inDock = dock.map { d in
                tap.frame.minY >= d.minY - 0.5 && tap.frame.minX >= d.minX - 0.5 && tap.frame.maxX <= d.maxX + 0.5
            } == true
            let dockChip = inDock && tap.frame.height >= 35
            if !dockChip {
                failures.append("\(tag): a touch area \(tap.frame.width) x \(tap.frame.height) at \(tap.frame.origin), under 44")
            }
        }
        func holds(_ a: CGRect, _ b: CGRect) -> Bool { a.insetBy(dx: -0.5, dy: -0.5).contains(b) }
        // With the end sheet up only its own controls answer: the rest are under its backdrop.
        let live = areas.contains(where: \.modal) ? areas.filter(\.modal) : areas
        for i in live.indices {
            for j in live.indices where j > i {
                if live[i].pinned != live[j].pinned { continue }
                let a = live[i].frame, b = live[j].frame
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
        XCTAssertTrue(failures.isEmpty, "\(failures.count) problems: " + summary(failures))
    }

    /// One entry per kind of problem -- its device and type size aside -- with how often it came and its first
    /// case, so one run shows every kind rather than the first forty of one.
    private func summary(_ failures: [String]) -> String {
        var order: [String] = []
        var seen: [String: (count: Int, first: String)] = [:]
        for failure in failures {
            let detail = failure.split(separator: ":", maxSplits: 1).last.map(String.init) ?? failure
            let kind = detail.replacingOccurrences(of: "[0-9.]+", with: "#", options: .regularExpression)
            if let entry = seen[kind] {
                seen[kind] = (entry.count + 1, entry.first)
            } else {
                seen[kind] = (1, failure)
                order.append(kind)
            }
        }
        return order.prefix(30).compactMap { kind in seen[kind].map { "\($0.count) x \($0.first)" } }
            .joined(separator: " · ")
    }

    /// The chat with the keyboard up, on every full-screen phone at the default, a large and an accessibility
    /// size: the dock above the keys, the thread over the dock. Not the guide (its field is locked) nor the
    /// sheet. Not a split screen's top half: the keys cover the other app. Android's keyboard sweep.
    func testTheChatWithTheKeyboardUpIsCleanOnEveryPhone() {
        var failures: [String] = []
        let typing = chats.filter { !["B start", "D guide", "F end sheet"].contains($0.0) }
        for type in [DynamicTypeSize.large, .xxxLarge, .accessibility2] {
            for device in phones where device.height >= 500 {
                // About two fifths of a phone, as the iOS keyboard and Gboard take, 250 to 330.
                let keys = min(330, max(250, (device.height * 0.38).rounded()))
                for (name, ui) in typing {
                    let shot = layout(ChatScreen(ui: ui, zone: DateSamples.zone), on: device, type: type, keyboard: keys)
                    let tag = "\(name), keyboard \(Int(keys)) \(device.described) @\(type)"
                    failures += tapFailures(shot, tag)
                    guard let thread = shot.frames["chat:thread"], let dock = shot.frames["chat:dock"] else {
                        failures.append("\(tag): no thread or no dock"); continue
                    }
                    if thread.maxY > dock.minY + 0.5 { failures.append("\(tag): the thread runs \(thread.maxY - dock.minY) under the dock") }
                    if dock.maxY > device.height - max(device.bottom, keys) + 0.5 {
                        failures.append("\(tag): the dock runs \(dock.maxY - device.height + max(device.bottom, keys)) under the keyboard")
                    }
                }
            }
        }
        XCTAssertTrue(failures.isEmpty, "\(failures.count) problems: " + summary(failures))
    }

    /// The acceptance criteria's own check: D at 375 x 667, the guide's title and Got it visible once revealed.
    /// A guide taller than the thread cannot show both (at the larger size here); the reveal rule then puts its
    /// title at the top and Got it is a short scroll away, which is what is held to. Reported for Philipp.
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
            // The thread's padding while the guide waits (6 above, 6 below), and the 12 over the guide.
            let fits = guide.height <= thread.height - 12
            if fits, gotIt.maxY > thread.maxY + 0.5 { failures.append("@\(type): Got it ends \(gotIt.maxY - thread.maxY) below the thread") }
            if !fits, guide.minY > thread.minY + 12.5 {
                failures.append("@\(type): the guide is taller than the thread and its title sits \(guide.minY - thread.minY) down")
            }
        }
        XCTAssertTrue(failures.isEmpty, failures.joined(separator: " · "))
    }
}
