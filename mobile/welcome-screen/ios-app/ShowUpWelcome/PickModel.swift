//
//  PickModel.swift
//  ShowUp · Interests, Life and Future — every rule, no pixels (SHOWUP-174 to SHOWUP-176)
//
//  The Swift twin of `profile/PickStepsViewModel.kt`. Read that file's header; the rules are
//  `DetailsModel`'s, unchanged:
//
//    · ONE MODEL FOR THE THREE: one answer sheet, and each step's back shows the previous step's
//      saved picks.
//    · ARRIVAL RESETS THE STEP — BUT ONLY A REAL ARRIVAL. `currentStep` tells an arrival from a
//      redraw (a Dynamic Type change, a scene restore), and it and the drafts live in scene storage.
//    · NOT OPTIMISTIC. A failed save keeps the user on the step with the picks showing and says so.
//    · EVERY PRESS ANSWERS ONLY THE STEP ON SCREEN.
//    · NEVER SAVE OVER ANSWERS THE SCREEN HAS NOT SHOWN. A late read fills every part the user has
//      not touched, and a Continue with no read yet reads first; a read that fails is a failed save.
//
//  No refusal: Continue with nothing picked IS a skip on all three, so the only toast is a failed
//  save.
//

import Foundation
import ShowUpAPI

/// What Interests, Life and Future draw.
struct PickUiState: Equatable, Sendable {
    var interests = InterestsDraft()
    var life = LifeDraft()
    /// A Continue is being saved. The CTA stops answering; it never greys out.
    var saving = false
    /// The save on Continue failed. The only toast these screens have.
    var saveFailed = false
    /// Bumped on every failed save, so a second failure while the toast is up is announced again.
    var toastTick = 0
    /// Bumped whenever a step is filled from the SERVER, so the chips draw it without the 180 ms
    /// change ("pre-fill without animation").
    var prefill = 0
}

/// What survives in scene storage. Every field after `life` decodes as absent from older values.
///
/// `touchedParts` is a new key, not `touched`: that one held a Bool, and a value written by the
/// older build must not be read back as a list — it is simply ignored.
private struct StoredPicks: Codable {
    var step: String?
    var interests: InterestsDraft
    var life: LifeDraft
    var touchedParts: [String] = []
    var startedAt: Double?

    init(step: String?, interests: InterestsDraft, life: LifeDraft, touchedParts: [String], startedAt: Double?) {
        self.step = step
        self.interests = interests
        self.life = life
        self.touchedParts = touchedParts
        self.startedAt = startedAt
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        step = try c.decodeIfPresent(String.self, forKey: .step)
        interests = try c.decode(InterestsDraft.self, forKey: .interests)
        life = try c.decode(LifeDraft.self, forKey: .life)
        touchedParts = try c.decodeIfPresent([String].self, forKey: .touchedParts) ?? []
        startedAt = try c.decodeIfPresent(Double.self, forKey: .startedAt)
    }
}

@MainActor
@Observable
final class PickModel {
    private(set) var state = PickUiState()

    private let store: any ProfileDetailsStoring
    private let positions: any FlowPositionReporting
    private let analytics: (any AnalyticsTracking)?
    /// Seconds. Injected so `time_on_step_s` is testable without waiting.
    private let now: () -> Double

    /// Set by `attach`, because `@State` builds the model before `@SceneStorage` can be read.
    private var persist: (String) -> Void = { _ in }
    private var attached = false

    /// What the server holds. Nil until the first successful read.
    private var saved: SavedDetails?
    private var loading: Task<Void, Never>?

    /// The step the user is on, or nil between steps.
    private var currentStep: PickStep?
    /// What the user changed on the step on screen since arriving: How I live field ids, life
    /// question ids and `interestTouchKey`s. A late read fills every OTHER part.
    private var touched: Set<String> = []
    /// Bumped on every real arrival and every leave, so a read or save that lands after the step was
    /// left some other way (a developer-menu jump mid-save) neither advances nor touches the step now
    /// on screen — the Kotlin twin's `visit`.
    private var visit = 0
    private var stepStartedAt: Double = 0
    private var toastTask: Task<Void, Never>?

