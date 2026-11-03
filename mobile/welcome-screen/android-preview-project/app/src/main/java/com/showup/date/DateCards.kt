/*
 * DateCards.kt
 * ShowUp · the Date screen's cards (SHOWUP-193 ②-⑭), from `screen-date-reference.jsx`
 *
 * Every number is the reference's. Raw dp below are the kit's own where no token holds the value
 * (14, 13, 11, 46…), as the Search cards do; the tokens are used wherever one exists.
 *
 * Built once and named after the reference, because later 07a tickets extend them in place:
 * [CoordinateModule] gains the arrival variant in Date 04, the time + place card gains the change
 * tags and binding note in Date 05. Extended, never forked.
 */
package com.showup.date

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showup.designsystem.BlushStops
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Card
import com.showup.designsystem.Chip
import com.showup.designsystem.ChipSize
import com.showup.designsystem.ChipTone
import com.showup.designsystem.Danger
import com.showup.designsystem.DangerFg
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.Lavender100
import com.showup.designsystem.LilacCard
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Muted
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.Raised
import com.showup.designsystem.ReasonDarkRed
import com.showup.designsystem.ShadowCta
import com.showup.designsystem.ShadowViolet
import com.showup.designsystem.ShowUpCount
import com.showup.designsystem.ShowUpCountSize
import com.showup.designsystem.ShowUpEasing
import com.showup.designsystem.Success
import com.showup.designsystem.SuccessFg
import com.showup.designsystem.cssBlurToShadowRadius
import com.showup.designsystem.pillShadows
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.prettyWrap
import com.showup.designsystem.rememberMotion
import com.showup.designsystem.fitWords
import com.showup.designsystem.OverflowTapTarget
import com.showup.search.PhotoBackdrop
import com.showup.search.motivationOf
import com.showup.search.vibeLabel
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import java.time.Instant
import java.time.ZoneId

// ── tones ───────────────────────────────────────────────────────────────────

/** The reference's `REASON_TONES`: ink, panel fill, chip border. */
internal data class ToneColors(val fg: Color, val bg: Color, val border: Color)

internal fun toneColors(tone: ReasonTone): ToneColors = when (tone) {
    ReasonTone.Violet -> ToneColors(Purple, Purple.copy(alpha = 0.10f), Purple.copy(alpha = 0.20f))
    ReasonTone.Orange -> ToneColors(Orange, Orange.copy(alpha = 0.11f), Orange.copy(alpha = 0.22f))
    ReasonTone.Danger -> ToneColors(DangerFg, Danger.copy(alpha = 0.10f), Danger.copy(alpha = 0.22f))
    ReasonTone.DarkRed -> ToneColors(ReasonDarkRed, ReasonDarkRed.copy(alpha = 0.09f), ReasonDarkRed.copy(alpha = 0.28f))
}

private val SerifBold = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Bold, color = Fg)

/** Press feedback: 0.98 on the brand curve, as every pressable here; nothing under reduced motion. */
@Composable
internal fun Modifier.press(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val motion = rememberMotion()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && motion.enabled) 0.98f else 1f,
        animationSpec = tween(if (motion.enabled) Motion.FAST else 0, easing = ShowUpEasing),
        label = "press",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** A tappable surface with the press scale and a 48 dp floor. */
@Composable
internal fun Modifier.pressable(role: Role, label: String? = null, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .press(interaction)
        .clickable(interactionSource = interaction, indication = null, role = role, onClickLabel = label, onClick = onClick)
}

/**
 * A button DRAWN at the reference's size that ANSWERS at the 44 floor: the touch area is laid over
 * the drawing ([OverflowTapTarget]) instead of growing the row it sits in. `minTapTarget` on these
 * pushed the time card 18 taller than the sheet (the Copy pill) and the Check-in bar 10 (End date).
 *
 * The touch area is a sibling of the drawing, so it carries the name: the drawing's own text is
 * cleared and TalkBack reads one button, once. [content] gets the press scale for its drawing.
 */
