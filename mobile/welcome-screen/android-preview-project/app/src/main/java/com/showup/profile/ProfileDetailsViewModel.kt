/*
 * ProfileDetailsViewModel.kt
 * ShowUp · Embrace 2 and the seven "Share some details" steps -- every rule, no pixels
 * (SHOWUP-166 to SHOWUP-173)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ONE VIEW MODEL FOR THE GROUP, BECAUSE THE GROUP SHARES ONE ANSWER SHEET
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The seven steps read and write one profile, move backwards and forwards between each other, and
 * each one's "back" has to show the previous step's SAVED value. One owner for that state is the
 * project's first rule about state; seven would have to agree with each other through the server.
 * It also owns the one asynchronous act every step has -- the save on Continue, which must finish
 * before the flow moves -- which is the moment the Android CLAUDE.md names for a view model.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ARRIVAL RESETS THE STEP -- BUT ONLY A REAL ARRIVAL
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Every ticket: "Back pops to <the previous step> and discards an unsaved selection", and "a resume,
 * or a back from <the next step>, pre-fills the saved value". Both are one rule: on arrival, a step
 * shows what the server holds. So [arrived] resets that step's draft from [saved].
 *
 * A RECOMPOSITION IS NOT AN ARRIVAL. The screen's arrival effect also re-runs when the Activity is
 * rebuilt -- a dark-mode switch, a font-size change, a locale change -- and resetting then would
 * throw away a half-made choice the user never left. [currentStep] tells the two apart: it is set on
 * arrival and cleared only by the acts that LEAVE a step (Continue, Skip, Back). It and the draft
 * live in the [SavedStateHandle], so they survive process death as well.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * NOT OPTIMISTIC
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * "Persist on Continue, before navigating." A failed save keeps the user on the step with the
 * answer still showing, says so in the group's toast, and the CTA works again. A SKIP is different
 * and is best-effort: it saves nothing, so there is nothing to lose, and the next step's own save
 * carries a later position anyway -- the server only moves it forward.
 */
package com.showup.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import com.showup.api.generated.model.FlowPosition
import com.showup.api.generated.model.UpsertProfileDto
import com.showup.designsystem.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the group's toast is saying, if anything. One toast, two messages. */
enum class DetailToast {
    /** Continue refused on height, gender or orientation -- the step's own sentence. */
    Refusal,

    /** The save on Continue failed. PROPOSED copy; see [DetailsCopy.SAVE_FAILED_PROPOSED]. */
    SaveFailed,
}

/** What a detail screen draws. */
data class DetailsUiState(
    val draft: DetailDraft = DetailDraft(),
    /** A Continue is being saved. The CTA stops answering; it never greys out. */
    val saving: Boolean = false,
    val toast: DetailToast? = null,
    /** Bumped on every toast, so a second refusal while one is up restarts it. */
    val toastTick: Int = 0,
    /**
     * Bumped whenever a step is filled from the SERVER -- on arrival, or when a slow read lands
     * after it. The lists key their one-off scroll on it, so a pre-filled row below the fold is
     * brought into view and a tap never scrolls anything.
     */
    val prefill: Int = 0,
)

