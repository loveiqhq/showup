/*
 * ProfilePhotosScreen.kt
 * ShowUp · Profile creation 06 — Photos (SHOWUP-156)
 *
 * The first screen in the flow that asks the user to GIVE something rather than type it, and the
 * first that scrolls. Seven states, one component, one layout column:
 *
 *   A · empty              nothing added yet
 *   B · some added         2 of 6, the drag hint visible
 *   C · uploading + failed 1 confirmed, 1 in flight, 1 failed -- and the count still reads 1
 *   D · library blocked    the grid is REPLACED by the access card         Android <= 12 only
 *   E · library can ask    the same card, one sentence and one label apart  Android <= 12 only
 *   F · source sheet       what "+" opens, over the untouched grid
 *   G · source sheet       camera permanently denied: one quiet row, nothing else changed
 *
 * A, B and C share one layout column exactly. D and E replace the grid. F and G overlay the sheet
 * on an otherwise untouched screen, scrim included, so the grid stays visible behind it.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE THREE RULES THAT MOST OF THIS FILE IS ABOUT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * EVERY SLOT IS 158 TALL IN EVERY STATE. Filled, empty, uploading, failed. It is what stops the
 * grid reflowing mid-upload, what keeps the CTA at the same scroll offset, and -- because it makes
 * the grid's geometry a constant -- what makes [reorderTarget] a closed form rather than a
 * position map rebuilt on every frame.
 *
 * THE COUNT ONLY ADVANCES ON A CONFIRMED UPLOAD. State C is the proof: three slots look occupied
 * and the count reads "1 of 6". See [PhotoGridState.confirmedCount].
 *
 * CONTINUE IS NEVER DISABLED. Pressing it below four answers with the toast. A dead button cannot
 * say what is missing, and a disabled CTA means the rule is never read at all -- which is the same
 * argument, and the same rule, as every text-input screen in "The basics".
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT IS NOT HERE, AND MUST NOT BE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The permission alert, the system picker, the camera UI and the Settings page are the OS's. None
 * of them is drawn here, none is measured, and none has a copy string in [PhotosCopy]. What IS
 * ours is the state before the sheet appears (F) and the state after it returns (D, E, G).
 *
 * There is no limited-access state and no partial-library banner anywhere in this build. See
 * `PhotoAccess.kt` for why, at length -- it is a consequence of presenting the system picker, not
 * an omission.
 */
package com.showup.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.ComponentSizes
import com.showup.designsystem.Cream
import com.showup.designsystem.Danger
import com.showup.designsystem.DangerFg
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.Lavender
import com.showup.designsystem.LilacStops
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Neutral
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.Purple
import com.showup.designsystem.Radius
import com.showup.designsystem.Raised
import com.showup.designsystem.Spacing
import com.showup.designsystem.Subtle
import com.showup.designsystem.SuccessFg
import com.showup.tutorial.NextButton
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline

/**
 * Copy — final strings. Every one is quoted by `audit/verify-profile.py`.
 *
 * THE REQUIREMENT IS STATED THREE TIMES ON PURPOSE: as a sentence in [SUB], as a number in the
 * count row, and in [TOAST_DEFAULT] on a refused press. The ticket says not to remove any of the
 * three, and the reason is that each answers a different question -- what am I being asked, where
 * am I up to, and why did nothing happen.
 */
internal object PhotosCopy {
    const val HEADLINE_LEAD = "The "
    const val HEADLINE_EM = "messy hair"
    const val HEADLINE_TAIL = ", the loud laugh."
    const val SUB =
        "Show who you actually are — not a polished version. Four photos to continue, " +
            "six if you've got them."

    const val COUNT_LABEL = "Photos · $PHOTOS_REQUIRED required, $PHOTOS_MAX max"
    fun countValue(filled: Int) = "$filled of $PHOTOS_MAX"

    /**
     * What each slot asks for. Four categories, then two of `Anything you like`.
     *
     * Named kinds rather than "Add a photo" four times, because four identical prompts get four
     * versions of the same photo -- which is the failure this screen exists to avoid.
     */
    val SLOT_HINTS = listOf(
        "Portrait",
        "With friends",
        "Full body",
        "Favourite activity",
        "Anything you like",
        "Anything you like",
    )
    const val FIRST_SLOT_CTA_HINT = "Start with your face"

    const val OPTIONAL_DIVIDER = "Optional · slots 5 & 6"
    const val ADD_MORE = "Add more"
    const val REORDER_HINT = "Drag to reorder · the first one is your main photo"
    const val MAIN_BADGE = "MAIN"
    const val CTA = "Continue"

    const val UPLOADING = "Uploading…"
    const val UPLOAD_FAILED = "Upload failed"
    const val RETRY = "Retry"

    // ── the access card, one card and two modes ─────────────────────────────
    const val ACCESS_TITLE = "Photo access is needed to continue"