@Composable
internal fun DrawnSmall(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    touch: Modifier = Modifier,
    content: @Composable (press: Modifier) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    OverflowTapTarget(
        touch = touch
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { text = AnnotatedString(label) },
        modifier = modifier,
    ) {
        Box(Modifier.clearAndSetSemantics {}) { content(Modifier.press(interaction)) }
    }
}

/** A square icon tile: the reference's `width/height N, borderRadius r, background, centred icon`. */
@Composable
internal fun IconTile(size: Dp, radius: Dp, fill: Brush, icon: BrandIcon, iconSize: Dp, tint: Color, stroke: Float) {
    Box(
        Modifier.size(size).background(fill, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, iconSize, tint = tint, strokeWidth = stroke) }
}

// ── ② hero ──────────────────────────────────────────────────────────────────

/**
 * The match's first photo, h 196, radius 24, `object-position: center 24%`, with `It's a date.` --
 * Lora 700 italic 30, −0.02em, white, centred, 14 from the top, `text-shadow: 0 2 12 rgba(0,0,0,.18)`.
 * The placeholder until the photo loads is the kit's first (`#FCC8A7 → #B86B5C`, 155deg).
 */
@Composable
internal fun DateHero(photoUrl: String?, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Box(
        modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        // 24% down: the Compose bias for a CSS object-position percentage p is 2p − 1.
        PhotoBackdrop(photoUrl.orEmpty(), 0, Modifier.fillMaxSize(), alignment = BiasAlignment(0f, -0.52f))
        Text(
            DateCopy.HERO,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).semantics { heading() },
            textAlign = TextAlign.Center,
            style = SerifBold.copy(
                color = Color.White,
                fontStyle = FontStyle.Italic,
                fontSize = 30.sp,
                letterSpacing = (-0.02 * 30).sp,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.18f),
                    offset = androidx.compose.ui.geometry.Offset(0f, with(density) { 2.dp.toPx() }),
                    blurRadius = cssBlurToShadowRadius(with(density) { 12.dp.toPx() }),
                ),
            ),
        )
    }
}

// ── ③ time + place ──────────────────────────────────────────────────────────

/**
 * A label and a value on one baseline, gap 8, the value dropping to its own line when the two do not
 * fit -- the reference's `display: flex; align-items: baseline; flex-wrap: wrap; gap: 8`. A long place
 * name wraps rather than truncating.
 */
@Composable
private fun BaselineWrap(label: @Composable () -> Unit, value: @Composable () -> Unit) {
    Layout(contents = listOf(label, value)) { (l, v), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val labelP = l.first().measure(loose)
        // The value at most a full line wide: a name longer than that wraps inside it, as CSS's does.
        val valueP = v.first().measure(loose)
        val gap = 8.dp.roundToPx()
        if (labelP.width + gap + valueP.width <= constraints.maxWidth) {
            val base = maxOf(labelP[FirstBaseline], valueP[FirstBaseline])
            val labelTop = base - labelP[FirstBaseline]
            val valueTop = base - valueP[FirstBaseline]
            layout(constraints.maxWidth, maxOf(labelTop + labelP.height, valueTop + valueP.height)) {
                labelP.place(0, labelTop)
                valueP.place(labelP.width + gap, valueTop)
            }
        } else {
            // `flex-wrap` with `gap: 8`, which is also the row gap: the value on its own line.
            layout(constraints.maxWidth, labelP.height + gap + valueP.height) {
                labelP.place(0, 0)
                valueP.place(0, labelP.height + gap)
            }
        }
    }
}

@Composable
private fun MeetLabel(text: String) = Text(
    text,
    color = Purple,
    fontFamily = Manrope,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    letterSpacing = 0.02.sp,
)

@Composable
private fun MeetValue(text: String) = Text(text, style = SerifBold.copy(fontSize = 20.sp))

