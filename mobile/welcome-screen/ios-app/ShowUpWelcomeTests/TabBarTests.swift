//
//  TabBarTests.swift
//  ShowUp · the four-tab bar and its Likes badge (SHOWUP-187 rule 10, SHOWUP-186 ②), and the
//  `tab_switched` rule it feeds
//
//  The Swift twin of `designsystem/TabBarTest.kt`: the count's text, the 99+ cap, 0 showing
//  nothing, the spoken label, and the nav event firing only for another tab.
//

import XCTest
@testable import ShowUpWelcome

final class TabBarTests: XCTestCase {

    func testTheFourTabsAreSearchLikesInstantSettingsInThatOrder() {
        XCTAssertEqual(AppTab.allCases.map(\.label), ["Search", "Likes", "Instant", "Settings"])
        // §37 nav_tab, the values tab_switched carries.
        XCTAssertEqual(AppTab.allCases.map(\.trackingValue), ["search", "likes", "instant", "settings"])
    }

    func testTheBadgeReadsTheCountCapsAt99PlusAndShowsNothingAtZero() {
        XCTAssertNil(likesBadgeText(0))
        XCTAssertNil(likesBadgeText(-1))
        XCTAssertEqual(likesBadgeText(1), "1")
        XCTAssertEqual(likesBadgeText(99), "99")
        XCTAssertEqual(likesBadgeText(100), "99+")
    }

    func testTheBadgeLabelIsOneOpenLikeOrNOpenLikes() {
        XCTAssertEqual(likesBadgeLabel(1), "1 open like")
        XCTAssertEqual(likesBadgeLabel(2), "2 open likes")
        // The cap is the drawing's; the spoken count is the real one.
        XCTAssertEqual(likesBadgeLabel(120), "120 open likes")
    }

    func testTabSwitchedFiresForAnotherTabNeverForTheOneAlreadyShowing() {
        XCTAssertNil(NavAnalytics.tabSwitched(active: .search, tapped: .search), "a tap on the active tab is no nav act")
        let event = NavAnalytics.tabSwitched(active: .search, tapped: .likes)
        XCTAssertEqual(event?.0, "tab_switched")
        XCTAssertEqual(event?.1["tab"] as? String, "likes")
        XCTAssertEqual(event?.1["sensitivity_class"] as? Int, 0, "sensitivity_class 0, as on every row")
        XCTAssertEqual(NavAnalytics.tabSwitched(active: .likes, tapped: .settings)?.1["tab"] as? String, "settings")
    }
}
