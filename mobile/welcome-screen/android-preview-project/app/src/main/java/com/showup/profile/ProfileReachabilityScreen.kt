/*
 * ProfileReachabilityScreen.kt
 * ShowUp · Profile creation 10 — Stay reachable, MVP (SHOWUP-163)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * scope="mvp". ONLY.
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The reference file renders two scopes and the ticket's first acceptance criterion is that
 * NOTHING FROM `scope="full"` SHIPS: no AI-concierge card, no calendar card, no phone-number
 * field, no email row, and no WhatsApp/SMS as live toggles. Those are the backlog ticket
 * (`10-reachability-later.md`) and they are kept in the reference so nothing is lost, not so they
 * can be built early.
 *
 * What ships is one card: head → a divider → the interest heading → three checkbox rows.
 * Plus a headline, fine print, and a pinned CTA.
 *
 * THE HEAD IS THE PUSH CONTROL, since 28 September 2026. It used to introduce notifications and
 * then offer a separate row to switch them on -- two titles for one thing, and about sixty points
 * of height that put the fine print below the fold on the 393 x 852 the ticket now targets.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE BODY SCROLLS AND THE CTA NEVER DOES
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * StatusBar → headline (`flex: none`) → body (`flex: 1`, `min-height: 0`, `overflow: auto`) →
 * pinned footer → HomeIndicator. This is exactly the shape `WelcomeScaffold(footer =)` grew for
 * the notifications ask, and for the same reason: a CTA that scrolls away is a CTA the user
 * cannot find. The difference is that here it is the DESIGN rather than a fallback -- the
 * reference marks the body `overflow: auto` itself.
 *
 * The fine print has to scroll clear of the CTA, so the body carries bottom padding at least the
 * footer's height. Measured, not guessed: see [FooterClearance].
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TWO JOBS, ONE LIVE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Push is the only consent. The three interest rows are a demand test -- see [ReachabilityModel].
 * Checking one fires `channel_interest_changed` and NOTHING ELSE: no dialog, no field, no
 * "coming soon" toast, no navigation, no server write, and never `consent_changed`.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE ABSENCES ARE THE DESIGN, AGAIN
 * ─────────────────────────────────────────────────────────────────────────────
 *
 *   · NO AppHeader, NO StepProgress -- not a step, in neither progress bar
 *   · NO skip, NO close, NO way backwards
 *   · NO "Skip for now" link -- it is in the reference and removed from the MVP
 *
 * `audit/verify-profile.py` asserts each one, so a later consistency pass cannot reintroduce them.
 */
package com.showup.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.BorderSoft
import com.showup.designsystem.Cream
import com.showup.designsystem.Danger
import com.showup.designsystem.Elevated
import com.showup.designsystem.ComponentSizes
import com.showup.designsystem.ConsentSwitch
import com.showup.designsystem.Fg
import com.showup.designsystem.FgSubtle
import com.showup.designsystem.LilacWash
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Neutral
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.Purple
import com.showup.designsystem.Raised
import com.showup.designsystem.InlineErrorCard
import com.showup.designsystem.minTapTarget
import kotlin.math.min
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline
import com.showup.welcome.WelcomeScaffold

/** The CTA, for the fit harness. Tagged rather than matched on its label -- see 162's CTA_TAG. */
internal const val REACH_CTA_TAG = "reachability-cta"

/** The scrolling body's last element, so the harness can prove the fine print clears the CTA. */
internal const val REACH_FINEPRINT_TAG = "reachability-fineprint"

/** The privacy link's annotation, so a test can find the run rather than the paragraph. */
internal const val REACH_PRIVACY_LINK_TAG = "reachability-privacy"

/** The save-failure card, which exists on no other path and so cannot be found by its copy. */
internal const val REACH_ERROR_TAG = "reachability-save-failed"

/**
 * How much room the pinned footer needs below the scrolling body.
 *
 * 56 button + 10 top + 6 bottom from the reference's footer padding, plus the fade. The
 * acceptance criterion is that "the fine print scrolls fully clear of the CTA", and the only way
 * that is true at every font scale is for the body to reserve the footer's height rather than a
 * number that looked right at 390.
 */
