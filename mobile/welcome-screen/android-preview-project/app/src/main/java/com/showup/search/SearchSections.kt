/*
 * SearchSections.kt
 * ShowUp · the sections of a Search profile, in the reference's order (SHOWUP-184)
 *
 * Every number is `screen-search-profile-reference.jsx`'s, read from the code rather than measured
 * off the sheet ("reference file wins on numbers"). Every section is optional: the stack renders a
 * section only when its source has content, and a missing one leaves no card, no gap and no
 * placeholder -- the column's 14 gap closes over it.
 *
 * Built once and shared: `SharedChip`, `SharedVibesRow`, `SharedInterestsBlock` and the photo slot
 * are what Search 02 and 03 reuse; a second copy of any of them is a bug.
 */
package com.showup.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.showup.designsystem.balanceWrap
import com.showup.designsystem.prettyWrap
import androidx.compose.ui.text.AnnotatedString
import com.showup.designsystem.cssBlurToShadowRadius
import com.showup.designsystem.cssLineBox
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.showup.designsystem.BlushWash
import com.showup.designsystem.Card
import com.showup.designsystem.CssShadow
import com.showup.designsystem.Chip
import com.showup.designsystem.ChipTone
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.LilacWash
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.Raised
import com.showup.designsystem.ShadowViolet
import com.showup.designsystem.ShowUpCount
import com.showup.designsystem.ShowUpCountSize
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.pillShadows
import com.showup.profile.MediaPrompts
import com.showup.profile.topicText
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ── colours of this screen's own, each named for the reference value it carries ─────────────────

/** `rgba(254,104,57,.12)` -- the meet card's clock tile. */
private val MeetTileFill = Orange.copy(alpha = 0.12f)

/** `rgba(255,255,255,.85)` -- the distance pill on the photo. */
private val PhotoPillFill = Color.White.copy(alpha = 0.85f)

/** `rgba(255,255,255,.22)` and `.30` -- the motivation pill's glass and its edge. */
private val GlassFill = Color.White.copy(alpha = 0.22f)
private val GlassEdge = Color.White.copy(alpha = 0.30f)

/** `rgba(129,42,236,.10)` -- the prompt card's edge. */
private val PromptEdge = Purple.copy(alpha = 0.10f)

/** `rgba(29,17,41,.18)` -- a waveform bar past the violet ones. */
private val BarRest = Fg.copy(alpha = 0.18f)

/** `rgba(255,255,255,.92)` and `0 8px 24px rgba(0,0,0,.16)` -- the video's play disc. */
private val VideoDiscFill = Color.White.copy(alpha = 0.92f)
private val VideoDiscLift = listOf(CssShadow(Color.Black.copy(alpha = 0.16f), offsetY = 8.dp, blur = 24.dp))


/**
 * The kit's six photo placeholders (`SEARCH_V2_PROFILE.photoColors`, 155deg): what a slot shows
 * while its photo loads, if it cannot load, and in every preview and screenshot -- never a grey box.
 */
internal val PHOTO_PLACEHOLDERS: List<List<Color>> = listOf(
    listOf(Color(0xFFFCC8A7), Color(0xFFE89B7A), Color(0xFFB86B5C)),
    listOf(Color(0xFFD6E8E6), Color(0xFF99C7C3), Color(0xFF4D8F88)),
    listOf(Color(0xFFF5C8A2), Color(0xFFB2C5B4), Color(0xFF7A9A86)),
    listOf(Color(0xFFFCDCDC), Color(0xFFE69CB5), Color(0xFFB85B7C)),
    listOf(Color(0xFFEAD9F4), Color(0xFFC8A6E8), Color(0xFF8B5CF6)),
    listOf(Color(0xFFFFE2C5), Color(0xFFFFB58A), Color(0xFFE07A4C)),
)
private val PLACEHOLDER_STOPS = listOf(
    listOf(0f, 0.40f, 1f), listOf(0f, 0.50f, 1f), listOf(0f, 0.70f, 1f),
    listOf(0f, 0.50f, 1f), listOf(0f, 0.40f, 1f), listOf(0f, 0.40f, 1f),
)

