//
//  PremiumRulesTests.swift
//  ShowUp · the Premium grid's and the Likes tab's rules, with no UI (SHOWUP-185, 186)
//
//  The Swift twin of `PremiumRulesTest.kt`: the tickets' exact lines for each closed reason, the cards'
//  accessible names, the meta line, the quoted message, the fly-out's target, and how a re-check
//  status and a confirm's answer are read.
//

import XCTest
@testable import ShowUpWelcome

final class PremiumRulesTests: XCTestCase {

    func testEachReasonCarriesTheTicketsExactLinesWithNoGenderedPronoun() {
        let m = CloseReason.matched
        XCTAssertEqual(m.pill, "Too late")
        XCTAssertEqual(m.gridLine, "Has a date with someone else.")
        XCTAssertEqual(m.likesHead("Anna"), "Too late — Anna has a date with someone else.")
        XCTAssertEqual(m.likesBody, "A like closes as soon as either of you matches. Confirm sooner next time.")
        XCTAssertEqual(m.gridLabel("Anna"), "Anna. Too late — Anna has a date with someone else.")
        XCTAssertEqual(m.sorryHeadLead("Anna"), "We're really sorry, Anna confirmed a date ")
        XCTAssertEqual(m.sorryEmphasis, "just before you did.")
        XCTAssertEqual(m.sorryBody("Anna"), "A like closes the moment either of you matches, and this time Anna's match came first.")

        let n = CloseReason.noOverlap
        XCTAssertEqual(n.pill, "Out of time")
        XCTAssertEqual(n.gridLine, "No time left to meet.")
        XCTAssertEqual(n.likesHead("Anna"), "Out of time — you and Anna have no time left to meet.")
        XCTAssertEqual(n.likesBody, "A like closes when your Check-Ins no longer overlap. Confirm sooner next time.")
        XCTAssertEqual(n.gridLabel("Anna"), "Anna. Out of time — you and Anna have no time left to meet.")
        XCTAssertEqual(n.sorryHeadLead("Anna"), "Sorry, you and Anna ")
        XCTAssertEqual(n.sorryEmphasis, "ran out of time.")
        XCTAssertEqual(n.sorryBody("Anna"),
                       "We're sorry this one didn't work out. Your Check-In and Anna's no longer share any time, so the like has closed.")

        for r in CloseReason.allCases {
            let all = [r.likesHead("X"), r.likesBody, r.sorryBody("X"), r.gridLine].joined(separator: " ").lowercased()
            for pronoun in [" he ", " she ", " his ", " her ", " him "] {
                XCTAssertFalse(all.contains(pronoun), "'\(pronoun)' in \(r)")
            }
        }
    }

    func testAReasonThisAppDoesNotKnowIsNoReason() {
        XCTAssertEqual(CloseReason.of("matched"), .matched)
        XCTAssertEqual(CloseReason.of("no_overlap"), .noOverlap)
        XCTAssertNil(CloseReason.of("expired"))
        XCTAssertNil(CloseReason.of(nil))
    }

    func testACardsAccessibleNameCarriesItsMarkNeverColourAlone() {
        XCTAssertEqual(gridCardLabel("Leonie", notNow: false, leaving: nil), "Open Leonie's profile")
        XCTAssertEqual(gridCardLabel("Leonie", notNow: false, leaving: .liked), "Open Leonie's profile, liked")
        XCTAssertEqual(gridCardLabel("Leonie", notNow: true, leaving: nil), "Open Leonie's profile, not now")
        XCTAssertEqual(gridCardLabel("Leonie", notNow: false, leaving: .notInterested), "Open Leonie's profile, not interested")
        XCTAssertEqual(gridCardLabel("Leonie", notNow: false, leaving: nil, likedYou: true), "Open Leonie's profile, liked you")
        XCTAssertEqual(gridCardLabel("Leonie", notNow: true, leaving: nil, likedYou: true), "Open Leonie's profile, not now",
                       "the user's own mark wins")
        XCTAssertEqual(likesPhotoLabel("Anna", notNow: false), "Open Anna's profile")
        XCTAssertEqual(likesPhotoLabel("Anna", notNow: true), "Open Anna's profile, not now")
    }

    func testTheGridCardsMetaLineIsTheMeetTimeAndTheRoundedDistanceNeverAnExactOne() {
        XCTAssertEqual(gridMetaLine(SearchSamples.grid[0].profile, zone: SearchSamples.zone), "19:00 today · ~1 km")
        XCTAssertEqual(gridMetaLine(SearchSamples.grid[2].profile, zone: SearchSamples.zone), "18:00 tomorrow · < 1 km")
        XCTAssertEqual(likesDistance(4), "· ~4 km")
        XCTAssertEqual(likesDistance(0), "· < 1 km")
    }

    func testTheMessageIsQuotedInStraightDoubleQuotesAsWritten() {
        XCTAssertEqual(quotedMessage("Coffee at Bonanza?"), "\"Coffee at Bonanza?\"")
        XCTAssertEqual(undoLine("Anna"), "Anna's like declined.")
    }

    func testTheFlyOutLandsAtTheVisibleListsCentreXAnd72PercentOfItsHeight() {
        // A 100 x 200 card at (20, 300) in a list 0..400 wide, 100..900 tall.
        let target = flyOutTarget(card: CGRect(x: 20, y: 300, width: 100, height: 200),
                                  view: CGRect(x: 0, y: 100, width: 400, height: 800))
        XCTAssertEqual(target.width, 200 - 70, accuracy: 0.001)
        XCTAssertEqual(target.height, 100 + 800 * 0.72 - 400, accuracy: 0.001)
        XCTAssertEqual(flyOutScale, 0.12)
    }

    func testAReCheckStatusIsReadTolerantlyUnknownOrIncompleteIsNoChange() {
        let at = Date(timeIntervalSince1970: 1_792_688_400)
        XCTAssertEqual(readStatus("available", meetAt: at, meetDay: "today"), .available(meetAt: at, meetDay: "today"))
        XCTAssertNil(readStatus("available", meetAt: nil, meetDay: "today"))
        XCTAssertNil(readStatus("available", meetAt: at, meetDay: nil))
        XCTAssertEqual(readStatus("matched", meetAt: nil, meetDay: nil), .closed(.matched))
        XCTAssertEqual(readStatus("no_overlap", meetAt: nil, meetDay: nil), .closed(.noOverlap))
        XCTAssertNil(readStatus("paused", meetAt: nil, meetDay: nil))
    }

    func testAConfirmsAnswerAMatchWinsAnythingElseIsRefusedNeverClaimingAMatch() {
        XCTAssertEqual(confirmAnswer(matched: true, closed: nil), .won)
        XCTAssertEqual(confirmAnswer(matched: false, closed: "matched"), .refused(.matched))
        XCTAssertEqual(confirmAnswer(matched: false, closed: "no_overlap"), .refused(.noOverlap))
        XCTAssertEqual(confirmAnswer(matched: false, closed: nil), .refused(.noOverlap))
    }

    /// The frozen frames' easing is the flight's own curve: none at the start, all at the end, and
    /// slow out of the hold -- `cubic-bezier(.55,0,.8,.2)` has covered little by half time.
    func testTheFrozenFlightIsEasedOnTheFlightsCurve() {
        XCTAssertEqual(frozenFlightProgress(0), 0, accuracy: 0.001)
        XCTAssertEqual(frozenFlightProgress(800), 1, accuracy: 0.001)
        XCTAssertLessThan(frozenFlightProgress(400), 0.3)
    }
}
