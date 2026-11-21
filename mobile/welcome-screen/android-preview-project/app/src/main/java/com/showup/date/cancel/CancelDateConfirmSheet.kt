/*
 * CancelDateConfirmSheet.kt
 * ShowUp · B to E and I, the penalty guard (SHOWUP-198), from `CancelDateConfirmSheet` in `screen-cancel-reference.jsx`
 *
 * ONE MODAL SHELL. Chat locked (B), chat open (C), a request sent (D), a failed cancel (E) and Date 05's cutoff (I)
 * are one sheet whose parts are replaced in place -- never a second sheet. The shell scrolls inside, so the choices
 * are reached by scrolling on every phone.
 *
 * The screen takes values and lambdas ([CancelUi], [CancelActions]); it never receives a navigator, so every frame
 * renders in every state at every size with no device.
 *
 * Numbers are the reference's, with CSS's border-in-the-box counted into the padding where Compose only draws a
 * border (a row's 13 + its 1 px), the line boxes as `cssLineBox`, and the portrait's missing-photo treatment the
 * Date screen hero's own (`PhotoBackdrop`'s placeholder gradient).
 */
package com.showup.date.cancel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.change.ChangeKind
import com.showup.date.DrawnSmall
import com.showup.date.end.EndErrorBox
import com.showup.date.pressable
import com.showup.designsystem.BlushWash
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.LilacWash
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.ModalShell
import com.showup.designsystem.Muted
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.ShadowViolet
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.SuccessFg
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.fitWordsOf
import com.showup.designsystem.prettyWrap
import com.showup.designsystem.roundedShadows
import com.showup.search.PhotoBackdrop
import com.showup.search.cssLinearGradient
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import java.time.ZoneId

const val CANCEL_SHEET_TAG = "cancel:sheet"
const val CANCEL_PORTRAIT_TAG = "cancel:portrait"
const val CANCEL_PILL_TAG = "cancel:pill"
const val CANCEL_HEADLINE_TAG = "cancel:headline"
const val CANCEL_REASON_TAG = "cancel:reason"
const val CANCEL_SOLUTION_TAG = "cancel:solution"
const val CANCEL_SOLUTION_BODY_TAG = "cancel:solution-body"
const val CANCEL_TILE_TIME_TAG = "cancel:tile-time"
const val CANCEL_TILE_PLACE_TAG = "cancel:tile-place"
const val CANCEL_SENT_TAG = "cancel:sent"
const val CANCEL_MESSAGE_TAG = "cancel:message"
const val CANCEL_COST_TAG = "cancel:cost"
const val CANCEL_KEEP_TAG = "cancel:keep"
const val CANCEL_CONFIRM_TAG = "cancel:confirm"
const val CANCEL_WARNING_TAG = "cancel:warning"

/**
 * The reference's own red (`#dc2626`) for the cost box, the link and the warning line. It is not `--liq-danger`
 * (`#FB323B`): the sheet draws a deeper one on purpose, and no token holds it.
 */
private val CancelRed = Color(0xFFDC2626)

/** Every tap the sheet can send. The host binds them to the model; a test records them. */
data class CancelActions(
    val onKeep: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onMessage: () -> Unit = {},
    val onTile: (ChangeKind) -> Unit = {},
    val onCancel: () -> Unit = {},
)

private val GutPunch = TextStyle(
    fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = (27 * 1.14).sp,
    letterSpacing = (-0.01).em, color = Fg, textAlign = TextAlign.Center,
)
private val GutBody = TextStyle(fontFamily = Manrope, fontSize = 15.sp, lineHeight = (15 * 1.55).sp, color = Muted, textAlign = TextAlign.Center)
private val ReasonStyle = TextStyle(fontFamily = Manrope, fontSize = 13.5.sp, lineHeight = (13.5 * 1.5).sp, color = Muted)
private val SolutionTitle = TextStyle(
    fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, letterSpacing = (-0.01).em, color = Fg,
)
private val SolutionBody = TextStyle(fontFamily = Manrope, fontSize = 13.sp, lineHeight = (13 * 1.5).sp, color = Muted)
private val SentStyle = TextStyle(fontFamily = Manrope, fontSize = 13.sp, lineHeight = (13 * 1.45).sp, color = Fg)
private val CostStyle = TextStyle(fontFamily = Manrope, fontSize = 12.5.sp, lineHeight = (12.5 * 1.5).sp, color = Fg)
private val WarningStyle = TextStyle(
    fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = (12 * 1.4).sp,
    letterSpacing = (-0.01).em, color = CancelRed, textAlign = TextAlign.Center,
)