/**
 * A CSS `linear-gradient(<angle>deg, ...)` drawn as CSS draws it: the gradient line through the
 * centre at that angle, long enough that its ends touch the box's corners. Compose's default runs
 * corner to corner, which is right only at 135 degrees on a square.
 */
internal fun Modifier.cssLinearGradient(angleDeg: Float, colors: List<Color>, stops: List<Float>): Modifier =
    drawBehind {
        val a = Math.toRadians(angleDeg.toDouble())
        val dx = sin(a).toFloat()
        val dy = -cos(a).toFloat()
        val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawRect(
            Brush.linearGradient(
                colorStops = stops.zip(colors).toTypedArray(),
                start = Offset(c.x - dx * half, c.y - dy * half),
                end = Offset(c.x + dx * half, c.y + dy * half),
            ),
        )
    }

// ── the photo slot ──────────────────────────────────────────────────────────────────────────────

/**
 * A photo: `object-fit: cover`, its corner, the kit's washes over it (`PortraitPlaceholder` with a
 * photo loaded: the highlight at .35, the shade at .40, the bottom 40% darkening that keeps white
 * type legible), and whatever the slot carries on top. The whole slot is the tap that opens the
 * viewer.
 *
 * An empty [url] draws the placeholder alone -- the dev store's profiles, previews, screenshots.
 */
@Composable
fun SearchPhoto(
    url: String,
    index: Int,
    height: Dp,
    radius: Dp,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .clickable(role = Role.Image, onClickLabel = "Open photo") { onClick() }
            .semantics { contentDescription = "Photo ${index + 1} of $name" },
    ) {
        PhotoBackdrop(url, index, Modifier.fillMaxSize())
        overlay()
    }
}

/**
 * What a photo slot shows under anything laid on it: the placeholder, the photo, and the kit's
 * washes. Its own composable because the hero's glass pills draw it a second time, blurred, behind
 * themselves ([HeroGlass]).
 */
@Composable
internal fun PhotoBackdrop(url: String, index: Int, modifier: Modifier) {
    val placeholder = PHOTO_PLACEHOLDERS[index % PHOTO_PLACEHOLDERS.size]
    Box(modifier.cssLinearGradient(155f, placeholder, PLACEHOLDER_STOPS[index % PLACEHOLDER_STOPS.size])) {
        if (url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // The painterly washes, softened for a loaded photo exactly as the kit softens them.
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.30f * 0.35f), Color.Transparent),
                        center = Offset(size.width * 0.35f, size.height * 0.28f),
                        radius = maxOf(size.width * 0.6f, size.height * 0.4f) * 0.65f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Black.copy(alpha = 0.18f * 0.40f), Color.Transparent),
                        center = Offset(size.width * 0.70f, size.height * 0.75f),
                        radius = maxOf(size.width, size.height) * 0.5f * 0.70f,
                    ),
                )
                val top = size.height * 0.60f
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)), startY = top, endY = size.height),
                    topLeft = Offset(0f, top),
                )
            },
        )
    }
}

// ── the hero's glass (callouts 5 and 7) ─────────────────────────────────────────────────────────

/**
 * The hero photo, as the glass pills see it: what to draw, and where the photo is laid out.
 *
 * CSS gives the two pills `backdrop-filter: blur()`, and Compose has no backdrop to filter. What is
 * behind them is known, though -- only photo 1 -- so each pill draws it again, at the photo's own size
 * and position, blurs that, and clips it to itself: the same pixels a backdrop filter would blur.
 */
@Stable
class HeroBackdrop(val content: @Composable (Modifier) -> Unit) {
    /** Where the photo is laid out; the pills measure themselves against it. */
    var photo: LayoutCoordinates? by mutableStateOf(null)
}

/** `blur(12px)` on the distance pill: sigma 12, as a RenderEffect radius -- (12 - 0.5) / 0.57735. */
private val DistancePillBlur = 19.92.dp

/** `blur(10px)` on the motivation pill: sigma 10, as a RenderEffect radius -- (10 - 0.5) / 0.57735. */
private val MotivationPillBlur = 16.45.dp

