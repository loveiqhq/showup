// DateEndSheetsTests.swift
// ShowUp · Date 06's frames, one test per frame (SHOWUP-197). Android's `DateEndSheetsTest.kt`.
//
// Each frame asserts the ticket's copy EXACTLY, as the screen builds it from `DateEndCopy` (the strings are the
// contract), the order and the disabled states as the rules give them, and lays the frame out in a hosting
// controller at 375 x 667 to prove it builds and takes a size in every state. The touch areas are the fit sweep's
// (`DateEndFitTests`, every one 44 both ways).

import XCTest
import SwiftUI
@testable import ShowUpWelcome

@MainActor
final class DateEndSheetsTests: XCTestCase {
    private let berlin = TimeZone(identifier: "Europe/Berlin")!
    private let meet = Date(timeIntervalSince1970: 1_795_024_800) // 19:00 in Berlin

    private func date(chatOpen: Bool = true) -> ActiveDate {
        var d = DateSamples.date
        d.meetAt = meet
        d.coordinateOpen = chatOpen
        d.checkedIn = true
        d.endsEnabled = true
        return d
    }

    private func ui(_ route: EndRoute = .wrap, _ step: EndStep = .menu, past: Bool = true, chatOpen: Bool = true,
                    failed: Bool = false, ending: Bool = false) -> DateEndUi {
        var ui = DateEndUi(route: route, step: step, ending: ending, failed: failed, date: date(chatOpen: chatOpen),
                           now: past ? meet.addingTimeInterval(11 * 60) : meet.addingTimeInterval(-5 * 60), checkedIn: true)
        ui.entry = .endDateBar
        return ui
    }

    /// The frame laid out at 375 x 667: it must take a size, with no crash, in every state.
    private func lay(_ view: some View, file: StaticString = #filePath, line: UInt = #line) {
        let host = UIHostingController(rootView: view)
        host.view.frame = CGRect(x: 0, y: 0, width: 375, height: 667)
        host.view.layoutIfNeeded()
        let fitted = host.sizeThatFits(in: CGSize(width: 375, height: 667))
        XCTAssertGreaterThan(fitted.height, 0, file: file, line: line)
    }

    private func host(_ ui: DateEndUi) -> some View {
        DateEndSheetHost(ui: ui, name: "Leonie", zone: berlin, actions: EndActions())
    }

    // MARK: A

    func testAReadsItsThreeStrings() {
        XCTAssertEqual(DateEndCopy.barTitle("Leonie"), "Your date with Leonie")
        XCTAssertEqual(DateEndCopy.barLine, "Tap End date - when your date is finished.")
        XCTAssertEqual(DateEndCopy.barButton, "End date")
        lay(EndDateBar(name: "Leonie", onEnd: {}))
    }

    // MARK: B

    func testBReadsTheTicketsCopyTheTwoRowsAndCancel() {
        XCTAssertEqual(DateEndCopy.wrapTitle("Leonie"), "How did it go with Leonie?")
        XCTAssertEqual(DateEndCopy.metTitle, "We met — date is finished")
        XCTAssertEqual(DateEndCopy.metSub, "Only once you’ve actually met in person.")
        XCTAssertEqual(DateEndCopy.noShowTitle, "My date isn't showing up")
        XCTAssertEqual(DateEndCopy.noShowSub, "They're not here yet — here's what to do and how the wait works.")
        XCTAssertEqual(DateEndCopy.cancel, "Cancel")
        lay(host(ui()))
    }

    // MARK: C

