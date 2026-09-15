/*
 * ProfilePromptsScreen.kt
 * ShowUp · Profile creation 07 — Prompts, the written answers (SHOWUP-158)
 *
 * The first screen in the flow that asks the user to WRITE something about themselves, and the one
 * where a profile-creation funnel typically bleeds. Everything before it is a fact they already
 * know or a thing they already have; this asks them to be interesting on demand, which is a
 * different kind of ask.
 *
 * Eight states, one screen, two sheets:
 *
 *   A · 0 of 3, arrival
 *   B · 1 of 3 saved
 *   C · 3 of 3 saved -- the suggestion block and the browse control are GONE, not disabled
 *   D · the topic sheet, all fifteen in three groups
 *   E · the write sheet, empty
 *   F · the write sheet, mid-draft
 *   G · the write sheet at 160/160 -- AMBER, and Save stays live
 *   H · the write sheet after Save was pressed on an empty field
 *
 * The sheets are folded in here rather than given their own files because neither has an entry
 * point of its own: they are endings of the same attempt.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * THE CONVERSION PASS IS THE TICKET, NOT POLISH
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Each of these replaces something an earlier mock showed, and the ticket is explicit that they
 * are the substance of the work:
 *
 *   · THREE SUGGESTED TOPICS ON THE SCREEN. The topic sheet is now the "Browse all 15 topics"
 *     escape hatch rather than the critical path -- which removes a tap and a sheet from the route
 *     to the first word. "Do not build the old flow and add the suggestions later": bolted on
 *     afterwards they become a fourth thing on a screen that already had two.
 *   · A WORKED EXAMPLE ABOVE THE FIELD, and NOT a placeholder. A placeholder vanishes at the first
 *     keystroke, which is exactly when the user still wants it.
 *   · A FLOOR, NOT A CEILING. "One good sentence is enough", always; the counter appears at 100
 *     characters, not at 0. Below 100 a numeral is not information, it is a target.
 *   · 160/160 IS AMBER AND SAVE STAYS LIVE. Reaching the cap is not a validation failure -- the
 *     user wrote to the end of the box, which is a thing boxes are for.
 *   · SAVE IS NEVER DISABLED. An empty press explains (state H) rather than doing nothing.
 *   · DRAFTS SURVIVE DISMISSAL. Closing the sheet keeps what was typed; only Save writes a prompt.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TWO THINGS THAT LOOK LIKE MISTAKES AND ARE NOT
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * THE PROGRESS BAR SCROLLS HERE. On photos it sits in the fixed region; here it is the first thing
 * in the scrolling column. "That is deliberate -- this screen carries more above the fold and the
 * bar is not worth the 26px. Do not 'fix' it into a third fixed row."
 *
 * SAVE IS SUNSET AND CONTINUE IS ORANGE, on the same screen. Saving an answer is the commitment
 * beat; moving to the next step is routine. The two CTAs are never both on screen at once.
 */
package com.showup.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.showup.designsystem.Border
import com.showup.designsystem.ComponentSizes
import com.showup.designsystem.Cream
import com.showup.designsystem.Danger
import com.showup.designsystem.DangerFg
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.LavenderWash
import com.showup.designsystem.LilacStops
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Neutral
import com.showup.designsystem.Orange
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.Purple
import com.showup.designsystem.Spacing
import com.showup.designsystem.ShowUpEasing
import com.showup.designsystem.StepProgress
import com.showup.designsystem.Subtle
import com.showup.designsystem.SuccessFg
import com.showup.designsystem.rememberMotion
import com.showup.tutorial.NextButton
import com.showup.welcome.BrandIcon
import com.showup.welcome.Icon
import com.showup.welcome.WashHeadline

/** Copy — final strings. Every one is quoted by `audit/verify-profile.py`. */
internal object PromptsCopy {
    const val HEADLINE_LEAD = "Your space to share something "
    const val HEADLINE_EM = "personal"
    const val HEADLINE_TAIL = "."
    const val SUB = "One is enough to continue. Add up to 3 if you're enjoying yourself."

    const val SECTION_NONE_SAVED = "Start with one of these"
    const val SECTION_SOME_SAVED = "Add another · optional"
    const val WRITE_THIS = "Write this"
    const val BROWSE_ALL = "Browse all 15 topics"

