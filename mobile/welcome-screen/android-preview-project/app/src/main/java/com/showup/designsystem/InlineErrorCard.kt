package com.showup.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The inline failure card: a round danger glyph and a sentence, in a tinted box.
 *
 * ONE COMPONENT ACROSS THE WHOLE PROFILE FLOW
 *
 * The epic names this explicitly as built-once, and the name ticket says the same thing twice: it
 * is "the same component the DoB screen uses, not a re-style". Every value here -- the 10/14
 * padding, the 12 radius, the two danger tints at 7% and 18%, the 18 glyph, Manrope 500 / 13.5 in
 * [DangerFg] -- is shared by name, email and date of birth. A fourth screen inventing a fifth
 * version is the failure this exists to prevent.
 *
 * The message is an [AnnotatedString] rather than a String because the email screen renders part of
 * its copy as a mono chip -- the missing `@` or `.com` -- inside the same sentence. Every other
 * screen passes plain text and gets exactly the same card.
 *
 * WHAT THIS DELIBERATELY DOES NOT DO
 *
 * It does not own the region it sits in. Name and verify-email RESERVE a fixed height around it so
 * nothing moves between states; email deliberately does not, because reserving the taller height
 * there would push the CTA into the keyboard. That difference belongs to the screens, and a card
 * that reserved its own space would take the choice away from them.
 *
 * It also does not announce itself: `aria-live` is a property of the region, not the card, and the
 * screens set it on the container so an empty region still has a live announcer attached.
 */
@Composable
fun InlineErrorCard(
    message: AnnotatedString,
    modifier: Modifier = Modifier,
    /**
     * Ceiling on the message, for a screen whose reserved region is fixed.
     *
     * Unbounded by default, which is right for the profile screens: their copy is one short
     * sentence and their region is sized to the card. The phone screen is the exception -- its
     * messages name a country and an example number, so they can run to three lines, and its
     * region is a fixed height that keeps the CTA still. Two lines there is not a new decision,
     * it is the ceiling that screen already had before the card existed.
     */
    maxLines: Int = Int.MAX_VALUE,
) {
    val shape = RoundedCornerShape(Radius.errorBox)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Danger.copy(alpha = 0.07f))
            .border(1.dp, Danger.copy(alpha = 0.18f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        // marginTop 1 in the reference: the glyph's optical centre sits a hair below the first
        // line's cap height, and without it the circle reads as riding high.
        Box(Modifier.padding(top = 1.dp)) {
            DangerGlyph(size = 18.dp, glyphSize = 12.dp)
        }
        Text(
            message,
            color = DangerFg,
            fontFamily = Manrope,
            fontWeight = FontWeight.Medium,
            fontSize = 13.5.sp,
            lineHeight = (13.5f * 1.4f).sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Plain-text overload -- every screen except email passes a bare sentence. */
@Composable
fun InlineErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) = InlineErrorCard(AnnotatedString(message), modifier, maxLines)
