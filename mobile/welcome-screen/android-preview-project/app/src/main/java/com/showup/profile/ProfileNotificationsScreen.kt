/*
 * ProfileNotificationsScreen.kt
 * ShowUp · Profile creation 09 — Notifications permission ask (SHOWUP-162)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ONE STATE, NO INPUT, NOTHING THAT CAN FAIL
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * No denied state, no recovery banner, no "asking" state, no error card. Profile creation runs
 * once on a FRESH INSTALL, and a fresh install has no notification permission on either platform,
 * so the status here is always `not determined` and the screen has exactly one path.
 *
 * The two cases where it is not shown are decided BEFORE it is pushed and never after it mounts --
 * Android 12 and below, which has no runtime permission to ask for, and the guard case of a status
 * that is somehow already determined. Neither is a state of this screen; both live in the host.
 * See [NotificationAccess].
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TWO NAMED EXCEPTIONS, AND THEY ARE THE BRIDGE'S OWN
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * RULE 5 SAYS NO AMBIENT BACKDROP on a profile screen, because every other one has the keyboard
 * open and a glow behind the keys reads as noise. This screen has no keyboard and no input, so it
 * carries the first-run atmosphere -- orange orb, violet orb, 360 peach wash -- exactly as the
 * embrace bridge does. That is why it uses [WelcomeScaffold] rather than a basics scaffold.
 *
 * RULE 7 SAYS THE CTA IS ORANGE. Granting push is a commitment beat, so it is the FULL-WIDTH
 * sunset [PrimaryButton].
 *
 * Both exceptions were extended to this screen on 21 September 2026 and to nothing else. The flow
 * README's rules 5 and 7 now read "the two bridges and the notifications ask".
 *
 * ONE DIFFERENCE FROM THE BRIDGE: there is no trailing arrow on this CTA. Screen 05's button ends
 * in an 18 arrow-right and this one does not -- the label is the whole button. Small, easy to
 * carry over by habit, and called out in the ticket for exactly that reason.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE ABSENCES ARE THE DESIGN
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *   · NO AppHeader -- no title, no back chevron, no skip, no close, no reserved slots
 *   · NO StepProgress -- it is in neither progress bar
 *   · NO "Not now" -- the OS sheet's own decline is the way past, and the flow advances either way
 *   · NO back of any kind -- no chevron, no gesture, no hardware key
 *
 * `audit/verify-profile.py` asserts the absences so a later tidy-up cannot reintroduce them.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * IT DOES NOT SCROLL, AND IT IS THE TIGHTEST CONTENT IN THE FLOW
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * A 32 headline, a lead paragraph and five two-line rows. There is ONE flexible element -- the
 * spacer below the list -- and every device difference lands in it.
 *
 * IT DOES NOT FIT EVERYWHERE, and that is measured rather than feared. Across the seventeen frames
 * this project ships to, at the DEFAULT font three of them come up short -- the Galaxy Fold cover
 * screen by 169, a 360 x 640 Android by 98, and the iPhone SE by 5. At 1.3x type thirteen of the
 * seventeen miss; at 2.0x all of them do.
 *
 * The ticket's agreed order of sacrifice -- spacer, list gap 16 to 14, row line 13.5 to 13, then
 * cut a row -- is worth about 15dp before that last step: 8 from the gaps and 7 from the lines.
 * That closes the SE and nothing else, and cutting a row is a content decision the ticket reserves
 * in as many words ("then come back and cut a row").
 *
 * So the approved values are kept, THE CTA IS PINNED BELOW THE SCROLL, and only the list moves.
 * On the fourteen frames where it fits, nothing scrolls and the layout is unchanged. See the note
 * at the scaffold call for the whole argument, and E20 in `audit/CONFLICTS-2026-08-27.md` for the
 * decision it leaves with the design side.
 *
 * Scrolling the CTA with the content was the first attempt and it was wrong: it made the button
 * REACHABLE rather than VISIBLE, and a Galaxy Fold opened this screen with no button drawn on it.
 * The iOS fit sweep caught that as a hard failure and the Android one did not, because BELOW THE
 * FOLD is only an advisory once a screen scrolls -- so `NotificationsFitTest` now asserts the CTA
 * is inside the viewport on every device, at every font scale, with no advisory to hide behind.
 *
 * `NotificationsFitTest` measures the spacer on every device, and the screen is in the 17-size
 * sweep at three font scales.
 */
