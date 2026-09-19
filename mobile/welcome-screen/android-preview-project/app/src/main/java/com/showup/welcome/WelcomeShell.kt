/*
 * WelcomeShell.kt
 * ShowUp · shared chrome for the welcome & sign-up flow (SHOWUP-140 / 142 / 143)
 *
 * Startup and Welcome back are one visual space: the same three backdrop layers, value for value,
 * and the same wordmark at 26. The handoff is explicit that building them separately makes them
 * drift, so everything shared lives here and neither screen owns a copy.
 *
 * Values come from design_handoff_showup/tokens/colors_and_type.css and components/shared.jsx,
 * which are authoritative, plus the two reference .jsx files which win on numbers.
 */
package com.showup.welcome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip

import com.showup.designsystem.Spacing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Cream
import com.showup.designsystem.Fg
import com.showup.designsystem.Lora
import com.showup.designsystem.Orange
import com.showup.designsystem.Purple
import com.showup.designsystem.WordmarkStops

// ─────────────────────────────────────────────────────────────────────────────
// ① Backdrop — full-bleed, BEHIND the status bar and home indicator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The ambient backdrop. Startup and Welcome back carry all three layers; phone verification carries
 * two (no peach wash) at lower intensity, because its keypad owns the bottom half of the screen and
 * nothing should glow behind it.
 *
 * Drawn on a Canvas rather than as blurred Boxes: `Modifier.blur` is a no-op below API 31, which
 * would give hard-edged circles on a third of devices. A radial gradient's own alpha falloff carries
 * the softness on every API level.
 */
/**
 * Where the two orbs sit, and how big they are.
 *
 * Four recipes, because four groups of screens ask for four. They were a boolean until 15
 * September 2026, when "The real you" arrived with two more -- and a second backdrop component
 * would have been the primary button all over again. The differences are small and deliberate:
 * every one of them is a number the reference file for that screen sets, and none of them mean
 * anything on their own, which is why they live here as named recipes rather than as five
 * parameters a call site has to get right.
 */
enum class OrbPlacement {
    /** Startup and Welcome back: orange 520 at top -15% / right -25%, violet 600 at bottom -20% / left -30%. */
    Startup,

    /** Phone verification: orange 460 at top -18% / right -30%, violet 420 at top -10% / left -25%. */
    PhoneVerify,

    /**
     * Profile photos: orange 460 at top -22% / right -30%, violet 420 at top -12% / left -28%.
     *
     * Both orbs at the top, because the screen below them scrolls: an orb anchored to the bottom
     * of a scrolling screen sits behind the sticky footer, where it reads as a smudge under the
     * CTA rather than as atmosphere.
     */
    RealYouTop,

    /**
     * Profile prompts: orange 460 at top -20% / right -25%, violet 420 at BOTTOM -12% / left -28%.
     *
     * The one split pair. This screen's footer is a bare CTA row with no gradient mask over it, so
     * there is nothing for a low violet to muddy -- and the suggestion cards fill the middle, which
     * a second top orb would sit behind.
     */
    RealYouSplit,

    /**
     * The voice capture screen: orange 520 at top -25% / right -30%, violet 520 at BOTTOM -22% /
     * left -30%, both a touch more saturated than elsewhere (SHOWUP-161).
     *
     * A fifth placement rather than the nearest existing one, because this is the only screen in
     * the app with no chrome at all -- no header, no progress bar, no footer -- so the orbs ARE the
     * composition rather than atmosphere behind one, and 40dp of orb radius is visible where it
     * would not be on a screen with content over it. `Startup` is the closest and is still 260/300
     * at different offsets.
     */
    VoiceCapture,
}

@Composable
fun WelcomeBackdrop(
    modifier: Modifier = Modifier,
    peachWash: Boolean = true,
    orangeAlpha: Float = 0.32f,
    violetAlpha: Float = 0.28f,
    placement: OrbPlacement = OrbPlacement.Startup,
) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (placement) {
            OrbPlacement.PhoneVerify -> {
                radial(Offset(w * 1.30f - 230.dp.toPx(), -0.18f * h + 230.dp.toPx()), 230.dp.toPx(), Orange, orangeAlpha)
                radial(Offset(-0.25f * w + 210.dp.toPx(), -0.10f * h + 210.dp.toPx()), 210.dp.toPx(), Purple, violetAlpha)
            }
            OrbPlacement.RealYouTop -> {
                radial(Offset(w * 1.30f - 230.dp.toPx(), -0.22f * h + 230.dp.toPx()), 230.dp.toPx(), Orange, orangeAlpha)
                radial(Offset(-0.28f * w + 210.dp.toPx(), -0.12f * h + 210.dp.toPx()), 210.dp.toPx(), Purple, violetAlpha)
            }
            OrbPlacement.RealYouSplit -> {
                radial(Offset(w * 1.25f - 230.dp.toPx(), -0.20f * h + 230.dp.toPx()), 230.dp.toPx(), Orange, orangeAlpha)
                radial(Offset(-0.28f * w + 210.dp.toPx(), h + 0.12f * h - 210.dp.toPx()), 210.dp.toPx(), Purple, violetAlpha)
            }
            OrbPlacement.VoiceCapture -> {
                radial(Offset(w + 0.30f * w - 260.dp.toPx(), -0.25f * h + 260.dp.toPx()), 260.dp.toPx(), Orange, orangeAlpha)
                radial(Offset(-0.30f * w + 260.dp.toPx(), h + 0.22f * h - 260.dp.toPx()), 260.dp.toPx(), Purple, violetAlpha)
            }
            OrbPlacement.Startup -> {
                radial(Offset(w + 0.25f * w - 260.dp.toPx(), -0.15f * h + 260.dp.toPx()), 260.dp.toPx(), Orange, orangeAlpha)
                radial(Offset(-0.30f * w + 300.dp.toPx(), h + 0.20f * h - 300.dp.toPx()), 300.dp.toPx(), Purple, violetAlpha)
            }
        }

        if (peachWash) {
            // linear-gradient(180deg, rgba(255,229,210,.55) 0%, rgba(255,251,247,0) 70%) · height 360
            val washH = 360.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color(0xFFFFE5D2).copy(alpha = 0.55f),
                    0.7f to Color(0x00FFFBF7),
                    1.0f to Color.Transparent,
                    startY = 0f, endY = washH,
                ),
                size = Size(w, washH),
            )
        }
    }
}

