// PartnerEndedRulesTests.swift
// ShowUp · Date 08: each PartnerScreen draws its own payload, and the layout values the three share are one set
// (SHOWUP-199). Android's `PartnerEndedRulesTest.kt`, case for case.

import XCTest
@testable import ShowUpWelcome

final class PartnerEndedRulesTests: XCTestCase {

    func testEachScreenMapsToItsPayload() {
        let a = partnerEndedPayload(.youNoShowed)
        XCTAssertEqual(a.tone, .blush)
        XCTAssertEqual(a.heroPadV, 30)
        XCTAssertEqual(a.heroPadH, 24)
        XCTAssertEqual(a.avatarGap, 18)
        XCTAssertEqual(a.rowsTop, 18)
        XCTAssertEqual(a.badgeIcon, .clock)
        XCTAssertEqual(a.badgeIconSize, 16)
        XCTAssertEqual(a.badgeStroke, 2.6)
        XCTAssertFalse(a.badgeSuccess)
        XCTAssertTrue(a.usesPhoto)
        XCTAssertEqual(a.rowIcons, [.userX, .messageCircle, .shieldCheck])
        XCTAssertEqual(a.rows.count, 3)
        XCTAssertEqual(a.accent, .orange)

        let b = partnerEndedPayload(.dateEndedDiscomfort)
        XCTAssertEqual(b.tone, .blush)
        XCTAssertEqual(b.heroPadV, 28)
        XCTAssertEqual(b.heroPadH, 22)
        XCTAssertEqual(b.avatarGap, 16)
        XCTAssertEqual(b.rowsTop, 16)
        XCTAssertEqual(b.badgeIcon, .hand)
        XCTAssertEqual(b.badgeIconSize, 15)
        XCTAssertEqual(b.badgeStroke, 2.6)
        XCTAssertFalse(b.badgeSuccess)
        XCTAssertTrue(b.usesPhoto)
        XCTAssertEqual(b.rowIcons, [.hand, .messageCircle, .shieldCheck])
        XCTAssertEqual(b.rows.count, 3)
        XCTAssertEqual(b.accent, .orange)

        let c = partnerEndedPayload(.dateClosedPositive)
        XCTAssertEqual(c.tone, .lilac)
        XCTAssertEqual(c.heroPadV, 30)
        XCTAssertEqual(c.heroPadH, 24)
        XCTAssertEqual(c.avatarGap, 18)
        XCTAssertEqual(c.rowsTop, 18)
        XCTAssertEqual(c.badgeIcon, .check)
        XCTAssertEqual(c.badgeIconSize, 15)
        XCTAssertEqual(c.badgeStroke, 3)
        XCTAssertTrue(c.badgeSuccess)
        XCTAssertFalse(c.usesPhoto)
        XCTAssertEqual(c.rowIcons, [.heart, .search])
        XCTAssertEqual(c.rows.count, 2)
        XCTAssertEqual(c.accent, .violet)
    }

    func testLayoutValuesSharedByAllThreeAreEqual() {
        XCTAssertEqual(PartnerEndedLayout.heroRadius, 28)
        XCTAssertEqual(PartnerEndedLayout.avatarSize, 76)
        XCTAssertEqual(PartnerEndedLayout.badgeSize, 30)
        XCTAssertEqual(PartnerEndedLayout.tileSize, 36)
        XCTAssertEqual(PartnerEndedLayout.tileRadius, 12)
        XCTAssertEqual(PartnerEndedLayout.rowGap, 10)
        for screen in PartnerScreen.allCases {
            let p = partnerEndedPayload(screen)
            XCTAssertEqual(p.rowIcons.count, p.rows.count, screen.rawValue)
        }
    }
}