    fun counter(count: Int): String =
        if (count >= PROMPTS_REQUIRED) {
            "$count/$PROMPTS_MAX prompts · enough to continue"
        } else {
            "$count/$PROMPTS_MAX prompts"
        }

    const val CTA = "Continue"
    const val TOAST = "Write 1 prompt to continue"

    // ── the topic sheet ─────────────────────────────────────────────────────
    const val TOPIC_HEADLINE_LEAD = "Choose a "
    const val TOPIC_HEADLINE_EM = "topic"
    const val TOPIC_HEADLINE_TAIL = "."
    const val TOPIC_SUB = "Pick something you'd want a match to actually know."
    const val TOPIC_USED = "Used"

    // ── the write sheet ─────────────────────────────────────────────────────
    const val EXAMPLE_EYEBROW = "FOR EXAMPLE"
    const val PLACEHOLDER = "Say it like you'd tell it to a friend…"
    const val FLOOR = "One good sentence is enough."
    const val AT_CAP = "That's the full 160 — short and specific lands harder anyway."
    const val EMPTY_SUBMIT = "Write a few words to save this prompt."
    const val SAVE = "Save"

    const val DISMISS = "Dismiss"
    const val HIDE_EXAMPLE = "Hide example"
    const val EDIT_PROMPT = "Edit prompt"
}

/** Which sheet is up, if any. */
sealed interface PromptSheet {
    /** All fifteen. Opened by `Browse all 15 topics` only — never the critical path. */
    data object Topics : PromptSheet

    /**
     * Write or edit one answer.
     *
     * [editing] is what makes Save overwrite rather than append, and it is carried on the sheet
     * rather than derived from whether the topic is already used — because a user can open the
     * topic sheet, pick a topic, and be editing nothing at all.
     */
    data class Write(val topicId: String, val editing: Boolean = false) : PromptSheet
}

/**
 * Everything the prompts screen renders.
 *
 * ONE VALUE, HOISTED. The screen owns no asynchronous work — there is no prompt endpoint to call —
 * so this is `rememberSaveable` at the host rather than a ViewModel, which is the rule in
 * `CLAUDE.md` applied rather than abandoned.
 *
 * [drafts] is why this is a map and not a string. "The draft survives dismissal... and is restored
 * when that topic is reopened", so a draft belongs to a TOPIC rather than to the sheet that is
 * currently up.
 */
data class PromptsState(
    val prompts: List<SavedPrompt> = emptyList(),
    val sheet: PromptSheet? = null,
    val drafts: Map<String, String> = emptyMap(),
    /** Set by an empty Save. Cleared by the first character typed, never by blur or a re-press. */
    val nudge: Boolean = false,
    /**
     * The topic whose worked example the user has dismissed.
     *
     * "The example is per sheet, not per session. Dismissing it hides it for that sheet only; the
     * next prompt shows it again." One id rather than a set, because only one sheet is ever open.
     */
    val exampleHiddenFor: String? = null,
) {
    val count: Int get() = prompts.size
    val canContinue: Boolean get() = count >= PROMPTS_REQUIRED
    val usedTopicIds: List<String> get() = prompts.map { it.topicId }

    fun draftFor(topicId: String): String = drafts[topicId].orEmpty()

    companion object {
        /**
         * Survives a rotation and process death.
         *
         * `rememberSaveable` writes into a Bundle, which takes primitives and lists of them and
         * nothing else — so the sheet, which is a sealed interface, is flattened into a kind, a
         * topic and a flag, and the draft map into two parallel lists. Ugly, and the alternative
         * is worse: the flow README's rule 4a says a resumed step behaves like a freshly-reached
         * one WITH EVERYTHING ALREADY ENTERED STILL PRESENT, and a half-written prompt lost to a
         * rotation is exactly the failure that rule exists to prevent.
         *
         * The lists are written and read in the same order in one place, which is the only thing
         * that keeps a flattened saver honest.
         */
        val Saver: androidx.compose.runtime.saveable.Saver<PromptsState, Any> =
            androidx.compose.runtime.saveable.listSaver(
                save = { state ->
                    val sheet = state.sheet
                    listOf(
                        state.prompts.map { it.topicId },
                        state.prompts.map { it.answer },
                        when (sheet) {
                            null -> ""
                            PromptSheet.Topics -> "topics"
                            is PromptSheet.Write -> "write"
                        },
                        (sheet as? PromptSheet.Write)?.topicId.orEmpty(),
                        (sheet as? PromptSheet.Write)?.editing ?: false,
                        state.drafts.keys.toList(),
                        state.drafts.values.toList(),
                        state.nudge,
                        state.exampleHiddenFor.orEmpty(),
                    )
                },
                restore = { saved ->
                    @Suppress("UNCHECKED_CAST")
                    val topics = saved[0] as List<String>
                    @Suppress("UNCHECKED_CAST")
                    val answers = saved[1] as List<String>
                    @Suppress("UNCHECKED_CAST")
                    val draftKeys = saved[5] as List<String>
                    @Suppress("UNCHECKED_CAST")
                    val draftValues = saved[6] as List<String>
                    PromptsState(
                        prompts = topics.zip(answers).map { SavedPrompt(it.first, it.second) },
                        sheet = when (saved[2] as String) {
                            "topics" -> PromptSheet.Topics
                            "write" -> PromptSheet.Write(saved[3] as String, saved[4] as Boolean)
                            else -> null
                        },
                        drafts = draftKeys.zip(draftValues).toMap(),
                        nudge = saved[7] as Boolean,
                        exampleHiddenFor = (saved[8] as String).ifEmpty { null },
                    )
                },
            )
    }
}

