//
//  MediaModel.swift
//  ShowUp · every rule on the media step, in one testable place (SHOWUP-161)
//
//  ─────────────────────────────────────────────────────────────────────────────
//  WHY SO MUCH IS HERE
//  ─────────────────────────────────────────────────────────────────────────────
//
//  The two things this screen actually does — point a camera at somebody and open a microphone —
//  cannot run in a unit test. Everything else can, and this file is where "everything else" was
//  deliberately put: the 10 and 15 second caps, the two-second interruption rule, the attempt
//  counter, the retake loop, the difference between stopping and keeping, and all fourteen tracking
//  events.
//
//  The recorders sit behind `MediaCaptureMaking` and the permissions behind `MediaAccessReading`,
//  so `MediaRulesTests` drives this whole type with no device, no camera and no microphone.
//
//  `@Observable` rather than hoisted state, and this is the case the Android and iOS CLAUDE.md both
//  describe: "when a screen genuinely owns asynchronous work — loading, uploading, retrying — that
//  work must outlive a redraw and be cancellable". Recording is all three.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  THE ONE DISTINCTION THE REST OF THE FILE HANGS OFF
//  ─────────────────────────────────────────────────────────────────────────────
//
//  STOPPING PRODUCES A TAKE. ACCEPTING PRODUCES AN ARTEFACT.
//
//  A `MediaTake` is a file and a length. A `MediaArtefact` is something on the profile. Stop, the
//  cap, and an interruption all produce the first; only `Use this clip` produces the second.
//  Collapsing them is how a build ends up firing `video_prompt_recorded` for every abandoned take,
//  which would make the per-prompt completion rate — the number the whole server-side ranking is
//  built on — measure nothing.
//

import Foundation

@MainActor
@Observable
final class MediaModel {

    private(set) var state = MediaState()

    private let repo: any MediaRepositoring
    private let access: any MediaAccessReading
    private let capture: any MediaCaptureMaking
    private let analytics: (any AnalyticsTracking)?

    /// The clock, injected.
    ///
    /// `time_on_sheet_s` and `time_on_step_s` cannot be asserted without one — a test reading the
    /// real clock could only assert "about zero", which is the same as asserting nothing.
    ///
    /// NOT `@Sendable`, and that is deliberate. It is stored in a `@MainActor` type and only ever
    /// called from there, so it never crosses an isolation boundary — and requiring `@Sendable`
    /// would force a test that moves the clock to reach for `@unchecked Sendable`, which this
    /// project bans precisely because it asserts a guarantee instead of arranging one.
    private let now: () -> Int64

    /// How often the recording clock advances, in milliseconds.
    private let tickMs: Int

    /// What one tick waits on.
    ///
    /// INJECTED, because the alternative is a test that takes ten real seconds to prove a
    /// ten-second cap. Kotlin gets this free from `runTest`'s virtual time; Swift has no
    /// equivalent, so the wait itself is the seam. A test passes a closure that returns
    /// immediately and the whole take runs in no time at all.
    private let tickWait: @Sendable (Int) async -> Void

    /// Reads a take off disk, and removes one.
    ///
    /// INJECTED RATHER THAN OVERRIDDEN. The Kotlin twin subclasses its view model in tests; doing
    /// that here would mean dropping `final` from an `@Observable` class to buy one seam. A pair of
    /// closures is cheaper, keeps the class final, and makes the default visible at the call site.
    ///
    /// A ten-second video is a few megabytes, small enough to hold once while it uploads, which is
    /// the same approach the photo grid takes.
    private let readFile: @Sendable (String) -> Data?
    private let removeFile: @Sendable (String) -> Void

    /// When the step was entered, for `time_on_step_s`. Nil until `arrived`.
    ///
    /// OPTIONAL RATHER THAN ZERO. A sentinel would mean "not arrived yet" AND "arrived at epoch",
    /// and those are different facts: the second is a legal clock reading, so any test injecting a
    /// clock that starts at zero could never observe a real duration.
    private var arrivedAtMs: Int64?

    private var session: (any MediaCaptureSession)?
    private var ticker: Task<Void, Never>?
    private var uploadTask: Task<Void, Never>?

