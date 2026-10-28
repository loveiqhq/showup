/*
 * SearchGridScreen.kt
 * ShowUp · Date search 02, the Search tab for a Premium user with a committed Check-In (SHOWUP-185)
 *
 * StatusBar -> SearchTopBar (1: wordmark, Premium tag, likes chip, filter) -> the grid (2), the only
 * scroll container -> TabBar (3), with the undo toast 12 above it. Values in, taps out:
 * `SearchViewModel` decides everything, so every state renders at every phone size with no device.
 *
 * THE GRID'S HEIGHT IS THE QUEUE. Padding `4 16 16`, two columns `minmax(0, 1fr)`, gap 12, every card
 * 248 tall; nothing is positioned by Y. A card is ONE control: a tap anywhere on it -- the heart
 * included -- opens the full profile. There is no shortcut: every decision is made on the profile.
 */
package com.showup.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.CssShadow
import com.showup.designsystem.Cream
import com.showup.designsystem.Elevated
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Orange
import com.showup.designsystem.PhotoInk
import com.showup.designsystem.ShowUpCount
import com.showup.designsystem.ShowUpCountSize
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.Tab
import com.showup.designsystem.TabBar
import com.showup.designsystem.cssBlurToShadowRadius
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.pillShadows
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import java.time.ZoneId

/** Test and evidence anchors. */
const val SEARCH_GRID_TAG = "search-grid"
const val GRID_CARD_TAG_PREFIX = "grid-card-"
const val GRID_META_TAG_PREFIX = "grid-meta-"

/** Card height: "every card is the same height (248)". */
private val GRID_CARD_HEIGHT = 248.dp

/** The card's bottom scrim: `linear-gradient(180deg, rgba(20,12,28,0) 42%, rgba(20,12,28,.66) 100%)`. */
private val CardScrim = Brush.verticalGradient(0.42f to PhotoInk.copy(alpha = 0f), 1f to PhotoInk.copy(alpha = 0.66f))

/** The heart's `rgba(255,255,255,.92)` and its `0 2px 10px rgba(0,0,0,.18)`. */
private val HeartFill = Elevated.copy(alpha = 0.92f)
private val HeartLift = listOf(CssShadow(Color.Black.copy(alpha = 0.18f), offsetY = 2.dp, blur = 10.dp))

/** `ShowUpCount variant="dark"`'s `backdrop-filter: blur(8px)`: sigma 8 as `Modifier.blur`'s radius. */
private val CountGlassBlur = 12.99.dp

private val Sunset = Brush.linearGradient(colorStops = SunsetStops.toTypedArray())

/**
 * @param settled the grid is the screen again after a profile -- the back transition has finished --
 *   so a card waiting to fly out may go.
 * @param freezeLeavingMs evidence frame K: every leaving card held this far into its flight.
 */
@Composable
fun SearchGridScreen(
    state: SearchUiState,
    zone: ZoneId = ZoneId.systemDefault(),
    settled: Boolean = true,
    onOpen: (String) -> Unit = {},
    onLeft: (String) -> Unit = {},
    onUndo: () -> Unit = {},
    onUndoFocus: (Boolean) -> Unit = {},
    onFilters: () -> Unit = {},
    onLikesChip: () -> Unit = {},
    onTab: (Tab) -> Unit = {},
    onRetry: () -> Unit = {},
    freezeLeavingMs: Int? = null,
) {
    val grid = state.grid
    Box(Modifier.fillMaxSize().background(Cream)) {
        PremiumFrame(
            topBar = {
                Box(Modifier.testTag(SEARCH_TOP_BAR_TAG)) {
                    SearchTopBar(
                        state.likesBadge, onLikesChip, onFilters, Modifier.windowInsetsPadding(WindowInsets.statusBars),
                        premium = true,
                    )
                }
            },
            region = {
                when {
                    grid.loading && grid.cards.isEmpty() -> ListLoading(SearchCopy.LOADING_LABEL)
                    grid.failure != null && grid.cards.isEmpty() -> CentredStatement(SearchCopy.LOAD_FAILED_PROPOSED, SearchCopy.TRY_AGAIN, onRetry)
                    grid.empty -> CentredStatement(SearchCopy.NO_RESULTS_PROPOSED, SearchCopy.WIDEN_FILTERS_PROPOSED, onFilters)
                    else -> GridList(grid, zone, settled, onOpen, onLeft, freezeLeavingMs)
                }
            },
            tabBar = { TabBar(active = Tab.Search, onTab = onTab, likes = state.likesBadge) },
            toast = { UndoToastSlot(state.undo?.takeIf { it.from == PremiumSurface.Grid }, onUndo, onUndoFocus) },
        )
    }
}