    /**
     * The blocked body, in three parts so the row's name can be set in bold.
     *
     * `Photos` IS THE PLATFORM'S OWN LABEL FOR THE ROW, not a product term, and the ticket says to
     * replace it with whatever the running version calls it rather than hard-coding this string.
     * It is one value here because neither platform lets us deep-link to a single toggle -- the
     * user lands on a list and has to find the row, which is the whole reason it is named at all.
     */
    const val ACCESS_BLOCKED_LEAD = "Show Up can't see your photos, so there is nothing to add. " +
        "Open Settings, turn on "
    const val ACCESS_BLOCKED_ROW = "Photos"
    const val ACCESS_BLOCKED_TAIL =
        ", then come straight back — nothing you have entered is lost."
    const val ACCESS_ASK_BODY =
        "Allow access so you can pick your photos. Nothing you have entered is lost."
    const val ACCESS_BUTTON_BLOCKED = "Open Settings"
    const val ACCESS_BUTTON_ASK = "Allow photo access"

    // ── the source sheet ────────────────────────────────────────────────────
    const val SHEET_TITLE = "Add a photo"
    const val SHEET_LIBRARY = "Choose from library"
    const val SHEET_LIBRARY_SUB = "Pick one or more"
    const val SHEET_CAMERA = "Take a photo"
    const val SHEET_CAMERA_SUB = "Use the camera now"
    const val SHEET_CAMERA_BLOCKED_SUB =
        "Camera access is off. Turn on Camera in Settings to use it."
    const val SHEET_CAMERA_SETTINGS = "Settings"

    // ── the toast, three variants ───────────────────────────────────────────
    const val TOAST_DEFAULT = "Upload at least $PHOTOS_REQUIRED photos to continue"
    const val TOAST_BLOCKED = "Turn on Photos in Settings to continue"
    const val TOAST_ASK = "Allow photo access to continue"

    /** Spoken by TalkBack on a filled slot's remove pip, which is a Canvas with no text. */
    fun removeLabel(hint: String) = "Remove $hint"
}

// ── shared drawing ──────────────────────────────────────────────────────────

/**
 * A dashed rounded outline.
 *
 * THE DASH PATTERN IS OURS AND THE REFERENCE DOES NOT SET IT. CSS `border: 1.5px dashed` leaves
 * the dash length and gap entirely to the user agent -- Chrome, Safari and Firefox each pick
 * differently -- so there is no number in the handoff to copy, and the PNG cannot be measured
 * because the PNG wins on nothing.
 *
 * 6 on / 4 off at a 1.5 stroke reads as dashed rather than dotted at arm's length on a 158-tall
 * card, and is short enough that the corners of a 20 radius still look dashed rather than solid.
 * The same two numbers are used on iOS, so the two platforms agree by construction rather than by
 * each guessing.
 */
private fun Modifier.dashedOutline(
    color: Color,
    radius: Dp,
    width: Dp = 1.5.dp,
): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        // Inset by half the stroke so the outline sits INSIDE the slot's bounds. Without this the
        // dash straddles the edge, and the grid's 12 gap reads as 10.5.
        topLeft = Offset(w / 2f, w / 2f),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = w,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        ),
    )
}

/**
 * The slot radius.
 *
 * 20, and deliberately NOT [com.showup.designsystem.Radius.card], which is 16 and means "a card in
 * the sign-up flow". A photo slot is a picture rather than a card, drawn a little softer so a face
 * is not cropped by a tight corner. Local because it means "a photo slot" and nothing else.
 */
private val SlotRadius = 20.dp

/** Every slot, in every state. See [PHOTO_SLOT_HEIGHT] for why this is a constant and not content. */
private val SlotHeight = PHOTO_SLOT_HEIGHT.dp

// ── the slot ────────────────────────────────────────────────────────────────

/**
 * What a picked photo looks like while it is on screen.
 *
 * A neutral lilac block rather than a decoded image, for now. The ticket is explicit that
 * `PortraitPlaceholder` is placeholder art and that "the slots need real photography before any
 * user-facing build" -- and on a device this is where the picked image goes, at
 * [ContentScale.Crop], keyed on the photo's local id.
 *
 * Drawn rather than left blank because every state above it -- the dimming, the ring, the remove
 * pip, the MAIN badge -- is a treatment OVER an image, and a white rectangle would not show
 * whether any of them has enough contrast.
 */
@Composable
private fun PhotoFill(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.linearGradient(colorStops = LilacStops.toTypedArray()),
        ),
    )
}

/**
 * One tile in the grid. Four renderings, one height.
 *
 * The four are not variants of a card with different content: a failed slot has a different fill,
 * border, glyph and a control; an uploading one is an image under a scrim; an empty one is a
 * dashed target. What they share is [SlotHeight], and that is the thing that had to be shared.
 */
@Composable
private fun PhotoSlot(
    photo: PickedPhoto?,
    hint: String,
    optional: Boolean,
    cta: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onRemove: () -> Unit = {},
    onRetry: () -> Unit = {},
    isMain: Boolean = false,
) {
    when (photo?.status) {
        UploadStatus.Failed -> FailedSlot(modifier, onRetry)
        UploadStatus.InFlight -> UploadingSlot(modifier, photo.progress)
        UploadStatus.Confirmed -> FilledSlot(modifier, hint, isMain, onTap, onRemove)
        null -> EmptySlot(modifier, hint, optional, cta, onTap)
    }
}

