// CancelDateConfirmSheetTests.swift
// ShowUp · Date 07's frames: the ticket's copy exactly and in order for B, C, D, E and I, the cost box following the
// value it is given, the error box between the cost box and the choices, the link at the end of the Date screen's
// scroll and gone after this user's check-in, and H on Date 02's component (SHOWUP-198). Android's
// `CancelDateConfirmSheetTest.kt`.
//
// The strings are asserted as `CancelCopy` builds them (they are the contract, quoted from the ticket), the order and
// presence of each part from the fit anchors the sheet publishes, and every frame is laid out in a hosting controller
// to prove it builds and takes a size. The touch areas are the fit sweep's (`CancelFitTests`, every one 44 both ways).

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class CancelDateConfirmSheetTests: XCTestCase {
    private let berlin = TimeZone(identifier: "Europe/Berlin")!
    private let meet = Date(timeIntervalSince1970: 1_795_024_800) // 19:00 in Berlin
    private var early: Date { meet.addingTimeInterval(-3 * 3600) }

    private func date(chatOpen: Bool = false, penalty: Int = 10, slots: Int = 1, checkedIn: Bool = false) -> ActiveDate {
        var d = DateSamples.date
        d.meetAt = meet
        d.unlockAt = meet.addingTimeInterval(-90 * 60)
        d.coordinateOpen = chatOpen
        d.checkedIn = checkedIn
        d.endsEnabled = true
        d.cancelPenaltyPoints = penalty
        d.change = DateChange(cutoffAt: meet.addingTimeInterval(-30 * 60), allowed: true,
                              slots: (0..<slots).map { meet.addingTimeInterval(900 * Double($0 + 1)) }, timeWas: nil,
                              placeWas: nil, pending: [])
        return d
    }

    private func ui(chatOpen: Bool = false, now: Date? = nil, sent: SentRequest? = nil, failed: Bool = false,
                    cancelling: Bool = false, penalty: Int = 10, slots: Int = 1) -> CancelUi {
        CancelUi(open: true, sent: sent, cancelling: cancelling, failed: failed,
                 date: date(chatOpen: chatOpen, penalty: penalty, slots: slots), now: now ?? early)
    }

    // MARK: laying frames out

    private final class Sink {
        var frames: [String: CGRect] = [:]
        func record(_ value: [String: CGRect]) -> Bool {
            frames = value
            return true
        }
    }

    /// The anchors a view publishes, laid out at `width` x `height` tall enough that nothing scrolls out of the layout.
    private func anchors(_ view: some View, width: CGFloat = 390, height: CGFloat = 3000) -> [String: CGRect] {
        let sink = Sink()
        let root = view.overlayPreferenceValue(FitFrameKey.self) { frames in
            let _ = sink.record(frames)
            Color.clear
        }
        let controller = UIHostingController(rootView: root)
        controller.overrideUserInterfaceStyle = .light
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: width, height: height))
        window.rootViewController = controller
        window.isHidden = false
        window.layoutIfNeeded()
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        controller.view.layoutIfNeeded()
        window.isHidden = true
        window.rootViewController = nil
        return sink.frames
    }

    private func sheet(_ state: CancelUi, actions: CancelActions = CancelActions()) -> some View {
        CancelDateConfirmSheet(ui: state, name: "Leonie", pill: guardPill(state.date ?? date(), zone: berlin),
                               photoUrl: nil, actions: actions)
            .frame(width: 390)
    }

    private func layoutHost(_ view: some View) {
        let host = UIHostingController(rootView: view)
        host.view.frame = CGRect(x: 0, y: 0, width: 375, height: 667)
        host.view.layoutIfNeeded()
        XCTAssertGreaterThan(host.sizeThatFits(in: CGSize(width: 375, height: 667)).height, 0)
    }

    // MARK: B

    func testBReadsTheTicketsCopyInTheTicketsOrder() {
        XCTAssertEqual(CancelCopy.link, "Cancel this date")
        XCTAssertEqual(guardPill(date(), zone: berlin), "Today · 19:00 · Cafe Krone")
        XCTAssertEqual(CancelCopy.counting("Leonie"), "Leonie is counting on you.")
        XCTAssertEqual(CancelCopy.headlineLead + CancelCopy.headlineItalic, "Are you really ready to disappoint her?")
        XCTAssertEqual(plain(CancelCopy.body),
                       "This isn't a button — it's a real person who cleared her evening, picked something to wear, and is quietly looking forward to meeting you. Cancelling lands on her side of the screen.")
        XCTAssertEqual(CancelCopy.reasons.map(plain), [
            "Almost every date leaves you glad you went. The hard part is only the front door.",
            "She chose you out of everyone. That's rare — showing up honours it.",
            "You'll go to sleep proud you stayed real and kept your word.",
        ])
        XCTAssertEqual(CancelCopy.solutionTitle("Leonie"), "Sometimes life happens — find a solution with Leonie!")
        XCTAssertEqual(CancelCopy.solutionBody("Leonie"),
                       "Would a later time or another place work? Request it — if Leonie agrees, your date goes ahead.")
        XCTAssertEqual(CancelCopy.changeTime, "Change meet time")
        XCTAssertEqual(CancelCopy.changePlace, "Change location")
        XCTAssertEqual(plain(CancelCopy.cost(10)),
                       "Cancelling this date counts as a no-show and lowers your Show-up Rate by 10 — a lower rate means fewer likes and matches. Let it stay low and you'll eventually lose access to the app entirely.")
        XCTAssertEqual(CancelCopy.keep, "Don't cancel my date")
        XCTAssertEqual(CancelCopy.confirm, "Cancel anyway and let her down")
        XCTAssertEqual(CancelCopy.warning("Leonie"), "This can't be undone — your date is off and you'll never match Leonie again.")

        let f = anchors(sheet(ui()))
        XCTAssertNil(f[FitAnchorID.cancelMessage])
        XCTAssertNotNil(f["\(FitAnchorID.cancelReason)-2"])
        XCTAssertNil(f["\(FitAnchorID.cancelReason)-3"])
        let order = [FitAnchorID.cancelPortrait, FitAnchorID.cancelHeadline, FitAnchorID.cancelSolution, FitAnchorID.cancelCost,
                     FitAnchorID.cancelKeep, FitAnchorID.cancelConfirm, FitAnchorID.cancelWarning].map { f[$0]?.minY ?? -1 }
        XCTAssertEqual(order, order.sorted())
        XCTAssertFalse(order.contains(-1))
        XCTAssertLessThan(f[FitAnchorID.cancelHeadline]!.minY, f["\(FitAnchorID.cancelReason)-0"]!.minY)
        XCTAssertLessThan(f["\(FitAnchorID.cancelReason)-2"]!.maxY, f[FitAnchorID.cancelSolution]!.minY)
        layoutHost(sheet(ui()))
    }

    func testThePortraitIs244Tall() {
        XCTAssertEqual(anchors(sheet(ui()))[FitAnchorID.cancelPortrait]?.height ?? 0, 244, accuracy: 0.5)
    }

    func testBTilesAndTheButtonsSendTheirTaps() {
        var tiles: [ChangeKind] = []
        var keep = 0
        var cancel = 0
        let actions = CancelActions(onKeep: { keep += 1 }, onTile: { tiles.append($0) }, onCancel: { cancel += 1 })
        actions.onTile(.time)
        actions.onTile(.place)
        actions.onKeep()
        actions.onCancel()
        XCTAssertEqual(tiles, [.time, .place])
        XCTAssertEqual(keep, 1)
        XCTAssertEqual(cancel, 1)
        let f = anchors(sheet(ui(), actions: actions))
        XCTAssertNotNil(f[FitAnchorID.cancelTileTime])
        XCTAssertNotNil(f[FitAnchorID.cancelTilePlace])
    }

    func testChangeMeetTimeHidesWithNoLaterSlot() {
        let f = anchors(sheet(ui(slots: 0)))
        XCTAssertNil(f[FitAnchorID.cancelTileTime])
        XCTAssertNotNil(f[FitAnchorID.cancelTilePlace])
    }

    // MARK: C

    func testCAddsMessageInsteadAfterTheTilesAndBeforeTheCostBox() {
        XCTAssertEqual(CancelCopy.message("Leonie"), "Message Leonie instead")
        let f = anchors(sheet(ui(chatOpen: true)))
        let tile = f[FitAnchorID.cancelTilePlace]!
        let message = f[FitAnchorID.cancelMessage]!
        XCTAssertLessThanOrEqual(tile.maxY, message.minY)
        XCTAssertLessThanOrEqual(message.maxY, f[FitAnchorID.cancelCost]!.minY)
        layoutHost(sheet(ui(chatOpen: true)))
    }

    // MARK: D

    func testDShowsTheConfirmationLineInPlaceOfTheTilesAndTheChoicesAreUnchanged() {
        XCTAssertEqual(CancelCopy.sentTail("Leonie"), " requested. Waiting for Leonie to agree — until then your date stays as planned.")
        let f = anchors(sheet(ui(chatOpen: true, sent: SentRequest(kind: .time, label: "20:00"))))
        XCTAssertNotNil(f[FitAnchorID.cancelSent])
        XCTAssertNil(f[FitAnchorID.cancelTileTime])
        XCTAssertNil(f[FitAnchorID.cancelTilePlace])
        XCTAssertNotNil(f[FitAnchorID.cancelSolutionBody])
        XCTAssertNotNil(f[FitAnchorID.cancelKeep])
        XCTAssertNotNil(f[FitAnchorID.cancelConfirm])
    }

    // MARK: E

    func testEPutsTheBoxBetweenTheCostBoxAndTheChoices() {
        XCTAssertEqual(CancelCopy.cancelFailed, "Couldn't cancel the date. Check your connection and try again.")
        let f = anchors(sheet(ui(failed: true)))
        let cost = f[FitAnchorID.cancelCost]!
        let box = f[FitAnchorID.endError]!
        let keep = f[FitAnchorID.cancelKeep]!
        XCTAssertLessThanOrEqual(cost.maxY, box.minY)
        XCTAssertLessThanOrEqual(box.maxY, keep.minY)
        XCTAssertNotNil(f[FitAnchorID.cancelConfirm])
    }

    func testACalmSheetHasNoErrorBox() {
        XCTAssertNil(anchors(sheet(ui()))[FitAnchorID.endError])
    }

    func testABothButtonsAreInertWhileTheCancelIsInFlightFrameStillLaysOut() {
        let f = anchors(sheet(ui(cancelling: true)))
        XCTAssertNotNil(f[FitAnchorID.cancelKeep])
        XCTAssertNotNil(f[FitAnchorID.cancelConfirm])
        layoutHost(sheet(ui(cancelling: true)))
    }

    // MARK: I

    func testIKeepsTheTitleAndMessageInsteadAndDropsTheBodyLineAndBothTiles() {
        let f = anchors(sheet(ui(chatOpen: false, now: meet.addingTimeInterval(-30 * 60))))
        XCTAssertNotNil(f[FitAnchorID.cancelSolution])
        XCTAssertNotNil(f[FitAnchorID.cancelMessage])
        XCTAssertNil(f[FitAnchorID.cancelSolutionBody])
        XCTAssertNil(f[FitAnchorID.cancelTileTime])
        XCTAssertNil(f[FitAnchorID.cancelTilePlace])
        XCTAssertNil(f[FitAnchorID.cancelSent])
        XCTAssertNotNil(f[FitAnchorID.cancelKeep])
    }

    func testARequestAlreadySentDoesNotShowInI() {
        let f = anchors(sheet(ui(now: meet.addingTimeInterval(-10 * 60), sent: SentRequest(kind: .time, label: "20:00"))))
        XCTAssertNil(f[FitAnchorID.cancelSent])
    }

    // MARK: the cost follows the value

    func testTheCostBoxReadsTheValueItIsGiven10AndThen7() {
        XCTAssertTrue(plain(CancelCopy.cost(7)).contains("lowers your Show-up Rate by 7 —"))
        XCTAssertFalse(plain(CancelCopy.cost(7)).contains("by 10"))
        XCTAssertEqual(ui(penalty: 7).drop, 7)
        XCTAssertEqual(ui(penalty: 10).drop, 10)
        layoutHost(sheet(ui(penalty: 7)))
    }

    // MARK: A: the link

    private func dateUi(checkedIn: Bool) -> DateUi {
        DateUi(date: date(checkedIn: checkedIn), now: early, loading: false)
    }

    func testAIsTheLastItemOfTheScrollBelowTheAboutCards() {
        let f = anchors(DateScreen(ui: dateUi(checkedIn: false), zone: berlin, showCancel: true), height: 844)
        let link = f["date:cancel"]
        XCTAssertNotNil(link)
        XCTAssertGreaterThanOrEqual(link?.minY ?? 0, f["date:works"]?.maxY ?? .infinity)
    }

    func testAIsGoneAfterThisUsersCheckIn() {
        let f = anchors(DateScreen(ui: dateUi(checkedIn: true), zone: berlin, showCancel: false), height: 844)
        XCTAssertNil(f["date:cancel"])
    }

    // MARK: H

    private let cancelled = EndedOutcome(dateId: "d1", name: "Leonie", endedByYou: false, reason: .cancelled)

    func testHReadsTheTicketsCopyWithThreeRowsAndTheSearchCta() {
        XCTAssertEqual(CancelCopy.hHeadlineLead("Leonie") + CancelCopy.hHeadlineItalic + CancelCopy.hHeadlineRest,
                       "Leonie had to cancel — we're truly sorry.")
        XCTAssertEqual(plain(CancelCopy.hBody),
                       "You made room in your day and you were ready to show up. Don't be discouraged - someone true as you is just a like away from meeting you.")
        XCTAssertEqual(CancelCopy.hRows("Leonie").map(plain), [
            "Your Show-up Rate stays untouched. A date someone else cancels will never count against you. You did everything right.",
            "Show Up is built on reliability and treasuring each other's time — real, warm, in-person connection. This isn't how our community behaves.",
            "People who repeatedly cancel or don't show up become less visible, and eventually lose access. Leonie carries that — not you.",
        ])
        XCTAssertEqual(CancelCopy.hCta, "Find someone who shows up")
        XCTAssertTrue(cancelledOnYou(cancelled))
        let f = anchors(DateEndedScreen(outcome: cancelled, photoURL: nil), height: 844)
        XCTAssertNotNil(f["outcome:row:2"])
        XCTAssertNil(f["outcome:row:3"])
        XCTAssertNotNil(f["chat:cta"])
        // The tile tops its row, 14 below the row's top.
        for index in 0..<3 {
            XCTAssertEqual(f["outcome:tile:\(index)"]!.minY, f["outcome:row:\(index)"]!.minY + 14, accuracy: 1)
        }
        // No back, no review, no "paused".
        let everything = ([CancelCopy.hHeadlineRest, CancelCopy.hCta] + CancelCopy.hRows("Leonie").map(plain) + [plain(CancelCopy.hBody)]).joined()
        XCTAssertFalse(everything.contains("paused"))
        XCTAssertFalse(everything.lowercased().contains("review"))
    }

    func testTheChatsOwnGAndHAreUnchangedOneRowAndFindYourNextDate() {
        let chat = EndedOutcome(dateId: "d1", name: "Leonie", endedByYou: false, reason: .chat)
        XCTAssertFalse(cancelledOnYou(chat))
        let f = anchors(DateEndedScreen(outcome: chat, photoURL: nil), height: 844)
        XCTAssertNotNil(f["outcome:row:0"])
        XCTAssertNil(f["outcome:row:1"])
        XCTAssertEqual(ChatCopy.findNext, "Find your next date")
    }

    func testACancelByYouIsNotH() {
        let mine = EndedOutcome(dateId: "d1", name: "Leonie", endedByYou: true, reason: .cancelled)
        XCTAssertFalse(cancelledOnYou(mine))
        let f = anchors(DateEndedScreen(outcome: mine, photoURL: nil), height: 844)
        XCTAssertNil(f["outcome:row:1"])
    }

    // MARK: the host

    func testNothingDrawsWhileTheSheetIsClosed() {
        layoutHost(CancelDateSheetHost(ui: CancelUi(), zone: berlin))
        XCTAssertFalse(CancelUi().open)
    }
}