/**
 * The CSS drop shadow under a round CTA, DRAWN rather than cast by the platform.
 *
 * `--liq-shadow-cta` is two shadows offset straight down and spread evenly around the shape:
 *
 *     0 8px 20px rgba(254,104,57,.32)
 *     0 2px  6px rgba(254,104,57,.20)
 *
 * `Modifier.shadow` cannot produce that. Android casts elevation shadows from a virtual light at
 * the top-centre of the WINDOW, so the direction depends on where the view sits on screen: a button
 * near the right edge throws its shadow down and to the LEFT, which is exactly what the Next circle
 * was doing. The design asks for a glow, which has no light source and no direction.
 *
 * Same reasoning as the backdrop orbs and for the same reason as avoiding `Modifier.blur`: a radial
 * gradient's own alpha falloff is soft on every API level and is under our control.
 *
 * @param radius the circle's radius, in px
 * @param centre the circle's centre, in px
 */
private fun DrawScope.ctaShadow(centre: Offset, radius: Float, color: Color) {
    // (downward offset, blur, alpha) — the two layers of the token, in CSS pixels.
    val layers = listOf(
        Triple(8.dp.toPx(), 20.dp.toPx(), 0.32f),
        Triple(2.dp.toPx(), 6.dp.toPx(), 0.20f),
    )
    for ((dy, blur, alpha) in layers) {
        // A CSS blur of B fades across roughly B, centred on the edge: solid until B/2 inside the
        // edge, gone by B/2 outside it.
        val outer = radius + blur / 2f
        val solid = ((radius - blur / 2f) / outer).coerceIn(0f, 0.99f)
        drawCircle(
            brush = Brush.radialGradient(
                solid to color.copy(alpha = alpha),
                1.00f to Color.Transparent,
                center = Offset(centre.x, centre.y + dy), radius = outer,
            ),
            radius = outer,
            center = Offset(centre.x, centre.y + dy),
        )
    }
}

/** Draws [ctaShadow] behind a circular control that fills this node. */
fun Modifier.ctaGlow(color: Color): Modifier = drawBehind {
    ctaShadow(Offset(size.width / 2f, size.height / 2f), size.minDimension / 2f, color)
}

/** One orb. The stop at 65% is where the reference puts full transparency. */
private fun DrawScope.radial(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to color.copy(alpha = alpha),
            0.65f to Color.Transparent,
            1.00f to Color.Transparent,
            center = center, radius = radius,
        ),
        radius = radius, center = center,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ② Wordmark — 26 on both launch screens, identical position
// ─────────────────────────────────────────────────────────────────────────────

/**
 * `.su-wordmark` — Lora 700, tracking -0.02em, baseline-aligned, and the gap is 0.04em,
 * not the 0.18em this carried for months.
 *
 * Measured rather than chosen, on the iOS twin against the Startup spec sheet: the sheet
 * shows ink gaps of 0.058em between "Show" and "Up" and 0.117em before the dot, and 0.18em
 * spacing produced 0.203em and 0.239em -- three times and twice too wide, which is what made
 * the mark read as three loose words rather than one lockup. 0.04em lands at 0.074em and
 * 0.092em, inside what a measurement off a screenshot can resolve.
 *
 * The two gaps differ from each other on their own: the period carries far more side bearing
 * than "U" does, so one spacing value reproduces the sheet's uneven pair without being told to.
 *
 * "Up" is filled with `--su-grad-wordmark` (96°, violet → rose → orange) clipped to the glyphs, not
 * flat violet. Compose does this with a Brush on the TextStyle, which is the direct equivalent of
 * the CSS `background-clip: text`.
 */
