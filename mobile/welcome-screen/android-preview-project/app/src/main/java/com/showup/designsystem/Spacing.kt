package com.showup.designsystem

import androidx.compose.ui.unit.dp

/**
 * The spacing values this design actually uses, named.
 *
 * NOT a canonical 4/8/16/24/32 scale, deliberately. The audit on 7 September 2026 found nine
 * spacing values in real, repeated use -- 4, 6, 8, 10, 12, 16, 24 among them -- and they do not sit
 * on any regular step. Imposing a tidy scale would mean changing spacing, which is a visual change
 * this cleanup is not allowed to make; keeping a tidy scale alongside two dozen exceptions would be
 * worse, because it would read as authoritative while lying.
 *
 * So these describe the design rather than prescribe at it. The names are ordinal because the
 * values genuinely serve many unrelated purposes: [md] is the gap between rule rows on tutorial
 * card 01 and the gap between provider marks and their labels. [screenGutter] is the one with a
 * single meaning, so it is the one with a semantic name.
 *
 * Values NOT here, on purpose: 1, 2, 3, 5, 7, 9, 11, 14, 18, 20, 22, 28, 32. Mostly dot offsets and
 * ring geometry, each used once or twice. 14 (22 uses) and 18 (9 uses) are frequent enough to look
 * like tokens and were left out anyway -- no single meaning could be found for either, and a token
 * whose name cannot say what it is for is a number with extra steps.
 */
object Spacing {
    /** Content inset from both screen edges. `padding(start = 24.dp, end = 24.dp)`. */
    val screenGutter = 24.dp

    val xs = 4.dp
    val sm = 6.dp
    val md = 8.dp
    val lg = 10.dp
    val xl = 12.dp
    val xxl = 16.dp
}
