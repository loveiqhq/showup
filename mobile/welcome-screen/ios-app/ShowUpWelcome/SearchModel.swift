//
//  SearchModel.swift
//  ShowUp · Date search 01, the Search tab for a user with a committed Check-In (SHOWUP-184)
//
//  The Swift twin of `search/SearchViewModel.kt`; read that file's header. In short:
//
//    · ONE OWNER FOR THE TAB: the queue, the profile on screen, the like flow's two sheets, the
//      actions coach, the photo viewer, playback, and every event of family I. The screen takes
//      `SearchUiState` and returns taps; nothing on it decides anything.
//    · A VISIT is one `screen_viewed` of the Search tab (`visit`). The coach, `position` and the
//      queue all reset with it. The way back from Filters is not a visit (`arrived`).
//    · SENDS RUN IN THE BACKGROUND. A like or a pass moves to the next profile at once and the request
//      follows, retried silently: both are idempotent on the server.
//    · AN OPEN LIKE FLOW SURVIVES THE PROCESS -- which sheet, for whom, the message as typed -- in
//      `@SceneStorage` through the host (`attach`), as the profile drafts do. It comes back once, when
//      the queue shows the same person first; anyone else, and it is dropped.
//

import Foundation

/// Which of the like flow's two sheets is open. String-backed, so an open flow can be stored.
enum LikeSheet: String, Equatable, Sendable, Codable { case confirm, message }

/// An open like flow, as scene storage keeps it.
private struct UnfinishedLike: Codable, Equatable {
    let userId: String
    let sheet: LikeSheet
    let draft: String
}

/// What is playing on the profile: which card, how far, for its readout.
struct SearchPlayback: Equatable, Sendable {
    let kind: MediaKind
    var positionMs: Int
    let durationMs: Int

    var progress: Double {
        guard durationMs > 0 else { return 0 }
        return min(1, max(0, Double(positionMs) / Double(durationMs)))
    }
}

struct SearchUiState: Equatable, Sendable {
    /// The queue is being read: the first load of a visit, before any profile is known.
    var loading = true
    /// The queue could not be read, and why -- no response, or one that was not a success.
    var failure: CallFailure?
    /// The profiles still to decide on, the one on screen first.
    var queue: [SearchProfile] = []
    /// 1-based, in this visit's queue: the profile on screen.
    var position = 1
    var sheet: LikeSheet?
    /// The optional message, as typed (already cut to 100).
    var draft = ""
    /// The actions coach is over the profile; the three buttons are inert.
    var coach = false
    /// The open-likes count -- the likes chip and the Likes tab badge read this one number.
    var likesOpen = 0
    var premium = false
    /// The fullscreen photo viewer, open at this photo; nil when closed.
    var viewerAt: Int?
    var playback: SearchPlayback?
    var viewer = SearchViewer()

    var current: SearchProfile? { queue.first }

    /// Loaded, nothing failed, and nobody to show: the no-results screen.
    var empty: Bool { !loading && failure == nil && queue.isEmpty }
}

@MainActor
@Observable
final class SearchModel {
    private(set) var state = SearchUiState()

    private let store: any SearchStoring
    private let analytics: (any AnalyticsTracking)?
    private let players: (any MediaPlayerMaking)?
    /// Milliseconds, for `dwell_ms` and `visible_ms`.
    private let millis: () -> Int
    /// Between silent retries of a like or a pass, in seconds. Short in tests.
    private let retryDelays: [TimeInterval]

    /// The stored opt-out: `Don't show again` once, on any device.
    private var coachHidden = false
    private var coachShownThisVisit = false
    private var coachShownAt = 0

    /// Who has been decided on this session, so a refill never brings them back mid-request.
    private var decided: Set<String> = []

    /// The visit's reported `filters_active`, kept for a reload after Filters.
    private var filtersActive: [String] = []

    /// A visit has been made, so a return from Filters is a return, not a first arrival.
    private var visited = false