/**
 * The blurred photo behind one pill: [HeroBackdrop.content] at the photo's size, placed so it lines up
 * with the photo underneath, blurred whole and then cut to the pill by the caller's clip -- blurring
 * the full photo rather than the pill's patch keeps the edges sampling the real neighbouring pixels.
 * Below Android 12 Compose cannot blur, and the pill keeps its plain translucent fill.
 */
@Composable
internal fun HeroGlass(backdrop: HeroBackdrop, blur: Dp, modifier: Modifier = Modifier) {
    var at by remember { mutableStateOf<Offset?>(null) }
    Layout(
        content = { backdrop.content(Modifier.fillMaxSize().blur(blur, BlurredEdgeTreatment.Rectangle)) },
        modifier = modifier.onGloballyPositioned { me ->
            at = backdrop.photo?.takeIf { it.isAttached && me.isAttached }?.localPositionOf(me, Offset.Zero)
        },
    ) { measurables, constraints ->
        val photo = backdrop.photo?.takeIf { it.isAttached }
        val offset = at
        val placeable = if (photo != null && offset != null) {
            measurables.first().measure(Constraints.fixed(photo.size.width, photo.size.height))
        } else null
        // Its parent's size (`matchParentSize`) -- and nothing at all when asked for its intrinsic
        // size, where the constraints are unbounded: the glass only fills what is already there.
        layout(
            if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth,
            if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight,
        ) {
            if (placeable != null && offset != null) placeable.place(-offset.x.roundToInt(), -offset.y.roundToInt())
        }
    }
}

// ── the hero (callouts 4 to 9) ──────────────────────────────────────────────────────────────────

/**
 * Photo 1 at 460 with the distance pill, the name and the motivation pill on it, and the meet card
 * hanging 22 below its bottom edge -- the stack's one overlap. The card is offset rather than placed
 * in the column, so the hero still measures 460 and the 18 spacer after it keeps the next section
 * clear, exactly as the reference does.
 */
@Composable
fun SearchHero(profile: SearchProfile, zone: ZoneId, onPhoto: (Int) -> Unit, likedYou: Boolean = false) {
    // THE NAME SITS ABOVE THE CARD'S REAL TOP, not at a fixed `bottom: 80`. The reference's 80 is
    // "the card's top, less 44" for a card one line tall; at the largest type the card wraps and grows
    // upward (its bottom is pinned 22 below the photo), and a fixed 80 put the name under it. The
    // layout measures the card first and keeps the reference's 44 between them on every phone.
    val gap = 44.dp
    val url = profile.photos.firstOrNull().orEmpty()
    val backdrop = remember(url) { HeroBackdrop { m -> PhotoBackdrop(url, 0, m) } }
    Layout(
        contents = listOf(
            {
                SearchPhoto(
                    url = url, index = 0, height = 460.dp, radius = 28.dp,
                    name = profile.name, onClick = { onPhoto(0) },
                    modifier = Modifier.onGloballyPositioned { backdrop.photo = it },
                ) {
                    // `top: 16, left: 16, right: 16`, space-between, gap 8: the distance pill, and on a
                    // profile that liked the viewer the `Liked you` badge at the other end (Search 02).
                    HeroTopRow(
                        pill = {
                            DistancePill(
                                distanceParts(profile.distanceKm, profile.district),
                                distanceLine(profile.distanceKm, profile.district),
                                backdrop,
                            )
                        },
                        badge = if (likedYou) ({ LikedYouBadge() }) else null,
                        modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(16.dp),
                    )
                }
            },
            { HeroName(profile, backdrop) },
            { MeetCard(profile, zone) },
        ),
    ) { (photo, name, card), constraints ->
        val w = constraints.maxWidth
        val p = photo.first().measure(constraints.copy(minHeight = 0))
        val c = card.first().measure(Constraints(minWidth = w - 32.dp.roundToPx(), maxWidth = w - 32.dp.roundToPx()))
        val n = name.first().measure(Constraints(maxWidth = (w - 36.dp.roundToPx()).coerceAtLeast(0)))
        val cardY = p.height + 22.dp.roundToPx() - c.height
        layout(w, p.height) {
            p.place(0, 0)
            n.place(18.dp.roundToPx(), cardY - gap.roundToPx() - n.height)
            c.place(16.dp.roundToPx(), cardY)
        }
    }
}