private val FooterClearance = 80.dp

/** Every string on the screen, verbatim from the ticket. Typographic apostrophes throughout. */
internal object ReachCopy {
    const val HEADLINE_EM = "Never miss"
    const val HEADLINE_TAIL = " a date and avoid getting a penalty!"

    /**
     * The lead, with the reference file's own bold runs.
     *
     * `Show-up Rate` -- NEVER "Show-Up Rate". The ticket says so twice and it is a product term.
     */
    const val LEAD_PLAIN =
        "Activate notifications so you never miss a date! Missing a date lowers your Show-up " +
            "Rate, which will lead to a temporary ban or permanent suspension from the app."

    // CHANGED 28 Sep 2026. The separate push row is gone and its title became the card's, so
    // the head names the one thing the card switches instead of naming the category twice.
    const val CARD_TITLE = "Push notifications"
    const val CARD_LINE =
        "We’ll let you know about new matches, meet time or location changes and date " +
            "cancellations."

    /**
     * The switch's accessibility name.
     *
     * Was the separate push row's visible label until 28 Sep 2026, when that row was folded into
     * the card head. The string survives as the switch's name because [CARD_TITLE] is now a
     * sibling of the switch rather than its label, and a switch announced only as "on" says
     * nothing about what it governs.
     */
    const val PUSH_ROW = "Push notifications"
    const val RECOMMENDED = "Recommended"

    const val INTEREST_HEADING = "More ways to reach you are coming soon."
    const val INTEREST_LINE = "Tell us which ones you’d like."

    const val FINE_PRINT =
        "We use this only to coordinate your dates — never for marketing, and we never sell " +
            "your data. Turn any of these off whenever you like in Settings."

    /** The one word in the fine print that is dark rather than subtle. Plain text, never a link. */
    const val SETTINGS_WORD = "Settings"

    const val PRIVACY_LINK = "Read our Privacy Policy"

    const val CTA = "Save preferences"

    /**
     * The save-failure message.
     *
     * COPY IS OPEN IN THE TICKET -- "Open: The save-failure error copy. Use the flow's shared
     * inline error card above the CTA." The card is specified; the words are not, so these are
     * the flow's existing failure voice rather than a new register, and they are recorded in the
     * conflicts file as needing a copy decision.
     *
     * It says what failed and what to do, and it does NOT apologise or blame the connection --
     * the write can fail for reasons that have nothing to do with the user's signal, and naming
     * one guess as the cause sends people to turn their wifi off and on.
     */
    const val SAVE_FAILED = "We couldn't save your preferences. Please try again."

    // ── the deactivation confirm ────────────────────────────────────────────
    const val CONFIRM_TITLE = "Are you sure?"
    const val CONFIRM_LEAD = "Please remember:"
    const val CONFIRM_BODY =
        "Missing a date lowers your Show-up Rate, which will lead to a temporary ban or " +
            "permanent suspension from the app. Keep notifications active to avoid any chances " +
            "of missing a date."
    const val CONFIRM_KEEP = "Keep active"
    const val CONFIRM_SETTINGS = "Open Settings"
    const val CONFIRM_DEACTIVATE = "Confirm deactivation"
}

/** The words the reference file sets in bold, in the lead and in the dialog body. */
private val LeadBold = listOf(
    "Activate notifications", "never miss a date!", "Missing", "lowers", "Show-up Rate",
    "will", "temporary", "permanent", "suspension", "app",
)

/**
 * The lead and the dialog body, with the reference's bold runs applied.
 *
 * MATCHED ON THE STRING, not hand-split into spans. The copy is one sentence in the ticket and
 * splitting it into a list of fragments in source is how a copy edit silently loses a word -- the
 * fragments would still compile.
 */
private fun emphasised(text: String, bold: List<String>) = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val hit = bold
            .mapNotNull { w -> text.indexOf(w, i).takeIf { it >= 0 }?.let { it to w } }
            .minByOrNull { it.first }
        if (hit == null) {
            append(text.substring(i)); break
        }
        val (at, word) = hit
        append(text.substring(i, at))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(word) }
        i = at + word.length
    }
}