@Composable
fun Wordmark(size: TextUnit = 26.sp, modifier: Modifier = Modifier) {
    val gap = with(LocalDensity.current) { (size.toPx() * 0.04f).toDp() }
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(gap)) {
        val base = TextStyle(
            fontFamily = Lora, fontWeight = FontWeight.Bold,
            fontSize = size, letterSpacing = (-0.02).em, lineHeight = size,
        )
        Text("Show", style = base, color = Fg)
        Text(
            "Up",
            style = base.copy(
                fontStyle = FontStyle.Italic,
                brush = Brush.horizontalGradient(colorStops = WordmarkStops.toTypedArray()),
            ),
        )
        Text(".", style = base, color = Orange)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ③ The underline wash — the signature editorial flourish
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A headline whose italic run carries the orange wash.
 *
 * From `tokens/colors_and_type.css`, `.su-underlined em::after`:
 *   left/right -2px · bottom -0.08em · height 0.32em · z-index -1
 *   radial-gradient(ellipse at 50% 100%, rgba(254,104,57,0.55) 0%, rgba(254,104,57,0) 70%)
 * and the em itself is italic at **weight 500**, not the surrounding 700.
 *
 * The wash is positioned from the laid-out text rather than a hard-coded width: `getPathForRange`
 * gives the italic run's real bounds, so the wash tracks the word wherever it wraps to. A fixed
 * width is what an earlier build used and it only ever looks right at one frame size.
 *
 * Compose's radial gradients are circular, so the canvas is scaled to turn the circle into the
 * ellipse the CSS asks for.
 */
/**
 * U+00A0. Named, because an invisible literal in source is one tidy-up away from vanishing.
 *
 * Not private: the code screen needs it too, to stop a phone number breaking across two lines.
 */
internal const val NBSP = '\u00A0'

@Composable
fun WashHeadline(
    parts: List<Pair<String, Boolean>>,      // text → is this the italic run?
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = (fontSize.value * 1.05f).sp,
    letterSpacing: TextUnit = (-0.02).em,
    /**
     * An icon that trails the headline, sitting on the baseline of whatever line the text ends on.
     *
     * Part of the text, not a sibling in a Row. The sheet and the ticket both put the heart on
     * SHOWUP-144 "on the baseline" at the end of the headline, and being in the flow is the only
     * way it lands there whichever line the text happens to end on. It also removes a whole class
     * of bug: a sibling in a Row reserves its width against every line, so the headline had less
     * room than it appeared to, wrapped badly, and pushed the icon past the edge.
     */
    trailing: BrandIcon? = null,
    trailingSize: Dp = 30.dp,
    trailingTint: Color = Orange,
    trailingGap: Dp = 12.dp,
) {
    // `white-space: nowrap` on the emphasis span — the token file sets it on `.su-underlined em`
    // and the ticket restates it: "an emphasis phrase never breaks across lines". A plain space is
    // a legal break point, so it is swapped for a non-breaking one here, at the single place that
    // builds the string, rather than trusted to every call site. Without it "Show Up" can split
    // across two lines and the wash paints under two disconnected fragments.
    val safe = parts.map { (t, italic) -> (if (italic) t.replace(' ', NBSP) else t) to italic }

    val text = buildAnnotatedString {
        safe.forEach { (t, italic) ->
            if (italic) {
                // Weight 500, as the token file specifies — the bundled family now carries a real
                // Medium Italic cut. See fonts/README-medium-italic.md.
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium)) { append(t) }
            } else {
                append(t)
            }
        }
    }
    val TRAIL = "trail"
    val textWithIcon = if (trailing == null) text else buildAnnotatedString {
        append(text)
        // U+FFFC OBJECT REPLACEMENT CHARACTER -- the placeholder Compose lays the icon into.
        appendInlineContent(TRAIL, "\uFFFC")
    }

    // The gap is inside the placeholder rather than a padding on the icon, so the line breaker
    // accounts for it: the icon can never be pulled to a line that has no room for the space
    // before it.
    val inline = if (trailing == null) mapOf() else mapOf(
        TRAIL to InlineTextContent(
            Placeholder(
                width = with(LocalDensity.current) { (trailingGap + trailingSize).toSp() },
                height = with(LocalDensity.current) { trailingSize.toSp() },
                // Bottom of the box on the baseline -- which is what "on the baseline" means for
                // a solid shape with no descender.
                placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline,
            )
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Spacer(Modifier.width(trailingGap))
                Icon(trailing, trailingSize, tint = trailingTint)
            }
        }
    )

    // character range of the italic run, if there is one
    var start = -1
    var end = -1
    var cursor = 0
    safe.forEach { (t, italic) ->
        if (italic && start < 0) { start = cursor; end = cursor + t.length }
        cursor += t.length
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = textWithIcon,
        modifier = modifier.drawBehind {
            val lr = layout ?: return@drawBehind
            if (start < 0 || end <= start) return@drawBehind
            val bounds = lr.getPathForRange(start, end).getBounds()
            if (bounds.width <= 0f) return@drawBehind

            val em = fontSize.toPx()
            val left = bounds.left - 2.dp.toPx()
            val right = bounds.right + 2.dp.toPx()
            val bottom = bounds.bottom + em * 0.08f      // bottom: -0.08em
            val h = em * 0.32f                            // height: 0.32em
            val w = right - left
            val rx = w / 2f

            // Clipped to the band, which the CSS gets for free and this did not.
            //
            // The gradient is an ellipse centred on the BOTTOM edge with a vertical radius of h,
            // so it spans h above that edge and h below it. In CSS the ::after box is only the h
            // above, and everything past it is clipped away -- which is what makes the wash read
            // as a band with an edge. Unclipped, the lower half bled down into the line below and
            // the whole thing looked like a smudge rather than an underline.
            clipRect(left = left, top = bottom - h, right = right, bottom = bottom) {
                withTransform({
                    translate(left, bottom - h)
                    // ellipse at 50% 100%: horizontal radius w/2, vertical radius h
                    scale(scaleX = 1f, scaleY = h / rx, pivot = Offset(rx, h))
                }) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            // Transparent at 1.0, not at 0.7. The stop is a fraction of the
                            // radius, and the radius is half the phrase -- so stopping at 0.7 put
                            // the last visible ink at 70% of the way out, and because alpha falls
                            // off the whole way the eye lost it around 56%. Measured against the
                            // design's own render, which reaches about 78%: the wash sat under the
                            // middle of "free to date" instead of under all of it.
                            0.0f to Orange.copy(alpha = 0.55f),
                            1.0f to Color.Transparent,
                            center = Offset(rx, h), radius = rx,
                        ),
                        radius = rx, center = Offset(rx, h),
                    )
                }
            }
        },
        onTextLayout = { layout = it },
        inlineContent = inline,
        color = Fg,
        fontFamily = Lora,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ④ Buttons — always pills (9999), never rounded rectangles
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Button treatments.
 *
 * [Apple], [Google] and [Facebook] are not style choices — each provider dictates the appearance of
 * its own sign-in button and enforces it. Google forbids recolouring or resizing the G and requires
 * a white background, and makes compliance a condition of app verification; Meta requires its mark
 * in white or #1877F2 and prohibits recolouring to a host brand's palette. The uniform ghost
 * treatment the design specified breaks both.
 *
 * The pill silhouette, the 56 height and the Manrope label are kept — those are ours, and neither
 * provider constrains them.
 */
/**
 * [Plain] is not a provider treatment and never carries one: it is the conflict modal's
 * secondary, which has no border precisely so the pair does not read as two equal choices.
 */
/**
 * How far a mark's ink is from the centre of its artboard, in 24-grid units.
 *
 * Only the provider logos are handled: they are the only marks ever shown standing alone. Anything
 * else returns zero, so an un-tabulated icon is drawn exactly as authored rather than nudged by a
 * guess.
 */