/**
 * The hero's top row: the distance pill at the start and, on a profile that liked the viewer, the
 * `Liked you` badge at the end -- `justify-content: space-between; gap: 8`, centred on one line. Where
 * the pill at its natural width and the badge do not fit together (a long district, a large type,
 * a narrow phone), the badge drops under the pill, at the end, 8 below -- rather than squeezing the
 * district into a column one letter wide (the 186 fit sweep, 25 Oct 2026).
 */
@Composable
private fun HeroTopRow(
    pill: @Composable () -> Unit,
    badge: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(pill, badge ?: {}), modifier = modifier) { (p, b), constraints ->
        val w = constraints.maxWidth
        val gap = 8.dp.roundToPx()
        val badgePlaceable = b.firstOrNull()?.measure(Constraints(maxWidth = w))
        if (badgePlaceable == null) {
            val pp = p.first().measure(Constraints(maxWidth = w))
            return@Layout layout(w, pp.height) { pp.place(0, 0) }
        }
        val natural = p.first().maxIntrinsicWidth(constraints.maxHeight)
        if (natural + gap + badgePlaceable.width <= w) {
            val pp = p.first().measure(Constraints(maxWidth = w - gap - badgePlaceable.width))
            val h = maxOf(pp.height, badgePlaceable.height)
            layout(w, h) {
                pp.place(0, (h - pp.height) / 2)
                badgePlaceable.place(w - badgePlaceable.width, (h - badgePlaceable.height) / 2)
            }
        } else {
            val pp = p.first().measure(Constraints(maxWidth = w))
            layout(w, pp.height + gap + badgePlaceable.height) {
                pp.place(0, 0)
                badgePlaceable.place(w - badgePlaceable.width, pp.height + gap)
            }
        }
    }
}

/**
 * The name, white over the photo with its shadow, and the motivation pill 8 under it.
 *
 * The name is a CSS line box, 34 tall at `line-height: 1`: Compose kept Lora's natural box, some 6
 * deeper, so the name sat that much higher over the pill than the sheet draws it.
 */
@Composable
private fun HeroName(profile: SearchProfile, backdrop: HeroBackdrop) {
    val density = LocalDensity.current.density
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            nameLine(profile.name, profile.age),
            modifier = Modifier.semantics { heading() }.cssLineBox(34.sp),
            style = TextStyle(
                color = Color.White, fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 34.sp,
                lineHeight = 34.sp, letterSpacing = (-0.01).em,
                // `text-shadow: 0 2px 12px rgba(0,0,0,.35)`.
                shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f * density), cssBlurToShadowRadius(12f * density)),
            ),
        )
        motivationOf(profile.motivation)?.let { MotivationPill(it.title, it.icon, backdrop) }
    }
}

/** Callout 5: `rgba(255,255,255,.85) + blur 12` -- the blurred photo under an 85% white fill. */
@Composable
private fun DistancePill(parts: List<String>, label: String, backdrop: HeroBackdrop, modifier: Modifier = Modifier) {
    val pill = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .clip(pill)
            // One phrase for a screen reader, not three fragments and a lone dot.
            .clearAndSetSemantics { contentDescription = label },
    ) {
        HeroGlass(backdrop, DistancePillBlur, Modifier.matchParentSize())
        Row(
            Modifier.background(PhotoPillFill).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(BrandIcon.MapPin, 13.dp, tint = Fg, strokeWidth = 2.2f)
            parts.forEachIndexed { i, part ->
                val last = i == parts.lastIndex
                // The district takes what is left and wraps there at the largest type, rather than being
                // cut; the distance and the dot never break.
                Text(
                    part, color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    softWrap = last, modifier = if (last) Modifier.weight(1f, fill = false) else Modifier,
                )
            }
        }
    }
}