    init(
        store: any ProfileDetailsStoring,
        positions: any FlowPositionReporting,
        analytics: (any AnalyticsTracking)? = nil,
        now: @escaping () -> Double = { Date().timeIntervalSinceReferenceDate }
    ) {
        self.store = store
        self.positions = positions
        self.analytics = analytics
        self.now = now
    }

    /// Binds to scene storage and restores from it on the first call only — `DetailsModel.attach`.
    func attach(stored: String, persist: @escaping (String) -> Void) {
        self.persist = persist
        guard !attached else { return }
        attached = true
        guard let data = stored.data(using: .utf8),
              let restored = try? JSONDecoder().decode(StoredPicks.self, from: data) else { return }
        state.interests = restored.interests
        state.life = restored.life
        currentStep = restored.step.flatMap(PickStep.init(rawValue:))
        touched = Set(restored.touchedParts)
        if currentStep != nil { stepStartedAt = restored.startedAt ?? now() }
    }

    // MARK: arriving and leaving

    /// Reads the account once, quietly. Safe to call on every arrival.
    func preload() {
        guard saved == nil, loading == nil else { return }
        loading = Task { [weak self] in
            guard let self else { return }
            let read = await self.store.load()
            // Cancelled by a Continue that reads for itself: that read lands, not this one.
            guard !Task.isCancelled else { return }
            self.loading = nil
            // A SAVE MAY HAVE LANDED FIRST, and its answers are newer than this read.
            guard let read, self.saved == nil else { return }
            self.land(read)
        }
    }

    /// The account's answers arrived. Every step off screen takes them; the one on screen takes them
    /// in every part the user has not touched, and is redrawn as a pre-fill only if untouched.
    private func land(_ read: SavedDetails) {
        saved = read
        let onScreen = currentStep
        for step in PickStep.allCases where step != onScreen {
            reset(step, from: read)
        }
        if let onScreen {
            switch onScreen {
            case .interests:
                state.interests = interestsOnLateRead(state.interests, saved: read, touched: touched)
            case .lifeNow, .lifeAhead:
                state.life = lifeOnLateRead(onScreen, current: state.life, saved: read, touched: touched)
            }
            if touched.isEmpty { state.prefill += 1 }
        }
        write()
    }

    /// A REAL arrival resets the step from the server and reports the view; a redraw does neither.
    func arrived(_ step: PickStep, referrer: ProfileScreen?) {
        preload()
        guard currentStep != step else { return }
        currentStep = step
        visit += 1
        touched = []
        stepStartedAt = now()
        clearToast()
        reset(step, from: saved ?? SavedDetails())
        state.saving = false
        state.prefill += 1
        write()
        analytics?.report(ProfileAnalytics.screenViewed(step.screen, referrer: referrer))
        analytics?.report(ProfileAnalytics.pickStepViewed(step))
    }

    /// The steps were left by a route the walk does not take — a developer-menu jump.
    func leftElsewhere() { leave() }

    // MARK: Interests

    /// A How I live chip. One answer per group; the chosen chip clears it. Nothing is reported yet.
    func lifestyleTapped(_ field: LifestyleField, _ value: String) {
        guard onScreen(.interests) else { return }
        touched.insert(field.fieldId)
        var draft = state.interests
        draft.lifestyle[field.fieldId] = pickLifestyle(current: draft.lifestyle[field.fieldId], tapped: value)
        state.interests = draft
        write()
    }

