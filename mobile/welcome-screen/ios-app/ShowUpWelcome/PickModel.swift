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
private struct StoredPicks: Codable {
    var step: String?
    var interests: InterestsDraft
    var life: LifeDraft
    var touched: Bool = false
    var startedAt: Double?

    init(step: String?, interests: InterestsDraft, life: LifeDraft, touched: Bool, startedAt: Double?) {
        self.step = step
        self.interests = interests
        self.life = life
        self.touched = touched
        self.startedAt = startedAt
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        step = try c.decodeIfPresent(String.self, forKey: .step)
        interests = try c.decode(InterestsDraft.self, forKey: .interests)
        life = try c.decode(LifeDraft.self, forKey: .life)
        touched = try c.decodeIfPresent(Bool.self, forKey: .touched) ?? false
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
    /// Whether the step on screen has been touched since arriving, so a late read cannot overwrite it.
    private var touched = false
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
        touched = restored.touched
        if currentStep != nil { stepStartedAt = restored.startedAt ?? now() }
    }

    // MARK: arriving and leaving

    /// Reads the account once, quietly. Safe to call on every arrival.
    func preload() {
        guard saved == nil, loading == nil else { return }
        loading = Task { [weak self] in
            guard let self else { return }
            let read = await self.store.load()
            self.loading = nil
            // A SAVE MAY HAVE LANDED FIRST, and its answers are newer than this read.
            guard let read, self.saved == nil else { return }
            self.saved = read
            // Every step off screen takes the server's answers now; the one on screen too, unless
            // the user has started on it.
            let onScreen = self.currentStep
            for step in PickStep.allCases where step != onScreen || !self.touched {
                self.reset(step, from: read)
            }
            if onScreen != nil, !self.touched { self.state.prefill += 1 }
            self.write()
        }
    }

    /// A REAL arrival resets the step from the server and reports the view; a redraw does neither.
    func arrived(_ step: PickStep, referrer: ProfileScreen?) {
        preload()
        guard currentStep != step else { return }
        currentStep = step
        touched = false
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
        touched = true
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
            touched = true
            draft.interests = next
            state.interests = draft
            write()
            analytics?.report(ProfileAnalytics.interestSelected(tagId, total: next.count))
        case .deselected(let next):
            touched = true
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
        touched = true
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
        if anything {
            await answer(step, snapshot: snapshot, onAdvance: onAdvance)
        } else {
            skip(step, onAdvance: onAdvance)
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

    /// Saves the picks AS THEY WERE WHEN CONTINUE WAS ACCEPTED, with the position, in one PATCH.
    private func answer(_ step: PickStep, snapshot: PickUiState, onAdvance: () -> Void) async {
        state.saving = true
        clearToast()
        guard let stored = await store.save(body(for: step, snapshot: snapshot)) else {
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
    /// block, which the server refuses to store. LIFE AND FUTURE send all three questions, an
    /// unanswered one as `[]` — "an unanswered question is saved as empty".
    private func body(for step: PickStep, snapshot: PickUiState) -> Components.Schemas.UpsertProfileDto {
        typealias Dto = Components.Schemas.UpsertProfileDto
        let position = Dto.flowPositionPayload(value1: step.position)
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
                interests: tags.isEmpty ? nil : tags,
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
                family: lifeInListOrder(.family, life.picked(.family))
                    .compactMap(Components.Schemas.LifeFamily.init(rawValue:)),
                flowPosition: position,
                living: lifeInListOrder(.living, life.picked(.living))
                    .compactMap(Components.Schemas.LifeLiving.init(rawValue:)),
                partnership: lifeInListOrder(.partnership, life.picked(.partnership))
                    .compactMap(Components.Schemas.LifePartnership.init(rawValue:)))
        case .lifeAhead:
            let life = snapshot.life
            return .init(
                familyAhead: lifeInListOrder(.familyAhead, life.picked(.familyAhead))
                    .compactMap(Components.Schemas.FutureFamily.init(rawValue:)),
                flowPosition: position,
                livingAhead: lifeInListOrder(.livingAhead, life.picked(.livingAhead))
                    .compactMap(Components.Schemas.FutureLiving.init(rawValue:)),
                partnershipAhead: lifeInListOrder(.partnershipAhead, life.picked(.partnershipAhead))
                    .compactMap(Components.Schemas.FuturePartnership.init(rawValue:)))
        }
    }

    /// The step is left — by an answer, a skip or a back. Its picks go back to what the server
    /// holds, which is "back discards unsaved picks".
    private func leave() {
        let step = currentStep
        currentStep = nil
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
                                 touched: touched, startedAt: currentStep == nil ? nil : stepStartedAt)
        guard let data = try? JSONEncoder().encode(stored),
              let text = String(data: data, encoding: .utf8) else { return }
        persist(text)
    }
}