/** Callout 7: `rgba(255,255,255,.22) + blur 10`, a `.30` edge -- the glass the name sits over. */
@Composable
private fun MotivationPill(label: String, icon: BrandIcon, backdrop: HeroBackdrop) {
    val pill = RoundedCornerShape(percent = 50)
    Box(Modifier.clip(pill).border(1.dp, GlassEdge, pill)) {
        HeroGlass(backdrop, MotivationPillBlur, Modifier.matchParentSize())
        Row(
            Modifier.background(GlassFill).padding(horizontal = 11.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, 13.dp, tint = Color.White, strokeWidth = 2.2f)
            Text(label, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.02.sp)
        }
    }
}

/**
 * Callout 8: `You could meet at:` / `19:00 today`, and the Show-up count.
 *
 * As drawn, one row: tile, the two lines, the count. The lines and the count are a FLOW, so where
 * the three do not fit side by side the count moves under the lines instead of squeezing them;
 * nothing is cut and nothing overlaps.
 *
 * WHERE THAT HAPPENS, measured (the 184 evidence review, 24 Oct 2026): from 390 wide the drawn row
 * fits, with some 1.5 to spare; at 375 it is 14 too wide. The REFERENCE does not fit there either --
 * its lines are `nowrap` in a `flex: 1; min-width: 0` box beside a `flex: none` count, so a browser
 * runs `You could meet at:` some 9 under the pill; the sheet only ever drew 390. Here the count takes
 * the second row instead. What the ticket holds to one line at 375 x 667 is the meet line itself --
 * "the earliest overlap, today or tomorrow" -- and it is one line on every phone (SearchFitTest).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeetCard(profile: SearchProfile, zone: ZoneId, modifier: Modifier = Modifier) {
    Card(modifier = modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).background(MeetTileFill, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(BrandIcon.Clock, 18.dp, tint = Orange, strokeWidth = 2.2f) }
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = BetweenAtLeast12,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column {
                    Text(SearchCopy.MEET_LABEL, color = FgSubtle, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(
                        meetLine(profile.meetAt, profile.meetDay, zone), color = Fg, fontFamily = Manrope,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.testTag(MEET_LINE_TAG),
                    )
                }
                // Centred on the two lines beside it, as `align-items: center` draws it.
                ShowUpCount(
                    profile.attendedCount, Modifier.align(Alignment.CenterVertically).testTag(MEET_COUNT_TAG),
                    size = ShowUpCountSize.Xs,
                )
            }
        }
    }
}

/** The meet line, for the test that holds it to one line at 375 x 667. */
const val MEET_LINE_TAG = "search-meet-line"

/** The meet card's Show-up count, for the test that knows where the drawn row fits. */
const val MEET_COUNT_TAG = "search-meet-count"

/**
 * The reference's row, `flex: 1` lines then the count: SPACE-BETWEEN when both fit, with AT LEAST the
 * 12 gap counted when the flow decides whether they do. Neither built-in arrangement is both.
 */
private val BetweenAtLeast12 = object : Arrangement.Horizontal {
    override val spacing = 12.dp
    override fun androidx.compose.ui.unit.Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        outPositions: IntArray,
    ) = with(Arrangement.SpaceBetween) { arrange(totalSize, sizes, layoutDirection, outPositions) }
}

// ── the overlap (callouts 10, 11, 13, 20) ───────────────────────────────────────────────────────

/** A shared vibe or interest: the orange tone selected, with a ✓, announced as shared. */
@Composable
fun SharedChip(label: String) {
    Chip(
        label = label,
        selected = true,
        tone = ChipTone.Orange,
        modifier = Modifier.semantics { stateDescription = "shared" },
        leading = { ink -> Icon(BrandIcon.Check, 13.dp, tint = ink, strokeWidth = 2.6f) },
    )
}

// LETTER SPACING: the reference's NUMBERS are pixels, its STRINGS ems. React appends `px` to a bare
// number in an inline style, so `letterSpacing: 0.1` is 0.1px -- `.sp` here -- and only
// `'-0.01em'` is an em. Read as ems, every capitals title came out some 15% wider than the sheet's
// (the 184 evidence review, 24 Oct 2026).

/** A vibe or interest that is not shared: outlined, the orange tone at rest. */
@Composable
private fun PlainChip(label: String) = Chip(label = label, tone = ChipTone.Orange)