/**
 * FAILED. Danger-tinted and never a solid red fill.
 *
 * The same restraint as the flow's inline error card, for the same reason: a solid red tile reads
 * as damage rather than as one upload that has to be tried again. The slot KEEPS ITS 158 so the
 * grid does not reflow, and Retry is a real control rather than hypertext -- it re-uploads into
 * this slot, so the user never re-picks.
 */
@Composable
private fun FailedSlot(modifier: Modifier, onRetry: () -> Unit) {
    Column(
        modifier
            .height(SlotHeight)
            .clip(RoundedCornerShape(SlotRadius))
            .background(Danger.copy(alpha = 0.06f))
            .dashedOutline(Danger.copy(alpha = 0.34f), SlotRadius)
            .padding(horizontal = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Danger),
            contentAlignment = Alignment.Center,
        ) {
            // AN ICON, NOT READING TEXT. See [UnscaledGlyph]: at 2x this became a 34sp "!" inside
            // a 34dp circle and clipped on every device in the matrix.
            UnscaledGlyph("!", 17.sp, Lora)
        }
        Text(
            PhotosCopy.UPLOAD_FAILED,
            color = DangerFg, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        // The pill DRAWS 32 and ANSWERS at 44. `requiredSizeIn` ignores the parent's constraint,
        // so the touch area overflows the pill symmetrically while the column still measures the
        // 32 the reference specifies -- the same trick, and the same reason, as the back chevron
        // in AppHeader. Growing the pill itself to 44 would open the gaps either side of it by 6.
        Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .requiredSizeIn(minHeight = ComponentSizes.minTapTarget)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onRetry),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    // A MINIMUM, not a fixed height. 32 is what the reference draws and what this
                    // is at every default-font size; at 2x a 12.5sp label needs about 34, and a
                    // fixed 32 clipped it on all seventeen devices. The slot's 158 has the room --
                    // the column is centred and its content comes to about 118 even at 2x.
                    Modifier
                        .heightIn(min = 32.dp)
                        .clip(CircleShape)
                        .background(Elevated)
                        .dashedOutlineSolid(Danger.copy(alpha = 0.34f))
                        .padding(horizontal = 15.dp, vertical = Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        PhotosCopy.RETRY,
                        color = DangerFg, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                    )
                }
            }
        }
    }
}

/** A solid 1.5 outline at the pill radius. Not dashed -- Retry is a real control, not a target. */
private fun Modifier.dashedOutlineSolid(color: Color): Modifier = drawBehind {
    val w = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2f, w / 2f),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(size.height / 2f),
        style = Stroke(width = w),
    )
}

/**
 * UPLOADING. The picked image, dimmed, under a DETERMINATE ring.
 *
 * Not a spinner on an empty slot, and that is the whole design of this state: the user has to be
 * able to see WHICH photo is in flight, which an empty tile with a spinner cannot say, and whether
 * it is moving, which an indeterminate spinner cannot say either.
 */
@Composable
private fun UploadingSlot(modifier: Modifier, progress: Float) {
    Box(
        modifier
            .height(SlotHeight)
            .clip(RoundedCornerShape(SlotRadius)),
    ) {
        PhotoFill(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .background(Fg.copy(alpha = 0.44f)),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A 38 disc swept to `progress` with a 28 disc over it -- which is the conic gradient
            // the reference draws, expressed as the two arcs it actually is. Drawn rather than
            // built from a CircularProgressIndicator, whose track, cap and stroke width are
            // Material's and none of which match.
            Canvas(Modifier.size(38.dp)) {
                val sweep = progress.coerceIn(0f, 1f) * 360f
                drawArc(Color.White, -90f, sweep, useCenter = true)
                drawArc(
                    Color.White.copy(alpha = 0.30f), -90f + sweep, 360f - sweep, useCenter = true,
                )
                val inner = 28.dp.toPx()
                drawCircle(
                    Fg.copy(alpha = 0.62f),
                    radius = inner / 2f,
                    center = Offset(size.width / 2f, size.height / 2f),
                )
            }
            Text(
                PhotosCopy.UPLOADING,
                color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}

/** FILLED. The image, a remove pip, and on slot 1 the MAIN badge. */
@Composable
private fun FilledSlot(
    modifier: Modifier,
    hint: String,
    isMain: Boolean,
    onTap: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(
        modifier
            .height(SlotHeight)
            .clip(RoundedCornerShape(SlotRadius))
            .clickable(role = Role.Button, onClick = onTap),
    ) {
        PhotoFill(Modifier.fillMaxSize())

        // REMOVE IS IMMEDIATE AND HAS NO CONFIRMATION. Re-adding is one tap; a dialog here is
        // friction on the screen with the most taps in the flow.
        //
        // The pip DRAWS 28 at top 8 / right 8 and ANSWERS at 44. Because 44 = 28 + 8 + 8, a 44 box
        // flush to the corner puts the drawn pip at exactly the offsets the reference specifies --
        // the hit area and the drawing agree without either being nudged.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(ComponentSizes.minTapTarget)
                .clickable(role = Role.Button, onClick = onRemove)
                .semantics { contentDescription = PhotosCopy.removeLabel(hint) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Muted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BrandIcon.Close, 14.dp, tint = Color.White, strokeWidth = 2.6.dp)
            }
        }

        if (isMain) {
            Text(
                PhotosCopy.MAIN_BADGE,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Spacing.md, bottom = Spacing.md)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                color = Fg, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp, letterSpacing = 0.06.em,
            )
        }
    }
}