/**
 * One interest row: pip, name, optional sub-line, checkbox.
 *
 * THE WHOLE ROW IS THE TARGET, not the 22dp square. The ticket makes it an acceptance criterion
 * and the reference makes the row itself the `<button role="checkbox">`.
 */
@Composable
private fun InterestRow(
    channel: InterestChannel,
    on: Boolean,
    onToggle: () -> Unit,
    first: Boolean,
) {
    val title = when (channel) {
        InterestChannel.AiCall -> "Phone call from our AI assistant"
        InterestChannel.WhatsApp -> "WhatsApp"
        InterestChannel.Sms -> "SMS"
    }
    val sub = if (channel == InterestChannel.AiCall) {
        "A short automated call when something changes."
    } else {
        null
    }
    val icon = when (channel) {
        InterestChannel.AiCall -> BrandIcon.PhoneCall
        InterestChannel.WhatsApp -> BrandIcon.WhatsApp
        InterestChannel.Sms -> BrandIcon.MessageCircle
    }

    Row(
        Modifier
            .fillMaxWidth()
            .then(if (first) Modifier else Modifier.topHairline())
            .minTapTarget()
            // `toggleable`, NOT `clickable(role = Role.Checkbox)`.
            //
            // The two look the same in source and are not: `clickable` with a role announces the
            // CONTROL TYPE and nothing else, so TalkBack read "Phone call from our AI assistant,
            // checkbox" whether the box was ticked or not. Checked state is carried by
            // `ToggleableState`, which only `toggleable` sets -- and a checkbox that never says
            // whether it is checked is the whole control, lost, to a screen reader user. The same
            // modifier is what `ConsentSwitch` uses two rows above for the same reason.
            .toggleable(
                value = on,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics(mergeDescendants = true) {}
            // 9, AND THE FLOOR STAYS THE SHARED ONE.
            //
            // The ticket tightens the padding and adds "(the row stays >= 52pt tall)", which
            // reads like a floor and is not one -- it is what the row MEASURES once the padding
            // changes: a 34dp pip between two 9s. The reference confirms it, keeping
            // `minHeight: 44` untouched through the same edit.
            //
            // Writing 52 here, as the first pass did, would have been a literal invented from a
            // parenthesis and a second standard tap height competing with the token. The
            // guarantee is real and belongs in a test, which is where it now is -- see
            // `ReachabilityFitTest`.
            .heightIn(min = ComponentSizes.minTapTarget)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(LilacWash)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) { Icon(icon, 17.dp, tint = Purple, strokeWidth = 1.8f) }

        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp, lineHeight = (15f * 1.3f).sp,
            )
            if (sub != null) {
                Text(
                    sub,
                    modifier = Modifier.padding(top = 2.dp),
                    color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp, lineHeight = (12.5f * 1.4f).sp,
                )
            }
        }

        // The box is DECORATION: the row carries the role and the checked state, so a screen
        // reader announces one control rather than a row and a box that disagree.
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (on) Purple else Color.White)
                .border(
                    1.5.dp,
                    if (on) Purple else Border,
                    RoundedCornerShape(7.dp),
                )
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(BrandIcon.Check, 14.dp, tint = Color.White, strokeWidth = 3f)
        }
    }
}

/**
 * `borderTop: 1px solid var(--liq-border-soft)` — every row but the first, and the divider above
 * the demand test.
 *
 * DRAWN RATHER THAN A `Divider`, because it has to sit on the row's own top edge inside the same
 * padding. A composable divider between the rows would take part in the column's arrangement and
 * push the 12dp row padding apart by its own height.
 *
 * ONE PHYSICAL PIXEL, not one dp. A hairline asked for in dp is 3px on a 2.625x phone, which is a
 * rule rather than a hairline.
 */
private fun Modifier.topHairline() = this.drawBehind {
    drawLine(
        BorderSoft,
        start = Offset(0f, 0f),
        end = Offset(size.width, 0f),
        strokeWidth = 1f,
    )
}

/**
 * The screen.
 *
 * Takes values and returns pixels. Every callback is a lambda and nothing here knows what a
 * ViewModel is, which is what keeps the four states renderable at seventeen sizes with no device.
 */
