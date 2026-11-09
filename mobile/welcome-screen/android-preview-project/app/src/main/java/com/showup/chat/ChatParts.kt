/*
 * ChatParts.kt
 * ShowUp · the coordinate chat's parts (SHOWUP-194): `ChatHeader`, `ChatBubble`, `ThreadScroll`,
 * `BigActionChip` and the chip grid, from `screen-chat-reference.jsx`, with its numbers
 *
 * Each part takes values and returns pixels. Where the reference draws a control smaller than
 * Android's 48 floor (the back chevron 34, Send 46), the drawing keeps its size and the touch area is
 * laid over it ([DrawnSmall] / [OverflowTapTarget]), as on the Date screen.
 */
package com.showup.chat

import com.showup.designsystem.OverflowTapTarget
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import com.showup.date.ActiveDate
import com.showup.date.DrawnSmall
import com.showup.date.IconTile
import com.showup.date.MatchPhoto
import com.showup.date.press
import com.showup.designsystem.Border
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Cream
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Muted
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.ShadowSm
import com.showup.designsystem.ShadowViolet
import com.showup.designsystem.ShowUpEasing
import com.showup.designsystem.Success
import com.showup.designsystem.SuccessFg
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.TabBarGlass
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.fitWordsOf
import com.showup.designsystem.widestWord
import com.showup.designsystem.wordFitFloor
import com.showup.designsystem.prettyWrap
import com.showup.designsystem.rememberMotion
import com.showup.designsystem.roundedShadows
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

// ── test tags ───────────────────────────────────────────────────────────────

const val CHAT_FIELD_TAG = "chat-field"
const val CHAT_SEND_TAG = "chat-send"
const val CHAT_END_CARD_BUTTON_TAG = "chat-end-card-button"
const val END_SHEET_KEEP_TAG = "end-sheet-keep"
const val END_SHEET_END_TAG = "end-sheet-end"
const val CHAT_THREAD_TAG = "chat:thread"
const val CHAT_DOCK_TAG = "chat:dock"
const val CHAT_GUIDE_TAG = "chat:guide"
const val CHAT_GOT_IT_TAG = "chat:got-it"
const val CHAT_END_CARD_TAG = "chat:end-card"
const val CHAT_SHEET_TAG = "chat:sheet"
const val CHAT_CTA_TAG = "chat:cta"

/** One chip on one surface: the start grid's tile and the dock's compact chip are different nodes. */
fun chipTag(reason: ChatReason, surface: ChipSurface) = "chat-chip-${surface.wire}-${reason.wire}"

/** A dock chip's drawing, which answers 3 beyond itself on every side ([chipTag] is the touch area). */
fun chipFaceTag(reason: ChatReason, surface: ChipSurface) = "${chipTag(reason, surface)}:face"

/** J: the line under a failed message that resends it. */
const val CHAT_RESEND_TAG = "chat-resend"

// ── small shared pieces ──────────────────────────────────────────────────────

/** The sunset, at the angle Compose's default gradient takes: corner to corner, as the kit's 135deg on a near-square. */
internal val SunsetBrush: Brush = Brush.linearGradient(colorStops = SunsetStops.toTypedArray())

/** [text] with each of [bold] set in [boldColor] 700: the reference's `<strong style={{ color: fg, fontWeight: 700 }}>`. */
internal fun withBold(text: String, bold: List<String>, boldColor: Color = Fg): AnnotatedString = buildAnnotatedString {
    append(text)
    for (run in bold) {
        val at = text.indexOf(run)
        if (at >= 0) addStyle(SpanStyle(color = boldColor, fontWeight = FontWeight.Bold), at, at + run.length)
    }
}

/** The back chevron: drawn 34 with `chevron-left` 23 / 2.2, answering at 48. */
@Composable
internal fun ChatBackButton(onBack: () -> Unit) {
    DrawnSmall(ChatCopy.BACK, onBack) { press ->
        Box(press.size(34.dp), contentAlignment = Alignment.Center) {
            Icon(BrandIcon.ChevronLeft, 23.dp, tint = Fg, strokeWidth = 2.2f)
        }
    }
}

/** `lock` 12 / 2.3 and a line, 11.5 / 600, subtle, centred: the start grid's and the tutorial's privacy notes. */
@Composable
internal fun LockLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(BrandIcon.Lock, 12.dp, tint = FgSubtle, strokeWidth = 2.3f)
        Spacer(Modifier.width(6.dp))
        // `pretty`, as the paragraphs are: at 320 the line ended `... never what you` / `type.` (the 8 Nov
        // 2026 device sweep).
        val style = TextStyle(color = FgSubtle, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
        Text(text, modifier = Modifier.weight(1f, fill = false).prettyWrap(AnnotatedString(text), style), style = style)
    }
}