// ── pieces ──────────────────────────────────────────────────────────────────

/**
 * The card radius in this group.
 *
 * 18, and deliberately not [com.showup.designsystem.Radius.card], which is 16 and belongs to the
 * sign-up flow's chrome. These are reading surfaces with a serif question on them, drawn a step
 * softer. Local because it means "a content card in The real you" and nothing else.
 */
private val CardRadius = 18.dp

/** A 1.5 outline at a given radius. */
private fun Modifier.outline(color: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2f, w / 2f),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = w),
    )
}

/**
 * `box-shadow: 0 0 0 4px <colour>` — a ring OUTSIDE the border box.
 *
 * Not `Modifier.shadow`, which is Android's elevation system: it has a light source at the top of
 * the window, so its direction depends on where the control sits on screen. This is a flat halo
 * with no direction, which is what the CSS actually specifies. Drawn outside the node's own bounds
 * rather than inside, so the field does not shrink when it gains focus.
 */
private fun Modifier.halo(color: Color, radius: Dp, spread: Dp = 4.dp): Modifier = drawBehind {
    val s = spread.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(-s, -s),
        size = Size(size.width + 2 * s, size.height + 2 * s),
        cornerRadius = CornerRadius(radius.toPx() + s),
    )
}

/**
 * A topic, on the screen, tappable.
 *
 * THE SINGLE BIGGEST CHANGE IN THIS REVISION. It is a topic first and a control second: the
 * question is set in Lora at reading size, and the affordance is a small violet `Write this` row
 * underneath rather than a chevron — so the card reads as an invitation rather than a menu item.
 */
@Composable
private fun SuggestionCard(topic: PromptTopic, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(Elevated)
            .outline(Purple.copy(alpha = 0.28f), CardRadius)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = Spacing.xxl, end = Spacing.xxl, top = 14.dp, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            topic.text,
            color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold,
            fontSize = 16.5.sp, lineHeight = (16.5f * 1.25f).sp, letterSpacing = (-0.008).em,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(LavenderWash),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BrandIcon.Plus, 13.dp, tint = Purple, strokeWidth = 2.6.dp)
            }
            Text(
                PromptsCopy.WRITE_THIS,
                color = Purple, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
            )
        }
    }
}

/**
 * The escape hatch to the full fifteen.
 *
 * DELIBERATELY QUIETER than a suggestion card: it is the slower path, and it should look like it.
 * Transparent with a hairline border against three filled cards above it.
 */
@Composable
private fun BrowseAllButton(onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .outline(Border, 24.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            PromptsCopy.BROWSE_ALL,
            color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        )
    }
}

/**
 * A saved answer.
 *
 * THE ANSWER IS NEVER TRUNCATED. The card grows to fit it — a profile answer the user wrote and
 * then cannot read back is the screen quietly disagreeing with the 160 it allowed.
 */