private fun inkOffset(icon: BrandIcon): Offset {
    val paths = when (icon) {
        BrandIcon.Apple -> listOf(applePath())
        BrandIcon.Facebook -> listOf(facebookPath())
        BrandIcon.Google -> listOf(googleBlue(), googleGreen(), googleYellow(), googleRed())
        else -> return Offset.Zero
    }
    var left = Float.MAX_VALUE; var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE; var bottom = -Float.MAX_VALUE
    paths.forEach {
        val b = it.getBounds()
        left = minOf(left, b.left); top = minOf(top, b.top)
        right = maxOf(right, b.right); bottom = maxOf(bottom, b.bottom)
    }
    if (left > right) return Offset.Zero
    return Offset(12f - (left + right) / 2f, 12f - (top + bottom) / 2f)
}

// ⑤ Icons — Lucide geometry, 24x24, stroke 1.7, currentColor
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Drawn rather than imported: CLAUDE.md requires inline stroke-only SVG at Lucide geometry, and
 * forbids icon fonts, PNGs and unicode glyphs. The brand marks (Apple, Google, Facebook) are filled
 * paths in the source, so they are drawn filled here.
 */
enum class BrandIcon { Phone, Apple, Google, Facebook, Calendar, ChevronDown, ChevronLeft,
                       ChevronRight, ArrowLeft, ArrowRight, Pencil, Pen, Edit, Close, Check,
                       Shield, Heart, EyeOff, Plus, Image, Camera, Lock, Sliders,
                       // ── the media step (SHOWUP-161) ──────────────────────────────
                       Video, Mic, Play, Refresh, Trash }

@Composable
/**
 * @param opticalCentre centre the mark on its own ink rather than on its 24-unit artboard.
 *
 * The provider logos are drawn to sit optically correct BESIDE a text label, which leaves them a
 * little high in their own box -- Apple's occupies y 3.8..16.5, so its centre is ~1.8 units above
 * the artboard's. Against a label that is invisible. Alone inside a 120dp ring it is not, which is
 * exactly where the linking and success heroes put it. Measured from the path rather than tabulated
 * per icon, so it stays right if the artwork is ever replaced.
 */
