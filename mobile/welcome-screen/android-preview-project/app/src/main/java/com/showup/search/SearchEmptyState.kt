package com.showup.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Elevated
import com.showup.designsystem.Faint
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral
import com.showup.designsystem.Orange
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.Purple
import com.showup.designsystem.ShadowMd
import com.showup.designsystem.Spacing
import com.showup.designsystem.roundedShadows
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline

/*
 * The interim no-results screen (Search 01, decided 27 Oct 2026): the kit's `search-empty` in its
 * NORMAL mode, from `screen-search-empty-reference.jsx`. Search 01's queue and Search 02's grid both
 * run dry into it -- the reference's "1-up / grid view can come up dry" -- inside their own top bar
 * and TabBar. Headline, body and `Widen my search`, which opens Filters "until the no-results story
 * defines the one-tap widen". Left out by the ticket: the CTA sub-line, the filter read-out and
 * `Adjust filters manually`. Instant mode is out of scope.
 */

internal const val SEARCH_EMPTY_CTA_TAG = "search-empty-cta"
internal const val SEARCH_EMPTY_HEADLINE_TAG = "search-empty-headline"
internal const val SEARCH_EMPTY_BODY_TAG = "search-empty-body"
/** The content's scroll -- a last resort past the last level, measured for overflow. */
internal const val SEARCH_EMPTY_SCROLL_TAG = "search-empty-scroll"

/**
 * The order of sacrifice when the region is short: the radar 184 -> 140 -> 112, then the content
 * scrolls under the pinned CTA. OURS, not the reference's -- it names no order -- and the location
 * gate's rule (SHOWUP-188): the decoration gives way, the headline and body never shrink.
 */
internal enum class EmptyFit(val radar: Dp) { Full(184.dp), Compact(140.dp), Tight(112.dp) }

/**
 * The region between the top bar and the TabBar: content (`padding: 8 24 0`), ONE flexible spacer
 * (min 24), and the CTA band (`padding: 0 24 22`) -- "sits in normal flow directly above the tab
 * bar, always visible". Past the last level the content scrolls under the band, never the CTA.
 */
@Composable
internal fun SearchEmptyState(onWiden: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val area = maxHeight
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(SEARCH_EMPTY_SCROLL_TAG)) {
                Column(
                    Modifier
                        .heightIn(min = area)
                        .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, top = Spacing.md),
                ) {
                    EmptyContent(available = area - Spacing.md - 24.dp)
                    // THE ONE FLEXIBLE ELEMENT, floored at 24 ("minHeight: 24").
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f).requiredHeightIn(min = 24.dp))
                }
            }
        }
        PrimaryButton(
            label = SearchCopy.WIDEN_MY_SEARCH,
            onClick = onWiden,
            modifier = Modifier
                .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, bottom = 22.dp)
                .testTag(SEARCH_EMPTY_CTA_TAG),
            variant = PrimaryButtonVariant.Sunset,
            labelMaxLines = 2,
            // Decorative: the visible label is the name.
            leading = {
                Box(Modifier.clearAndSetSemantics {}) {
                    Icon(BrandIcon.Sliders, 18.dp, tint = Color.White, strokeWidth = 2.2f)
                }
            },
        )
    }
}

/** The content above the spacer, at the first [EmptyFit] level that fits [available]. */
@Composable
private fun EmptyContent(available: Dp) {
    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val budget = available.roundToPx()
        var placeables = emptyList<Placeable>()
        for (level in EmptyFit.entries) {
            placeables = subcompose(level) { EmptyColumn(level) }.map { it.measure(loose) }
            if (placeables.sumOf { it.height } <= budget) break
        }
        layout(constraints.maxWidth, placeables.sumOf { it.height }) {
            var y = 0
            placeables.forEach { it.place(0, y); y += it.height }
        }
    }
}

