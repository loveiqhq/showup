package com.showup.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Which tone a [StatusBadge] wears.
 *
 * Two, because the design uses two and no more: the sign-up flow's badges are orange and the
 * tutorial's are lavender. This is the case `CLAUDE.md` describes -- "a component's tone is per
 * screen, and both tones stay" -- so the tone is a variant rather than a token that one screen
 * could redefine and break the other.
 *
 * It controls the fill and the label colour, and deliberately NOT the dot, which is [Orange] in all
 * three implementations this replaced, lavender badge included.
 */
enum class BadgeTone {
    /** Sign-up flow: the phone screens and Connect. */
    Orange,

    /** The tutorial cards. */
    Lavender,
}

/**
 * The small pill above a headline: a 5dp dot and a short uppercase label.
 *
 * WHAT THIS REPLACED
 *
 * Three implementations per platform, six in all, drawing the same pill:
 *
 *  * `Eyebrow` in `ConnectAccountScreen` -- orange, and the only one that did not use a token
 *  * `EyebrowPill` in `TutorialShell` -- lavender
 *  * `Eyebrow` in `PhoneVerificationScreen` -- orange, label hardcoded
 *
 * They agreed on the shape, the 5dp dot, the padding, the gap and the 11sp bold label, and had
 * already drifted in four ways: the fill (see below), the letter spacing written as `0.08.em` in
 * two of them and `0.88.sp` in the third -- the same number at 11sp -- a `lineHeight` set on one,
 * and a hardcoded label on another.
 *
 * THE FILL, WHICH IS THE ONE THAT MATTERED
 *
 * Connect painted `Orange.copy(alpha = 0.12f)` while a token for exactly that value already
 * existed. [EyebrowOrangeBg] is `0x1FFE6839`, and `0x1F` is 31, which is `0.12 * 255` rounded --
 * so the two are the same pixels reached by two routes, one of them re-derivable and one of them
 * not. That is how a token stops being the single definition of a value without anything looking
 * wrong.
 *
 * IT DOES NOT POSITION ITSELF
 *
 * Two of the three were `ColumnScope` extensions that called `.align(Alignment.Start)` on
 * themselves. That is what stopped Connect from reusing one of them -- Connect's badge is CENTRED,
 * inside a Column with `horizontalAlignment = CenterHorizontally`, and a component that insists on
 * its own alignment cannot go there. Alignment is the parent's business and reaches this through
 * [modifier].
 */
@Composable
fun StatusBadge(
    /** Shown uppercased. All three implementations uppercased, two by call and one by literal. */
    label: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Orange,
) {
    val fill: Color
    val labelColor: Color
    when (tone) {
        BadgeTone.Orange -> {
            fill = EyebrowOrangeBg
            labelColor = Orange
        }
        BadgeTone.Lavender -> {
            fill = EyebrowBg
            labelColor = Purple
        }
    }

    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(fill)
            .padding(horizontal = Spacing.lg, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Orange in every tone, including lavender. Not an oversight in the originals -- all three
        // drew it this way, so it is preserved rather than tidied into the variant.
        Box(Modifier.size(5.dp).background(Orange, CircleShape))
        Text(
            label.uppercase(),
            color = labelColor,
            fontFamily = Manrope,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            // 0.08em, which is 0.88sp at 11sp. The third implementation wrote the resolved number;
            // the em form is kept because it is the one that survives a font-size change.
            letterSpacing = 0.08.em,
            // NO lineHeight, though the tutorial's pill set 13.sp.
            //
            // Dropped on measurement, not on taste. Both spellings render a label exactly 15.00dp
            // tall at every width -- 13sp is below what this font's ascent and descent need at
            // 11sp, so Compose was already ignoring it. Carrying it as a parameter would have been
            // a knob that changes nothing, which is worse than no knob.
        )
    }
}