/** EMPTY. Three tones: the first-slot call to action, a required slot, an optional one. */
@Composable
private fun EmptySlot(
    modifier: Modifier,
    hint: String,
    optional: Boolean,
    cta: Boolean,
    onTap: () -> Unit,
) {
    // The CTA tone is the lilac wash with a SOLID violet pip, so a completely empty grid has one
    // obvious place to start rather than four equal ones.
    val borderColor = when {
        cta -> Purple.copy(alpha = 0.46f)
        optional -> Fg.copy(alpha = 0.18f)
        else -> Purple.copy(alpha = 0.36f)
    }
    val pipBg = if (cta) Purple else Elevated
    val pipBorder = when {
        cta -> Color.Transparent
        optional -> Fg.copy(alpha = 0.12f)
        else -> Purple.copy(alpha = 0.20f)
    }
    val ink = when {
        cta -> Purple
        optional -> Subtle
        else -> Purple
    }
    val pipInk = if (cta) Color.White else ink

    Column(
        modifier
            // A FLOOR, and this is the one place the "every slot is 158" rule bends -- only for
            // the system font, and only upwards.
            //
            // The rule exists so the grid does not reflow BETWEEN STATES: a slot must not change
            // height when its photo starts uploading. It still cannot. What it may now do is be
            // taller than 158 on a phone whose type is set to 2x, where "Start with your face"
            // takes four lines under a 40 pip and simply does not fit in 158 -- the harness
            // measured 42px of it below the cut on the 320 x 686 cover screen. The row sizes both
            // of its cells to the taller of the two, so a row is still a row.
            //
            // At every default font size this resolves to exactly 158 and nothing moves.
            .heightIn(min = SlotHeight)
            .clip(RoundedCornerShape(SlotRadius))
            .then(
                if (cta) {
                    Modifier.background(Brush.linearGradient(colorStops = LilacStops.toTypedArray()))
                } else {
                    Modifier.background(Raised)
                },
            )
            .dashedOutline(borderColor, SlotRadius)
            .clickable(role = Role.Button, onClick = onTap)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(pipBg)
                .then(
                    if (pipBorder == Color.Transparent) {
                        Modifier
                    } else {
                        Modifier.dashedOutlineSolidCircle(pipBorder)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(BrandIcon.Plus, 20.dp, tint = pipInk, strokeWidth = 2.4.dp)
        }
        Text(
            hint,
            modifier = Modifier.widthIn(max = 130.dp),
            color = ink, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, lineHeight = (13f * 1.25f).sp, textAlign = TextAlign.Center,
        )
    }
}

/** A 1 hairline around a circular pip. */
private fun Modifier.dashedOutlineSolidCircle(color: Color): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawCircle(color, radius = (size.minDimension - w) / 2f, style = Stroke(width = w))
}

// ── the count row ───────────────────────────────────────────────────────────

/**
 * The 0 -> 6 scale, stated plainly: what is required, what is allowed, and where the user stands.
 *
 * The numeral turning [SuccessFg] at four is THE ONLY SUCCESS AFFORDANCE ON THE SCREEN. There is
 * no tick, no banner and no colour change on the CTA, because the CTA is never disabled and
 * therefore never changes state -- so this one numeral carries the whole "you are done" signal.
 */
@Composable
private fun PhotoCount(filled: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // NO maxLines. The ticket asks for one line at 390 (`white-space: nowrap`) and it is one
        // line at every width in the matrix at the default font size. At the largest accessibility
        // size it is not, and `maxLines = 1` made that an ELLIPSIS on the narrowest phone --
        // "Photos · 4 required, 6 m…" -- which cuts the maximum out of the sentence that states
        // it. nowrap describes the 1x layout; it is not a promise to somebody using large type.
        Text(
            PhotosCopy.COUNT_LABEL,
            modifier = Modifier.weight(1f, fill = false),
            color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
            fontSize = 10.5.sp, letterSpacing = 0.08.em,
        )
        Text(
            PhotosCopy.countValue(filled),
            color = if (filled >= PHOTOS_REQUIRED) SuccessFg else Fg,
            fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        )
    }
}

// ── the access card, one card and two modes ─────────────────────────────────

/**
 * D and E. THE GRID IS REPLACED, not disabled.
 *
 * Six tappable slots that cannot open anything is the worst state a mandatory step can be in: the
 * user taps, nothing happens, and the screen has told them nothing. So the count row, the grid,
 * `Add more` and the reorder hint are all absent here.
 *
 * LILAC, NOT DANGER. The user made a choice; they did not make a mistake.
 *
 * ONE CARD, TWO MODES, and collapsing them is the specific failure the ticket names: "how a screen
 * ends up telling a user to open Settings when it could simply have asked". The button's label
 * says what the button will actually do, and the app always knows which of the two it is in.
 */
