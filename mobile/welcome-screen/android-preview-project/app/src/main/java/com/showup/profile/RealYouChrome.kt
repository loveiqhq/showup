/*
 * RealYouChrome.kt
 * ShowUp · the shell every screen in "The real you" shares (SHOWUP-156, SHOWUP-158)
 *
 * The twin of `BasicsChrome.kt`, for the second profile group. Three screens share it — photos,
 * prompts and media — and the differences between them are parameters, not forks. Both tickets say
 * so in the same words: "the group's header + progress shell is built ONCE and consumed by photos,
 * prompts and media", and "a second copy is a bug".
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THIS IS A SECOND SHELL AND NOT A PARAMETER ON THE FIRST
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * [BasicsScaffold] is a single non-scrolling column with ONE flexible spacer and a CTA hanging off
 * the bottom of it. This one is four fixed regions with a scroll in the middle. They are not the
 * same layout with a flag: the basics group's guarantee is that nothing moves between a screen's
 * states, and this group's is that the header, the progress bar and the CTA never move while the
 * content under them does. A shell that did both would have to abandon one of those guarantees to
 * express the other.
 *
 * The flow README says the same thing from the other direction: "Screen 06 is the first screen in
 * the flow that scrolls... Every other screen in profile/ is a single non-scrolling column, so do
 * not copy this screen's shell to them."
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THREE SEGMENTS, NOT FOUR
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * SHOWUP-156 shipped its acceptance criteria with `steps={4} current={1}` — photos, prompts, media
 * and details. SHOWUP-158 supersedes that in as many words: "Verify profile is OUT of the MVP, so
 * 'The real you' is photos (1) -> prompts (2) -> media (3). Its acceptance criterion naming
 * steps={4} is superseded by this ticket."
 *
 * The count lives in [RealYouStep.COUNT] for exactly the reason the ticket gives: "if profile
 * verification returns post-MVP it comes back as a fourth segment, and the shell takes the new
 * count as a prop — nobody edits three screens."
 */
package com.showup.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Fg
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.rememberMotion
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.showup.designsystem.Cream
import com.showup.designsystem.Spacing
import com.showup.designsystem.StepProgress
import com.showup.welcome.OrbPlacement
import com.showup.welcome.WelcomeBackdrop

/** Copy shared by every screen in the group. */
internal object RealYouCopy {
    const val SECTION = "The real you"
}

/**
 * A step of "The real you".
 *
 * The same shape as [BasicsStep] and for the same reasons: a typed position with no magic values,
 * an exhaustive `when` at the host, and a Kotlin enum so `rememberSaveable` persists it for free.
 *
 * [Media] is declared and not built. It is here because [COUNT] has to be 3 rather than 2 — the
 * progress bar shows a step the user has not reached yet, which is the whole point of a progress
 * bar — and because declaring it is how the count and the last segment stay in agreement.
 *
 * CONFIRMED BY THE PRODUCT SIDE ON 16 SEPTEMBER 2026: media is planned and arrives in a later
 * ticket. The third segment is not speculative and must not be dropped to make the bar match the
 * screens that exist today — see `audit/CONFLICTS-2026-08-27.md` E8.
 */
enum class RealYouStep {
    Photos,
    Prompts,

    /** Voice and video. Not built; see SHOWUP-158's "out of scope". */
    Media,
    ;

    /** Which segment is filled. */
    val progressSegment: Int get() = ordinal + 1

    /**
     * `step_id` from the taxonomy's §2 vocabulary.
     *
     * `media` is NOT a registry value: §2 carries `media_voice` and `media_video` as two separate
     * steps, both outside the "Share some details" bar. Which of them a single media screen would
     * report is a question for that ticket, so this one returns the id the group's own tickets use
     * and the decision is recorded rather than guessed. Nothing emits it yet.
     */
    val stepId: String
        get() = when (this) {
            Photos -> "photos"
            Prompts -> "prompts"
            Media -> "media"
        }

