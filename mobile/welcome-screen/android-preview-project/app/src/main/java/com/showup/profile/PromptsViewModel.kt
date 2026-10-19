/*
 * PromptsViewModel.kt
 * ShowUp · the prompts step's asynchronous work (SHOWUP-158)
 *
 * WHY THIS EXISTS NOW AND NOT WHEN THE SCREEN WAS BUILT
 *
 * It did not, and the comment where its state used to live said why: "the prompts screen owns no
 * asynchronous work. There is no prompt endpoint to call... It becomes a ViewModel the day
 * persistence lands." That day is this commit. `/me/prompts` exists, so saving a prompt is a
 * request that has to outlive a redraw and be cancellable, which is the trigger the Android
 * CLAUDE.md names.
 *
 * The SCREEN did not change. It already took a value and a set of lambdas, which is what made
 * swapping the owner underneath it a change to one file.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * WHAT IS SAVED WHERE, AND WHY IT IS BOTH
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * SAVED PROMPTS COME FROM THE SERVER. [load] reads them on arrival, so a reinstall or a second
 * device shows what the account holds rather than what this phone remembers.
 *
 * THE DRAFT DOES NOT. A half-written answer is not a prompt -- only Save writes one -- so there is
 * nothing to send and nothing to read back. It lives in [SavedStateHandle], which survives a
 * rotation and a process kill, which is what the flow README's rule 4a asks for: a resumed step
 * behaves like a freshly reached one WITH EVERYTHING ALREADY ENTERED STILL PRESENT.
 *
 * Both halves are one value, so there is one place to read and one place to write.
 */
