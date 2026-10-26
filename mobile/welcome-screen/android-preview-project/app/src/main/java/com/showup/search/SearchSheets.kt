/*
 * SearchSheets.kt
 * ShowUp · the like flow's sheets: Be sure (Search 01 callout 22, and its mutual mode, Search 02 15),
 * the optional message (callout 23), and the sorry sheet after a refused confirm (Search 02 18)
 *
 * Behaviour is `SheetScaffold`'s, the one bottom sheet in the app: the 42% scrim, a scrim tap as
 * `backdrop`, the swipe down, Android back as `system_back`, and a bottom inset that clears the
 * keyboard. The SURFACE is Search's own `ModalShell` -- 28 corners, at most 88% of the screen, the
 * canvas colour, a 36 grabber in `--liq-fg-faint`, an upward violet shadow -- because the design
 * draws two sheet surfaces and both stay (the profile flow's is 32 / 660 / 40).
 *
 * Dismissing either sheet sends nothing: every way out but `Continue`, `Skip message` and
 * `Send message` is the caller's cancel.
 */
package com.showup.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.showup.designsystem.cssLineBox
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.BlushWash
import com.showup.designsystem.Cream
import com.showup.designsystem.Elevated
import com.showup.designsystem.ButtonPair
import com.showup.designsystem.CssShadow
import com.showup.designsystem.PAIR_BUTTON_PADDING
import com.showup.designsystem.Faint
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Neutral
import com.showup.designsystem.Lora
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.PrimaryDeep
import com.showup.designsystem.Purple
import com.showup.designsystem.Raised
import com.showup.designsystem.SheetGrabber
import com.showup.designsystem.SheetScaffold
import com.showup.designsystem.ShadowInk
import com.showup.designsystem.roundedShadows
import com.showup.profile.SheetDismissMethod
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline

/**
 * `ModalShell`'s `backdrop-filter: blur(8px)`: sigma 8 as the radius `Modifier.blur` takes,
 * (8 - 0.5) / 0.57735, the conversion `Shadows.kt` documents.
 *
 * NOT `blurBehindSheet`, which is the profile flow's 1.5 -- "a softening" by its own spec. The design
 * draws two sheet surfaces with two backdrops; borrowing that one put this sheet over a screen that
 * was barely softened (the 184 evidence review, 24 Oct 2026). Robolectric does not render a
 * RenderEffect, so no screenshot here could have shown it. A no-op on Android 11, where the 42%
 * scrim alone separates the sheet, as with every blur in this app.
 */
private val SearchSheetBlur = 12.99.dp

/** The blur behind either like sheet, [fraction] 0 to 1 so it arrives with the sheet rather than snapping on. */
fun Modifier.blurBehindSearchSheet(fraction: Float): Modifier =
    if (fraction > 0f) blur(SearchSheetBlur * fraction) else this

/** `0 -8px 32px rgba(46,1,71,.18)` -- the sheet's shadow, cast upward over the screen. */
private val SheetLift = listOf(CssShadow(ShadowInk.copy(alpha = 0.18f), offsetY = (-8).dp, blur = 32.dp))


/** CSS `line-height: normal` for Lora: its hhea ascent, descent and gap, 1006 + 274 + 0 per 1000. */
private const val LORA_NORMAL_LINE = 1.28f

/** The kit's `Button size="md"`: 48 tall, 15 type. */
private val SHEET_BUTTON_HEIGHT = 48.dp

