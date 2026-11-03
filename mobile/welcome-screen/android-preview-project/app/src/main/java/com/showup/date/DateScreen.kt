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
 * THE WASH IS OVER THE CARDS, NOT UNDER THEM. It is `position: absolute` in the kit and the cards are
 * not, so CSS paints it above them at its .18 -- the sheet's time card is deeper lilac at its top,
 * and C's card lavender, for exactly that reason. The hero and the unread avatar are positioned and
 * come after the wash, so they paint above it: they register with [WashHoles] and the wash leaves
 * them out. It draws nothing touchable, so taps pass through it.
 *
 * THE BAR GIVES WAY AT THE LARGEST TYPE. The bar and the TabBar are one sticky footer -- unless the
 * two together would cover more than [FOOTER_MAX_SHARE] of the screen (200 % type on a 640-tall
 * phone), where a sticky footer would leave almost nothing of the date visible and squash its own
 * links. Then the bar ends the scroll instead and the TabBar alone stays pinned: everything is still
 * reachable, nothing is cut. The kit draws one size; this is the fit sweep's answer for the others.
 */
package com.showup.date

import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
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

/** The wash's CSS box: top 0, h 320. */
private val WASH_BOX = 320.dp

/**
 * The wash's `blur(40px)`, DRAWN rather than rendered. RenderEffect blur does not exist on API 30
 * (this app's minimum) and the edge it softens is the bottom one -- the top and sides are the
 * screen's -- so the blur is the fade it produces there: full until 2σ above the 320 edge, half at
 * the edge, gone 2σ below it. One look on every API level.
 */
private val WASH_FADE_FROM = 240.dp
private val WASH_FADE_TO = 400.dp

/** The most of the screen the sticky footer may cover before the bar moves into the scroll. */
private const val FOOTER_MAX_SHARE = 0.6f

/** What paints ABOVE the wash, as a positioned element after it in the kit's DOM: see the header. */
internal class WashHoles {
    /** The box the wash and the content share; set when it is placed, before its children are. */
    var origin: LayoutCoordinates? = null
    val outlines = mutableStateMapOf<String, Pair<Rect, Shape>>()
}

internal val LocalWashHoles = staticCompositionLocalOf<WashHoles?> { null }

/** Paint this above the wash. Written only when it moves within the screen, never on a scroll. */
@Composable
internal fun Modifier.aboveWash(key: String, shape: Shape): Modifier {
    val holes = LocalWashHoles.current ?: return this
    DisposableEffect(holes, key) { onDispose { holes.outlines.remove(key) } }
    return onGloballyPositioned { c ->
        val origin = holes.origin?.takeIf { it.isAttached } ?: return@onGloballyPositioned
        val r = origin.localBoundingBoxOf(c, clipBounds = false)
        if (holes.outlines[key]?.first != r) holes.outlines[key] = r to shape
    }
}

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
    /** The scroll, hoisted only so the evidence harness can photograph frame D's end. */
    scroll: ScrollState = rememberScrollState(),
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
            // Cream under the .92 glass: the kit blurs what scrolls behind the bar, which over these
            // cards reads as cream; without a backdrop blur the cards' text showed through it.
            modifier = Modifier.background(Cream).onSizeChanged { tabHeight = it.height },
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
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                val holes = remember { WashHoles() }
                Box(Modifier.onPlaced { holes.origin = it }) {
                    CompositionLocalProvider(LocalWashHoles provides holes) {
                        DateContent(date, state, ui, zone, onOpenChat, onReport, onDirections, onShare, onCalendar, onCopy, onSeeProfile, onCancel, matchMate)
                    }
                    // ① The sunset wash: top 0, h 320, opacity .18, blur 40 -- scrolling with the content,
                    // drawn after it (see the header).
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(WASH_FADE_TO)
                            .graphicsLayer { alpha = 0.18f; compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                val cut = Path()
                                holes.outlines.values.forEach { (r, shape) ->
                                    cut.addPath(Path().apply { addOutline(shape.createOutline(r.size, layoutDirection, this@drawWithContent)) }, r.topLeft)
                                }
                                clipPath(cut, ClipOp.Difference) { this@drawWithContent.drawContent() }
                            }
                            .cssLinearGradient(135f, SunsetStops.map { it.second }, SunsetStops.map { it.first }, boxHeight = WASH_BOX)
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    Brush.verticalGradient(
                                        0f to Color.Black,
                                        (WASH_FADE_FROM.toPx() / size.height) to Color.Black,
                                        1f to Color.Transparent,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    )
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

/** The content column: padding 0 16 24, gap 8, in the ticket's fixed order, ending in the 80. */
@Composable
private fun DateContent(
    date: ActiveDate,
    state: DateState,
    ui: DateUi,
    zone: ZoneId,
    onOpenChat: () -> Unit,
    onReport: () -> Unit,
    onDirections: () -> Unit,
    onShare: () -> Unit,
    onCalendar: () -> Unit,
    onCopy: () -> Unit,
    onSeeProfile: () -> Unit,
    onCancel: () -> Unit,
    matchMate: @Composable () -> Unit,
) {
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DateHero(
            date.match.photoUrl,
            Modifier.padding(top = 8.dp).aboveWash("hero", RoundedCornerShape(24.dp)).testTag(DATE_HERO_TAG),
        )
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