fun Icon(
    icon: BrandIcon,
    size: Dp,
    tint: Color = Fg,
    /**
     * Stroke weight IN GRID UNITS, exactly as the design's SVGs author it.
     *
     * NOT Dp, and that is the whole point. It used to be, and it was converted with `toPx()`
     * OUTSIDE the `scale(s, s)` below and then drawn INSIDE it -- so every stroke was multiplied
     * by the scale a second time and came out `density` times too thick. On a 2.625x phone every
     * icon in the app was 2.6x heavier than drawn, and the arrow on the embrace CTA was fat
     * enough that its two head strokes merged into a solid triangle.
     *
     * It looked correct at exactly one density, 1.0, which is why previews never showed it and
     * the fit harness could not: that harness measures the space a control occupies, and this is
     * a bug in the ink inside it.
     *
     * A [Float] rather than a [Dp] because the value belongs to the 24-grid the paths are drawn
     * on, and the scale below is what turns it into pixels. Typing it as Dp is what invited the
     * conversion that broke it.
     */
    strokeWidth: Float = 1.7f,
    opticalCentre: Boolean = false,
) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f                      // all paths are authored on a 24 grid
        // The width is in grid units and the transform scales it, exactly like the coordinates.
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val nudge = if (opticalCentre) inkOffset(icon) else Offset.Zero
        withTransform({ scale(s, s, pivot = Offset.Zero); translate(nudge.x, nudge.y) }) {
            when (icon) {
                BrandIcon.Calendar -> {
                    drawRoundRect(
                        color = tint, topLeft = Offset(3f, 4f), size = Size(18f, 18f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f), style = stroke,
                    )
                    drawLine(tint, Offset(16f, 2f), Offset(16f, 6f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(8f, 2f), Offset(8f, 6f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(3f, 10f), Offset(21f, 10f), stroke.width, StrokeCap.Round)
                }
                BrandIcon.ChevronDown -> drawPath(
                    path(listOf(6f to 9f, 12f to 15f, 18f to 9f)), tint, style = stroke,
                )
                // The back control, per the handoff's AppHeader: chevron-left, NOT arrow-left.
                // Both icons exist in the design's set and they are not interchangeable -- the
                // arrow has a shaft and reads much heavier at the top of a screen.
                BrandIcon.ChevronLeft -> drawPath(
                    path(listOf(15f to 18f, 9f to 12f, 15f to 6f)), tint, style = stroke,
                )
                BrandIcon.ArrowLeft -> {
                    drawLine(tint, Offset(19f, 12f), Offset(5f, 12f), stroke.width, StrokeCap.Round)
                    drawPath(path(listOf(12f to 19f, 5f to 12f, 12f to 5f)), tint, style = stroke)
                }
                BrandIcon.ArrowRight -> {
                    drawLine(tint, Offset(5f, 12f), Offset(19f, 12f), stroke.width, StrokeCap.Round)
                    drawPath(path(listOf(12f to 5f, 19f to 12f, 12f to 19f)), tint, style = stroke)
                }
                // ── added for "The real you" (SHOWUP-156 / SHOWUP-158) ──────────────
                //
                // Every one of these is copied from `components/shared.jsx`, the design system's
                // own icon set, at its exact 24-grid geometry. Not from a screen's inline SVG and
                // not from memory: the back control was drawn `arrow-left` where the design says
                // `chevron-left` once already, and no test catches that.
                BrandIcon.ChevronRight ->
                    drawPath(path(listOf(9f to 18f, 15f to 12f, 9f to 6f)), tint, style = stroke)
                BrandIcon.Plus -> {
                    drawLine(tint, Offset(12f, 5f), Offset(12f, 19f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(5f, 12f), Offset(19f, 12f), stroke.width, StrokeCap.Round)
                }
                BrandIcon.Image -> {
                    drawRoundRect(
                        color = tint, topLeft = Offset(3f, 3f), size = Size(18f, 18f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                        style = stroke,
                    )
                    drawCircle(tint, radius = 1.6f, center = Offset(8.5f, 8.5f), style = stroke)
                    drawPath(path(listOf(21f to 15f, 16f to 10f, 5f to 21f)), tint, style = stroke)
                }
                BrandIcon.Camera -> {
                    // The body is a 2-radius rounded rect with a notch cut into its top edge for
                    // the lens housing. Drawn as one path rather than a rect plus a trapezium, so
                    // the stroke has no join artefacts where the notch meets the edge.
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(23f, 19f)
                            arcTo(Rect(19f, 17f, 23f, 21f), 0f, 90f, false)
                            lineTo(3f, 21f)
                            arcTo(Rect(1f, 17f, 5f, 21f), 90f, 90f, false)
                            lineTo(1f, 8f)
                            arcTo(Rect(1f, 6f, 5f, 10f), 180f, 90f, false)
                            lineTo(7f, 6f); lineTo(9f, 3f); lineTo(15f, 3f); lineTo(17f, 6f)
                            lineTo(21f, 6f)
                            arcTo(Rect(19f, 6f, 23f, 10f), 270f, 90f, false)
                            close()
                        }, tint, style = stroke)
                    drawCircle(tint, radius = 4f, center = Offset(12f, 13f), style = stroke)
                }
                BrandIcon.Lock -> {
                    drawRoundRect(
                        color = tint, topLeft = Offset(3f, 11f), size = Size(18f, 11f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                        style = stroke,
                    )
                    // The shackle: up, over the top as a true semicircle, back down.
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(7f, 11f); lineTo(7f, 7f)
                            arcTo(Rect(7f, 2f, 17f, 12f), 180f, 180f, false)
                            lineTo(17f, 11f)
                        }, tint, style = stroke)
                }
                BrandIcon.Sliders -> {
                    listOf(
                        4f to (21f to 14f), 4f to (10f to 3f),
                        12f to (21f to 12f), 12f to (8f to 3f),
                        20f to (21f to 16f), 20f to (12f to 3f),
                    ).forEach { (x, span) ->
                        drawLine(tint, Offset(x, span.first), Offset(x, span.second), stroke.width, StrokeCap.Round)
                    }
                    listOf(
                        Triple(1f, 7f, 14f), Triple(9f, 15f, 8f), Triple(17f, 23f, 16f),
                    ).forEach { (x1, x2, y) ->
                        drawLine(tint, Offset(x1, y), Offset(x2, y), stroke.width, StrokeCap.Round)
                    }
                }
                // `edit` from the shared set: a pen over a baseline stroke. The arc in the source
                // path -- `a2.121 2.121 0 0 1 3 3` -- is a true semicircle, because the chord
                // (3, 3) has length 4.243 and the radius is 2.121, so 2r is the chord. Centre is
                // therefore the chord's midpoint.
                BrandIcon.Edit -> {
                    drawLine(tint, Offset(12f, 20f), Offset(21f, 20f), stroke.width, StrokeCap.Round)
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(16.5f, 3.5f)
                            arcTo(Rect(15.879f, 2.879f, 20.121f, 7.121f), 225f, 180f, false)
                            lineTo(7f, 19f); lineTo(3f, 20f); lineTo(4f, 16f)
                            close()
                        }, tint, style = stroke)
                }
                // The prompts card's edit pip. A pen with NO baseline stroke, which is what makes
                // it a different glyph from [Edit] rather than the same one at another size --
                // SHOWUP-158's reference draws this one inline.
                BrandIcon.Pen -> {
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(17f, 3f)
                            arcTo(Rect(16.17f, 2.17f, 21.83f, 7.83f), 225f, 180f, false)
                            lineTo(7.5f, 20.5f); lineTo(2f, 22f); lineTo(3.5f, 16.5f)
                            close()
                        }, tint, style = stroke)
                    drawLine(tint, Offset(15f, 5f), Offset(19f, 9f), stroke.width, StrokeCap.Round)
                }
                BrandIcon.Close -> {
                    drawLine(tint, Offset(18f, 6f), Offset(6f, 18f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(6f, 6f), Offset(18f, 18f), stroke.width, StrokeCap.Round)
                }
                BrandIcon.Check -> drawPath(path(listOf(20f to 6f, 9f to 17f, 4f to 12f)), tint, style = stroke)
                // ── added for the media step (SHOWUP-161) ───────────────────────────
                //
                // All five copied from `components/shared.jsx` at its exact 24-grid geometry,
                // like the set above and for the same reason: an icon redrawn from memory is a
                // real icon, faithfully drawn, and the wrong one -- which no test catches.
                //
                // `video` and `mic` are the two slot pips; `play`, `refresh` and `trash` are the
                // controls on a filled card and on the review screen.
                BrandIcon.Video -> {
                    // polygon 23 7 -> 16 12 -> 23 17. The lens flare, drawn stroked and closed.
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(23f, 7f); lineTo(16f, 12f); lineTo(23f, 17f); close()
                        },
                        tint, style = stroke,
                    )
                    drawRoundRect(
                        color = tint, topLeft = Offset(1f, 5f), size = Size(15f, 14f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                        style = stroke,
                    )
                }
                BrandIcon.Mic -> {
                    // The capsule is a 6x12 rect at radius 3 -- a rounded rectangle whose radius
                    // is half its width, which is a capsule exactly.
                    drawRoundRect(
                        color = tint, topLeft = Offset(9f, 2f), size = Size(6f, 12f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                        style = stroke,
                    )
                    // `M5 11 a7 7 0 0 0 14 0` -- the lower half of a circle centred (12,11) r 7.
                    // 0 degrees is at 3 o'clock and a positive sweep runs clockwise, which on a
                    // y-down canvas is downwards: 0 -> 180 is the half that cradles the capsule.
                    drawArc(
                        color = tint,
                        startAngle = 0f, sweepAngle = 180f, useCenter = false,
                        topLeft = Offset(5f, 4f), size = Size(14f, 14f), style = stroke,
                    )
                    drawLine(tint, Offset(12f, 18f), Offset(12f, 22f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(8f, 22f), Offset(16f, 22f), stroke.width, StrokeCap.Round)
                }
                // FILLED, not stroked. The design draws it as a solid polygon in every size it
                // appears at, the same way the provider marks are filled paths in the source.
                BrandIcon.Play -> drawPath(
                    androidx.compose.ui.graphics.Path().apply {
                        moveTo(6f, 4f); lineTo(20f, 12f); lineTo(6f, 20f); close()
                    },
                    tint,
                )
                BrandIcon.Refresh -> {
                    drawPath(path(listOf(23f to 4f, 23f to 10f, 17f to 10f)), tint, style = stroke)
                    drawPath(path(listOf(1f to 20f, 1f to 14f, 7f to 14f)), tint, style = stroke)
                    // The two 9-unit arcs both run on the circle centred (12,12) -- solved from
                    // the SVG's endpoints rather than eyeballed, which is why the rect below is
                    // (3,3)..(21,21) and the sweeps are the same 115.5 degrees in opposite
                    // directions. Drawn as one path each so the line joins stay round.
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            arcTo(
                                androidx.compose.ui.geometry.Rect(3f, 3f, 21f, 21f),
                                -160.5f, 115.5f, true,
                            )
                            lineTo(23f, 10f)
                        },
                        tint, style = stroke,
                    )
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(1f, 14f)
                            lineTo(5.64f, 18.36f)
                            arcTo(
                                androidx.compose.ui.geometry.Rect(3f, 3f, 21f, 21f),
                                135f, -115.5f, false,
                            )
                        },
                        tint, style = stroke,
                    )
                }
                BrandIcon.Trash -> {
                    drawLine(tint, Offset(3f, 6f), Offset(21f, 6f), stroke.width, StrokeCap.Round)
                    // The can. Its two lower corners are 2-unit rounds, drawn as quadratics with
                    // the control point at the corner the curve replaces -- indistinguishable from
                    // the SVG arc at every size this is drawn at, and far less arithmetic.
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(19f, 6f)
                            lineTo(18f, 20f)
                            quadraticBezierTo(18f, 22f, 16f, 22f)
                            lineTo(8f, 22f)
                            quadraticBezierTo(6f, 22f, 6f, 20f)
                            lineTo(5f, 6f)
                        },
                        tint, style = stroke,
                    )
                    drawLine(tint, Offset(10f, 11f), Offset(10f, 17f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(14f, 11f), Offset(14f, 17f), stroke.width, StrokeCap.Round)
                    // The lid's handle, 1-unit rounds.
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(9f, 6f)
                            lineTo(9f, 4f)
                            quadraticBezierTo(9f, 3f, 10f, 3f)
                            lineTo(14f, 3f)
                            quadraticBezierTo(15f, 3f, 15f, 4f)
                            lineTo(15f, 6f)
                        },
                        tint, style = stroke,
                    )
                }
                // The visibility band's mark (SHOWUP-154 callout 11). Feather's eye-off: the
                // eye's two arcs with the pupil, struck through corner to corner. Authored on the
                // same 24 grid as everything else here.
                BrandIcon.EyeOff -> {
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(17.94f, 17.94f)
                            cubicTo(16.23f, 19.24f, 14.15f, 19.97f, 12f, 20f)
                            cubicTo(5f, 20f, 1f, 12f, 1f, 12f)
                            cubicTo(2.24f, 9.68f, 3.97f, 7.65f, 6.06f, 6.06f)
                        }, tint, style = stroke)
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(9.9f, 4.24f)
                            cubicTo(10.59f, 4.08f, 11.29f, 4f, 12f, 4f)
                            cubicTo(19f, 4f, 23f, 12f, 23f, 12f)
                            cubicTo(22.39f, 13.13f, 21.678f, 14.2f, 20.84f, 15.19f)
                        }, tint, style = stroke)
                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(14.12f, 14.12f)
                            cubicTo(13.55f, 14.73f, 12.76f, 15.09f, 11.93f, 15.1f)
                            cubicTo(10.24f, 15.1f, 8.87f, 13.73f, 8.87f, 12.04f)
                            cubicTo(8.88f, 11.21f, 9.24f, 10.42f, 9.85f, 9.85f)
                        }, tint, style = stroke)
                    drawLine(tint, Offset(1f, 1f), Offset(23f, 23f), stroke.width, StrokeCap.Round)
                }
                BrandIcon.Shield -> drawPath(
                    androidx.compose.ui.graphics.Path().apply {
                        moveTo(12f, 22f)
                        cubicTo(12f, 22f, 20f, 18f, 20f, 12f)
                        lineTo(20f, 5f); lineTo(12f, 2f); lineTo(4f, 5f); lineTo(4f, 12f)
                        cubicTo(4f, 18f, 12f, 22f, 12f, 22f)
                        close()
                    }, tint, style = stroke)
                BrandIcon.Heart -> drawPath(
                    androidx.compose.ui.graphics.Path().apply {
                        moveTo(12f, 21f)
                        cubicTo(12f, 21f, 3.5f, 15.4f, 3.5f, 10f)
                        cubicTo(3.5f, 6.5f, 8.5f, 4.7f, 12f, 7.2f)
                        cubicTo(15.5f, 4.7f, 20.5f, 6.5f, 20.5f, 10f)
                        cubicTo(20.5f, 15.4f, 12f, 21f, 12f, 21f)
                        close()
                    }, tint)
                
                BrandIcon.Pencil -> {
                    drawPath(path(listOf(11f to 4f, 4f to 4f, 2f to 6f, 2f to 20f, 4f to 22f, 18f to 22f, 20f to 20f, 20f to 13f)), tint, style = stroke)
                    drawPath(path(listOf(18.5f to 2.5f, 21.5f to 5.5f, 12f to 15f, 8f to 16f, 9f to 12f), close = true), tint, style = stroke)
                }
                // Brand marks are filled in the source rather than stroked.
                BrandIcon.Phone -> drawPath(phonePath(), tint, style = stroke)
                BrandIcon.Apple -> drawPath(applePath(), tint)
                // The four-colour G. Never tinted — recolouring it is the thing the branding
                // guidelines forbid most explicitly, so this ignores the tint argument by design.
                BrandIcon.Google -> {
                    drawPath(googleBlue(), Color(0xFF4285F4))
                    drawPath(googleGreen(), Color(0xFF34A853))
                    drawPath(googleYellow(), Color(0xFFFBBC05))
                    drawPath(googleRed(), Color(0xFFEA4335))
                }
                BrandIcon.Facebook -> drawPath(facebookPath(), tint)
            }
        }
    }
}