@Composable
fun ProfileReachabilityScreen(
    state: ReachabilityState = ReachabilityState(),
    onPushChange: (Boolean) -> Unit = {},
    onInterestToggle: (InterestChannel) -> Unit = {},
    onKeepActive: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onConfirmDeactivate: () -> Unit = {},
    onPrivacy: () -> Unit = {},
    onSave: () -> Unit = {},
    /**
     * The body's scroll position.
     *
     * A PARAMETER ONLY SO THE EVIDENCE CAMERA CAN PLACE IT -- the acceptance criteria ask for
     * this screen at 375 x 667 "scrolled to the top and to the bottom", and the camera draws into
     * a raw `ComposeView` with no gesture to send. Every other caller takes the default and is
     * unaffected.
     */
    scrollState: ScrollState = rememberScrollState(),
) {
    // No chevron, no gesture, no hardware key -- as on 09. Profile creation is mandatory once
    // entered, and a screen the OS can dismiss but the design cannot is worse than no rule.
    BackHandler(enabled = true) { /* deliberately nothing */ }

    Box(Modifier.fillMaxSize()) {
    // `aria-modal`, WHICH IS NOT THE SAME AS "a scrim is drawn over it".
    //
    // The scrim swallows touches, so the screen behind is already untappable -- and it was still
    // fully navigable by TalkBack and VoiceOver, which do not route through a touch. A screen
    // reader user could reach past the dialog, flip the push switch that the dialog exists to ask
    // about, and tick interest boxes, all while the confirm sat on top unanswered. Four operable
    // controls behind a modal, and nothing visual about it to notice.
    //
    // `clearAndSetSemantics {}` is how Compose says "this subtree is not here": it removes the
    // whole tree from the accessibility graph rather than hiding it, which is what modality
    // means. It is applied to the scaffold only, so the dialog -- a sibling in this Box -- keeps
    // its own semantics and is the only thing reachable.
    val behindModal = if (state.prompt != null) {
        Modifier.clearAndSetSemantics {}
    } else {
        Modifier
    }
    Box(behindModal) {
    WelcomeScaffold(
        // THE TWO BLOCKS DO NOT SHARE A GUTTER. The reference has the headline block at
        // `padding: '32px 28px 0'` and the body at `'20px 24px 8px'` -- the card is four wider
        // than the text above it, which is what stops it reading as an indent.
        //
        // The scaffold carries the narrower of the two and the headline pads the difference.
        // 20, NOT 32, SINCE 28 September 2026 -- and every value in this file that the
        // ticket's "Updated" section names moved with it. They are one change, not seven: the
        // goal is that on 393 x 852 at default type the headline through `Save preferences` is
        // on screen without scrolling, in all four states. Anything given back here is height
        // the fine print gets at the bottom.
        topPadding = 20.dp,
        gutter = 24.dp,
        scrollWhenTight = true,
        scrollState = scrollState,
        footer = {
          // `padding: '10px 24px 6px'` with `background: linear-gradient(180deg,
          // rgba(255,251,247,0) 0%, var(--liq-bg) 34%)`. FULL-BLEED, which is why the scaffold's
          // footer stopped inheriting the gutter: inset by 24 the fade leaves two strips down
          // the sides where the backdrop orbs show through untouched.
          Column(
              Modifier
                  .fillMaxWidth()
                  .background(
                      Brush.verticalGradient(
                          0.00f to Cream.copy(alpha = 0f),
                          0.34f to Cream,
                          1.00f to Cream,
                      ),
                  )
                  .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 6.dp),
          ) {
            // ABOVE THE CTA, IN THE PINNED FOOTER, which is where the ticket puts it: "Use the
            // flow's shared inline error card above the CTA."
            //
            // In the footer rather than at the end of the scrolling body, and that is the whole
            // point of it being here: a failure the user cannot see is a CTA that appears to do
            // nothing. The body may be scrolled anywhere when the save fails, so a card at the
            // bottom of it would be off-screen exactly when it is needed.
            //
            // NOT RESERVED. This screen's CTA is pinned to the frame rather than pushed down by
            // content, so the card growing into the footer moves the button up by its own height
            // instead of moving anything the user was reading -- the "reserve the region" rule
            // exists for screens where the CTA sits in the flow, and this is not one.
            if (state.saveFailed) {
                InlineErrorCard(
                    ReachCopy.SAVE_FAILED,
                    modifier = Modifier.padding(bottom = 10.dp).testTag(REACH_ERROR_TAG),
                )
            }
            PrimaryButton(
                label = ReachCopy.CTA,
                // NEVER DISABLED, and not tappable twice: it stops answering while a save is in
                // flight rather than greying out. A disabled CTA on a screen with no other exit
                // is a dead end, which is the failure 09 was redesigned around.
                onClick = { if (!state.saving) onSave() },
                modifier = Modifier.testTag(REACH_CTA_TAG),
                variant = PrimaryButtonVariant.Sunset,
            )
          }
        },
    ) {
        // The headline block's four of extra gutter -- see the scaffold call above.
        WashHeadline(
            modifier = Modifier.padding(horizontal = 4.dp),
            parts = listOf(
                ReachCopy.HEADLINE_EM to true,
                ReachCopy.HEADLINE_TAIL to false,
            ),
            fontSize = 31.sp,
            lineHeight = (31f * 1.1f).sp,
            letterSpacing = (-0.015).em,
            balance = true,
        )

        Text(
            emphasised(ReachCopy.LEAD_PLAIN, LeadBold),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
            color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
            fontSize = 14.5.sp, lineHeight = (14.5f * 1.5f).sp,
        )

        // ── the body, which is the part that scrolls ────────────────────────
        //
        // `padding: '20px 24px 8px'` in the reference, measured from the SCREEN edge -- and the
        // headline above sits at 28. So the body pulls back 4 on each side rather than the
        // scaffold carrying two gutters.
        Column(
            // `padding: '14px 24px 4px'`. The 4 is the body's own bottom and is separate from
            // the footer reservation below it: the reference's footer is a flex SIBLING of the
            // scroll region, so it never overlaps, while ours is pinned over it and needs
            // [FooterClearance] as well. The two stack rather than replace each other.
            Modifier.padding(top = 14.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReachCard(state, onPushChange, onInterestToggle)
            FinePrint(onPrivacy)
        }

        // The fine print must scroll CLEAR of the pinned CTA. Reserved rather than hoped for.
        Spacer(Modifier.height(FooterClearance))
    }

    }

    // OVER THE WHOLE SCREEN, inside the same Box, so the scrim covers the pinned CTA too. A
    // dialog that leaves the primary action tappable behind it is not modal.
    if (state.prompt != null) {
        DeactivationDialog(
            prompt = state.prompt,
            onKeepActive = onKeepActive,
            onOpenSettings = onOpenSettings,
            onConfirm = onConfirmDeactivate,
        )
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReachCard(
    state: ReachabilityState,
    onPushChange: (Boolean) -> Unit,
    onInterestToggle: (InterestChannel) -> Unit,
) {
    // THE CARD LIFTS TO THE VIOLET TINT WHILE PUSH IS ON. `rgba(167,139,250,0.10)` over
    // `rgba(129,42,236,0.28)`, and the elevated surface with its shadow when it is off.
    val lifted = state.pushOn
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (lifted) Color(0x1A_A7_8B_FA) else Elevated)
            .border(
                1.dp,
                if (lifted) Color(0x47_81_2A_EC) else BorderSoft,
                RoundedCornerShape(18.dp),
            )
            .padding(16.dp),
    ) {
        // ── the head, which is the whole push control since 28 September 2026 ──
        //
        // The card used to introduce notifications and then offer a separate row to switch them
        // on. Two titles for one thing, and the pair cost about sixty vertical points -- enough
        // that the fine print fell below the fold on a 393 x 852 iPhone, which is the frame the
        // ticket now names as the target. Folding the switch into the head is Philipp's answer
        // and it is a better card as well as a shorter one: the thing being switched and the
        // switch are one row.
        Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            // FILLED, NOT TINTED, since the same change. The pip was a lilac wash behind a
            // violet bell while a second, filled pip sat in the push row below; with one head
            // there is one pip, and it takes the filled treatment that used to mark the row
            // that was already the answer.
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Purple)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) { Icon(BrandIcon.Bell, 20.dp, tint = Color.White, strokeWidth = 1.8f) }
            Column(Modifier.weight(1f)) {
                // ABOVE THE TITLE, which is what retires the FlowRow this screen used to need.
                // The pill and the label shared a row and really did wrap at 390, so the layout
                // had to be a flow rather than a Row -- and the badge had to stop growing at
                // 1.3x because eleven unbreakable characters would not fit beside the label at
                // 320. On its own line the pill has the column's full width and neither
                // workaround is load-bearing any more. The type cap is gone with the constraint
                // that justified it; `ScreenFitTest` is what confirms that, not this comment.
                Box(
                    Modifier
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Purple)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        ReachCopy.RECOMMENDED.uppercase(),
                        color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp, letterSpacing = 0.03.em,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        ReachCopy.CARD_TITLE,
                        modifier = Modifier.weight(1f),
                        color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold,
                        fontSize = 16.5.sp, lineHeight = (16.5f * 1.2f).sp,
                        letterSpacing = (-0.005).em,
                    )
                    // `weight` ON THE TITLE AND NOT HERE: the switch is a fixed 51 x 31 and the
                    // title is text, so the text is what absorbs a narrow screen or a large
                    // font. Without it the switch is the thing that shrinks, and a 44dp target
                    // is a floor rather than a starting point.
                    ConsentSwitch(
                        on = state.pushOn,
                        label = ReachCopy.PUSH_ROW,
                        onChange = onPushChange,
                    )
                }
                Text(
                    ReachCopy.CARD_LINE,
                    modifier = Modifier.padding(top = 6.dp),
                    color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                    fontSize = 13.sp, lineHeight = (13f * 1.42f).sp,
                )
            }
        }

        // ── the demand test ─────────────────────────────────────────────────
        // 12 above the rule and 12 below it, where it used to be 10 and 18. Even now, which
        // reads as a divider between two blocks rather than as a lid on the one beneath it.
        Column(Modifier.padding(top = 12.dp).topHairline().padding(top = 12.dp)) {
            Text(
                ReachCopy.INTEREST_HEADING,
                color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, lineHeight = (14f * 1.35f).sp,
            )
            Text(
                ReachCopy.INTEREST_LINE,
                modifier = Modifier.padding(top = 3.dp),
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 13.sp, lineHeight = (13f * 1.42f).sp,
            )
            Column(Modifier.padding(top = 6.dp)) {
                InterestChannel.ORDER.forEachIndexed { i, channel ->
                    InterestRow(
                        channel = channel,
                        on = state.isInterested(channel),
                        onToggle = { onInterestToggle(channel) },
                        first = i == 0,
                    )
                }
            }
        }
    }
}