/** Callout 10: at most three shared vibes and `+N`. Not rendered when nothing is shared. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedVibesRow(name: String, row: SharedVibesRow) {
    if (row.shown.isEmpty()) return
    Card(
        fill = SolidColor(Elevated),
        border = BorderStroke(1.5.dp, Orange),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            SearchCopy.SHARED_VIBES_TITLE.format(name).uppercase(),
            color = Orange, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp,
            letterSpacing = 0.1.sp, modifier = Modifier.padding(bottom = 10.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            row.shown.forEach { id -> SharedChip(vibeLabel(id) ?: id) }
            if (row.more > 0) {
                Text(
                    "+${row.more}", color = Orange, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

/** Callout 13: every vibe today, the shared ones marked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VibeTodayCard(name: String, vibes: List<String>, mine: Set<String>) {
    val labelled = vibes.mapNotNull { id -> vibeLabel(id)?.let { id to it } }
    if (labelled.isEmpty()) return
    Card {
        CapsTitle(SearchCopy.VIBE_TODAY_TITLE.format(name))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labelled.forEach { (id, label) -> if (id in mine) SharedChip(label) else PlainChip(label) }
        }
    }
}

/** Callout 20: what they are into, `· k in common` only from one shared. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedInterestsBlock(name: String, interests: List<String>, mine: Set<String>) {
    val labelled = interests.mapNotNull { id -> interestLabel(id)?.let { id to it } }
    if (labelled.isEmpty()) return
    val shared = sharedInterests(labelled.map { it.first }, mine)
    Card {
        SectionTitle(SearchCopy.INTERESTS_TITLE.format(name), interestsSub(name, labelled.size, shared.size))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labelled.forEach { (id, label) -> if (id in shared) SharedChip(label) else PlainChip(label) }
        }
    }
}

// ── titles ──────────────────────────────────────────────────────────────────────────────────────

/** The reference's `SectionTitle`: Lora 700 / 18, its sub 12 / 500 four below, 14 under the block. */
@Composable
private fun SectionTitle(title: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 14.dp).semantics(mergeDescendants = true) { heading() }) {
        Text(title, color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = (-0.005).em)
        if (sub != null) {
            Text(
                sub, color = FgSubtle, fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp,
                letterSpacing = 0.01.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** The capitals titles of vibe today and charms: 13 / 700, `letter-spacing: .08em`, 10 under. */
@Composable
private fun CapsTitle(text: String) {
    Text(
        text.uppercase(), color = FgSubtle, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        letterSpacing = 0.08.sp, modifier = Modifier.padding(bottom = 10.dp).semantics { heading() },
    )
}

// ── the highlight (callout 12) ──────────────────────────────────────────────────────────────────

@Composable
fun HighlightCard(name: String, uplift: String) {
    Card(fill = BlushWash) {
        Row(
            Modifier.padding(bottom = 8.dp).semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(BrandIcon.Sparkles, 14.dp, tint = Orange, strokeWidth = 2.4f)
            Text(
                SearchCopy.HIGHLIGHT_TITLE.format(name).uppercase(), color = Orange, fontFamily = Manrope,
                fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 0.1.sp,
            )
        }
        // Straight quotes, as the reference writes them and the sheet draws them; `text-wrap: pretty`.
        val quoted = AnnotatedString("\"$uplift\"")
        val style = TextStyle(
            color = Fg, fontFamily = Lora, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium,
            fontSize = 18.sp, lineHeight = (18 * 1.35).sp,
        )
        Text(quoted, Modifier.prettyWrap(quoted, style).cssLineBox(style.lineHeight), style = style)
    }
}

// ── prompts (callouts 15) ───────────────────────────────────────────────────────────────────────

/** A written prompt: the topic in Lora italic violet, the answer under it. The lilac card, no shadow. */
@Composable
fun PromptCard(prompt: SearchPrompt) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LilacWash)
            .border(1.dp, PromptEdge, RoundedCornerShape(20.dp))
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
    ) {
        // The topic `text-wrap: balance`, the answer `pretty`, as the reference sets them.
        val topic = AnnotatedString(topicText(prompt.topicId))
        val topicStyle = TextStyle(
            color = Purple, fontFamily = Lora, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic,
            fontSize = 17.sp, lineHeight = (17 * 1.3).sp,
        )
        Text(
            topic,
            Modifier.semantics { heading() }.balanceWrap(topic, topicStyle).cssLineBox(topicStyle.lineHeight),
            style = topicStyle,
        )
        val answer = AnnotatedString(prompt.answer)
        val answerStyle = TextStyle(
            color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = (15 * 1.5).sp,
        )
        Text(
            answer,
            Modifier.padding(top = 10.dp).prettyWrap(answer, answerStyle).cssLineBox(answerStyle.lineHeight),
            style = answerStyle,
        )
    }
}

