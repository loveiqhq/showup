/*
 * LikesReceivedScreen.kt
 * ShowUp · Date search 03, the Likes tab for a Premium user: the likes they received (SHOWUP-186)
 *
 * StatusBar -> top bar (1: the wordmark and the Premium tag, no filter, no likes chip, no back -- a
 * tab root) -> the list (4), the only scroll container -> TabBar (2, Likes active), with the undo
 * toast 12 above it. The list takes the space between them, so on a short list the TabBar sits at
 * the bottom with no number holding it there.
 *
 * CARD HEIGHT COMES FROM ITS CONTENT. A message wraps and the chips wrap, so no card, list or Y is
 * fixed; a card that leaves (a decline) or closes (a refused confirm) reflows the list. A card has
 * ONE action, `View profile`, the same target as its photo: no decision is made from the list.
 */
package com.showup.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.BlushWash
import com.showup.designsystem.Border
import com.showup.designsystem.Card
import com.showup.designsystem.Chip
import com.showup.designsystem.ChipSize
import com.showup.designsystem.ChipTone
import com.showup.designsystem.Cream
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral
import com.showup.designsystem.OverflowTapTarget
import com.showup.designsystem.Orange
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.Raised
import com.showup.designsystem.ShowUpCount
import com.showup.designsystem.ShowUpCountSize
import com.showup.designsystem.Tab
import com.showup.designsystem.TabBar
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.rememberMotion
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline
import java.time.ZoneId

/** Test and evidence anchors. */
const val LIKES_LIST_TAG = "likes-list"
const val LIKES_CARD_TAG_PREFIX = "likes-card-"
const val LIKES_EMPTY_TAG = "likes-empty"

/**
 * @param settled the list is the screen again after a profile, so a card waiting to fly out may go.
 * @param freezeLeavingMs evidence frame H: every leaving card held this far into its flight.
 */
@Composable
fun LikesReceivedScreen(
    state: SearchUiState,
    zone: ZoneId = ZoneId.systemDefault(),
    settled: Boolean = true,
    onOpen: (String) -> Unit = {},
    onLeft: (String) -> Unit = {},
    onUndo: () -> Unit = {},
    onUndoFocus: (Boolean) -> Unit = {},
    onBackToSearch: () -> Unit = {},
    onTab: (Tab) -> Unit = {},
    onRetry: () -> Unit = {},
    freezeLeavingMs: Int? = null,
) {
    val likes = state.likes
    Box(Modifier.fillMaxSize().background(Cream)) {
        PremiumFrame(
            topBar = {
                Box(Modifier.testTag(SEARCH_TOP_BAR_TAG)) {
                    SearchTopBar(
                        state.likesBadge, {}, {}, Modifier.windowInsetsPadding(WindowInsets.statusBars),
                        premium = true, filter = false, chip = false,
                    )
                }
            },
            region = {
                when {
                    likes.loading && likes.cards.isEmpty() -> ListLoading(PremiumCopy.LIKES_LOADING_LABEL)
                    likes.failure != null && likes.cards.isEmpty() ->
                        CentredStatement(PremiumCopy.LIKES_LOAD_FAILED_PROPOSED, SearchCopy.RETRY, onRetry)
                    likes.empty -> EmptyLikes(onBackToSearch)
                    else -> LikesList(likes, zone, settled, onOpen, onLeft, freezeLeavingMs)
                }
            },
            tabBar = { TabBar(active = Tab.Likes, onTab = onTab, likes = state.likesBadge) },
            toast = { UndoToastSlot(state.undo?.takeIf { it.from == PremiumSurface.Likes }, onUndo, onUndoFocus) },
        )
    }
}