/**
 * The GDPR footer note.
 *
 * `Privacy Policy` IS THE ONLY LINK. "Settings" is set in the foreground weight and is plain
 * text -- the ticket says so outright, and a second link here would be a second thing to tap that
 * goes nowhere.
 */
@Composable
private fun FinePrint(onPrivacy: () -> Unit) {
    // ONE PARAGRAPH, AND THE LINK IS INSIDE IT.
    //
    // This was two Texts in a Column, with `Read our Privacy Policy` on its own line under a 44dp
    // tap target. Callout 12 and the spec sheet's scrolled frame both show ONE FLOWING SENTENCE
    // that ends with the link and a full stop -- "... whenever you like in Settings. Read our
    // Privacy Policy." -- and a link on its own line read as a detached button, which is how it
    // came back reported as missing rather than as misplaced.
    //
    // `withLink` rather than a clickable modifier, because it is the only way to make a RUN of
    // text tappable rather than a block, and because it is what the welcome flow's legal line
    // already uses -- so the two behave the same way to a finger and to a screen reader.
    //
    // THE 44DP FLOOR CANNOT APPLY TO A WORD INSIDE A SENTENCE, and does not here. That is the
    // same trade the three legal links on the sign-up screens already make, and it is the design
    // rather than an oversight. Recorded as E35.
    val linkStyle = SpanStyle(
        color = Purple,
        fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.Underline,
    )
    Text(
        buildAnnotatedString {
            val text = ReachCopy.FINE_PRINT
            val settings = text.indexOf(ReachCopy.SETTINGS_WORD)
            append(text.substring(0, settings))
            // `Settings` is --liq-fg AND 600 -- DARK, not the subtle grey the rest is set in.
            // `emphasised` carries weight and nothing else, which is why this word was bold and
            // still grey, and why it was reported as not being black.
            withStyle(SpanStyle(color = Fg, fontWeight = FontWeight.SemiBold)) {
                append(ReachCopy.SETTINGS_WORD)
            }
            append(text.substring(settings + ReachCopy.SETTINGS_WORD.length))
            append(" ")
            withLink(LinkAnnotation.Clickable(REACH_PRIVACY_LINK_TAG) { onPrivacy() }) {
                withStyle(linkStyle) { append(ReachCopy.PRIVACY_LINK) }
            }
            append(".")
        },
        modifier = Modifier
            // No top margin of its own since 28 Sep 2026: the body column's 12 between the card
            // and this is the whole gap now.
            .padding(horizontal = 4.dp)
            .testTag(REACH_FINEPRINT_TAG),
        color = FgSubtle, fontFamily = Manrope, fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp, lineHeight = (11.5f * 1.5f).sp,
    )
}

