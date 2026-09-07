/*
 * TutorialShell.kt
 * ShowUp · the shared chrome for every tutorial screen (SHOWUP-135)
 *
 * Screen 2 ("Meet in real life") is the reference implementation of this chrome; screens 3-6 reuse
 * it unchanged and pass content only. Nothing screen-specific belongs in this file.
 *
 * The layout rule that matters: this is ONE top-anchored column with a SINGLE Spacer(weight = 1f)
 * between the illustration and the nav row. That spacer is the only thing that absorbs height
 * differences between screens and between devices. No Y position is ever hard-coded — offsets that
 * look right at 844 tall break on every other frame.
 *
 * The illustration is the only flexible element. On short frames it shrinks; the type, the nav row
 * and the safe-area margins never do, and the screen never scrolls.
 */
package com.showup.tutorial

import com.showup.designsystem.IconSizes
import com.showup.designsystem.Spacing

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Cream
import com.showup.welcome.ctaGlow
import com.showup.designsystem.EyebrowBg
import com.showup.designsystem.Faint
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.Subtle
import com.showup.designsystem.Track

/**
 * ② Step progress — 5 segments, height 5, gap 6, full content width.
 *
 * The segment colour animates rather than snapping, so advancing a card reads as progress being
 * made rather than as the bar being redrawn. It is one colour tween per segment, which costs
 * nothing and is skipped entirely when the device asks for no motion.
 *
 * Semantics: the bar is one node reporting "Step N of 5", not five anonymous boxes. Without this a
 * screen reader announces nothing at all here — the segments carry no text.
 */
@Composable
fun StepProgress(steps: Int, current: Int, modifier: Modifier = Modifier) {
    val motion = rememberMotion()
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo =
                    ProgressBarRangeInfo(current.toFloat(), 0f..steps.toFloat(), steps)
                contentDescription = "Step " + current + " of " + steps
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(steps) { i ->
            val target = if (i < current) Purple else Track
            val segment by animateColorAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = if (motion.enabled) 320 else 0),
                label = "segment",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(segment)
            )
        }
    }
}

/**
 * ③ Eyebrow pill — Manrope 700 / 11 / uppercase, tracking .08, padding 5/10.
 *
 * `align(Alignment.Start)` is load-bearing: in a Column the default stretches children to the full
 * content width, which is the full-width band the reference render shows. The design-system
 * component is a hug-width pill.
 *
 * CONFIRMED 2026-09-01, after this was queried a second time for looking too short next to the
 * render. It is short on purpose. The 04-thirty-minutes spec sheet says so in its own words:
 *
 *     "intended hug width (align-self flex-start) -- reference render still shows the
 *      full-width stretch, as on card 01"
 *
 * So the render disagreeing with us is the discrepancy the handoff already knows about, not a bug
 * on our side. Anyone comparing the two will notice it again; this note is here so the next person
 * spends a minute on it rather than an afternoon. Changing it means overruling the spec text, which
 * is the product side's call and is recorded in audit/CONFLICTS-2026-08-27.md as A10.
 */
@Composable
fun ColumnScope.EyebrowPill(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .align(Alignment.Start)
            .clip(RoundedCornerShape(50))
            .background(EyebrowBg)
            .padding(horizontal = Spacing.lg, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(Modifier.size(5.dp).background(Orange, CircleShape))
        Text(
            text.uppercase(),
            color = Purple, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.08.em,
        )
    }
}

/**
 * Which circle the nav row's forward action wears.
 *
 * A variant on the shared button, not a forked nav row — screen 6 is the only terminal screen, and
 * forking would mean its progress bar and Back slot drift from the other five.
 */
enum class NextVariant { Orange, Sunset }

/**
 * Whether this device wants motion.
 *
 * Android has no single "reduce motion" flag. The honest signal is the system animator duration
 * scale, which the OS sets to 0 when someone turns animations off — either in Accessibility >
 * Remove animations, or in Developer options. Respecting it keeps the flow usable for people who
 * get motion sick, and has the useful side effect of holding the screens still under UI tests.
 */
@Immutable
data class Motion(val enabled: Boolean)

@Composable
fun rememberMotion(): Motion {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        Motion(enabled = scale > 0f)
    }
}

/**
 * The spec's arrow — a 2px stroke with round caps, not a filled glyph.
 *
 * Material's `Icons.AutoMirrored.Filled.ArrowForward` is a solid shape with a different silhouette;
 * at 56dp against a saturated circle the difference is plainly visible, so the arrow is drawn.
 */