// ── ChatHeader ───────────────────────────────────────────────────────────────

/**
 * Back, the avatar 40, the name (Lora 700 17) with `DATE ON`, and `{day} · {time} · {place} · {countdown}`
 * on one line, ellipsised -- always the latest AGREED time and place. Glass cream 92%, a 1 px soft rule
 * under it; it runs up under the status bar.
 */
@Composable
internal fun ChatHeader(date: ActiveDate, now: Instant, zone: ZoneId, onBack: () -> Unit) {
    val line = headerLine(date, zone)
    val countdown = countdown(now, date.meetAt)
    // The header's type grows to 165 % -- iOS's AX1 -- and no further, as a bar's title does. At 200 % its
    // line wrapped to three and the header took 148 of the 298 the keys leave on a 320 x 568 screen, and
    // the field no longer fit under it (the SHOWUP-194 keyboard sweep). The Date screen shows the same
    // time and place at full size.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, HEADER_MOST_SCALE))) {
        HeaderBar(date, line, countdown, onBack)
    }
}

/** The most the header's type grows: iOS's AX1, 165 %. */
private const val HEADER_MOST_SCALE = 1.65f

@Composable
private fun HeaderBar(date: ActiveDate, line: String, countdown: String?, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TabBarGlass)
            .drawBehind {
                val rule = 1.dp.toPx()
                drawRect(BorderSoft, topLeft = Offset(0f, size.height - rule), size = Size(size.width, rule))
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChatBackButton(onBack)
            Spacer(Modifier.width(10.dp))
            MatchPhoto(date.match.name, date.match.photoUrl, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // One line wherever it fits; at the largest type on a narrow phone a name too wide for
                    // the room beside `DATE ON` shrinks, or wraps between its words, rather than be cut.
                    Text(
                        date.match.name,
                        modifier = Modifier.weight(1f, fill = false).fitWordsOf(date.match.name, HeaderName),
                        color = Fg,
                        style = HeaderName,
                    )
                    Spacer(Modifier.width(7.dp))
                    DateOnPill()
                }
                Text(
                    buildAnnotatedString {
                        append(line)
                        if (countdown != null) {
                            append(" · ")
                            withStyle(SpanStyle(color = Orange)) { append(countdown) }
                        }
                    },
                    modifier = Modifier.padding(top = 1.dp),
                    color = Muted,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    // One line wherever it fits, as the reference sets it; at large type it wraps rather than
                    // cut the place or the countdown off -- the header always shows the agreed time and place.
                )
            }
        }
    }
}

/** The header's name: Lora 700 17. */
private val HeaderName = TextStyle(fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 17.sp)

/** `DATE ON`: success 12% pill, pad 2 7, the 5 dot, 10 / 800 uppercase in the success ink. */
@Composable
private fun DateOnPill() {
    Row(
        Modifier
            .background(Success.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).background(Success, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(
            ChatCopy.DATE_ON,
            color = SuccessFg,
            fontFamily = Manrope,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp,
            letterSpacing = 0.04.sp,
            maxLines = 1,
        )
    }
}

// ── ChatBubble ───────────────────────────────────────────────────────────────

private val BubbleText = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, lineHeight = (14.5 * 1.42).sp)

/**
 * One message. Mine: right, sunset, white, corners 18 18 5 18, the violet glow, then the time and the
 * read receipt. Theirs: left, white with a 1 px soft border, corners 18 18 18 5, the small shadow, and
 * the avatar 26 beside the last of a run. A chip message carries its badge above the text. At most 78%
 * of the thread's width.
 */
