/*
 * DateEndSheets.kt
 * ShowUp · B to J, the end-date and report sheets (SHOWUP-197), from `screen-wrap-report-reference.jsx`
 *
 * ONE MODAL SHELL. [DateEndSheetHost] keeps a single `ModalShell` up while the model's sheet is visible and swaps
 * the step inside it (the content crossfades and the height animates, as the arrival gate's does): the
 * reference draws one shell per step, which here would be a sheet dropping while another rises.
 *
 * SHARED STEPS, ONE COPY EACH. [NoShowGuide], [NoShowConfirm], [UncomfortableStep] and [EndErrorBox] are rendered
 * by BOTH [DateWrapSheet] and [ReportProblemSheet] (through one private dispatcher). A second copy is a bug.
 *
 * The screens take values and lambdas ([DateEndUi], [EndActions]); they never receive a navigator, so every
 * frame renders in every state at every size with no device.
 *
 * Numbers are the reference's, with CSS's border-in-the-box counted into the padding where Compose only draws a
 * border (a row's 14 + its 1 or 1.5 border), the line boxes as `cssLineBox`, and Manrope's normal line height
 * left to the font (1.366).
 */
package com.showup.date.end

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.change.changeClock
import com.showup.date.DrawnSmall
import com.showup.date.pressable
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.ModalShell
import com.showup.designsystem.Motion
import com.showup.designsystem.Muted
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.Raised
import com.showup.designsystem.SheetErrorBox
import com.showup.designsystem.SheetPrimaryButton
import com.showup.designsystem.SheetSecondaryButton
import com.showup.designsystem.ShowUpEasing
import com.showup.designsystem.SuccessFg
import com.showup.designsystem.blurBehindModalShell
import com.showup.designsystem.cssLineBox
import com.showup.designsystem.prettyWrap
import com.showup.designsystem.rememberMotion
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import java.time.ZoneId

const val END_SHEET_TAG = "end:sheet"
const val END_CIRCLE_TAG = "end:circle"
const val END_PICK_MET_TAG = "end:pick-met"
const val END_PICK_NO_SHOW_TAG = "end:pick-no-show"
const val END_PICK_UNCOMFORTABLE_TAG = "end:pick-uncomfortable"
const val END_CANCEL_TAG = "end:cancel"
const val END_BACK_TAG = "end:back"
const val END_CTA_TAG = "end:cta"
const val END_SECONDARY_TAG = "end:secondary"
const val END_MESSAGE_TAG = "end:message"
const val END_GUIDE_END_TAG = "end:guide-end"
const val END_ERROR_TAG = "end:error"
const val END_STAY_TAG = "end:stay"
const val END_NOTE_TAG = "end:note"

// The kit's amber for the caution row, as ChangeSheets and the Date screen's change card draw it.
private val Amber = Color(0xFFF59E0B)
private val AmberBorder = Color(0xFFD97706)
private val AmberIcon = Color(0xFFB45309)
private val AmberTitle = Color(0xFF92400E)

/** Every tap the sheets can send. The host binds them to the model; a test records them. */
data class EndActions(
    val onPick: (EndOption) -> Unit = {},
    val onBack: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onConfirmWait: () -> Unit = {},
    val onMessage: () -> Unit = {},
    val onEnd: () -> Unit = {},
    val onStay: () -> Unit = {},
)

private val SheetTitle = TextStyle(
    fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = (26 * 1.1).sp,
    letterSpacing = (-0.01).em, color = Fg,
)
private val BodyStyle = TextStyle(fontFamily = Manrope, fontSize = 14.sp, lineHeight = (14 * 1.5).sp, color = Muted)
private val StepStyle = TextStyle(fontFamily = Manrope, fontSize = 13.5.sp, lineHeight = (13.5 * 1.4).sp, color = Fg)
private val GuideStyle = TextStyle(fontFamily = Manrope, fontSize = 13.5.sp, lineHeight = (13.5 * 1.45).sp, color = Fg)
private val NoteStyle = TextStyle(fontFamily = Manrope, fontSize = 12.sp, lineHeight = (12 * 1.45).sp, color = Muted)
private val RowTitle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.5.sp, lineHeight = (15.5 * 1.25).sp, color = Fg)
private val RowSub = TextStyle(fontFamily = Manrope, fontSize = 12.5.sp, lineHeight = (12.5 * 1.4).sp, color = Muted)
private val Label = TextStyle(
    fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, letterSpacing = 0.08.sp, color = FgSubtle,
)