    /**
     * `step_index` for the funnel.
     *
     * TAKEN FROM THE TICKETS, AND §2 DISAGREES ON ONE OF THEM. Registry 1.3.0 gives `photos`
     * step_index 1 with the screen note "The real you · step 1 of 4" — right index, stale count —
     * and gives `prompts` step_index **10**, under "Share some details · step 10", which is where
     * prompts sat before it moved into this group.
     *
     * SHOWUP-158 says what to do about it: "the corrected step_index for photos and media must be
     * added before the ticket is picked up". It has not been. The tickets are authoritative on
     * behaviour, so the position in THIS group is what ships, and the mismatch is recorded here
     * and in `audit/CONFLICTS-2026-08-27.md` rather than silently resolved either way.
     */
    val stepIndex: Int get() = progressSegment

    /**
     * Where the backdrop's two orbs sit on this screen.
     *
     * Photos puts both at the top because its footer carries a gradient mask that a low orb would
     * muddy; prompts splits them because its footer is a bare CTA row. Per-screen numbers from the
     * two reference files, named in one place so a screen does not carry its own copy.
     */
    internal val orbPlacement: OrbPlacement
        get() = if (this == Prompts) OrbPlacement.RealYouSplit else OrbPlacement.RealYouTop

    companion object {
        /**
         * How many segments the group's bar has.
         *
         * THREE, not four. `ScreenProfileVerify` was dropped from the MVP with SHOWUP-158 and gets
         * no segment. If it returns, this constant changes and nothing else does.
         *
         * And three, not two: media is a later ticket, confirmed 16 September 2026. Nothing routes
         * to [Media] and `resumePoint` has no case for it, so the unbuilt step costs a segment and
         * nothing else.
         */
        const val COUNT = 3
    }
}

/**
 * The group's scaffold: four fixed regions and one scrolling one.
 *
 *     status bar        safe-area inset
 *     AppHeader         52, fixed
 *     StepProgress      fixed on photos, part of the scroll on prompts — see [progressFixed]
 *     content           flex 1, THE ONLY SCROLLING REGION
 *     footer            fixed, the screen's own padding and background
 *     home indicator    safe-area inset
 *
 * NO ABSOLUTE Y ANYWHERE, and it breaks harder here than elsewhere in the flow: a fixed Y inside
 * the scrolling region is wrong the moment the user drags. `safeDrawing` takes the status bar, the
 * gesture bar and the IME, so no inset is hard-coded either.
 *
 * THE BACKDROP IS A SIBLING OF THE COLUMN, NOT ITS BACKGROUND. The insets apply to the column
 * only, so the orbs run full-bleed behind the status bar and the gesture bar with no seam — the
 * same arrangement, and the same reason, as [com.showup.welcome.WelcomeScaffold].
 *
 * @param progressFixed whether the bar sits in the fixed region above the scroll.
 *
 * True on photos, false on prompts, and that is a design decision rather than an oversight.
 * SHOWUP-158: "The progress bar scrolls with the content here, unlike photos where it sits in the
 * fixed region. That is deliberate — this screen carries more above the fold and the bar is not
 * worth the 26px. Do not 'fix' it into a third fixed row." When false, the screen renders
 * [StepProgress] itself as the first thing in [content].
 */
@Composable
fun RealYouScaffold(
    step: RealYouStep,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    progressFixed: Boolean = true,
    scrollState: ScrollState = rememberScrollState(),
    footer: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(Cream)) {
        // Lower intensity than the welcome flow's 0.32 / 0.28: this group has content all the way
        // down rather than a hero and a band, and the orbs are atmosphere behind it rather than
        // the subject. No peach wash — that is the bridge screens' and Startup's, and it would sit
        // straight over the headline here.
        WelcomeBackdrop(
            peachWash = false,
            placement = step.orbPlacement,
            orangeAlpha = 0.18f,
            violetAlpha = 0.16f,
        )

        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            AppHeader(
                title = RealYouCopy.SECTION,
                leading = HeaderLeading.Back,
                onBack = onBack,
            )

            if (progressFixed) {
                // padding '4px 24px 14px' from the reference's progress wrapper.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Spacing.screenGutter,
                            end = Spacing.screenGutter,
                            top = Spacing.xs,
                            bottom = 14.dp,
                        ),
                ) {
                    StepProgress(steps = RealYouStep.COUNT, current = step.progressSegment)
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    // padding '4 24 0'. The floor is 0 because each screen ends its own content
                    // with a tail spacer sized to clear its footer.
                    .padding(
                        start = Spacing.screenGutter,
                        end = Spacing.screenGutter,
                        top = Spacing.xs,
                    ),
                content = content,
            )

            footer()
        }
    }
}