@Composable
internal fun ChatBubble(message: ChatMessage, name: String, photoUrl: String?, showAvatar: Boolean, zone: ZoneId) {
    val mine = message.fromYou
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // J: a message that did not send has no Delivered / Read -- it was neither.
        val most = maxWidth * 0.78f
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!mine) {
                Box(Modifier.width(26.dp)) { if (showAvatar) MatchPhoto(name, photoUrl, 26.dp) }
                Spacer(Modifier.width(8.dp))
            }
            Column(
                Modifier.widthIn(max = most),
                horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                val shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomEnd = if (mine) 5.dp else 18.dp, bottomStart = if (mine) 18.dp else 5.dp,
                )
                val fill: Brush = if (mine) SunsetBrush else SolidColor(Elevated)
                Column(
                    Modifier
                        // The glow on the 18 rounding, the fill on the real shape over it: the one sharper
                        // corner covers the glow's rounder one, and a soft shadow cannot show the 13 between.
                        .roundedShadows(fill, 18.dp, if (mine) ShadowViolet else ShadowSm)
                        .background(fill, shape)
                        .then(if (mine) Modifier else Modifier.border(1.dp, BorderSoft, shape))
                        // Pad 9 13 11, plus theirs' 1 px border, which CSS counts in the box.
                        .padding(
                            start = if (mine) 13.dp else 14.dp, end = if (mine) 13.dp else 14.dp,
                            top = if (mine) 9.dp else 10.dp, bottom = if (mine) 11.dp else 12.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    message.reason?.let { ReasonBadge(it, onLight = !mine) }
                    val text = AnnotatedString(message.body)
                    Text(
                        text,
                        modifier = Modifier.prettyWrap(text, BubbleText).cssLineBox(BubbleText.lineHeight),
                        style = BubbleText.copy(color = if (mine) Color.White else Fg),
                    )
                }
                Row(
                    Modifier.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        messageTime(message.sentAt, zone),
                        color = FgSubtle, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                    )
                    if (mine && !message.failed) Receipt(read = message.read == true)
                }
            }
        }
    }
}

/** `check` 12 / 3 and `Read` in violet, or `Delivered` in subtle ink: one word to TalkBack. */
@Composable
private fun Receipt(read: Boolean) {
    val ink = if (read) Purple else FgSubtle
    val word = if (read) ChatCopy.READ else ChatCopy.DELIVERED
    Row(
        Modifier.clearAndSetSemantics { contentDescription = word },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(BrandIcon.Check, 12.dp, tint = ink, strokeWidth = 3f)
        Text(word, color = ink, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

// ── ThreadScroll ─────────────────────────────────────────────────────────────

/**
 * The thread: the only scroll in C to E, white, with the reference's always-visible indicator and its
 * reveal rule.
 *
 * THE FOOT. The cancel guide and its collapsed line sit at the foot of the thread -- `margin-top: auto`
 * in the reference: under the messages when they overflow, at the bottom of the viewport when they do
 * not. Two groups spaced apart in a column at least the viewport tall do exactly that, with no absolute
 * position. (The End date card has no `margin-top: auto`: it is the messages' last item.)
 *
 * THE REVEAL RULE. When the foot changes ([footKey]) or a message arrives, the thread scrolls to the
 * end -- unless the foot is taller than the viewport (the guide on a short phone), when it scrolls to the
 * foot's top less 8, so its title and `Got it` are what is seen. The first scroll jumps; later ones glide
 * on the brand curve, and jump with reduced motion. When the viewport shrinks (the keyboard), a thread
 * at its end stays at its end.
 *
 * THE INDICATOR. Track `rgba(29,17,41,.06)` and thumb `.34`, 6 wide, 3 from the right, inset 8, the
 * thumb at least 30; drawn only while the thread overflows, fading over [Motion.TINT]. It is drawn in
 * the draw phase from the scroll position, so scrolling never recomposes it.
 */
@Composable
internal fun ThreadScroll(
    state: ScrollState,
    contentPadding: PaddingValues,
    messageCount: Int,
    footKey: Any?,
    modifier: Modifier = Modifier,
    foot: (@Composable () -> Unit)? = null,
    messages: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val motion = rememberMotion()
    var content by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var footBox by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var first by remember { mutableStateOf(true) }
    BoxWithConstraints(modifier) {
        val viewport = maxHeight
        val padded = contentPadding.calculateTopPadding() + contentPadding.calculateBottomPadding()
        Column(Modifier.fillMaxSize().verticalScroll(state)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { content = it }
                    .padding(contentPadding)
                    .heightIn(min = (viewport - padded).coerceAtLeast(0.dp)),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), content = messages)
                if (foot != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = if (messageCount > 0) 12.dp else 0.dp)
                            .onGloballyPositioned { footBox = it },
                    ) { foot() }
                }
            }
        }
        LaunchedEffect(messageCount, footKey) {
            withFrameNanos { }
            val viewportPx = with(density) { viewport.toPx() }
            val box = footBox.takeIf { foot != null }
            val holder = content
            val target = if (box != null && holder != null && box.size.height > viewportPx) {
                val top = holder.localPositionOf(box, Offset.Zero).y
                (top - with(density) { 8.dp.toPx() }).toInt().coerceIn(0, state.maxValue)
            } else {
                state.maxValue
            }
            if (first || !motion.enabled) state.scrollTo(target) else state.animateScrollTo(target, tween(Motion.SCREEN, easing = ShowUpEasing))
            first = false
        }
        // How far the reader is from the end, as they or a reveal scroll -- not as the viewport changes.
        var fromEnd by remember { mutableIntStateOf(0) }
        LaunchedEffect(state) { snapshotFlow { state.value }.collect { fromEnd = state.maxValue - it } }
        // The keyboard takes the thread's height from below, and with the scroll held from the top the
        // newest messages slid out of sight -- Read & reply, a tap on the field, and the message being
        // answered was gone (the SHOWUP-194 review). A thread at its end stays at its end; one the
        // reader scrolled up stays where they left it.
        LaunchedEffect(viewport) {
            if (first) return@LaunchedEffect
            withFrameNanos { }
            if (fromEnd <= 1) state.scrollTo(state.maxValue)
        }
        val shown by animateFloatAsState(
            targetValue = if (state.maxValue > with(density) { 2.dp.toPx() }) 1f else 0f,
            animationSpec = tween(if (motion.enabled) Motion.TINT else 0),
            label = "thread-indicator",
        )
        Spacer(
            Modifier
                .matchParentSize()
                .drawBehind {
                    if (shown <= 0f) return@drawBehind
                    val inset = 8.dp.toPx()
                    val w = 6.dp.toPx()
                    val x = size.width - 3.dp.toPx() - w
                    val track = size.height - inset * 2
                    if (track <= 0f) return@drawBehind
                    val radius = CornerRadius(w / 2, w / 2)
                    drawRoundRect(Fg.copy(alpha = 0.06f * shown), Offset(x, inset), Size(w, track), radius)
                    val total = size.height + state.maxValue
                    val thumb = max(30.dp.toPx(), track * (size.height / total))
                    val ratio = if (state.maxValue > 0) state.value.toFloat() / state.maxValue else 0f
                    drawRoundRect(Fg.copy(alpha = 0.34f * shown), Offset(x, inset + (track - thumb) * ratio), Size(w, thumb), radius)
                },
        )
    }
}