private fun bold(color: Color? = null) = SpanStyle(fontWeight = FontWeight.Bold, color = color ?: Color.Unspecified)

/** One text block as the reference sets it: a pretty-wrapped CSS line box. */
@Composable
private fun EndText(text: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier, centered: Boolean = false) {
    Text(
        text,
        modifier = modifier.prettyWrap(text, style, centered = centered).cssLineBox(style.lineHeight),
        style = if (centered) style.copy(textAlign = TextAlign.Center) else style,
    )
}

// ── the shell ───────────────────────────────────────────────────────────────────────────────────

/**
 * B to J while the model's sheet is visible, nothing otherwise. The scrim, back and swipe are the step's own
 * back: one step up, and on a menu a close (nothing is stored).
 */
@Composable
fun DateEndSheetHost(ui: DateEndUi, name: String, zone: ZoneId, actions: EndActions) {
    val route = ui.route
    if (!ui.visible || route == null) return
    val motion = rememberMotion()
    ModalShell(
        label = sheetLabel(route, ui.step, name),
        onDismiss = { if (ui.step == EndStep.Menu) actions.onClose() else actions.onBack() },
        contentPadding = PaddingValues(0.dp),
    ) {
        AnimatedContent(
            targetState = route to ui.step,
            modifier = Modifier.fillMaxWidth().testTag(END_SHEET_TAG),
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
            label = "end-sheet",
        ) { (shownRoute, shownStep) ->
            when (shownRoute) {
                EndRoute.Wrap -> DateWrapSheet(ui, shownStep, name, zone, actions)
                EndRoute.Report -> ReportProblemSheet(ui, shownStep, name, zone, actions)
            }
        }
    }
}

private fun sheetLabel(route: EndRoute, step: EndStep, name: String): String = when (step) {
    EndStep.Menu -> if (route == EndRoute.Wrap) DateEndCopy.WRAP_TITLE.format(name) else DateEndCopy.H_TITLE
    EndStep.Met -> DateEndCopy.C_TITLE
    EndStep.Guide -> DateEndCopy.GUIDE_TITLE
    EndStep.NoShow -> DateEndCopy.G_TITLE
    EndStep.Uncomfortable -> DateEndCopy.I_TITLE
}

/** The screen behind the sheets, blurred as `ModalShell`'s backdrop does, arriving with the sheet. */
@Composable
fun DateEndOver(visible: Boolean, content: @Composable () -> Unit, sheets: @Composable () -> Unit) {
    val motion = rememberMotion()
    val blur by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (motion.enabled) Motion.SHEET else 0),
        label = "end-sheet-blur",
    )
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().blurBehindModalShell(blur)) { content() }
        sheets()
    }
}

// ── the two sheets ──────────────────────────────────────────────────────────────────────────────

/** B, C and the steps they share with H. */
@Composable
fun DateWrapSheet(ui: DateEndUi, step: EndStep, name: String, zone: ZoneId, actions: EndActions) {
    when (step) {
        EndStep.Menu -> WrapMenu(name, actions)
        EndStep.Met -> WrapConfirm(
            icon = BrandIcon.Sparkles, iconTint = SuccessFg.copy(alpha = 0.14f), iconColor = SuccessFg,
            title = DateEndCopy.C_TITLE,
            body = AnnotatedString(DateEndCopy.C_BODY_1.format(name) + "\n" + DateEndCopy.C_BODY_2),
            steps = listOf(BrandIcon.Star to DateEndCopy.C_STEP_1, BrandIcon.Search to DateEndCopy.C_STEP_2),
            note = buildAnnotatedString {
                append(DateEndCopy.C_NOTE_1)
                withStyle(bold(Fg)) { append(DateEndCopy.C_NOTE_BOLD_1) }
                append(DateEndCopy.C_NOTE_2)
                withStyle(bold(Fg)) { append(DateEndCopy.C_NOTE_BOLD_2) }
                append(DateEndCopy.C_NOTE_3)
            },
            cta = DateEndCopy.C_CTA, secondary = DateEndCopy.CANCEL,
            ui = ui, onCta = actions.onEnd, onBack = actions.onBack,
        )
        else -> SharedStep(ui, step, name, zone, actions)
    }
}