@Composable
private fun FilledPromptCard(prompt: SavedPrompt, onEdit: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(Brush.verticalGradient(colorStops = LilacStops.toTypedArray()))
            .outline(Purple.copy(alpha = 0.10f), CardRadius, width = 1.dp),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = Spacing.xxl)) {
            Text(
                topicText(prompt.topicId),
                modifier = Modifier.padding(end = 32.dp),
                color = Fg, fontFamily = Lora, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, lineHeight = (16f * 1.25f).sp, letterSpacing = (-0.005).em,
            )
            Text(
                prompt.answer,
                modifier = Modifier.padding(top = Spacing.sm),
                color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp, lineHeight = (13.5f * 1.5f).sp,
            )
        }
        // The pip DRAWS 30 at top 12 / right 12 and ANSWERS at 44, by the same arithmetic as the
        // photo grid's remove control: 44 = 30 + 7 + 7, so a 44 box inset by 5 puts the drawn pip
        // exactly where the reference specifies.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(ComponentSizes.minTapTarget)
                .clickable(role = Role.Button, onClick = onEdit)
                .semantics { contentDescription = PromptsCopy.EDIT_PROMPT },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BrandIcon.Pen, 14.dp, tint = Purple, strokeWidth = 1.8.dp)
            }
        }
    }
}

/** The drag grabber. Decorative: a sheet is dismissed by the X or the scrim, not by this. */
@Composable
private fun SheetGrabber() {
    Box(
        Modifier
            .size(width = 40.dp, height = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Fg.copy(alpha = 0.18f))
            .clearAndSetSemantics {},
    )
}

/**
 * The close X. Placed by the caller, because the two sheets pad differently.
 *
 * DRAWS 36 AND ANSWERS AT 44. The reference specifies a 36 control and the fit harness flagged it
 * at all seventeen sizes the first time it was built that way -- "36.0dp, unusable below 44" --
 * which is the rule every tappable thing in this app is held to. `requiredSize` ignores the 36
 * layout slot and overflows it symmetrically, so the hit area grows and nothing visible moves.
 * Same trick, same reason, as the back chevron in AppHeader and the photo grid's remove pip.
 */
@Composable
private fun SheetCloseButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .requiredSize(ComponentSizes.minTapTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = PromptsCopy.DISMISS },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Fg.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BrandIcon.Close, 20.dp, tint = Fg, strokeWidth = 1.8.dp)
            }
        }
    }
}

/**
 * The scrim and the rising surface both sheets sit in.
 *
 * `sheet-rise` is 28 up and 0.85 -> 1 opacity over [Motion.SHEET] — a shared keyframe, not a
 * per-sheet animation, and skipped entirely when the device asks for no motion.
 */
@Composable
private fun SheetScaffold(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val motion = rememberMotion()
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val rise by animateFloatAsState(
        targetValue = if (shown) 0f else 28f,
        animationSpec = tween(
            durationMillis = if (motion.enabled) Motion.SHEET else 0,
            easing = ShowUpEasing,
        ),
        label = "sheetRise",
    )
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0.85f,
        animationSpec = tween(durationMillis = if (motion.enabled) Motion.SHEET else 0),
        label = "sheetFade",
    )

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Fg.copy(alpha = 0.42f))
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = PromptsCopy.DISMISS },
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = rise * density; alpha = fade }
                // The keyboard is not ours and its height is not knowable, and neither is the
                // gesture bar's. `safeDrawing` bottom is BOTH -- it reports the keyboard when one
                // is up and the navigation inset when one is not, taking whichever is larger, so
                // Save clears the keys while typing and the gesture bar while not.
                //
                // `imePadding()` alone was wrong here: with the keyboard down it reserves nothing,
                // and this sheet's own bottom padding is 16, which on a gesture-navigation device
                // puts Save underneath the bar.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            content()
        }
    }
}

// ── the topic sheet ─────────────────────────────────────────────────────────

/**
 * All fifteen, in three named groups.
 *
 * NO LONGER THE DEFAULT PATH: this is what `Browse all 15 topics` opens.
 *
 * A USED TOPIC IS DISABLED, NEVER HIDDEN. "The list never changes length or order between visits"
 * — a list that shortened as the user used it would move every remaining topic under their finger
 * between the first prompt and the second.
 */