// ── voice and video (callouts 16, 18) ───────────────────────────────────────────────────────────

/**
 * The quoted prompt line under a voice or video card: Lora italic 14 / 1.45, 12 above, in the
 * reference's straight quotes, `text-wrap: pretty`.
 */
@Composable
private fun PromptQuote(promptId: String) {
    val text = MediaPrompts.ALL.firstOrNull { it.id == promptId }?.display ?: return
    val quoted = AnnotatedString("\"$text\"")
    val style = TextStyle(color = Neutral, fontFamily = Lora, fontStyle = FontStyle.Italic, fontSize = 14.sp, lineHeight = (14 * 1.45).sp)
    Text(quoted, Modifier.padding(top = 12.dp).prettyWrap(quoted, style).cssLineBox(style.lineHeight), style = style)
}

/**
 * Callout 16: the voice note. Plays on tap only. At rest the waveform is drawn as the reference draws
 * it, bars 0 to 17 violet ([voiceVioletBars]); while it plays, the violet follows the playhead. The
 * button is the 46 sunset disc with its violet glow, answering at 46 (over the 44 floor), labelled
 * for what it does.
 */
@Composable
fun VoiceCard(name: String, media: SearchMedia, playing: SearchPlayback?, onToggle: () -> Unit) {
    val violet = voiceVioletBars(playing != null, playing?.progress ?: 0f)
    Card {
        SectionTitle(SearchCopy.VOICE_TITLE.format(name), voiceSub(media.durationMs))
        Row(
            Modifier.fillMaxWidth().background(Raised, RoundedCornerShape(16.dp)).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .pillShadows(Brush.linearGradient(colorStops = SunsetStops.toTypedArray()), ShadowViolet)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = if (playing != null) "Pause" else "Play") { onToggle() }
                    .semantics { contentDescription = if (playing != null) "Pause $name's voice" else "Play $name's voice" },
                contentAlignment = Alignment.Center,
            ) {
                if (playing != null) {
                    Icon(BrandIcon.Pause, 18.dp, tint = Color.White, strokeWidth = 2f)
                } else {
                    // `margin-left: 2` in the kit: a triangle centred on its box looks left of centre.
                    Box(Modifier.offset(x = 1.dp)) { Icon(BrandIcon.Play, 18.dp, tint = Color.White) }
                }
            }
            Row(
                Modifier.weight(1f).height(36.dp).clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VOICE_BARS.forEachIndexed { i, v ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(v)
                            .heightIn(min = 4.dp)
                            .background(if (i < violet) Purple else BarRest, RoundedCornerShape(percent = 50)),
                    )
                }
            }
            Text(
                com.showup.profile.formatTakeLength(media.durationMs), color = FgSubtle, fontFamily = Manrope,
                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
        }
        PromptQuote(media.promptId)
    }
}

/**
 * Callout 18: the video. The poster is the profile's fourth photo, as the kit draws it; the frames
 * replace it while it plays. Plays with sound, on tap only.
 */