package com.showup.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Fg
import com.showup.designsystem.LilacWash
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.Purple
import com.showup.designsystem.SunsetStops
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline
import com.showup.welcome.WelcomeScaffold

/**
 * The CTA, for the fit harness.
 *
 * A tag rather than a text match: the harness measures the BUTTON's bounds, and the label inside
 * it is a child with its own smaller box. Asking for the text would measure the wrong rectangle
 * and pass while the button itself hung off the bottom of the frame.
 */
internal const val CTA_TAG = "notifications-cta"

/**
 * The benefit list, for the fit harness.
 *
 * The spacer is the one number the spec sheet states and it is not a node anything can query, so
 * it is measured as the gap between this box's bottom and [CTA_TAG]'s top. Anchoring on the last
 * row's TEXT instead reads 3 low -- the row is a 40 pip beside a text column and the last line box
 * does not end where the row does -- which is exactly enough to make the 12 floor look broken when
 * it is not.
 */
internal const val LIST_TAG = "notifications-benefits"

/** Row 2's Premium pill, for the fit harness. */
internal const val TAG_TAG = "notifications-premium"

/**
 * Every string on the screen, verbatim from the ticket.
 *
 * The em dashes are SPACED EM DASHES, not hyphens, and `e.g.` is lower case with both points --
 * the ticket says so outright, and `verify-profile.py` quotes it back.
 */
internal object NotificationsCopy {
    const val HEADLINE_LEAD = "Never miss "
    const val HEADLINE_EM = "a date"
    const val HEADLINE_TAIL = " with Notifications!"

    const val LEAD =
        "No spam! Every notification is about your dates and helps you to never miss one."

    const val CTA = "Enable notifications"

    /** Row 2's label. Always capitalised, always this one word, never an upsell. */
    const val PREMIUM = "Premium"
}

/**
 * One of the five things push is for.
 *
 * FIVE, NOT SIX, and the order is content rather than layout: row 1 is the reason the user is in
 * the flow at all and row 5 is the one that protects their time. An earlier draft had a sixth
 * ("Match Mate reply") and it was cut -- do not re-add it and do not re-order.
 */
internal data class NotifyBenefit(
    val icon: BrandIcon,
    val title: String,
    val line: String,
    val tag: String? = null,
)

internal val NOTIFY_BENEFITS = listOf(
    NotifyBenefit(
        BrandIcon.Heart,
        "Match alert",
        "Get instant notifications when you receive a match and never miss out on a date.",
    ),
    NotifyBenefit(
        BrandIcon.Sparkles,
        "Like received",
        "Don't miss your chance to meet — we surface it the second it arrives.",
        tag = NotificationsCopy.PREMIUM,
    ),
    NotifyBenefit(
        BrandIcon.MessageCircle,
        "Meeting details change",
        "Get notified if your date asks — e.g. meet time change, running late.",
    ),
    NotifyBenefit(
        BrandIcon.Clock,
        "Date reminder",
        "A heads-up an hour out — never arrive late.",
    ),
    NotifyBenefit(
        // The kit's `x`, which this project already draws as [BrandIcon.Close] at the kit's own
        // geometry. Not a new glyph.
        BrandIcon.Close,
        "Date cancelled",
        "Never waste time waiting — get notified, look for someone else instead.",
    ),
)

/**
 * The sunset `Premium` pill on row 2.
 *
 * A LABEL, NOT A CONTROL. It is not clickable, opens nothing and has no paywall behind it -- and
 * it is read as part of row 2's title rather than as a thing of its own, which is why the row
 * above merges its semantics rather than this drawing its own.
 */
@Composable
private fun PremiumTag(modifier: Modifier = Modifier) {
    Box(
        modifier
            // `transform: translateY(-1px)` in the reference, which is an OFFSET and not a
            // padding: it moves the pill and takes part in no layout. Spelling it as a bottom
            // padding made the tag's box 1 taller and moved the pill half as far, which is the
            // kind of difference that is invisible until a row wraps.
            .offset(y = (-1).dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Brush.linearGradient(colorStops = SunsetStops.toTypedArray()))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            NotificationsCopy.PREMIUM.uppercase(),
            color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp, letterSpacing = 0.06.em,
        )
    }
}

/**
 * One benefit row: a lilac pip, a serif title, and a line underneath.
 *
 * THE PIP IS THE MEDIA CARD'S RECIPE, not a second one -- a lilac square with a primary-500 glyph,
 * which is what makes profile creation read as one system from the photos grid to here. The
 * numbers are this screen's own (40 at radius 12, against the media card's 42 at 13): the
 * reference wins on numbers and "same recipe" is about the vocabulary, not the measurements.
 *
 * ONE GROUP FOR A SCREEN READER. Title and line read together and the icon is decorative, so the
 * row merges rather than announcing three unrelated fragments; `Premium` is part of row 2's title.
 */
