// PartnerEndedScreenTests.swift
// ShowUp · Date 08's three payloads: the ticket's strings exactly and in order, one control, and the words each frame
// must not use (SHOWUP-199). Android's `PartnerEndedScreenTest.kt`, case for case.
//
// A SwiftUI unit test cannot read text back out of a hosted view (FitDevices.swift says why), so the strings are
// asserted as the screen builds them -- `partnerEndedHeadline`, `partnerEndedSub` and the payload's rows are what it
// draws, nothing else -- and the order, the count and the one control from the fit anchors and touch areas the screen
// publishes, laid out in a window.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class PartnerEndedScreenTests: XCTestCase {

    private struct Expect {
        let screen: PartnerScreen
        let headline: String
        let washed: String
        let sub: String
        let rows: [String]
    }

    private let a = Expect(
        screen: .youNoShowed,
        headline: "Leonie ended the date, stating you didn't show up.",
        washed: "didn't show up.",
        sub: "Leonie ended the date after waiting for you.\nIt's now closed.",
        rows: [
            "Not showing up to a date — or being very late without reaching out to your date — shows no respect for each other's time.",
            "To clear this up, proceed to the review and give us your side.",
            "Once you've completed the review, we run an automated check: did you both check in, and did you message each other? Your Show-up Rate only drops if the no-show is proven.",
        ])

    private let b = Expect(
        screen: .dateEndedDiscomfort,
        headline: "Leonie ended the date, saying they didn't feel comfortable in the situation.",
        washed: "didn't feel comfortable",
        sub: "The date is now closed.",
        rows: [
            "Respect is non-negotiable at Show Up. Behaviour that leaves someone feeling uncomfortable is not accepted here.",
            "So far we only have one side of the story. In the next step, tell us what happened from your point of view.",
            "We review every report and act on it where it's needed.",
        ])

    private let c = Expect(
        screen: .dateClosedPositive,
        headline: "Leonie ended the date, stating you both have met.",
        washed: "met.",
        sub: "Thank you for being real and showing up to your date.",
        rows: [
            "We hope it was a good one. Head into the review and tell us how it went — it takes a minute.",
            "Once the review is done, you're free to search for your next date.",
        ])

    /// Every string the screen draws for `screen`, in its order: the headline, the sub, the rows, the CTA.
    private func texts(_ screen: PartnerScreen) -> [String] {
        var all = [partnerEndedHeadline(screen, name: "Leonie").map(\.0).joined()]
        all.append(plain(partnerEndedSub(screen, name: "Leonie")))
        all += partnerEndedPayload(screen).rows.map(plain)
        all.append(PartnerEndedCopy.cta)
        return all
    }

    // MARK: laying the screen out

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

    /// The anchors and touch areas the screen publishes, laid out at 390 x 844.
    private func shot(_ screen: PartnerScreen, onProceed: @escaping () -> Void = {}) -> Shot {
        let sink = Sink()
        let root = PartnerEndedScreen(screen: screen, name: "Leonie", photoURL: nil, onProceed: onProceed)
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
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.rootViewController = controller
        window.isHidden = false
        window.layoutIfNeeded()
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        controller.view.layoutIfNeeded()
        window.isHidden = true
        window.rootViewController = nil
        return Shot(frames: sink.frames, taps: sink.taps)
    }

    private func readsTheTicket(_ e: Expect, file: StaticString = #filePath, line: UInt = #line) {
        let parts = partnerEndedHeadline(e.screen, name: "Leonie")
        XCTAssertEqual(parts.map(\.0).joined(), e.headline, file: file, line: line)
        // One run washed, the ticket's `*...*`.
        XCTAssertEqual(parts.filter(\.1).map(\.0), [e.washed], file: file, line: line)
        XCTAssertEqual(plain(partnerEndedSub(e.screen, name: "Leonie")), e.sub, file: file, line: line)
        XCTAssertEqual(partnerEndedPayload(e.screen).rows.map(plain), e.rows, file: file, line: line)
        XCTAssertEqual(PartnerEndedCopy.cta, "Proceed to review", file: file, line: line)

        // Drawn in that order: the hero over the rows, row after row, the CTA last.
        let f = shot(e.screen).frames
        guard let hero = f[FitAnchorID.partnerEndedHero], let rows = f[FitAnchorID.partnerEndedRows],
              let cta = f[FitAnchorID.partnerEndedCta] else {
            return XCTFail("the hero, the rows or the CTA is not drawn", file: file, line: line)
        }
        XCTAssertLessThanOrEqual(hero.maxY, rows.minY, file: file, line: line)
        XCTAssertLessThanOrEqual(rows.maxY, cta.minY, file: file, line: line)
        var last = rows.minY
        for index in e.rows.indices {
            guard let row = f[FitAnchorID.partnerEndedRow(index)], let tile = f[FitAnchorID.partnerEndedTile(index)] else {
                return XCTFail("row \(index) is not drawn", file: file, line: line)
            }
            XCTAssertGreaterThanOrEqual(row.minY, last, file: file, line: line)
            last = row.maxY
            // No `alignItems`: the tile tops its row, 15 below the row's top (14 inside the 1 px border).
            XCTAssertEqual(tile.minY, row.minY + 15, accuracy: 1, file: file, line: line)
            XCTAssertEqual(tile.height, PartnerEndedLayout.tileSize, accuracy: 0.5, file: file, line: line)
        }
    }

    func testAReadsTheTicketExactly() { readsTheTicket(a) }
    func testBReadsTheTicketExactly() { readsTheTicket(b) }
    func testCReadsTheTicketExactly() { readsTheTicket(c) }

    func testProceedToReviewIsTheOnlyClickActionOnEachPayload() {
        for screen in PartnerScreen.allCases {
            let s = shot(screen)
            let areas = s.taps.filter { $0.frame.width > 0 && $0.frame.height > 0 }
            XCTAssertEqual(areas.count, 1, "\(screen.rawValue): one control, no back, no close, no tab bar")
            guard let cta = s.frames[FitAnchorID.partnerEndedCta], let area = areas.first else {
                XCTFail("\(screen.rawValue): the CTA is not drawn")
                continue
            }
            XCTAssertEqual(area.frame.minY, cta.minY, accuracy: 0.5, screen.rawValue)
            XCTAssertEqual(area.frame.height, cta.height, accuracy: 0.5, screen.rawValue)
        }
    }

    func testBUsesNoGenderedPronounAndNeverSaysUnsafe() {
        let joined = texts(.dateEndedDiscomfort).joined(separator: "\n")
        // Whole words: a substring check would fail on "the".
        XCTAssertNil(joined.range(of: "\\b(she|he|her|his|him|hers)\\b", options: [.regularExpression, .caseInsensitive]))
        XCTAssertNil(joined.range(of: "unsafe", options: .caseInsensitive))
    }

    func testACarriesNoRateValueAndNoVisibilityThreat() {
        let joined = texts(.youNoShowed).joined(separator: "\n")
        XCTAssertNil(joined.range(of: "[0-9]", options: .regularExpression))
        XCTAssertNil(joined.range(of: "less visible", options: .caseInsensitive))
        XCTAssertNil(joined.range(of: "lose App access", options: .caseInsensitive))
    }

    func testTappingTheCtaCallsOnProceedOnce() {
        var taps = 0
        let screen = PartnerEndedScreen(screen: .dateClosedPositive, name: "Leonie", photoURL: nil, onProceed: { taps += 1 })
        // SwiftUI offers no tap a unit test can send; the screen hands its one button exactly the closure it was given.
        screen.onProceed()
        XCTAssertEqual(taps, 1)
        XCTAssertNotNil(shot(.dateClosedPositive, onProceed: { taps += 1 }).frames[FitAnchorID.partnerEndedCta])
        XCTAssertEqual(taps, 1, "laying the screen out taps nothing")
    }

    /// B's balanced headline breaks only at a space on the three phones the ticket names. Android drew `didn't feel
    /// comforta` / `ble in the situation.` on all three: the balance search went below the washed run's width, where
    /// the line breaker splits the run rather than overflow (22 Nov 2026). The column is the phone less the screen's
    /// 20 a side and B's hero padding; the width is the one `WashHost` lays the label out at.
    func testBsHeadlineNeverSplitsAWordAt375390And430() {
        for phone in [375, 390, 430] as [CGFloat] {
            let laid = laidOut(phone: phone)
            XCTAssertEqual(wordSplits(laid.text, width: laid.width), [], "at \(phone)")
        }
    }

    private let bParts = partnerEndedHeadline(.dateEndedDiscomfort, name: "Leonie")

    /// B's headline as `WashHost` lays it out in B's column on `phone` at `size` points: the text it chose -- the run
    /// joined, or loose where the run is wider than the column -- and the width it breaks at. The column is the phone
    /// less the screen's 20 a side and B's hero padding.
    private func laidOut(phone: CGFloat, size: CGFloat = 27) -> (text: NSAttributedString, width: CGFloat) {
        let host = balancedWashHost(bParts, size: size)
        let column = phone - 2 * 20 - 2 * partnerEndedPayload(.dateEndedDiscomfort).heroPadH
        host.useText(within: column)
        let width = host.lineWidth(within: column)
        return (host.label.attributedText ?? NSAttributedString(), width)
    }

    /// Where the washed run is wider than the column itself -- 320 at 27, 390 at 54 -- no width keeps it whole, and
    /// the line breaker split a word inside it (ruling R-e, 22 Nov 2026: no word may ever break inside). It wraps
    /// between its own words instead. Android's `B's headline never splits a word where its run is wider than the
    /// column`.
    func testBsHeadlineNeverSplitsAWordWhereItsRunIsWiderThanTheColumn() {
        for (phone, size) in [(320, 27), (390, 54)] as [(CGFloat, CGFloat)] {
            let laid = laidOut(phone: phone, size: size)
            XCTAssertEqual(wordSplits(laid.text, width: laid.width), [], "at \(phone), \(size) pt")
        }
    }

    /// The wash covers every line of a run that wraps: one band a line, each holding that line's words of the run.
    func testTheWashCoversEveryFragmentOfARunThatWraps() {
        for (phone, size) in [(320, 27), (390, 54)] as [(CGFloat, CGFloat)] {
            let laid = laidOut(phone: phone, size: size)
            let bands = washBands(laid.text, width: laid.width, run: washRunRange(bParts))
            XCTAssertGreaterThanOrEqual(bands.count, 2, "the run did not wrap at \(phone), \(size) pt: \(bands)")
            XCTAssertEqual(bands.joined(separator: " "), "didn't feel comfortable", "at \(phone), \(size) pt")
        }
    }

    /// Where the run fits, it keeps its no-break spaces and its one line -- one band, the whole run: nothing moves on
    /// those headlines.
    func testBsRunStaysJoinedOnOneLineWhereItFits() {
        let run = washRunRange(bParts)
        for phone in [375, 390, 430] as [CGFloat] {
            let laid = laidOut(phone: phone)
            XCTAssertTrue((laid.text.string as NSString).substring(with: run).contains("\u{00A0}"), "at \(phone)")
            XCTAssertEqual(washBands(laid.text, width: laid.width, run: run),
                           [(laid.text.string as NSString).substring(with: run)], "at \(phone)")
        }
    }

    func testRowCountFollowsThePayload() {
        let met = shot(.dateClosedPositive).frames
        XCTAssertNotNil(met[FitAnchorID.partnerEndedRow(1)])
        XCTAssertNil(met[FitAnchorID.partnerEndedRow(2)])
        for screen in [PartnerScreen.youNoShowed, .dateEndedDiscomfort] {
            let f = shot(screen).frames
            XCTAssertNotNil(f[FitAnchorID.partnerEndedRow(2)], screen.rawValue)
            XCTAssertNil(f[FitAnchorID.partnerEndedRow(3)], screen.rawValue)
        }
    }
}