    /// A What I'm into chip. Reported per tap, with the running total after it.
    func interestTapped(_ tagId: String) {
        guard onScreen(.interests) else { return }
        var draft = state.interests
        switch tapInterest(draft.interests, tapped: tagId) {
        case .selected(let next):
            touched.insert(interestTouchKey(tagId))
            draft.interests = next
            state.interests = draft
            write()
            analytics?.report(ProfileAnalytics.interestSelected(tagId, total: next.count))
        case .deselected(let next):
            touched.insert(interestTouchKey(tagId))
            draft.interests = next
            state.interests = draft
            write()
            analytics?.report(ProfileAnalytics.interestDeselected(tagId, total: next.count))
        case .capReached:
            // "A tap on a faded chip does nothing visible" — and fires on every such tap.
            analytics?.report(ProfileAnalytics.interestCapReached())
        }
    }

    /// `Show more` / `Show less`. Reported on expand only; the region does not scroll.
    func showMoreTapped(_ section: InterestSection) {
        guard onScreen(.interests), section.collapsible else { return }
        // NOT `touched`: opening a group picks nothing, and a late read must still fill the chips.
        var draft = state.interests
        let opening = !draft.expanded.contains(section.sectionId)
        if opening { draft.expanded.insert(section.sectionId) } else { draft.expanded.remove(section.sectionId) }
        state.interests = draft
        write()
        if opening { analytics?.report(ProfileAnalytics.showMoreExpanded(section)) }
    }

    // MARK: Life and Future

    /// A pill. The question's exclusive rule applies; nothing is reported per tap.
    func pillTapped(_ step: PickStep, _ question: LifeQuestion, _ value: String) {
        guard onScreen(step), step.questions.contains(question) else { return }
        touched.insert(question.questionId)
        let draft = state.life
        state.life = draft.with(question, toggleLifePick(question, current: draft.picked(question), tapped: value))
        write()
    }

    // MARK: the footer and back

    /// Continue. Anything picked saves and advances; nothing picked is exactly a skip.
    func continuePressed(_ step: PickStep, onAdvance: () -> Void) async {
        guard onScreen(step) else { return }
        let snapshot = state
        let anything = step == .interests ? snapshot.interests.anything : snapshot.life.anything(step)
        if !anything {
            skip(step, onAdvance: onAdvance)
        } else if saved != nil {
            await answer(step, snapshot: snapshot, onAdvance: onAdvance)
        } else {
            await readThenAnswer(step, onAdvance: onAdvance)
        }
    }

    /// `Skip for now`. Saves nothing and keeps whatever was saved before.
    func skipPressed(_ step: PickStep, onAdvance: () -> Void) {
        guard onScreen(step) else { return }
        skip(step, onAdvance: onAdvance)
    }

    /// The chevron and the swipe. Discards the step's unsaved picks.
    func backPressed(_ step: PickStep, onBack: () -> Void) {
        guard onScreen(step) else { return }
        leave()
        onBack()
    }

    // MARK: internals

    private func onScreen(_ step: PickStep) -> Bool { !state.saving && currentStep == step }

    /// "Skip for now: advance the flow position and navigate. Save nothing." No `detail_skipped`.
    private func skip(_ step: PickStep, onAdvance: () -> Void) {
        analytics?.report(ProfileAnalytics.pickStepSkipped(step))
        let positions = self.positions
        let position = step.position
        Task { _ = await positions.report(position) }
        leave()
        onAdvance()
    }

    /// Continue before the account was ever read — a slow or failed first read. Reads it now and fills
    /// every untouched part, so what is saved is what the screen would have shown plus what the user
    /// did. A read that fails is a failed save; saving blind would write `[]` over unseen answers.
    private func readThenAnswer(_ step: PickStep, onAdvance: () -> Void) async {
        state.saving = true
        clearToast()
        // One read, not two racing to land.
        loading?.cancel()
        loading = nil
        let pressedOn = visit
        let fetched = await store.load()
        guard visit == pressedOn else {
            // Left mid-read: the read is still the account's, so it lands like any late read.
            state.saving = false
            if let fetched, saved == nil { land(fetched) }
            return
        }
        guard let read = fetched else {
            state.saving = false
            showSaveFailed()
            return
        }
        land(read)
        // THE MERGE MAY HAVE CUT A SAVED INTEREST (the user's picks first, ten at most). Saving now
        // would delete a tag never on screen: stop, and let the next Continue save what is shown.
        if step == .interests, interestsCutOnLateRead(read, merged: state.interests, touched: touched) {
            state.saving = false
            return
        }
        await answer(step, snapshot: state, onAdvance: onAdvance)
    }

