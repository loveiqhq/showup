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

    init(repo: any PromptsRepositoring,
         restoredFrom encoded: String = "",
         analytics: (any AnalyticsTracking)? = nil,
         persist: @escaping (String) -> Void = { _ in }) {
        self.repo = repo
        self.analytics = analytics
        self.persist = persist
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

    // MARK: the sheets

    func openTopics() {
        analytics?.report(ProfileAnalytics.promptTopicPickerOpened(slotIndex: state.count))
        var next = state
        next.sheet = .topics
        set(next)
    }

    /// Picking a topic REPLACES the topic sheet with the write sheet — the two never stack — and
    /// resets the example, which is per sheet rather than per session.
    func writeTopic(_ topicId: String) {
        analytics?.report(ProfileAnalytics.promptTopicSelected(topicId: topicId))
        var next = state
        next.sheet = .write(topicId: topicId, editing: state.usedTopicIds.contains(topicId))
        next.nudge = false
        next.exampleHiddenFor = nil
        set(next)
    }

    /// Editing reopens the sheet WITH THE SAVED TEXT IN THE FIELD, which is what makes Save an
    /// overwrite rather than a second prompt.
    func editPrompt(_ topicId: String) {
        var next = state
        next.sheet = .write(topicId: topicId, editing: true)
        next.drafts[topicId] = state.prompts.first { $0.topicId == topicId }?.answer ?? ""
        next.nudge = false
        next.exampleHiddenFor = nil
        set(next)
    }

    func draftChanged(_ text: String) {
        guard case .write(let topicId, _) = state.sheet else { return }
        var next = state
        next.drafts[topicId] = text
        // The empty-submit error clears on the FIRST CHARACTER TYPED, not on blur and not on a
        // re-press.
        next.nudge = false
        set(next)
    }

    func hideExample() {
        guard case .write(let topicId, _) = state.sheet else { return }
        var next = state
        next.exampleHiddenFor = topicId
        set(next)
    }

    /// DISMISSAL KEEPS THE DRAFT. Only Save writes a prompt.
    func dismissSheet() {
        var next = state
        next.sheet = nil
        set(next)
    }

    // MARK: saving

    /// Writes the draft, or nudges when it is empty.
    ///
    /// THE SHEET CLOSES ON A CONFIRMED SAVE, not on the press. A sheet that closed optimistically
    /// and then failed would leave the user looking at a list without their answer in it and no way
    /// back to the text they wrote — so the draft is only cleared once the server has it, and a
    /// failure leaves the sheet exactly as it was.
    func save() {
        guard case .write(let topicId, let editing) = state.sheet else { return }
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
                let slot = existing ?? (next.prompts.count - 1)
                self.analytics?.report(
                    editing
                        ? ProfileAnalytics.promptEdited(
                            promptId: promptId(topicId: topicId, slot: slot))
                        : ProfileAnalytics.promptAnswered(
                            promptId: promptId(topicId: topicId, slot: slot),
                            charCount: prompt.answer.count,
                            atCharLimit: prompt.answer.count >= promptMaxChars))
                next.sheet = nil
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