// ── BigActionChip ────────────────────────────────────────────────────────────

/**
 * The reference's `BigActionChip`: a tile on the start grid (pad 15, radius 18, at least 68, icon box
 * 42 / 13 with the icon 21, label 15 / 700)  * or the dock's compact chip (pad 4 10 4 5, radius 11, at least
 * 38, icon box 26 / 8 with the icon 14, label 11.5 / 700). Selected: the tone's fill and a border in the
 * tone's ink; otherwise white with the tone's chip border. The small shadow either way. The full label
 * always -- it wraps, and a word wider than its column shrinks to fit rather than break.
 *
 * The compact chip answers 3 beyond its drawing on every side, into the 6 gap: 44 to touch where it is
 * drawn 38, and neighbours' areas meet without overlapping (SHOWUP-194, 9 Nov 2026: "38 high, 44 tap
 * area ... not 48 on Android: four rows would cost 32 px more with the keyboard up"). The one control
 * here under Android's 48, by the ticket's decision.
 */
@Composable
internal fun BigActionChip(
    reason: ChatReason,
    active: Boolean,
    compact: Boolean,
    surface: ChipSurface,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = reason.tone
    val motion = rememberMotion()
    val spec = tween<Color>(if (motion.enabled) Motion.TINT else 0)
    val fill by animateColorAsState(if (active) tone.bg else Elevated, spec, label = "chip-fill")
    val edge by animateColorAsState(if (active) tone.fg else tone.chipBorder, spec, label = "chip-border")
    val radius = if (compact) 11.dp else 18.dp
    val shape = RoundedCornerShape(radius)
    val interaction = remember { MutableInteractionSource() }
    val label = chipLabel(compact).copy(color = if (tone == ReasonTone.DarkRed || tone == ReasonTone.Danger) tone.fg else Fg)
    if (!compact) {
        ChipFace(
            reason, compact = false, fill, edge, radius, label,
            modifier
                .testTag(chipTag(reason, surface))
                .press(interaction)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics { selected = active },
        )
        return
    }
    // The touch area carries the role, the state and the name; the drawing says nothing of its own.
    OverflowTapTarget(
        touch = Modifier
            .testTag(chipTag(reason, surface))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { selected = active; contentDescription = reason.label },
        modifier = modifier,
        slop = DOCK_CHIP_SLOP,
    ) {
        ChipFace(
            reason, compact = true, fill, edge, radius, label,
            Modifier.testTag(chipFaceTag(reason, surface)).clearAndSetSemantics {}.press(interaction),
        )
    }
}

