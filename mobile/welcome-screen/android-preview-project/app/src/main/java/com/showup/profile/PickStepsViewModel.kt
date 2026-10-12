/*
 * PickStepsViewModel.kt
 * ShowUp · Interests, Life and Future -- every rule, no pixels (SHOWUP-174 to SHOWUP-176)
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * ONE VIEW MODEL FOR THE THREE, FOR THE REASON THE DETAIL GROUP HAS ONE
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * The three steps read and write one profile and move backwards and forwards between each other,
 * and each one's back has to show the previous step's SAVED answers: "back pops to politics and
 * discards unsaved picks", "a resume or back from Life pre-fills saved picks". One owner for that
 * state; and it owns the one asynchronous act every step has, the save on Continue, which must
 * finish before the flow moves.
 *
 * It follows `ProfileDetailsViewModel` rule for rule, and the header there is the long form:
 *
 *   - ARRIVAL RESETS THE STEP, BUT ONLY A REAL ARRIVAL. [currentStep] tells a step reached from a
 *     step rebuilt by a dark-mode or font-size change, and lives in the [SavedStateHandle] with the
 *     drafts, so process death keeps both.
 *   - NOT OPTIMISTIC. A failed save keeps the user on the step with their picks still showing and
 *     says so; the CTA works again. A skip saves nothing and is best-effort.
 *   - EVERY PRESS ANSWERS ONLY THE STEP ON SCREEN. The outgoing screen still takes taps while it
 *     slides away; a tap there would land on a step already left.
 *
 * WHAT IS DIFFERENT HERE: there is no refusal. Continue with nothing picked IS a skip, on all three
 * ("no toast, advances, saves nothing"), so the only toast is the failed save.
 */
package com.showup.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.showup.analytics.AnalyticsTracker
import com.showup.api.generated.model.UpsertProfileDto
import com.showup.designsystem.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Interests, Life and Future draw. */
data class PickUiState(
    val interests: InterestsDraft = InterestsDraft(),
    val life: LifeDraft = LifeDraft(),
    /** A Continue is being saved. The CTA stops answering; it never greys out. */
    val saving: Boolean = false,
    /** The save on Continue failed. The only toast these screens have. */
    val saveFailed: Boolean = false,
    /**
     * Bumped whenever a step is filled from the SERVER, on arrival or when a slow read lands after
     * it. The screens key their one-off "region at the top" on it, and key their chips on it so a
     * pre-fill draws without the 180 ms change ("pre-fill without animation").
     */
    val prefill: Int = 0,
)