@Composable
private fun BenefitRow(benefit: NotifyBenefit) {
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LilacWash)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Icon(benefit.icon, 20.dp, tint = Purple, strokeWidth = 1.8f)
        }
        // paddingTop 1 in the reference: the serif title's cap-height sits a fraction below the
        // pip's optical centre without it.
        Column(Modifier.padding(top = 1.dp)) {
            // BASELINE, NOT CENTRE. The reference sets `alignItems: 'baseline'`, and the
            // difference is visible: a 10 uppercase pill centred against a 16 serif sits low,
            // which is exactly what its -1 nudge is correcting for. Compose expresses baseline
            // alignment per child rather than on the Row.
            //
            // THE TITLE IS THE WEIGHTED ONE, AND THAT IS THE WHOLE BUG IT FIXES. The spec sheet
            // has this row at `flex-wrap: wrap` -- "the tag rides here" -- and a Compose Row does
            // not wrap; it shares the width, and the order it measures in decides who loses. An
            // unweighted Text is measured FIRST at the full width, so at 2.0x type on a 320 frame
            // the title took everything and the pill was left 25dp of the 123 it needed, with
            // PREMIUM ellipsised inside a stub of a capsule. Nothing overflowed, so no fit sweep
            // could see it. Weighted, the pill is measured at its own size first and the title
            // wraps into what is left -- which is what the wrap would have done anyway.
            //
            // `fill = false` so a short title does not stretch to the full width and drag the
            // pill out to the margin.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    benefit.title,
                    modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                    color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, lineHeight = (16f * 1.2f).sp,
                    letterSpacing = (-0.005).em,
                )
                if (benefit.tag != null) PremiumTag(Modifier.alignByBaseline().testTag(TAG_TAG))
            }
            Text(
                benefit.line,
                modifier = Modifier.padding(top = 3.dp),
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp, lineHeight = (13.5f * 1.4f).sp,
            )
        }
    }
}

/**
 * The screen. One state and one callback.
 *
 * [onEnable] raises the OS sheet. It is not a variant and there is nothing else to press.
 */
