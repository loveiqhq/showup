/*
 * NavAnalytics.kt
 * ShowUp · the TabBar's event, family A `tab_switched` (registry 1.4.29; respecified for the four
 * tabs by SHOWUP-186)
 *
 * "User selects a TabBar item other than the active one ... The screen it opens fires its own
 * screen_viewed; tab_switched is the nav act, not the screen." So a tap on the tab already showing
 * fires nothing, and `likes` fires for Freemium too -- the tab is in every user's bar, wherever its
 * tap goes (§37 note).
 *
 * Its own catalogue because it belongs to the shell, not to a flow: the gate, Settings and every
 * Search screen share one bar and one event.
 */
package com.showup.analytics

import com.showup.designsystem.Tab

object NavAnalytics {
    const val TAB_SWITCHED = "tab_switched"

    /**
     * The event for a tap on [tapped] while [active] is showing, or null when they are the same tab.
     * `tab` is §37 `nav_tab`.
     */
    fun tabSwitched(active: Tab, tapped: Tab): Pair<String, Map<String, Any>>? =
        if (tapped == active) null else TAB_SWITCHED to mapOf("tab" to tapped.trackingValue)
}