/** ③ The time + place card: `#E8DCF5`, padding 13 / 14, the latest AGREED time and place. */
@Composable
internal fun TimePlaceCard(
    date: ActiveDate,
    zone: ZoneId,
    copied: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier,
        fill = SolidColor(LilacCard),
        // 13 / 14 inside the card's 1 px border.
        contentPadding = PaddingValues(horizontal = 15.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 13.dp)) {
            IconTile(26.dp, 9.dp, SolidColor(Purple), BrandIcon.HeartFilled, 14.dp, Color.White, 2f)
            Spacer(Modifier.width(9.dp))
            val (lead, day) = meetingHeadline(date.match.name, date.meetDay)
            Text(
                buildAnnotatedString {
                    append(lead)
                    withStyle(SpanStyle(color = Purple, fontStyle = FontStyle.Italic)) { append(day) }
                },
                modifier = Modifier.cssLineBox((20 * 1.15).sp),
                style = SerifBold.copy(fontSize = 20.sp, lineHeight = (20 * 1.15).sp),
            )
        }
        Row(verticalAlignment = Alignment.Top) {
            IconTile(40.dp, 13.dp, SolidColor(Elevated), BrandIcon.Clock, 19.dp, Purple, 2.2f)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                BaselineWrap({ MeetLabel(DateCopy.MEETING_TIME) }, { MeetValue(com.showup.search.meetTime(date.meetAt, zone)) })
            }
        }
        date.place?.let { place ->
            Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Fg.copy(alpha = 0.10f)))
            Row(verticalAlignment = Alignment.Top) {
                IconTile(40.dp, 13.dp, SolidColor(Elevated), BrandIcon.MapPin, 19.dp, Purple, 2.2f)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    BaselineWrap({ MeetLabel(DateCopy.MEETING_PLACE) }, { MeetValue(place.name) })
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                        place.address?.let {
                            Text(
                                it,
                                modifier = Modifier.weight(1f, fill = false).cssLineBox((12.5 * 1.35).sp),
                                color = Muted,
                                fontFamily = Manrope,
                                fontSize = 12.5.sp,
                                lineHeight = (12.5 * 1.35).sp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        CopyAddress(copied, onCopy)
                    }
                }
            }
        }
    }
}

/**
 * ④ The Copy pill: padding 4 10 4 8, white, 1 px border-soft, violet 11.5 / 700, copy icon 13.
 * `Copied` + check in the success tint while [copied]; the colours cross over 160 ms. No toast.
 */
@Composable
internal fun CopyAddress(copied: Boolean, onCopy: () -> Unit) {
    val spec = tween<Color>(160, easing = ShowUpEasing)
    val fill by animateColorAsState(if (copied) Success.copy(alpha = 0.14f) else Elevated, spec, label = "copy-fill")
    val stroke by animateColorAsState(if (copied) Success.copy(alpha = 0.32f) else BorderSoft, spec, label = "copy-stroke")
    val ink by animateColorAsState(if (copied) SuccessFg else Purple, spec, label = "copy-ink")
    DrawnSmall(if (copied) DateCopy.COPIED else DateCopy.COPY, onCopy) { press ->
        Row(
            press
                .background(fill, RoundedCornerShape(50))
                .border(1.dp, stroke, RoundedCornerShape(50))
                // 4 10 4 8 inside the 1 px border, which CSS counts in the pill's size.
                .padding(start = 9.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (copied) BrandIcon.Check else BrandIcon.Copy, 13.dp, tint = ink, strokeWidth = 2.4f)
            Spacer(Modifier.width(5.dp))
            Text(
                if (copied) DateCopy.COPIED else DateCopy.COPY,
                color = ink,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
            )
        }
    }
}

// ── ⑥ ⑦ ⑧ Coordinate ────────────────────────────────────────────────────────

