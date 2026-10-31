/*
 * SearchProfileScreen.kt
 * ShowUp · Date search 01, the Search tab for a Freemium user with a committed Check-In (SHOWUP-184)
 * -- and the same profile opened from the Premium grid (185) or the Likes tab (186)
 *
 * EXTENDED, NOT FORKED ("a second profile component is a bug"). Opened from a list ([SearchUiState.opened])
 * the screen gains what the five reference props add: the Premium tag (`premium`), the `All profiles`
 * / `All likes` back pill under the top bar (`onBack`, `backLabel`), the `Liked you` badge on the hero
 * and `Confirm date` in place of Like (`likedYou`), the Likes tab active with no filter and no chip
 * (`tab`), and every action returning to the list (`onAction`, the view model's). With none of them
 * -- the Freemium queue -- it is Search 01, unchanged.
 *
 * StatusBar -> SearchTopBar (1) -> the stack, the only scroll container -> the action bar (2), sticky
 * above the TabBar with its fade -> TabBar (3). Values in, taps out: nothing here decides anything
 * (`SearchViewModel` does), so every state renders at every phone size with no device.
 *
 * THE FRAME IS A LAYOUT, NOT A STACK OF OVERLAYS. The actions coach needs a scrim over everything --
 * top bar and TabBar included -- with the action bar lifted above it, at the bar's own place, on every
 * phone. [SearchFrame] measures the top bar, the region and the TabBar, then places the scrim over
 * all three and the action bar directly on the TabBar's top edge. No fixed Y anywhere, no measured
 * height fed back a frame late, and no second invisible TabBar to stand in for the real one.
 */
package com.showup.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.showup.welcome.WORDMARK_TAB_GAP
import com.showup.designsystem.cssLineBox
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.showup.designsystem.Border
import com.showup.designsystem.Cream
import com.showup.designsystem.Danger
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Muted
import com.showup.designsystem.Neutral
import com.showup.designsystem.OverflowTapTarget
import com.showup.designsystem.PremiumTag
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.Purple
import com.showup.designsystem.ShadowSm
import com.showup.designsystem.ShadowViolet
import com.showup.designsystem.ShowUpEasing
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.Tab
import com.showup.designsystem.TabBar
import com.showup.designsystem.pillShadows
import com.showup.designsystem.rememberMotion
import com.showup.profile.MediaKind
import com.showup.profile.SheetDismissMethod
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.Spinner
import com.showup.welcome.Wordmark
import java.time.ZoneId

/** `rgba(29,17,41,.62)` -- the coach's scrim, over everything but the action bar. */
private val CoachScrim = Fg.copy(alpha = 0.62f)

/** The action bar's fade: canvas from 0 through .95 at 35% to 1 (`linear-gradient(180deg, ...)`). */
private val ActionFade = Brush.verticalGradient(
    0f to Cream.copy(alpha = 0f),
    0.35f to Cream.copy(alpha = 0.95f),
    1f to Cream,
)

/** The sunset, 135deg, the way the token file writes it. */
private val Sunset = Brush.linearGradient(colorStops = SunsetStops.toTypedArray())

/** Test and evidence anchors. */
const val SEARCH_STACK_TAG = "search-stack"
const val SEARCH_ACTION_BAR_TAG = "search-action-bar"
const val SEARCH_COACH_TAG = "search-coach"
const val SEARCH_LIKE_TAG = "search-like"
const val SEARCH_TOP_BAR_TAG = "search-top-bar"