    /// Filters saved new settings: the return reads the queue again under them.
    private var reloadOnReturn = false

    // Per profile, for `profile_card_viewed`.
    private var shownAt = 0
    private var photosSwiped = 0
    private var sectionsSeen: Set<Int> = []

    private var loadTask: Task<Void, Never>?
    private var session: (any MediaPlaying)?
    private var ticker: Task<Void, Never>?
    /// Every stop moves it on, so a play still opening when it moves knows it was superseded.
    private var playGeneration = 0

    /// Set by `attach`, because `@State` builds the model before `@SceneStorage` can be read.
    private var persist: (String) -> Void = { _ in }
    private var attached = false
    /// A like flow the process lost, waiting for the first queue that shows its person.
    private var unfinished: UnfinishedLike?

    init(
        store: any SearchStoring,
        analytics: (any AnalyticsTracking)? = nil,
        players: (any MediaPlayerMaking)? = nil,
        millis: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) },
        retryDelays: [TimeInterval] = [1, 3, 9, 27]
    ) {
        self.store = store
        self.analytics = analytics
        self.players = players
        self.millis = millis
        self.retryDelays = retryDelays
    }

    /// Binds to scene storage and reads what it holds on the first call only -- the shape of
    /// `DetailsModel.attach`, so the host can call it on every arrival.
    func attach(stored: String, persist: @escaping (String) -> Void) {
        self.persist = persist
        guard !attached else { return }
        attached = true
        guard let data = stored.data(using: .utf8) else { return }
        unfinished = try? JSONDecoder().decode(UnfinishedLike.self, from: data)
    }

    // MARK: - the visit

    /// Search is on screen. A tab, a launch or `Find my date` is a VISIT (`visit`). The way back from
    /// Filters is not: no second coach, nobody already decided coming back, and the queue read again
    /// only if Filters saved new settings. It IS a `screen_viewed` -- without one, everything after it
    /// would be counted against Filters -- and so `position` starts again at the profile shown, as the
    /// ticket has it reset "on each screen_viewed".
    func arrived(zone: String, filtersActive: [String], referrer: ProfileScreen?) {
        guard visited, referrer == .checkinFilters else {
            visit(zone: zone, filtersActive: filtersActive, referrer: referrer)
            return
        }
        report(ProfileAnalytics.screenViewed(.searchProfile, referrer: referrer))
        guard reloadOnReturn else {
            state.position = 1
            resume()
            return
        }
        reloadOnReturn = false
        self.filtersActive = filtersActive
        stopPlayback()
        state.loading = true
        state.failure = nil
        state.queue = []
        state.position = 1
        state.sheet = nil
        state.viewerAt = nil
        load(zone: zone)
    }

    /// Filters saved new settings; the return to Search reads the queue under them.
    func filtersSaved() {
        reloadOnReturn = true
    }

    /// A visit to the Search tab: `screen_viewed` once, the queue read, the coach armed.
    func visit(zone: String, filtersActive: [String], referrer: ProfileScreen?) {
        report(ProfileAnalytics.screenViewed(.searchProfile, referrer: referrer))
        visited = true
        reloadOnReturn = false
        self.filtersActive = filtersActive
        coachShownThisVisit = false
        stopPlayback()
        state.loading = true
        state.failure = nil
        state.queue = []
        state.position = 1
        state.sheet = nil
        state.coach = false
        state.viewerAt = nil
        load(zone: zone)
    }

    /// `Retry` on the failed state: the same visit, read again.
    func retry(zone: String) {
        state.loading = true
        state.failure = nil
        load(zone: zone)
    }

    private func load(zone: String) {
        loadTask?.cancel()
        let store = self.store
        loadTask = Task { [weak self] in
            async let sessionCall = store.session()
            async let countCall = store.likesOpenCount()
            let queue = await store.queue(zone: zone)
            let session = await sessionCall
            let count = await countCall
            guard let self, !Task.isCancelled else { return }
            if case .success(let s) = session {
                // ONCE HIDDEN HERE, HIDDEN: a session read that left before `Don't show again` landed
                // on the server still says false, and must not bring the coach back on this device.
                self.coachHidden = self.coachHidden || s.coachHidden
                self.state.premium = s.premium
            }
            if case .success(let n) = count { self.state.likesOpen = n }
            switch queue {
            case .failure(let why):
                self.state.loading = false
                self.state.failure = why
            case .success(let q):
                let profiles = q.profiles.filter { !self.decided.contains($0.userId) }
                self.report(SearchAnalytics.searchViewed(resultsCount: q.resultsCount, filtersActive: self.filtersActive))
                let reopened = self.unfinished.flatMap { $0.userId == profiles.first?.userId ? $0 : nil }
                self.unfinished = nil
                self.state.loading = false
                self.state.failure = nil
                self.state.queue = profiles
                self.state.viewer = q.viewer
                self.state.sheet = reopened?.sheet
                if let reopened { self.state.draft = reopened.draft }
                // The user had passed the coach to tap Like; it does not come back over their sheet.
                if reopened != nil { self.coachShownThisVisit = true }
                self.keepLikeFlow()
                self.present()
            }
        }
    }

    /// A profile is on screen: its dwell starts, unless the coach is over it (counted from the dismissal).
    private func present() {
        photosSwiped = 0
        sectionsSeen = []
        if shouldShowCoach(hiddenForever: coachHidden, shownThisVisit: coachShownThisVisit,
                           hasProfile: state.current != nil, likedYou: false) {
            coachShownThisVisit = true
            coachShownAt = millis()
            state.coach = true
            report(SearchAnalytics.coachShown())
        } else {
            shownAt = millis()
        }
    }

    /// The profile left the screen without a decision -- a tab change or backgrounding. Its view is
    /// reported now, while the dwell is known; `resume` starts a new one if it comes back.
    func leave() {
        stopPlayback()
        if !state.coach { reportCardViewed() }
    }

    func resume() {
        if state.current != nil && !state.coach {
            shownAt = millis()
            photosSwiped = 0
            sectionsSeen = []
        }
    }

    // MARK: - the coach

    /// `Got it`, a scrim tap, or `Don't show again` -- the last the only one stored. The same profile
    /// is revealed either way, and its dwell starts now.
    func dismissCoach(_ method: SearchAnalytics.CoachDismiss, dontShowAgain: Bool) {
        guard state.coach else { return }
        report(SearchAnalytics.coachDismissed(method, dontShowAgain: dontShowAgain, visibleMs: millis() - coachShownAt))
        state.coach = false
        shownAt = millis()
        if dontShowAgain {
            coachHidden = true
            let store = self.store
            sendInBackground { await store.hideCoach() }
        }
    }

    // MARK: - the like flow

    /// `Like`: Be sure opens. Inert under the coach -- no event either.
    func like() {
        guard let profile = state.current, !state.coach, state.sheet == nil else { return }
        stopPlayback()
        report(SearchAnalytics.likeTapped(position: state.position))
        state.sheet = .confirm
        keepLikeFlow()
        report(SearchAnalytics.likeConfirmShown(targetAttendedCount: profile.attendedCount))
    }

    /// `Cancel` or a scrim tap on Be sure: nothing is sent, the profile stays.
    func cancelConfirm() {
        guard let profile = state.current, state.sheet == .confirm else { return }
        state.sheet = nil
        keepLikeFlow()
        report(SearchAnalytics.likeConfirmAbandoned(.confirm, targetAttendedCount: profile.attendedCount))
    }

    /// `Continue`: the message sheet, its draft pre-filled for this person and day.
    func continueToMessage() {
        guard let profile = state.current, state.sheet == .confirm else { return }
        state.sheet = .message
        state.draft = messageDraft(profile.name, profile.meetDay)
        keepLikeFlow()
    }

    /// A keystroke or a paste: never more than 100, a paste cut at 100. Nothing is tracked per key.
    func editDraft(_ text: String) {
        state.draft = limitMessage(text)
        keepLikeFlow()
    }

    /// A scrim tap or a swipe down on the message sheet: nothing is sent.
    func dismissMessage() {
        guard let profile = state.current, state.sheet == .message else { return }
        state.sheet = nil
        keepLikeFlow()
        report(SearchAnalytics.likeConfirmAbandoned(.message, targetAttendedCount: profile.attendedCount))
    }

    /// `Skip message`: the like, with no message. Not an abandon.
    func skipMessage() { sendLike(message: nil) }

    /// `Send message`: the trimmed text; blank sends as no message, and attaches nothing.
    func sendMessage() {
        let message = messageToSend(state.draft)
        if let message { report(SearchAnalytics.likeMessageAttached(charCount: graphemes(message))) }
        sendLike(message: message)
    }

    private func sendLike(message: String?) {
        guard let profile = state.current, state.sheet == .message else { return }
        let position = state.position
        state.sheet = nil
        keepLikeFlow()
        decided.insert(profile.userId)
        let store = self.store
        let target = profile.userId
        sendInBackground { await store.like(targetUserId: target, message: message, position: position) }
        next()
    }

    // MARK: - passes

    /// `Not now` / `Not interested`: one tap, no confirmation, no undo. Inert under the coach.
    func pass(_ type: SearchAnalytics.PassType) {
        guard let profile = state.current, !state.coach, state.sheet == nil else { return }
        report(SearchAnalytics.passSent(position: state.position, passType: type))
        decided.insert(profile.userId)
        let store = self.store
        let target = profile.userId
        sendInBackground { await store.pass(targetUserId: target, type: type) }
        next()
    }

    /// The profile that was on screen leaves; the next slides in.
    private func next() {
        stopPlayback()
        reportCardViewed()
        state.queue = Array(state.queue.dropFirst())
        state.position += 1
        state.viewerAt = nil
        present()
    }

    private func reportCardViewed() {
        guard let profile = state.current else { return }
        report(SearchAnalytics.profileCardViewed(
            position: state.position,
            dwellMs: max(0, millis() - shownAt),
            photosSwiped: photosSwiped,
            sectionsScrolled: sectionsSeen.count,
            sharedVibeCount: sharedVibes(profile.vibes, mine: state.viewer.vibes).count,
            sharedInterestCount: sharedInterests(profile.interests, mine: state.viewer.interests).count))
    }

    /// A section of the stack came into view -- `sections_scrolled` counts the distinct ones.
    func sectionSeen(_ index: Int) {
        sectionsSeen.insert(index)
    }

    // MARK: - the photo viewer

    /// A photo tapped: the viewer opens at it. `index` is 0-based; the event's is 1-based.
    func openPhoto(_ index: Int) {
        guard !state.coach else { return }
        stopPlayback()
        state.viewerAt = index
        report(SearchAnalytics.photoOpened(photoIndex: index + 1))
    }

    /// Swiped to another photo in the viewer.
    func viewerPage(_ index: Int) {
        guard state.viewerAt != index else { return }
        photosSwiped += 1
        state.viewerAt = index
    }

    func closeViewer() {
        state.viewerAt = nil
    }

    // MARK: - playback

    /// Play or pause `kind`. On tap only, never automatically; starting one stops the other ("only one
    /// item plays at a time").
    func togglePlay(_ kind: MediaKind) {
        guard let profile = state.current, !state.coach else { return }
        let playing = state.playback
        stopPlayback()
        if playing?.kind == kind { return }
        guard let media = kind == .voice ? profile.voice : profile.video, let url = media.url, players != nil else { return }
        let generation = playGeneration
        Task { [weak self] in
            guard let self, let players = self.players else { return }
            let next = players.makePlayer(kind: kind)
            let started = await next.start(path: url.absoluteString)
            // SUPERSEDED while the clip was opening -- a pass, a like, a sheet, the app leaving. Without
            // this, a voice note tapped just before `Not now` played over the next profile.
            guard generation == self.playGeneration else {
                await next.stop()
                return
            }
            guard started else { return }
            self.session = next
            self.state.playback = SearchPlayback(kind: kind, positionMs: 0, durationMs: media.durationMs)
            self.runPlayClock(next, durationMs: media.durationMs)
        }
    }

    /// Polls the playhead and notices the end. BOUNDED, as `MediaModel`'s clock is: by the clip's own
    /// length and a grace, so a player that never reports an ending cannot keep a loop alive.
    private func runPlayClock(_ playing: any MediaPlaying, durationMs: Int) {
        ticker = Task { [weak self] in
            var waited = 0
            let limit = durationMs + Self.playClockGraceMs
            while waited < limit {
                try? await Task.sleep(nanoseconds: UInt64(Self.tickMs) * 1_000_000)
                waited += Self.tickMs
                guard let self, !Task.isCancelled, self.session === playing else { return }
                if playing.hasFinished() {
                    self.state.playback?.positionMs = durationMs
                    self.stopPlayback()
                    return
                }
                self.state.playback?.positionMs = playing.positionMs()
            }
            self?.stopPlayback()
        }
    }

    /// A playing card scrolled out of view: it pauses.
    func scrolledOut(_ kind: MediaKind) {
        if state.playback?.kind == kind { stopPlayback() }
    }

    /// Stops whatever plays -- or is still opening -- and reports a play, once, with how much of it was
    /// heard: "fire when playback stops (end, pause, scrolled out, profile changed)".
    private func stopPlayback() {
        playGeneration += 1
        ticker?.cancel()
        ticker = nil
        let ending = session
        session = nil
        if let ending { Task { await ending.stop() } }
        guard let playing = state.playback else { return }
        state.playback = nil
        let pct = (playing.progress * 1000).rounded() / 10
        report(SearchAnalytics.promptPlayed(playing.kind == .voice ? .voice : .video, position: state.position, playedPct: pct))
    }

    // MARK: - the likes chip

    /// The open-likes count again, e.g. when the app comes back to the front.
    func refreshLikesOpen() {
        let store = self.store
        Task { [weak self] in
            let count = await store.likesOpenCount()
            if case .success(let n) = count { self?.state.likesOpen = n }
        }
    }

    // MARK: - the dev menu

    func setPremium(_ premium: Bool) {
        let store = self.store
        Task { [weak self] in
            if case .success = await store.setPremium(premium) { self?.state.premium = premium }
        }
    }

    // MARK: - plumbing

    /// Runs `call` until it lands, waiting longer each time; gives up after the last delay. Silent by
    /// design ("If it fails, retry silently"): the user has already moved on to the next profile.
    private func sendInBackground(_ call: @escaping @Sendable () async -> Result<Void, CallFailure>) {
        let delays = retryDelays
        Task {
            if case .success = await call() { return }
            for wait in delays {
                try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
                if case .success = await call() { return }
            }
        }
    }

    /// The like flow as it stands, into scene storage: an open sheet kept, a closed one forgotten.
    private func keepLikeFlow() {
        guard let profile = state.current, let sheet = state.sheet else {
            persist("")
            return
        }
        let kept = UnfinishedLike(userId: profile.userId, sheet: sheet, draft: sheet == .message ? state.draft : "")
        guard let data = try? JSONEncoder().encode(kept), let text = String(data: data, encoding: .utf8) else { return }
        persist(text)
    }

    private func report(_ event: (String, [String: any Sendable])) {
        analytics?.report(event)
    }

    /// How often a playing card's readout moves.
    private static let tickMs = 100
    /// How long the clock keeps polling past a clip's stated length: a backstop, not a timing rule.
    private static let playClockGraceMs = 1_000
}