/** A 48-tall pill CTA with a message icon -- `Open chat` and `Read & reply`. */
@Composable
private fun CoordinateCta(label: String, fill: Color, shadows: List<com.showup.designsystem.CssShadow>, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pressable(Role.Button, onClick = onClick)
            .pillShadows(SolidColor(fill), shadows),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(BrandIcon.MessageCircle, 18.dp, tint = Color.White, strokeWidth = 2.2f)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun CoordinateTitle(name: String) = Text(
    DateCopy.COORD_TITLE.format(name),
    modifier = Modifier.cssLineBox((20 * 1.1).sp),
    style = SerifBold.copy(fontSize = 20.sp, lineHeight = (20 * 1.1).sp),
)

@Composable
private fun CoordinateSub(text: String) {
    // `textWrap: 'pretty'`: the sheet's A reads `… before` / `you meet`, not `meet` alone.
    val style = TextStyle(color = Muted, fontFamily = Manrope, fontSize = 14.sp, lineHeight = (14 * 1.35).sp)
    Text(text, Modifier.prettyWrap(AnnotatedString(text), style).cssLineBox(style.lineHeight), style = style)
}

/**
 * ⑥ locked · ⑦ available · ⑧ unread: ONE card in three states, never a second one. The state is the
 * screen's ([DateRules.dateState]); this draws it.
 */
@Composable
internal fun CoordinateModule(
    state: DateState,
    date: ActiveDate,
    now: Instant,
    zone: ZoneId,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unread = date.unread
    if (state == DateState.Unread && unread != null) {
        UnreadCoordinate(date.match, unread, now, zone, onOpenChat, modifier)
        return
    }
    // 18 inside the 1.5 border.
    Card(modifier, border = BorderStroke(1.5.dp, Lavender100), contentPadding = PaddingValues(19.5.dp)) {
        if (state == DateState.Locked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(48.dp, 14.dp, SolidColor(Purple.copy(alpha = 0.10f)), BrandIcon.MessageCircle, 22.dp, Purple, 2.1f)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    CoordinateTitle(date.match.name)
                    Spacer(Modifier.height(4.dp))
                    CoordinateSub(DateCopy.COORD_LOCKED_SUB)
                }
            }
            // No progress bar (2 Nov 2026): the time alone says it. Not tappable.
            Text(
                lockedLine(date.unlockAt, now, zone),
                modifier = Modifier.padding(top = 14.dp),
                color = Purple,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        } else {
            Row(verticalAlignment = Alignment.Top) {
                IconTile(48.dp, 14.dp, SolidColor(Purple), BrandIcon.MessageCircle, 22.dp, Color.White, 2.1f)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(Success, CircleShape))
                        Spacer(Modifier.width(9.dp))
                        CoordinateTitle(date.match.name)
                    }
                    Spacer(Modifier.height(5.dp))
                    CoordinateSub(DateCopy.COORD_OPEN_SUB.format(date.match.name))
                }
            }
            Spacer(Modifier.height(16.dp))
            CoordinateCta(DateCopy.OPEN_CHAT, Purple, ShadowViolet, onOpenChat)
        }
    }
}

/**
 * ⑧ A message from the match is waiting: the card takes the reason's tone -- a 1.5 border and a 4 top
 * rail -- and says who, why and a two-line preview, with the reply in the same tone.
 */
