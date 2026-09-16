//
//  PromptsModel.swift
//  ShowUp · the prompts step's asynchronous work (SHOWUP-158)
//
//  The Swift twin of `profile/PromptsViewModel.kt`. It did not exist when the screen was built, and
//  the comment where its state used to live said when it would: "it becomes an `@Observable` the
//  day persistence lands." `/me/prompts` exists, so saving a prompt is now a request that has to
//  outlive a redraw and be cancellable — the trigger the iOS CLAUDE.md names.
//
//  THE VIEW DID NOT CHANGE. It already took a value and a set of closures, which is what made
//  swapping the owner underneath it a change to one file.
//
//  WHAT IS SAVED WHERE, AND WHY IT IS BOTH:
//
//    · SAVED PROMPTS COME FROM THE SERVER. `load()` reads them on arrival, so a reinstall or a
//      second device shows what the account holds rather than what this phone remembers.
//    · THE DRAFT DOES NOT. A half-written answer is not a prompt — only Save writes one — so there
//      is nothing to send and nothing to read back. It goes to `@SceneStorage` through the host,
//      which survives a rotation and a scene being backgrounded, and that is what the flow
//      README's rule 4a asks for: a resumed step behaves like a freshly reached one WITH
//      EVERYTHING ALREADY ENTERED STILL PRESENT.
//

import Foundation

@MainActor
@Observable
final class PromptsModel {
    private(set) var state: PromptsState

    private let repo: any PromptsRepositoring
    private let analytics: (any AnalyticsTracking)?
    /// Called with the encoded state after every change, so the host can put it in scene storage.
    ///
    /// A `var` set by `attach` rather than an init argument, because `@State` builds the model
    /// before `@SceneStorage` can be read — a SwiftUI view's property wrappers are not available
    /// to each other's initialisers.
    private var persist: (String) -> Void

    private(set) var loaded = false
    private var attached = false
    private var saveTask: Task<Void, Never>?

    /// The clock, injected.
    ///
    /// `time_on_sheet_s` and `time_on_step_s` are the only two numbers here that cannot be
    /// asserted without one - a test that read the real clock would have to assert "about zero",
    /// which is the same as asserting nothing.
    private let now: () -> Double

    init(repo: any PromptsRepositoring,
         restoredFrom encoded: String = "",
         analytics: (any AnalyticsTracking)? = nil,
         persist: @escaping (String) -> Void = { _ in },
         now: @escaping () -> Double = { Date().timeIntervalSinceReferenceDate }) {
        self.repo = repo
        self.analytics = analytics
        self.persist = persist
        self.now = now
        // Whatever survived; empty on a genuinely fresh arrival, and `load()` then fills the saved
        // prompts in from the server.
        self.state = PromptsState.decode(encoded)
    }

    private func set(_ next: PromptsState) {
        state = next
        persist(next.encoded)
    }

    /// Binds the model to the scene's storage, and restores a draft from it on the first call.
    ///
    /// Idempotent: it runs on every appearance of the screen, and only the FIRST one restores.
    /// Restoring again would overwrite what the user has typed since with what the scene last
    /// wrote, which is the same value one keystroke ago and stale by the time it lands.
    func attach(stored: String, persist: @escaping (String) -> Void) {
        self.persist = persist
        guard !attached else { return }
        attached = true
        guard !stored.isEmpty else { return }
        let restored = PromptsState.decode(stored)
        // The server's prompts win when `load` comes back; the DRAFT is what this is for.
        state.drafts = restored.drafts
        state.sheet = restored.sheet
        state.exampleHiddenFor = restored.exampleHiddenFor
    }

    /// Reads what the account already holds.
    ///
    /// THE DRAFTS ARE KEPT. A user who was mid-sentence when the scene went away comes back to the
    /// server's prompts and their own unfinished one, which are different things and both theirs.
    func load() {
        Task { [weak self] in
            guard let self else { return }
            let prompts = await self.repo.list()
            self.loaded = true
            guard let prompts else { return }
            var next = self.state
            next.prompts = prompts
            self.set(next)
        }
    }

    // MARK: arriving

    /// The step was reached.
    ///
    /// TWO EVENTS, NOT ONE, and they answer different questions: `screen_viewed` says WHICH SCREEN
    /// and `profile_step_viewed` says WHERE IN THE FLOW. `step_index` comes from `RealYouStep`
    /// rather than a literal, which is the registry's rule and the reason the 2-vs-10
    /// disagreement of registry 1.3.0 could be resolved by changing a registry rather than a call
    /// site.
    ///
    /// Idempotent on the timer: a re-entry after a scene teardown keeps the original start, so
    /// `time_on_step_s` measures the visit rather than the resumption.
    func arrived(referrer: ProfileScreen? = nil) {
        analytics?.report(ProfileAnalytics.screenViewed(.prompts, referrer: referrer))
        analytics?.report(ProfileAnalytics.realYouStepViewed(.prompts))
        guard state.stepStartedAt == 0 else { return }
        var next = state
        next.stepStartedAt = now()
        set(next)
    }

