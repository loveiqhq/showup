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

    // ── the sheets ──────────────────────────────────────────────────────────

    fun openTopics() {
        analytics?.report(
            ProfileAnalytics.promptTopicPickerOpened(slotIndex = _state.value.count),
        )
        set(_state.value.copy(sheet = PromptSheet.Topics))
    }

    /**
     * Picking a topic REPLACES the topic sheet with the write sheet -- the two never stack -- and
     * resets the example, which is per sheet rather than per session.
     */
    fun writeTopic(topicId: String) {
        analytics?.report(ProfileAnalytics.promptTopicSelected(topicId))
        set(
            _state.value.copy(
                sheet = PromptSheet.Write(topicId, editing = topicId in _state.value.usedTopicIds),
                nudge = false,
                exampleHiddenFor = null,
            ),
        )
    }

    /**
     * Editing reopens the sheet WITH THE SAVED TEXT IN THE FIELD, which is what makes Save an
     * overwrite rather than a second prompt.
     */
    fun editPrompt(topicId: String) {
        val current = _state.value
        val existing = current.prompts.firstOrNull { it.topicId == topicId }?.answer.orEmpty()
        set(
            current.copy(
                sheet = PromptSheet.Write(topicId, editing = true),
                drafts = current.drafts + (topicId to existing),
                nudge = false,
                exampleHiddenFor = null,
            ),
        )
    }

    fun draftChanged(text: String) {
        val current = _state.value
        val sheet = current.sheet as? PromptSheet.Write ?: return
        set(
            current.copy(
                drafts = current.drafts + (sheet.topicId to text),
                // The empty-submit error clears on the FIRST CHARACTER TYPED, not on blur and not
                // on a re-press.
                nudge = false,
            ),
        )
    }

    fun hideExample() {
        val current = _state.value
        val sheet = current.sheet as? PromptSheet.Write ?: return
        set(current.copy(exampleHiddenFor = sheet.topicId))
    }

    /** DISMISSAL KEEPS THE DRAFT. Only Save writes a prompt. */
    fun dismissSheet() {
        set(_state.value.copy(sheet = null))
    }

    // ── saving ──────────────────────────────────────────────────────────────

    /**
     * Writes the draft, or nudges when it is empty.
     *
     * THE SHEET CLOSES ON A CONFIRMED SAVE, not on the press. A sheet that closed optimistically
     * and then failed would leave the user looking at a list that does not have their answer in it
     * and no way back to the text they wrote -- so the draft is only cleared once the server has
     * it, and a failure leaves the sheet exactly as it was.
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
                        if (sheet.editing) {
                            ProfileAnalytics.promptEdited(
                                promptId(sheet.topicId, maxOf(existing, 0)),
                            )
                        } else {
                            ProfileAnalytics.promptAnswered(
                                promptId = promptId(sheet.topicId, updated.lastIndex),
                                charCount = result.prompt.answer.length,
                                atCharLimit = result.prompt.answer.length >= PROMPT_MAX_CHARS,
                            )
                        },
                    )
                    set(
                        now.copy(
                            prompts = updated,
                            sheet = null,
                            // The draft is cleared only once it has become a prompt.
                            drafts = now.drafts - sheet.topicId,
                            nudge = false,
                            saving = false,
                            failed = false,
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