@Composable
private fun UnreadCoordinate(
    match: DateMatch,
    unread: DateUnread,
    now: Instant,
    zone: ZoneId,
    onOpenChat: () -> Unit,
    modifier: Modifier,
) {
    val style = chatReasonStyle(unread.reason)
    val tone = toneColors(style.tone)
    // The rail and the body sit inside the 1.5 border, as CSS's `overflow: hidden` card has them.
    Card(modifier, border = BorderStroke(1.5.dp, tone.border), contentPadding = PaddingValues(1.5.dp)) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(topStart = 18.5.dp, topEnd = 18.5.dp)).background(tone.fg))
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MatchAvatar(match, unread.count, tone.fg)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    InlineOrStack(gap = 8.dp) {
                        Text(
                            match.name,
                            Modifier.cssLineBox((19 * 1.1).sp),
                            style = SerifBold.copy(fontSize = 19.sp, lineHeight = (19 * 1.1).sp),
                        )
                        Row(
                            Modifier
                                .background(tone.bg, RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(5.dp).background(tone.fg, CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                unreadTag(unread.count).uppercase(),
                                color = tone.fg,
                                fontFamily = Manrope,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.05.sp,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        DateCopy.NEEDS_RESPONSE.format(unreadTime(unread.sentAt, now, zone)),
                        modifier = Modifier.padding(top = 2.dp),
                        color = Muted,
                        fontFamily = Manrope,
                        fontSize = 12.5.sp,
                    )
                }
            }
            Column(
                Modifier
                    .padding(top = 13.dp)
                    .fillMaxWidth()
                    .background(tone.bg, RoundedCornerShape(14.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                ReasonBadge(style, onLight = true)
                unread.body?.let {
                    // `textWrap: 'pretty'` before the 2-line clamp: the sheet's `… sorry!` / `Almost there.”`.
                    val quoted = "“$it”"
                    val style = TextStyle(
                        color = Fg,
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = (14 * 1.4).sp,
                    )
                    Text(
                        quoted,
                        Modifier.prettyWrap(AnnotatedString(quoted), style).cssLineBox(style.lineHeight),
                        style = style,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            val shadows = when (style.tone) {
                ReasonTone.Orange -> ShadowCta
                ReasonTone.Danger -> emptyList()
                else -> ShadowViolet
            }
            CoordinateCta(DateCopy.READ_AND_REPLY, tone.fg, shadows, onOpenChat)
        }
    }
}

/** The reference's `ReasonBadge`: uppercase reason label with its icon, in the tone's tint. */
@Composable
internal fun ReasonBadge(style: ChatReasonStyle, onLight: Boolean) {
    val tone = toneColors(style.tone)
    Row(
        Modifier
            .background(if (onLight) tone.bg else Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
            .padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(style.icon, 11.dp, tint = if (onLight) tone.fg else Color.White, strokeWidth = 2.5f)
        Spacer(Modifier.width(5.dp))
        Text(
            style.label.uppercase(),
            color = if (onLight) tone.fg else Color.White,
            fontFamily = Manrope,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.5.sp,
            letterSpacing = 0.05.sp,
            maxLines = 1,
        )
    }
}

/**
 * The kit's `Avatar` at 46 with the unread count badge (18, the tone's fill, a 2.5 white ring) and
 * the pulsing ring behind it (`su-pulse-ring`: 1 → 2.6 scale, .55 → 0 by 70%, 1.8 s, the brand curve).
 * Under reduced motion the ring stays still and hidden.
 */
@Composable
private fun MatchAvatar(match: DateMatch, count: Int, tone: Color) {
    val motion = rememberMotion()
    Box(Modifier.size(46.dp)) {
        Box(Modifier.size(46.dp).aboveWash("avatar", CircleShape).clip(CircleShape)) {
            PhotoBackdrop(match.photoUrl.orEmpty(), 0, Modifier.fillMaxSize())
            if (match.photoUrl == null) {
                Text(
                    match.name.take(1),
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.92f),
                    fontFamily = Lora,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (46 * 0.42).sp,
                )
            }
        }
        if (motion.enabled) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val spec = InfiniteRepeatableSpec<Float>(
                keyframes {
                    durationMillis = Motion.PULSE_RING
                    1f at 0 using ShowUpEasing
                    2.6f at (Motion.PULSE_RING * 0.7).toInt()
                },
                RepeatMode.Restart,
            )
            val alphaSpec = InfiniteRepeatableSpec<Float>(
                keyframes {
                    durationMillis = Motion.PULSE_RING
                    0.55f at 0 using ShowUpEasing
                    0f at (Motion.PULSE_RING * 0.7).toInt()
                },
                RepeatMode.Restart,
            )
            val scale by transition.animateFloat(1f, 2.6f, spec, label = "pulse-scale")
            val alpha by transition.animateFloat(0.55f, 0f, alphaSpec, label = "pulse-alpha")
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .size(18.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                    .background(tone, CircleShape),
            )
        }
        val digit = with(LocalDensity.current) { 10.5.dp.toSp() }
        val line = with(LocalDensity.current) { 18.dp.toSp() }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 3.dp, y = (-3).dp)
                .size(23.dp)
                .aboveWash("avatar-badge", CircleShape)
                .background(Color.White, CircleShape)
                .padding(2.5.dp)
                .background(tone, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // A fixed-size mark, as the TabBar's badge: the count is spoken in the card's text.
            Text(
                count.toString(),
                color = Color.White,
                fontFamily = Manrope,
                fontWeight = FontWeight.ExtraBold,
                fontSize = digit,
                style = TextStyle(lineHeight = line),
                maxLines = 1,
            )
        }
    }
}

/**
 * Two children side by side, [gap] apart and centred on each other, where both fit; the second under
 * the first, [gap] / 2 below, where they do not -- the yield at 200 % type on a narrow phone, so the
 * second is never cut.
 */
@Composable
private fun InlineOrStack(gap: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val (first, second) = measurables.map { it.measure(loose) }
        val g = gap.roundToPx()
        if (first.width + g + second.width <= constraints.maxWidth) {
            val h = maxOf(first.height, second.height)
            layout(first.width + g + second.width, h) {
                first.place(0, (h - first.height) / 2)
                second.place(first.width + g, (h - second.height) / 2)
            }
        } else {
            layout(maxOf(first.width, second.width), first.height + g / 2 + second.height) {
                first.place(0, 0)
                second.place(0, first.height + g / 2)
            }
        }
    }
}