    func testCReadsTheTicketsCopyWithTheNoteEndDateToReviewAndCancel() {
        XCTAssertEqual(DateEndCopy.cTitle, "Thanks for showing up")
        XCTAssertEqual(DateEndCopy.cBody1("Leonie") + "\n" + DateEndCopy.cBody2,
                       "You are wonderful for showing up to Leonie.\nWe hope you had a great time together.")
        XCTAssertEqual(DateEndCopy.whatNext.uppercased(), "WHAT HAPPENS NEXT")
        XCTAssertEqual(DateEndCopy.cStep1, "Please review your date now.")
        XCTAssertEqual(DateEndCopy.cStep2, "Search for new dates resumes after the review.")
        XCTAssertEqual(DateEndCopy.cNote1 + DateEndCopy.cNoteBold1 + DateEndCopy.cNote2 + DateEndCopy.cNoteBold2 + DateEndCopy.cNote3,
                       "This is only for dates that happened. Need to call it off before meeting? Use Cancel this date at the bottom of the Date screen. "
                       + "Ending a date you didn’t attend counts as a no-show and lowers your Show-up Rate.")
        XCTAssertEqual(DateEndCopy.cCta, "End date → Review")
        lay(host(ui(.wrap, .met)))
    }

    // MARK: E and F

    func testEShowsTheFourGuideCardsMessageAndADisabledEndButtonNamingTheAgreedTime() {
        XCTAssertEqual(DateEndCopy.guideTitle, "Not here yet?")
        XCTAssertEqual(DateEndCopy.guide1Bold("Leonie") + DateEndCopy.guide1,
                       "Message Leonie to ask where they are. A quick “on my way?” clears up most late arrivals.")
        XCTAssertEqual(DateEndCopy.guide2A + DateEndCopy.guide2Bold + DateEndCopy.guide2B,
                       "Please give it at least 10 minutes. Something can always come up.")
        XCTAssertEqual(DateEndCopy.guide3, "Please check you are at the right location.")
        XCTAssertEqual(DateEndCopy.guide4A + DateEndCopy.guide4Bold + DateEndCopy.guide4B,
                       "If you get no reply and you’re 10 minutes past your meet time — you’re free to end the date and leave.")
        XCTAssertEqual(DateEndCopy.message("Leonie"), "Message Leonie")
        let shown = ui(.wrap, .guide, past: false)
        let end = guideEnd(pastMeet: shown.pastMeet, meetTime: changeClock(meet, zone: berlin))
        XCTAssertEqual(end.label, "You can end the date from 19:00")
        XCTAssertFalse(end.enabled)
        XCTAssertTrue(end.clock)
        lay(host(shown))
    }

    func testFsEndButtonIsEnabledSaysItHasBeen10MinutesAndOpensG() {
        let shown = ui(.wrap, .guide, past: true)
        let end = guideEnd(pastMeet: shown.pastMeet, meetTime: changeClock(meet, zone: berlin))
        XCTAssertEqual(end.label, "It’s been 10+ minutes — end date")
        XCTAssertTrue(end.enabled)
        XCTAssertFalse(end.clock)
        lay(host(shown))
    }

    func testTheGuideHidesMessageWhileTheChatIsLocked() {
        XCTAssertFalse(messageShown(chatOpen: ui(.wrap, .guide, chatOpen: false).chatOpen))
        XCTAssertTrue(messageShown(chatOpen: ui(.wrap, .guide, chatOpen: true).chatOpen))
        lay(host(ui(.wrap, .guide, chatOpen: false)))
    }

    // MARK: G

    func testGReadsTheTicketsCopyAndSaysNothingAboutTheMatchsFault() {
        XCTAssertEqual(DateEndCopy.gTitle, "Thanks for waiting")
        XCTAssertEqual(DateEndCopy.gBodyA("Leonie") + DateEndCopy.gBodyBold,
                       "You gave Leonie time. Leaving now won’t lower your Show-up Rate.")
        XCTAssertEqual(DateEndCopy.gStep1, "Please give us a little more information in the review now - helps us to determine the consequences for your date partner.")
        XCTAssertEqual(DateEndCopy.gStep2, "No penalty to you — your Show-up Rate stays where it is.")
        XCTAssertEqual(DateEndCopy.gStep3, "Searching for a new date will resume.")
        XCTAssertEqual(DateEndCopy.gCta, "End date and review")
        XCTAssertEqual(DateEndCopy.back, "Back")
        let everything = [DateEndCopy.gStep1, DateEndCopy.gStep2, DateEndCopy.gStep3, DateEndCopy.gBodyBold].joined()
        XCTAssertFalse(everything.contains("This is on them"))
        lay(host(ui(.wrap, .noShow)))
    }