private fun path(points: List<Pair<Float, Float>>, close: Boolean = false) =
    androidx.compose.ui.graphics.Path().apply {
        points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
        if (close) close()
    }

private fun phonePath() = androidx.compose.ui.graphics.Path().apply {
    moveTo(22f, 16.92f); lineTo(22f, 19.92f)
    cubicTo(22f, 21.02f, 20.92f, 21.99f, 19.82f, 21.92f)
    cubicTo(16.7f, 21.6f, 13.7f, 20.6f, 11.19f, 18.85f)
    cubicTo(8.85f, 17.25f, 6.87f, 15.27f, 5.19f, 12.85f)
    cubicTo(3.43f, 10.3f, 2.44f, 7.3f, 2.12f, 4.18f)
    cubicTo(2.05f, 3.08f, 3.01f, 2f, 4.11f, 2f)
    lineTo(7.11f, 2f)
    cubicTo(8.11f, 2f, 8.98f, 2.72f, 9.11f, 3.72f)
    cubicTo(9.24f, 4.68f, 9.48f, 5.62f, 9.83f, 6.53f)
    cubicTo(10.06f, 7.24f, 9.86f, 8.02f, 9.38f, 8.64f)
    lineTo(8.09f, 9.91f)
    cubicTo(9.53f, 12.36f, 11.64f, 14.47f, 14.09f, 15.91f)
    lineTo(15.36f, 14.64f)
    cubicTo(15.98f, 14.16f, 16.76f, 13.96f, 17.47f, 14.19f)
    cubicTo(18.38f, 14.54f, 19.32f, 14.78f, 20.28f, 14.91f)
    cubicTo(21.29f, 15.05f, 22f, 15.92f, 22f, 16.92f)
    close()
}