/**
 * Search's `ModalShell` surface inside the app's one sheet behaviour.
 *
 * @param endOnKeyboard while the keyboard is up, keep the sheet scrolled to its end. On a short
 *   phone the message sheet outgrows the space above the keys, and its two buttons would sit behind
 *   them until the user thought to scroll; held at the end, the field, the counter and both buttons
 *   show together. Pinned again only when the scroll range changes -- the keyboard rising, the field
 *   growing a line -- so a user who scrolls up to read keeps their place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchSheet(
    label: String,
    onDismiss: (SheetDismissMethod) -> Unit,
    endOnKeyboard: Boolean = false,
    /** The content's padding: 24 all round for the like sheets, `20 24 24` for the sorry sheet. */
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val keyboard = endOnKeyboard && WindowInsets.isImeVisible
    LaunchedEffect(keyboard) {
        if (keyboard) snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) }
    }
    SheetScaffold(onDismiss = onDismiss) {
        // The scaffold keeps the sheet above the gesture bar (or the keyboard); the kit's sheet runs
        // to the bottom edge, so its colour is painted down into that inset too -- otherwise a strip
        // of scrim shows under it on every gesture-navigation phone.
        val inset = WindowInsets.safeDrawing.getBottom(LocalDensity.current).toFloat()
        BoxWithConstraints(
            Modifier.drawBehind {
                drawRect(Cream, topLeft = Offset(0f, size.height - 1f), size = Size(size.width, inset + 1f))
            },
        ) {
            // `max-height: 88%` of the frame the sheet is docked in; past it the sheet scrolls.
            val cap = maxHeight * 0.88f
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = cap)
                    .roundedShadows(SolidColor(Cream), 28.dp, SheetLift)
                    // Square bottom corners: docked to the edge, so only the top two round.
                    .background(Cream, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .verticalScroll(scroll)
                    .semantics {
                        dialog()
                        paneTitle = label
                    },
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    SheetGrabber(width = 36.dp, color = Faint)
                }
                Column(Modifier.padding(contentPadding), content = content)
            }
        }
    }
}

/** The send mark on the sheets' primary buttons: `send` 16 / 2.2. */
@Composable
private fun SendMark() = Icon(BrandIcon.Send, 16.dp, tint = Color.White, strokeWidth = 2.2f)

/** Mutual Be sure's mark: `check` 16 / 2.2 (Search 02: "`Confirm date` + check icon"). */
@Composable
private fun CheckMark() = Icon(BrandIcon.Check, 16.dp, tint = Color.White, strokeWidth = 2.2f)

/**
 * Be sure (callout 22): what a match commits to, before anything is sent. The line names `today` or
 * `tomorrow` exactly as the meet card does. `Continue`, never "Send like": the like is sent from the
 * message sheet.
 *
 * MUTUAL (Search 02 15, on a profile that liked the viewer): `Confirm, and you and {name} have…`, the
 * CTA `Confirm date` with a check, and no message step -- [onContinue] is the confirm itself. While it
 * is with the server ([confirming]) the CTA stays drawn as it is and does nothing, and the sheet does
 * not close: the view model holds both.
 */
@Composable
fun BeSureSheet(
    name: String,
    day: String,
    onCancel: (SheetDismissMethod) -> Unit,
    onContinue: () -> Unit,
    mutual: Boolean = false,
    confirming: Boolean = false,
) {
    SearchSheet(label = SearchCopy.BE_SURE, onDismiss = onCancel) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(BlushWash, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // `<em>Be sure.</em>` -- the whole phrase is the italic run, washed, in --liq-primary.
            // No line height and no letter spacing in the reference: CSS `normal` -- Lora's own 1.28
            // (its hhea metrics) -- and 0, not WashHeadline's defaults of 1.05 and -0.02em.
            WashHeadline(
                parts = listOf(SearchCopy.BE_SURE to true),
                fontSize = 22.sp,
                lineHeight = (22 * LORA_NORMAL_LINE).sp,
                letterSpacing = 0.em,
                color = PrimaryDeep,
                textAlign = TextAlign.Center,
            )
            Text(
                buildAnnotatedString {
                    append((if (mutual) PremiumCopy.BE_SURE_MUTUAL_LEAD else SearchCopy.BE_SURE_LINE_LEAD).format(name))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Purple)) {
                        append(SearchCopy.BE_SURE_LINE_EMPHASIS.format(day))
                    }
                    append(".")
                },
                color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                lineHeight = (14 * 1.45).sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp).cssLineBox((14 * 1.45).sp),
            )
        }
        Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                SearchCopy.BE_SURE_SUB, color = Purple, fontFamily = Lora, fontStyle = FontStyle.Italic,
                fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            SearchCopy.BE_SURE_ROWS.forEach { (icon, text) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(28.dp).background(Raised, RoundedCornerShape(8.dp)).clearAndSetSemantics {},
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, 14.dp, tint = Purple, strokeWidth = 2.2f) }
                    Text(
                        text, color = Neutral, fontFamily = Manrope, fontSize = 13.sp, lineHeight = (13 * 1.45).sp,
                        modifier = Modifier.cssLineBox((13 * 1.45).sp),
                    )
                }
            }
        }
        ButtonPair(
            modifier = Modifier.padding(top = 22.dp),
            first = {
                PrimaryButton(
                    SearchCopy.CANCEL, { onCancel(SheetDismissMethod.Close) },
                    variant = PrimaryButtonVariant.Ghost, height = SHEET_BUTTON_HEIGHT, labelSize = 15.sp,
                    horizontalPadding = PAIR_BUTTON_PADDING,
                )
            },
            second = {
                PrimaryButton(
                    if (mutual) PremiumCopy.CONFIRM_DATE else SearchCopy.CONTINUE,
                    { if (!confirming) onContinue() },
                    // Inert while the server decides, and said so: a screen reader hears it waiting.
                    modifier = if (confirming) Modifier.semantics { stateDescription = CONFIRMING_STATE } else Modifier,
                    variant = PrimaryButtonVariant.Sunset, height = SHEET_BUTTON_HEIGHT, labelSize = 15.sp,
                    horizontalPadding = PAIR_BUTTON_PADDING, leading = { if (mutual) CheckMark() else SendMark() },
                )
            },
        )
    }
}