    /// Saves the picks AS THEY WERE WHEN CONTINUE WAS ACCEPTED, with the position, in one PATCH.
    private func answer(_ step: PickStep, snapshot: PickUiState, onAdvance: () -> Void) async {
        state.saving = true
        clearToast()
        let pressedOn = visit
        let result = await store.save(body(for: step, snapshot: snapshot))
        guard visit == pressedOn else {
            // Left mid-save: what was saved is the account's now, but the flow has moved on.
            state.saving = false
            if let result { saved = result }
            return
        }
        guard let stored = result else {
            state.saving = false
            showSaveFailed()
            return
        }
        saved = stored
        // The attribute events in list order, then the step, all before navigation.
        switch step {
        case .interests:
            for (field, value) in answeredLifestyle(snapshot.interests) {
                analytics?.report(ProfileAnalytics.lifestyleAnswered(field, value: value))
            }
        case .lifeNow, .lifeAhead:
            for question in step.questions {
                let picked = snapshot.life.picked(question)
                guard !picked.isEmpty else { continue }
                analytics?.report(ProfileAnalytics.lifeAnswered(
                    step, question, valueBucketed: lifeBucketed(question, picked)))
            }
        }
        analytics?.report(ProfileAnalytics.pickStepCompleted(
            step, timeOnStepSeconds: Int(now() - stepStartedAt)))
        leave()
        state.saving = false
        onAdvance()
    }