@Composable
private fun AccessCard(
    canAsk: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier
            .padding(top = 18.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Lavender.copy(alpha = 0.12f))
            .dashedOutlineSolidCard(Purple.copy(alpha = 0.16f), 18.dp)
            .padding(horizontal = Spacing.xxl, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Purple),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BrandIcon.Lock, 17.dp, tint = Color.White, strokeWidth = 2.2.dp)
            }
            Column {
                Text(
                    PhotosCopy.ACCESS_TITLE,
                    color = Fg, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp, letterSpacing = (-0.01).em,
                )
                Text(
                    text = if (canAsk) {
                        buildAnnotatedString { append(PhotosCopy.ACCESS_ASK_BODY) }
                    } else {
                        buildAnnotatedString {
                            append(PhotosCopy.ACCESS_BLOCKED_LEAD)
                            // The row's name in bold, because the user has to FIND it: neither
                            // platform can deep-link to a single permission toggle, so they land
                            // on a list and scan.
                            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = Fg)) {
                                append(PhotosCopy.ACCESS_BLOCKED_ROW)
                            }
                            append(PhotosCopy.ACCESS_BLOCKED_TAIL)
                        }
                    },
                    modifier = Modifier.padding(top = Spacing.xs),
                    color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp, lineHeight = (13.5f * 1.45f).sp,
                )
            }
        }
        // Violet and not sunset: this is the user getting back to where they already were, not a
        // commitment beat. 48 / 15 is the reference's `size="md"`.
        PrimaryButton(
            label = if (canAsk) PhotosCopy.ACCESS_BUTTON_ASK else PhotosCopy.ACCESS_BUTTON_BLOCKED,
            onClick = if (canAsk) onAllow else onOpenSettings,
            variant = PrimaryButtonVariant.Violet,
            height = 48.dp,
            labelSize = 15.sp,
        )
    }
}

/** A 1 hairline around a rounded card. */
private fun Modifier.dashedOutlineSolidCard(color: Color, radius: Dp): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2f, w / 2f),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = w),
    )
}

// ── the source sheet ────────────────────────────────────────────────────────

/**
 * F and G. What "+" opens.
 *
 * A SLOT TAP DOES NOT OPEN THE PICKER DIRECTLY. It asks where the photo comes from, and this sheet
 * is the only place the camera can be offered: the library path needs no permission and the camera
 * path always does, so the two cannot share one control.
 *
 * THE SHEET IS OURS; WHAT IT LAUNCHES IS THE OS'S. The scrim dismisses it and the grid stays
 * visible behind it, because the user is choosing a source rather than leaving the screen.
 *
 * A DENIED CAMERA BLOCKS NOTHING. In [CameraAccess.Blocked] the camera row goes quiet and grows a
 * `Settings` pill; THE LIBRARY ROW IS UNTOUCHED and the step is still completable. That is why
 * camera denial is never a grid replacement and never a full-screen card -- the answer arrives
 * exactly where the question was asked.
 */
@Composable
private fun PhotoSourceSheet(
    camera: CameraAccess,
    onLibrary: () -> Unit,
    onCamera: () -> Unit,
    onCameraSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val blocked = camera == CameraAccess.Blocked
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Fg.copy(alpha = 0.42f))
                .clickable(
                    role = Role.Button,
                    onClick = onDismiss,
                )
                .semantics { contentDescription = "Dismiss" },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(Cream)
                // The sheet is a sibling of the scaffold rather than a child, so it carries no
                // inset of its own -- and its bottom padding is 16, which on a gesture-navigation
                // device would put the camera row under the bar. `safeDrawing` bottom is the
                // gesture inset here and the keyboard's if one ever opens over this sheet.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(start = Spacing.xxl, end = Spacing.xxl, top = Spacing.lg, bottom = Spacing.xxl),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 14.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Fg.copy(alpha = 0.18f))
                    .clearAndSetSemantics {},
            )
            Text(
                PhotosCopy.SHEET_TITLE,
                modifier = Modifier.padding(bottom = Spacing.xl),
                color = Fg, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp, letterSpacing = (-0.01).em,
            )

            SheetRow(
                icon = BrandIcon.Image,
                iconSize = 18.dp,
                pipBg = Purple,
                pipInk = Color.White,
                title = PhotosCopy.SHEET_LIBRARY,
                subtitle = PhotosCopy.SHEET_LIBRARY_SUB,
                onClick = onLibrary,
            )
            Spacer(Modifier.height(Spacing.lg))
            SheetRow(
                icon = if (blocked) BrandIcon.Lock else BrandIcon.Camera,
                iconSize = if (blocked) 16.dp else 18.dp,
                pipBg = if (blocked) Purple.copy(alpha = 0.14f) else Purple,
                pipInk = if (blocked) Purple else Color.White,
                title = PhotosCopy.SHEET_CAMERA,
                subtitle = if (blocked) {
                    PhotosCopy.SHEET_CAMERA_BLOCKED_SUB
                } else {
                    PhotosCopy.SHEET_CAMERA_SUB
                },
                quiet = blocked,
                trailingPill = if (blocked) PhotosCopy.SHEET_CAMERA_SETTINGS else null,
                onClick = if (blocked) onCameraSettings else onCamera,
            )
        }
    }
}