// ── ⑨ Report a problem ──────────────────────────────────────────────────────

/** ⑨ Always present while the date is active. Danger 6 % fill, 1 px danger 20 %, shield tile 40. */
@Composable
internal fun ReportRow(onReport: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .pressable(Role.Button, onClick = onReport)
            .background(Danger.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .border(1.dp, Danger.copy(alpha = 0.20f), RoundedCornerShape(16.dp))
            // 13 / 15 inside the 1 px border.
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(40.dp, 12.dp, SolidColor(Elevated), BrandIcon.Shield, 19.dp, Danger, 2.1f)
        Spacer(Modifier.width(13.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Fg, fontWeight = FontWeight.Bold)) { append(DateCopy.REPORT_TITLE) }
                append(" ")
                append(DateCopy.REPORT_BODY)
            },
            modifier = Modifier.weight(1f).cssLineBox((13.5 * 1.4).sp),
            color = Muted,
            fontFamily = Manrope,
            fontSize = 13.5.sp,
            lineHeight = (13.5 * 1.4).sp,
        )
        Spacer(Modifier.width(13.dp))
        Icon(BrandIcon.ChevronRight, 18.dp, tint = FgSubtle, strokeWidth = 2.2f)
    }
}

// ── ⑩ intent + Show-up count ────────────────────────────────────────────────

@Composable
private fun Eyebrow(text: String, size: Float = 11f) = Text(
    text.uppercase(),
    color = FgSubtle,
    fontFamily = Manrope,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    letterSpacing = 0.08.sp,
)

/** A shared value as a selected chip with the shared check -- Search's shared mark. */
@Composable
private fun SharedValueChip(label: String, icon: BrandIcon?, tone: ChipTone, size: ChipSize, iconSize: Dp, stroke: Float) = Chip(
    label = label,
    selected = true,
    tone = tone,
    size = size,
    leading = icon?.let { { ink -> Icon(it, iconSize, tint = ink, strokeWidth = stroke) } },
)

