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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.showup.designsystem.Border
import com.showup.designsystem.ComponentSizes
import com.showup.designsystem.eyebrowCase
import com.showup.designsystem.Cream
import com.showup.designsystem.Danger
import com.showup.designsystem.DangerFg
import com.showup.designsystem.Elevated
import com.showup.designsystem.Fg
import com.showup.designsystem.LavenderWash
import com.showup.designsystem.LilacWash
import com.showup.designsystem.Lora
import com.showup.designsystem.Manrope
import com.showup.designsystem.Motion
import com.showup.designsystem.Neutral
import com.showup.designsystem.Orange
import com.showup.designsystem.PrimaryButton
import com.showup.designsystem.PrimaryButtonVariant
import com.showup.designsystem.Purple
import com.showup.designsystem.Spacing
import com.showup.designsystem.SheetCloseButton
import com.showup.designsystem.SheetGrabber
import com.showup.designsystem.SheetScaffold
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

    /**
     * PROPOSED — not yet approved.
     *
     * SHOWUP-158 specifies three status messages and this is a fourth: the ticket was written
     * before `/me/prompts` existed, so a save could not fail. Worded to match the two the flow
     * already has for a transport failure ("We couldn't send the code just now. Please try
     * again."), and marked with the same `_PROPOSED` suffix the rest of the flow uses for copy
     * that design has not signed off.
     */
    const val SAVE_FAILED_PROPOSED = "We couldn't save that just now. Please try again."
    const val SAVE = "Save"

    const val HIDE_EXAMPLE = "Hide example"
    const val EDIT_PROMPT = "Edit prompt"
}

/**
 * How far a sheet has to be pushed down before letting go dismisses it.
 *
 * In PIXELS because a drag arrives in pixels. 120 is roughly a thumb's travel: far enough that a
 * scroll inside the sheet cannot trigger it by accident, near enough that the gesture does not
 * feel like it is being resisted. Local rather than a token -- it is this gesture's threshold and
 * nothing else's, and `Spacing` holds no value that means "a deliberate drag".
 */

/** Which sheet is up, if any. */
@Serializable
sealed interface PromptSheet {
    /** All fifteen. Opened by `Browse all 15 topics` only — never the critical path. */
    @Serializable
    data object Topics : PromptSheet