/** One row of the source sheet. 62 tall, radius 16, pip 38. */
@Composable
private fun SheetRow(
    icon: BrandIcon,
    iconSize: Dp,
    pipBg: Color,
    pipInk: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    quiet: Boolean = false,
    trailingPill: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // minHeight rather than height: the blocked camera row's subtitle is two lines on a
            // narrow phone, and a fixed 62 would clip it.
            .heightIn(min = 62.dp)
            .clip(RoundedCornerShape(Radius.card))
            .then(
                if (quiet) {
                    Modifier
                        .background(Lavender.copy(alpha = 0.10f))
                        .dashedOutlineSolidCard(Purple.copy(alpha = 0.16f), Radius.card)
                } else {
                    Modifier.background(Raised)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                start = 14.dp,
                end = if (quiet) Spacing.xl else 14.dp,
                top = if (quiet) 11.dp else 0.dp,
                bottom = if (quiet) 11.dp else 0.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(pipBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, iconSize, tint = pipInk, strokeWidth = 2.2.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            )
            Text(
                subtitle,
                modifier = Modifier.padding(top = 1.dp),
                color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp, lineHeight = (12.5f * 1.35f).sp,
            )
        }
        if (trailingPill != null) {
            Box(
                Modifier
                    .height(30.dp)
                    .clip(CircleShape)
                    .background(Elevated)
                    .dashedOutlineSolid(Purple.copy(alpha = 0.32f))
                    .padding(horizontal = Spacing.xl),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    trailingPill,
                    color = Purple, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                )
            }
        } else {
            Icon(BrandIcon.ChevronRight, 17.dp, tint = Fg, strokeWidth = 2.2.dp)
        }
    }
}


// ── the screen ──────────────────────────────────────────────────────────────