@Composable
private fun GridList(
    grid: PremiumList,
    zone: ZoneId,
    settled: Boolean,
    onOpen: (String) -> Unit,
    onLeft: (String) -> Unit,
    freezeLeavingMs: Int?,
) {
    // EVERY CARD THE SAME HEIGHT: 248 as drawn, and at a larger type the height the tallest card's
    // info block needs, so the cards grow together and a row stays level. The info blocks are measured
    // first, at the column's width, before the grid is laid out with that height.
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val columnWidth = ((constraints.maxWidth - (16 + 16 + 12).dp.roundToPx()) / 2).coerceAtLeast(0)
        val infoWidth = (columnWidth - 24.dp.roundToPx()).coerceAtLeast(0)
        val tallest = subcompose("probe") {
            grid.shown.forEach { CardInfo(it, zone, backdrop = null, modifier = Modifier) }
        }.maxOfOrNull { it.measure(Constraints(maxWidth = infoWidth)).height } ?: 0
        val needed = (tallest + INFO_INSET.roundToPx() + TOP_RESERVE.roundToPx()).toDp()
        val cardHeight = maxOf(GRID_CARD_HEIGHT, needed)
        val list = subcompose("grid") {
            GridCards(grid, zone, settled, onOpen, onLeft, freezeLeavingMs, cardHeight)
        }.first().measure(constraints)
        layout(list.width, list.height) { list.place(0, 0) }
    }
}

/** The bottom inset of the info block. */
private val INFO_INSET = 12.dp

/** What the info block leaves free at the top of a card: the heart's 8 + 36, and 8 more. */
private val TOP_RESERVE = 52.dp

@Composable
private fun GridCards(
    grid: PremiumList,
    zone: ZoneId,
    settled: Boolean,
    onOpen: (String) -> Unit,
    onLeft: (String) -> Unit,
    freezeLeavingMs: Int?,
    cardHeight: androidx.compose.ui.unit.Dp,
) {
    // A new read of the grid is a new scroll position; a return from a profile keeps the old one
    // ("Returning restores the grid's scroll position"), saved with the screen.
    key(grid.generation) {
        val scroll = rememberLazyGridState()
        val host = remember { FlyOutHost() }
        Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = scroll,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { host.viewport = it.boundsInRoot() }
                .testTag(SEARCH_GRID_TAG),
        ) {
            items(grid.shown, key = { it.userId }) { card ->
                val position = grid.positionOf(card.userId)
                PremiumGridCard(
                    card = card,
                    placeholder = position - 1,
                    zone = zone,
                    onOpen = if (card.open && card.leaving == null) ({ onOpen(card.userId) }) else null,
                    height = cardHeight,
                    modifier = Modifier
                        // The reflow: the rest move into their new slots, a FLIP of [Motion.GRID_REFLOW].
                        .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = gridReflowSpec())
                        .cardFlyOut(
                            host = host,
                            key = card.userId,
                            leaving = card.leaving != null,
                            settled = settled,
                            onLeft = { onLeft(card.userId) },
                            freezeMs = freezeLeavingMs,
                        )
                        .testTag(GRID_CARD_TAG_PREFIX + card.userId),
                )
            }
        }
        FlyOutLayer(
            host,
            card = { id ->
                grid.card(id)?.let { c -> PremiumGridCard(c, grid.positionOf(id) - 1, zone, onOpen = null, height = cardHeight) }
            },
            modifier = Modifier.matchParentSize(),
            frozenKeys = if (freezeLeavingMs != null) grid.shown.filter { it.leaving != null }.map { it.userId } else emptyList(),
            frozenAt = frozenProgress(freezeLeavingMs),
        )
        }
    }
}

