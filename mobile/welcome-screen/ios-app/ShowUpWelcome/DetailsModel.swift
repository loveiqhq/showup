//
//  DetailsModel.swift
//  ShowUp · Embrace 2 and the seven "Share some details" steps — every rule, no pixels
//  (SHOWUP-166 to SHOWUP-173)
//
//  The Swift twin of `profile/ProfileDetailsViewModel.kt`. Read that file's header for the three
//  arguments, all of which hold here unchanged:
//
//    · ONE MODEL FOR THE GROUP, because the steps share one answer sheet and each step's back has
//      to show the previous step's saved value.
//    · ARRIVAL RESETS THE STEP — BUT ONLY A REAL ARRIVAL. A redraw of a step the user never left
//      (a Dynamic Type change, a scene restore) must not throw away a half-made choice;
//      `currentStep` tells the two apart, and it and the draft live in scene storage.
//    · NOT OPTIMISTIC. "Persist on Continue, before navigating." A failed save keeps the user on the
//      step with the answer showing, says so, and the CTA works again. A skip is best-effort.
//

import Foundation
import ShowUpAPI

/// What the group's toast is saying, if anything.
enum DetailToast: Equatable, Sendable {
    /// Continue refused on height, gender or orientation — the step's own sentence.
    case refusal
    /// The save failed. PROPOSED copy; see `DetailsCopy.saveFailedProposed`.
    case saveFailed
}

/// What a detail screen draws.
struct DetailsUiState: Equatable, Sendable {
    var draft = DetailDraft()
    /// A Continue is being saved. The CTA stops answering; it never greys out.
    var saving = false
    var toast: DetailToast?
    /// Bumped on every toast, so a second refusal while one is up restarts it.
    var toastTick = 0
    /// Bumped whenever a step is filled from the SERVER, so the lists bring a saved row into view
    /// once — and a tap never scrolls anything.
    var prefill = 0
}

/// What survives in scene storage: the step the user is on, what they had picked, whether they had
/// touched it, when they arrived, and whether the bridge has been announced.
///
/// `touched` IS SAVED WITH THE STEP: a restore brings the draft back but starts the account read
/// again, and without it the read would land on top of what was picked. `startedAt` likewise, or a
/// restore would time the step from 2001. Every field after `draft` decodes as absent from a value
/// written before it existed.
private struct StoredDetails: Codable {
    var step: String?
    var draft: DetailDraft
    var touched: Bool = false
    var embraceAnnounced: Bool = false
    var startedAt: Double?

    init(step: String?, draft: DetailDraft, touched: Bool, embraceAnnounced: Bool, startedAt: Double?) {
        self.step = step
        self.draft = draft
        self.touched = touched
        self.embraceAnnounced = embraceAnnounced
        self.startedAt = startedAt
    }

    init(from decoder: any Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        step = try c.decodeIfPresent(String.self, forKey: .step)
        draft = try c.decode(DetailDraft.self, forKey: .draft)
        touched = try c.decodeIfPresent(Bool.self, forKey: .touched) ?? false
        embraceAnnounced = try c.decodeIfPresent(Bool.self, forKey: .embraceAnnounced) ?? false
        startedAt = try c.decodeIfPresent(Double.self, forKey: .startedAt)
    }
}

@MainActor
@Observable
final class DetailsModel {
    private(set) var state = DetailsUiState()

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
    private var currentStep: DetailStep?
    /// Whether the user has touched the current step since arriving, so a late read cannot
    /// overwrite a choice already made.
    private var touched = false
    private var stepStartedAt: Double = 0
    private var embraceAnnounced = false
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

    /// Binds to scene storage and restores from it on the first call only — the same shape as
    /// `PromptsModel.attach`, for the same reason.
    func attach(stored: String, persist: @escaping (String) -> Void) {
        self.persist = persist
        guard !attached else { return }
        attached = true
        guard let data = stored.data(using: .utf8),
              let restored = try? JSONDecoder().decode(StoredDetails.self, from: data) else { return }
        state.draft = restored.draft
        currentStep = restored.step.flatMap(DetailStep.init(rawValue:))
        touched = restored.touched
        embraceAnnounced = restored.embraceAnnounced
        if currentStep != nil { stepStartedAt = restored.startedAt ?? now() }
    }

    // MARK: Embrace 2

    /// `screen_viewed`, plus `embrace_bridge_viewed` on a PUSH. A back-pop from height "fires
    /// `screen_viewed` there and nothing else". Also starts the read so height pre-fills.
    func embraceArrived(referrer: ProfileScreen?, pop: Bool) {
        preload()
        guard !embraceAnnounced else { return }
        embraceAnnounced = true
        write()
        analytics?.report(ProfileAnalytics.screenViewed(.embraceDetails, referrer: referrer))
        if !pop {
            analytics?.report(ProfileAnalytics.embraceBridgeViewed(variant: EmbraceVariant.addDetails))
        }
    }

