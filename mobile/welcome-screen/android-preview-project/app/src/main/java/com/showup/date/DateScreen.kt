/*
 * DateScreen.kt
 * ShowUp · the Date screen, Date 01 (SHOWUP-193): the screen both people live on from the match until
 * the date ends. `ScreenMatchCoord` in `screen-date-reference.jsx`.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * LAYOUT -- one scroll, one sticky footer, no absolute Y
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * One scroll container holds the sunset wash (behind, scrolling with the content) and the content
 * column: padding 0 16 24, gap 8, ending in an 80 spacer. The footer (the bar + the TabBar) is ONE
 * container at the bottom. In the kit it is `position: sticky` and so takes its height at the end of
 * the scroll; here it is laid over the bottom and the scroll ends with a spacer of its measured
 * height, which is the same thing: the last card clears the footer by the 80.
 *
 * ORDER IS FIXED (the ticket): hero → time + place → Coordinate → Report → intent → quick actions →
 * About → common → How the date works → Match Mate (slot) → Cancel this date. In C the SAME
 * Coordinate card moves above time + place; nothing else moves.
 *
 * No header, no back: while a date is active this screen is the app's home.
 *
 * THE BAR GIVES WAY AT THE LARGEST TYPE. The bar and the TabBar are one sticky footer -- unless the
 * two together would cover more than [FOOTER_MAX_SHARE] of the screen (200 % type on a 640-tall
 * phone), where a sticky footer would leave almost nothing of the date visible and squash its own
 * links. Then the bar ends the scroll instead and the TabBar alone stays pinned: everything is still
 * reachable, nothing is cut. The kit draws one size; this is the fit sweep's answer for the others.
 */
package com.showup.date

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.showup.designsystem.Cream
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.Tab
import com.showup.designsystem.TabBar
import com.showup.search.cssLinearGradient
import java.time.ZoneId

const val DATE_SCREEN_TAG = "date:screen"
const val DATE_HERO_TAG = "date:hero"
const val DATE_TIME_CARD_TAG = "date:time-card"
const val DATE_COORDINATE_TAG = "date:coordinate"
const val DATE_REPORT_TAG = "date:report"
const val DATE_INTENT_TAG = "date:intent"
const val DATE_ACTIONS_TAG = "date:actions"
const val DATE_ABOUT_TAG = "date:about"
const val DATE_COMMON_TAG = "date:common"
const val DATE_WORKS_TAG = "date:works"
const val DATE_MATCH_MATE_TAG = "date:match-mate"
const val DATE_CANCEL_TAG = "date:cancel"

/** Search, Likes and Instant are disabled from the match until the date ends; Settings stays open. */
val DATE_LOCKED_TABS = setOf(Tab.Search, Tab.Likes, Tab.Instant)

/** `blur(40px)` -- a CSS sigma -- as a Compose radius: (40 − 0.5) / 0.57735. API 31+; a no-op on 30. */
private val WASH_BLUR = 68.4.dp

/** The most of the screen the sticky footer may cover before the bar moves into the scroll. */
private const val FOOTER_MAX_SHARE = 0.6f

@Composable
fun DateScreen(
    ui: DateUi,
    zone: ZoneId = ZoneId.systemDefault(),
    onOpenChat: () -> Unit = {},
    onReport: () -> Unit = {},
    onDirections: () -> Unit = {},
    onShare: () -> Unit = {},
    onCalendar: () -> Unit = {},
    onCopy: () -> Unit = {},
    onSeeProfile: () -> Unit = {},
    onCancel: () -> Unit = {},
    onCheckIn: () -> Unit = {},
    onEndDate: () -> Unit = {},
    onTab: (Tab) -> Unit = {},
    /** ⑮ Match Mate: a slot, always between How the date works and Cancel. Empty until Date 10. */
    matchMate: @Composable () -> Unit = {},
) {
    val density = LocalDensity.current
    var frameHeight by remember { mutableIntStateOf(0) }
    var barHeight by remember { mutableIntStateOf(0) }
    var tabHeight by remember { mutableIntStateOf(0) }
    val barInFlow = frameHeight > 0 && barHeight + tabHeight > frameHeight * FOOTER_MAX_SHARE
    val pinnedHeight = tabHeight + if (barInFlow) 0 else barHeight
    val tabBar: @Composable () -> Unit = {
        TabBar(
            active = null,
            onTab = onTab,
            modifier = Modifier.onSizeChanged { tabHeight = it.height },
            disabled = DATE_LOCKED_TABS,
            disabledHint = DateCopy.TAB_HINT,
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Cream)
            .windowInsetsPadding(WindowInsets.statusBars)
            .onSizeChanged { frameHeight = it.height }
            .testTag(DATE_SCREEN_TAG),
    ) {
        val date = ui.date
        val state = ui.state
        if (date != null && state != null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box {
                    // ① The sunset wash: top 0, h 320, opacity .18, blur 40 -- behind, scrolling with it.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .graphicsLayer { alpha = 0.18f }
                            .blur(WASH_BLUR, BlurredEdgeTreatment.Unbounded)
                            .cssLinearGradient(135f, SunsetStops.map { it.second }, SunsetStops.map { it.first }),
                    )
                    Column(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DateHero(date.match.photoUrl, Modifier.padding(top = 8.dp).testTag(DATE_HERO_TAG))
                        Spacer(Modifier.height(12.dp))
                        val coordinate = @Composable {
                            CoordinateModule(state, date, ui.now, zone, onOpenChat, Modifier.testTag(DATE_COORDINATE_TAG))
                        }
                        if (state == DateState.Unread) coordinate()
                        TimePlaceCard(date, zone, ui.copied, onCopy, Modifier.testTag(DATE_TIME_CARD_TAG))
                        if (state != DateState.Unread) coordinate()
                        ReportRow(onReport, Modifier.testTag(DATE_REPORT_TAG))
                        IntentCard(date, Modifier.testTag(DATE_INTENT_TAG))
                        QuickActions(date.place != null, onDirections, onShare, onCalendar, Modifier.testTag(DATE_ACTIONS_TAG))
                        Spacer(Modifier.height(2.dp))
                        AboutCard(date.match, onSeeProfile, Modifier.testTag(DATE_ABOUT_TAG))
                        CommonCard(date, Modifier.testTag(DATE_COMMON_TAG))
                        DateWorksCard(date, zone, Modifier.testTag(DATE_WORKS_TAG))
                        matchMate()
                        Spacer(Modifier.height(24.dp))
                        CancelLink(onCancel, Modifier.testTag(DATE_CANCEL_TAG))
                        Spacer(Modifier.height(80.dp))
                    }
                }
                val bar = @Composable { m: Modifier ->
                    DateBar(date.checkedIn, date.match.name, onCheckIn, onEndDate, m.onSizeChanged { barHeight = it.height })
                }
                if (barInFlow) bar(Modifier)
                Spacer(Modifier.height(with(density) { pinnedHeight.toDp() }))
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                if (!barInFlow) {
                    DateBar(date.checkedIn, date.match.name, onCheckIn, onEndDate, Modifier.onSizeChanged { barHeight = it.height })
                }
                tabBar()
            }
        } else {
            // Loading: the screen is reached only once a read found a date, so this is the moment
            // before the first frame of data -- cream and the locked bar, never a spinner over nothing.
            Box(Modifier.align(Alignment.BottomCenter)) { tabBar() }
        }
    }
}