/**
 * `PremiumGridCard`: the photo 248, radius 20, cover, under the bottom scrim; the `Liked you` badge
 * top 12 / left 10 with the 2.5 orange ring; the marked label top 12 / right 10; the heart 36 at top
 * / right 8 -- a marker, not a button; and the info block anchored to the bottom, 12 from each edge,
 * a column with gap 5 that grows upward: Show-up count -> name -> meet time and distance -> motivation.
 *
 * MARKED (Not now, and Not interested during its hold) and CLOSED: grayscale .9 at .5, no heart, no
 * ring, no badge, the label in white. Closed also drops the count and puts its reason line where the
 * meet time and motivation were, and is not a control (`role="group"` with its accessible name).
 *
 * @param placeholder which of the kit's painterly placeholders a card with no photo draws, so the
 *   grid of placeholders is not six copies of one colour.
 * @param onOpen null when the card is not a tap target (closed, or flying out).
 */
@Composable
fun PremiumGridCard(
    card: PremiumCard,
    placeholder: Int,
    zone: ZoneId,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
    /** 248 as drawn; the grid passes more at a large type, the same for every card. */
    height: androidx.compose.ui.unit.Dp = GRID_CARD_HEIGHT,
) {
    val closed = card.closed
    val dim = card.notNow || card.leaving == Leave.NotInterested || closed != null
    val liked = card.leaving == Leave.Liked
    val ring = card.likedYou && !dim
    val shape = RoundedCornerShape(20.dp)
    val backdrop = remember(card.photo, placeholder) { HeroBackdrop { m -> PhotoBackdrop(card.photo, placeholder, m) } }
    Box(modifier.fillMaxWidth()) {
        // `box-shadow: 0 0 0 2.5px` -- a spread with no blur, OUTSIDE the photo: a rounded rect 2.5
        // larger on every side, its corner 20 + 2.5, drawn behind the opaque photo.
        val tile = Modifier
            .fillMaxWidth()
            .height(height)
            .then(if (ring) Modifier.drawBehind { drawLikedRing() } else Modifier)
        Box(
            tile
                .then(
                    if (closed != null) {
                        Modifier.semantics {
                            contentDescription = closed.gridLabel(card.name)
                            // "Announced with its accessible name" when it closes in front of the user.
                            liveRegion = LiveRegionMode.Polite
                        }
                    } else {
                        Modifier
                            .clip(shape)
                            .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
                            .clearAndSetSemantics { contentDescription = gridCardLabel(card.name, card.notNow, card.leaving, card.likedYou) }
                    },
                ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .then(if (dim) Modifier.cssGrayscale(0.9f).graphicsLayer { alpha = 0.5f } else Modifier)
                    .then(if (closed != null) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                PhotoBackdrop(
                    card.photo, placeholder,
                    Modifier.fillMaxSize().onGloballyPositioned { backdrop.photo = it },
                )
                Box(Modifier.fillMaxSize().background(CardScrim))
                if (ring) LikedYouBadge(Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 10.dp))
                CardInfo(card, zone, backdrop, Modifier.align(Alignment.BottomStart).padding(12.dp))
                if (!dim) Heart(liked, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        if (dim) {
            val text = closed?.pill ?: if (card.notNow) PremiumCopy.NOT_NOW_LABEL else PremiumCopy.NOT_INTERESTED_LABEL
            PillLabel(text, Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 10.dp).clearAndSetSemantics {})
        }
    }
}

/** The orange ring around a `Liked you` card. */
private fun DrawScope.drawLikedRing() {
    val spread = 2.5.dp.toPx()
    drawRoundRect(
        color = Orange,
        topLeft = Offset(-spread, -spread),
        size = Size(size.width + 2 * spread, size.height + 2 * spread),
        cornerRadius = CornerRadius((20.dp + 2.5.dp).toPx()),
    )
}

/**
 * The info block, bottom-anchored: count, name, then the meet line and motivation -- or the reason.
 * [backdrop] null draws the count without its glass, for measuring.
 */
@Composable
private fun CardInfo(card: PremiumCard, zone: ZoneId, backdrop: HeroBackdrop?, modifier: Modifier) {
    val density = LocalDensity.current.density
    val closed = card.closed
    val profile = card.profile
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (closed == null && profile != null) {
            val glass: (@Composable (Modifier) -> Unit)? = backdrop?.let { b -> { m -> HeroGlass(b, CountGlassBlur, m) } }
            ShowUpCount(profile.attendedCount, size = ShowUpCountSize.Xs, dark = true, glass = glass)
        }
        Text(
            nameLine(card.name, card.age),
            style = TextStyle(
                color = Color.White, fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                lineHeight = (17 * 1.1).sp,
                // `text-shadow: 0 1px 8px rgba(0,0,0,.3)`.
                shadow = Shadow(Color.Black.copy(alpha = 0.3f), Offset(0f, 1f * density), cssBlurToShadowRadius(8f * density)),
            ),
            modifier = Modifier.cssLineBox((17 * 1.1).sp),
        )
        when {
            // The reason line wraps, `text-wrap: pretty`.
            closed != null -> PrettyText(
                closed.gridLine,
                TextStyle(color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, lineHeight = (11.5 * 1.3).sp),
            )
            profile != null -> {
                val meet = meetLine(profile.meetAt, profile.meetDay, zone)
                val distance = searchDistance(profile.distanceKm)
                MetaLine(gridMetaLine(profile, zone), meet, distance, Modifier.testTag(GRID_META_TAG_PREFIX + card.userId))
                motivationOf(profile.motivation)?.let { m ->
                    // "The motivation label on one line with ellipsis" (Search 02): the whole label is
                    // on the profile one tap away.
                    Text(m.title, style = MotivationStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val MotivationStyle = TextStyle(
    color = Color.White.copy(alpha = 0.92f), fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 11.5.sp,
)

private val MetaStyle = TextStyle(color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)

/**
 * `{HH:MM} {day} · ~N km` on one line wherever it fits -- every phone 375 wide or more, as the ticket
 * requires -- and the time over the distance where it does not, each wrapping if it must. Only the
 * form that fits is composed.
 */
@Composable
private fun MetaLine(full: String, time: String, distance: String, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val fits = !constraints.hasBoundedWidth ||
            measurer.measure(full, MetaStyle, maxLines = 1, softWrap = false).size.width <= constraints.maxWidth
        if (fits) {
            Text(full, style = MetaStyle, maxLines = 1, softWrap = false)
        } else {
            Column {
                Text(time, style = MetaStyle)
                Text(distance, style = MetaStyle)
            }
        }
    }
}

/** The heart: 36, white at .92 with an orange outline heart -- or sunset with a white filled one, liked. */
@Composable
private fun Heart(liked: Boolean, modifier: Modifier) {
    Box(
        modifier
            .size(36.dp)
            .pillShadows(if (liked) Sunset else androidx.compose.ui.graphics.SolidColor(HeartFill), HeartLift)
            .clip(CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (liked) BrandIcon.HeartFilled else BrandIcon.Heart, 17.dp, tint = if (liked) Color.White else Orange, strokeWidth = 2.4f)
    }
}