@Composable
fun ProfileNotificationsScreen(
    onEnable: () -> Unit = {},
    /**
     * Swallows a second press for the lifetime of the OS sheet.
     *
     * The CTA is NEVER DISABLED -- it never greys out and never changes -- it simply stops
     * answering while the sheet is up. A second request no-ops silently at the platform level, so
     * a double tap that looked like a hang would be a hang we invented.
     */
    busy: Boolean = false,
) {
    // No chevron, no gesture, no hardware key. Profile creation is mandatory once entered, and a
    // screen with no visible way back that the OS can still dismiss is worse than no rule at all.
    BackHandler(enabled = true) { /* deliberately nothing */ }

    // topPadding 40 and gutter 28 are the headline block's `padding: '40px 28px 0'`. The 28 gutter
    // is the bridge's, not the group's 24, for the same reason: no input field to align to.
    // `scrollWhenTight`, AND THE TICKET SAYS "NEVER LET IT SCROLL". Both are true and the conflict
    // is real, so here is the whole of it.
    //
    // This is the most content on any non-scrolling screen in the flow -- a 32 headline, a lead
    // paragraph and five two-line rows -- and measured across the seventeen frames this project
    // ships to, three of them come up short at the DEFAULT font: the Galaxy Fold cover screen by
    // 169, a 360 x 640 Android by 98, and the iPhone SE by 5. At 1.3x type thirteen of the
    // seventeen miss, and at 2.0x all of them do.
    //
    // The ticket's agreed order of sacrifice -- spacer, then list gap 16 to 14, then the row line
    // 13.5 to 13, then cut a row -- is worth about 15dp before the last step: 8 from the four
    // gaps and 7 from the ten lines. That closes the SE and nothing else, and cutting a row is a
    // content decision this ticket explicitly reserves -- "then come back and cut a row".
    //
    // So: the approved values are kept everywhere, and the scaffold carries the sizes the design
    // was not drawn for in the two ways it has. It changes NOTHING where the screen fits -- the
    // inner column is floored at the viewport height, so with slack there is nothing to scroll and
    // the layout is byte-for-byte the same.
    //
    // THE CTA IS IN `footer`, NOT IN THE SCROLL, and that is the half that matters. Scrolling
    // alone makes the button reachable and leaves it invisible: on the Fold this opened with the
    // whole button below the fold and nothing on screen saying so, which on a PERMISSION ASK
    // reads as a broken screen rather than a scrollable one. Pinned, the band is the bottom of
    // the same column -- same gutter, same 18 above the gesture bar -- so on the frames that fit
    // it is exactly where it already was, and on the three that do not the list scrolls beneath a
    // button that never leaves.
    //
    // Recorded for the design side rather than settled here: at 320 and at accessibility type
    // sizes, five two-line rows cannot fit, and the real choice is this scroll or a fourth row.
    WelcomeScaffold(
        topPadding = 40.dp,
        gutter = 28.dp,
        scrollWhenTight = true,
        // Sunset and full width, rule 7's named exception. NO TRAILING ICON: screen 05's button
        // ends in an arrow and this one does not.
        footer = {
            PrimaryButton(
                label = NotificationsCopy.CTA,
                onClick = { if (!busy) onEnable() },
                modifier = Modifier.padding(bottom = 18.dp).testTag(CTA_TAG),
                variant = PrimaryButtonVariant.Sunset,
            )
        },
    ) {
        WashHeadline(
            parts = listOf(
                NotificationsCopy.HEADLINE_LEAD to false,
                NotificationsCopy.HEADLINE_EM to true,
                NotificationsCopy.HEADLINE_TAIL to false,
            ),
            fontSize = 32.sp,
            lineHeight = (32f * 1.1f).sp,
            letterSpacing = (-0.015).em,
            // `text-wrap: balance`, which the reference sets and the acceptance criteria name.
            // At 390 a greedy wrap gives `Never miss a date with` / `Notifications!` and the
            // artboard shows `Never miss a date` / `with Notifications!`.
            balance = true,
        )

        // The body block's own 20 top padding. The headline block has no bottom padding, so this
        // single value is the whole gap between them.
        Text(
            NotificationsCopy.LEAD,
            modifier = Modifier.padding(top = 20.dp),
            color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 15.sp, lineHeight = (15f * 1.55f).sp,
        )

        Column(
            Modifier.padding(top = 22.dp).testTag(LIST_TAG),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NOTIFY_BENEFITS.forEach { BenefitRow(it) }
        }

        // THE ONLY FLEXIBLE ELEMENT ON THE SCREEN, with a 12 floor. Every device difference lands
        // here and nothing else moves: about 95 at 390 x 844, and the floor itself at 375 x 667.
        // One spacer, never two -- a second one would split the surplus and nothing would be
        // anchored to anything.
        // `requiredHeightIn`, NOT `heightIn`. The reference is `flex: 1; min-height: 12`, and the
        // obvious spelling loses the floor silently: `weight` hands down a FIXED height, and
        // `heightIn` can only coerce inside the constraints it is given, so a 9dp leftover clamps
        // the 12 minimum down to 9. Measured on an iPhone SE, where it produced exactly that.
        // `requiredHeightIn` ignores the incoming constraint, which is what the CSS means -- the
        // column overflows instead, and the scroll above catches it.
        //
        // IT STAYS IN THE CONTENT even though the CTA no longer is: with slack it is the thing
        // that holds the button at the bottom of the screen, and with none it is the 12 of air
        // between the last row and the pinned band once the user has scrolled to the end.
        Spacer(Modifier.weight(1f).requiredHeightIn(min = 12.dp))
    }
}

// ── previews: the three frames the ticket asks for, plus the two that break things ──────────
//
// 375 x 667 is where the spacer hits its floor and is checked first; 430 x 932 is where the
// surplus lands. 320 x 686 is not in the ticket and is in the fit sweep anyway -- it is here
// because it is the frame that has actually broken every screen in this flow.

@Preview(name = "Notifications · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PN375() { ProfileNotificationsScreen() }

@Preview(name = "Notifications · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PN390() { ProfileNotificationsScreen() }

@Preview(name = "Notifications · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PN430() { ProfileNotificationsScreen() }

@Preview(name = "Notifications · 320", showBackground = true, widthDp = 320, heightDp = 686)
@Composable private fun PN320() { ProfileNotificationsScreen() }

@Preview(
    name = "Notifications · 375 · large type", showBackground = true,
    widthDp = 375, heightDp = 667, fontScale = 1.3f,
)
@Composable private fun PN375Large() { ProfileNotificationsScreen() }