    /// Attempts so far, per medium, within this visit.
    ///
    /// `attempt` starts at 1 and counts takes of the SAME prompt so a retake loop is readable
    /// without diffing timestamps. Reset when the prompt changes, because attempt 3 of a prompt the
    /// user has just switched to is not a third attempt at anything.
    private var attempts: [MediaKind: Int] = [:]
    private var attemptPrompt: [MediaKind: String] = [:]

    init(
        repo: any MediaRepositoring,
        access: any MediaAccessReading,
        capture: any MediaCaptureMaking,
        analytics: (any AnalyticsTracking)? = nil,
        now: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) },
        tickMs: Int = 50,
        tickWait: @escaping @Sendable (Int) async -> Void = { ms in
            try? await Task.sleep(nanoseconds: UInt64(ms) * 1_000_000)
        },
        readFile: @escaping @Sendable (String) -> Data? = {
            try? Data(contentsOf: URL(fileURLWithPath: $0))
        },
        removeFile: @escaping @Sendable (String) -> Void = {
            try? FileManager.default.removeItem(atPath: $0)
        }
    ) {
        self.repo = repo
        self.access = access
        self.capture = capture
        self.analytics = analytics
        self.now = now
        self.tickMs = tickMs
        self.tickWait = tickWait
        self.readFile = readFile
        self.removeFile = removeFile
    }

    // MARK: arriving

    /// Reads the account's recordings and the previewed prompts, then reports the entry state.
    ///
    /// `media_screen_viewed` FIRES ON EVERY MOUNT — including the return from an accepted take,
    /// which is the case that makes it more than a duplicate of `screen_viewed`. It names the two
    /// prompts that were on the cards, because the ranking moves and a view that does not record
    /// what it showed cannot be attributed afterwards.
    ///
    /// The step views fire once per medium: one screen carries two §2 steps.
    func arrived() async {
        arrivedAtMs = now()
        refreshAccess()
        analytics?.report(ProfileAnalytics.screenViewed(.media, referrer: .prompts))
        analytics?.report(ProfileAnalytics.mediaStepViewed(.video))
        analytics?.report(ProfileAnalytics.mediaStepViewed(.voice))

        let snapshot = await repo.load()
        if let snapshot {
            adopt(snapshot)
        } else {
            // A read that did not answer must not clear a card the user just filled. Nil is "do not
            // touch what is on screen", which is not the same fact as "nothing stored".
            state.loaded = true
        }
        analytics?.report(ProfileAnalytics.mediaScreenViewed(state))
    }

    /// Re-reads both permission statuses.
    ///
    /// CALLED ON EVERY FOREGROUND. "A user who granted access and comes back to a blocked row will
    /// not try twice" — so nothing is cached and the only thing the screen has to do is ask again.
    func refreshAccess() {
        state.access = access.read()
    }

    /// The platform's own name for a permission row. Never hard-coded.
    func platformLabel(_ capability: MediaCapability) -> String {
        access.platformLabel(capability)
    }

    /// The running camera the viewfinder shows, when there is one.
    ///
    /// Read from the capture factory rather than owned here: the model does not know what a camera
    /// is, and the factory is the one thing that has to hold the same session the recorder uses.
    var cameraSession: CameraSession? { capture.previewSession }

    // MARK: the prompt list

    /// Opens the eleven for one medium.
    ///
    /// NOTHING IS PRESELECTED FROM AN EMPTY CARD — not even the previewed prompt. The card previews
    /// it; the list does not pick it. Opening over a FILLED card preselects the answered prompt, so
    /// keeping it is one tap and changing it is two.
    func openPrompts(_ kind: MediaKind, entryPoint: MediaEntryPoint) {
        let existing = state.artefact(kind)
        state.sheet = MediaSheet(kind: kind, entryPoint: entryPoint,
                                 selectedId: existing?.promptId, openedAtMs: now())
        analytics?.report(ProfileAnalytics.mediaPromptListOpened(
            kind, entryPoint: entryPoint, hasExisting: existing != nil
        ))
    }

    /// Ticks a row.
    ///
    /// ROWS ARE RADIO-SELECT, NOT TAP-TO-LAUNCH: the user can read all eleven and change their mind
    /// before anything opens. Nothing is tracked here — `media_prompt_selected` fires on the commit
    /// CTA, so the count of rows tried before committing is what `selections_before` carries.
    func pickPrompt(_ promptId: String) {
        guard var sheet = state.sheet, sheet.selectedId != promptId else { return }
        sheet.selectedId = promptId
        sheet.selectionsBefore += 1
        state.sheet = sheet
    }

    /// Closes the list without committing.
    ///
    /// DISMISSING KEEPS NOTHING. There is no draft to lose: the selection is discarded and the card
    /// is unchanged. `had_selection` is what splits "read it and left" from "picked one and lost
    /// their nerve", which are two different problems with two different fixes.
    func dismissPrompts(_ method: SheetDismissMethod) {
        guard let sheet = state.sheet else { return }
        state.sheet = nil
        analytics?.report(ProfileAnalytics.mediaPromptListDismissed(
            sheet.kind, method: method,
            hadSelection: sheet.selectedId != nil,
            timeOnSheetSeconds: seconds(since: sheet.openedAtMs)
        ))
    }

    /// The commit CTA. Records the selection and answers with whatever still has to be requested.
    ///
    /// PERMISSIONS ARE REQUESTED HERE — on the commit CTA, not on screen entry and not on
    /// `See the prompts`. By this point the user has chosen what they are about to say, which is
    /// the moment the ask is least likely to read as an ambush.
    ///
    /// The selection event fires either way: the user did choose a prompt, and whether the OS then
    /// refused them is a different question the permission row answers.
    @discardableResult
    func commitPrompt() async -> [MediaCapability] {
        guard let sheet = state.sheet, let prompt = MediaPrompts.byId(sheet.selectedId) else {
            return []
        }
        let kind = sheet.kind

        analytics?.report(ProfileAnalytics.mediaPromptSelected(
            kind, prompt: prompt,
            selectionsBefore: sheet.selectionsBefore,
            // THE FIELD THAT KEEPS THE RANKING HONEST. The previewed prompt is far more visible
            // than the other ten, so the job counts only takes where this is false.
            wasPreviewed: prompt.id == state.preview(kind).id
        ))

        let missing = state.access.missing(for: kind)
        guard missing.isEmpty else { return missing }
        await beginTake(kind, promptId: prompt.id)
        return []
    }

    /// Asks the OS for what is missing, then starts the take if it was granted.
    ///
    /// Granted for everything the medium needs → straight into the viewfinder, because that is what
    /// the user asked for when they pressed the commit CTA. Refused → the sheet stays open with the
    /// blocked row, which is where the question was asked.
    func requestAndBegin(_ kind: MediaKind, promptId: String,
                         capabilities: [MediaCapability]) async {
        for capability in capabilities {
            _ = await access.request(capability)
        }
        refreshAccess()
        if state.access.isReady(for: kind) {
            await beginTake(kind, promptId: promptId)
        }
    }

    // MARK: recording

    /// Opens the viewfinder on a prompt and starts the take.
    ///
    /// The attempt counter resets when the PROMPT changes: attempt 3 of a prompt the user has just
    /// switched to is not a third attempt at anything.
    private func beginTake(_ kind: MediaKind, promptId: String) async {
        if attemptPrompt[kind] != promptId {
            attemptPrompt[kind] = promptId
            attempts[kind] = 0
        }
        let attempt = (attempts[kind] ?? 0) + 1
        attempts[kind] = attempt

        let isRetake = attempt > 1 || state.artefact(kind) != nil
        state.sheet = nil
        state.take = MediaTake(kind: kind, promptId: promptId, attempt: attempt)

        analytics?.report(ProfileAnalytics.screenViewed(.mediaRecord, referrer: .media))
        if let prompt = MediaPrompts.byId(promptId) {
            analytics?.report(ProfileAnalytics.mediaRecordingStarted(
                kind, prompt: prompt, attempt: attempt, isRetake: isRetake
            ))
        }

        let created = capture.makeSession(kind)
        session = created
        let started = await created.start { [weak self] reason in
            self?.captureEnded(reason)
        }
        guard started else {
            // The recorder would not start at all. Nothing was captured, so there is nothing to
            // review -- return to the card rather than showing an empty review screen.
            session = nil
            state.take = nil
            return
        }
        runClock(kind)
    }

    /// The clock that drives the progress bar AND the cap.
    ///
    /// REACHING THE CAP STOPS THE TAKE AND SHOWS THE REVIEW SCREEN, exactly as Stop does. Never an
    /// alert, never a truncated save: the bar fills to the limit and the take simply ends, which is
    /// what "the cap is hard" means from the user's side.
    private func runClock(_ kind: MediaKind) {
        ticker?.cancel()
        let step = tickMs
        ticker = Task { [weak self] in
            guard let self else { return }
            let maxMs = MediaLimits.maxMs(kind)
            var elapsed = 0
            while elapsed < maxMs {
                await self.tickWait(step)
                if Task.isCancelled { return }
                elapsed = min(maxMs, elapsed + step)
                guard var take = self.state.take, take.phase == .recording else { return }
                take.elapsedMs = elapsed
                self.state.take = take
            }
            await self.stopTake(reason: .maxLength)
        }
    }

    /// The shutter.
    func stopPressed() async { await stopTake(reason: .userStop) }

    private func stopTake(reason: MediaStopReason) async {
        guard let take = state.take, take.phase == .recording, let current = session else { return }
        ticker?.cancel()
        guard let captured = await current.finish(elapsedMs: take.elapsedMs) else {
            // Nothing usable was written -- a take too short for the encoder, or a failure on
            // close. There is no take to review, so the card is where the user goes.
            session = nil
            state.take = nil
            return
        }
        showReview(take, captured: captured, reason: reason)
    }

    /// An interruption ended the take.
    ///
    /// A call, an alarm or another app taking the microphone. AT OR ABOVE
    /// `MediaLimits.interruptionKeepMs` the review screen is shown; below it the take is discarded
    /// and we return to the card. NEVER A SILENT RESUME — the user was interrupted and did not
    /// choose to stop, so pretending the recording continued would produce a take with a hole in it.
    ///
    /// The stop reason reported is `user_stop`, and that is the closed §8 set forcing a choice
    /// between two values neither of which is "interrupted". `max_length` would be a lie that
    /// corrupts the one measurement the field exists for — whether the caps are too short — so the
    /// other value carries it. Raised as a registry gap rather than resolved silently.
    private func captureEnded(_ reason: CaptureFailure) {
        guard reason == .interrupted else { return }
        guard let take = state.take, take.phase == .recording, let current = session else { return }
        ticker?.cancel()
        Task { [weak self] in
            guard let self else { return }
            if take.elapsedMs < MediaLimits.interruptionKeepMs {
                await current.discard()
                self.session = nil
                self.state.take = nil
                return
            }
            guard let captured = await current.finish(elapsedMs: take.elapsedMs) else {
                self.session = nil
                self.state.take = nil
                return
            }
            self.showReview(take, captured: captured, reason: .userStop)
        }
    }

    private func showReview(_ take: MediaTake, captured: CaptureTake, reason: MediaStopReason) {
        var updated = take
        updated.phase = .review
        updated.elapsedMs = captured.durationMs
        updated.path = captured.path
        updated.stopReason = reason
        state.take = updated

        analytics?.report(ProfileAnalytics.screenViewed(.mediaReview, referrer: .mediaRecord))
        if let prompt = MediaPrompts.byId(take.promptId) {
            analytics?.report(ProfileAnalytics.mediaReviewShown(
                take.kind, prompt: prompt, durationMs: captured.durationMs,
                attempt: take.attempt, stopReason: reason
            ))
        }
    }

    // MARK: review

    /// Playback on review is ON DEMAND and repeatable, and the CTA row does not move between plays.
    ///
    /// `play_count` is a running count rather than a flag because "watched it once and kept it" and
    /// "watched it four times and kept it" are different levels of confidence in the same outcome.
    func playPressed() {
        guard var take = state.take, take.phase == .review else { return }
        take.playCount += 1
        take.isPlaying = true
        state.take = take
        analytics?.report(ProfileAnalytics.mediaPreviewPlayed(
            take.kind, attempt: take.attempt, playCount: take.playCount
        ))
    }

    func playbackFinished() {
        guard var take = state.take else { return }
        take.isPlaying = false
        state.take = take
    }

    /// `Retake` on the review screen.
    ///
    /// RETURNS TO THE VIEWFINDER ON THE SAME PROMPT — it does not reopen the prompt list. The user
    /// has already decided what to say; making them choose again would be asking a settled question.
    func retakeFromReview() async {
        guard let take = state.take, let prompt = MediaPrompts.byId(take.promptId) else { return }
        analytics?.report(ProfileAnalytics.mediaRetaken(
            take.kind, from: .review, prompt: prompt, attempt: take.attempt,
            priorDurationMs: take.elapsedMs, state: state
        ))
        await session?.discard()
        session = nil
        await beginTake(take.kind, promptId: take.promptId)
    }

    /// `Use this clip` / `Use this recording` — the take becomes an artefact.
    ///
    /// THE CARD FILLS OPTIMISTICALLY and the upload runs in the background. A failure surfaces on
    /// the card, never as a modal, and the user can leave the screen; an upload still running when
    /// Continue is pressed keeps running.
    ///
    /// `*_prompt_recorded` fires HERE and never on Stop — see the file header.
    func acceptTake() {
        guard let take = state.take, let prompt = MediaPrompts.byId(take.promptId),
              let path = take.path else { return }

        analytics?.report(ProfileAnalytics.mediaPromptRecorded(
            take.kind, prompt: prompt, durationMs: take.elapsedMs,
            // attempt counts from 1, so the number of RETAKES is one fewer.
            retakes: take.attempt - 1, playsBeforeAccept: take.playCount
        ))

        let artefact = MediaArtefact(kind: take.kind, promptId: take.promptId,
                                     durationMs: take.elapsedMs, localPath: path, status: .queued)
        session = nil
        state.setArtefact(artefact, for: take.kind)
        state.take = nil
        // The screen is remounted by the return from the viewfinder, so the entry state is reported
        // again -- which is what "on every mount, including the return from an accepted take" asks.
        analytics?.report(ProfileAnalytics.mediaScreenViewed(state))
        upload(artefact, kind: take.kind)
    }

    /// `Cancel` in the viewfinder. Returns to the card with nothing saved.
    func cancelTake() async {
        ticker?.cancel()
        let current = session
        session = nil
        state.take = nil
        await current?.discard()
    }

    private func upload(_ artefact: MediaArtefact, kind: MediaKind) {
        uploadTask?.cancel()
        uploadTask = Task { [weak self] in
            guard let self else { return }
            if var live = self.state.artefact(kind) {
                live.status = .inFlight
                self.state.setArtefact(live, for: kind)
            }
            guard let path = artefact.localPath, let bytes = self.readFile(path) else {
                if var live = self.state.artefact(kind) {
                    live.status = .failed
                    self.state.setArtefact(live, for: kind)
                }
                return
            }
            let result = await self.repo.upload(
                kind: kind, promptId: artefact.promptId, durationMs: artefact.durationMs,
                bytes: bytes, mimeType: mimeType(for: kind), fileName: fileName(for: kind),
                onProgress: { _ in }
            )
            // Only touch the slot if it still holds the artefact this upload was for. A retake that
            // finished first must not be overwritten by the older upload's answer.
            guard var live = self.state.artefact(kind), live.localPath == artefact.localPath else {
                return
            }
            switch result {
            case .stored(let stored):
                live.remoteId = stored.id
                live.url = stored.url
                live.status = .confirmed
            case .failed:
                live.status = .failed
            }
            self.state.setArtefact(live, for: kind)
        }
    }

    /// Re-sends a take whose upload failed. The bytes are still in the cache.
    func retryUpload(_ kind: MediaKind) {
        guard let artefact = state.artefact(kind), artefact.status == .failed else { return }
        upload(artefact, kind: kind)
    }

    // MARK: the cards

    /// `Retake` on a FILLED card — reopens the prompt list with the answered prompt selected.
    ///
    /// The existing artefact stays on the profile until a new take is accepted, so a user who opens
    /// the list and changes their mind has lost nothing.
    func retakeFromCard(_ kind: MediaKind) {
        guard let artefact = state.artefact(kind) else { return }
        if let prompt = MediaPrompts.byId(artefact.promptId) {
            analytics?.report(ProfileAnalytics.mediaRetaken(
                kind, from: .mediaCard, prompt: prompt, attempt: (attempts[kind] ?? 0) + 1,
                priorDurationMs: artefact.durationMs, state: state
            ))
        }
        openPrompts(kind, entryPoint: .retake)
    }

    /// `Delete` — immediate, with NO confirmation dialog.
    ///
    /// The card returns to empty with its previewed prompt. DELETING IS NOT RETAKING: there is no
    /// replacement take coming, which is why the two events must never be collapsed.
    ///
    /// The event carries the state BEFORE the removal, so `had_video` and `had_voice` describe what
    /// the user was looking at when they decided.
    func delete(_ kind: MediaKind) {
        guard let artefact = state.artefact(kind) else { return }
        analytics?.report(ProfileAnalytics.mediaDeleted(kind, artefact: artefact, state: state))
        state.setArtefact(nil, for: kind)
        // The attempt counter belongs to the prompt that is no longer answered.
        attempts[kind] = nil
        attemptPrompt[kind] = nil
        if let path = artefact.localPath { removeFile(path) }
        if let remoteId = artefact.remoteId {
            Task { [repo] in _ = await repo.remove(id: remoteId) }
        }
    }

    // MARK: leaving

    /// Continue. Never disabled, never gated, in any state including empty.
    ///
    /// Per medium: a slot with a recording completed its step, a slot without skipped it. "An empty
    /// Continue is a skip that the user did not call one" — so pressing Continue with nothing
    /// records exactly what `Skip for now` records.
    ///
    /// NO VALIDATION EVENT EVER FIRES HERE. The step is optional, so there is no rule to fail.
    func continuePressed() {
        let elapsed = seconds(since: arrivedAtMs)
        for kind in MediaKind.allCases {
            if state.artefact(kind) != nil {
                analytics?.report(ProfileAnalytics.mediaStepCompleted(kind, timeOnStepSeconds: elapsed))
            } else {
                analytics?.report(ProfileAnalytics.mediaStepSkipped(kind, state: state))
            }
        }
    }

    /// `Skip for now`. Same navigation as Continue; they differ only in what they record.
    ///
    /// An explicit skip is a skip for BOTH media, whatever is on the cards — the user said so.
    /// Anything already recorded stays on the profile; the event describes the act, not the state,
    /// and `has_video` / `has_voice` carry the state alongside it.
    func skipPressed() {
        for kind in MediaKind.allCases {
            analytics?.report(ProfileAnalytics.mediaStepSkipped(kind, state: state))
        }
    }

    // MARK: helpers

    private func seconds(since startedAtMs: Int64?) -> Int {
        guard let startedAtMs else { return 0 }
        return max(0, Int((now() - startedAtMs) / 1000))
    }

    /// Folds a server read into the screen's state without disturbing anything in flight.
    private func adopt(_ snapshot: MediaSnapshot) {
        func stored(_ kind: MediaKind) -> MediaArtefact? {
            guard let item = snapshot.items.first(where: { $0.kind == kind }) else { return nil }
            return MediaArtefact(kind: kind, promptId: item.promptId, durationMs: item.durationMs,
                                 localPath: nil, remoteId: item.id, url: item.url,
                                 status: .confirmed)
        }
        // ANYTHING THIS SESSION IS STILL UPLOADING SURVIVES. A read that lands while a take is in
        // flight would otherwise replace the card the user is watching with the server's older
        // answer -- the same bug the photo grid's `load` had to be fixed for.
        func keep(_ kind: MediaKind) -> MediaArtefact? {
            if let live = state.artefact(kind), live.status != .confirmed { return live }
            return stored(kind)
        }
        state.video = keep(.video)
        state.voice = keep(.voice)
        state.previewVideoId = snapshot.previewVideoId
        state.previewVoiceId = snapshot.previewVoiceId
        state.previewSource = snapshot.previewSource
        state.loaded = true
    }
}