@Composable
fun SearchProfileScreen(
    state: SearchUiState,
    zone: ZoneId = ZoneId.systemDefault(),
    player: Player? = null,
    onLike: () -> Unit = {},
    onPass: (SearchAnalytics.PassType) -> Unit = {},
    onCoachDone: (SearchAnalytics.CoachDismiss, Boolean) -> Unit = { _, _ -> },
    onCancelConfirm: () -> Unit = {},
    onContinue: () -> Unit = {},
    onDraft: (String) -> Unit = {},
    onDismissMessage: () -> Unit = {},
    onSkipMessage: () -> Unit = {},
    onSendMessage: () -> Unit = {},
    onOpenPhoto: (Int) -> Unit = {},
    onViewerPage: (Int) -> Unit = {},
    onCloseViewer: () -> Unit = {},
    onTogglePlay: (MediaKind) -> Unit = {},
    onScrolledOut: (MediaKind) -> Unit = {},
    onSectionSeen: (Int) -> Unit = {},
    onFilters: () -> Unit = {},
    onLikesChip: () -> Unit = {},
    onTab: (Tab) -> Unit = {},
    onRetry: () -> Unit = {},
    /** `All profiles` / `All likes`, and Android back with nothing over the profile. */
    onBack: () -> Unit = {},
    /** `Confirm date` in mutual Be sure. */
    onConfirmDate: () -> Unit = {},
    /** The sorry sheet closed, any way. */
    onRefusedDone: () -> Unit = {},
) {
    val profile = state.current
    val opened = state.opened
    val fromLikes = opened?.from == PremiumSurface.Likes
    val modal = state.coach || state.sheet != null || state.viewerAt != null
    // Behind any modal, the screen is out of a screen reader's reach -- the coach's three buttons too.
    val behind = if (modal) Modifier.clearAndSetSemantics {} else Modifier
    val motion = rememberMotion()
    // The like sheets' backdrop blur, faded over the sheet's own rise. The coach's scrim has none.
    val sheetBlur by animateFloatAsState(
        if (state.sheet != null) 1f else 0f,
        tween(if (motion.enabled) Motion.SHEET else 0, easing = ShowUpEasing),
        label = "searchSheetBlur",
    )

    Box(Modifier.fillMaxSize().background(Cream)) {
        SearchFrame(
            modifier = Modifier.blurBehindSearchSheet(sheetBlur),
            topBar = {
                // The tag on a wrapper, outside the cleared semantics: a modal hides the bar from a
                // screen reader, and the fit tests still have to find its edge.
                Box(Modifier.testTag(SEARCH_TOP_BAR_TAG)) {
                    Column(behind.windowInsetsPadding(WindowInsets.statusBars)) {
                        // On the Likes tab: the wordmark and the tag only -- "the chip shows on the
                        // Search and Instant tabs only, never on ... a profile opened from it".
                        SearchTopBar(
                            state.likesBadge, onLikesChip, onFilters,
                            premium = opened != null, filter = !fromLikes, chip = !fromLikes,
                        )
                        if (opened != null) {
                            BackPill(if (fromLikes) PremiumCopy.ALL_LIKES else PremiumCopy.ALL_PROFILES, onBack)
                        }
                    }
                }
            },
            region = {
                Box(behind.fillMaxSize()) {
                    when {
                        profile != null -> AnimatedContent(
                            targetState = profile,
                            contentKey = { it.userId },
                            transitionSpec = {
                                val ms = if (motion.enabled) Motion.SCREEN else 0
                                (slideInHorizontally(tween(ms, easing = ShowUpEasing)) { it / 4 } + fadeIn(tween(ms))) togetherWith
                                    (slideOutHorizontally(tween(ms, easing = ShowUpEasing)) { -it / 4 } + fadeOut(tween(ms)))
                            },
                            label = "nextProfile",
                        ) { shown ->
                            // A new profile is a new scroll position: "the next profile replaces the
                            // stack and resets the scroll to the top".
                            key(shown.userId) {
                                ScrollingStack(
                                    shown, state, zone, player, rememberScrollState(),
                                    onOpenPhoto, onTogglePlay, onScrolledOut, onSectionSeen,
                                )
                            }
                        }
                        state.loading -> LoadingState()
                        state.failure != null -> FailedState(onRetry)
                        else -> NoResultsState(onFilters)
                    }
                }
            },
            tabBar = {
                TabBar(active = if (fromLikes) Tab.Likes else Tab.Search, onTab = onTab, modifier = behind, likes = state.likesBadge)
            },
            scrim = if (state.coach) {
                {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(CoachScrim)
                            // A scrim tap is `Got it` -- never Don't show again -- and reported as `backdrop`.
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                onCoachDone(SearchAnalytics.CoachDismiss.Backdrop, false)
                            }
                            .clearAndSetSemantics {},
                    )
                }
            } else {
                null
            },
            actionBar = if (profile != null) {
                {
                    SearchActionBar(
                        coach = state.coach,
                        covered = state.coach || state.sheet != null,
                        likedYou = state.likedYou,
                        onLike = onLike,
                        onPass = onPass,
                        onCoachDone = { dontShow -> onCoachDone(SearchAnalytics.CoachDismiss.Close, dontShow) },
                        modifier = if (state.sheet != null || state.viewerAt != null) Modifier.clearAndSetSemantics {} else Modifier,
                    )
                }
            } else {
                null
            },
        )

        if (profile != null) {
            when (state.sheet) {
                LikeSheet.Confirm -> BeSureSheet(
                    profile.name, profile.meetDay,
                    onCancel = { onCancelConfirm() },
                    onContinue = if (state.likedYou) onConfirmDate else onContinue,
                    mutual = state.likedYou,
                    confirming = state.confirming,
                )
                LikeSheet.Refused -> state.refused?.let { reason ->
                    ConfirmRefusedSheet(
                        name = profile.name,
                        photo = profile.photos.firstOrNull().orEmpty(),
                        reason = reason,
                        buttonLabel = if (fromLikes) PremiumCopy.BACK_TO_LIKES else PremiumCopy.FIND_SOMEONE_ELSE,
                        onDone = onRefusedDone,
                    )
                }
                LikeSheet.Message -> MessageSheet(
                    name = profile.name, draft = state.draft, onDraft = onDraft,
                    onDismiss = { _: SheetDismissMethod -> onDismissMessage() },
                    onSkip = onSkipMessage, onSend = onSendMessage,
                )
                null -> Unit
            }
            state.viewerAt?.let { at ->
                PhotoViewer(profile.name, profile.photos.ifEmpty { listOf("") }, at, onViewerPage, onCloseViewer)
            }
        }
        // Android back closes the coach as `system_back` -- Got it, never Don't show again.
        if (state.coach) {
            androidx.activity.compose.BackHandler { onCoachDone(SearchAnalytics.CoachDismiss.SystemBack, false) }
        }
        // Opened from a list, with nothing over it: Android back is the back pill. A sheet, the
        // viewer and the coach each take back first (declared after this, they are asked first).
        if (opened != null && !modal) {
            androidx.activity.compose.BackHandler { onBack() }
        }
    }
}