@Composable
private fun TopicPickerSheet(
    used: List<String>,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            // maxHeight 600 in the reference. A cap rather than a height: on a short phone the
            // list scrolls inside whatever is left, and on a tall one the sheet stops well short
            // of the header so the screen is still visible behind it.
            .heightIn(max = 600.dp)
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(Cream),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = Spacing.md)) {
            Box(Modifier.align(Alignment.TopCenter)) { SheetGrabber() }
            SheetCloseButton(onClose, Modifier.align(Alignment.TopEnd).padding(end = Spacing.md))
        }

        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = Spacing.xl)) {
            WashHeadline(
                parts = listOf(
                    PromptsCopy.TOPIC_HEADLINE_LEAD to false,
                    PromptsCopy.TOPIC_HEADLINE_EM to true,
                    PromptsCopy.TOPIC_HEADLINE_TAIL to false,
                ),
                modifier = Modifier.padding(vertical = Spacing.sm),
                fontSize = 26.sp,
                lineHeight = (26f * 1.1f).sp,
                letterSpacing = (-0.018).em,
            )
            Text(
                PromptsCopy.TOPIC_SUB,
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 14.sp, lineHeight = (14f * 1.45f).sp,
            )
        }

        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            TOPIC_GROUPS.forEachIndexed { groupIndex, group ->
                Text(
                    group.label,
                    modifier = Modifier.padding(
                        start = 2.dp,
                        top = if (groupIndex == 0) 2.dp else Spacing.xl,
                    ),
                    color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.5.sp, letterSpacing = 0.08.em,
                )
                group.topics.forEach { topic ->
                    val isUsed = topic.id in used
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .clip(CircleShape)
                            .background(if (isUsed) Fg.copy(alpha = 0.03f) else Elevated)
                            .outline(if (isUsed) Border else Purple, 25.dp)
                            .then(
                                if (isUsed) {
                                    Modifier
                                } else {
                                    Modifier.clickable(role = Role.Button) { onPick(topic.id) }
                                },
                            )
                            .padding(horizontal = 20.dp, vertical = Spacing.lg)
                            // A used topic is DISABLED, not hidden: it keeps its place in the
                            // list and reads back at 55%.
                            .alpha(if (isUsed) 0.55f else 1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                    ) {
                        Text(
                            topic.text,
                            modifier = Modifier.weight(1f),
                            color = Fg, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp, lineHeight = (14.5f * 1.3f).sp,
                        )
                        if (isUsed) {
                            Text(
                                PromptsCopy.TOPIC_USED,
                                color = Subtle, fontFamily = Manrope,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.5.sp, letterSpacing = 0.06.em,
                            )
                        } else {
                            Icon(BrandIcon.Plus, Spacing.xxl, tint = Purple, strokeWidth = 2.4.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

// ── the write sheet ─────────────────────────────────────────────────────────

/**
 * Write or edit one answer.
 *
 * THE STATUS ROW IS RESERVED, NOT CONDITIONAL — `min-height: 34`, always present, carrying the
 * floor line in the calm state. It is what keeps the field, Save and the sheet's own height
 * identical between calm, at-cap and empty-submit. A conditional row would move the Save button
 * every time the message changed, which on this screen means every time the user reaches 160.
 *
 * THE CAP COLOUR IS THE ORANGE TOKEN, NOT THE DANGER TOKEN. Deliberate, and the one thing in this
 * sheet most likely to be "corrected" later: red says you did something wrong, and writing to the
 * end of the box is not wrong.
 */
@Composable
private fun WritePromptSheet(
    topicId: String,
    draft: String,
    nudge: Boolean,
    exampleHidden: Boolean,
    onDraftChange: (String) -> Unit,
    onHideExample: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val length = draft.length
    val atCap = length >= PROMPT_MAX_CHARS
    val isEmpty = promptAnswerIsEmpty(draft)
    val showCounter = length >= PROMPT_COUNTER_FROM
    val showNudge = nudge && isEmpty

    val borderColor = when {
        atCap -> Orange
        showNudge -> Danger
        isEmpty -> Border
        else -> Purple
    }
    val haloColor = when {
        atCap -> Orange.copy(alpha = 0.12f)
        showNudge -> Danger.copy(alpha = 0.10f)
        isEmpty -> Color.Transparent
        else -> Purple.copy(alpha = 0.10f)
    }

    val motion = rememberMotion()
    var shakeKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(showNudge) { if (showNudge) shakeKey += 1 }

    val focus = remember { FocusRequester() }
    // The field takes focus as the sheet arrives, so the keyboard is already up and the user types
    // without a second tap. runCatching because a focus request throws while the field is not yet
    // attached, and a convenience must never be fatal.
    LaunchedEffect(topicId) { runCatching { focus.requestFocus() } }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 560.dp)
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(Cream)
            .padding(start = 24.dp, end = 24.dp, top = Spacing.md, bottom = Spacing.xxl),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.align(Alignment.TopCenter)) { SheetGrabber() }
            SheetCloseButton(onClose, Modifier.align(Alignment.TopEnd).padding(top = Spacing.md))
        }

        WashHeadline(
            parts = listOf(topicText(topicId) to false),
            modifier = Modifier.padding(top = 14.dp, bottom = Spacing.lg, end = 40.dp),
            fontSize = 23.sp,
            lineHeight = (23f * 1.18f).sp,
            letterSpacing = (-0.018).em,
        )

        // THE EXAMPLE IS A CARD ABOVE THE FIELD AND NOT A PLACEHOLDER. A placeholder disappears at
        // the first keystroke, which is exactly when the user still wants someone else's sentence
        // in front of them. Dismissible, because a user who already knows does not.
        if (!exampleHidden) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xl)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LavenderWash)
                    .outline(Purple.copy(alpha = 0.14f), 14.dp, width = 1.dp)
                    .padding(start = 14.dp, end = Spacing.xl, top = Spacing.lg, bottom = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        PromptsCopy.EXAMPLE_EYEBROW,
                        modifier = Modifier.padding(bottom = 3.dp),
                        color = Purple, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp, letterSpacing = 0.08.em,
                    )
                    Text(
                        exampleFor(topicId),
                        color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                        fontSize = 13.sp, lineHeight = (13f * 1.45f).sp,
                    )
                }
                // 22 drawn, 44 answered -- see [SheetCloseButton]. The overflow reaches about
                // 11 over the example's text, which is not itself tappable, so the only effect is
                // that a near-miss dismisses the example rather than doing nothing.
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .requiredSize(ComponentSizes.minTapTarget)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onHideExample)
                            .semantics { contentDescription = PromptsCopy.HIDE_EXAMPLE },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Purple.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(BrandIcon.Close, 13.dp, tint = Purple, strokeWidth = 2.2.dp)
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .shakeOnce(shakeKey, showNudge && motion.enabled)
                .clip(RoundedCornerShape(Spacing.xxl))
                .halo(haloColor, Spacing.xxl)
                .background(Elevated)
                .outline(borderColor, Spacing.xxl)
                // Bottom 34 rather than 16: the counter sits inside the box, and the text must not
                // run under it.
                .padding(start = Spacing.xxl, end = Spacing.xxl, top = 14.dp, bottom = 34.dp),
        ) {
            Box {
                if (draft.isEmpty()) {
                    Text(
                        PromptsCopy.PLACEHOLDER,
                        color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                        fontSize = 15.5.sp, lineHeight = (15.5f * 1.5f).sp,
                    )
                }
                BasicTextField(
                    value = draft,
                    // THE CAP IS APPLIED HERE, SILENTLY. A paste over the limit is sliced and
                    // nothing is said about it.
                    onValueChange = { onDraftChange(cappedAnswer(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        // rows={4} / min-height 116, so 160 characters fit without an inner
                        // scrollbar at every width in the matrix.
                        .defaultMinSize(minHeight = 116.dp)
                        .focusRequester(focus),
                    textStyle = TextStyle(
                        color = Fg, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                        fontSize = 15.5.sp, lineHeight = (15.5f * 1.5f).sp,
                    ),
                    cursorBrush = SolidColor(Purple),
                )
            }
            if (showCounter) {
                Text(
                    "$length/$PROMPT_MAX_CHARS",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        // The box reserves 34 below the text and the counter sits 10 from the
                        // edge, so it is pushed back out by the 24 between them. An offset
                        // rather than negative padding, which Compose does not have.
                        .offset(y = 24.dp),
                    color = if (atCap) Orange else Subtle,
                    fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    // tabular-nums: 99/160 and 100/160 must not change the numeral's width, or the
                    // counter twitches sideways as the user types.
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
            }
        }

        // RESERVED AT 34, in every state. See the function header.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.lg)
                .heightIn(min = 34.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when {
                showNudge -> {
                    Box(
                        Modifier
                            .padding(top = 1.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Danger),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "!",
                            color = Color.White, fontFamily = Lora, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        PromptsCopy.EMPTY_SUBMIT,
                        color = DangerFg, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp, lineHeight = (13.5f * 1.4f).sp,
                    )
                }
                atCap -> Text(
                    PromptsCopy.AT_CAP,
                    color = Orange, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, lineHeight = (13f * 1.4f).sp,
                )
                else -> Text(
                    PromptsCopy.FLOOR,
                    color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                    fontSize = 13.sp, lineHeight = (13f * 1.4f).sp,
                )
            }
        }

        // SUNSET, and NEVER DISABLED. An empty press produces the nudge above rather than nothing.
        PrimaryButton(
            label = PromptsCopy.SAVE,
            onClick = onSave,
            modifier = Modifier.padding(top = Spacing.lg),
            variant = PrimaryButtonVariant.Sunset,
        )
    }
}

// ── the screen ──────────────────────────────────────────────────────────────

@Composable
fun ProfilePromptsScreen(
    state: PromptsState = PromptsState(),
    onBack: () -> Unit = {},
    onOpenTopics: () -> Unit = {},
    onWriteTopic: (String) -> Unit = {},
    onEditPrompt: (String) -> Unit = {},
    onDraftChange: (String) -> Unit = {},
    onHideExample: () -> Unit = {},
    onSave: () -> Unit = {},
    onDismissSheet: () -> Unit = {},
    onContinue: () -> Unit = {},
    /** Fires on the REFUSED press, never on render. */
    onRefused: () -> Unit = {},
    /** Artboard only — forces the toast open. See the photo screen's equivalent. */
    previewToast: Boolean = false,
) {
    val toast = rememberRefusalToast()

    Box(Modifier.fillMaxSize()) {
        RealYouScaffold(
            step = RealYouStep.Prompts,
            onBack = onBack,
            // THE BAR SCROLLS ON THIS SCREEN. Deliberate; see the file header.
            progressFixed = false,
            footer = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = Spacing.xl, bottom = 14.dp),
                ) {
                    RefusalToast(
                        visible = toast.visible || previewToast,
                        icon = BrandIcon.Edit,
                        message = PromptsCopy.TOAST,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NextButton(
                            PromptsCopy.CTA,
                            onClick = {
                                if (state.canContinue) {
                                    onContinue()
                                } else {
                                    toast.show()
                                    onRefused()
                                }
                            },
                            arrowSize = 22.dp,
                            circleSize = 52.dp,
                        )
                    }
                }
            },
        ) {
            StepProgress(steps = RealYouStep.COUNT, current = RealYouStep.Prompts.progressSegment)
            Spacer(Modifier.height(20.dp))

            WashHeadline(
                parts = listOf(
                    PromptsCopy.HEADLINE_LEAD to false,
                    PromptsCopy.HEADLINE_EM to true,
                    PromptsCopy.HEADLINE_TAIL to false,
                ),
                modifier = Modifier.padding(bottom = Spacing.md),
                fontSize = 30.sp,
                lineHeight = (30f * 1.08f).sp,
                letterSpacing = (-0.018).em,
            )
            Text(
                PromptsCopy.SUB,
                modifier = Modifier.widthIn(max = 330.dp),
                color = Neutral, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp, lineHeight = (14.5f * 1.45f).sp,
            )

            if (state.count > 0) {
                Column(
                    Modifier.padding(top = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    // A NEW CARD APPEARS AT THE BOTTOM, not the top: the reading order stays
                    // chronological, so a user adding a second prompt does not find their first
                    // one has moved.
                    state.prompts.forEach { prompt ->
                        FilledPromptCard(prompt) { onEditPrompt(prompt.topicId) }
                    }
                }
            }

            // AT 3 SAVED THE BLOCK IS ABSENT, not disabled. There is nothing left to suggest.
            if (state.count < PROMPTS_MAX) {
                Text(
                    if (state.count == 0) {
                        PromptsCopy.SECTION_NONE_SAVED
                    } else {
                        PromptsCopy.SECTION_SOME_SAVED
                    },
                    modifier = Modifier.padding(
                        top = if (state.count == 0) 20.dp else 22.dp,
                        bottom = Spacing.lg,
                    ),
                    color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.5.sp, letterSpacing = 0.08.em,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    suggestionsFor(
                        used = state.usedTopicIds,
                        count = if (state.count == 0) 3 else 2,
                    ).forEach { topic ->
                        SuggestionCard(topic) { onWriteTopic(topic.id) }
                    }
                    BrowseAllButton(onOpenTopics)
                }
            }

            Text(
                PromptsCopy.counter(state.count),
                modifier = Modifier.padding(top = 14.dp, start = 2.dp),
                color = if (state.canContinue) SuccessFg else Subtle,
                fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                maxLines = 1, textAlign = TextAlign.Start,
            )

            Spacer(Modifier.height(20.dp))
        }

        when (val sheet = state.sheet) {
            is PromptSheet.Topics -> SheetScaffold(onDismiss = onDismissSheet) {
                TopicPickerSheet(
                    used = state.usedTopicIds,
                    onPick = onWriteTopic,
                    onClose = onDismissSheet,
                )
            }
            is PromptSheet.Write -> SheetScaffold(onDismiss = onDismissSheet) {
                WritePromptSheet(
                    topicId = sheet.topicId,
                    draft = state.draftFor(sheet.topicId),
                    nudge = state.nudge,
                    exampleHidden = state.exampleHiddenFor == sheet.topicId,
                    onDraftChange = onDraftChange,
                    onHideExample = onHideExample,
                    onSave = onSave,
                    onClose = onDismissSheet,
                )
            }
            null -> Unit
        }
    }
}