@Composable
fun ProfilePhotosScreen(
    state: PhotoGridState = PhotoGridState(),
    /** [LibraryAccess.NotNeeded] on every platform and version this app supports. See PhotoAccess.kt. */
    library: LibraryAccess = LibraryAccess.NotNeeded,
    camera: CameraAccess = CameraAccess.CanAsk,
    /** Whether the source sheet is up. Hoisted, so the screen stays a function of values. */
    sheetOpen: Boolean = false,
    onBack: () -> Unit = {},
    onSlotTap: (Int) -> Unit = {},
    onRemove: (Int) -> Unit = {},
    onRetry: (Int) -> Unit = {},
    onRevealOptional: () -> Unit = {},
    onReorder: (from: Int, to: Int) -> Unit = { _, _ -> },
    onChooseLibrary: () -> Unit = {},
    onChooseCamera: () -> Unit = {},
    onCameraSettings: () -> Unit = {},
    onDismissSheet: () -> Unit = {},
    onAllowLibrary: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onContinue: () -> Unit = {},
    /** Fires on the REFUSED press, never on render. */
    onRefused: () -> Unit = {},
    /**
     * Forces the refusal toast open so an artboard can show the treatment.
     *
     * **Previews and the fit harness only.** In the app the toast exists for 2.6 seconds after a
     * refused Continue and never on arrival. The reference file carries the same escape hatch, for
     * the same reason, and marks it artboard-only.
     */
    previewToast: Boolean = false,
) {
    val denied = library != LibraryAccess.NotNeeded
    val canAsk = library == LibraryAccess.CanAsk
    val filled = state.confirmedCount
    val toast = rememberRefusalToast()

    Box(Modifier.fillMaxSize()) {
        RealYouScaffold(
            step = RealYouStep.Photos,
            onBack = onBack,
            footer = {
                PhotosFooter(
                    toastVisible = toast.visible || previewToast,
                    toastMessage = when {
                        !denied -> PhotosCopy.TOAST_DEFAULT
                        canAsk -> PhotosCopy.TOAST_ASK
                        else -> PhotosCopy.TOAST_BLOCKED
                    },
                    onContinue = {
                        if (state.meetsMinimum) {
                            onContinue()
                        } else {
                            toast.show()
                            onRefused()
                        }
                    },
                )
            },
        ) {
            WashHeadline(
                parts = listOf(
                    PhotosCopy.HEADLINE_LEAD to false,
                    PhotosCopy.HEADLINE_EM to true,
                    PhotosCopy.HEADLINE_TAIL to false,
                ),
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 32.sp,
                lineHeight = (32f * 1.08f).sp,
                letterSpacing = (-0.018).em,
            )
            Text(
                PhotosCopy.SUB,
                modifier = Modifier.padding(top = Spacing.md).widthIn(max = 320.dp),
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp, lineHeight = (14.5f * 1.45f).sp,
            )

            if (denied) {
                AccessCard(canAsk = canAsk, onAllow = onAllowLibrary, onOpenSettings = onOpenSettings)
            } else {
                Box(Modifier.padding(top = Spacing.xxl)) { PhotoCount(filled) }

                PhotoGrid(
                    state = state,
                    first = 0,
                    count = PHOTOS_REQUIRED,
                    modifier = Modifier.padding(top = 14.dp),
                    onSlotTap = onSlotTap,
                    onRemove = onRemove,
                    onRetry = onRetry,
                    onReorder = onReorder,
                )

                if (state.optionalRevealed) {
                    Column(Modifier.padding(top = Spacing.xl)) {
                        Row(
                            Modifier.padding(top = 2.dp, bottom = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Text(
                                PhotosCopy.OPTIONAL_DIVIDER,
                                color = Subtle, fontFamily = Manrope,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.5.sp, letterSpacing = 0.08.em,
                            )
                            Box(Modifier.weight(1f).height(1.dp).background(BorderSoft))
                        }
                        PhotoGrid(
                            state = state,
                            first = PHOTOS_REQUIRED,
                            count = PHOTOS_MAX - PHOTOS_REQUIRED,
                            onSlotTap = onSlotTap,
                            onRemove = onRemove,
                            onRetry = onRetry,
                            onReorder = onReorder,
                        )
                    }
                } else {
                    // The ONLY way to reach slots 5-6 before the requirement is met. An outlined
                    // pill rather than hypertext, so it reads as a real control at the size of a
                    // tap target -- and it is 44, which is the floor rather than a coincidence.
                    Box(Modifier.fillMaxWidth().padding(top = 14.dp), Alignment.Center) {
                        Row(
                            Modifier
                                .height(ComponentSizes.minTapTarget)
                                .clip(CircleShape)
                                .background(Elevated)
                                .dashedOutlineSolid(Purple.copy(alpha = 0.36f))
                                .clickable(role = Role.Button, onClick = onRevealOptional)
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            // 16, between IconSizes.sm (20) and nothing smaller. A glyph inside a pill next to
                            // a 14.5 label, which neither icon token is sized for.
                            Icon(BrandIcon.Plus, 16.dp, tint = Purple, strokeWidth = 2.6.dp)
                            Text(
                                PhotosCopy.ADD_MORE,
                                color = Purple, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                            )
                        }
                    }
                }

                // FROM THE SECOND PHOTO, not the first: with one there is nothing to reorder.
                if (state.canReorder) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = Spacing.xl),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(BrandIcon.Sliders, 13.dp, tint = Subtle, strokeWidth = 2.dp)
                        Text(
                            PhotosCopy.REORDER_HINT,
                            color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // The tail spacer. 24 so the last row clears the footer's gradient mask rather than
            // ending underneath it -- the one place a fixed height inside the scroll is correct,
            // because it is a margin rather than a position.
            Spacer(Modifier.height(Spacing.screenGutter))
        }

        if (sheetOpen) {
            PhotoSourceSheet(
                camera = camera,
                onLibrary = onChooseLibrary,
                onCamera = onChooseCamera,
                onCameraSettings = onCameraSettings,
                onDismiss = onDismissSheet,
            )
        }
    }
}

/**
 * The sticky footer: a gradient mask, and Continue.
 *
 * The mask is what lets the scroll region end underneath the CTA without a hard edge -- content
 * fades into the canvas colour rather than being cut by it. `Continue` is orange, never sunset:
 * these are routine screens, and sunset is reserved for commitment beats.
 */
@Composable
private fun PhotosFooter(
    toastVisible: Boolean,
    toastMessage: String,
    onContinue: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.00f to Cream.copy(alpha = 0f),
                    0.22f to Cream.copy(alpha = 0.92f),
                    1.00f to Cream,
                ),
            )
            .padding(start = 20.dp, end = 20.dp, top = Spacing.xl),
    ) {
        RefusalToast(visible = toastVisible, icon = BrandIcon.Camera, message = toastMessage)

        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 52, not the 56 every other screen draws. Both "The real you" references pass
            // `size={52}`: the footer here is a band over scrolling content rather than the end of
            // a column, and the larger circle reads heavier against it.
            NextButton(PhotosCopy.CTA, onContinue, arrowSize = 22.dp, circleSize = 52.dp)
        }
    }
}

/**
 * A 2-column run of slots, with drag-to-reorder.
 *
 * `first` and `count` rather than a list, so the required four and the optional two are the same
 * component at two offsets rather than two grids that have to be kept in step.
 */