@Composable
private fun LikesList(
    likes: PremiumList,
    zone: ZoneId,
    settled: Boolean,
    onOpen: (String) -> Unit,
    onLeft: (String) -> Unit,
    freezeLeavingMs: Int?,
) {
    val motion = rememberMotion()
    // Kept across a tab switch and a profile, started again when the list read back different.
    key(likes.generation) {
        val scroll = rememberLazyListState()
        val host = remember { FlyOutHost() }
        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = scroll,
            // `8 16 88`: the 88 keeps the last card clear of the toast.
            contentPadding = listPadding(top = 8.dp, bottom = ToastClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { host.viewport = it.boundsInRoot() }
                .testTag(LIKES_LIST_TAG),
        ) {
            item(key = "intro") { LikesIntro() }
            items(likes.shown, key = { it.userId }) { card ->
                val cardModifier = Modifier
                    .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = gridReflowSpec(motion.enabled))
                    .cardFlyOut(
                        host = host,
                        key = card.userId,
                        leaving = card.leaving != null,
                        settled = settled,
                        onLeft = { onLeft(card.userId) },
                        freezeMs = freezeLeavingMs,
                    )
                    .testTag(LIKES_CARD_TAG_PREFIX + card.userId)
                val placeholder = likes.positionOf(card.userId) - 1
                if (card.closed != null) {
                    ClosedLikeCard(card, card.closed, placeholder, cardModifier)
                } else {
                    OpenLikeCard(
                        card, placeholder, zone,
                        onOpen = if (card.open && card.leaving == null) ({ onOpen(card.userId) }) else null,
                        modifier = cardModifier,
                    )
                }
            }
        }
        FlyOutLayer(
            host,
            card = { id ->
                likes.card(id)?.let { c ->
                    val placeholder = likes.positionOf(id) - 1
                    if (c.closed != null) ClosedLikeCard(c, c.closed, placeholder) else OpenLikeCard(c, placeholder, zone, onOpen = null)
                }
            },
            modifier = Modifier.matchParentSize(),
            frozenKeys = if (freezeLeavingMs != null) likes.shown.filter { it.leaving != null }.map { it.userId } else emptyList(),
            frozenAt = frozenProgress(freezeLeavingMs),
        )
        }
    }
}

/**
 * The intro (3): `People who want to meet you` (Lora 700 / 24 / 1.2, `-0.01em`, the italic run washed)
 * over its line (14 / 1.5), gap 6, with 6 more under it before the first card.
 */
@Composable
private fun LikesIntro() {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WashHeadline(
            parts = listOf(PremiumCopy.LIKES_INTRO_LEAD to false, PremiumCopy.LIKES_INTRO_EMPHASIS to true),
            fontSize = 24.sp,
            lineHeight = (24 * 1.2).sp,
            letterSpacing = (-0.01).em,
            color = Fg,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            PremiumCopy.LIKES_INTRO_BODY, color = Neutral, fontFamily = Manrope, fontSize = 14.sp,
            lineHeight = (14 * 1.5).sp, modifier = Modifier.cssLineBox((14 * 1.5).sp),
        )
    }
}

/**
 * An open card (5-11): `Card` padding 14, a row with gap 14. The photo 88 x 120, radius 16, is a
 * button; the column beside it, gap 7: name and distance (and `Not now`) -> the Show-up count -> the
 * message, if one was written -> the meet and motivation chips -> `View profile` (sm, sunset, 2 more
 * above it).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenLikeCard(card: PremiumCard, placeholder: Int, zone: ZoneId, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    val profile = card.profile ?: return
    Card(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(width = 88.dp, height = 120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
                    .clearAndSetSemantics { contentDescription = likesPhotoLabel(card.name, card.notNow) },
            ) { PhotoBackdrop(card.photo, placeholder, Modifier.fillMaxSize()) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                // `display: flex; align-items: baseline; gap: 6; flex-wrap: wrap`, and `Not now` pushed
                // to the end of the line (`margin-left: auto`). The label keeps its own width -- `nowrap`
                // -- and the name and distance wrap in what it leaves, never the other way round.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            nameLine(card.name, card.age), color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Text(
                            likesDistance(profile.distanceKm), color = FgSubtle, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp, modifier = Modifier.alignByBaseline(),
                        )
                    }
                    if (card.notNow) PillLabel(PremiumCopy.NOT_NOW_LABEL, compact = true)
                }
                ShowUpCount(profile.attendedCount, size = ShowUpCountSize.Xs)
                card.message?.takeIf { it.isNotBlank() }?.let { msg ->
                    PrettyText(
                        quotedMessage(msg),
                        TextStyle(color = Fg, fontFamily = Lora, fontStyle = FontStyle.Italic, fontSize = 13.5.sp, lineHeight = (13.5 * 1.4).sp),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip(
                        meetLine(profile.meetAt, profile.meetDay, zone),
                        modifier = Modifier.align(Alignment.CenterVertically),
                        selected = true, tone = ChipTone.Orange, size = ChipSize.Xs,
                        leading = { ink -> Icon(BrandIcon.Clock, 12.dp, tint = ink) },
                    )
                    motivationOf(profile.motivation)?.let { m ->
                        Chip(
                            m.title, modifier = Modifier.align(Alignment.CenterVertically),
                            tone = ChipTone.Lavender, size = ChipSize.Xs,
                            leading = { ink -> Icon(m.icon, 12.dp, tint = ink) },
                        )
                    }
                }
                ViewProfileButton(onOpen, Modifier.padding(top = 2.dp))
            }
        }
    }
}

/**
 * `View profile`: the kit's `Button size="sm"` -- 36 tall, padding `0 16`, 700 / 14, sunset -- drawn at
 * its own width and answering at 44, the photo's target again.
 */
