//
//  NavAnalytics.swift
//  ShowUp · the TabBar's event, family A `tab_switched` (registry 1.4.29; respecified for the four
//  tabs by SHOWUP-186)
//
//  The Swift twin of `analytics/NavAnalytics.kt`. "User selects a TabBar item other than the active
//  one ... tab_switched is the nav act, not the screen." A tap on the tab already showing fires
//  nothing; `likes` fires for Freemium too, wherever its tap goes (§37 note).
//

import Foundation

enum NavAnalytics {
    /// `Name`-suffixed, as `CheckInAnalytics.motivationSelectedName` is: the builder below has the
    /// same base name, and a bare `tabSwitched` inside it would be ambiguous.
    static let tabSwitchedName = "tab_switched"

    /// The event for a tap on `tapped` while `active` is showing, or nil when they are the same tab.
    /// `tab` is §37 `nav_tab`.
    static func tabSwitched(active: AppTab, tapped: AppTab) -> (String, [String: any Sendable])? {
        tapped == active ? nil : (tabSwitchedName, ["tab": tapped.trackingValue])
    }
}
