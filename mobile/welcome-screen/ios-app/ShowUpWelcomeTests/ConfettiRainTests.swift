//
//  ConfettiRainTests.swift
//  ShowUp · Embrace 2's confetti, evaluated frame by frame against the reference (SHOWUP-166)
//
//  The Swift half of `ConfettiRainTest.kt`. The keyframes are a pure function of time, so every
//  number the ticket states is checked here rather than eyeballed in a recording: the table, the
//  stagger, the fall distance, the fade over the last 20%, nothing born or killed on screen, and the
//  layer gone by 3000 ms.
//

import XCTest
@testable import ShowUpWelcome

final class ConfettiRainTests: XCTestCase {

    private let height = 844.0

    func testThirtyTwoPiecesFromTheFixedTableDeterministic() {
        XCTAssertEqual(confettiPieces.count, 32)
        let first = confettiPieces[0]
        XCTAssertEqual(first.leftPercent, 4)
        XCTAssertEqual(first.fallMs, 2300)
        XCTAssertEqual(first.shape, .strip)
        let last = confettiPieces[31]
        XCTAssertEqual(last.leftPercent, 85)
        XCTAssertEqual(last.delayMs, 200)
    }

    func testStagger0To700Falls1950To2600Sizes8To14() {
        XCTAssertTrue(confettiPieces.allSatisfy { (0...700).contains($0.delayMs) })
        XCTAssertTrue(confettiPieces.allSatisfy { (1950...2600).contains($0.fallMs) })
        XCTAssertTrue(confettiPieces.allSatisfy { (8...14).contains($0.size) })
        XCTAssertEqual(confettiPieces.map(\.delayMs).min(), 0)
        XCTAssertEqual(confettiPieces.map(\.delayMs).max(), 700)
    }

    func testSwayPeriodsAre700820940And1060ByIndex() {
        XCTAssertEqual((0...4).map(confettiSwayMs), [700, 820, 940, 1060, 700])
    }

    func testTheLastPieceLeavesTheScreenByAbout2930MsInsideThe3000MsLayer() {
        let lastLanding = confettiPieces.map { $0.delayMs + $0.fallMs }.max() ?? .infinity
        XCTAssertLessThanOrEqual(lastLanding, 2930)
        XCTAssertLessThan(lastLanding, confettiTotalMs)
    }

    func testEveryPieceStarts40AboveTheTopEdgeAndHoldsThereThroughItsDelay() {
        for (i, p) in confettiPieces.enumerated() {
            let before = confettiFrame(p, index: i, elapsedMs: 0, height: height)
            XCTAssertEqual(before.y, -40, accuracy: 0.001)
            XCTAssertEqual(before.alpha, 1, accuracy: 0.001)
        }
    }

    func testEveryPieceEnds40BelowTheBottomEdgeFullyFadedNoneVanishesOnScreen() {
        for (i, p) in confettiPieces.enumerated() {
            let end = confettiFrame(p, index: i, elapsedMs: p.delayMs + p.fallMs, height: height)
            XCTAssertEqual(end.y, height + 40, accuracy: 0.01)
            XCTAssertEqual(end.alpha, 0, accuracy: 0.001)
        }
    }

    func testOpacityHoldsAt1Until80PercentOfTheFall() {
        let p = confettiPieces[0]
        let at = p.delayMs + p.fallMs * 0.79
        XCTAssertEqual(confettiFrame(p, index: 0, elapsedMs: at, height: height).alpha, 1, accuracy: 0.0001)
        let later = p.delayMs + p.fallMs * 0.9
        let fading = confettiFrame(p, index: 0, elapsedMs: later, height: height).alpha
        XCTAssertTrue((0.01...0.99).contains(fading), "fading at 90%: \(fading)")
    }

    func testTheFallDistanceIsTheScreensOwnHeightNot884() {
        let p = confettiPieces[3]
        let end = p.delayMs + p.fallMs
        XCTAssertEqual(confettiFrame(p, index: 3, elapsedMs: end, height: 667).y, 707, accuracy: 0.01)
        XCTAssertEqual(confettiFrame(p, index: 3, elapsedMs: end, height: 932).y, 972, accuracy: 0.01)
    }

    func testTheSameDurationsOnEveryDevice() {
        let p = confettiPieces[5]
        let mid = p.delayMs + p.fallMs / 2
        let a = confettiFrame(p, index: 5, elapsedMs: mid, height: 667)
        let b = confettiFrame(p, index: 5, elapsedMs: mid, height: 932)
        // Same progress, different distance: the fraction of the fall covered is identical.
        XCTAssertEqual((a.y + 40) / (667 + 80), (b.y + 40) / (932 + 80), accuracy: 0.0001)
    }

    func testTheSwingAlternatesAndEasesIntoEachTurn() {
        let p = confettiPieces[0] // sway 14, rot0 -18, spin 540, swing 700
        let start = confettiFrame(p, index: 0, elapsedMs: p.delayMs, height: height)
        let turn = confettiFrame(p, index: 0, elapsedMs: p.delayMs + 700, height: height)
        let back = confettiFrame(p, index: 0, elapsedMs: p.delayMs + 1400, height: height)
        XCTAssertEqual(start.swayX, -14, accuracy: 0.01)
        XCTAssertEqual(turn.swayX, 14, accuracy: 0.01)
        XCTAssertEqual(back.swayX, -14, accuracy: 0.01)
        XCTAssertEqual(start.rotation, -18, accuracy: 0.01)
        XCTAssertEqual(turn.rotation, -18 + 540, accuracy: 0.01)
        // rotateY 0 -> 180: the flip runs from face-on, through edge-on, to face-on reversed.
        XCTAssertEqual(start.flipScale, 1, accuracy: 0.001)
        XCTAssertEqual(turn.flipScale, -1, accuracy: 0.001)
        let quarter = confettiFrame(p, index: 0, elapsedMs: p.delayMs + 350, height: height)
        XCTAssertLessThan(abs(quarter.flipScale), 0.2, "edge-on mid-swing")
    }

    func testTheEasingCurvesAreTheCSSOnes() {
        // `ease-in-out` and the fall's cubic-bezier, solved at their ends and middle.
        let inOut = CubicBezier(x1: 0.42, y1: 0, x2: 0.58, y2: 1)
        XCTAssertEqual(inOut(0), 0)
        XCTAssertEqual(inOut(1), 1)
        XCTAssertEqual(inOut(0.5), 0.5, accuracy: 0.001)
        let fall = CubicBezier(x1: 0.3, y1: 0.4, x2: 0.6, y2: 1)
        XCTAssertGreaterThan(fall(0.5), 0.5, "the fall is front-loaded")
    }
}