/**
 * A refusal toast, drawn ABOVE the footer and occupying none of it.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHY THE ZERO-SIZE LAYOUT, RATHER THAN A ROW IN THE FOOTER
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Both tickets specify `bottom: 100%` of the footer and both say why: "so the footer keeps
 * identical geometry when it is idle and nothing reflows when the toast appears". A toast that
 * took part in the footer's layout would push the CTA down by its own height every time the user
 * pressed a button that refused — which is the CTA moving in response to being pressed, on the one
 * press where the user most needs it to stay put.
 *
 * `align(TopCenter)` with a reported size of 0 x 0 puts this node's origin at the top edge of the
 * footer, horizontally centred; placing the content at `(-width / 2, -height)` then centres it and
 * lifts it entirely above that edge. That is `bottom: 100%` with `justify-content: center`, and
 * because the reported size is zero it is invisible to the footer's measurement in both states.
 *
 * Reserving the height instead — the pattern "The basics" uses for its error cards — is the wrong
 * answer here. There, the message belongs to the field above it and the layout must not shift. A
 * toast is transient chrome over a scrolling screen, and reserving 40 of permanent empty space
 * above the CTA to hold something that is visible for 2.6 seconds would cost the grid a row on the
 * smallest device.
 *
 * VISIBILITY, NOT REMOVAL. The node stays composed and fades, so the exit animation runs; removing
 * it would cut instead. It is `clearAndSetSemantics` when hidden so TalkBack does not read a toast
 * nobody can see — and a live region when shown, because the user has just pressed a button and
 * the answer is somewhere other than where they are looking.
 */
@Composable
fun BoxScope.RefusalToast(
    visible: Boolean,
    icon: BrandIcon,
    message: String,
) {
    val motion = rememberMotion()
    val fade = if (motion.enabled) 200 else 0
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(fade),
        label = "toastAlpha",
    )
    // 6 up on the way in, from the reference's `translateY(6px)` resting state.
    val rise by animateFloatAsState(
        targetValue = if (visible) 0f else 6f,
        animationSpec = tween(fade),
        label = "toastRise",
    )

    Box(
        Modifier
            .align(Alignment.TopCenter)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                // Report nothing, so the footer measures identically in both states.
                layout(0, 0) { placeable.place(-placeable.width / 2, -placeable.height) }
            },
    ) {
        Row(
            Modifier
                .graphicsLayer {
                    this.alpha = alpha
                    translationY = rise * density
                }
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Fg)
                .padding(horizontal = 14.dp, vertical = Spacing.lg)
                // Announced when up, and absent from the tree when not. A toast that stays
                // readable while invisible is a control TalkBack can land on and a sighted user
                // cannot see.
                .then(
                    if (visible) {
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    } else {
                        Modifier.clearAndSetSemantics {}
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, 15.dp, tint = Color.White, strokeWidth = 2.2.dp)
            Text(
                message,
                color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, lineHeight = (13f * 1.2f).sp,
            )
        }
    }
}

/**
 * When a refusal toast is up.
 *
 * Hoisted into a holder rather than left as a raw boolean because the AUTO-DISMISS is the part
 * that is easy to get wrong: a second refused press while the first toast is still up has to
 * restart the 2.6 seconds, not be swallowed by the effect that is already running. Keying the
 * effect on a counter rather than on the boolean is what makes that work — the boolean is already
 * true, so an effect keyed on it would not re-run and the toast would vanish early.
 *
 * The same bug, and the same fix, as `shakeOnce` on the name screen.
 */
@Stable
class RefusalToastState {
    /** Bumped on every refusal. The effect below is keyed on it, not on [visible]. */
    var presses by mutableIntStateOf(0)
        private set

    var visible by mutableStateOf(false)
        internal set

    fun show() {
        visible = true
        presses += 1
    }
}

/** Creates a [RefusalToastState] and runs its dismissal timer. */
@Composable
fun rememberRefusalToast(): RefusalToastState {
    val state = remember { RefusalToastState() }
    LaunchedEffect(state.presses) {
        if (state.presses > 0) {
            delay(Motion.TOAST.toLong())
            state.visible = false
        }
    }
    return state
}