/**
 * ⑩ Only what matches: the intent line when both motivations are equal, otherwise the row keeps the
 * count under `{name}'s Show-up`; `You both want` with the shared vibes only, absent when none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IntentCard(date: ActiveDate, modifier: Modifier = Modifier) {
    val shared = intentShared(date.motivationYou, date.motivationThem)
    // 11 / 13 inside the 1 px border.
    Card(modifier, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (shared) {
                    Icon(BrandIcon.HeartFilled, 20.dp, tint = Orange)
                } else {
                    Icon(BrandIcon.ShieldCheck, 20.dp, tint = Purple)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Eyebrow(if (shared) DateCopy.MUTUAL_INTENT else DateCopy.THEIR_SHOW_UP.format(date.match.name))
                    if (shared) {
                        Text(
                            motivationOf(date.motivationThem)?.title ?: date.motivationThem.orEmpty(),
                            color = Fg,
                            fontFamily = Manrope,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                ShowUpCount(date.match.attendedCount, size = ShowUpCountSize.Sm)
            }
            if (date.sharedVibes.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSoft))
                Column {
                    Eyebrow(DateCopy.YOU_BOTH_WANT)
                    Spacer(Modifier.height(5.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        date.sharedVibes.forEach { id ->
                            SharedValueChip(vibeLabel(id) ?: id, vibeIcon(id), ChipTone.Orange, ChipSize.Sm, 11.dp, 2.4f)
                        }
                    }
                }
            }
        }
    }
}

// ── ⑪ quick actions ─────────────────────────────────────────────────────────

/**
 * ⑪ Three tiles, gap 8 (Order Ride hidden until a ride-app API exists). Directions needs a place,
 * so with none there are two.
 */
@Composable
internal fun QuickActions(
    hasPlace: Boolean,
    onDirections: () -> Unit,
    onShare: () -> Unit,
    onCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (hasPlace) QuickTile(BrandIcon.Navigation, DateCopy.DIRECTIONS, onDirections)
        QuickTile(BrandIcon.Share, DateCopy.SHARE_DATE, onShare)
        QuickTile(BrandIcon.Calendar, DateCopy.CALENDAR, onCalendar)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.QuickTile(icon: BrandIcon, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .pressable(Role.Button, onClick = onClick)
            .background(Elevated, RoundedCornerShape(16.dp))
            .border(1.dp, BorderSoft, RoundedCornerShape(16.dp))
            // 14 / 0 inside the 1 px border.
            .padding(horizontal = 1.dp, vertical = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, 18.dp, tint = Purple, strokeWidth = 2f)
        // Wraps between words (`Share / Date`) at a large type on a narrow phone, and a single word
        // wider than the tile (`Directions` at 200 %) shrinks just enough -- never cut, as `ShowUpCount`.
        val style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Fg)
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val longestWord = remember(label, density) {
            label.split(' ').maxOf { measurer.measure(it, style, softWrap = false).size.width }
        }
        Text(
            label,
            modifier = Modifier.fitWords(longestWord),
            style = style,
            textAlign = TextAlign.Center,
        )
    }
}

// ── ⑫ About ─────────────────────────────────────────────────────────────────