    // MARK: H

    func testHShowsTwoOptionsOnlyInOrderAndCancel() {
        XCTAssertEqual(DateEndCopy.hTitle, "Report a problem")
        XCTAssertEqual(DateEndCopy.hLine, "Everything here is confidential. We've got you.")
        XCTAssertEqual(DateEndCopy.uncomfortableTitle, "I don't feel comfortable")
        XCTAssertEqual(DateEndCopy.uncomfortableSub, "If you don't feel comfortable in the situation - head out now. That's ok!")
        XCTAssertEqual(DateEndCopy.reportCardTitle, "Report a problem")
        XCTAssertEqual(DateEndCopy.reportCardLine, "If you feel unsafe or uncomfortable, let us know.")
        lay(host(ui(.report, .menu)))
    }

    func testHReachesTheSameGuideAndConfirmTheWrapUpSheetDoes() {
        lay(host(ui(.report, .noShow)))
        lay(host(ui(.report, .guide)))
    }

    // MARK: I

    func testIReadsTheTicketsCopyWithHeadOutNowAndStayForNow() {
        XCTAssertEqual(DateEndCopy.iTitle, "We're sorry to hear that.")
        XCTAssertEqual(DateEndCopy.iBodyA + DateEndCopy.iBodyBold1 + DateEndCopy.iBodyB + DateEndCopy.iBodyBold2,
                       "You don't have to explain a thing — you showed up, so you're free to head out now, with no penalty to your Show-up Rate.")
        XCTAssertEqual(DateEndCopy.iStep1, "No penalty to you — you showed up, and that's what counts.")
        XCTAssertEqual(DateEndCopy.iStep2, "Head out whenever you're ready — no need to say goodbye.")
        XCTAssertEqual(DateEndCopy.iStep3, "Review your date next. What you tell us stays confidential.")
        XCTAssertEqual(DateEndCopy.headOut, "Head out now")
        XCTAssertEqual(DateEndCopy.stay, "Stay for now")
        lay(host(ui(.report, .uncomfortable)))
    }

    // MARK: J

    func testJPutsTheBoxAboveTheCtaOnG() { lay(host(ui(.wrap, .noShow, failed: true))) }
    func testJPutsTheBoxAboveTheCtaOnC() { lay(host(ui(.wrap, .met, failed: true))) }
    func testJPutsTheBoxAboveTheCtaOnI() { lay(host(ui(.report, .uncomfortable, failed: true))) }

    func testJsSentence() {
        XCTAssertEqual(DateEndCopy.endFailed, "Couldn't end the date. Check your connection and try again.")
    }

    func testAnEndInFlightLaysOutWithBothButtonsInert() {
        lay(host(ui(.wrap, .noShow, ending: true)))
    }

    // MARK: shared pieces

    func testEndErrorBoxTakesItsOwnTextForDate07AndDefaultsToDate06s() {
        XCTAssertEqual(EndErrorBox().text, DateEndCopy.endFailed)
        XCTAssertEqual(EndErrorBox(text: "Couldn't cancel the date.").text, "Couldn't cancel the date.")
        lay(EndErrorBox(text: "Couldn't cancel the date."))
    }

    func testNothingDrawsWhileTheSheetIsHiddenBehindTheGateOrClosed() {
        let hidden = DateEndUi(route: .wrap, gated: .guide, date: date())
        XCTAssertFalse(hidden.visible)
        XCTAssertFalse(DateEndUi().visible)
    }

    // MARK: the switch (R1)

    func testTheReportCardIsDrawnOnlyWhenTheHostSaysSo() {
        let shown = DateUi(date: date(), now: DateSamples.aNow, loading: false)
        lay(DateScreen(ui: shown, zone: berlin, showReport: true))
        lay(DateScreen(ui: shown, zone: berlin, showReport: false))
    }

    func testTheCheckInBarsEndLinkTakesItsHandlerFromTheHost() {
        lay(CheckInBar(name: "Leonie", onCheckIn: {}, onEnd: {}))
        lay(CheckInBar(name: "Leonie", onCheckIn: {}))
    }
}