@Composable
private fun PhotoGrid(
    state: PhotoGridState,
    first: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onSlotTap: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onRetry: (Int) -> Unit,
    onReorder: (Int, Int) -> Unit,
) {
    val filled = state.confirmedCount
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        for (row in 0 until (count + 1) / 2) {
            // IntrinsicSize.Min so the two cells in a row match: a slot may grow past 158 at a
            // large system font, and a row with one tall cell and one short one would read as a
            // broken grid rather than a roomy one.
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                for (column in 0..1) {
                    val index = first + row * 2 + column
                    if (index >= first + count) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    PhotoSlot(
                        photo = state.at(index),
                        hint = slotHint(index, filled),
                        optional = isOptionalSlot(index),
                        cta = filled == 0 && index == 0,
                        isMain = index == 0 && filled > 0,
                        // aspectRatio is NOT used: the slot's height is a constant, and a ratio
                        // would make it depend on the column width and therefore on the device.
                        modifier = Modifier
                            .weight(1f)
                            .reorderable(
                                index = index,
                                enabled = state.canReorder && state.at(index) != null,
                                count = state.photos.size,
                                onReorder = onReorder,
                            ),
                        onTap = { onSlotTap(index) },
                        onRemove = { onRemove(index) },
                        onRetry = { onRetry(index) },
                    )
                }
            }
        }
    }
}

/**
 * Long-press and drag a slot to move it.
 *
 * WHY LONG PRESS AND NOT AN IMMEDIATE DRAG. The grid lives inside the screen's only scrolling
 * region, and a slot that started dragging on the first pixel of movement would steal every
 * vertical scroll that happened to begin on a photo — which, with six 158-tall tiles, is most of
 * the screen. `detectDragGesturesAfterLongPress` is what lets the same finger both scroll the
 * screen and move a photo.
 *
 * WHY THE ARITHMETIC IS NOT HERE. [reorderTarget] is a plain function over the cell size and the
 * gap, so every offset can be checked in a JVM test. What this modifier contributes is the three
 * things only a composable can: the measured cell size, the live translation, and raising the
 * dragged tile above its neighbours so it is not drawn underneath them.
 *
 * ONLY A FILLED SLOT DRAGS, and only once there are two. An empty slot has nothing to move, and
 * dragging the single first photo onto itself is the gesture doing nothing in an expensive way.
 */
@Composable
private fun Modifier.reorderable(
    index: Int,
    enabled: Boolean,
    count: Int,
    onReorder: (Int, Int) -> Unit,
): Modifier {
    if (!enabled) return this
    var cell by remember { mutableStateOf(IntSize.Zero) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val gapPx = with(LocalDensity.current) { Spacing.xl.toPx() }
    val dragging = drag != Offset.Zero
    return this
        .onSizeChanged { cell = it }
        // Above its neighbours while it moves, so the tile the user is holding is the one on top.
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer { translationX = drag.x; translationY = drag.y }
        .pointerInput(index, count, cell) {
            detectDragGesturesAfterLongPress(
                onDragEnd = {
                    val to = reorderTarget(
                        from = index,
                        dx = drag.x, dy = drag.y,
                        cellWidth = cell.width.toFloat(), cellHeight = cell.height.toFloat(),
                        gap = gapPx, count = count,
                    )
                    drag = Offset.Zero
                    if (to != index) onReorder(index, to)
                },
                onDragCancel = { drag = Offset.Zero },
                onDrag = { change, amount ->
                    change.consume()
                    drag += amount
                },
            )
        }
}

// ── previews: seven states x three frames ───────────────────────────────────
//
// The ticket's device matrix. 375 x 667 is checked first and is where the grid, the count row and
// the footer compete for height; 430 x 932 is where the whole grid fits above the fold.

private fun confirmed(n: Int) = List(n) { PickedPhoto(it.toLong(), null, UploadStatus.Confirmed) }

private val InFlightState = PhotoGridState(
    photos = listOf(
        PickedPhoto(0, null, UploadStatus.Confirmed),
        PickedPhoto(1, null, UploadStatus.InFlight, progress = 0.62f),
        PickedPhoto(2, null, UploadStatus.Failed),
    ),
)

@Preview(name = "A · empty · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PP_A375() { ProfilePhotosScreen() }

@Preview(name = "A · empty · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PP_A390() { ProfilePhotosScreen() }

@Preview(name = "B · partial · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PP_B390() { ProfilePhotosScreen(PhotoGridState(photos = confirmed(2))) }

@Preview(name = "B · partial · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PP_B430() { ProfilePhotosScreen(PhotoGridState(photos = confirmed(2))) }

@Preview(name = "C · uploading + failed · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PP_C390() { ProfilePhotosScreen(InFlightState) }

@Preview(name = "D · library blocked · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PP_D390() { ProfilePhotosScreen(library = LibraryAccess.Blocked) }

@Preview(name = "E · library can ask · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PP_E390() { ProfilePhotosScreen(library = LibraryAccess.CanAsk) }

@Preview(name = "F · source sheet · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PP_F390() {
    ProfilePhotosScreen(PhotoGridState(photos = confirmed(2)), sheetOpen = true)
}

@Preview(name = "G · camera blocked · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PP_G390() {
    ProfilePhotosScreen(
        PhotoGridState(photos = confirmed(2)),
        sheetOpen = true,
        camera = CameraAccess.Blocked,
    )
}

@Preview(name = "toast · refused · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PP_T375() { ProfilePhotosScreen(previewToast = true) }

@Preview(name = "six of six · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PP_S390() {
    ProfilePhotosScreen(PhotoGridState(photos = confirmed(6), optionalRevealed = true))
}