    /**
     * Write or edit one answer.
     *
     * [editing] is what makes Save overwrite rather than append, and it is carried on the sheet
     * rather than derived from whether the topic is already used — because a user can open the
     * topic sheet, pick a topic, and be editing nothing at all.
     *
     * [entryPoint] is carried for the same reason and a sharper one: the registry says
     * [editing] IS THE ONLY THING THAT RIDES HERE NOW. The sheet used to carry `entry_point`
     * too, so that `prompt_saved` and `prompt_editor_dismissed` -- which happen later -- could
     * still report the control that started the sheet. §18 is retired (registry 1.4.5), and what
     * those two events need from the beginning of the sheet is whether it was an edit.
     */
    @Serializable
    data class Write(
        val topicId: String,
        val editing: Boolean = false,
    ) : PromptSheet
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
@Serializable
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
    /**
     * A save is in flight.
     *
     * Save is NEVER DISABLED -- the group rule holds -- so this changes nothing on screen. What it
     * does is stop a second press starting a second request while the first is still open, which
     * on an idempotent PUT would be harmless and on a slow connection would still be two.
     */
    val saving: Boolean = false,
    /**
     * The last save did not land.
     *
     * NEW GROUND. SHOWUP-158 has no failure state for Save, because there was no endpoint when it
     * was written -- the sheet's three status messages are the floor, the cap and the empty press.
     * A fourth message goes in the same RESERVED row, so nothing about the layout changes, and the
     * sheet stays open with the text still in it.
     */
    val failed: Boolean = false,
    /**
     * When the sheet now up was opened, as wall-clock millis. 0 when none is.
     *
     * `time_on_sheet_s` needs a start, and one field serves both sheets because only one is ever
     * open. Wall clock rather than elapsed-since-boot ON PURPOSE: it is the only one still true
     * after process death, and a duration that resets when Android kills the app would report the
     * abandonment it is measuring as having taken no time at all.
     */
    val sheetOpenedAtMillis: Long = 0,
    /** When this visit to the step began. Feeds `time_on_step_s` on the accepted Continue. */
    val stepStartedAtMillis: Long = 0,
    /**
     * How many topics have been chosen in this visit to the step.
     *
     * `selection_index` is this plus one, and the rule the registry is emphatic about is that it
     * COUNTS PER VISIT TO THE STEP, NOT PER SHEET: it does not reset when a sheet closes. Reset it
     * per sheet and "which topic did they reach for first" quietly becomes "which topic did they
     * reach for first in this sheet", which is a question nobody asked. It lives in the persisted
     * state so a process death does not restart the count either. An EDIT does not increment it.
     */
    val topicSelections: Int = 0,
    /** Whether `prompts_minimum_met` has fired. Once, on the FIRST save, never again. */
    val minimumReported: Boolean = false,
    /**
     * The topic whose 160-character cap has already been reported in this editor session.
     *
     * `prompt_char_limit_reached` fires ONCE PER EDITOR SESSION, not per keystroke — otherwise
     * every character typed at the cap is another row saying the same thing. Cleared when a sheet
     * opens, which is what makes it per session rather than per topic.
     */
    val charLimitReportedFor: String? = null,
) {
    val count: Int get() = prompts.size
    val canContinue: Boolean get() = count >= PROMPTS_REQUIRED
    val usedTopicIds: List<String> get() = prompts.map { it.topicId }

    fun draftFor(topicId: String): String = drafts[topicId].orEmpty()

    companion object {
        /**
         * Survives a rotation AND process death.
         *
         * WHY JSON RATHER THAN A FLATTENED LIST. This was a hand-written `listSaver` that packed
         * the sheet into three parallel fields and the drafts into two parallel lists, read back
         * by index. It worked, and every future field would have had to be added to two ordered
         * lists that only agree by inspection.
         *
         * `@Serializable` costs nothing here -- kotlinx-serialization is already a dependency,
         * because the generated API client is built on it -- and it is the SAME mechanism the iOS
         * side uses to put this value in `@SceneStorage`. The two platforms now persist the same
         * shape rather than two hand-rolled encodings that have to be kept in step.
         *
         * The rule this serves is the flow README's 4a: a resumed step behaves like a freshly
         * reached one WITH EVERYTHING ALREADY ENTERED STILL PRESENT. A half-written prompt lost to
         * a rotation is exactly the failure that rule exists to prevent.
         */
        fun encode(state: PromptsState): String = Json.encodeToString(serializer(), state)

        /**
         * Anything that does not decode is an empty screen rather than a crash.
         *
         * A stored value from an older build is a shape this one has never seen, and losing a
         * draft is survivable where refusing to launch is not.
         */
        fun decode(raw: String): PromptsState =
            runCatching { Json.decodeFromString<PromptsState>(raw) }.getOrDefault(PromptsState())
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
/**
 * `border-radius: 9999` -- a pill whose corners follow its own height.
 *
 * NOT A FIXED RADIUS, and that is the whole point. The topic rows clip to [CircleShape], which is
 * a pill: its corner radius is half the height, whatever the height turns out to be. The stroke
 * was drawn at a fixed 25, which agrees with that only while a row is exactly 50 tall. A topic
 * whose text wraps to two lines grows past 50, the clip's corners open out to match, the stroke's
 * do not -- and the parts of the stroke that now sit outside the clip are cut away. The result is
 * a purple outline with pieces missing, on exactly the rows with the longest questions.
 *
 * iOS never had this: it draws the same row with `Capsule()`, which is a pill by construction.
 */
private fun Modifier.outlinePill(color: Color, width: Dp = 1.5.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2f, w / 2f),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius((size.height - w) / 2f),
        style = Stroke(width = w),
    )
}

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
                Icon(BrandIcon.Plus, 13.dp, tint = Purple, strokeWidth = 2.6f)
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
            .outlinePill(Border)
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
            .background(LilacWash)
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
                Icon(BrandIcon.Pen, 14.dp, tint = Purple, strokeWidth = 1.8f)
            }
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
    onPick: (topicId: String) -> Unit,
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
                    group.label.eyebrowCase(),
                    modifier = Modifier.padding(
                        start = 2.dp,
                        top = if (groupIndex == 0) 2.dp else Spacing.xl,
                    ),
                    color = Subtle, fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.5.sp, letterSpacing = 0.08.em,
                )
                group.topics.forEach { topic ->
                    val isUsed = topic.id in used
                    // Flat across the whole sheet rather than within the group: "the row's
                    // index" is what a person scanning the list sees, and the group boundaries
                    // are already carried by `topic_group`.
                    val position = PROMPT_TOPICS.indexOfFirst { it.id == topic.id }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .clip(CircleShape)
                            .background(if (isUsed) Fg.copy(alpha = 0.03f) else Elevated)
                            .outlinePill(if (isUsed) Border else Purple)
                            .then(
                                if (isUsed) {
                                    Modifier
                                } else {
                                    Modifier.clickable(role = Role.Button) {
                                        onPick(topic.id)
                                    }
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
                                PromptsCopy.TOPIC_USED.eyebrowCase(),
                                color = Subtle, fontFamily = Manrope,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.5.sp, letterSpacing = 0.06.em,
                            )
                        } else {
                            Icon(BrandIcon.Plus, Spacing.xxl, tint = Purple, strokeWidth = 2.4f)
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
    failed: Boolean,
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

    // THE SHEET ASKS FOR 560 AND TAKES LESS WHEN THERE IS LESS.
    //
    // `heightIn(min = 560)` on its own was a bug, and the fit harness found it the first time it
    // was given a keyboard: on a 320 x 686 phone with a 300dp IME there are 362dp of screen left,
    // the column demanded 560, and Compose resolved that by giving its children what was left --
    // which was nothing. `Save` measured ZERO HEIGHT on the narrowest phones, and the status line
    // with it. A control the user cannot see, on the only screen where it is the way out.
    //
    // The outer scroll is what makes the minimum a minimum rather than a demand: it measures the
    // content with no height limit, then takes the smaller of that and what the parent offers. On
    // a phone with room nothing scrolls and the sheet is exactly what it was -- 560 or its
    // content, whichever is larger. On a phone without room it scrolls, which is the same answer
    // `WelcomeScaffold(scrollWhenTight)` reached for the same reason: "between a CTA the user
    // cannot reach and a few points of scroll, the scroll is the right failure."
    //
    // The insets are OUTSIDE the scroll, so the keyboard shortens the viewport rather than
    // scrolling with the content.
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(Cream)
            .verticalScroll(rememberScrollState()),
    ) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 560.dp)
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
                            Icon(BrandIcon.Close, 13.dp, tint = Purple, strokeWidth = 2.2f)
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
                // The failure sits ABOVE the nudge in this order because it is the more recent
                // thing that happened: a user whose save failed has a non-empty field, so the two
                // cannot both be true anyway.
                failed -> {
                    Box(
                        Modifier
                            .padding(top = 1.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Danger),
                        contentAlignment = Alignment.Center,
                    ) {
                        UnscaledGlyph("!", 12.sp, Lora)
                    }
                    Text(
                        PromptsCopy.SAVE_FAILED_PROPOSED,
                        color = DangerFg, fontFamily = Manrope, fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp, lineHeight = (13.5f * 1.4f).sp,
                    )
                }
                showNudge -> {
                    Box(
                        Modifier
                            .padding(top = 1.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Danger),
                        contentAlignment = Alignment.Center,
                    ) {
                        // AN ICON, NOT READING TEXT, so it does not scale with the system font.
                        //
                        // Every other glyph in this app is drawn in dp and is unaffected by the
                        // user's type size; this one happens to be a character, which at 2x turned
                        // a 12sp "!" into 24sp inside an 18dp circle and clipped it on all
                        // seventeen devices. Pinning the font scale is how a glyph opts out --
                        // the circle it lives in cannot grow, because the row it sits in is
                        // reserved at 34.
                        UnscaledGlyph("!", 12.sp, Lora)
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
}

/**
 * A character used as an ICON, drawn at a fixed size whatever the system font is set to.
 *
 * Icons in this app are drawn in dp and do not scale; two of them happen to be characters rather
 * than paths -- the danger "!" in the failed photo slot and the one in the empty-submit row. Both
 * live inside a circle whose size is fixed by the layout around it, so scaling the glyph only
 * clips it. `Density(density, fontScale = 1f)` is the supported way to opt a subtree out.
 *
 * Reading text is NEVER drawn through this. Everything a user reads scales.
 */
@Composable
internal fun UnscaledGlyph(
    glyph: String,
    size: androidx.compose.ui.unit.TextUnit,
    family: androidx.compose.ui.text.font.FontFamily,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale = 1f),
    ) {
        Text(
            glyph,
            color = Color.White, fontFamily = family, fontWeight = FontWeight.Bold,
            fontSize = size,
        )
    }
}