/** The sorry sheet's one button, for the fit harness. */
const val SORRY_BUTTON_TAG = "sorry-button"

/** What a screen reader hears on `Confirm date` while the confirm is with the server. Not drawn. */
private const val CONFIRMING_STATE = "Waiting for an answer"

/**
 * The sorry sheet (Search 02 18): a confirm the server refused -- the like closed first. Over the
 * profile, straight after Be sure; the person's photo and name keep them real, and the copy names the
 * timing so it never reads as a fake profile.
 *
 * Padding `20 24 24`, a centred column with gap 14: the photo 96, round, grayscale .6 at .85 with the
 * reason pill over its bottom edge (a 96 x 108 box) -> the headline (Lora 700 / 24 / 1.2, `-0.01em`,
 * the reason's last words in italic with the wash; 4 more above it) -> the body (14 / 1.5, at most
 * 300) -> the violet line (700 / 14 / 1.45) -> one full-width sunset button, 6 more above it. Its
 * height is its content.
 *
 * Every way out -- [buttonLabel] (`Find someone else` on the grid, `Back to likes` on the list), the
 * scrim, a swipe down, Android back -- is the same [onDone], and fires nothing.
 */
@Composable
fun ConfirmRefusedSheet(
    name: String,
    photo: String,
    reason: CloseReason,
    buttonLabel: String,
    onDone: () -> Unit,
) {
    SearchSheet(
        label = "${reason.pill}. $name",
        onDismiss = { onDone() },
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 96 wide as drawn -- and wider only when the pill is, at a large type, so the reason is
            // never cut to fit under the photo.
            Box(Modifier.height(108.dp).clearAndSetSemantics {}, contentAlignment = Alignment.TopCenter) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .cssGrayscale(0.6f)
                        .graphicsLayer { alpha = 0.85f },
                ) { PhotoBackdrop(photo, 0, Modifier.fillMaxSize()) }
                PillLabel(reason.pill, Modifier.align(Alignment.BottomCenter))
            }
            // `text-wrap: balance` on the headline, `pretty` on the two paragraphs, as the reference
            // sets them: `We're really sorry,` / `Leonie confirmed a` / `date just before you did.`.
            WashHeadline(
                parts = listOf(reason.sorryHeadLead(name) to false, reason.sorryEmphasis to true),
                fontSize = 24.sp,
                lineHeight = (24 * 1.2).sp,
                letterSpacing = (-0.01).em,
                color = Fg,
                textAlign = TextAlign.Center,
                balance = true,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
            PrettyText(
                reason.sorryBody(name),
                TextStyle(color = Neutral, fontFamily = Manrope, fontSize = 14.sp, lineHeight = (14 * 1.5).sp, textAlign = TextAlign.Center),
                Modifier.widthIn(max = 300.dp),
                centered = true,
            )
            PrettyText(
                PremiumCopy.SORRY_LINE,
                TextStyle(
                    color = Purple, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    lineHeight = (14 * 1.45).sp, textAlign = TextAlign.Center,
                ),
                Modifier.widthIn(max = 300.dp),
                centered = true,
            )
            PrimaryButton(
                buttonLabel, onDone,
                modifier = Modifier.padding(top = 6.dp).testTag(SORRY_BUTTON_TAG),
                variant = PrimaryButtonVariant.Sunset, height = SHEET_BUTTON_HEIGHT, labelSize = 15.sp,
                horizontalPadding = 22.dp,
            )
        }
    }
}