    /// `Add profile details`: re-states `location` best-effort, and advances to height.
    func embraceContinue(onAdvance: () -> Void) {
        // Only from a showing of the bridge — a second tap during the exit must not navigate again.
        guard embraceAnnounced else { return }
        embraceAnnounced = false
        write()
        record(.location)
        onAdvance()
    }

    // MARK: a step

    /// Reads the account once, quietly. Safe to call on every arrival.
    func preload() {
        guard saved == nil, loading == nil else { return }
        loading = Task { [weak self] in
            guard let self else { return }
            let read = await self.store.load()
            self.loading = nil
            // A SAVE MAY HAVE LANDED FIRST, and its answer is newer than this read.
            guard let read, self.saved == nil else { return }
            self.saved = read
            // Every step off screen takes the server's value now, so arriving at it later draws the
            // right thing on its first frame; the one on screen too, unless it has been touched.
            let step = self.currentStep
            var draft = draftFromSaved(self.state.draft, saved: read, onScreen: step)
            if let step, !self.touched {
                draft = draftOnArrival(step, current: draft, saved: read)
                self.state.prefill += 1
            }
            self.setDraft(draft)
        }
    }

    /// A REAL arrival resets the step from the server and reports the view; a redraw does neither.
    func arrived(_ step: DetailStep, referrer: ProfileScreen?) {
        preload()
        guard currentStep != step else { return }
        currentStep = step
        touched = false
        stepStartedAt = now()
        clearToast()
        setDraft(draftOnArrival(step, current: state.draft, saved: saved ?? SavedDetails()))
        state.saving = false
        state.prefill += 1
        analytics?.report(ProfileAnalytics.screenViewed(step.screen, referrer: referrer))
        analytics?.report(ProfileAnalytics.detailStepViewed(step))
    }

    func heightChanged(_ text: String) {
        // Every input, like every press, answers only the step on screen.
        guard currentStep == .height else { return }
        touched = true
        var draft = state.draft
        draft.heightText = filterHeightInput(text)
        setDraft(draft)
        if parseHeight(draft.heightText) != nil { clearRefusal() }
    }

    func optionTapped(_ step: DetailStep, _ value: String) {
        guard !state.saving, currentStep == step else { return }
        touched = true
        let draft = state.draft
        setDraft(draft.withSelection(step, pickSingle(step, current: draft.selection(step), tapped: value)))
        if state.draft.selection(step) != nil { clearRefusal() }
    }

    func languageTapped(_ value: String) {
        guard !state.saving, currentStep == .datingLanguage else { return }
        touched = true
        var draft = state.draft
        draft.languages = toggleMulti(draft.languages, tapped: value)
        setDraft(draft)
    }

    /// `field_display_opted_out` on TICK only. Nothing is saved here.
    func visibilityToggled(_ step: DetailStep) {
        guard !state.saving, currentStep == step else { return }
        touched = true
        var draft = state.draft
        let hiding = !draft.isHidden(step)
        if hiding { draft.hidden.insert(step.hiddenField) } else { draft.hidden.remove(step.hiddenField) }
        setDraft(draft)
        if hiding { analytics?.report(ProfileAnalytics.fieldDisplayOptedOut(step)) }
    }

    /// Continue. Refused, a skip, or an answer — `resolveContinue` decides.
    func continuePressed(_ step: DetailStep, onAdvance: () -> Void) async {
        // NOT THE STEP ON SCREEN: a tap on the outgoing screen during its exit transition.
        guard !state.saving, currentStep == step else { return }
        let draft = state.draft
        switch resolveContinue(step, draft) {
        case .refused(let rule):
            analytics?.report(ProfileAnalytics.detailValidationFailed(step, rule: rule))
            showToast(.refusal)
        case .skip:
            skip(step, onAdvance: onAdvance)
        case .answer:
            await answer(step, draft: draft, onAdvance: onAdvance)
        }
    }

    /// `Skip for now`. Never on gender or orientation.
    func skipPressed(_ step: DetailStep, onAdvance: () -> Void) {
        guard !state.saving, !step.mandatory, currentStep == step else { return }
        skip(step, onAdvance: onAdvance)
    }

    /// The chevron and the swipe. Discards the unsaved draft for this step.
    func backPressed(_ step: DetailStep, onBack: () -> Void) {
        guard !state.saving, currentStep == step else { return }
        leave()
        onBack()
    }

    /// Advance the position from a screen outside this group — media, 09, Stay reachable.
    func record(_ position: Components.Schemas.FlowPosition) {
        let positions = self.positions
        Task { _ = await positions.report(position) }
    }

    // MARK: internals

    /// `detail_skipped` then `profile_step_skipped`, the order the tickets give.
    private func skip(_ step: DetailStep, onAdvance: () -> Void) {
        analytics?.report(ProfileAnalytics.detailSkipped(step))
        analytics?.report(ProfileAnalytics.detailStepSkipped(step))
        record(step.position)
        leave()
        onAdvance()
    }