package com.showup.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PromptsViewModel(
    private val repo: PromptsRepository,
    private val saved: SavedStateHandle,
    private val analytics: AnalyticsTracker? = null,
    /**
     * The clock, injected.
     *
     * `time_on_sheet_s` and `time_on_step_s` are the only two numbers here that cannot be asserted
     * without one -- a test that read the real clock would have to assert "about zero", which is
     * the same as asserting nothing. Wall clock rather than elapsed: see
     * [PromptsState.sheetOpenedAtMillis].
     */
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(
        // Whatever survived a process death. Empty on a genuinely fresh arrival, and [load] then
        // fills the saved prompts in from the server.
        PromptsState.decode(saved.get<String>(KEY).orEmpty()),
    )
    val state: StateFlow<PromptsState> = _state.asStateFlow()

    /** Whether the first read has come back. The screen does not need it; the host's routing does. */
    var loaded = false
        private set

    private fun set(next: PromptsState) {
        _state.value = next
        saved[KEY] = PromptsState.encode(next)
    }

    /**
     * Reads what the account already holds.
     *
     * The DRAFTS ARE KEPT. A user who was mid-sentence when the app was killed comes back to the
     * server's prompts and their own unfinished one, which are different things and both theirs.
     */
    fun load() {
        viewModelScope.launch {
            val prompts = repo.list()
            loaded = true
            if (prompts != null) set(_state.value.copy(prompts = prompts))
        }
    }

    // ── arriving ────────────────────────────────────────────────────────────

    /**
     * The step was reached.
     *
     * TWO EVENTS, NOT ONE, and they answer different questions: `screen_viewed` says WHICH SCREEN
     * and `profile_step_viewed` says WHERE IN THE FLOW. `step_index` comes from [RealYouStep]
     * rather than a literal, which is the registry's rule and the reason the 2-vs-10 disagreement
     * of registry 1.3.0 could be resolved by changing a registry rather than a call site.
     *
     * Idempotent on the timer: a re-entry after a process death keeps the original start, so
     * `time_on_step_s` measures the visit rather than the resumption.
     */
    fun arrived(referrer: ProfileScreen? = null) {
        analytics?.report(ProfileAnalytics.screenViewed(ProfileScreen.Prompts, referrer))
        analytics?.report(ProfileAnalytics.realYouStepViewed(RealYouStep.Prompts))
        if (_state.value.stepStartedAtMillis == 0L) {
            set(_state.value.copy(stepStartedAtMillis = now()))
        }
    }

    // ── the sheets ──────────────────────────────────────────────────────────

    fun openTopics() {
        val current = _state.value
        analytics?.report(ProfileAnalytics.promptTopicListOpened(usedCount = current.count))
        set(current.copy(sheet = PromptSheet.Topics, sheetOpenedAtMillis = now()))
    }

    /**
     * A suggestion card on the screen was tapped.
     *
     */
    /**
     * Picking a topic REPLACES the topic sheet with the write sheet -- the two never stack.
     *
     * ONE SELECTION, NOT A DISMISSAL PLUS A SELECTION. The registry says so in as many words, and
     * it is why this does not route through [dismissSheet]: the browse sheet resolving into the
     * write sheet is the sheet succeeding, and counting it as an abandonment would make
     * `prompt_topic_list_opened = selected + dismissed` stop adding up.
     *
     * The example resets too -- it is per sheet rather than per session.
     */
    fun chooseTopic(topicId: String) {
        val current = _state.value
        val selections = current.topicSelections + 1
        analytics?.report(
            ProfileAnalytics.promptTopicSelected(
                topicId = topicId,
                // 1-based, and counted across the whole visit to the step.
                selectionIndex = selections,
            ),
        )
        val editing = topicId in current.usedTopicIds
        analytics?.report(
            ProfileAnalytics.promptEditorOpened(
                topicId = topicId,
                isEdit = editing,
                promptCount = current.count,
            ),
        )
        set(
            current.copy(
                sheet = PromptSheet.Write(topicId, editing = editing),
                nudge = false,
                exampleHiddenFor = null,
                sheetOpenedAtMillis = now(),
                topicSelections = selections,
                charLimitReportedFor = null,
            ),
        )
    }

    /**
     * Editing reopens the sheet WITH THE SAVED TEXT IN THE FIELD, which is what makes Save an
     * overwrite rather than a second prompt.
     *
     * NO `prompt_topic_selected` HERE, and that is the registry change of 16 September 2026: an
     * edit is not a fresh choice of topic, and firing it inflated the topic-demand chart with
     * re-edits of prompts already written. The type that used to enforce it is gone -- §18 was
     * retired in registry 1.4.5 -- so what keeps it true is this function not calling it.
     *
     * It does not raise [PromptsState.topicSelections] either, for the same reason.
     */
    fun editPrompt(topicId: String) {
        val current = _state.value
        val existing = current.prompts.firstOrNull { it.topicId == topicId }?.answer.orEmpty()
        analytics?.report(
            ProfileAnalytics.promptEditorOpened(
                topicId = topicId,
                isEdit = true,
                promptCount = current.count,
            ),
        )
        set(
            current.copy(
                sheet = PromptSheet.Write(topicId, editing = true),
                drafts = current.drafts + (topicId to existing),
                nudge = false,
                exampleHiddenFor = null,
                sheetOpenedAtMillis = now(),
                charLimitReportedFor = null,
            ),
        )
    }

    fun draftChanged(text: String) {
        val current = _state.value
        val sheet = current.sheet as? PromptSheet.Write ?: return
        // ONCE PER EDITOR SESSION. Every keystroke at the cap is the same fact, and 160 rows
        // saying it is not 160 times the information.
        val reachedCap = text.length >= PROMPT_MAX_CHARS
        val report = reachedCap && current.charLimitReportedFor != sheet.topicId
        if (report) analytics?.report(ProfileAnalytics.promptCharLimitReached(sheet.topicId))
        set(
            current.copy(
                drafts = current.drafts + (sheet.topicId to text),
                // The empty-submit error clears on the FIRST CHARACTER TYPED, not on blur and not
                // on a re-press.
                nudge = false,
                charLimitReportedFor =
                    if (report) sheet.topicId else current.charLimitReportedFor,
            ),
        )
    }

    fun hideExample() {
        val current = _state.value
        val sheet = current.sheet as? PromptSheet.Write ?: return
        analytics?.report(ProfileAnalytics.promptExampleDismissed(sheet.topicId))
        set(current.copy(exampleHiddenFor = sheet.topicId))
    }

    /**
     * DISMISSAL KEEPS THE DRAFT. Only Save writes a prompt.
     *
     * [method] is required rather than defaulted, because the four §23 values are four different
     * acts and a default would quietly make three of them look like the fourth. The X is not the
     * Android back gesture, and the registry calls that out by name.
     */
    fun dismissSheet(method: SheetDismissMethod) {
        val current = _state.value
        when (val sheet = current.sheet) {
            is PromptSheet.Topics -> analytics?.report(
                ProfileAnalytics.promptTopicListDismissed(
                    method = method,
                    usedCount = current.count,
                    timeOnSheetSeconds = secondsSince(current.sheetOpenedAtMillis),
                ),
            )

            is PromptSheet.Write -> analytics?.report(
                ProfileAnalytics.promptEditorDismissed(
                    topicId = sheet.topicId,
                    // NEW IN 1.4.5, and the reason retiring §18 cost this event nothing: giving up
                    // on a rewrite is not the same as giving up on a blank one.
                    isEdit = sheet.editing,
                    // The LENGTH, never the draft. The bucket is computed inside the builder.
                    draftLength = current.draftFor(sheet.topicId).trim().length,
                    method = method,
                ),
            )

            null -> return
        }
        set(current.copy(sheet = null, sheetOpenedAtMillis = 0))
    }

    // ── continuing ──────────────────────────────────────────────────────────

    /**
     * Continue was pressed.
     *
     * CONTINUE IS NEVER DISABLED, so this event is the only record that a user tried to leave with
     * nothing written -- there is no disabled button to infer it from. The refused press reports
     * `prompts_below_minimum`, which registry 1.4.2 added as the sibling of `photos_below_minimum`;
     * this screen used to send `nothing_selected`, which belongs to a chooser where nothing was
     * ticked rather than a screen where nothing was written.
     *
     * @return whether the flow may advance. The host routes; this decides and records.
     */
    fun continuePressed(): Boolean {
        val current = _state.value
        if (!current.canContinue) {
            analytics?.report(
                ProfileAnalytics.realYouValidationFailed(
                    fieldId = ProfileField.PROMPTS,
                    rule = ValidationRule.PROMPTS_BELOW_MINIMUM,
                    screen = ProfileScreen.Prompts,
                    step = RealYouStep.Prompts,
                ),
            )
            return false
        }
        analytics?.report(
            ProfileAnalytics.realYouStepCompleted(
                step = RealYouStep.Prompts,
                timeOnStepSeconds = secondsSince(current.stepStartedAtMillis),
                // 1 to 3, the count at the moment Continue was ACCEPTED. Screen-scoped: no other
                // step sends it.
                promptCount = current.count,
            ),
        )
        return true
    }

    /** Whole seconds since a wall-clock stamp, never negative and never a lie about 0. */
    private fun secondsSince(startedAtMillis: Long): Int {
        if (startedAtMillis <= 0L) return 0
        val elapsed = now() - startedAtMillis
        return if (elapsed <= 0L) 0 else (elapsed / 1000L).toInt()
    }

    // ── saving ──────────────────────────────────────────────────────────────

    /**
     * Writes the draft, or nudges when it is empty.
     *
     * THE SHEET CLOSES ON A CONFIRMED SAVE, not on the press. A sheet that closed optimistically
     * and then failed would leave the user looking at a list that does not have their answer in it
     * and no way back to the text they wrote -- so the draft is only cleared once the server has
     * it, and a failure leaves the sheet exactly as it was.
     *
     * NOTHING IS REPORTED ON THE EMPTY PRESS. State H is a nudge, not a validation failure: the
     * one refusal this screen registers is Continue, and adding a second would double-count the
     * same user against a rule that only exists once.
     */
    fun save() {
        val current = _state.value
        val sheet = current.sheet as? PromptSheet.Write ?: return
        val answer = current.draftFor(sheet.topicId)
        if (promptAnswerIsEmpty(answer)) {
            // SAVE IS NEVER DISABLED. An empty press explains.
            set(current.copy(nudge = true))
            return
        }
        if (current.saving) return

        set(current.copy(saving = true))
        viewModelScope.launch {
            when (val result = repo.save(sheet.topicId, answer.trim())) {
                is SavePromptResult.Saved -> {
                    val now = _state.value
                    val existing = now.prompts.indexOfFirst { it.topicId == sheet.topicId }
                    val updated = if (existing >= 0) {
                        now.prompts.toMutableList().also { it[existing] = result.prompt }
                    } else {
                        // A new card appears at the BOTTOM of the list, so the reading order stays
                        // chronological -- the same order the server assigns.
                        now.prompts + result.prompt
                    }
                    analytics?.report(
                        ProfileAnalytics.promptSaved(
                            topicId = sheet.topicId,
                            // An edit does not consume a slot, and `is_edit` has to be honest
                            // about that: `prompt_count` is the count AFTER the action either way.
                            isEdit = sheet.editing,
                            answerLength = result.prompt.answer.length,
                            promptCount = updated.size,
                        ),
                    )
                    // ONCE, on the FIRST save, carrying the topic that got the user there.
                    val crossed = !now.minimumReported && updated.size >= PROMPTS_REQUIRED
                    if (crossed) {
                        analytics?.report(
                            ProfileAnalytics.promptsMinimumMet(
                                count = updated.size,
                                topicId = sheet.topicId,
                            ),
                        )
                    }
                    set(
                        now.copy(
                            prompts = updated,
                            sheet = null,
                            // The draft is cleared only once it has become a prompt.
                            drafts = now.drafts - sheet.topicId,
                            nudge = false,
                            saving = false,
                            failed = false,
                            sheetOpenedAtMillis = 0,
                            minimumReported = now.minimumReported || crossed,
                            charLimitReportedFor = null,
                        ),
                    )
                }

                is SavePromptResult.Failed -> {
                    // The sheet stays open with the text in it. See the function header.
                    set(_state.value.copy(saving = false, failed = true))
                }
            }
        }
    }

    private companion object {
        /** One key, because the whole screen is one value. */
        const val KEY = "profile.prompts"
    }
}