open class PickStepsViewModel(
    private val store: ProfileDetailsStore,
    private val positions: FlowPositionSink,
    private val handle: SavedStateHandle = SavedStateHandle(),
    private val analytics: AnalyticsTracker? = null,
    /** Milliseconds. Injected so `time_on_step_s` is testable without waiting. */
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PickUiState(interests = restoreInterests(), life = restoreLife(), prefill = handle[KEY_PREFILL] ?: 0),
    )
    val state: StateFlow<PickUiState> = _state.asStateFlow()

    /** What the server holds. Null until the first successful read. */
    private var saved: SavedDetails? = null
    private var loading: Job? = null
    private var toastJob: Job? = null

    /** The step the user is on, or null between steps. */
    private var currentStep: PickStep?
        get() = handle.get<String>(KEY_STEP)?.let { name -> PickStep.entries.firstOrNull { it.name == name } }
        set(value) { handle[KEY_STEP] = value?.name }

    /** Whether the step on screen has been touched since arriving, so a late read cannot overwrite it. */
    private var touched: Boolean
        get() = handle[KEY_TOUCHED] ?: false
        set(value) { handle[KEY_TOUCHED] = value }

    /** When the step on screen was arrived at -- saved with it, or a restore would time from 1970. */
    private var stepStartedAt: Long
        get() = handle[KEY_STARTED] ?: now()
        set(value) { handle[KEY_STARTED] = value }

    // ── arriving and leaving ─────────────────────────────────────────────────

    /** Reads the account once, quietly. Safe to call on every arrival. */
    fun preload() {
        if (saved != null || loading?.isActive == true) return
        loading = viewModelScope.launch {
            val read = store.load() ?: return@launch
            // A SAVE MAY HAVE LANDED FIRST, and its answers are newer than this read.
            if (saved != null) return@launch
            saved = read
            // Every step off screen takes the server's answers now, so arriving at it later draws the
            // right thing on its first frame. The one on screen too -- unless the user has started.
            val onScreen = currentStep
            PickStep.entries.filter { it != onScreen || !touched }.forEach { reset(it, read) }
            if (onScreen != null && !touched) bumpPrefill()
        }
    }

    /**
     * Step [step] is on screen. A REAL arrival resets it from what the server holds and reports the
     * view; a recomposition of a step the user never left does neither.
     */
    fun arrived(step: PickStep, referrer: ProfileScreen?) {
        preload()
        if (currentStep == step) return
        currentStep = step
        touched = false
        stepStartedAt = now()
        clearToast()
        reset(step, saved ?: SavedDetails())
        _state.update { it.copy(saving = false) }
        bumpPrefill()
        analytics?.report(ProfileAnalytics.screenViewed(step.screen, referrer))
        analytics?.report(ProfileAnalytics.pickStepViewed(step))
    }

    /** The steps were left by a route the walk does not take -- a developer-menu jump. */
    fun leftElsewhere() = leave()

    // ── Interests ────────────────────────────────────────────────────────────

    /** A How I live chip. One answer per group; the chosen chip clears it. Nothing is reported yet. */
    fun lifestyleTapped(field: LifestyleField, value: String) {
        if (!onScreen(PickStep.Interests)) return
        touched = true
        val draft = _state.value.interests
        val next = pickLifestyle(draft.lifestyle[field.fieldId], value)
        setInterests(
            draft.copy(
                lifestyle = if (next == null) draft.lifestyle - field.fieldId else draft.lifestyle + (field.fieldId to next),
            ),
        )
    }

    /** A What I'm into chip. Reported per tap, with the running total after it. */
    fun interestTapped(tagId: String) {
        if (!onScreen(PickStep.Interests)) return
        val draft = _state.value.interests
        when (val tap = tapInterest(draft.interests, tagId)) {
            is InterestTap.Selected -> {
                touched = true
                setInterests(draft.copy(interests = tap.interests))
                analytics?.report(ProfileAnalytics.interestSelected(tagId, tap.interests.size))
            }
            is InterestTap.Deselected -> {
                touched = true
                setInterests(draft.copy(interests = tap.interests))
                analytics?.report(ProfileAnalytics.interestDeselected(tagId, tap.interests.size))
            }
            // "A tap on a faded chip does nothing visible" -- and fires on every such tap.
            InterestTap.CapReached -> analytics?.report(ProfileAnalytics.interestCapReached())
        }
    }

    /** `Show more` / `Show less`. Reported on expand only; the region does not scroll. */
    fun showMoreTapped(section: InterestSection) {
        if (!onScreen(PickStep.Interests) || !section.collapsible) return
        // NOT `touched`: opening a group picks nothing, and a late read must still fill the chips.
        val draft = _state.value.interests
        val opening = section.sectionId !in draft.expanded
        setInterests(
            draft.copy(expanded = if (opening) draft.expanded + section.sectionId else draft.expanded - section.sectionId),
        )
        if (opening) analytics?.report(ProfileAnalytics.showMoreExpanded(section))
    }

    // ── Life and Future ──────────────────────────────────────────────────────

    /** A pill on Life or Future. The question's exclusive rule applies; nothing is reported per tap. */
    fun pillTapped(step: PickStep, question: LifeQuestion, value: String) {
        if (!onScreen(step) || question !in step.questions) return
        touched = true
        val draft = _state.value.life
        setLife(draft.with(question, toggleLifePick(question, draft.picked(question), value)))
    }

    // ── the footer and back ──────────────────────────────────────────────────

    /** Continue. Anything picked saves and advances; nothing picked is exactly a skip. */
    fun continuePressed(step: PickStep, onAdvance: () -> Unit) {
        if (!onScreen(step)) return
        val state = _state.value
        val anything = if (step == PickStep.Interests) state.interests.anything else state.life.anything(step)
        if (anything) answer(step, state, onAdvance) else skip(step, onAdvance)
    }

    /** `Skip for now`. Saves nothing and keeps whatever was saved before. */
    fun skipPressed(step: PickStep, onAdvance: () -> Unit) {
        if (!onScreen(step)) return
        skip(step, onAdvance)
    }

    /** The chevron, the swipe and the system back. Discards the step's unsaved picks. */
    fun backPressed(step: PickStep, onBack: () -> Unit) {
        if (!onScreen(step)) return
        leave()
        onBack()
    }

    // ── internals ────────────────────────────────────────────────────────────

    private fun onScreen(step: PickStep): Boolean = !_state.value.saving && currentStep == step

    /**
     * "Skip for now: advance the flow position and navigate. Save nothing." One event, no
     * `detail_skipped` -- these steps have no single field to name.
     */
    private fun skip(step: PickStep, onAdvance: () -> Unit) {
        analytics?.report(ProfileAnalytics.pickStepSkipped(step))
        viewModelScope.launch { positions.report(step.position) }
        leave()
        onAdvance()
    }

    /**
     * Saves the picks AS THEY WERE WHEN CONTINUE WAS ACCEPTED, with the flow position, in one PATCH:
     * "persist, then advance the flow position, then navigate" cannot half-happen when the server
     * commits both in one transaction.
     */
    private fun answer(step: PickStep, state: PickUiState, onAdvance: () -> Unit) {
        _state.update { it.copy(saving = true) }
        clearToast()
        viewModelScope.launch {
            val stored = store.save(bodyFor(step, state))
            if (stored == null) {
                _state.update { it.copy(saving = false) }
                showSaveFailed()
                return@launch
            }
            saved = stored
            // The attribute events in list order, then the step, all before navigation.
            when (step) {
                PickStep.Interests -> answeredLifestyle(state.interests).forEach { (field, value) ->
                    analytics?.report(ProfileAnalytics.lifestyleAnswered(field, value))
                }
                PickStep.LifeNow, PickStep.LifeAhead -> step.questions.forEach { question ->
                    val picked = state.life.picked(question)
                    if (picked.isNotEmpty()) {
                        analytics?.report(ProfileAnalytics.lifeAnswered(step, question, lifeBucketed(question, picked)))
                    }
                }
            }
            analytics?.report(ProfileAnalytics.pickStepCompleted(step, ((now() - stepStartedAt) / 1000).toInt()))
            leave()
            _state.update { it.copy(saving = false) }
            onAdvance()
        }
    }

    /**
     * The PATCH for an accepted Continue.
     *
     * INTERESTS sends only the groups answered, and the interest list only when it has something in
     * it: an unanswered group is "not supplied" and keeps its earlier value (rule 0), and an empty
     * list is a skip of that block, which the server refuses to store as an answer.
     *
     * LIFE AND FUTURE send all three questions, an unanswered one as `[]`: "an unanswered question
     * is saved as empty". Values go in LIST order whatever order they were tapped in.
     */
    private fun bodyFor(step: PickStep, state: PickUiState): UpsertProfileDto {
        val base = UpsertProfileDto(flowPosition = step.position)
        return when (step) {
            PickStep.Interests -> {
                val lifestyle = state.interests.lifestyle
                val interests = interestsInListOrder(state.interests.interests)
                base.copy(
                    activity = lifestyle[LifestyleField.Activity.fieldId]?.let { v ->
                        com.showup.api.generated.model.Activity.entries.first { it.value == v }
                    },
                    diet = lifestyle[LifestyleField.Diet.fieldId]?.let { v ->
                        com.showup.api.generated.model.Diet.entries.first { it.value == v }
                    },
                    alcohol = lifestyle[LifestyleField.Alcohol.fieldId]?.let { v ->
                        com.showup.api.generated.model.Alcohol.entries.first { it.value == v }
                    },
                    smoking = lifestyle[LifestyleField.Smoking.fieldId]?.let { v ->
                        com.showup.api.generated.model.Smoking.entries.first { it.value == v }
                    },
                    rhythm = lifestyle[LifestyleField.Rhythm.fieldId]?.let { v ->
                        com.showup.api.generated.model.Rhythm.entries.first { it.value == v }
                    },
                    social = lifestyle[LifestyleField.Social.fieldId]?.let { v ->
                        com.showup.api.generated.model.SocialBattery.entries.first { it.value == v }
                    },
                    interests = interests.takeIf { it.isNotEmpty() }?.map { v ->
                        com.showup.api.generated.model.InterestTag.entries.first { it.value == v }
                    },
                )
            }
            PickStep.LifeNow -> base.copy(
                partnership = lifeInListOrder(LifeQuestion.Partnership, state.life.picked(LifeQuestion.Partnership))
                    .map { v -> com.showup.api.generated.model.LifePartnership.entries.first { it.value == v } },
                family = lifeInListOrder(LifeQuestion.Family, state.life.picked(LifeQuestion.Family))
                    .map { v -> com.showup.api.generated.model.LifeFamily.entries.first { it.value == v } },
                living = lifeInListOrder(LifeQuestion.Living, state.life.picked(LifeQuestion.Living))
                    .map { v -> com.showup.api.generated.model.LifeLiving.entries.first { it.value == v } },
            )
            PickStep.LifeAhead -> base.copy(
                familyAhead = lifeInListOrder(LifeQuestion.FamilyAhead, state.life.picked(LifeQuestion.FamilyAhead))
                    .map { v -> com.showup.api.generated.model.FutureFamily.entries.first { it.value == v } },
                partnershipAhead = lifeInListOrder(
                    LifeQuestion.PartnershipAhead, state.life.picked(LifeQuestion.PartnershipAhead),
                ).map { v -> com.showup.api.generated.model.FuturePartnership.entries.first { it.value == v } },
                livingAhead = lifeInListOrder(LifeQuestion.LivingAhead, state.life.picked(LifeQuestion.LivingAhead))
                    .map { v -> com.showup.api.generated.model.FutureLiving.entries.first { it.value == v } },
            )
        }
    }

    /**
     * The step is left -- by an answer, a skip or a back. Its picks go back to what the server holds
     * (the answers just saved, or the earlier ones), which is "back discards unsaved picks", and lets
     * a later arrival draw the right chips on its first frame.
     */
    private fun leave() {
        val step = currentStep
        currentStep = null
        clearToast()
        if (step != null) reset(step, saved ?: SavedDetails())
    }

    /** [step]'s part of the drafts, from [from]. Interests also closes every group. */
    private fun reset(step: PickStep, from: SavedDetails) {
        when (step) {
            PickStep.Interests -> setInterests(interestsOnArrival(from))
            PickStep.LifeNow, PickStep.LifeAhead -> setLife(lifeOnArrival(step, _state.value.life, from))
        }
    }

    private fun showSaveFailed() {
        toastJob?.cancel()
        _state.update { it.copy(saveFailed = true) }
        toastJob = viewModelScope.launch {
            delay(Motion.TOAST.toLong())
            _state.update { it.copy(saveFailed = false) }
        }
    }

    private fun clearToast() {
        toastJob?.cancel()
        toastJob = null
        if (_state.value.saveFailed) _state.update { it.copy(saveFailed = false) }
    }

    private fun bumpPrefill() {
        _state.update { it.copy(prefill = it.prefill + 1) }
        handle[KEY_PREFILL] = _state.value.prefill
    }

    private fun setInterests(draft: InterestsDraft) {
        _state.update { it.copy(interests = draft) }
        handle[KEY_LIFESTYLE] = ArrayList(draft.lifestyle.map { (k, v) -> "$k=$v" })
        handle[KEY_INTERESTS] = ArrayList(draft.interests)
        handle[KEY_EXPANDED] = ArrayList(draft.expanded)
    }

    private fun setLife(draft: LifeDraft) {
        _state.update { it.copy(life = draft) }
        handle[KEY_LIFE] = ArrayList(draft.answers.flatMap { (q, values) -> values.map { "$q=$it" } })
        // A question answered with nothing still has to come back as answered-with-nothing.
        handle[KEY_LIFE_TOUCHED] = ArrayList(draft.answers.keys)
    }

    private fun restoreInterests() = InterestsDraft(
        lifestyle = handle.get<ArrayList<String>>(KEY_LIFESTYLE).orEmpty().mapNotNull { entry ->
            entry.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap(),
        interests = handle.get<ArrayList<String>>(KEY_INTERESTS)?.toSet().orEmpty(),
        expanded = handle.get<ArrayList<String>>(KEY_EXPANDED)?.toSet().orEmpty(),
    )

    private fun restoreLife(): LifeDraft {
        val pairs = handle.get<ArrayList<String>>(KEY_LIFE).orEmpty().mapNotNull { entry ->
            entry.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }
        val keys = handle.get<ArrayList<String>>(KEY_LIFE_TOUCHED).orEmpty()
        return LifeDraft(keys.associateWith { q -> pairs.filter { it.first == q }.map { it.second }.toSet() })
    }

    private companion object {
        const val KEY_STEP = "picks.step"
        const val KEY_TOUCHED = "picks.touched"
        const val KEY_STARTED = "picks.startedAt"
        const val KEY_PREFILL = "picks.prefill"
        const val KEY_LIFESTYLE = "picks.lifestyle"
        const val KEY_INTERESTS = "picks.interests"
        const val KEY_EXPANDED = "picks.expanded"
        const val KEY_LIFE = "picks.life"
        const val KEY_LIFE_TOUCHED = "picks.lifeQuestions"
    }
}