/** H, and the steps it shares with B. Two options: *I'm in danger* and *Something else* are parked. */
@Composable
fun ReportProblemSheet(ui: DateEndUi, step: EndStep, name: String, zone: ZoneId, actions: EndActions) {
    when (step) {
        EndStep.Menu -> ReportMenu(actions)
        else -> SharedStep(ui, step, name, zone, actions)
    }
}

/** The steps both sheets render: one copy each. */
@Composable
private fun SharedStep(ui: DateEndUi, step: EndStep, name: String, zone: ZoneId, actions: EndActions) {
    when (step) {
        EndStep.Guide -> NoShowGuide(
            name = name, pastMeet = ui.pastMeet, meetTime = ui.date?.let { changeClock(it.meetAt, zone) }.orEmpty(),
            chatOpen = ui.chatOpen, onBack = actions.onBack, onMessage = actions.onMessage, onEnd = actions.onConfirmWait,
        )
        EndStep.NoShow -> NoShowConfirm(name, ui, actions.onEnd, actions.onBack)
        EndStep.Uncomfortable -> UncomfortableStep(ui, actions.onBack, actions.onEnd, actions.onStay)
        EndStep.Menu, EndStep.Met -> Unit
    }
}

// ── menus ───────────────────────────────────────────────────────────────────────────────────────

/** B: padding 14 20 22; the title (4 above, 18 below), two rows gap 10, `Cancel` 14 under them. */
@Composable
private fun WrapMenu(name: String, actions: EndActions) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 22.dp)) {
        Spacer(Modifier.height(4.dp))
        SheetHeading(DateEndCopy.WRAP_TITLE.format(name))
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuRow(
                tag = END_PICK_MET_TAG, icon = BrandIcon.Sparkles, iconSize = 22.dp, stroke = 2.4f,
                tile = SuccessFg.copy(alpha = 0.12f), tint = SuccessFg,
                title = DateEndCopy.MET_TITLE, sub = DateEndCopy.MET_SUB,
                borderColor = Purple, borderWidth = 1.5.dp, onClick = { actions.onPick(EndOption.Met) },
            )
            MenuRow(
                tag = END_PICK_NO_SHOW_TAG, icon = BrandIcon.Hourglass, iconSize = 22.dp, stroke = 2.1f,
                tile = Orange.copy(alpha = 0.12f), tint = Orange,
                title = DateEndCopy.NO_SHOW_TITLE, sub = DateEndCopy.NO_SHOW_SUB,
                onClick = { actions.onPick(EndOption.NoShow) },
            )
        }
        Spacer(Modifier.height(14.dp))
        SheetSecondaryButton(
            DateEndCopy.CANCEL, actions.onClose, Modifier.testTag(END_CANCEL_TAG), minHeight = 52.dp, fontSize = 15.sp,
        )
    }
}

/** H: the title (4 above, 4 below), the line (18 below), two rows gap 10, `Cancel` 14 under them. */
@Composable
private fun ReportMenu(actions: EndActions) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 22.dp)) {
        Spacer(Modifier.height(4.dp))
        SheetHeading(DateEndCopy.H_TITLE)
        Spacer(Modifier.height(4.dp))
        EndText(AnnotatedString(DateEndCopy.H_LINE), BodyStyle.copy(lineHeight = (14 * 1.45).sp))
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuRow(
                tag = END_PICK_NO_SHOW_TAG, icon = BrandIcon.Hourglass, iconSize = 22.dp, stroke = 2.1f,
                tile = Raised, tint = Purple,
                title = DateEndCopy.NO_SHOW_TITLE, sub = DateEndCopy.NO_SHOW_SUB,
                onClick = { actions.onPick(EndOption.NoShow) },
            )
            MenuRow(
                tag = END_PICK_UNCOMFORTABLE_TAG, icon = BrandIcon.Hand, iconSize = 22.dp, stroke = 2.1f,
                tile = Amber.copy(alpha = 0.16f), tint = AmberIcon, titleColor = AmberTitle,
                fill = Amber.copy(alpha = 0.07f), borderColor = AmberBorder.copy(alpha = 0.32f),
                title = DateEndCopy.UNCOMFORTABLE_TITLE, sub = DateEndCopy.UNCOMFORTABLE_SUB,
                onClick = { actions.onPick(EndOption.Uncomfortable) },
            )
        }
        Spacer(Modifier.height(14.dp))
        SheetSecondaryButton(
            DateEndCopy.CANCEL, actions.onClose, Modifier.testTag(END_CANCEL_TAG),
            minHeight = 52.dp, fontSize = 15.sp, textColor = Fg,
        )
    }
}