/**
 * The optional message (callout 23). The draft arrives pre-filled and is edited here; the field
 * never holds more than 100 (the view model cuts a paste), the counter is a polite live region, and
 * the text is never logged or tracked.
 */
@Composable
fun MessageSheet(
    name: String,
    draft: String,
    onDraft: (String) -> Unit,
    onDismiss: (SheetDismissMethod) -> Unit,
    onSkip: () -> Unit,
    onSend: () -> Unit,
) {
    SearchSheet(
        label = SearchCopy.MESSAGE_TITLE_EMPHASIS + SearchCopy.MESSAGE_TITLE_REST.format(name),
        onDismiss = onDismiss,
        endOnKeyboard = true,
    ) {
        WashHeadline(
            parts = listOf(SearchCopy.MESSAGE_TITLE_EMPHASIS to true, SearchCopy.MESSAGE_TITLE_REST.format(name) to false),
            fontSize = 26.sp,
            // CSS `normal`, Lora's own 1.28: at 375 the title takes two lines, and WashHeadline's 1.05
            // set them 6 closer than the browser does.
            lineHeight = (26 * LORA_NORMAL_LINE).sp,
            letterSpacing = (-0.01).em,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            SearchCopy.MESSAGE_BODY, color = Muted, fontFamily = Manrope, fontSize = 14.sp,
            lineHeight = (14 * 1.5).sp, modifier = Modifier.padding(bottom = 18.dp).cssLineBox((14 * 1.5).sp),
        )
        BasicTextField(
            value = draft,
            onValueChange = onDraft,
            textStyle = TextStyle(color = Fg, fontFamily = Manrope, fontSize = 15.sp, lineHeight = (15 * 1.5).sp),
            cursorBrush = SolidColor(Purple),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .background(Elevated, RoundedCornerShape(16.dp))
                .border(1.5.dp, Purple, RoundedCornerShape(16.dp))
                .padding(16.dp)
                .semantics { contentDescription = SearchCopy.MESSAGE_TITLE_EMPHASIS + SearchCopy.MESSAGE_TITLE_REST.format(name) },
        )
        // 10.5, not the reference's 6: its textarea is INLINE, and the browser leaves the line's
        // descender room under it -- 4.5 measured on the signed-off sheet (box to Skip, 45 there and
        // 40.5 here before this). The counter carries it so the buttons land where the sheet has them.
        Row(Modifier.fillMaxWidth().padding(top = 10.5.dp), horizontalArrangement = Arrangement.End) {
            Text(
                messageCounter(draft), color = FgSubtle, fontFamily = Manrope, fontSize = 12.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        ButtonPair(
            modifier = Modifier.padding(top = 18.dp),
            first = {
                PrimaryButton(
                    SearchCopy.SKIP_MESSAGE, onSkip,
                    variant = PrimaryButtonVariant.Ghost, height = SHEET_BUTTON_HEIGHT, labelSize = 15.sp,
                    horizontalPadding = PAIR_BUTTON_PADDING,
                )
            },
            second = {
                PrimaryButton(
                    SearchCopy.SEND_MESSAGE, onSend,
                    variant = PrimaryButtonVariant.Sunset, height = SHEET_BUTTON_HEIGHT, labelSize = 15.sp,
                    horizontalPadding = PAIR_BUTTON_PADDING, leading = { SendMark() },
                )
            },
        )
    }
}