/** How far a dock chip answers beyond its drawing, on every side: half the 6 gap. */
private val DOCK_CHIP_SLOP = 3.dp

/** A chip as it is drawn: the fill, the border, the icon tile and the label. */
@Composable
private fun ChipFace(
    reason: ChatReason,
    compact: Boolean,
    fill: Color,
    edge: Color,
    radius: Dp,
    label: TextStyle,
    modifier: Modifier,
) {
    val tone = reason.tone
    val shape = RoundedCornerShape(radius)
    Row(
        modifier
            .roundedShadows(SolidColor(fill), radius, ShadowSm)
            .border(1.5.dp, edge, shape)
            .heightIn(min = if (compact) 38.dp else 68.dp)
            // The reference's padding plus the 1.5 border, which CSS counts in the box and Compose only
            // draws: tiles come out 75 tall as the sheet renders them. The compact chip takes the border
            // at its sides only, so its label wraps as the reference's and it stays the ticket's 38 high.
            .padding(
                if (compact) PaddingValues(start = 6.5.dp, end = 11.5.dp, top = 4.dp, bottom = 4.dp) else PaddingValues(16.5.dp),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            size = if (compact) 26.dp else 42.dp,
            radius = if (compact) 8.dp else 13.dp,
            fill = SolidColor(tone.bg),
            icon = reason.icon,
            iconSize = if (compact) 14.dp else 21.dp,
            tint = tone.fg,
            stroke = 2.3f,
        )
        Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
        // Weighted, so the row's intrinsic height -- one height for both chips in a grid row -- measures
        // the label at the width it really gets, wrapped, not on one line.
        Text(
            reason.label,
            modifier = Modifier.weight(1f).fitWordsOf(reason.label, label).cssLineBox(label.lineHeight),
            style = label,
        )
    }
}

/** A chip's label: Manrope 700, 15 / 1.16 on a tile, 11.5 / 1.16 compact. */
private fun chipLabel(compact: Boolean) = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight.Bold,
    fontSize = if (compact) 11.5.sp else 15.sp,
    lineHeight = ((if (compact) 11.5 else 15.0) * 1.16).sp,
)

/** A tile's width that is not its label: the 16.5 padding either side, the icon box 42 and the gap 12. */
private val TileChrome = (16.5 * 2 + 42 + 12).dp

/**
 * The chips in two columns, in the vocabulary's order: tiles 10 apart on the start grid, compact chips
 * 6 apart in the dock. The two in a row are one height, as a CSS grid row stretches them; an odd last
 * chip keeps its column's width.
 *
 * Tiles go to one column where a label's widest word would not fit a two-column tile even at the least
 * [fitWordsOf] shrinks it to -- the largest type on a 320 phone, where the label has 52 and `running`
 * broke as `runnin / g` (the SHOWUP-194 fit sweep). One column gives each label the tile's width
 * rather than shrink it below the default size. Measured, so it happens there and nowhere else.
 */
@Composable
internal fun ChipGrid(
    reasons: List<ChatReason>,
    selected: ChatReason?,
    compact: Boolean,
    surface: ChipSurface,
    onPick: (ChatReason, ChipSurface) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap: Dp = if (compact) 6.dp else 10.dp
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val floor = wordFitFloor()
    val widest = remember(reasons, density) { reasons.maxOfOrNull { widestWord(measurer, it.label, chipLabel(compact = false)) } ?: 0 }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val room = with(density) { ((maxWidth - gap) / 2 - TileChrome).toPx() }
        val columns = if (compact || widest * floor <= room) 2 else 1
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in reasons.chunked(columns)) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (reason in row) {
                        BigActionChip(
                            reason = reason,
                            active = reason == selected,
                            compact = compact,
                            surface = surface,
                            onClick = { onPick(reason, surface) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    if (row.size < columns) Spacer(Modifier.weight((columns - row.size).toFloat()))
                }
            }
        }
    }
}

/** The cream 96% glass a pinned bar sits on (the dock, the tutorial's CTA), with its 1 px soft top rule. */
internal fun Modifier.pinnedGlass(): Modifier = this
    .background(Cream.copy(alpha = 0.96f))
    .drawBehind { drawRect(BorderSoft, size = Size(size.width, 1.dp.toPx())) }

/** The field's border: the ink while a chip is selected and the field open, otherwise the border token. */
internal fun fieldBorder(ui: ChatUi): Color = if (ui.selected != null && !ui.locked) Fg else Border