// ── the screen ──────────────────────────────────────────────────────────────

@Composable
fun ProfilePromptsScreen(
    state: PromptsState = PromptsState(),
    onBack: () -> Unit = {},
    onOpenTopics: () -> Unit = {},
    /** A suggestion card. The Int is which card, from 0 -- `position` in the registry. */
    onWriteTopic: (topicId: String) -> Unit = {},
    /** A row of the browse sheet. The Int is the row's index across the whole sheet. */
    onPickTopic: (topicId: String) -> Unit = {},
    onEditPrompt: (String) -> Unit = {},
    onDraftChange: (String) -> Unit = {},
    onHideExample: () -> Unit = {},
    onSave: () -> Unit = {},
    onDismissSheet: (SheetDismissMethod) -> Unit = {},
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
                        PromptsCopy.SECTION_NONE_SAVED.eyebrowCase()
                    } else {
                        PromptsCopy.SECTION_SOME_SAVED.eyebrowCase()
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

            // NO maxLines, and that is a correction rather than an omission.
            //
            // The ticket asks for this on one line at 390 (`white-space: nowrap`) and it is, at
            // every width in the matrix -- at the system's default font size. At the largest
            // accessibility size it is not, and `maxLines = 1` turned that into an ELLIPSIS on all
            // seventeen devices: "1/3 prompts · enough to c…", which loses the half of the sentence
            // that says the requirement is met.
            //
            // nowrap is a statement about the 1x layout, not a promise to the user who has turned
            // their type up. Wrapping costs one line on a screen that already scrolls.
            Text(
                PromptsCopy.counter(state.count),
                modifier = Modifier.padding(top = 14.dp, start = 2.dp),
                color = if (state.canContinue) SuccessFg else Subtle,
                fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                textAlign = TextAlign.Start,
            )

            Spacer(Modifier.height(20.dp))
        }

        when (val sheet = state.sheet) {
            is PromptSheet.Topics -> SheetScaffold(onDismiss = onDismissSheet) {
                TopicPickerSheet(
                    used = state.usedTopicIds,
                    onPick = onPickTopic,
                    onClose = { onDismissSheet(SheetDismissMethod.Close) },
                )
            }
            is PromptSheet.Write -> SheetScaffold(onDismiss = onDismissSheet) {
                WritePromptSheet(
                    topicId = sheet.topicId,
                    draft = state.draftFor(sheet.topicId),
                    nudge = state.nudge,
                    failed = state.failed,
                    exampleHidden = state.exampleHiddenFor == sheet.topicId,
                    onDraftChange = onDraftChange,
                    onHideExample = onHideExample,
                    onSave = onSave,
                    onClose = { onDismissSheet(SheetDismissMethod.Close) },
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
/** The reference's mid-draft: about 63 characters, under the counter's threshold. */
internal const val PROMPT_SAMPLE_MID =
    "Talk about anything real. Not jobs, not pets, not the weather."

/**
 * The reference's full draft, which is EXACTLY 160 characters.
 *
 * Shared rather than retyped, because state G is "at the cap" and a sample one character short is
 * state F wearing its name. That is not hypothetical: the fit sweep and the evidence screenshots
 * each carried their own shortened paraphrase, and the image filed as `G-write-at-cap` showed a
 * grey `130/160` and a violet border -- the calm state -- rather than the amber the state exists
 * to demonstrate. Caught by looking at the picture.
 *
 * `PromptTopicsTest` asserts the 160, so a copy edit here cannot quietly undo it again.
 */
internal val PROMPT_SAMPLE_AT_CAP = cappedAnswer(
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
            drafts = mapOf("first_date" to PROMPT_SAMPLE_MID),
        ),
    )
}

@Preview(name = "G · write at cap · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_G390() {
    ProfilePromptsScreen(
        PromptsState(
            sheet = PromptSheet.Write("first_date"),
            drafts = mapOf("first_date" to PROMPT_SAMPLE_AT_CAP),
        ),
    )
}

@Preview(name = "H · write nudge · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_H390() {
    ProfilePromptsScreen(PromptsState(sheet = PromptSheet.Write("first_date"), nudge = true))
}

@Preview(name = "toast · refused · 375", showBackground = true, widthDp = 375, heightDp = 667)
@Composable private fun PR_T375() { ProfilePromptsScreen(previewToast = true) }

@Preview(name = "I · save failed · 390", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun PR_I390() {
    ProfilePromptsScreen(
        PromptsState(
            sheet = PromptSheet.Write("first_date"),
            drafts = mapOf("first_date" to PROMPT_SAMPLE_MID),
            failed = true,
        ),
    )
}