/**
 * The deactivation confirm — states C and D.
 *
 * ONE DIALOG, ONE FLAG. C and D differ by the primary's label and nothing else; two dialogs would
 * be two places for the body copy to drift.
 *
 * THE SCRIM IS `Keep active`, not a dismissal: tapping it leaves push ON and reports
 * `consent_deactivation_abandoned`, exactly as the button does. In state D it is still an
 * abandonment -- only the explicit `Open Settings` press reports `permission_settings_opened`.
 */
@Composable
private fun DeactivationDialog(
    prompt: DeactivationPrompt,
    onKeepActive: () -> Unit,
    onOpenSettings: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x70_1D_11_29))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onKeepActive,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // THE VERTICAL PADDING AND THE SCROLL ARE LOAD-BEARING, not tidiness.
        //
        // A `Column` centred in a `Box` is given the whole frame to measure in and simply keeps
        // laying children out past the bottom of it. At font scale 2.0 on a 360-wide phone this
        // dialog is taller than every frame in the sweep, and what that produced was not an
        // obviously broken picture: the title drew two lines with 148px below the cut, and the
        // two buttons under it -- INCLUDING `Confirm deactivation`, the only destructive action
        // on this screen -- measured to ZERO HEIGHT and disappeared. A user at an accessibility
        // font size got a dialog they could not act on and could only dismiss by tapping the
        // scrim. The 50dp secondary also reported as a 27dp tap target at 1.3 on the Fold, which
        // is the same overflow one step earlier.
        //
        // `padding(vertical = 24)` caps the card at the frame less its margins; `verticalScroll`
        // gives the overflow somewhere to go. The scroll sits INSIDE the background and clip so
        // the card keeps its rounded edge and its own surface while the content moves under it.
        Column(
            Modifier
                .padding(horizontal = 26.dp, vertical = 24.dp)
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Elevated)
                // The card swallows taps so a press inside it is not read as a scrim tap.
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                )
                .padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // THE EXPLANATION SCROLLS, THE ACTIONS DO NOT.
            //
            // `weight(1f, fill = false)` is the whole of it: the region takes only the height it
            // needs, so at every normal font size this Column lays out exactly as it did before
            // the fix and the dialog is still sized by its content. Only when the content would
            // push past the card does the weight bite, and then it yields to the buttons below
            // rather than pushing them off. Scrolling the buttons away was the first fix and it
            // was still a dialog whose destructive action you had to go looking for.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                        .background(Color(0x1A_FB_32_3B)).clearAndSetSemantics {},
                    contentAlignment = Alignment.Center,
                ) { Icon(BrandIcon.AlertCircle, 24.dp, tint = Danger, strokeWidth = 1.9f) }

                // ITALIC. The reference sets the whole title in an `<em>` -- the same flourish
                // the headline uses on one word, here running the full line.
                Text(
                    ReachCopy.CONFIRM_TITLE,
                    modifier = Modifier.padding(top = 16.dp),
                    color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    fontSize = 23.sp, lineHeight = (23f * 1.15f).sp, letterSpacing = (-0.01).em,
                )

                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(ReachCopy.CONFIRM_LEAD)
                        }
                        append("\n")
                        append(emphasised(ReachCopy.CONFIRM_BODY, LeadBold))
                    },
                    modifier = Modifier.padding(top = 10.dp),
                    color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                    fontSize = 14.sp, lineHeight = (14f * 1.5f).sp,
                    textAlign = TextAlign.Center,
                )

                // The channel chip: which consent this is about, so the dialog is never ambiguous.
                Row(
                    Modifier
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Raised)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(LilacWash)
                            .clearAndSetSemantics {},
                        contentAlignment = Alignment.Center,
                    ) { Icon(BrandIcon.Bell, 16.dp, tint = Purple, strokeWidth = 1.8f) }
                    Text(
                        ReachCopy.PUSH_ROW,
                        color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                    )
            }

            }

            // FOCUS MOVES TO THE PRIMARY WHEN THE DIALOG OPENS -- the ticket says so, and it is
            // the other half of modality. `clearAndSetSemantics` on the screen behind stops a
            // screen reader reaching past the dialog; this puts it INSIDE the dialog rather than
            // leaving it wherever it was, which on a freshly composed overlay is nowhere.
            //
            // The primary and not the destructive action, deliberately: the first thing a screen
            // reader user lands on should be the one that changes nothing.
            val primaryFocus = remember { FocusRequester() }
            LaunchedEffect(prompt) { primaryFocus.requestFocus() }

            PrimaryButton(
                label = if (prompt == DeactivationPrompt.AfterOsDenial) {
                    ReachCopy.CONFIRM_SETTINGS
                } else {
                    ReachCopy.CONFIRM_KEEP
                },
                onClick = {
                    if (prompt == DeactivationPrompt.AfterOsDenial) onOpenSettings()
                    else onKeepActive()
                },
                modifier = Modifier.padding(top = 18.dp).focusRequester(primaryFocus),
                variant = PrimaryButtonVariant.Sunset,
            )

            // `height: 50` in the reference, which is already over the 44 floor. A Box rather
            // than a tall Text so the label stays optically centred at every font scale.
            //
            // `heightIn(min = )`, NOT `height( )`. The reference's 50 is a FLOOR, and writing it
            // as a fixed height clipped `Confirm deactivation` in half at font scale 2.0 -- the
            // label wraps to two lines there and a 50dp box has room for one. Same pixels
            // wherever the label fits on one line; room to grow where it does not. This is the
            // rule `CLAUDE.md` states for width applied to the other axis, and the reason is the
            // same: a fixed frame is right at one font size and wrong at the rest.
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clickable(role = Role.Button, onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ReachCopy.CONFIRM_DEACTIVATE,
                    color = Danger, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

// ── previews: the four states at the three frames the ticket names ──────────
//
// 375 x 667 is where the body has to scroll and where the fine print must still clear the CTA;
// 430 x 932 is where the whole card fits above the fold. 320 x 686 is not in the ticket and is in
// the fit sweep anyway -- it is the frame that has broken every screen in this flow.

private val InterestB = ReachabilityState(
    interest = setOf(InterestChannel.AiCall, InterestChannel.WhatsApp),
)

@Preview(name = "A · default · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PR_A375() { ProfileReachabilityScreen() }

@Preview(name = "A · default · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_A390() { ProfileReachabilityScreen() }

@Preview(name = "A · default · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PR_A430() { ProfileReachabilityScreen() }

@Preview(name = "B · interest · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_B390() { ProfileReachabilityScreen(InterestB) }

@Preview(name = "C · confirm · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_C390() {
    ProfileReachabilityScreen(ReachabilityState(prompt = DeactivationPrompt.UserTurnedItOff))
}

@Preview(name = "D · after denial · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_D390() {
    ProfileReachabilityScreen(ReachabilityState(prompt = DeactivationPrompt.AfterOsDenial))
}

@Preview(name = "A · 320 · large type", showBackground = true, widthDp = 320, heightDp = 686, fontScale = 1.3f)
@Composable private fun PR_A320Large() { ProfileReachabilityScreen() }