@Composable
private fun ArrowRight(size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        val midY = this.size.height / 2f
        val w = 2.dp.toPx()
        drawLine(
            Color.White, Offset(s * 0.10f, midY), Offset(s * 0.84f, midY),
            strokeWidth = w, cap = StrokeCap.Round,
        )
        drawPath(
            Path().apply {
                moveTo(s * 0.56f, midY - s * 0.25f)
                lineTo(s * 0.86f, midY)
                lineTo(s * 0.56f, midY + s * 0.25f)
            },
            Color.White,
            style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/**
 * ⑨ Next — label plus a 56dp circular arrow, gap 14.
 *
 * Card 05 is the only terminal card. Its circle takes the sunset gradient and a violet shadow, and
 * its arrow is 22 rather than 20 — both variants on this one button, never a forked nav row.
 */
@Composable
fun NextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NextVariant = NextVariant.Orange,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motion = rememberMotion()
    // 0.94 reads as a press without the circle appearing to shrink away from the finger.
    val scale by animateFloatAsState(
        targetValue = if (pressed && motion.enabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "nextPress",
    )
    val glow = if (variant == NextVariant.Sunset) Purple else Orange
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = interaction,
                indication = null,          // the scale IS the feedback; a ripple would fight it
                role = Role.Button,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(label, color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Box(
            Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                // --liq-shadow-cta on cards 01-04, --liq-shadow-violet on card 05.
                //
                // Drawn, not cast. Modifier.shadow uses Android's elevation system, whose light
                // sits at the top-centre of the WINDOW -- so the shadow's direction depends on
                // where the control happens to sit on screen, and this circle lives at the right
                // edge, which threw its glow down and to the LEFT. The token is a glow: no light
                // source, no direction, spread evenly and pushed straight down.
                .size(IconSizes.badge)
                .ctaGlow(glow)
                .clip(CircleShape)
                .then(
                    when (variant) {
                        NextVariant.Orange -> Modifier.background(Orange)
                        // 135 degrees, midpoint at 38% - deliberately not an even three-stop ramp
                        NextVariant.Sunset -> Modifier.background(
                            Brush.linearGradient(
                                0.00f to Orange,
                                0.38f to Color(0xFFD05976),
                                1.00f to Purple,
                            )
                        )
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            ArrowRight(size = if (variant == NextVariant.Sunset) 22.dp else 20.dp)
        }
    }
}

/**
 * ⑤ A rule row: dot · rule · faint dash · purple consequence.
 *
 * Shared because four screens use it. Screen 2 sets `wraps = false` (spec: nowrap, single line);
 * screens 3-5 wrap to two lines, which is why the flag exists rather than a hard-coded rule.
 */
@Composable
fun RuleRow(rule: String, consequence: String, wraps: Boolean = true) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        // 6dp dot, offset 6 from the top so it sits on the first line's optical centre
        Box(Modifier.padding(top = Spacing.sm).size(6.dp).background(Orange, CircleShape))
        Text(
            buildAnnotatedString {
                append(rule)
                withStyle(SpanStyle(color = Faint, fontWeight = FontWeight.Medium)) { append(" — ") }
                withStyle(SpanStyle(color = Purple)) { append(consequence) }
            },
            color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, lineHeight = 18.2.sp, letterSpacing = (-0.01).em,
            maxLines = if (wraps) Int.MAX_VALUE else 1,
            softWrap = wraps,
        )
    }
}

/**
 * Screen 6 only: a plain statement row, single colour.
 *
 * Deliberately NOT the two-tone rule/consequence pattern — that sells a benefit, this states
 * policy. 14.5 / 1.42, 7dp dot at offset 7, gap 12 to the text.
 */
@Composable
fun StatementRow(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        Box(Modifier.padding(top = 7.dp).size(7.dp).background(Orange, CircleShape))
        Text(
            text, color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
            fontSize = 14.5.sp, lineHeight = 20.6.sp,
        )
    }
}

/**
 * The screen shell. Everything here is identical across tutorial screens 2-6.
 *
 * @param step          which segment of the progress bar is filled (1-based)
 * @param totalSteps    how many segments — 5 for the tour
 * @param eyebrow       the pill label
 * @param showBack      false on the first screen of the tour: the slot stays, reserving layout
 *                      width, but is invisible and non-interactive
 * @param headline      the Lora headline, passed in so each screen can style its own italic run
 * @param content       ⑤⑥⑦ the per-screen body — rule rows, paragraphs
 * @param art           ⑧ the illustration block, 342 x 230, the only element allowed to shrink.
 *                      BoxScope, not ColumnScope: art is invoked inside the flexible Box below.
 *                      Compose marks every layout scope with @LayoutScopeMarker (a @DslMarker), so
 *                      opening a Box scope hides the enclosing ColumnScope — a ColumnScope receiver
 *                      here has nothing to resolve against and fails to compile.
 */
@Composable
fun TutorialShell(
    step: Int,
    totalSteps: Int,
    eyebrow: String,
    nextLabel: String,
    onNext: () -> Unit,
    nextVariant: NextVariant = NextVariant.Orange,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    onBack: () -> Unit = {},
    headline: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    art: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(Cream)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                // ① content region: pad-top 8 below the safe-area inset, gutter 24, floor 0
                .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, top = Spacing.md),
        ) {
            StepProgress(totalSteps, step)
            Spacer(Modifier.height(24.dp))          // progress -> eyebrow

            EyebrowPill(eyebrow)
            Spacer(Modifier.height(14.dp))          // eyebrow -> headline

            // ④ headline + the underline accent. Every card's AC asks for it; only the Welcome
            //    screen had one.
            //
            //    The bar is a child of a Box wrapping just the headline, bottom-aligned and nudged
            //    down 4. Modifier.offset places without re-measuring, so the accent cannot change
            //    the headline's measured height — the column's 14 / 28 margins are untouched.
            Column { headline() }
            Spacer(Modifier.height(28.dp))          // headline -> content

            content()

            Spacer(Modifier.height(24.dp))          // content -> illustration

            // The single flexible region. Compose does not shrink a fixed-height child the way CSS
            // flex does, so rather than "art at 230 + a spacer", the art's SLOT takes all remaining
            // space and the art measures min(available, 230) inside it, aligned to the top. Same
            // result as the spec's "art + flex:1 spacer": art at its natural size on tall frames,
            // shrinking on short ones, nav row pinned to the bottom either way.
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                art()
            }

            // ⑨ nav row — bottom-anchored, 24 above the content floor
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The label is ~20dp tall; 48dp is Android's minimum touch target, so the box
                // carries the target and the text sits at its leading edge — same pixels, a
                // tappable area that passes an accessibility scan. When the slot is reserved but
                // invisible (card 01) it is also cleared from the semantics tree, so a screen
                // reader does not announce a "Back" that cannot be pressed.
                Box(
                    Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .then(
                            if (showBack) Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(role = Role.Button, onClick = onBack)
                            else Modifier.clearAndSetSemantics { }
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        "Back",
                        color = if (showBack) Subtle else Color.Transparent,
                        fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    )
                }
                NextButton(nextLabel, onNext, variant = nextVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * ⑧ Placeholder for the artwork design will supply.
 *
 * It holds the exact block the real asset gets — 248 x 210, centred, shrinking with the frame — so
 * dropping the image in later changes nothing about the layout. Replace the Canvas with an
 * Image(painterResource(...)) and the surrounding code is unchanged.
 *
 * `scale` exists for screen 6, which is drawn at 0.62 because its text block is the tallest of the
 * set and would not otherwise fit.
 */
@Composable
fun IllustrationPlaceholder(scale: Float = 1f) {
    // Decorative: it carries no information the copy does not already state, so it is cleared from
    // the semantics tree rather than given a label a screen reader would have to read past.
    Box(contentAlignment = Alignment.Center, modifier = Modifier.clearAndSetSemantics { }) {
        // Radial glow — kept so the "no banding" criterion stays testable.
        //
        // Three stops exactly as the reference: sunset .16 at the centre, violet .10 at 48%,
        // transparent by 70%. An earlier build carried a fourth stop at 26% that exists in no
        // source; it flattened the falloff and is the kind of drift that makes a gradient
        // "nearly right" forever.
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to Orange.copy(alpha = 0.16f),
                    0.48f to Purple.copy(alpha = 0.10f),
                    0.70f to Color.Transparent,
                    1.00f to Color.Transparent,
                ),
                radius = size.minDimension * 0.62f,
            )
        }
        Box(
            Modifier.fillMaxHeight().heightIn(max = 230.dp * scale).aspectRatio(248f / 210f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRoundRect(
                    color = Purple.copy(alpha = 0.38f),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(7.dp.toPx(), 6.dp.toPx()), 0f,
                        ),
                    ),
                )
            }
            Text(
                "ILLUSTRATION\n248 × 210",
                color = Purple.copy(alpha = 0.75f), fontFamily = Manrope,
                fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 16.sp,
                letterSpacing = 0.07.em,
            )
        }
    }
}