@Composable
private fun SheetHeading(text: String) {
    val t = AnnotatedString(text)
    Text(t, Modifier.semantics { heading() }.prettyWrap(t, SheetTitle).cssLineBox(SheetTitle.lineHeight), style = SheetTitle)
}

/**
 * A menu row: padding 14 (plus its 1 or 1.5 px border), radius 18, gap 14; the tile 46 (radius 13), the title
 * 15.5 / 700 / 1.25 over the sub 12.5 / 1.4 (3 below), and `chevron-right` 18 / 2.2.
 */
@Composable
private fun MenuRow(
    tag: String,
    icon: BrandIcon,
    iconSize: Dp,
    stroke: Float,
    tile: Color,
    tint: Color,
    title: String,
    sub: String,
    onClick: () -> Unit,
    titleColor: Color = Fg,
    fill: Color = Elevated,
    borderColor: Color = BorderSoft,
    borderWidth: Dp = 1.dp,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(tag)
            .pressable(Role.Button, onClick = onClick)
            .background(fill, shape)
            .border(borderWidth, borderColor, shape)
            .padding(14.dp + borderWidth),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).background(tile, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, iconSize, tint = tint, strokeWidth = stroke)
        }
        Column(Modifier.weight(1f)) {
            EndText(AnnotatedString(title), RowTitle.copy(color = titleColor))
            Spacer(Modifier.height(3.dp))
            EndText(AnnotatedString(sub), RowSub)
        }
        Icon(BrandIcon.ChevronRight, 18.dp, tint = FgSubtle, strokeWidth = 2.2f)
    }
}

// ── shared pieces ───────────────────────────────────────────────────────────────────────────────

/** The error box with its 14 below (`margin 0 0 14`). Date 07 passes its own [text]. */
@Composable
fun EndErrorBox(text: String = DateEndCopy.END_FAILED, modifier: Modifier = Modifier) {
    Column(modifier.testTag(END_ERROR_TAG)) {
        SheetErrorBox(text)
        Spacer(Modifier.height(14.dp))
    }
}