/** The runs as the reference draws them: `<strong>` is 700 in the ink, `<em>` italic, `no-show` 800 in the red. */
internal fun runsToText(runs: List<Run>): AnnotatedString = buildAnnotatedString {
    for (r in runs) {
        when (r.style) {
            RunStyle.Plain -> append(r.text)
            RunStyle.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Fg)) { append(r.text) }
            RunStyle.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(r.text) }
            RunStyle.Danger -> withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = CancelRed)) { append(r.text) }
        }
    }
}

@Composable
private fun CancelText(text: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier, pretty: Boolean = true) {
    Text(
        text,
        modifier = modifier.let { if (pretty) it.prettyWrap(text, style, centered = style.textAlign == TextAlign.Center) else it }.cssLineBox(style.lineHeight),
        style = style,
    )
}

// ── the shell ───────────────────────────────────────────────────────────────────────────────────

/**
 * B to E and I while the model's sheet is open, nothing otherwise. The scrim, back and swipe all close it (the
 * model's `dismiss`); nothing is stored.
 */
@Composable
fun CancelDateSheetHost(ui: CancelUi, zone: ZoneId, actions: CancelActions) {
    val date = ui.date
    if (!ui.open || date == null) return
    ModalShell(
        label = CancelCopy.LINK,
        onDismiss = { actions.onDismiss() },
        contentPadding = PaddingValues(0.dp),
    ) {
        CancelDateConfirmSheet(ui, date.match.name, guardPill(date, zone), date.match.photoUrl, actions)
    }
}

/**
 * The sheet's content, padding `10 0 26`, in the reference's order: the portrait, the gut-punch, the three reasons,
 * the solution box, the cost box and the choices.
 */
@Composable
fun CancelDateConfirmSheet(ui: CancelUi, name: String, pill: String, photoUrl: String?, actions: CancelActions) {
    Column(Modifier.fillMaxWidth().testTag(CANCEL_SHEET_TAG).padding(top = 10.dp, bottom = 26.dp)) {
        Portrait(name, pill, photoUrl)
        GutPunch()
        Reasons()
        Solution(ui, name, actions)
        CostBox(ui.drop)
        Choices(ui, name, actions)
    }
}

// ── the portrait ────────────────────────────────────────────────────────────────────────────────

/** Her, made real: the match's photo 244 tall, radius 24, `center 22%`, the ink gradient, the pill and the line. */
@Composable
private fun Portrait(name: String, pill: String, photoUrl: String?) {
    val shape = RoundedCornerShape(24.dp)
    Box(Modifier.padding(start = 20.dp, top = 6.dp, end = 20.dp).fillMaxWidth().height(244.dp).clip(shape).testTag(CANCEL_PORTRAIT_TAG)) {
        // 22% down: the Compose bias for a CSS object-position percentage p is 2p - 1.
        PhotoBackdrop(photoUrl.orEmpty(), 0, Modifier.fillMaxSize(), alignment = BiasAlignment(0f, -0.56f))
        // rgba(29,17,41,0) at 38% to rgba(29,17,41,.78): the ink at both ends, so the ramp never passes through grey.
        val ink = Color(0xFF1D1129)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.38f to ink.copy(alpha = 0f), 1f to ink.copy(alpha = 0.78f)),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) {
            val pillShape = RoundedCornerShape(50)
            Row(
                Modifier
                    .testTag(CANCEL_PILL_TAG)
                    .background(Color.White.copy(alpha = 0.16f), pillShape)
                    .border(1.dp, Color.White.copy(alpha = 0.28f), pillShape)
                    // Pad 5 11, plus the 1 px border, which CSS counts in the box.
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BrandIcon.Calendar, 13.dp, tint = Color.White, strokeWidth = 2.4f)
                Text(
                    pill, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.5.sp,
                    letterSpacing = (-0.01).em,
                )
            }
            Spacer(Modifier.height(8.dp))
            val counting = TextStyle(
                fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 24.sp,
                letterSpacing = (-0.01).em, color = Color.White,
            )
            Text(CancelCopy.COUNTING.format(name), Modifier.cssLineBox(counting.lineHeight), style = counting)
        }
    }
}

// ── the gut-punch ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun GutPunch() {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, top = 20.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val headline = buildAnnotatedString {
            append(CancelCopy.HEADLINE_LEAD)
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(CancelCopy.HEADLINE_ITALIC) }
        }
        CancelText(headline, GutPunch, Modifier.fillMaxWidth().testTag(CANCEL_HEADLINE_TAG).semantics { heading() })
        Spacer(Modifier.height(10.dp))
        CancelText(runsToText(CancelCopy.BODY), GutBody, Modifier.widthIn(max = 330.dp))
    }
}