/*
 * The Apple mark, on the 24 grid, in two subpaths: the body and the leaf.
 *
 * REDRAWN 11 September 2026, because the previous one was not the Apple logo. It was a hand-drawn
 * approximation and it showed: the body had no bite, the shoulders were square, and the leaf sat
 * as a lens floating clear of the fruit. On a 16dp mark inside a black pill it read as a smudge.
 *
 * Apple's Sign in with Apple guidelines say the mark may be scaled but not redrawn, so the one
 * thing this must not be is somebody's impression of an apple. The geometry below is the standard
 * 24-unit outline -- the same one Google's four-colour G beside it uses, and for the same reason:
 * "close enough" is altering a mark we are not permitted to alter.
 *
 * Two subpaths and NonZero winding, which is the default. The bite is not a hole to be subtracted;
 * it is part of the body's own outline, curving inward on the right. Drawing it as a second
 * subpath would punch a notch through the leaf as well.
 */
private fun applePath() = androidx.compose.ui.graphics.Path().apply {
    // The body. Starts at the top of the bite and runs anticlockwise around the fruit.
    moveTo(17.05f, 12.54f)
    cubicTo(17.04f, 10.2f, 18.98f, 9.07f, 19.07f, 9.01f)
    cubicTo(17.97f, 7.41f, 16.26f, 7.19f, 15.65f, 7.17f)
    cubicTo(14.2f, 7.02f, 12.8f, 8.03f, 12.06f, 8.03f)
    cubicTo(11.31f, 8.03f, 10.16f, 7.19f, 8.93f, 7.21f)
    cubicTo(7.35f, 7.23f, 5.89f, 8.14f, 5.08f, 9.56f)
    cubicTo(3.42f, 12.43f, 4.66f, 16.67f, 6.27f, 18.99f)
    cubicTo(7.06f, 20.13f, 7.99f, 21.4f, 9.22f, 21.36f)
    cubicTo(10.41f, 21.31f, 10.86f, 20.59f, 12.3f, 20.59f)
    cubicTo(13.73f, 20.59f, 14.14f, 21.36f, 15.39f, 21.33f)
    cubicTo(16.67f, 21.31f, 17.47f, 20.18f, 18.25f, 19.04f)
    cubicTo(19.16f, 17.72f, 19.53f, 16.44f, 19.55f, 16.37f)
    cubicTo(19.52f, 16.36f, 17.07f, 15.42f, 17.05f, 12.54f)
    close()
    // The leaf, meeting the body at the stem rather than floating above it.
    moveTo(14.7f, 5.64f)
    cubicTo(15.36f, 4.85f, 15.8f, 3.74f, 15.68f, 2.64f)
    cubicTo(14.74f, 2.68f, 13.6f, 3.27f, 12.92f, 4.06f)
    cubicTo(12.31f, 4.76f, 11.78f, 5.89f, 11.92f, 6.96f)
    cubicTo(12.97f, 7.04f, 14.04f, 6.42f, 14.7f, 5.64f)
    close()
}

/*
 * The official four-colour Google G, one path per colour, on the 24 grid.
 *
 * These are Google's own path data. They are not redrawn or simplified: the branding guidelines
 * forbid altering the mark, and "close enough" is altering it.
 */
private fun googleBlue() = androidx.compose.ui.graphics.Path().apply {
    moveTo(22.56f, 12.25f)
    cubicTo(22.56f, 11.47f, 22.49f, 10.72f, 22.36f, 10f)
    lineTo(12f, 10f); lineTo(12f, 14.26f); lineTo(17.92f, 14.26f)
    cubicTo(17.66f, 15.63f, 16.88f, 16.79f, 15.71f, 17.57f)
    lineTo(15.71f, 20.34f); lineTo(19.28f, 20.34f)
    cubicTo(21.36f, 18.42f, 22.56f, 15.6f, 22.56f, 12.25f)
    close()
}