/** The small back link: `chevron-left` 16 / 2.4 and `Back` 13.5 / 700 in the subtle ink, drawn small, answering at 44. */
@Composable
fun WrapBack(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(bottom = 8.dp)) {
        DrawnSmall(label = DateEndCopy.BACK, onClick = onBack, touch = Modifier.testTag(END_BACK_TAG)) { press ->
            Row(
                press.padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(BrandIcon.ChevronLeft, 16.dp, tint = FgSubtle, strokeWidth = 2.4f)
                Text(DateEndCopy.BACK, color = FgSubtle, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        }
    }
}

/** The 72 hero circle, 4 above, with its icon. */
@Composable
private fun HeroCircle(icon: BrandIcon, tint: Color, color: Color, iconSize: Dp, stroke: Float) {
    Box(
        Modifier.padding(top = 4.dp).size(72.dp).testTag(END_CIRCLE_TAG).background(tint, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, iconSize, tint = color, strokeWidth = stroke) }
}

/** The centred title: Lora 700 26 / 1.1, 14 above and 8 below. */
@Composable
private fun CenteredTitle(text: String) {
    val t = AnnotatedString(text)
    Spacer(Modifier.height(14.dp))
    Text(
        t,
        Modifier.semantics { heading() }.prettyWrap(t, SheetTitle, centered = true).cssLineBox(SheetTitle.lineHeight),
        style = SheetTitle.copy(textAlign = TextAlign.Center),
    )
    Spacer(Modifier.height(8.dp))
}

/** The centred body: 14 / 1.5 muted, at most 300, 18 below. */
@Composable
private fun CenteredBody(text: AnnotatedString) {
    EndText(text, BodyStyle, Modifier.widthIn(max = 300.dp), centered = true)
    Spacer(Modifier.height(18.dp))
}

/**
 * The "What happens next" card: padding 14 15 (plus the 1 px border), radius 16, raised, the label (11.5 / 800,
 * +0.08, upper-case, 10 below) over rows of `icon` 17 / 2.1 (violet, 1 down) and text 13.5 / 1.4, 11 apart; 18 below.
 */
@Composable
private fun NextCard(steps: List<Pair<BrandIcon, String>>) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Raised, shape)
            .border(1.dp, BorderSoft, shape)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Text(DateEndCopy.WHAT_NEXT.uppercase(), style = Label)
        Spacer(Modifier.height(10.dp))
        steps.forEachIndexed { i, (icon, text) ->
            if (i > 0) Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 1.dp)) { Icon(icon, 17.dp, tint = Purple, strokeWidth = 2.1f) }
                EndText(AnnotatedString(text), StepStyle, Modifier.weight(1f))
            }
        }
    }
    Spacer(Modifier.height(18.dp))
}

/**
 * The shared confirmation beat: hero icon, title, body, "What happens next", an optional note, the error box
 * above the CTA, one sunset CTA (h 56) and the secondary under it (10 below). Both buttons are inert while the
 * end is in flight; the CTA then sits at alpha .6.
 */
@Composable
fun WrapConfirm(
    icon: BrandIcon,
    iconTint: Color,
    iconColor: Color,
    title: String,
    body: AnnotatedString,
    steps: List<Pair<BrandIcon, String>>,
    cta: String,
    secondary: String,
    ui: DateEndUi,
    onCta: () -> Unit,
    onBack: () -> Unit,
    note: AnnotatedString? = null,
) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 24.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            HeroCircle(icon, iconTint, iconColor, 30.dp, 2.2f)
            CenteredTitle(title)
            CenteredBody(body)
        }
        NextCard(steps)
        if (note != null) {
            val shape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag(END_NOTE_TAG)
                    .background(Orange.copy(alpha = 0.07f), shape)
                    .border(1.dp, Orange.copy(alpha = 0.28f), shape)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.padding(top = 1.dp)) { Icon(BrandIcon.ShieldAlert, 15.dp, tint = Orange, strokeWidth = 2.2f) }
                EndText(note, NoteStyle, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
        }
        if (ui.failed) EndErrorBox()
        SheetPrimaryButton(cta, onCta, Modifier.testTag(END_CTA_TAG), waiting = ui.ending)
        Spacer(Modifier.height(10.dp))
        SheetSecondaryButton(secondary, onBack, Modifier.testTag(END_SECONDARY_TAG), inert = ui.ending)
    }
}

/**
 * E and F, "Not here yet?": the back link, the orange hourglass circle, the title, four numbered cards (gap 10,
 * 18 below) and the buttons. `Message {name}` is hidden while the chat is locked and the end button moves up.
 * The end button is disabled before the BINDING meet time and says when; from it, it opens G.
 */
