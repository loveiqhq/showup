/*
 * ArrivalSheets.kt
 * ShowUp · B, C and D, the check-in sheets (SHOWUP-195), from `CheckInConfirmSheet` / `CheckInDoneSheet`
 *
 * ONE MODAL SHELL (R9). [ArrivalSheetHost] keeps a single `ModalShell` up while the phase is not Idle and
 * swaps B / C for D inside it: the content crossfades and the height animates ([Motion.PROGRESS]) on the
 * same scrim, instead of one sheet dropping while another rises. C's error box grows in ([Motion.FAST]).
 * With motion off every change is instant.
 *
 * BUSY (R7). While the location is read and stored both buttons are inert (disabled semantics), the primary
 * sits at alpha .6 -- the 194 End date sheet's waiting look -- and the scrim, back and swipe do nothing.
 *
 * The buttons are drawn as `EndDateSheet` draws its own: Boxes with radius 16 / 18, sunset through
 * `roundedShadows` and `ShadowCta`, never a second button component.
 */
package com.showup.arrival

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.date.pressable
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.ModalShell
import com.showup.designsystem.Motion
import com.showup.designsystem.Muted
import com.showup.designsystem.Purple
import com.showup.designsystem.ShadowCta
import com.showup.designsystem.ShowUpEasing
import com.showup.designsystem.SheetErrorBox
import com.showup.designsystem.SunsetStops
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.prettyWrap
import com.showup.designsystem.rememberMotion
import com.showup.designsystem.roundedShadows
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon

const val ARRIVAL_SHEET_TAG = "arrival:sheet"
const val ARRIVAL_CIRCLE_TAG = "arrival:circle"
const val ARRIVAL_NOT_YET_TAG = "arrival:not-yet"
const val ARRIVAL_YES_TAG = "arrival:yes"
const val ARRIVAL_ERROR_TAG = "arrival:error"
const val ARRIVAL_CLOSE_TAG = "arrival:close"
const val ARRIVAL_UNDO_TAG = "arrival:undo"

private val SheetTitle = TextStyle(
    fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = (26 * 1.15).sp,
    letterSpacing = (-0.01).em, color = Fg, textAlign = TextAlign.Center,
)
private val NoteStyle = TextStyle(
    fontFamily = Manrope, fontSize = 14.sp, lineHeight = (14 * 1.5).sp, color = Muted, textAlign = TextAlign.Center,
)
private val BodyStyle = TextStyle(
    fontFamily = Manrope, fontSize = 15.sp, lineHeight = (15 * 1.5).sp, color = Muted, textAlign = TextAlign.Center,
)

/** B / C are one kind, D the other: the key the host's swap animates on. */
private enum class SheetKind { Confirm, Done }

/**
 * B and C while the phase is Confirm, D while it is Done, nothing while Idle. [onNotYet] answers `Not yet`
 * and the scrim / back / swipe on B and C (ignored while busy); [onClose] answers `Close` and the same on D.
 */
@Composable
fun ArrivalSheetHost(
    ui: GateUi,
    name: String,
    onYes: () -> Unit,
    onNotYet: () -> Unit,
    onClose: () -> Unit,
    onUndo: () -> Unit,
) {
    val phase = ui.phase
    if (phase is GatePhase.Idle) return
    val motion = rememberMotion()
    // Confirm's last values, so its content keeps drawing while it fades out after the phase became Done.
    val lastConfirm = remember { arrayOfNulls<GatePhase.Confirm>(1) }
    if (phase is GatePhase.Confirm) lastConfirm[0] = phase
    val kind = if (phase is GatePhase.Done) SheetKind.Done else SheetKind.Confirm
    ModalShell(
        label = if (kind == SheetKind.Done) ArrivalCopy.DONE_TITLE else ArrivalCopy.CONFIRM_TITLE.format(name),
        onDismiss = {
            when (val p = ui.phase) {
                is GatePhase.Confirm -> if (!p.busy) onNotYet()
                is GatePhase.Done -> onClose()
                GatePhase.Idle -> Unit
            }
        },
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 24.dp),
    ) {
        AnimatedContent(
            targetState = kind,
            modifier = Modifier.fillMaxWidth().testTag(ARRIVAL_SHEET_TAG),
            transitionSpec = {
                if (motion.enabled) {
                    val spec = tween<Float>(Motion.PROGRESS, easing = ShowUpEasing)
                    ContentTransform(fadeIn(spec), fadeOut(spec), sizeTransform = SizeTransform(clip = false) { _, _ ->
                        tween(Motion.PROGRESS, easing = ShowUpEasing)
                    })
                } else {
                    ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = SizeTransform(clip = false) { _, _ -> snap() })
                }
            },
            label = "arrival-sheet",
        ) { shown ->
            when (shown) {
                SheetKind.Confirm -> {
                    val c = lastConfirm[0]
                    CheckInConfirmSheet(name, c?.note, c?.locError == true, c?.busy == true, onYes, onNotYet)
                }
                SheetKind.Done -> CheckInDoneSheet(name, onClose, onUndo)
            }
        }
    }
}

/** The 80 circle, violet 12 %, with its icon (34 / 2.2) and the 8 above it. */
@Composable
private fun SheetCircle(icon: BrandIcon) {
    Box(
        Modifier
            .padding(top = 8.dp)
            .size(80.dp)
            .testTag(ARRIVAL_CIRCLE_TAG)
            .background(Purple.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, 34.dp, tint = Purple, strokeWidth = 2.2f) }
}