// ── the reasons ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun Reasons() {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, top = 20.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val icons = listOf(BrandIcon.Smile, BrandIcon.Heart, BrandIcon.Star)
        CancelCopy.REASONS.forEachIndexed { index, runs ->
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag(CANCEL_REASON_TAG)
                    .background(LilacWash, shape)
                    .border(1.dp, BorderSoft, shape)
                    // Pad 13 15, plus the 1 px border, which CSS counts in the box.
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier.padding(top = 1.dp).size(34.dp).background(Purple.copy(alpha = 0.10f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icons[index], 18.dp, tint = Purple, strokeWidth = 2.2f) }
                CancelText(runsToText(runs), ReasonStyle.copy(textAlign = TextAlign.Start), Modifier.weight(1f))
            }
        }
    }
}

// ── the solution box ────────────────────────────────────────────────────────────────────────────

/**
 * `Sometimes life happens`, with what is left of Date 05's offer: the body line and the two tiles while a new time
 * or place can still be agreed, or D in their place once one was sent, and `Message {name} instead` whenever the
 * chat is open. From the cutoff (I) only the title and the message button remain.
 */
@Composable
private fun Solution(ui: CancelUi, name: String, actions: CancelActions) {
    val closed = ui.changeClosed
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, top = 18.dp, end = 24.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag(CANCEL_SOLUTION_TAG)
                .background(BlushWash, shape)
                .border(1.dp, Orange.copy(alpha = 0.18f), shape)
                // Pad 16 16 15, plus the 1 px border, which CSS counts in the box.
                .padding(start = 17.dp, top = 17.dp, end = 17.dp, bottom = 16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(BrandIcon.MessageCircle, 17.dp, tint = Orange, strokeWidth = 2.3f)
                CancelText(AnnotatedString(CancelCopy.SOLUTION_TITLE.format(name)), SolutionTitle.copy(lineHeight = (13.5 * 1.366).sp), Modifier.weight(1f), pretty = false)
            }
            if (!closed) {
                Spacer(Modifier.height(6.dp))
                CancelText(AnnotatedString(CancelCopy.SOLUTION_BODY.format(name)), SolutionBody, Modifier.testTag(CANCEL_SOLUTION_BODY_TAG))
                Spacer(Modifier.height(13.dp))
                val sent = ui.sent
                if (sent != null) SentLine(sent, name) else Tiles(ui, actions)
            }
            if (ui.showMessage) {
                Spacer(Modifier.height(if (closed) 10.dp else 8.dp))
                MessageButton(name, actions.onMessage, inert = ui.cancelling)
            }
        }
    }
}

@Composable
private fun SentLine(sent: SentRequest, name: String) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(CANCEL_SENT_TAG)
            .background(Elevated, shape)
            .border(1.dp, BorderSoft, shape)
            // Pad 12 13, plus the 1 px border, which CSS counts in the box.
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 1.dp)) { Icon(BrandIcon.Check, 16.dp, tint = SuccessFg, strokeWidth = 2.6f) }
        val text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(sent.label) }
            append(CancelCopy.SENT_TAIL.format(name))
        }
        CancelText(text, SentStyle, Modifier.weight(1f))
    }
}

/** The two tiles in two columns, gap 8: the same tile Date 05's locked card draws, each following its own gate. */
@Composable
private fun Tiles(ui: CancelUi, actions: CancelActions) {
    val kinds = cancelTiles(ui.date?.change, ui.now)
    if (kinds.isEmpty()) return
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (kind in kinds) {
            val shape = RoundedCornerShape(16.dp)
            val label = if (kind == ChangeKind.Time) CancelCopy.CHANGE_TIME else CancelCopy.CHANGE_PLACE
            val tag = if (kind == ChangeKind.Time) CANCEL_TILE_TIME_TAG else CANCEL_TILE_PLACE_TAG
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag(tag)
                    .then(if (ui.cancelling) Modifier.semantics { role = Role.Button; disabled() } else Modifier.pressable(Role.Button) { actions.onTile(kind) })
                    .background(Elevated, shape)
                    .border(1.dp, BorderSoft, shape)
                    // Pad 14 8, plus the 1 px border, which CSS counts in the box.
                    .padding(vertical = 15.dp, horizontal = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Icon(if (kind == ChangeKind.Time) BrandIcon.Clock else BrandIcon.MapPin, 18.dp, tint = Purple, strokeWidth = 2f)
                val style = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Fg, textAlign = TextAlign.Center)
                Text(label, Modifier.fitWordsOf(label, style), style = style)
            }
        }
        // A lone tile keeps its column, as the grid's `1fr 1fr` does.
        if (kinds.size == 1) Spacer(Modifier.weight(1f))
    }
}