@Composable
fun NoShowGuide(
    name: String,
    pastMeet: Boolean,
    meetTime: String,
    chatOpen: Boolean,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onEnd: () -> Unit,
) {
    val guide = listOf(
        buildAnnotatedString {
            withStyle(bold()) { append(DateEndCopy.GUIDE_1_BOLD.format(name)) }
            append(DateEndCopy.GUIDE_1)
        },
        buildAnnotatedString {
            append(DateEndCopy.GUIDE_2_A)
            withStyle(bold(Fg)) { append(DateEndCopy.GUIDE_2_BOLD) }
            append(DateEndCopy.GUIDE_2_B)
        },
        AnnotatedString(DateEndCopy.GUIDE_3),
        buildAnnotatedString {
            append(DateEndCopy.GUIDE_4_A)
            withStyle(bold(Fg)) { append(DateEndCopy.GUIDE_4_BOLD) }
            append(DateEndCopy.GUIDE_4_B)
        },
    )
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 24.dp)) {
        WrapBack(onBack)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            HeroCircle(BrandIcon.Hourglass, Orange.copy(alpha = 0.12f), Orange, 30.dp, 2.2f)
            CenteredTitle(DateEndCopy.GUIDE_TITLE)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            guide.forEachIndexed { i, text ->
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Raised, shape)
                        .border(1.dp, BorderSoft, shape)
                        .padding(horizontal = 15.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(30.dp).background(Elevated, CircleShape).border(1.dp, BorderSoft, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", color = Orange, fontFamily = Lora, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    EndText(text, GuideStyle, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        if (messageShown(chatOpen)) {
            SheetPrimaryButton(
                DateEndCopy.MESSAGE.format(name), onMessage, Modifier.testTag(END_MESSAGE_TAG),
                leading = { Icon(BrandIcon.MessageCircle, 20.dp, tint = Color.White, strokeWidth = 2.2f) },
            )
            Spacer(Modifier.height(10.dp))
        }
        val end = guideEnd(pastMeet, meetTime)
        SheetSecondaryButton(
            end.label, onEnd, Modifier.testTag(END_GUIDE_END_TAG), locked = !end.enabled,
            leading = if (end.clock) ({ Icon(BrandIcon.Clock, 16.dp, tint = FgSubtle, strokeWidth = 2.2f) }) else null,
        )
    }
}

/** G, "Thanks for waiting": no verdict on the match, only the ender's own rate is promised. `Back` returns to the guide. */
@Composable
fun NoShowConfirm(name: String, ui: DateEndUi, onCta: () -> Unit, onBack: () -> Unit) {
    WrapConfirm(
        icon = BrandIcon.Hourglass, iconTint = Orange.copy(alpha = 0.12f), iconColor = Orange,
        title = DateEndCopy.G_TITLE,
        body = buildAnnotatedString {
            append(DateEndCopy.G_BODY_A.format(name))
            withStyle(bold(Fg)) { append(DateEndCopy.G_BODY_BOLD) }
        },
        steps = listOf(
            BrandIcon.Star to DateEndCopy.G_STEP_1,
            BrandIcon.ShieldCheck to DateEndCopy.G_STEP_2,
            BrandIcon.Search to DateEndCopy.G_STEP_3,
        ),
        cta = DateEndCopy.G_CTA, secondary = DateEndCopy.BACK,
        ui = ui, onCta = onCta, onBack = onBack,
    )
}

/** I, "I don't feel comfortable": `Head out now` ends the date, `Stay for now` stores a flag and closes. */
@Composable
fun UncomfortableStep(ui: DateEndUi, onBack: () -> Unit, onHeadOut: () -> Unit, onStay: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 24.dp)) {
        WrapBack(onBack)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            HeroCircle(BrandIcon.Heart, Purple.copy(alpha = 0.10f), Purple, 32.dp, 2.1f)
            CenteredTitle(DateEndCopy.I_TITLE)
            CenteredBody(
                buildAnnotatedString {
                    append(DateEndCopy.I_BODY_A)
                    withStyle(bold()) { append(DateEndCopy.I_BODY_BOLD_1) }
                    append(DateEndCopy.I_BODY_B)
                    withStyle(bold(Fg)) { append(DateEndCopy.I_BODY_BOLD_2) }
                },
            )
        }
        NextCard(
            listOf(
                BrandIcon.ShieldCheck to DateEndCopy.I_STEP_1,
                BrandIcon.Home to DateEndCopy.I_STEP_2,
                BrandIcon.Star to DateEndCopy.I_STEP_3,
            ),
        )
        if (ui.failed) EndErrorBox()
        SheetPrimaryButton(DateEndCopy.HEAD_OUT, onHeadOut, Modifier.testTag(END_CTA_TAG), waiting = ui.ending)
        Spacer(Modifier.height(10.dp))
        SheetSecondaryButton(DateEndCopy.STAY, onStay, Modifier.testTag(END_STAY_TAG), inert = ui.ending, textColor = Fg)
    }
}