@Composable
private fun EmptyColumn(fit: EmptyFit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
        EmptyRadar(fit.radar)
        // No eyebrow in normal mode: the reference's `marginTop: 14` in its place.
        // Lora 700 / 30 / 1.12 / -0.015em, centred, balanced, `no people` washed. The screen's heading.
        WashHeadline(
            parts = listOf(
                SearchCopy.NO_RESULTS_LEAD to false,
                SearchCopy.NO_RESULTS_EM to true,
                SearchCopy.NO_RESULTS_TAIL to false,
            ),
            fontSize = 30.sp,
            lineHeight = (30f * 1.12f).sp,
            letterSpacing = (-0.015).em,
            balance = true,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 14.dp)
                .testTag(SEARCH_EMPTY_HEADLINE_TAG)
                .semantics { heading() },
        )
        // Manrope 500 / 14.5 / 1.5, `--liq-neutral-200`, centred, at most 320 wide, 12 below.
        Text(
            SearchCopy.NO_RESULTS_BODY,
            modifier = Modifier.padding(top = Spacing.xl).widthIn(max = 320.dp).testTag(SEARCH_EMPTY_BODY_TAG),
            color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 14.5.sp, lineHeight = (14.5f * 1.5f).sp, textAlign = TextAlign.Center,
        )
    }
}

/**
 * `EmptyRadar` -- "came up dry": three rings around a muted search glyph, no nearby dots, the visual
 * inverse of the location gate's live radar. Drawn on the reference's 184 box and scaled as one
 * picture when a [EmptyFit] level shrinks it. Decorative, so out of the reading order.
 *
 * - Halo: 220, `radial-gradient(rgba(254,104,57,.10) 0%, transparent 65%)`. Its `blur(6px)` is not
 *   drawn: the gradient is already transparent from 65% out, so the blur moves nothing visible.
 * - Rings: 176 dashed .10, 116 dashed .16, 64 solid .28, `1.25px rgba(129,42,236)`. THE DASH IS OURS:
 *   CSS `dashed` leaves dash and gap to the browser (ProfilePhotosScreen's note), so the gate radar's
 *   2 on / 4 off is used and the two radars read as one family.
 * - Disc: 64, `--liq-bg-elevated`, 1 `--liq-border-soft`, `--liq-shadow-md`; the 26 `search` glyph at
 *   stroke 1.8 in `--liq-fg-faint`.
 */
@Composable
internal fun EmptyRadar(height: Dp) {
    val s = height.value / 184f
    Box(Modifier.fillMaxWidth().height(height).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        // `requiredSize`, not `size`: the 220 halo overflows the 184 box top and bottom, as the
        // reference's absolutely placed halo does; `size` would clamp it to the box and squash it.
        Canvas(Modifier.requiredSize(220.dp * s)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val u = size.width / 220f
            drawCircle(
                Brush.radialGradient(
                    0f to Orange.copy(alpha = 0.10f),
                    0.65f to Orange.copy(alpha = 0f),
                    center = c,
                    radius = 110f * u,
                ),
                radius = 110f * u,
                center = c,
            )
            val ring = 1.25f * u
            val dash = PathEffect.dashPathEffect(floatArrayOf(2f * u, 4f * u))
            // A CSS border is drawn inside its box: the ring's centre line sits half a stroke in.
            drawCircle(Purple.copy(alpha = 0.10f), 88f * u - ring / 2f, c, style = Stroke(ring, pathEffect = dash))
            drawCircle(Purple.copy(alpha = 0.16f), 58f * u - ring / 2f, c, style = Stroke(ring, pathEffect = dash))
            drawCircle(Purple.copy(alpha = 0.28f), 32f * u - ring / 2f, c, style = Stroke(ring))
        }
        Box(
            Modifier
                .size(64.dp * s)
                .roundedShadows(SolidColor(Elevated), 32.dp * s, ShadowMd)
                .border(1.dp, BorderSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(BrandIcon.Search, 26.dp * s, tint = Faint, strokeWidth = 1.8f)
        }
    }
}
