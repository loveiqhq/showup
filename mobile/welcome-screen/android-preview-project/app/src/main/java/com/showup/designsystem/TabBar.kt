/*
 * TabBar.kt
 * ShowUp · the app's bottom navigation: Search, Instant, Settings (`components/shared.jsx` TabBar)
 *
 * First drawn for the Check-In gate (SHOWUP-178), where Search is active and "the tabs are NOT
 * disabled": Search and Instant reopen the Check-In, Settings navigates. A design-system primitive
 * rather than a Check-In one, because every tab screen after it -- Search, Match, Settings -- sits
 * on the same bar.
 *
 * NOT BUILT HERE: the kit's disabled state (lock badge, hint toast) belongs to Match's "active
 * date" lock, which no ticket has asked for yet; it lands with that ticket as a parameter of this
 * component, not as a second bar.
 *
 * The kit blurs the content behind the bar (`backdrop-filter: blur(20px)`); Compose has no live
 * backdrop blur below API 31, so the bar is its .92 glass alone. Over the gate's flat cream the two
 * are indistinguishable.
 */
package com.showup.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

/** The three tabs, in order. */
enum class Tab(val label: String, val icon: BrandIcon) {
    Search("Search", BrandIcon.Search),
    Instant("Instant", BrandIcon.Zap),
    Settings("Settings", BrandIcon.Settings),
}

/** Fit-harness anchor for one tab. */
internal fun tabTag(tab: Tab) = "tab-${tab.name.lowercase()}"

/**
 * The bar: a 1px `--liq-border-soft` top rule over the .92 cream glass, padding 8 / 8 / 0, three
 * equal tabs. Each tab is a 24 icon (stroke 2.2 active, 1.7 idle) over a Manrope 600 / 10 label 3
 * below, `--liq-primary-500` when active and `--liq-fg-subtle` otherwise. The gesture bar's inset is
 * inside it, so the glass reaches the bottom edge.
 */
@Composable
fun TabBar(active: Tab, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(TabBarGlass)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSoft))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.md, end = Spacing.md, top = Spacing.md)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Tab.entries.forEach { tab ->
                val selected = tab == active
                val tint = if (selected) Purple else FgSubtle
                Column(
                    Modifier
                        .weight(1f)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onTab(tab) })
                        .padding(vertical = Spacing.md)
                        .testTag(tabTag(tab)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, 24.dp, tint = tint, strokeWidth = if (selected) 2.2f else 1.7f)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tab.label,
                        color = tint,
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.2.sp,
                    )
                }
            }
        }
    }
}