    /// Saves `draft` — the answer AS IT WAS WHEN CONTINUE WAS ACCEPTED. Height's field still takes
    /// typing during the save, so re-reading the draft after the account read would save whatever
    /// was in the field by then.
    private func answer(_ step: DetailStep, draft: DetailDraft, onAdvance: () -> Void) async {
        state.saving = true
        clearToast()
        // `hidden_fields` IS A REPLACE, so it is built from what the account hides NOW — read fresh
        // for every save, never from a copy. A copy taken on an earlier screen predates the age
        // hidden on the date-of-birth step, and sending it would silently un-hide it. No read, no
        // save: a failed save is recoverable, a guess is not.
        let current = await store.load()
        if let current { saved = current }
        guard let current, let stored = await store.save(body(for: step, draft: draft, current: current)) else {
            state.saving = false
            showToast(.saveFailed)
            return
        }
        saved = stored
        analytics?.report(ProfileAnalytics.detailAnswered(step, valueBucketed: bucket(for: step, draft: draft)))
        analytics?.report(ProfileAnalytics.detailStepCompleted(
            step, timeOnStepSeconds: Int(now() - stepStartedAt)))
        leave()
        state.saving = false
        onAdvance()
    }

    /// The one PATCH. Arguments in the schema's declared order, which Swift requires.
    private func body(for step: DetailStep, draft: DetailDraft,
                      current: SavedDetails) -> Components.Schemas.UpsertProfileDto {
        let hidden = hiddenFieldsToSend(step, saved: current.hiddenFields, hideThis: draft.isHidden(step))
        let position = Components.Schemas.UpsertProfileDto.flowPositionPayload(value1: step.position)
        switch step {
        case .height:
            return .init(flowPosition: position, heightCm: parseHeight(draft.heightText), hiddenFields: hidden)
        case .gender:
            return .init(
                flowPosition: position,
                gender: draft.gender.flatMap(Components.Schemas.Gender.init(rawValue:))
                    .map { Components.Schemas.UpsertProfileDto.genderPayload(value1: $0) },
                hiddenFields: hidden)
        case .orientation:
            return .init(
                flowPosition: position, hiddenFields: hidden,
                orientation: draft.orientation.flatMap(Components.Schemas.Orientation.init(rawValue:))
                    .map { Components.Schemas.UpsertProfileDto.orientationPayload(value1: $0) })
        case .datingLanguage:
            return .init(
                datingLanguages: inListOrder(step, draft.languages)
                    .compactMap(Components.Schemas.DatingLanguage.init(rawValue:)),
                flowPosition: position, hiddenFields: hidden)
        case .education:
            return .init(
                education: draft.education.flatMap(Components.Schemas.Education.init(rawValue:))
                    .map { Components.Schemas.UpsertProfileDto.educationPayload(value1: $0) },
                flowPosition: position, hiddenFields: hidden)
        case .religion:
            return .init(
                flowPosition: position, hiddenFields: hidden,
                religion: draft.religion.flatMap(Components.Schemas.Religion.init(rawValue:))
                    .map { Components.Schemas.UpsertProfileDto.religionPayload(value1: $0) })
        case .politics:
            return .init(
                flowPosition: position, hiddenFields: hidden,
                politics: draft.politics.flatMap(Components.Schemas.Politics.init(rawValue:))
                    .map { Components.Schemas.UpsertProfileDto.politicsPayload(value1: $0) })
        }
    }

    /// `value_bucketed` — height's bucket, the languages in list order, else the §1 value.
    private func bucket(for step: DetailStep, draft: DetailDraft) -> String {
        switch step {
        case .height: return heightBucket(parseHeight(draft.heightText) ?? 0)
        case .datingLanguage: return languagesBucketed(inListOrder(step, draft.languages))
        default: return draft.selection(step) ?? ""
        }
    }

    /// The step is left — by an answer, a skip or a back. Its part of the draft goes back to what
    /// the server holds, which is what "back discards an unsaved selection" means and what lets a
    /// later arrival draw the right row on its first frame.
    private func leave() {
        let step = currentStep
        currentStep = nil
        clearToast()
        if let step {
            state.draft = draftOnArrival(step, current: state.draft, saved: saved ?? SavedDetails())
        }
        write()
    }

    private func showToast(_ kind: DetailToast) {
        toastTask?.cancel()
        state.toast = kind
        state.toastTick += 1
        toastTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(Motion.toast * 1_000_000_000))
            guard !Task.isCancelled else { return }
            self?.state.toast = nil
        }
    }

    private func clearRefusal() {
        if state.toast == .refusal { clearToast() }
    }

    private func clearToast() {
        toastTask?.cancel()
        toastTask = nil
        if state.toast != nil { state.toast = nil }
    }

    private func setDraft(_ draft: DetailDraft) {
        state.draft = draft
        write()
    }

    private func write() {
        let stored = StoredDetails(step: currentStep?.rawValue, draft: state.draft,
                                   touched: touched, embraceAnnounced: embraceAnnounced,
                                   startedAt: currentStep == nil ? nil : stepStartedAt)
        guard let data = try? JSONEncoder().encode(stored),
              let text = String(data: data, encoding: .utf8) else { return }
        persist(text)
    }
}