/** ⑫ The mini profile: photo 92 r16, `{name}, {age}`, area · distance, `ShowUpCount` xs, and the link. */
@Composable
internal fun AboutCard(match: DateMatch, onSeeProfile: () -> Unit, modifier: Modifier = Modifier) {
    // Padding 0, its contents inside the 1 px border.
    Card(modifier, contentPadding = PaddingValues(1.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(92.dp).clip(RoundedCornerShape(16.dp))) {
                PhotoBackdrop(match.photoUrl.orEmpty(), 0, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    match.age?.let { DateCopy.NAME_AGE.format(match.name, it) } ?: match.name,
                    style = SerifBold.copy(fontSize = 20.sp),
                )
                aboutLine(match.district, match.distanceKm)?.let {
                    Text(it, color = FgSubtle, fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
                ShowUpCount(match.attendedCount, size = ShowUpCountSize.Xs)
            }
        }
        // Drawn 43 tall, as the reference's 12 / 12 around a 13 line under its 1 px `border-top`, which
        // CSS counts in the height and the drawn rule here does not -- hence 13 above.
        DrawnSmall(DateCopy.SEE_PROFILE.format(match.name), onSeeProfile, touch = Modifier.fillMaxWidth()) { press ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 19.dp, bottomEnd = 19.dp))
                    .then(press)
                    .background(Raised)
                    .drawTopRule()
                    .padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    DateCopy.SEE_PROFILE.format(match.name),
                    color = Purple,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.width(6.dp))
                Icon(BrandIcon.ChevronRight, 14.dp, tint = Purple, strokeWidth = 2.4f)
            }
        }
    }
}

private fun Modifier.drawTopRule(): Modifier = drawBehind {
    drawRect(BorderSoft, size = Size(size.width, 1.dp.toPx()))
}

// ── ⑬ in common ─────────────────────────────────────────────────────────────

/**
 * ⑬ Only what both filled in: the motivation when it is shared, then the shared interests. No
 * overlap, no card (Search's rule).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommonCard(date: ActiveDate, modifier: Modifier = Modifier) {
    val chips = commonChips(date)
    if (chips.isEmpty()) return
    // 16 inside the 1 px border.
    Card(modifier, contentPadding = PaddingValues(17.dp)) {
        Eyebrow(DateCopy.IN_COMMON, size = 13f)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { (label, icon) -> SharedValueChip(label, icon, ChipTone.Lavender, ChipSize.Md, 13.dp, 2.2f) }
        }
    }
}

// ── ⑭ How the date works ────────────────────────────────────────────────────

/** ⑭ Rewritten 2 Nov 2026 to what the app does: no timer, no "halfway". Four rows, gap 14. */
@Composable
internal fun DateWorksCard(date: ActiveDate, zone: ZoneId, modifier: Modifier = Modifier) {
    val rows = listOf(
        Triple(BrandIcon.MapPin, worksMeetLine(date, zone), DateCopy.WORKS_MEET_SUB),
        Triple(BrandIcon.Navigation, DateCopy.WORKS_CHECK_IN, DateCopy.WORKS_CHECK_IN_SUB.format(date.match.name)),
        Triple(BrandIcon.Clock, DateCopy.WORKS_THIRTY, DateCopy.WORKS_THIRTY_SUB),
        Triple(BrandIcon.Check, DateCopy.WORKS_END, DateCopy.WORKS_END_SUB),
    )
    // 16 inside the 1 px border.
    Card(modifier, contentPadding = PaddingValues(17.dp)) {
        Text(DateCopy.WORKS_TITLE, style = SerifBold.copy(fontSize = 18.sp), modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { (icon, title, sub) ->
                Row(verticalAlignment = Alignment.Top) {
                    IconTile(
                        32.dp, 10.dp,
                        Brush.verticalGradient(colorStops = BlushStops.toTypedArray()),
                        icon, 16.dp, Orange, 2.2f,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            sub,
                            modifier = Modifier.padding(top = 2.dp).cssLineBox((13 * 1.4).sp),
                            color = Muted,
                            fontFamily = Manrope,
                            fontSize = 13.sp,
                            lineHeight = (13 * 1.4).sp,
                        )
                    }
                }
            }
        }
    }
}

// ── ⑱ Cancel this date ──────────────────────────────────────────────────────

/** ⑱ The link at the end of the scroll: danger 13 / 600, centred, padding 10 0. Date 07's sheet. */
@Composable
internal fun CancelLink(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DrawnSmall(DateCopy.CANCEL_DATE, onCancel) { press ->
            Text(
                DateCopy.CANCEL_DATE,
                modifier = press.padding(vertical = 10.dp),
                color = Danger,
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
    }
}