@Composable
private fun SheetTitleText(text: String, maxWidth: androidx.compose.ui.unit.Dp) {
    val heading = AnnotatedString(text)
    Text(
        heading,
        modifier = Modifier.widthIn(max = maxWidth).prettyWrap(heading, SheetTitle, centered = true).cssLineBox(SheetTitle.lineHeight),
        style = SheetTitle,
    )
}

/** A sunset button, radius 18, h 56, 16.5 / 700 white, under `--liq-shadow-cta`. Waiting reads at alpha .6. */
@Composable
private fun SunsetSheetButton(label: String, tag: String, waiting: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .testTag(tag)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (waiting) 0.6f else 1f)
            .then(if (waiting) Modifier.semantics { disabled() } else Modifier.pressable(Role.Button, onClick = onClick))
            .roundedShadows(Brush.linearGradient(colorStops = SunsetStops.toTypedArray()), 18.dp, ShadowCta),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.5.sp)
    }
}

/**
 * B and C: circle 80 over the title (Lora 700 26 / 1.15, -0.01em, 16 above, at most 310, pretty), the optional
 * [note] (14 / 1.5 muted, 12 under the title), C's [SheetErrorBox] (14 under the title, 12 under a note, 16
 * above the buttons), `Not yet` (h 50, radius 16, white, soft border, 14.5 / 700 muted) and the primary 10
 * below (h 56, radius 18, sunset, `Yes, I've arrived` / `Try again`).
 *
 * The spacing is the reference's margins summed: title 22 below less the error's -8 = 14; note -10 above
 * and 20 below, so 12 and 12.
 */
@Composable
fun CheckInConfirmSheet(
    name: String,
    note: String?,
    locError: Boolean,
    busy: Boolean,
    onYes: () -> Unit,
    onNotYet: () -> Unit,
) {
    val motion = rememberMotion()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SheetCircle(BrandIcon.MapPin)
        Spacer(Modifier.height(16.dp))
        SheetTitleText(ArrivalCopy.CONFIRM_TITLE.format(name), 310.dp)
        if (note != null) {
            Spacer(Modifier.height(12.dp))
            val text = AnnotatedString(note)
            Text(
                text,
                modifier = Modifier.widthIn(max = 310.dp).prettyWrap(text, NoteStyle, centered = true).cssLineBox(NoteStyle.lineHeight),
                style = NoteStyle,
            )
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(14.dp))
        }
        val duration = if (motion.enabled) Motion.FAST else 0
        // 14 (or 12) above + 8 here = the 22 (or 20) a calm sheet has; with the box, 16 below it instead.
        AnimatedVisibility(
            visible = locError,
            enter = expandVertically(tween(duration, easing = ShowUpEasing)) + fadeIn(tween(duration, easing = ShowUpEasing)),
            exit = shrinkVertically(tween(duration, easing = ShowUpEasing)) + fadeOut(tween(duration, easing = ShowUpEasing)),
        ) {
            Column(Modifier.testTag(ARRIVAL_ERROR_TAG)) {
                SheetErrorBox(ArrivalCopy.LOC_ERROR)
                Spacer(Modifier.height(16.dp))
            }
        }
        AnimatedVisibility(
            visible = !locError,
            enter = expandVertically(tween(duration, easing = ShowUpEasing)),
            exit = shrinkVertically(tween(duration, easing = ShowUpEasing)),
        ) { Spacer(Modifier.height(8.dp)) }
        val notYetShape = RoundedCornerShape(16.dp)
        Box(
            Modifier
                .testTag(ARRIVAL_NOT_YET_TAG)
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .then(if (busy) Modifier.semantics { disabled() } else Modifier.pressable(Role.Button, onClick = onNotYet))
                .background(Elevated, notYetShape)
                .border(1.dp, BorderSoft, notYetShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(ArrivalCopy.NOT_YET, color = Muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
        }
        Spacer(Modifier.height(10.dp))
        SunsetSheetButton(
            label = if (locError) ArrivalCopy.TRY_AGAIN else ArrivalCopy.YES,
            tag = ARRIVAL_YES_TAG,
            waiting = busy,
            onClick = onYes,
        )
    }
}

/**
 * D: the same shell, circle 80 with `heart-filled`, the title (16 above, 8 below, at most 300), the body
 * (15 / 1.5 muted, at most 300, 22 below), `Close` (h 56, radius 18, sunset) and `Undo check-in` 6 below
 * (min h 44, pad 0 16, Manrope 600 14 muted, no fill, no border).
 */
@Composable
fun CheckInDoneSheet(name: String, onClose: () -> Unit, onUndo: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SheetCircle(BrandIcon.HeartFilled)
        Spacer(Modifier.height(16.dp))
        SheetTitleText(ArrivalCopy.DONE_TITLE, 300.dp)
        Spacer(Modifier.height(8.dp))
        val body = AnnotatedString(ArrivalCopy.DONE_BODY.format(name))
        Text(
            body,
            modifier = Modifier.widthIn(max = 300.dp).prettyWrap(body, BodyStyle, centered = true).cssLineBox(BodyStyle.lineHeight),
            style = BodyStyle,
        )
        Spacer(Modifier.height(22.dp))
        SunsetSheetButton(ArrivalCopy.CLOSE, ARRIVAL_CLOSE_TAG, waiting = false, onClick = onClose)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .testTag(ARRIVAL_UNDO_TAG)
                .heightIn(min = 44.dp)
                .pressable(Role.Button, onClick = onUndo)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(ArrivalCopy.UNDO, color = Muted, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}