/**
 * The back pill (Search 02 13), under the top bar: padding `0 16 10`; a 36-tall pill, padding
 * `0 14 0 8`, 1px `--liq-border` on white, the chevron 18 (stroke 2.2) and the label in Manrope 700 /
 * 13, gap 2. Drawn at 36, answering at 44.
 *
 * [rowBackground] is the profile's cream band under its top bar. The location gate (SHOWUP-188 ⑪)
 * puts the same pill straight on its backdrop, with no band, so it passes [Color.Transparent].
 */
@Composable
internal fun BackPill(label: String, onBack: () -> Unit, rowBackground: Color = Cream) {
    Box(Modifier.fillMaxWidth().background(rowBackground).padding(start = 16.dp, end = 16.dp, bottom = 10.dp)) {
        OverflowTapTarget(
            touch = Modifier
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = label },
        ) {
            Row(
                Modifier
                    .height(36.dp)
                    .background(Elevated, RoundedCornerShape(percent = 50))
                    .border(1.dp, Border, RoundedCornerShape(percent = 50))
                    .padding(start = 8.dp, end = 14.dp)
                    .clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BrandIcon.ChevronLeft, 18.dp, tint = Fg, strokeWidth = 2.2f)
                Text(label, color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * Top bar, region and TabBar in a column; the scrim over all three; the action bar on the TabBar's
 * top edge. Drawn in that order, so the action bar is above the scrim and the scrim above the rest.
 */
@Composable
private fun SearchFrame(
    topBar: @Composable () -> Unit,
    region: @Composable () -> Unit,
    tabBar: @Composable () -> Unit,
    scrim: (@Composable () -> Unit)?,
    actionBar: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(topBar, region, tabBar, scrim ?: {}, actionBar ?: {}),
        modifier = modifier.fillMaxSize(),
    ) { (top, reg, tab, scr, act), constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val row = Constraints(minWidth = w, maxWidth = w)
        val t = top.first().measure(row)
        val b = tab.first().measure(row)
        val regionHeight = (h - t.height - b.height).coerceAtLeast(0)
        val r = reg.first().measure(Constraints.fixed(w, regionHeight))
        val s = scr.firstOrNull()?.measure(Constraints.fixed(w, h))
        // Never taller than the space between the top bar and the TabBar: the coach in it must not
        // reach under the top bar ("never clipped by the top bar"), whatever the type size.
        val a = act.firstOrNull()?.measure(Constraints(minWidth = w, maxWidth = w, maxHeight = (h - b.height - t.height).coerceAtLeast(0)))
        layout(w, h) {
            t.place(0, 0)
            r.place(0, t.height)
            b.place(0, h - b.height)
            s?.place(0, 0)
            a?.place(0, h - b.height - a.height)
        }
    }
}

// ── the top bar (callout 1) ─────────────────────────────────────────────────────────────────────

/**
 * Wordmark at 20, then -- for Premium -- the Premium tag at size 10, then the likes chip and the
 * filter button, gap 10. Both buttons draw smaller than 44 and answer at 44. The chip shows the
 * open-likes count -- the Likes badge's number -- and its tap is the host's: Premium, the Likes tab;
 * Freemium, the likes upsell, its own story, so nothing until it ships and never Likes received.
 *
 * @param premium the tag (Search 02 1): a label, not a control.
 * @param filter / [chip] false on the Likes tab and a profile opened from it (Search 03 1).
 */
@Composable
fun SearchTopBar(
    likes: Int,
    onLikes: () -> Unit,
    onFilters: () -> Unit,
    modifier: Modifier = Modifier,
    premium: Boolean = false,
    filter: Boolean = true,
    chip: Boolean = true,
) {
    // ONE ROW AS DRAWN: the wordmark at the start, the tag, the chip and the filter at the end, gap
    // 10. Where they do not fit side by side -- the Premium bar on the 320 Fold cover screen, or any
    // bar at a large type -- the trailing group drops to a second row, still at the end, rather than
    // running off the screen or cutting the chip's count (the 185 fit sweep, 25 Oct 2026).
    Layout(
        contents = listOf(
            { Wordmark(size = with(LocalDensity.current) { 20.dp.toSp() }, gapEm = WORDMARK_TAB_GAP) },
            {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (premium) PremiumTag(size = 10f)
                    if (chip) LikesChip(likes, onLikes)
                    if (filter) FilterButton(onFilters)
                }
            },
        ),
        modifier = modifier
            .fillMaxWidth()
            .background(Cream)
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
    ) { (mark, tail), constraints ->
        val w = constraints.maxWidth
        val gap = 10.dp.roundToPx()
        val loose = Constraints(maxWidth = w)
        val m = mark.first().measure(loose)
        val t = tail.first().measure(loose)
        if (m.width + gap + t.width <= w) {
            val h = maxOf(m.height, t.height)
            layout(w, h) {
                m.place(0, (h - m.height) / 2)
                t.place(w - t.width, (h - t.height) / 2)
            }
        } else {
            val rowGap = 8.dp.roundToPx()
            layout(w, m.height + rowGap + t.height) {
                m.place(0, 0)
                t.place((w - t.width).coerceAtLeast(0), m.height + rowGap)
            }
        }
    }
}

/** The likes chip: the sunset pill, padding `6 12`, the filled heart 13 and `{n} likes`, 700 / 12. */
@Composable
private fun LikesChip(likes: Int, onLikes: () -> Unit) {
    val label = if (likes == 1) "1 like" else "$likes likes"
    OverflowTapTarget(
        touch = Modifier
            .clickable(role = Role.Button, onClick = onLikes)
            .semantics { contentDescription = label },
    ) {
        Row(
            Modifier
                .background(Sunset, RoundedCornerShape(percent = 50))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(BrandIcon.HeartFilled, 13.dp, tint = Color.White)
            Text(label, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** The filter button: 36 round, white, 1px `--liq-border`, the filter icon 16. `Filters`. */
@Composable
private fun FilterButton(onFilters: () -> Unit) {
    OverflowTapTarget(
        touch = Modifier
            .clickable(role = Role.Button, onClick = onFilters)
            .semantics { contentDescription = SearchCopy.FILTERS_LABEL },
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(Elevated, CircleShape)
                .border(1.dp, Border, CircleShape)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) { Icon(BrandIcon.Filter, 16.dp, tint = Fg) }
    }
}

// ── the action bar (callout 2) and the coach (F) ────────────────────────────────────────────────

/**
 * `Not interested` 56, `Not now` 56, `Like` 64 with its label, gap 10, centred, in a column padded
 * 12 / 16 over a fade into the canvas. While the coach shows, the card sits above the buttons in
 * this same column (gap 14), the fade is removed, and the buttons stay visible but do nothing.
 */
@Composable
fun SearchActionBar(
    coach: Boolean,
    onLike: () -> Unit,
    onPass: (SearchAnalytics.PassType) -> Unit,
    onCoachDone: (dontShowAgain: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** The coach or a like sheet is over the profile; when it closes, focus goes back to Like. */
    covered: Boolean = coach,
    /** A profile that liked the viewer: the third button is `Confirm date` with a check. */
    likedYou: Boolean = false,
) {
    // "On close, focus goes to Like" -- for the coach and for both sheets. Requested on the closing
    // edge only: Like taking focus on every recomposition would steal it from wherever the user was.
    val likeFocus = remember { FocusRequester() }
    val wasCovered = remember { mutableStateOf(covered) }
    LaunchedEffect(covered) {
        if (wasCovered.value && !covered) runCatching { likeFocus.requestFocus() }
        wasCovered.value = covered
    }
    Column(
        modifier
            .fillMaxWidth()
            .then(if (coach) Modifier else Modifier.background(ActionFade))
            .testTag(SEARCH_ACTION_BAR_TAG)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // `weight(fill = false)`: the coach takes what is left above the buttons and no more, and
        // scrolls inside its card when that is not enough -- the largest system font on a small
        // phone. Where it fits it is its own height and the column wraps it, exactly as drawn.
        AnimatedVisibility(
            visible = coach,
            modifier = Modifier.weight(1f, fill = false),
            enter = fadeIn(tween(Motion.FAST)),
            exit = fadeOut(tween(Motion.FAST)),
        ) {
            SearchActionsCoach(onDone = onCoachDone, modifier = Modifier.testTag(SEARCH_COACH_TAG))
        }
        // Inert under the coach: the taps are not sent at all, and the three are hidden from a screen
        // reader while it shows ("everything behind it, the three buttons included").
        val buttons = if (coach) Modifier.clearAndSetSemantics {} else Modifier
        Row(
            buttons.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundAction(BrandIcon.Close, SearchCopy.NOT_INTERESTED, Danger, DangerOutline, enabled = !coach) {
                onPass(SearchAnalytics.PassType.NotInterested)
            }
            RoundAction(BrandIcon.Clock, SearchCopy.NOT_NOW, Muted, Border, enabled = !coach) {
                onPass(SearchAnalytics.PassType.NotNow)
            }
            Row(
                Modifier
                    // A minimum: at the largest type `Confirm date` takes two lines, and the pill
                    // grows to hold them rather than cutting the label.
                    .heightIn(min = 64.dp)
                    .pillShadows(Sunset, ShadowViolet)
                    .clip(RoundedCornerShape(percent = 50))
                    .focusRequester(likeFocus)
                    .clickable(enabled = !coach, role = Role.Button, onClick = onLike)
                    .padding(horizontal = 24.dp)
                    .testTag(SEARCH_LIKE_TAG),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (likedYou) BrandIcon.Check else BrandIcon.Heart, 22.dp, tint = Color.White, strokeWidth = 2.4f)
                ActionLabel(
                    if (likedYou) PremiumCopy.CONFIRM_DATE else SearchCopy.LIKE,
                    TextStyle(color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                )
            }
        }
    }
}

/**
 * The primary action's label: one line wherever it fits, as drawn. Where it does not -- `Confirm
 * date` at the largest type beside two round buttons on a narrow phone -- two lines, set just small
 * enough that the longest word fits, so it is never cut and never broken inside a word (the 185 fit
 * sweep, 25 Oct 2026).
 */
@Composable
private fun ActionLabel(text: String, style: TextStyle) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        val room = constraints.maxWidth
        val oneLine = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        if (!constraints.hasBoundedWidth || oneLine <= room) {
            Text(text, style = style, maxLines = 1, softWrap = false)
        } else {
            val longest = text.split(' ').maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }
            val scale = (room.toFloat() / longest).coerceAtMost(1f)
            val size = style.fontSize * scale
            Text(
                text,
                style = style.copy(fontSize = size, lineHeight = size * 1.15f, textAlign = TextAlign.Center),
                maxLines = 2,
                softWrap = true,
            )
        }
    }
}

/** One of the two icon-only actions: never unlabelled. */
@Composable
private fun RoundAction(
    icon: BrandIcon,
    label: String,
    ink: Color,
    outline: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(56.dp)
            .pillShadows(SolidColor(Elevated), ShadowSm)
            .border(1.5.dp, outline, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, 22.dp, tint = ink, strokeWidth = 2f) }
}

// ── the stack ───────────────────────────────────────────────────────────────────────────────────

/** The stack in its scroll container, reporting what scrolls into view and what playing card leaves it. */
@Composable
private fun ScrollingStack(
    profile: SearchProfile,
    state: SearchUiState,
    zone: ZoneId,
    player: Player?,
    scroll: ScrollState,
    onOpenPhoto: (Int) -> Unit,
    onTogglePlay: (MediaKind) -> Unit,
    onScrolledOut: (MediaKind) -> Unit,
    onSectionSeen: (Int) -> Unit,
) {
    val viewport = remember { mutableStateOf<Rect?>(null) }
    Column(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { viewport.value = it.boundsInWindow() }
            .verticalScroll(scroll, enabled = !state.coach)
            .testTag(SEARCH_STACK_TAG),
    ) {
        SearchProfileStack(
            profile = profile,
            viewer = state.viewer,
            zone = zone,
            playback = state.playback,
            player = player,
            onOpenPhoto = onOpenPhoto,
            onTogglePlay = onTogglePlay,
            likedYou = state.likedYou,
            visibility = { index, kind ->
                Modifier.onGloballyPositioned { c ->
                    val vp = viewport.value ?: return@onGloballyPositioned
                    if (c.visibleIn(vp)) onSectionSeen(index) else kind?.let(onScrolledOut)
                }
            },
        )
    }
}

private fun LayoutCoordinates.visibleIn(viewport: Rect): Boolean {
    val b = boundsInWindow()
    return b.bottom > viewport.top && b.top < viewport.bottom
}

/**
 * The stack, in the reference's JSX order, which IS the spec: hero -> 18 -> shared vibes ->
 * highlight -> vibe today -> photo 2 -> prompt 1 -> voice -> essentials -> photo 3 -> prompt 2 ->
 * video -> How I live -> interests -> prompt 3 -> photos 4 and 5 side by side -> photo 6 -> charms
 * -> 80. Padding 0 / 16 / 16, a flex column with gap 14. A missing section is not rendered and the
 * gap closes by itself; photo 4 without photo 5 is a single 300.
 *
 * Public, unscrolled, for the evidence frame E ("a full-length capture at 390 wide").
 *
 * @param visibility each section's modifier for "it came into view" -- index, and the media it
 *   carries, so a playing card that leaves the screen can pause.
 */
@Composable
fun SearchProfileStack(
    profile: SearchProfile,
    viewer: SearchViewer,
    zone: ZoneId,
    playback: SearchPlayback?,
    player: Player?,
    onOpenPhoto: (Int) -> Unit,
    onTogglePlay: (MediaKind) -> Unit,
    visibility: (index: Int, kind: MediaKind?) -> Modifier = { _, _ -> Modifier },
    /** The hero's `Liked you` badge (Search 02). */
    likedYou: Boolean = false,
) {
    val photos = profile.photos
    val prompts = profile.prompts
    var index = 0
    fun seen(kind: MediaKind? = null) = visibility(index++, kind)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(seen()) { SearchHero(profile, zone, onOpenPhoto, likedYou) }
        Spacer(Modifier.height(18.dp))

        val shared = sharedVibesRow(sharedVibes(profile.vibes, viewer.vibes))
        if (shared.shown.isNotEmpty()) Box(seen()) { SharedVibesRow(profile.name, shared) }
        profile.uplift?.takeIf { it.isNotBlank() }?.let { Box(seen()) { HighlightCard(profile.name, it) } }
        if (profile.vibes.any { vibeLabel(it) != null }) Box(seen()) { VibeTodayCard(profile.name, profile.vibes, viewer.vibes) }

        photos.getOrNull(1)?.let { url -> Box(seen()) { StackPhoto(profile, url, 1, onOpenPhoto) } }
        prompts.getOrNull(0)?.let { Box(seen()) { PromptCard(it) } }
        profile.voice?.let { voice ->
            Box(seen(MediaKind.Voice)) {
                VoiceCard(profile.name, voice, playback?.takeIf { it.kind == MediaKind.Voice }) { onTogglePlay(MediaKind.Voice) }
            }
        }
        val rows = essentials(profile.essentials)
        if (rows.isNotEmpty()) Box(seen()) { EssentialsCard(profile.name, rows) }
        photos.getOrNull(2)?.let { url -> Box(seen()) { StackPhoto(profile, url, 2, onOpenPhoto) } }
        prompts.getOrNull(1)?.let { Box(seen()) { PromptCard(it) } }
        profile.video?.let { video ->
            Box(seen(MediaKind.Video)) {
                VideoCard(
                    profile.name, video, photos.getOrNull(3).orEmpty(),
                    playback?.takeIf { it.kind == MediaKind.Video }, player,
                ) { onTogglePlay(MediaKind.Video) }
            }
        }
        val tiles = lifestyleTiles(profile.howILive)
        if (tiles.isNotEmpty()) Box(seen()) { HowILiveCard(profile.name, tiles) }
        if (profile.interests.any { interestLabel(it) != null }) {
            Box(seen()) { SharedInterestsBlock(profile.name, profile.interests, viewer.interests) }
        }
        prompts.getOrNull(2)?.let { Box(seen()) { PromptCard(it) } }

        val p4 = photos.getOrNull(3)
        val p5 = photos.getOrNull(4)
        when {
            p4 != null && p5 != null -> Row(seen().fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SearchPhoto(p4, 3, 220.dp, 20.dp, profile.name, { onOpenPhoto(3) }, Modifier.weight(1f))
                SearchPhoto(p5, 4, 220.dp, 20.dp, profile.name, { onOpenPhoto(4) }, Modifier.weight(1f))
            }
            p4 != null -> Box(seen()) { StackPhoto(profile, p4, 3, onOpenPhoto) }
        }
        photos.getOrNull(5)?.let { url -> Box(seen()) { StackPhoto(profile, url, 5, onOpenPhoto) } }
        if (profile.charms.isNotEmpty()) Box(seen()) { CharmsCard(profile.charms) }

        Spacer(Modifier.height(80.dp))
    }
}