/** `Message {name} instead`: white, h 48, radius 14, the orange hairline, `message-circle` 17 / 2.4, 15 / 700. */
@Composable
private fun MessageButton(name: String, onClick: () -> Unit, inert: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(CANCEL_MESSAGE_TAG)
            .alpha(if (inert) 0.6f else 1f)
            .then(if (inert) Modifier.semantics { role = Role.Button; disabled() } else Modifier.pressable(Role.Button, onClick = onClick))
            .background(Elevated, shape)
            .border(1.dp, Orange.copy(alpha = 0.35f), shape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(BrandIcon.MessageCircle, 17.dp, tint = Orange, strokeWidth = 2.4f)
            Text(
                CancelCopy.MESSAGE.format(name), color = Orange, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

// ── the cost, kept honest but quiet ─────────────────────────────────────────────────────────────

@Composable
private fun CostBox(drop: Int) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, top = 16.dp, end = 24.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .testTag(CANCEL_COST_TAG)
                .background(CancelRed.copy(alpha = 0.10f), shape)
                .border(1.dp, CancelRed.copy(alpha = 0.32f), shape)
                // Pad 14 16, plus the 1 px border, which CSS counts in the box.
                .padding(horizontal = 17.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.padding(top = 1.dp)) { Icon(BrandIcon.ShieldAlert, 17.dp, tint = CancelRed, strokeWidth = 2.3f) }
            CancelText(runsToText(CancelCopy.cost(drop)), CostStyle, Modifier.weight(1f))
        }
    }
}

// ── the choices ─────────────────────────────────────────────────────────────────────────────────

/**
 * Padding `20 24 0`, gap 10: E's box (when the cancel failed) between the cost box and the choices, then
 * `Don't cancel my date` (sunset, h 54, radius 16) and the quiet red link with its warning (gap 14).
 */
@Composable
private fun Choices(ui: CancelUi, name: String, actions: CancelActions) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, top = 20.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The box carries its own 14 below (`margin 0 0 14`) beside the column's 10, as the reference does.
        if (ui.failed) EndErrorBox(text = CancelCopy.CANCEL_FAILED)
        KeepButton(actions.onKeep, inert = ui.cancelling)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ConfirmLink(actions.onCancel, inert = ui.cancelling)
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                val warning = AnnotatedString(CancelCopy.WARNING.format(name))
                CancelText(warning, WarningStyle, Modifier.testTag(CANCEL_WARNING_TAG))
            }
        }
    }
}

/** `Don't cancel my date`: the sunset button, h 54 (a minimum, so the largest type grows it), radius 16, the violet glow. */
@Composable
private fun KeepButton(onClick: () -> Unit, inert: Boolean) {
    val brush = Brush.linearGradient(colorStops = SunsetStops.toTypedArray())
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .testTag(CANCEL_KEEP_TAG)
            .alpha(if (inert) 0.6f else 1f)
            .then(if (inert) Modifier.semantics { role = Role.Button; disabled() } else Modifier.pressable(Role.Button, onClick = onClick))
            .roundedShadows(brush, 16.dp, ShadowViolet)
            .clip(RoundedCornerShape(16.dp))
            .cssLinearGradient(135f, SunsetStops.map { it.second }, SunsetStops.map { it.first })
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(CancelCopy.KEEP, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

/** `Cancel anyway and let her down`: drawn 30 tall, 14 / 600 in the red, underlined 3 below; it answers at 44. */
@Composable
private fun ConfirmLink(onClick: () -> Unit, inert: Boolean) {
    DrawnSmall(
        label = CancelCopy.CONFIRM,
        onClick = { if (!inert) onClick() },
        modifier = Modifier.fillMaxWidth().alpha(if (inert) 0.6f else 1f),
        touch = Modifier.testTag(CANCEL_CONFIRM_TAG).semantics { if (inert) disabled() },
    ) { press ->
        Box(press.fillMaxWidth().heightIn(min = 30.dp), contentAlignment = Alignment.Center) {
            Text(
                CancelCopy.CONFIRM, color = CancelRed, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                textDecoration = TextDecoration.Underline, textAlign = TextAlign.Center,
            )
        }
    }
}