@Composable
private fun ViewProfileButton(onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    OverflowTapTarget(
        touch = if (onOpen != null) {
            Modifier.clickable(role = Role.Button, onClick = onOpen).semantics { contentDescription = PremiumCopy.VIEW_PROFILE }
        } else {
            Modifier.semantics { contentDescription = PremiumCopy.VIEW_PROFILE }
        },
        modifier = modifier,
    ) {
        PrimaryButton(
            PremiumCopy.VIEW_PROFILE, { onOpen?.invoke() },
            modifier = Modifier.width(IntrinsicSize.Max).clearAndSetSemantics {},
            variant = PrimaryButtonVariant.Sunset, height = 36.dp, labelSize = 14.sp, horizontalPadding = 16.dp,
        )
    }
}

/**
 * A closed card (12): the same row with no actions -- the raised background, no shadow, a 1px
 * `--liq-border`, the photo grayscale .92 at .7; the name in `--liq-fg-subtle`, the reason's first
 * line (Lora 700 / 15.5 / 1.25) and its second (12.5 / 1.45), centred in the column with gap 6. Not a
 * tap target. Announced by its first line when it closes in front of the user.
 */
@Composable
fun ClosedLikeCard(card: PremiumCard, reason: CloseReason, placeholder: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        fill = androidx.compose.ui.graphics.SolidColor(Raised),
        border = BorderStroke(1.dp, Border),
        shadows = emptyList(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 88.dp, height = 120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .cssGrayscale(0.92f)
                    .graphicsLayer { alpha = 0.7f }
                    .clearAndSetSemantics {},
            ) { PhotoBackdrop(card.photo, placeholder, Modifier.fillMaxSize()) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(nameLine(card.name, card.age), color = FgSubtle, fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                PrettyText(
                    reason.likesHead(card.name),
                    TextStyle(color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, lineHeight = (15.5 * 1.25).sp),
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                PrettyText(
                    reason.likesBody,
                    TextStyle(color = Neutral, fontFamily = Manrope, fontSize = 12.5.sp, lineHeight = (12.5 * 1.45).sp),
                )
            }
        }
    }
}

/**
 * The empty state (G): padding `72 32 0`, centred, gap 14 -- a blush circle 88 with the heart 36
 * (stroke 2) in orange, `No likes yet` (the italic run washed; 6 more above), the line (14 / 1.5, at
 * most 290), and `Back to search` (md, sunset; 8 more above).
 */
@Composable
private fun EmptyLikes(onBackToSearch: () -> Unit) {
    // Scrolls only when it must: at the largest type on a short phone it outgrows the space above
    // the TabBar, and `Back to search` would be cut.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 32.dp, end = 32.dp, top = 72.dp, bottom = 24.dp)
            .testTag(LIKES_EMPTY_TAG),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(88.dp).background(BlushWash, CircleShape).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) { Icon(BrandIcon.Heart, 36.dp, tint = Orange, strokeWidth = 2f) }
        WashHeadline(
            parts = listOf(PremiumCopy.EMPTY_LEAD to false, PremiumCopy.EMPTY_EMPHASIS to true),
            fontSize = 24.sp,
            lineHeight = (24 * 1.2).sp,
            letterSpacing = (-0.01).em,
            color = Fg,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp).semantics { heading() },
        )
        PrettyText(
            PremiumCopy.EMPTY_BODY,
            TextStyle(color = Neutral, fontFamily = Manrope, fontSize = 14.sp, lineHeight = (14 * 1.5).sp, textAlign = TextAlign.Center),
            Modifier.widthIn(max = 290.dp),
            centered = true,
        )
        PrimaryButton(
            PremiumCopy.BACK_TO_SEARCH, onBackToSearch,
            modifier = Modifier.padding(top = 8.dp).width(IntrinsicSize.Max),
            variant = PrimaryButtonVariant.Sunset, height = 48.dp, labelSize = 15.sp, horizontalPadding = 22.dp,
        )
    }
}
