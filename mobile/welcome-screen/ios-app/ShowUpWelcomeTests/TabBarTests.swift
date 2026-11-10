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

    /// SHOWUP-193 ⑰: the disabled-tab hint stays 3200 ms, as `Motion.TAB_HINT` on Android. The tap
    /// and the restart are measured by Android's `TabBarDisabledTest`; this suite has no view driver.
    func testTheDisabledHintStays3200msAsOnAndroid() {
        XCTAssertEqual(Motion.tabHint, 3.2, accuracy: 0.0001)
        XCTAssertEqual(Motion.reveal, 0.2, accuracy: 0.0001)
    }

    func testTheBadgeReadsTheCountCapsAt99PlusAndShowsNothingAtZero() {
        XCTAssertNil(likesBadgeText(0))
        XCTAssertNil(likesBadgeText(-1))
        XCTAssertEqual(likesBadgeText(1), "1")
        XCTAssertEqual(likesBadgeText(99), "99")
        XCTAssertEqual(likesBadgeText(100), "99+")
    }

    func testOnlyAnOpenLikesTabCarriesTheCountAndALockedOneShowsNone() {
        XCTAssertEqual(tabBadgeText(.likes, likes: 2, locked: false), "2")
        // SHOWUP-193, 9 Nov 2026: while a date is active the Likes tab shows no number.
        XCTAssertNil(tabBadgeText(.likes, likes: 2, locked: true))
        XCTAssertNil(tabBadgeText(.search, likes: 2, locked: false))
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