    /// The PATCH. Arguments in the schema's declared order, which Swift requires.
    ///
    /// INTERESTS sends only the groups answered, and the interest list only when it holds something:
    /// an unanswered group keeps its earlier value (rule 0), and an empty list is a skip of that
    /// block, which the server refuses to store. LIFE sends all three questions, an unanswered one as
    /// `[]` — "an unanswered question is saved as empty". FUTURE sends only the questions answered:
    /// its ticket says "persist each answered question", so rule 0 keeps an unanswered one's value.
    ///
    /// A PART THE USER DID NOT TOUCH IS NOT SENT IF IT HOLDS A VALUE THIS BUILD CANNOT DRAW: the
    /// screen shows only the values it knows, so re-sending the part would delete the others from a
    /// question nobody touched. Left out, the server keeps it (the Kotlin twin has the long form).
    private func body(for step: PickStep, snapshot: PickUiState) -> Components.Schemas.UpsertProfileDto {
        typealias Dto = Components.Schemas.UpsertProfileDto
        let position = Dto.flowPositionPayload(value1: step.position)
        // Never nil here: a save only runs once the account has been read (`readThenAnswer`).
        let account = saved ?? SavedDetails()
        let touched = self.touched
        func sendable(_ question: LifeQuestion) -> Bool {
            touched.contains(question.questionId) || !lifeHoldsUnknown(question, saved: account)
        }
        switch step {
        case .interests:
            let answers = snapshot.interests.lifestyle
            let tags = interestsInListOrder(snapshot.interests.interests)
                .compactMap(Components.Schemas.InterestTag.init(rawValue:))
            return .init(
                activity: answers[LifestyleField.activity.fieldId]
                    .flatMap(Components.Schemas.Activity.init(rawValue:))
                    .map { Dto.activityPayload(value1: $0) },
                alcohol: answers[LifestyleField.alcohol.fieldId]
                    .flatMap(Components.Schemas.Alcohol.init(rawValue:))
                    .map { Dto.alcoholPayload(value1: $0) },
                diet: answers[LifestyleField.diet.fieldId]
                    .flatMap(Components.Schemas.Diet.init(rawValue:))
                    .map { Dto.dietPayload(value1: $0) },
                flowPosition: position,
                interests: tags.isEmpty || !(interestsTouched(touched) || !interestsHoldUnknown(account))
                    ? nil : tags,
                rhythm: answers[LifestyleField.rhythm.fieldId]
                    .flatMap(Components.Schemas.Rhythm.init(rawValue:))
                    .map { Dto.rhythmPayload(value1: $0) },
                smoking: answers[LifestyleField.smoking.fieldId]
                    .flatMap(Components.Schemas.Smoking.init(rawValue:))
                    .map { Dto.smokingPayload(value1: $0) },
                social: answers[LifestyleField.social.fieldId]
                    .flatMap(Components.Schemas.SocialBattery.init(rawValue:))
                    .map { Dto.socialPayload(value1: $0) })
        case .lifeNow:
            let life = snapshot.life
            return .init(
                family: sendable(.family)
                    ? lifeInListOrder(.family, life.picked(.family))
                        .compactMap(Components.Schemas.LifeFamily.init(rawValue:))
                    : nil,
                flowPosition: position,
                living: sendable(.living)
                    ? lifeInListOrder(.living, life.picked(.living))
                        .compactMap(Components.Schemas.LifeLiving.init(rawValue:))
                    : nil,
                partnership: sendable(.partnership)
                    ? lifeInListOrder(.partnership, life.picked(.partnership))
                        .compactMap(Components.Schemas.LifePartnership.init(rawValue:))
                    : nil)
        case .lifeAhead:
            let life = snapshot.life
            return .init(
                familyAhead: sendable(.familyAhead)
                    ? life.answered(.familyAhead)?.compactMap(Components.Schemas.FutureFamily.init(rawValue:))
                    : nil,
                flowPosition: position,
                livingAhead: sendable(.livingAhead)
                    ? life.answered(.livingAhead)?.compactMap(Components.Schemas.FutureLiving.init(rawValue:))
                    : nil,
                partnershipAhead: sendable(.partnershipAhead)
                    ? life.answered(.partnershipAhead)?.compactMap(Components.Schemas.FuturePartnership.init(rawValue:))
                    : nil)
        }
    }

    /// The step is left — by an answer, a skip or a back. Its picks go back to what the server
    /// holds, which is "back discards unsaved picks".
    private func leave() {
        let step = currentStep
        currentStep = nil
        visit += 1
        clearToast()
        if let step { reset(step, from: saved ?? SavedDetails()) }
        write()
    }

    /// `step`'s part of the drafts, from `from`. Interests also closes every group.
    private func reset(_ step: PickStep, from: SavedDetails) {
        switch step {
        case .interests: state.interests = interestsOnArrival(from)
        case .lifeNow, .lifeAhead: state.life = lifeOnArrival(step, current: state.life, saved: from)
        }
    }

    private func showSaveFailed() {
        toastTask?.cancel()
        state.saveFailed = true
        state.toastTick += 1
        toastTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(Motion.toast * 1_000_000_000))
            guard !Task.isCancelled else { return }
            self?.state.saveFailed = false
        }
    }

    private func clearToast() {
        toastTask?.cancel()
        toastTask = nil
        if state.saveFailed { state.saveFailed = false }
    }

    private func write() {
        let stored = StoredPicks(step: currentStep?.rawValue, interests: state.interests, life: state.life,
                                 touchedParts: touched.sorted(),
                                 startedAt: currentStep == nil ? nil : stepStartedAt)
        guard let data = try? JSONEncoder().encode(stored),
              let text = String(data: data, encoding: .utf8) else { return }
        persist(text)
    }
}