open class ProfileDetailsViewModel(
    private val store: ProfileDetailsStore,
    private val positions: FlowPositionSink,
    private val handle: SavedStateHandle = SavedStateHandle(),
    private val analytics: AnalyticsTracker? = null,
    /** Milliseconds. Injected so `time_on_step_s` is testable without waiting. */
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    // `prefill` comes back with the draft: the lists remember (rememberSaveable) which pre-fill
    // they have already scrolled for, and a counter that restarted at 0 after process death would
    // match the wrong one.
    private val _state = MutableStateFlow(
        DetailsUiState(draft = restoreDraft(), prefill = handle[KEY_PREFILL] ?: 0),
    )
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    /** What the server holds. Null until the first successful read. */
    private var saved: SavedDetails? = null
    private var loading: Job? = null

    /** The step the user is on, or null between steps. See the file header. */
    private var currentStep: DetailStep?
        get() = handle.get<String>(KEY_STEP)?.let { name -> DetailStep.entries.firstOrNull { it.name == name } }
        set(value) { handle[KEY_STEP] = value?.name }

    /**
     * Whether the user has touched the current step since arriving, so a late read cannot overwrite
     * it. SAVED WITH THE STEP: after process death the draft comes back from the handle but the
     * account read starts again, and without this the read would land on top of what was picked.
     */
    private var touched: Boolean
        get() = handle[KEY_TOUCHED] ?: false
        set(value) { handle[KEY_TOUCHED] = value }

    /** When the step on screen was arrived at -- saved with it, or a restore would time from 1970. */
    private var stepStartedAt: Long
        get() = handle[KEY_STARTED] ?: now()
        set(value) { handle[KEY_STARTED] = value }

    /** Saved for the same reason: a restore onto the bridge is not a second showing of it. */
    private var embraceAnnounced: Boolean
        get() = handle[KEY_EMBRACE_ANNOUNCED] ?: false
        set(value) { handle[KEY_EMBRACE_ANNOUNCED] = value }
    private var toastJob: Job? = null

    // ── Embrace 2 ───────────────────────────────────────────────────────────────

    /**
     * The bridge is on screen. `screen_viewed`, plus `embrace_bridge_viewed` on a PUSH -- and
     * nothing else, because it is not a step. Also starts the read, so height pre-fills on the next
     * screen without a flash.
     *
     * A POP IS NOT A SHOWING OF THE BRIDGE. Profile 14: "a back-pop onto 13 fires `screen_viewed`
     * there and nothing else" -- the same pop that does not replay the confetti.
     */
    fun embraceArrived(referrer: ProfileScreen?, pop: Boolean) {
        preload()
        if (embraceAnnounced) return
        embraceAnnounced = true
        analytics?.report(ProfileAnalytics.screenViewed(ProfileScreen.EmbraceDetails, referrer))
        if (!pop) analytics?.report(ProfileAnalytics.embraceBridgeViewed(EmbraceVariant.ADD_DETAILS))
    }

    /**
     * `Add profile details`. "Tapping the CTA advances to height and updates the saved flow
     * position" -- location is already recorded on every way in, so this re-states it,
     * best-effort, for the one case where that report was lost to a dropped connection.
     */
    fun embraceContinue(onAdvance: () -> Unit) {
        // Only from a showing of the bridge: the outgoing screen still takes taps while it slides
        // away, and a second tap must not navigate again.
        if (!embraceAnnounced) return
        embraceAnnounced = false
        record(FlowPosition.location)
        onAdvance()
    }

    // ── a step ──────────────────────────────────────────────────────────────────

    /** Reads the account once, quietly. Safe to call on every arrival. */
    fun preload() {
        if (saved != null || loading?.isActive == true) return
        loading = viewModelScope.launch {
            val read = store.load() ?: return@launch
            // A SAVE MAY HAVE LANDED FIRST, and its answer is newer than this read.
            if (saved != null) return@launch
            saved = read
            // Every step off screen takes the server's value now, so arriving at it later draws
            // the right thing on its first frame. The one on screen gets it too -- unless the user
            // has already started answering it.
            val step = currentStep
            var draft = draftFromSaved(_state.value.draft, read, onScreen = step)
            if (step != null && !touched) {
                draft = draftOnArrival(step, draft, read)
                bumpPrefill()
            }
            setDraft(draft)
        }
    }

    /**
     * Step [step] is on screen. A REAL arrival resets it from what the server holds and reports
     * the view; a recomposition of a step the user never left does neither.
     */
    fun arrived(step: DetailStep, referrer: ProfileScreen?) {
        preload()
        if (currentStep == step) return
        currentStep = step
        touched = false
        stepStartedAt = now()
        clearToast()
        setDraft(draftOnArrival(step, _state.value.draft, saved ?: SavedDetails()))
        _state.update { it.copy(saving = false) }
        bumpPrefill()
        analytics?.report(ProfileAnalytics.screenViewed(step.screen, referrer))
        analytics?.report(ProfileAnalytics.detailStepViewed(step))
    }

    /** Height's field. Digits only, at most three, paste included. */
    fun heightChanged(text: String) {
        // Every input, like every press, answers only the step on screen: the outgoing screen
        // still takes taps during its exit, and a pick there would land on a step already left.
        if (currentStep != DetailStep.Height) return
        touched = true
        val filtered = filterHeightInput(text)
        setDraft(_state.value.draft.copy(heightText = filtered))
        // "The toast hides as soon as the value becomes valid."
        if (parseHeight(filtered) != null) clearRefusal()
    }

    /** A row on a single-select step. */
    fun optionTapped(step: DetailStep, value: String) {
        if (_state.value.saving || currentStep != step) return
        touched = true
        val draft = _state.value.draft
        setDraft(draft.withSelection(step, pickSingle(step, draft.selection(step), value)))
        if (_state.value.draft.selection(step) != null) clearRefusal()
    }

    /** A row on dating language. */
    fun languageTapped(value: String) {
        if (_state.value.saving || currentStep != DetailStep.DatingLanguage) return
        touched = true
        val draft = _state.value.draft
        setDraft(draft.copy(languages = toggleMulti(draft.languages, value)))
    }

    /**
     * The visibility band. `field_display_opted_out` on TICK only -- "no event on untick". Nothing
     * is saved here: the flag goes with an accepted Continue and is ignored by a skip.
     */
    fun visibilityToggled(step: DetailStep) {
        if (_state.value.saving || currentStep != step) return
        touched = true
        val draft = _state.value.draft
        val hiding = !draft.isHidden(step)
        setDraft(
            draft.copy(
                hidden = if (hiding) draft.hidden + step.hiddenField else draft.hidden - step.hiddenField,
            ),
        )
        if (hiding) analytics?.report(ProfileAnalytics.fieldDisplayOptedOut(step))
    }

    /**
     * Continue. Refused, a skip, or an answer -- [resolveContinue] decides, and each branch reports
     * exactly what its ticket names, in the order it names.
     */
    fun continuePressed(step: DetailStep, onAdvance: () -> Unit) {
        // NOT THE STEP ON SCREEN: a tap on the outgoing screen during its exit animation.
        if (_state.value.saving || currentStep != step) return
        val draft = _state.value.draft
        when (val outcome = resolveContinue(step, draft)) {
            is DetailContinue.Refused -> {
                analytics?.report(ProfileAnalytics.detailValidationFailed(step, outcome.rule))
                showToast(DetailToast.Refusal)
            }
            DetailContinue.Skip -> skip(step, onAdvance)
            DetailContinue.Answer -> answer(step, draft, onAdvance)
        }
    }

    /** `Skip for now`. Never on gender or orientation, which have no SkipLink. */
    fun skipPressed(step: DetailStep, onAdvance: () -> Unit) {
        if (_state.value.saving || step.mandatory || currentStep != step) return
        skip(step, onAdvance)
    }

    /** The chevron, the swipe and the system back. Discards the unsaved draft for this step. */
    fun backPressed(step: DetailStep, onBack: () -> Unit) {
        if (_state.value.saving || currentStep != step) return
        leave()
        onBack()
    }

    /** Advance the position from a screen outside this group -- media, 09, Stay reachable. */
    fun record(position: FlowPosition) {
        viewModelScope.launch { positions.report(position) }
    }

    // ── internals ───────────────────────────────────────────────────────────────

    /**
     * "Skip advances the flow position, saves nothing for that step and never deletes a value saved
     * earlier." Reported `detail_skipped` then `profile_step_skipped`, the order the tickets give.
     * An empty Continue on the skippable lists lands here too, recorded identically by decision.
     */
    private fun skip(step: DetailStep, onAdvance: () -> Unit) {
        analytics?.report(ProfileAnalytics.detailSkipped(step))
        analytics?.report(ProfileAnalytics.detailStepSkipped(step))
        record(step.position)
        leave()
        onAdvance()
    }

    /**
     * Saves [draft] -- the answer AS IT WAS WHEN CONTINUE WAS ACCEPTED. Height's field still takes
     * typing while the save is in flight, so reading the draft again after the account read would
     * save whatever happened to be in the field by then, and report a bucket for a value that was
     * never accepted.
     */
    private fun answer(step: DetailStep, draft: DetailDraft, onAdvance: () -> Unit) {
        _state.update { it.copy(saving = true) }
        clearToast()
        viewModelScope.launch {
            // `hidden_fields` IS A REPLACE, so it is built from what the account hides NOW -- read
            // fresh for every save, never from a copy. A copy taken on an earlier screen predates
            // the age hidden on the date-of-birth step, and sending it would silently un-hide it.
            // No read, no save: a failed save is recoverable, a guess is not.
            val current = store.load()?.also { saved = it }
            val stored = current?.let { store.save(bodyFor(step, draft, it)) }
            if (stored == null) {
                _state.update { it.copy(saving = false) }
                showToast(DetailToast.SaveFailed)
                return@launch
            }
            saved = stored
            // "detail_answered and profile_step_completed fire before navigation, in that order,
            // and only for an accepted press."
            analytics?.report(ProfileAnalytics.detailAnswered(step, bucketFor(step, draft)))
            analytics?.report(
                ProfileAnalytics.detailStepCompleted(step, ((now() - stepStartedAt) / 1000).toInt()),
            )
            leave()
            _state.update { it.copy(saving = false) }
            onAdvance()
        }
    }

    /** The one PATCH: the answer, this step's visibility, and the position. */
    private fun bodyFor(step: DetailStep, draft: DetailDraft, current: SavedDetails): UpsertProfileDto {
        val hidden = hiddenFieldsToSend(step, current.hiddenFields, draft.isHidden(step))
        val base = UpsertProfileDto(hiddenFields = hidden, flowPosition = step.position)
        return when (step) {
            DetailStep.Height -> base.copy(heightCm = parseHeight(draft.heightText))
            DetailStep.Gender -> base.copy(
                gender = com.showup.api.generated.model.Gender.entries.first { it.value == draft.gender },
            )
            DetailStep.Orientation -> base.copy(
                orientation = com.showup.api.generated.model.Orientation.entries
                    .first { it.value == draft.orientation },
            )
            DetailStep.DatingLanguage -> base.copy(
                datingLanguages = inListOrder(step, draft.languages).map { v ->
                    com.showup.api.generated.model.DatingLanguage.entries.first { it.value == v }
                },
            )
            DetailStep.Education -> base.copy(
                education = com.showup.api.generated.model.Education.entries
                    .first { it.value == draft.education },
            )
            DetailStep.Religion -> base.copy(
                religion = com.showup.api.generated.model.Religion.entries
                    .first { it.value == draft.religion },
            )
            DetailStep.Politics -> base.copy(
                politics = com.showup.api.generated.model.Politics.entries
                    .first { it.value == draft.politics },
            )
        }
    }

    /** `value_bucketed` -- the bucket for height, the list-order set for languages, else the value. */
    private fun bucketFor(step: DetailStep, draft: DetailDraft): String = when (step) {
        DetailStep.Height -> heightBucket(parseHeight(draft.heightText) ?: 0)
        DetailStep.DatingLanguage -> languagesBucketed(inListOrder(step, draft.languages))
        else -> draft.selection(step).orEmpty()
    }

    /**
     * The step is left -- by an answer, a skip or a back. Its part of the draft goes back to what
     * the server holds (the answer just saved, or the earlier value), which is what "back discards
     * an unsaved selection" means, and what lets a later arrival draw the right row on its first
     * frame.
     */
    private fun leave() {
        val step = currentStep
        currentStep = null
        clearToast()
        if (step != null) setDraft(draftOnArrival(step, _state.value.draft, saved ?: SavedDetails()))
    }

    private fun showToast(kind: DetailToast) {
        toastJob?.cancel()
        _state.update { it.copy(toast = kind, toastTick = it.toastTick + 1) }
        toastJob = viewModelScope.launch {
            delay(Motion.TOAST.toLong())
            _state.update { it.copy(toast = null) }
        }
    }

    private fun clearRefusal() {
        if (_state.value.toast == DetailToast.Refusal) clearToast()
    }

    private fun clearToast() {
        toastJob?.cancel()
        toastJob = null
        if (_state.value.toast != null) _state.update { it.copy(toast = null) }
    }

    private fun bumpPrefill() {
        _state.update { it.copy(prefill = it.prefill + 1) }
        handle[KEY_PREFILL] = _state.value.prefill
    }

    private fun setDraft(draft: DetailDraft) {
        _state.update { it.copy(draft = draft) }
        handle[KEY_HEIGHT] = draft.heightText
        handle[KEY_GENDER] = draft.gender
        handle[KEY_ORIENTATION] = draft.orientation
        handle[KEY_LANGUAGES] = ArrayList(draft.languages)
        handle[KEY_EDUCATION] = draft.education
        handle[KEY_RELIGION] = draft.religion
        handle[KEY_POLITICS] = draft.politics
        handle[KEY_HIDDEN] = ArrayList(draft.hidden)
    }

    private fun restoreDraft() = DetailDraft(
        heightText = handle[KEY_HEIGHT] ?: "",
        gender = handle[KEY_GENDER],
        orientation = handle[KEY_ORIENTATION],
        languages = handle.get<ArrayList<String>>(KEY_LANGUAGES)?.toSet().orEmpty(),
        education = handle[KEY_EDUCATION],
        religion = handle[KEY_RELIGION],
        politics = handle[KEY_POLITICS],
        hidden = handle.get<ArrayList<String>>(KEY_HIDDEN)?.toSet().orEmpty(),
    )

    private companion object {
        const val KEY_STEP = "details.step"
        const val KEY_TOUCHED = "details.touched"
        const val KEY_STARTED = "details.startedAt"
        const val KEY_PREFILL = "details.prefill"
        const val KEY_EMBRACE_ANNOUNCED = "details.embraceAnnounced"
        const val KEY_HEIGHT = "details.height"
        const val KEY_GENDER = "details.gender"
        const val KEY_ORIENTATION = "details.orientation"
        const val KEY_LANGUAGES = "details.languages"
        const val KEY_EDUCATION = "details.education"
        const val KEY_RELIGION = "details.religion"
        const val KEY_POLITICS = "details.politics"
        const val KEY_HIDDEN = "details.hidden"
    }
}
