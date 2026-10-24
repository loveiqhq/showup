/*
 * TabBar.kt
 * ShowUp · the app's bottom navigation: Search, Likes, Instant, Settings (`components/shared.jsx` TabBar)
 *
 * First drawn for the Check-In gate (SHOWUP-178), where Search is active and "the tabs are NOT
 * disabled": Search and Instant reopen the Check-In, Settings navigates. A design-system primitive
 * rather than a Check-In one, because every tab screen after it -- Search, Match, Settings -- sits
 * on the same bar.
 *
 * FOUR TABS FOR EVERY USER since the Date search epic (SHOWUP-187 rule 10, 21 Oct 2026): `Search ·
 * Likes · Instant · Settings`. The Likes badge counts open received likes, Freemium too; where the
 * tab goes is the host's decision (Premium: Likes received, SHOWUP-186; Freemium: the likes upsell,
 * its own story). One bar with a [likes] count -- a second bar for Premium is the bug the epic names.
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

/** The four tabs, in bar order. [trackingValue] is `enums.json` §37 `nav_tab`. */
enum class Tab(val label: String, val icon: BrandIcon, val trackingValue: String) {
    Search("Search", BrandIcon.Search, "search"),
    Likes("Likes", BrandIcon.Heart, "likes"),
    Instant("Instant", BrandIcon.Zap, "instant"),
    Settings("Settings", BrandIcon.Settings, "settings"),
}

/** Fit-harness anchor for one tab. */
/** Every tab's test tag starts with this. */
const val TAB_TAG_PREFIX = "tab-"

/** The bar itself, top rule included -- what screens' fit tests measure their bottom edge against. */
const val TAB_BAR_TAG = "tab-bar"

internal fun tabTag(tab: Tab) = "$TAB_TAG_PREFIX${tab.name.lowercase()}"

/** What the badge reads: the count, capped at `99+` (SHOWUP-186 ②). Null for none at all. */
fun likesBadgeText(count: Int): String? = when {
    count <= 0 -> null
    count > 99 -> "99+"
    else -> count.toString()
}

/** The badge's accessible label, `{n} open likes` / `1 open like` (SHOWUP-186 copy). */
fun likesBadgeLabel(count: Int): String = if (count == 1) "1 open like" else "$count open likes"

/**
 * The bar: a 1px `--liq-border-soft` top rule over the .92 cream glass, padding 8 / 8 / 0, four
 * equal tabs. Each tab is a 24 icon (stroke 2.2 active, 1.7 idle) over a Manrope 600 / 10 label 3
 * below, `--liq-primary-500` when active and `--liq-fg-subtle` otherwise. The gesture bar's inset is
 * inside it, so the glass reaches the bottom edge. [likes] is the open received likes: `0` shows no
 * badge.
 */
@Composable
fun TabBar(active: Tab, onTab: (Tab) -> Unit, modifier: Modifier = Modifier, likes: Int = 0) {
    Column(modifier.fillMaxWidth().background(TabBarGlass).testTag(TAB_BAR_TAG)) {
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
                val badge = if (tab == Tab.Likes) likesBadgeText(likes) else null
                Column(
                    Modifier
                        .weight(1f)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onTab(tab) })
                        // "Likes, 2 open likes" (SHOWUP-186 accessibility): the badge is part of
                        // the tab's name, not a second thing to swipe to.
                        .semantics {
                            contentDescription = if (badge != null) "${tab.label}, ${likesBadgeLabel(likes)}" else tab.label
                        }
                        .padding(vertical = Spacing.md)
                        .testTag(tabTag(tab)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(24.dp).clearAndSetSemantics {}) {
                        Icon(tab.icon, 24.dp, tint = tint, strokeWidth = if (selected) 2.2f else 1.7f)
                        if (badge != null) LikesBadge(badge)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tab.label,
                        modifier = Modifier.clearAndSetSemantics {},
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

/**
 * The kit's badge, `top: -4; left: 15` of the 24 icon. CONTENT-BOX: the 18 min-width and 18 height
 * are the number's box, the 0 / 5 padding and the 2px cream ring sit outside it -- so a single digit
 * is a 28 x 18 sunset pill inside a 32 x 22 ring, which is what the sheet draws (a pill, not a
 * circle). The ring is the bar's own canvas, so the badge reads as cut out of the icon.
 *
 * Placed by `layout` rather than `offset` so it takes no room in the 24 box: the icon stays centred
 * over its label whatever the count.
 */
@Composable
private fun LikesBadge(text: String) {
    // THE DIGITS DO NOT SCALE WITH THE SYSTEM FONT, on purpose (the Search fit sweep, 22 Oct 2026):
    // the pill is a fixed 18 tall by spec, and at 2x type an `sp` digit was cut in half inside it.
    // The count is not lost to anyone -- it is spoken in full in the tab's label ("Likes, 2 open
    // likes") -- so the badge is drawn like the wordmark, as a mark of a fixed size.
    val density = LocalDensity.current
    val digit = with(density) { 10.5.dp.toSp() }
    val line = with(density) { 18.dp.toSp() }
    Box(
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Int.MAX_VALUE))
                layout(0, 0) { placeable.place(15.dp.roundToPx(), (-4).dp.roundToPx()) }
            }
            .border(2.dp, Cream, RoundedCornerShape(50))
            .padding(2.dp)
            .background(Brush.linearGradient(colorStops = SunsetStops.toTypedArray()), RoundedCornerShape(50))
            .padding(horizontal = 5.dp)
            .defaultMinSize(minWidth = 18.dp)
            .height(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            modifier = Modifier.wrapContentSize(),
            color = Color.White,
            fontFamily = Manrope,
            fontWeight = FontWeight.ExtraBold,
            fontSize = digit,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFeatureSettings = "tnum", lineHeight = line),
            maxLines = 1,
        )
    }
}