// ── previews: eight states x three frames ───────────────────────────────────

private val ONE = listOf(SavedPrompt("first_date", "Talk about anything real. Not jobs, not pets, not the weather. The thing actually on your mind this week. Bring it. I'll listen."))
private val THREE = ONE + listOf(
    SavedPrompt("hill_to_die_on", "Showing up. Cancelling last minute isn't a scheduling problem, it's an answer."),
    SavedPrompt("cross_town", "A proper conversation. An old cinema. The 8pm walk after a long day."),
)
private const val MID_DRAFT = "Talk about anything real. Not jobs, not pets, not the weather."
private val FULL_DRAFT = cappedAnswer(
    "Talk about anything real. Not jobs, not pets, not the weather. The thing actually on your " +
        "mind this week. Bring it. I will listen for the entire thirty minutes!",
)

@Preview(name = "A · 0 of 3 · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PR_A375() { ProfilePromptsScreen() }

@Preview(name = "A · 0 of 3 · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_A390() { ProfilePromptsScreen() }

@Preview(name = "B · 1 of 3 · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_B390() { ProfilePromptsScreen(PromptsState(prompts = ONE)) }

@Preview(name = "C · 3 of 3 · 430", showBackground = true, widthDp = 430, heightDp = 932)
@Composable private fun PR_C430() { ProfilePromptsScreen(PromptsState(prompts = THREE)) }

@Preview(name = "D · topic sheet · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_D390() {
    ProfilePromptsScreen(PromptsState(prompts = ONE, sheet = PromptSheet.Topics))
}

@Preview(name = "E · write empty · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_E390() {
    ProfilePromptsScreen(PromptsState(sheet = PromptSheet.Write("first_date")))
}

@Preview(name = "F · write mid · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_F390() {
    ProfilePromptsScreen(
        PromptsState(
            sheet = PromptSheet.Write("first_date"),
            drafts = mapOf("first_date" to MID_DRAFT),
        ),
    )
}

@Preview(name = "G · write at cap · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_G390() {
    ProfilePromptsScreen(
        PromptsState(
            sheet = PromptSheet.Write("first_date"),
            drafts = mapOf("first_date" to FULL_DRAFT),
        ),
    )
}

@Preview(name = "H · write nudge · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_H390() {
    ProfilePromptsScreen(PromptsState(sheet = PromptSheet.Write("first_date"), nudge = true))
}

@Preview(name = "toast · refused · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PR_T375() { ProfilePromptsScreen(previewToast = true) }
