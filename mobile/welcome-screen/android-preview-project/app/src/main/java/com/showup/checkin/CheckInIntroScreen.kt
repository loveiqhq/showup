/*
 * CheckInIntroScreen.kt
 * ShowUp · Check-In 01 (SHOWUP-178): A, the intro -- and B, "date search not available"
 *
 * Two screens, one ticket, from `screen-checkin-intro-reference.jsx`.
 *
 * A IS A BRIDGE, NOT A STEP: no header title, no back, no step bar. The ✕ is the only control
 * besides the CTA, in a 52 close row at the leading inset 16 -- the row replaced the 52 of top
 * padding the headline had, so the headline did not move. One flexible spacer between the closing
 * line and the CTA; the screen never scrolls on the three ticket frames (`CheckInFitTest`), and the
 * scaffold's floor means it scrolls only where the largest system font truly cannot fit.
 *
 * B IS THE SEARCH TAB for a user with no committed Check-In -- not a modal, not an error. A
 * wordmark row, a centred statement, one CTA, and the tab bar with Search active. The tabs are not
 * disabled: Search and Instant reopen the Check-In, as the CTA does.
 */
package com.showup.checkin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.showup.welcome.WORDMARK_TAB_GAP
import androidx.compose.ui.platform.LocalDensity
import com.showup.designsystem.prettyWrap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Cream
import com.showup.designsystem.LilacWash
import com.showup.designsystem.Manrope
import com.showup.designsystem.Muted
import com.showup.designsystem.Neutral
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.Purple
import com.showup.designsystem.Spacing
import com.showup.designsystem.Tab
import com.showup.designsystem.TabBar
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.OrbPlacement
import com.showup.welcome.WashHeadline
import com.showup.welcome.WelcomeScaffold
import com.showup.welcome.Wordmark

/** Fit-harness anchors. */
internal const val INTRO_CLOSE_ROW_TAG = "check-in-intro-close-row"
internal const val INTRO_LIST_TAG = "check-in-intro-list"
internal const val GATE_STATEMENT_TAG = "check-in-gate-statement"

/**
 * A -- the intro. [onStart] is `Check-in for a date` (single-fire in the view model); [onClose] is the
 * ✕ AND Android back: "No back. Suppress iOS swipe-back. Android back = ✕."
 */
@Composable
fun CheckInIntroScreen(onStart: () -> Unit = {}, onClose: () -> Unit = {}) {
    BackHandler(onBack = onClose)
    // The bridges' backdrop recipe -- orange .32 top-right, violet .28 bottom-left, peach wash --
    // full-bleed behind the status and gesture bars. Gutter 16 is the close row's inset; the
    // headline and body add 12 to reach the reference's 28.
    WelcomeScaffold(
        placement = OrbPlacement.Startup,
        orangeAlpha = 0.32f,
        violetAlpha = 0.28f,
        topPadding = 0.dp,
        gutter = 16.dp,
        scrollWhenTight = true,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag(INTRO_CLOSE_ROW_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckInCloseButton(onClose)
        }

        WashHeadline(
            parts = CheckInCopy.INTRO_HEADLINE,
            fontSize = 34.sp,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .testTag(CHECK_IN_HEADLINE_TAG)
                .semantics { heading() },
            lineHeight = (34f * 1.1f).sp,
            cssLineBox = true,
            letterSpacing = (-0.015).em,
            balance = true,
        )

        Column(
            Modifier
                .padding(start = 12.dp, end = 12.dp, top = 28.dp)
                .testTag(INTRO_LIST_TAG),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            CheckInCopy.INTRO_LIST.forEachIndexed { i, line -> NumberedRow(i + 1, line) }
        }

        WashHeadline(
            parts = CheckInCopy.INTRO_CLOSING,
            fontSize = 17.sp,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 22.dp),
            lineHeight = (17f * 1.45f).sp,
            cssLineBox = true,
            // `textWrap: 'pretty'` in the reference.
            pretty = true,
            letterSpacing = 0.em,
            fontWeight = FontWeight.Normal,
            color = Muted,
        )

        // THE ONLY FLEXIBLE ELEMENT. Never a hard-coded Y.
        Box(Modifier.weight(1f))

        PrimaryButton(
            label = CheckInCopy.CHECK_IN_CTA,
            onClick = onStart,
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, bottom = 22.dp)
                .testTag(CHECK_IN_CTA_TAG),
            variant = PrimaryButtonVariant.Sunset,
            trailing = { CheckInArrow() },
        )
    }
}