    // MARK: the sheets

    func openTopics() {
        analytics?.report(ProfileAnalytics.promptTopicListOpened(usedCount: state.count))
        var next = state
        next.sheet = .topics
        next.sheetOpenedAt = now()
        set(next)
    }

    /// A suggestion card on the screen was tapped.
    ///
    /// - Parameter position: which card, from 0. The registry wants it so that "were these the
    ///   right three" can be read per slot rather than only per topic.
    func writeSuggestion(_ topicId: String, position: Int) {
        chooseTopic(topicId, from: .suggestion, position: position)
    }

    /// A row of the browse sheet was tapped. `position` is the row's index within the sheet.
    func pickTopic(_ topicId: String, position: Int) {
        chooseTopic(topicId, from: .browse, position: position)
    }

    /// Picking a topic REPLACES the topic sheet with the write sheet - the two never stack.
    ///
    /// ONE SELECTION, NOT A DISMISSAL PLUS A SELECTION. The registry says so in as many words, and
    /// it is why this does not route through `dismissSheet`: the browse sheet resolving into the
    /// write sheet is the sheet succeeding, and counting it as an abandonment would make
    /// `prompt_topic_list_opened = selected + dismissed` stop adding up.
    ///
    /// The example resets too - it is per sheet rather than per session.
    private func chooseTopic(_ topicId: String, from: TopicEntryPoint, position: Int) {
        let selections = state.topicSelections + 1
        analytics?.report(ProfileAnalytics.promptTopicSelected(
            topicId: topicId, entryPoint: from, position: position,
            // 1-based, and counted across the whole visit to the step.
            selectionIndex: selections))
        let editing = state.usedTopicIds.contains(topicId)
        analytics?.report(ProfileAnalytics.promptEditorOpened(
            topicId: topicId, entryPoint: from.entryPoint, isEdit: editing,
            promptCount: state.count))

        var next = state
        next.sheet = .write(topicId: topicId, editing: editing, entryPoint: from.entryPoint)
        next.nudge = false
        next.exampleHiddenFor = nil
        next.sheetOpenedAt = now()
        next.topicSelections = selections
        next.charLimitReportedFor = nil
        set(next)
    }

    /// Editing reopens the sheet WITH THE SAVED TEXT IN THE FIELD, which is what makes Save an
    /// overwrite rather than a second prompt.
    ///
    /// NO `prompt_topic_selected` HERE, and that is the registry change of 16 September 2026: an
    /// edit is not a fresh choice of topic, and firing it inflated the topic-demand chart with
    /// re-edits of prompts already written. `promptTopicSelected` could not accept this entry
    /// point even if it were called - `TopicEntryPoint` has no `edit` case.
    ///
    /// It does not raise `topicSelections` either, for the same reason.
    func editPrompt(_ topicId: String) {
        analytics?.report(ProfileAnalytics.promptEditorOpened(
            topicId: topicId, entryPoint: .edit, isEdit: true, promptCount: state.count))
        var next = state
        next.sheet = .write(topicId: topicId, editing: true, entryPoint: .edit)
        next.drafts[topicId] = state.prompts.first { $0.topicId == topicId }?.answer ?? ""
        next.nudge = false
        next.exampleHiddenFor = nil
        next.sheetOpenedAt = now()
        next.charLimitReportedFor = nil
        set(next)
    }

    func draftChanged(_ text: String) {
        guard case .write(let topicId, _, _) = state.sheet else { return }
        // ONCE PER EDITOR SESSION. Every keystroke at the cap is the same fact, and 160 rows
        // saying it is not 160 times the information.
        let report = text.count >= promptMaxChars && state.charLimitReportedFor != topicId
        if report { analytics?.report(ProfileAnalytics.promptCharLimitReached(topicId: topicId)) }
        var next = state
        next.drafts[topicId] = text
        // The empty-submit error clears on the FIRST CHARACTER TYPED, not on blur and not on a
        // re-press.
        next.nudge = false
        if report { next.charLimitReportedFor = topicId }
        set(next)
    }

    func hideExample() {
        guard case .write(let topicId, _, _) = state.sheet else { return }
        analytics?.report(ProfileAnalytics.promptExampleDismissed(topicId: topicId))
        var next = state
        next.exampleHiddenFor = topicId
        set(next)
    }