@Composable
fun VideoCard(
    name: String,
    media: SearchMedia,
    posterUrl: String,
    playing: SearchPlayback?,
    player: androidx.media3.common.Player?,
    onToggle: () -> Unit,
) {
    Card {
        SectionTitle(SearchCopy.VIDEO_TITLE.format(name), videoSub(media.durationMs))
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .cssLinearGradient(155f, PHOTO_PLACEHOLDERS[3], PLACEHOLDER_STOPS[3])
                .clickable(role = Role.Button, onClickLabel = if (playing != null) "Pause" else "Play") { onToggle() }
                .semantics { contentDescription = if (playing != null) "Pause $name's video" else "Play $name's video" },
            contentAlignment = Alignment.Center,
        ) {
            if (posterUrl.isNotEmpty()) {
                AsyncImage(model = posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (playing != null && player != null) {
                com.showup.profile.VideoSurface(player, Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier.fillMaxSize().drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                                center = Offset(size.width * 0.40f, size.height * 0.35f),
                                radius = size.maxDimension * 0.6f,
                            ),
                        )
                    },
                )
                Box(
                    Modifier
                        .size(58.dp)
                        // `rgba(255,255,255,.92)`, lifted `0 8px 24px rgba(0,0,0,.16)` -- the shadow was
                        // missing until the iOS port read the reference again (24 Oct 2026).
                        .pillShadows(SolidColor(VideoDiscFill), VideoDiscLift),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.offset(x = 1.5.dp)) { Icon(BrandIcon.Play, 22.dp, tint = Fg) } }
            }
        }
        PromptQuote(media.promptId)
    }
}

// ── Essentials, How I live, charms (callouts 17, 19, 21) ────────────────────────────────────────

@Composable
fun EssentialsCard(name: String, rows: List<Essential>) {
    if (rows.isEmpty()) return
    Card {
        SectionTitle(SearchCopy.ESSENTIALS_TITLE.format(name))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { r ->
                Row(
                    Modifier.semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                        Icon(r.row.icon, 16.dp, tint = Purple, strokeWidth = if (r.row.isKit) 2f else 1.8f)
                    }
                    Text(
                        r.row.label.uppercase(), color = FgSubtle, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, letterSpacing = 0.06.sp, modifier = Modifier.widthIn(min = 76.dp),
                    )
                    Text(
                        r.value, color = Fg, fontFamily = Manrope, fontSize = 14.sp, lineHeight = (14 * 1.35).sp,
                        modifier = Modifier.weight(1f).cssLineBox((14 * 1.35).sp),
                    )
                }
            }
        }
    }
}

@Composable
fun HowILiveCard(name: String, tiles: List<LifestyleTile>) {
    if (tiles.isEmpty()) return
    Card {
        SectionTitle(SearchCopy.EVERYDAY_TITLE.format(name), SearchCopy.EVERYDAY_SUB)
        // `grid-template-columns: 1fr 1fr; gap: 10`, row by row so the two tiles of a row share its
        // height -- a tall answer must not leave its neighbour short.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { t -> LifestyleTileView(t, Modifier.weight(1f).fillMaxHeight()) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LifestyleTileView(t: LifestyleTile, modifier: Modifier) {
    Row(
        modifier
            .background(Raised, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = lifestyleIcon(t.field)
        Box(
            Modifier.size(30.dp).background(Elevated, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // The kit's own marks at 15 / stroke 2; Search's section marks at 16 / 1.8 (`SearchIcon`).
            val kit = icon == BrandIcon.Zap || icon == BrandIcon.Clock || icon == BrandIcon.Users
            Icon(icon, if (kit) 15.dp else 16.dp, tint = Purple, strokeWidth = if (kit) 2f else 1.8f)
        }
        Column {
            Text(
                t.field.label.uppercase(), color = FgSubtle, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp, letterSpacing = 0.06.sp,
            )
            Text(
                t.value, color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
                lineHeight = (13.5 * 1.25).sp, modifier = Modifier.padding(top = 1.dp).cssLineBox((13.5 * 1.25).sp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharmsCard(charms: List<Charm>) {
    if (charms.isEmpty()) return
    Card {
        CapsTitle(SearchCopy.CHARMS_TITLE)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            charms.forEach { c ->
                Row(
                    Modifier.background(Raised, RoundedCornerShape(percent = 50)).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (c.star) BrandIcon.Star else BrandIcon.Gift, 13.dp, tint = Fg, strokeWidth = 2f)
                    Text(c.label, color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
    }
}

/** The kit's icons (`Icon`) draw at stroke 2 here; Search's own section marks (`SearchIcon`) at 1.8. */
private val EssentialRow.isKit: Boolean get() = this == EssentialRow.Height || this == EssentialRow.Gender || this == EssentialRow.Orientation