/**
 * B -- "date search not available": the Search tab with no committed Check-In. [onCheckIn] takes the
 * target, because the CTA, the Search tab and the Instant tab each reopen the Check-In and the
 * event says which; [onSettings] navigates normally. [onLikes] is the Likes tab (SHOWUP-186: in every
 * user's bar, [likes] its badge); where it goes is the host's decision.
 */
@Composable
fun CheckInGateScreen(
    onCheckIn: (CheckInAnalytics.GateTarget) -> Unit = {},
    onSettings: () -> Unit = {},
    onLikes: () -> Unit = {},
    likes: Int = 0,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Cream)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // The top row: 52, padding 4 / 16 / 8, the wordmark at 20 and nothing else.
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 16.dp, end = 16.dp, top = Spacing.xs, bottom = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A logo, at 20 whatever the system font: in a fixed 52 row a 2x wordmark is clipped.
            Wordmark(size = with(LocalDensity.current) { 20.dp.toSp() }, gapEm = WORDMARK_TAB_GAP)
        }

        // THE STATEMENT BLOCK -- the only flexible element, centred both ways. Floored at its own
        // height so the largest system font scrolls it rather than clipping it.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = viewport)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenGutter)
                    .testTag(GATE_STATEMENT_TAG),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The tile is decorative; the headline is the screen's title.
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(LilacWash),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BrandIcon.Calendar, 28.dp, tint = Purple, strokeWidth = 1.9f)
                }
                WashHeadline(
                    parts = CheckInCopy.GATE_HEADLINE,
                    fontSize = 30.sp,
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .testTag(CHECK_IN_HEADLINE_TAG)
                        .semantics { heading() },
                    lineHeight = (30f * 1.12f).sp,
                    cssLineBox = true,
                    letterSpacing = (-0.015).em,
                    balance = true,
                    textAlign = TextAlign.Center,
                )
                val body = remember { AnnotatedString(CheckInCopy.GATE_BODY) }
                val bodyStyle = TextStyle(
                    color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp, lineHeight = (14.5f * 1.5f).sp, textAlign = TextAlign.Center,
                )
                Text(
                    body,
                    Modifier
                        .padding(top = Spacing.xl)
                        .widthIn(max = 310.dp)
                        .prettyWrap(body, bodyStyle, centered = true),
                    style = bodyStyle,
                )
            }
        }

        // The CTA block: padding 0 / 24 / 22.
        PrimaryButton(
            label = CheckInCopy.CHECK_IN_CTA,
            onClick = { onCheckIn(CheckInAnalytics.GateTarget.Cta) },
            modifier = Modifier
                .padding(start = Spacing.screenGutter, end = Spacing.screenGutter, bottom = 22.dp)
                .testTag(CHECK_IN_CTA_TAG),
            variant = PrimaryButtonVariant.Sunset,
            trailing = { CheckInArrow() },
        )

        TabBar(
            active = Tab.Search,
            likes = likes,
            onTab = { tab ->
                when (tab) {
                    Tab.Search -> onCheckIn(CheckInAnalytics.GateTarget.SearchTab)
                    Tab.Likes -> onLikes()
                    Tab.Instant -> onCheckIn(CheckInAnalytics.GateTarget.InstantTab)
                    Tab.Settings -> onSettings()
                }
            },
        )
    }
}

@Preview(name = "Check-In 01 · A · 375", widthDp = 375, heightDp = 667)
@Composable private fun PreviewIntro375() = CheckInIntroScreen()

@Preview(name = "Check-In 01 · A · 430", widthDp = 430, heightDp = 932)
@Composable private fun PreviewIntro430() = CheckInIntroScreen()

@Preview(name = "Check-In 01 · B · 375", widthDp = 375, heightDp = 667)
@Composable private fun PreviewGate375() = CheckInGateScreen()

@Preview(name = "Check-In 01 · B · 320", widthDp = 320, heightDp = 686)
@Composable private fun PreviewGate320() = CheckInGateScreen()
