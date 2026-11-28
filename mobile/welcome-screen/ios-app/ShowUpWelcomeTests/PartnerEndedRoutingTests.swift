// PartnerEndedRoutingTests.swift
// ShowUp · Date 08's routing: which ended date goes to it, the route and its once-per-date guard, the screen view and
// its referrer, no way out but Proceed to review, and every sheet closed behind it (SHOWUP-199, R1, R2, R6, R7).
// Android's `PartnerEndedRoutingTest.kt`, case for case, plus the iOS-only one: a restored Date 05 / 06 / 07 draft (each
// in `@SceneStorage`) is cleared when Date 08 shows.
//
// The host's wiring (`ShowUpWelcomeApp.routeEnded` / `showPartnerEnded` / `startReview`) is a View's private methods and
// is not driven here; every decision it makes is one of the functions or model calls below.

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class PartnerEndedRoutingTests: XCTestCase {

    private func ended(_ reason: OutcomeReason, byYou: Bool, screen: PartnerScreen? = nil, id: String = "d1") -> EndedOutcome {
        EndedOutcome(dateId: id, name: "Leonie", endedByYou: byYou, reason: reason, partnerScreen: screen)
    }

    // MARK: endedRoute

    func testDate06EndOnYouIsPartnerEndedForEachOfTheThreeReasons() {
        let cases: [(OutcomeReason, PartnerScreen)] = [
            (.partnerNoShow, .youNoShowed), (.uncomfortable, .dateEndedDiscomfort), (.met, .dateClosedPositive),
        ]
        for (reason, screen) in cases {
            XCTAssertEqual(endedRoute(ended(reason, byYou: false, screen: screen)), .partnerEnded(screen), reason.rawValue)
        }
    }

    func testYourOwnDate06EndLands() {
        for reason in [OutcomeReason.partnerNoShow, .uncomfortable, .met] {
            XCTAssertEqual(endedRoute(ended(reason, byYou: true)), .land, reason.rawValue)
            // Even if a server sent the match's screen to the person who ended it.
            XCTAssertEqual(endedRoute(ended(reason, byYou: true, screen: .dateClosedPositive)), .land, reason.rawValue)
        }
    }

    func testADate06EndWithNoPartnerScreenLandsAsAnOlderServerSendsIt() {
        XCTAssertEqual(endedRoute(ended(.met, byYou: false)), .land)
    }

    func testChatEndIsDateEndedEitherWay() {
        XCTAssertEqual(endedRoute(ended(.chat, byYou: true)), .dateEnded)
        XCTAssertEqual(endedRoute(ended(.chat, byYou: false)), .dateEnded)
    }

    func testCancelOnYouIsPartnerCancelledYourOwnCancelLands() {
        XCTAssertEqual(endedRoute(ended(.cancelled, byYou: false)), .partnerCancelled)
        XCTAssertEqual(endedRoute(ended(.cancelled, byYou: true)), .land)
    }

    // MARK: ids and the route

    func testPartnerEndedScreenIDMapsTheThreeScreensToTheirIdsAndNames() {
        XCTAssertEqual(partnerEndedScreenID(.youNoShowed), .datePartnerEndedNoShow)
        XCTAssertEqual(partnerEndedScreenID(.dateEndedDiscomfort), .datePartnerEndedUncomfortable)
        XCTAssertEqual(partnerEndedScreenID(.dateClosedPositive), .datePartnerEndedMet)
        XCTAssertEqual(ProfileScreen.datePartnerEndedNoShow.screenId, "date_partner_ended_no_show")
        XCTAssertEqual(ProfileScreen.datePartnerEndedNoShow.screenName, "Date - Partner ended no-show")
        XCTAssertEqual(ProfileScreen.datePartnerEndedUncomfortable.screenId, "date_partner_ended_uncomfortable")
        XCTAssertEqual(ProfileScreen.datePartnerEndedUncomfortable.screenName, "Date - Partner ended uncomfortable")
        XCTAssertEqual(ProfileScreen.datePartnerEndedMet.screenId, "date_partner_ended_met")
        XCTAssertEqual(ProfileScreen.datePartnerEndedMet.screenName, "Date - Partner ended met")
    }

    func testTheRouteCarriesEverythingItDrawsTheOutcomesOwnPhotoFirst() throws {
        let own = try XCTUnwrap(URL(string: "https://x/own.jpg"))
        let held = try XCTUnwrap(URL(string: "https://x/held.jpg"))
        var outcome = ended(.met, byYou: false, screen: .dateClosedPositive)
        outcome.photoUrl = own
        XCTAssertEqual(partnerEndedRoute(outcome, screen: .dateClosedPositive, photoURL: held),
                       .partnerEnded(dateId: "d1", name: "Leonie", screen: .dateClosedPositive, photoURL: own))
        outcome.photoUrl = nil
        XCTAssertEqual(partnerEndedRoute(outcome, screen: .dateClosedPositive, photoURL: held),
                       .partnerEnded(dateId: "d1", name: "Leonie", screen: .dateClosedPositive, photoURL: held))
    }

    func testTwoReadsOfOneEndRouteOnce() {
        var once = LandedOnce()
        XCTAssertTrue(once.first("d1"))
        XCTAssertFalse(once.first("d1"))
        XCTAssertTrue(once.first("d2"))
    }

    // An end a Settings read stored before the Date screen's view existed is routed when the view appears
    // (`onChange(initial: true)`); the later changes that see the same end route nothing more.
    func testAnEndAlreadyStoredOnAppearShowsOnceAndLaterCallsShowNothing() {
        var once = LandedOnce()
        let stored = ended(.met, byYou: false, screen: .dateClosedPositive)
        XCTAssertEqual(partnerEndedToShow(stored, once: &once), .dateClosedPositive) // on appear
        XCTAssertNil(partnerEndedToShow(stored, once: &once))                         // the 15 s poll's same value
        XCTAssertNil(partnerEndedToShow(stored, once: &once))                         // the view appearing again
        XCTAssertEqual(partnerEndedToShow(ended(.met, byYou: false, screen: .youNoShowed, id: "d2"), once: &once), .youNoShowed)
    }

    func testTheOnAppearGateLeavesGAndHAndTheLandingToTheirOwnGuards() {
        var once = LandedOnce()
        XCTAssertNil(partnerEndedToShow(ended(.chat, byYou: false), once: &once))      // G
        XCTAssertNil(partnerEndedToShow(ended(.cancelled, byYou: false), once: &once)) // H
        XCTAssertNil(partnerEndedToShow(ended(.met, byYou: true), once: &once))        // landing
        // ...and did not use up Date 08's slot for that date.
        XCTAssertEqual(partnerEndedToShow(ended(.met, byYou: false, screen: .dateClosedPositive), once: &once), .dateClosedPositive)
    }

    // MARK: tracking

    // The referrer is decided by what was on screen, not by whether a navigation happened: a launch sets the screen
    // directly (`cameFrom` says nothing about it).

    func testAnEndFoundWhileADateIsHeldWasFoundOnTheDateScreen() {
        XCTAssertEqual(partnerEndedReferrer(heldDate: date(checkedIn: true), from: nil), .dateScreen)
    }

    func testAnEndFoundByTheLaunchsFirstReadWithNoDateHeldHasNoReferrer() {
        XCTAssertNil(partnerEndedReferrer(heldDate: nil, from: nil))
    }

    func testAnEndFoundFromTheChatIsTheChatsHeldDateOrNot() {
        XCTAssertEqual(partnerEndedReferrer(heldDate: date(checkedIn: true), from: .dateChat), .dateChat)
        XCTAssertEqual(partnerEndedReferrer(heldDate: nil, from: .dateChat), .dateChat)
    }

    /// iOS only: the Date model reads again on every return to the foreground, even with the chat pushed over it, so its
    /// read and the chat's race for one end. Whichever wins, the referrer is where the person was, read from the stack.
    func testAnEndTheDateScreensReadFindsUnderTheChatIsTheChatsWhicheverReadWins() {
        XCTAssertNil(endFoundFrom([]), "the Date screen on top")
        XCTAssertEqual(endFoundFrom([.chat(.openChat)]), .dateChat)
        XCTAssertEqual(endFoundFrom([.chat(.readReply), .changePicker(kind: .time, entry: .chatChip)]), .dateChat)
        XCTAssertEqual(endFoundFrom([.chatTutorial]), .dateChatTutorial)
        XCTAssertNil(endFoundFrom([.changePicker(kind: .place, entry: .coordinateTile)]), "a picker over the Date screen")
        // The Date screen's read with the chat up and the chat's own poll report the same referrer...
        let held = date(checkedIn: true)
        XCTAssertEqual(partnerEndedReferrer(heldDate: held, from: endFoundFrom([.chat(.openChat)])),
                       partnerEndedReferrer(heldDate: held, from: .dateChat))
        // ...and with the Date screen on top it is still the Date screen, a launch still none.
        XCTAssertEqual(partnerEndedReferrer(heldDate: held, from: endFoundFrom([])), .dateScreen)
        XCTAssertNil(partnerEndedReferrer(heldDate: nil, from: endFoundFrom([])))
    }

    func testAColdStartShowsThePayloadsIdWithNoReferrer() {
        let analytics = RecordingAnalytics()
        ChatHostEvents(analytics: analytics).partnerEndedShown(.youNoShowed, referrer: nil)
        let views = analytics.of("screen_viewed")
        XCTAssertEqual(views.count, 1)
        XCTAssertEqual(views.first?.properties["screen_id"] as? String, "date_partner_ended_no_show")
        XCTAssertEqual(views.first?.properties["screen_name"] as? String, "Date - Partner ended no-show")
        XCTAssertEqual(views.first?.properties["referrer_screen_id"] as? String, "")
    }

    func testReachedFromTheChatCarriesTheChatsReferrer() {
        let analytics = RecordingAnalytics()
        ChatHostEvents(analytics: analytics).partnerEndedShown(.dateEndedDiscomfort, referrer: .dateChat)
        let views = analytics.of("screen_viewed")
        XCTAssertEqual(views.count, 1)
        XCTAssertEqual(views.first?.properties["screen_id"] as? String, "date_partner_ended_uncomfortable")
        XCTAssertEqual(views.first?.properties["referrer_screen_id"] as? String, "date_chat")
    }

    /// The route on screen, in a window of the host app's scene, so its appearance runs as it does on a phone.
    private func show(_ view: some View) {
        let controller = UIHostingController(rootView: view)
        controller.overrideUserInterfaceStyle = .light
        let frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        let window = scene.map { UIWindow(windowScene: $0) } ?? UIWindow(frame: frame)
        window.frame = frame
        window.rootViewController = controller
        window.isHidden = false
        window.layoutIfNeeded()
        controller.view.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.2))
        controller.view.layoutIfNeeded()
        window.isHidden = true
        window.rootViewController = nil
    }

    func testScreenViewedFiresEachTimeTheScreenIsShownAndProceedFiresNothingAndStartsTheReview() {
        let analytics = RecordingAnalytics()
        let events = ChatHostEvents(analytics: analytics)
        var proceeded = 0
        func route() -> PartnerEndedRoute {
            PartnerEndedRoute(screen: .dateClosedPositive, name: "Leonie", photoURL: nil,
                              onShown: { events.partnerEndedShown(.dateClosedPositive, referrer: .dateScreen) },
                              onProceed: { proceeded += 1 })
        }
        let first = route()
        show(first)
        XCTAssertEqual(analytics.of("screen_viewed").count, 1)
        // SwiftUI offers no tap a unit test can send; the route hands the screen's one button exactly this closure.
        first.onProceed()
        XCTAssertEqual(proceeded, 1)
        XCTAssertEqual(analytics.names(), ["screen_viewed"], "Proceed fires no event")
        // Shown again (a new showing, the next launch's push): another view.
        show(route())
        XCTAssertEqual(analytics.of("screen_viewed").count, 2)
    }

    // MARK: no exit

    func testSystemBackOnTheScreenDoesNothingTheRouteIsStillOnTop() {
        let route = AppRoute.partnerEnded(dateId: "d1", name: "Leonie", screen: .youNoShowed, photoURL: nil)
        XCTAssertFalse(canGoBack(from: route), "a back off Date 08 must be refused: the path stays")
        // The routes it replaces still go back as they did.
        XCTAssertTrue(canGoBack(from: .chat(.openChat)))
        XCTAssertTrue(canGoBack(from: .changePicker(kind: .time, entry: .chatChip)))
        XCTAssertTrue(canGoBack(from: .dateEnded(ended(.chat, byYou: false), photoURL: nil)))
        XCTAssertTrue(canGoBack(from: nil))
    }

    // MARK: every sheet closes

    private let meet = Date(timeIntervalSince1970: 1_795_024_800) // 2026-11-18T18:00:00Z (19:00 Berlin)

    private final class Draft {
        var value = ""
    }

    private func date(checkedIn: Bool) -> ActiveDate {
        var d = DateSamples.date
        d.id = "d1"
        d.meetAt = meet
        d.unlockAt = meet.addingTimeInterval(-90 * 60)
        d.coordinateOpen = true
        d.checkedIn = checkedIn
        d.endsEnabled = true
        return d
    }

    func testPartnerEndedClosesTheDate06SheetAndItsSavedState() {
        let analytics = RecordingAnalytics()
        let draft = Draft()
        let m = DateEndModel(store: FakeEndStore(), analytics: analytics)
        m.attach(stored: draft.value) { draft.value = $0 }
        m.bind(date(checkedIn: true), now: meet.addingTimeInterval(-300))
        m.openWrap(.endDateBar)
        XCTAssertEqual(m.ui.route, .wrap)
        XCTAssertFalse(draft.value.isEmpty)
        let reported = analytics.names()
        m.discardForPartnerEnd()
        XCTAssertNil(m.ui.route)
        XCTAssertFalse(m.ui.visible)
        XCTAssertEqual(draft.value, "")
        XCTAssertEqual(analytics.names(), reported, "a discard is not the person's exit: nothing is reported")
        // After a process death the model is rebuilt from what was saved: nothing comes back.
        let rebuilt = DateEndModel(store: FakeEndStore())
        rebuilt.attach(stored: draft.value) { _ in }
        rebuilt.bind(date(checkedIn: true), now: meet.addingTimeInterval(-300))
        XCTAssertNil(rebuilt.ui.route)
    }

    func testPartnerEndedClosesTheDate07GuardAndItsSavedState() {
        let analytics = RecordingAnalytics()
        let draft = Draft()
        let m = CancelModel(store: FakeCancelStore(), analytics: analytics)
        m.attach(stored: draft.value) { draft.value = $0 }
        m.bind(date(checkedIn: false), now: meet.addingTimeInterval(-3 * 3600))
        m.open()
        XCTAssertTrue(m.ui.open)
        XCTAssertFalse(draft.value.isEmpty)
        m.discardForPartnerEnd()
        XCTAssertFalse(m.ui.open)
        XCTAssertEqual(draft.value, "")
        XCTAssertFalse(analytics.names().contains("cancel_abandoned_at_guard"))
        let rebuilt = CancelModel(store: FakeCancelStore())
        rebuilt.attach(stored: draft.value) { _ in }
        rebuilt.bind(date(checkedIn: false), now: meet.addingTimeInterval(-3 * 3600))
        XCTAssertFalse(rebuilt.ui.open)
    }

    func testPartnerEndedPopsTheDate05PickerTheChatAndTheDateScreenOffTheStack() {
        let route = AppRoute.partnerEnded(dateId: "d1", name: "Leonie", screen: .youNoShowed, photoURL: nil)
        // The Date screen is the stack's root, the flow: nothing is left over it but Date 08.
        XCTAssertEqual(pathAfterPartnerEnd([.chat(.openChat), .changePicker(kind: .time, entry: .chatChip)], showing: route),
                       [route])
        XCTAssertEqual(pathAfterPartnerEnd([.chatTutorial], showing: route), [route])
        XCTAssertEqual(pathAfterPartnerEnd([], showing: route), [route])
    }

    /// iOS only. Each sheet's draft lives in the host's `@SceneStorage`, which a process death hands back to the model on
    /// its first `attach`; Date 05's is handed to the picker the next time the same picker opens. A restored draft must
    /// not survive Date 08: the model it was restored into is discarded, the draft goes with it, and a model rebuilt from
    /// what was saved finds nothing.
    func testARestoredDate05_06_07DraftIsClearedWhenDate08Shows() {
        let analytics = RecordingAnalytics()

        // Date 06: I, as the process left it.
        let endDraft = Draft()
        endDraft.value = DateEndDraft(route: .report, step: .uncomfortable, entry: .endDateBar, gated: nil, dateId: "d1").encoded
        let end = DateEndModel(store: FakeEndStore(), analytics: analytics)
        end.attach(stored: endDraft.value) { endDraft.value = $0 }
        XCTAssertEqual(end.ui.route, .report, "the draft restored")
        end.discardForPartnerEnd()
        XCTAssertNil(end.ui.route)
        XCTAssertEqual(endDraft.value, "")

        // Date 07: D, with the request it sent.
        let cancelDraft = Draft()
        cancelDraft.value = CancelDraft(sentKind: "time", sentLabel: "20:00", changeRequested: true, dateId: "d1").encoded
        let cancel = CancelModel(store: FakeCancelStore(), analytics: analytics)
        cancel.attach(stored: cancelDraft.value) { cancelDraft.value = $0 }
        XCTAssertTrue(cancel.ui.open, "the draft restored")
        cancel.discardForPartnerEnd()
        XCTAssertFalse(cancel.ui.open)
        XCTAssertEqual(cancelDraft.value, "")

        // Date 05: a time picked, not yet sent.
        let changeDraft = Draft()
        changeDraft.value = ChangePickerDraft(dateId: "d1", kind: ChangeKind.time.rawValue,
                                              slot: meet.addingTimeInterval(1800).timeIntervalSince1970, place: nil,
                                              query: "", fromSearch: false, attemptValue: nil, attemptId: nil).encoded
        let picker = ChangePickerModel(kind: .time, entry: .chatChip, store: FakeChangeStore(), analytics: analytics,
                                       restoring: ChangePickerDraft.decode(changeDraft.value),
                                       persist: { changeDraft.value = $0 })
        XCTAssertNotNil(picker.ui.slot, "the draft restored")
        picker.discardForPartnerEnd()
        XCTAssertTrue(picker.ui.closed)
        XCTAssertEqual(changeDraft.value, "")

        // After a process death each is rebuilt from what was saved: nothing comes back.
        let endAgain = DateEndModel(store: FakeEndStore())
        endAgain.attach(stored: endDraft.value) { _ in }
        XCTAssertNil(endAgain.ui.route)
        let cancelAgain = CancelModel(store: FakeCancelStore())
        cancelAgain.attach(stored: cancelDraft.value) { _ in }
        XCTAssertFalse(cancelAgain.ui.open)
        XCTAssertNil(ChangePickerDraft.decode(changeDraft.value))
        let pickerAgain = ChangePickerModel(kind: .time, entry: .chatChip, store: FakeChangeStore(),
                                            restoring: ChangePickerDraft.decode(changeDraft.value))
        XCTAssertNil(pickerAgain.ui.slot)

        // None of the three is the person's exit: no abandon, no dismissal.
        XCTAssertEqual(analytics.names(), [])
    }
}