    /// DISMISSAL KEEPS THE DRAFT. Only Save writes a prompt.
    ///
    /// `method` is required rather than defaulted, because the four 23 values are four different
    /// acts and a default would quietly make three of them look like the fourth.
    func dismissSheet(_ method: SheetDismissMethod) {
        switch state.sheet {
        case .topics:
            analytics?.report(ProfileAnalytics.promptTopicListDismissed(
                method: method, usedCount: state.count,
                timeOnSheetSeconds: secondsSince(state.sheetOpenedAt)))
        case .write(let topicId, _, let entryPoint):
            analytics?.report(ProfileAnalytics.promptEditorDismissed(
                topicId: topicId, entryPoint: entryPoint,
                // The LENGTH, never the draft. The bucket is computed inside the builder.
                draftLength: state.draftFor(topicId)
                    .trimmingCharacters(in: .whitespacesAndNewlines).count,
                method: method))
        case .none:
            return
        }
        var next = state
        next.sheet = nil
        next.sheetOpenedAt = 0
        set(next)
    }

    // MARK: continuing

    /// Continue was pressed.
    ///
    /// CONTINUE IS NEVER DISABLED, so this event is the only record that a user tried to leave
    /// with nothing written - there is no disabled button to infer it from. The refused press
    /// reports `prompts_below_minimum`, which registry 1.4.2 added as the sibling of
    /// `photos_below_minimum`; this screen used to send `nothing_selected`, which belongs to a
    /// chooser where nothing was ticked rather than a screen where nothing was written.
    ///
    /// - Returns: whether the flow may advance. The host routes; this decides and records.
    @discardableResult
    func continuePressed() -> Bool {
        guard state.canContinue else {
            analytics?.report(ProfileAnalytics.realYouValidationFailed(
                fieldId: ProfileField.prompts, rule: ValidationRule.promptsBelowMinimum,
                screen: .prompts, step: .prompts))
            return false
        }
        analytics?.report(ProfileAnalytics.realYouStepCompleted(
            .prompts, timeOnStepSeconds: secondsSince(state.stepStartedAt),
            // 1 to 3, the count at the moment Continue was ACCEPTED. Screen-scoped: no other step
            // sends it.
            promptCount: state.count))
        return true
    }

    /// Whole seconds since an absolute stamp, never negative and never a lie about 0.
    private func secondsSince(_ startedAt: Double) -> Int {
        guard startedAt > 0 else { return 0 }
        let elapsed = now() - startedAt
        return elapsed <= 0 ? 0 : Int(elapsed)
    }

    // MARK: saving

    /// Writes the draft, or nudges when it is empty.
    ///
    /// THE SHEET CLOSES ON A CONFIRMED SAVE, not on the press. A sheet that closed optimistically
    /// and then failed would leave the user looking at a list without their answer in it and no way
    /// back to the text they wrote — so the draft is only cleared once the server has it, and a
    /// failure leaves the sheet exactly as it was.
    func save() {
        guard case .write(let topicId, let editing, let entryPoint) = state.sheet else { return }
        let answer = state.draftFor(topicId)
        if promptAnswerIsEmpty(answer) {
            // SAVE IS NEVER DISABLED. An empty press explains.
            var next = state
            next.nudge = true
            set(next)
            return
        }
        guard !state.saving else { return }

        var pending = state
        pending.saving = true
        set(pending)

        saveTask?.cancel()
        saveTask = Task { [weak self] in
            guard let self else { return }
            let result = await self.repo.save(
                topicId: topicId,
                answer: answer.trimmingCharacters(in: .whitespacesAndNewlines))
            guard !Task.isCancelled else { return }
            var next = self.state
            switch result {
            case .saved(let prompt):
                let existing = next.prompts.firstIndex { $0.topicId == topicId }
                if let existing {
                    next.prompts[existing] = prompt
                } else {
                    // A new card appears at the BOTTOM, so the reading order stays chronological —
                    // the same order the server assigns.
                    next.prompts.append(prompt)
                }
                self.analytics?.report(ProfileAnalytics.promptSaved(
                    topicId: topicId, entryPoint: entryPoint,
                    // An edit does not consume a slot, and `is_edit` has to be honest about that:
                    // `prompt_count` is the count AFTER the action either way.
                    isEdit: editing, answerLength: prompt.answer.count,
                    promptCount: next.prompts.count))
                // ONCE, on the FIRST save, carrying the topic and entry point that got the user
                // there - the registry calls that pairing the most useful row here.
                let crossed = !next.minimumReported && next.prompts.count >= promptsRequired
                if crossed {
                    self.analytics?.report(ProfileAnalytics.promptsMinimumMet(
                        count: next.prompts.count, topicId: topicId, entryPoint: entryPoint))
                    next.minimumReported = true
                }
                next.sheet = nil
                next.sheetOpenedAt = 0
                next.charLimitReportedFor = nil
                // The draft is cleared only once it has become a prompt.
                next.drafts.removeValue(forKey: topicId)
                next.nudge = false
                next.saving = false
                next.failed = false
            case .failed:
                // The sheet stays open with the text in it. See the function header.
                next.saving = false
                next.failed = true
            }
            self.set(next)
        }
    }
}