/** A single photo in the stack: 300 tall, radius 24. */
@Composable
private fun StackPhoto(profile: SearchProfile, url: String, index: Int, onOpenPhoto: (Int) -> Unit) {
    SearchPhoto(url, index, 300.dp, 24.dp, profile.name, { onOpenPhoto(index) })
}

// ── loading, failed, no results ─────────────────────────────────────────────────────────────────

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize().semantics { contentDescription = SearchCopy.LOADING_LABEL }, contentAlignment = Alignment.Center) {
        Spinner(tint = Purple, size = 24.dp)
    }
}

/** The queue could not be read -- offline or a server error. Words approved 27 Oct 2026. */
@Composable
private fun FailedState(onRetry: () -> Unit) {
    CentredStatement(SearchCopy.LOAD_FAILED, SearchCopy.TRY_AGAIN, onRetry)
}

/** The queue ran dry: the interim no-results screen ([SearchEmptyState]); `Widen my search` opens Filters. */
@Composable
private fun NoResultsState(onFilters: () -> Unit) {
    SearchEmptyState(onWiden = onFilters)
}

@Composable
internal fun CentredStatement(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text, color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp,
            lineHeight = (15 * 1.5).sp, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp).cssLineBox((15 * 1.5).sp),
        )
        PrimaryButton(action, onAction, Modifier.widthIn(max = 240.dp), variant = PrimaryButtonVariant.Ghost, height = 48.dp, labelSize = 15.sp)
    }
}