private fun googleGreen() = androidx.compose.ui.graphics.Path().apply {
    moveTo(12f, 23f)
    cubicTo(14.97f, 23f, 17.46f, 22.02f, 19.28f, 20.34f)
    lineTo(15.71f, 17.57f)
    cubicTo(14.73f, 18.23f, 13.48f, 18.63f, 12f, 18.63f)
    cubicTo(9.14f, 18.63f, 6.71f, 16.7f, 5.84f, 14.1f)
    lineTo(2.18f, 14.1f); lineTo(2.18f, 16.94f)
    cubicTo(3.99f, 20.53f, 7.7f, 23f, 12f, 23f)
    close()
}

private fun googleYellow() = androidx.compose.ui.graphics.Path().apply {
    moveTo(5.84f, 14.09f)
    cubicTo(5.62f, 13.43f, 5.49f, 12.73f, 5.49f, 12f)
    cubicTo(5.49f, 11.27f, 5.62f, 10.57f, 5.84f, 9.91f)
    lineTo(5.84f, 7.07f); lineTo(2.18f, 7.07f)
    cubicTo(1.43f, 8.55f, 1f, 10.22f, 1f, 12f)
    cubicTo(1f, 13.78f, 1.43f, 15.45f, 2.18f, 16.93f)
    lineTo(5.03f, 14.71f)
    close()
}

private fun googleRed() = androidx.compose.ui.graphics.Path().apply {
    moveTo(12f, 5.38f)
    cubicTo(13.62f, 5.38f, 15.06f, 5.94f, 16.21f, 7.02f)
    lineTo(19.36f, 3.87f)
    cubicTo(17.45f, 2.09f, 14.97f, 1f, 12f, 1f)
    cubicTo(7.7f, 1f, 3.99f, 3.47f, 2.18f, 7.07f)
    lineTo(5.84f, 9.91f)
    cubicTo(6.71f, 7.31f, 9.14f, 5.38f, 12f, 5.38f)
    close()
}

private fun facebookPath() = androidx.compose.ui.graphics.Path().apply {
    moveTo(22f, 12f); cubicTo(22f, 6.48f, 17.52f, 2f, 12f, 2f); cubicTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
    cubicTo(2f, 16.84f, 5.44f, 20.87f, 10.44f, 21.88f); lineTo(10.44f, 14.89f); lineTo(7.9f, 14.89f)
    lineTo(7.9f, 12f); lineTo(10.44f, 12f); lineTo(10.44f, 9.8f)
    cubicTo(10.44f, 7.29f, 11.93f, 5.9f, 14.22f, 5.9f); cubicTo(15.32f, 5.9f, 16.46f, 6.1f, 16.46f, 6.1f)
    lineTo(16.46f, 8.56f); lineTo(15.2f, 8.56f); cubicTo(13.96f, 8.56f, 13.57f, 9.33f, 13.57f, 10.12f)
    lineTo(13.57f, 12f); lineTo(16.35f, 12f); lineTo(15.9f, 14.89f); lineTo(13.57f, 14.89f)
    lineTo(13.57f, 21.88f); cubicTo(18.56f, 20.87f, 22f, 16.84f, 22f, 12f); close()
}

// ─────────────────────────────────────────────────────────────────────────────
// ⑥ Chrome — the mock's 54 / 28 are placeholders for the real safe-area insets
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The launch-screen scaffold: backdrop at the bottom of the z-order, content above it, and the
 * safe-area insets applied to the content only — so the backdrop runs full-bleed behind the status
 * bar and home indicator with no seam, which is an acceptance criterion on both 140 and 142.
 */
@Composable
fun WelcomeScaffold(
    modifier: Modifier = Modifier,
    peachWash: Boolean = true,
    placement: OrbPlacement = OrbPlacement.Startup,
    orangeAlpha: Float = 0.32f,
    violetAlpha: Float = 0.28f,
    topPadding: Dp = 20.dp,
    /**
     * The side gutter.
     *
     * [Spacing.screenGutter] on every screen this scaffold was written for. A parameter because
     * the embrace bridge (SHOWUP-155) draws at 28 -- its reference sets `padding: '64px 28px 0'`
     * on the headline and `'28px 28px 0'` on the body, and a bridge is a wider, quieter beat than
     * the screens either side of it. Not a token: 28 has no meaning beyond "this one screen",
     * which is exactly the case the design-system rules say to keep local.
     */
    gutter: Dp = Spacing.screenGutter,
    /**
     * Scroll only as a last resort, for screens that share the frame with a keyboard.
     *
     * The handoff says these screens never scroll, and with the design decisions on SHOWUP-143
     * settled they will not: the content fits and this does nothing. But "never scroll" and "the
     * CTA is always visible and accessible" are both requirements, and when the keyboard takes half
     * the screen on a small device only one of them can hold. Between a CTA the user cannot reach
     * and a few pixels of scroll, the scroll is the right failure.
     *
     * The inner column is floored at the viewport height, so when everything fits there is nothing
     * to scroll and the weighted spacer still pushes the bottom row down — the layout is byte-for-
     * byte what it was. It only engages when the alternative is an unreachable button.
     */
    scrollWhenTight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(Cream)) {
        WelcomeBackdrop(
            peachWash = peachWash, placement = placement,
            orangeAlpha = orangeAlpha, violetAlpha = violetAlpha,
        )
        // safeDrawing = system bars + display cutout + IME, so the column sits above the keyboard.
        // It only reports the IME when the activity is in adjustResize; the manifest sets that.
        val insets = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = gutter, end = gutter, top = topPadding)

        if (scrollWhenTight) {
            BoxWithConstraints(insets) {
                val viewport = maxHeight
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Column(Modifier.heightIn(min = viewport), content = content)
                }
            }
        } else {
            Column(insets, content = content)
        }
    }
}
