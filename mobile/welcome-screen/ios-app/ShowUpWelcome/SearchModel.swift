//
//  SearchModel.swift
//  ShowUp · Date search: the Search tab (SHOWUP-184 queue, 185 Premium grid) and the Likes tab (186)
//
//  The Swift twin of `search/SearchViewModel.kt`; read that file's header. In short:
//
//    · ONE OWNER FOR DATE SEARCH: the queue, the grid, the likes list, the profile on screen --
//      wherever it was opened from -- the like flow's sheets, the confirm and its sorry sheet, the
//      actions coach, the photo viewer, playback, the decline's Undo window and every event of
//      family I. The screens take `SearchUiState` and return taps; nothing on them decides anything.
//    · A VISIT is one `screen_viewed` of a tab: a Search visit (`visit`) reads the queue or the grid,
//      a Likes visit (`likesArrived`) reads the list. A return from a profile is not a visit: nothing
//      is read again but the cards' availability (`recheck`). The way back from Filters is not a
//      visit either (`arrived`).
//    · THE PLAN PICKS THE LAYOUT WHEN THE TAB OPENS (Search 02): the session is read first, and
//      Premium gets the grid, Freemium the queue -- for the whole visit.
//    · SENDS RUN IN THE BACKGROUND. A like or a pass moves on at once and the request follows,
//      retried silently: both are idempotent on the server. A CONFIRM waits for the server's answer,
//      because the answer decides what the user sees next -- a match, or the sorry sheet.
//    · AN OPEN LIKE FLOW SURVIVES THE PROCESS -- which sheet, for whom, from where, the message as
//      typed -- in `@SceneStorage` through the host (`attach`), as the profile drafts do. It comes back
//      once, when its person is there again; anyone else, and it is dropped.
//

import Foundation

/// Which of the like flow's sheets is open. `refused` is the sorry sheet, after a refused confirm.
/// String-backed, so an open flow can be stored.
enum LikeSheet: String, Equatable, Sendable, Codable { case confirm, message, refused }

/// The two Premium lists a profile opens from. String-backed, so an open like flow can name it.
enum PremiumSurface: String, Equatable, Sendable, Codable { case grid, likes }

/// An open like flow, as scene storage keeps it. `from` is the list its profile was opened from, nil
/// for the Freemium queue -- and for a flow kept by a build from before the grid, which decodes it so.
private struct UnfinishedLike: Codable, Equatable {
    let userId: String
    let sheet: LikeSheet
    let draft: String
    var from: PremiumSurface?
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

/// One card of the grid or the likes list, as it stands this visit. Cards are never reordered and
/// never removed from the list -- a card that flew out is `gone`, so Undo can put it back in its slot
/// and its position stays its place in the order the visit loaded.
struct PremiumCard: Equatable, Sendable {
    let userId: String
    let name: String
    let age: Int?
    /// The first photo; nil draws the placeholder.
    let photo: URL?
    /// What opening the card shows. Nil for a closed like, which cannot be opened.
    var profile: SearchProfile?
    let likedYou: Bool
    /// The liker's message (Likes list only).
    var message: String? = nil
    /// `Not now` this visit: dimmed and labelled on the grid, labelled only on the list.
    var notNow = false
    /// Closed in place: a refused confirm, or the re-check. Not a tap target.
    var closed: CloseReason? = nil
    /// A final answer: the 150 ms hold, then the fly-out.
    var leaving: Leave? = nil
    /// Flown out. Still in the list, for Undo and for its position.
    var gone = false

    /// A card the user can still open and act on.
    var open: Bool { closed == nil && !gone && profile != nil }
}

/// The grid or the likes list for this visit.
struct PremiumList: Equatable, Sendable {
    var loading = true
    var failure: CallFailure?
    var cards: [PremiumCard] = []
    /// A new read that changed the list: the screen starts again at its top.
    var generation = 0

    /// What is drawn: every card but the ones that flew out.
    var shown: [PremiumCard] { cards.filter { !$0.gone } }

    /// Loaded, nothing failed, and nothing to draw.
    var empty: Bool { !loading && failure == nil && shown.isEmpty }

    func card(_ userId: String) -> PremiumCard? { cards.first { $0.userId == userId } }

    /// 1-based, in the order the visit loaded, gone and closed cards included.
    func positionOf(_ userId: String) -> Int { (cards.firstIndex { $0.userId == userId } ?? -1) + 1 }
}

/// The profile on screen, opened from a list.
struct Opened: Equatable, Sendable {
    let from: PremiumSurface
    let userId: String
}

/// A declined like's Undo window: whose, from which list, at which position.
struct UndoToast: Equatable, Sendable {
    let userId: String
    let name: String
    let from: PremiumSurface
    let position: Int

    /// §35 for a decline: from the Likes list's toast it is `likes_received`; from the grid, `profile`.
    var surface: SearchAnalytics.Surface { from == .likes ? .likesReceived : .profile }
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
    /// The Search tab's layout for this visit, decided by the plan when the tab opened.
    var layout: SearchAnalytics.Layout = .oneUp
    /// The Premium grid (Search 02).
    var grid = PremiumList()
    /// The Likes tab's list (Search 03).
    var likes = PremiumList()
    /// The profile opened from the grid or the list, while it is on screen.
    var opened: Opened?
    /// The confirm is with the server: `Confirm date` is inert and the sheet stays open.
    var confirming = false
    /// Why the confirm was refused: what the sorry sheet (`LikeSheet.refused`) says.
    var refused: CloseReason?
    /// A declined like's 6 s Undo, over the list it was declined from.
    var undo: UndoToast?

    /// The list a profile was opened from.
    func list(_ from: PremiumSurface) -> PremiumList { from == .grid ? grid : likes }

    /// The card the opened profile belongs to.
    var openedCard: PremiumCard? { opened.flatMap { list($0.from).card($0.userId) } }

    /// The profile on screen: the opened card's, or the queue's first.
    var current: SearchProfile? { opened != nil ? openedCard?.profile : queue.first }

    /// The profile on screen liked the viewer: Like is `Confirm date`, Be sure is mutual.
    var likedYou: Bool { openedCard?.likedYou == true }

    /// Loaded, nothing failed, and nobody to show: the no-results screen.
    var empty: Bool { !loading && failure == nil && queue.isEmpty }

    /// The Likes badge: the server's open count, less a decline from the Likes list waiting out its
    /// Undo -- that card has left the list, and "the badge equals the open cards on the list". A
    /// decline from the grid counts until it is sent ("it drops ... when a decline is sent").
    var likesBadge: Int {
        let pending = undo.map { u in u.from == .likes && likes.card(u.userId)?.gone == true } ?? false
        return max(0, likesOpen - (pending ? 1 : 0))
    }
}

@MainActor
@Observable
final class SearchModel {
    private(set) var state = SearchUiState()

    private let store: any SearchStoring
    private let analytics: (any AnalyticsTracking)?
    private let players: (any MediaPlayerMaking)?
    /// Milliseconds, for `dwell_ms`, `visible_ms` and the Undo window.
    private let millis: () -> Int
    /// Between silent retries of a like or a pass, in seconds. Short in tests.
    private let retryDelays: [TimeInterval]
    /// How long a declined like can be undone: `Motion.likesUndo`. Short in tests.
    private let undoWindow: TimeInterval

    /// The stored opt-out: `Don't show again` once, on any device.
    private var coachHidden = false
    private var coachShownThisVisit = false
    private var coachShownAt = 0

    /// Who has been decided on this session, so a refill never brings them back mid-request.
    private var decided: Set<String> = []

    /// Who got a FINAL answer this session -- a like, a confirm, a decline, Not interested: never back
    /// on the Likes list. Narrower than `decided`: a `Not now` leaves a received like open, on the list
    /// ("it stays open in the Likes tab until it closes"). Both cover the moment a read returns before
    /// the answer it raced has landed on the server.
    private var answered: Set<String> = []

    /// The visit's reported `filters_active`, kept for a reload after Filters.
    private var filtersActive: [String] = []
    private var resultsCount: Int?
    private var emptyReported = false

    /// The phone's zone, for the server's `today` / `tomorrow` on a re-check.
    private var zone = "UTC"

    /// A visit has been made, so a return from Filters is a return, not a first arrival.
    private var visited = false

    /// Filters saved new settings: the return reads the queue or the grid again under them.
    private var reloadOnReturn = false

    /// The list screens arrived at and not left, this model's life: `.grid` for the Search tab (grid
    /// or queue), `.likes` for the Likes tab. A screen appearing again while it is here -- under a
    /// popped profile, which fired its own `screen_viewed` -- is not an arrival; leaving it
    /// (`leftScreen`), or leaving a profile opened from it for somewhere else, makes the next showing
    /// one. Android's `SearchViewModel.isOnScreen`, the same rule.
    private var onScreen: Set<PremiumSurface> = []

    /// Where the Likes list was when its tab was left -- the first card showing -- for the next visit;
    /// nil, the top, once the list read back different ("Switching tabs and coming back restores the
    /// scroll position unless the list reloaded"). Not observed: it is read when the list is built,
    /// and a write on every card scrolled past must not redraw the app.
    @ObservationIgnored private(set) var likesScrollAnchor: String?

    // Per profile, for `profile_card_viewed`.
    private var shownAt = 0
    private var photosSwiped = 0
    private var sectionsSeen: Set<Int> = []

    private var loadTask: Task<Void, Never>?
    private var likesTask: Task<Void, Never>?
    private var recheckTask: Task<Void, Never>?
    private var confirmTask: Task<Void, Never>?
    private var session: (any MediaPlaying)?
    private var ticker: Task<Void, Never>?
    /// Every stop moves it on, so a play still opening when it moves knows it was superseded.
    private var playGeneration = 0

    // The Undo window.
    private var undoTask: Task<Void, Never>?
    /// What is left of the window, so focus on Undo can hold it and let it go.
    private var undoLeftMs = 0
    private var undoStartedAt = 0

    /// Set by `attach`, because `@State` builds the model before `@SceneStorage` can be read.
    private var persist: (String) -> Void = { _ in }
    private var attached = false
    /// A like flow the process lost, waiting for the first list that shows its person.
    private var unfinished: UnfinishedLike?

    init(
        store: any SearchStoring,
        analytics: (any AnalyticsTracking)? = nil,
        players: (any MediaPlayerMaking)? = nil,
        millis: @escaping () -> Int = { Int(Date().timeIntervalSince1970 * 1000) },
        retryDelays: [TimeInterval] = [1, 3, 9, 27],
        undoWindow: TimeInterval = Motion.likesUndo
    ) {
        self.store = store
        self.analytics = analytics
        self.players = players
        self.millis = millis
        self.retryDelays = retryDelays
        self.undoWindow = undoWindow
    }

    /// Binds to scene storage and reads what it holds on the first call only -- the shape of
    /// `DetailsModel.attach`, so the host can call it on every arrival.
    func attach(stored: String, persist: @escaping (String) -> Void) {
        self.persist = persist
        guard !attached else { return }
        attached = true
        guard let data = stored.data(using: .utf8),
              let kept = try? JSONDecoder().decode(UnfinishedLike.self, from: data),
              kept.sheet != .refused else { return }
        unfinished = kept
    }

    /// A Search visit has been made in this model's life -- a process that ended starts again.
    var hasVisited: Bool { visited }

    /// The host's question when a list screen appears: is it still here from before?
    func isOnScreen(_ surface: PremiumSurface) -> Bool { onScreen.contains(surface) }

    /// The list screen went away: the next time it shows is an arrival.
    func leftScreen(_ surface: PremiumSurface) {
        onScreen.remove(surface)
    }

    // MARK: - the visit

    /// Search is on screen. Returning from Filters starts a new visit with position 1, but keeps the
    /// coach suppressed and reloads the queue or grid only when settings changed (27 Oct 2026).
    func arrived(zone: String, filtersActive: [String], referrer: ProfileScreen?) {
        onScreen.insert(.grid)
        guard visited, referrer == .checkinFilters else {
            visit(zone: zone, filtersActive: filtersActive, referrer: referrer)
            return
        }
        self.zone = zone
        report(ProfileAnalytics.screenViewed(searchScreen, referrer: referrer))
        self.filtersActive = filtersActive
        coachShownThisVisit = true
        emptyReported = false
        state.position = 1
        state.coach = false
        guard reloadOnReturn else {
            if let resultsCount { report(SearchAnalytics.searchViewed(resultsCount: resultsCount, filtersActive: filtersActive, layout: state.layout)) }
            reportEmptyIfNeeded()
            resume()
            return
        }
        reloadOnReturn = false
        self.filtersActive = filtersActive
        resultsCount = nil
        stopPlayback()
        if state.layout == .grid {
            state.grid.loading = true
            state.grid.failure = nil
            state.opened = nil
            state.sheet = nil
            state.viewerAt = nil
            loadGrid(zone: zone)
        } else {
            state.loading = true
            state.failure = nil
            state.queue = []
            state.position = 1
            state.sheet = nil
            state.viewerAt = nil
            loadQueue(zone: zone)
        }
        keepLikeFlow()
    }

    /// Filters saved new settings; the return to Search reads the queue or the grid under them.
    func filtersSaved() {
        reloadOnReturn = true
    }

    /// A visit to the Search tab: the session first, because the plan picks the layout; then
    /// `screen_viewed` for that layout, once, and the queue or the grid read.
    func visit(zone: String, filtersActive: [String], referrer: ProfileScreen?) {
        onScreen.insert(.grid)
        visited = true
        reloadOnReturn = false
        self.zone = zone
        self.filtersActive = filtersActive
        coachShownThisVisit = referrer == .checkinFilters
        emptyReported = false
        resultsCount = nil
        stopPlayback()
        flushDecline()
        state.loading = true
        state.failure = nil
        state.queue = []
        state.position = 1
        state.sheet = nil
        state.coach = false
        state.viewerAt = nil
        state.opened = nil
        state.refused = nil
        state.confirming = false
        state.grid = PremiumList(generation: state.grid.generation + 1)
        // The plan last read (at the last foreground) draws the loading frame; the session read below
        // confirms it before anything but the spinner is shown.
        state.layout = state.premium ? .grid : .oneUp
        // The sheet the reset closed is forgotten too. One the process lost is still held in
        // `unfinished`, and is kept again when its person comes back.
        keepLikeFlow()
        loadTask?.cancel()
        let store = self.store
        loadTask = Task { [weak self] in
            async let countCall = store.likesOpenCount()
            let session = await store.session()
            guard let self, !Task.isCancelled else { return }
            if case .success(let s) = session {
                // ONCE HIDDEN HERE, HIDDEN: a session read that left before `Don't show again` landed
                // on the server still says false, and must not bring the coach back on this device.
                self.coachHidden = self.coachHidden || s.coachHidden
                self.state.premium = s.premium
            }
            // A failed session read keeps the plan last known: the layout must be decided either way.
            let layout: SearchAnalytics.Layout = self.state.premium ? .grid : .oneUp
            self.state.layout = layout
            self.report(ProfileAnalytics.screenViewed(self.searchScreen, referrer: referrer))
            if case .success(let n) = await countCall { self.state.likesOpen = n }
            guard !Task.isCancelled else { return }
            if layout == .grid {
                await self.readGrid(zone: zone)
            } else {
                await self.readQueue(zone: zone)
            }
        }
    }

    /// `Retry` on the failed state: the same visit, read again.
    func retry(zone: String) {
        if state.layout == .grid {
            state.grid.loading = true
            state.grid.failure = nil
            loadGrid(zone: zone)
        } else {
            state.loading = true
            state.failure = nil
            loadQueue(zone: zone)
        }
    }

    /// The Search screen of this visit's layout, for `screen_viewed`.
    private var searchScreen: ProfileScreen { state.layout == .grid ? .searchGrid : .searchProfile }

    private func loadQueue(zone: String) {
        loadTask?.cancel()
        loadTask = Task { [weak self] in await self?.readQueue(zone: zone) }
    }

    private func reportEmptyIfNeeded() {
        let empty = state.layout == .grid ? state.grid.empty : state.empty
        if !emptyReported, empty, onScreen.contains(.grid) {
            emptyReported = true
            report(SearchAnalytics.emptyShown())
        }
    }

    private func readQueue(zone: String) async {
        let queue = await store.queue(zone: zone)
        guard !Task.isCancelled else { return }
        switch queue {
        case .failure(let why):
            state.loading = false
            state.failure = why
        case .success(let q):
            let profiles = q.profiles.filter { !decided.contains($0.userId) }
            resultsCount = q.resultsCount
            report(SearchAnalytics.searchViewed(resultsCount: q.resultsCount, filtersActive: filtersActive))
            let reopened = unfinished.flatMap { $0.from == nil && $0.userId == profiles.first?.userId ? $0 : nil }
            if unfinished?.from == nil { unfinished = nil }
            state.loading = false
            state.failure = nil
            state.queue = profiles
            state.viewer = q.viewer
            state.sheet = reopened?.sheet
            if let reopened { state.draft = reopened.draft }
            // The user had passed the coach to tap Like; it does not come back over their sheet.
            if reopened != nil { coachShownThisVisit = true }
            keepLikeFlow()
            present()
        }
    }

    // MARK: - the grid (Search 02)

    private func loadGrid(zone: String) {
        loadTask?.cancel()
        loadTask = Task { [weak self] in await self?.readGrid(zone: zone) }
    }

    /// The grid, once per visit: only people still available, so no card is closed on arrival.
    private func readGrid(zone: String) async {
        let read = await store.grid(zone: zone)
        guard !Task.isCancelled else { return }
        switch read {
        case .failure(let why):
            state.loading = false
            state.grid.loading = false
            state.grid.failure = why
        case .success(let g):
            resultsCount = g.resultsCount
            report(SearchAnalytics.searchViewed(resultsCount: g.resultsCount, filtersActive: filtersActive, layout: .grid))
            // Nobody already decided this session, as the queue: a `Not now` card is gone on the next
            // visit (the Search 01 pool rules), and an answer still on its way to the server does not
            // bring its card back.
            let cards = g.cards.filter { !decided.contains($0.profile.userId) }.map { c in
                PremiumCard(userId: c.profile.userId, name: c.profile.name, age: c.profile.age,
                            photo: c.profile.photos.first ?? nil, profile: c.profile, likedYou: c.likedYou)
            }
            state.loading = false
            state.viewer = g.viewer
            state.grid = PremiumList(loading: false, cards: cards, generation: state.grid.generation + 1)
            reopenUnfinished(.grid)
            reportEmptyIfNeeded()
        }
    }

    // MARK: - the Likes tab (Search 03)

    /// A visit to the Likes tab: `screen_viewed`, then the list read once, in the server's order, and
    /// `likes_received_viewed` with what it holds. Switching tabs and coming back keeps the scroll
    /// position "unless the list reloaded" -- read again here, it keeps it when the same cards came
    /// back in the same order, and starts at the top when they did not.
    func likesArrived(zone: String, referrer: ProfileScreen?) {
        onScreen.insert(.likes)
        self.zone = zone
        stopPlayback()
        flushDecline()
        report(ProfileAnalytics.screenViewed(.likesReceived, referrer: referrer))
        state.likes.loading = state.likes.cards.isEmpty
        state.likes.failure = nil
        state.opened = nil
        readLikes(zone: zone)
    }

    /// The Likes list moved: the first card showing.
    func likesScrolled(_ anchor: String?) {
        likesScrollAnchor = anchor
    }

    /// `Retry` on the list's failed state.
    func likesRetry(zone: String) {
        state.likes.loading = true
        state.likes.failure = nil
        readLikes(zone: zone)
    }

    private func readLikes(zone: String) {
        likesTask?.cancel()
        let store = self.store
        likesTask = Task { [weak self] in
            let read = await store.likesReceived(zone: zone)
            guard let self, !Task.isCancelled else { return }
            switch read {
            case .failure(let why):
                self.state.likes.loading = false
                self.state.likes.failure = why
            case .success(let l):
                // Nobody given a final answer this session: a decline or a confirm still on its way to
                // the server leaves no card behind. The counts are the list's, as drawn -- "the badge
                // equals the open cards on the list".
                let kept = l.cards.filter { !self.answered.contains($0.userId) }
                let openCount = kept.filter { $0.closed == nil }.count
                self.report(SearchAnalytics.likesReceivedViewed(openCount: openCount, closedCount: kept.count - openCount))
                let cards = kept.map { c in
                    PremiumCard(userId: c.userId, name: c.name, age: c.age, photo: c.photo, profile: c.profile,
                                likedYou: true, message: c.message, closed: c.closed)
                }
                let old = self.state.likes.cards
                let same = old.map(\.userId) == cards.map(\.userId) && old.map(\.closed) == cards.map(\.closed)
                if !same { self.likesScrollAnchor = nil }
                self.state.likesOpen = openCount
                self.state.viewer = l.viewer
                self.state.likes = PremiumList(loading: false, cards: cards,
                                               generation: same ? self.state.likes.generation : self.state.likes.generation + 1)
                self.reopenUnfinished(.likes)
            }
        }
    }

    /// A like flow the process lost, reopened once its person is on the list again.
    private func reopenUnfinished(_ from: PremiumSurface) {
        guard let u = unfinished, u.from == from else { return }
        unfinished = nil
        guard let card = state.list(from).card(u.userId), card.open else {
            keepLikeFlow()
            return
        }
        // The user had passed the coach to tap Like; it does not come back over their sheet.
        coachShownThisVisit = true
        state.opened = Opened(from: from, userId: card.userId)
        state.sheet = u.sheet != .message || !card.likedYou ? u.sheet : nil
        state.draft = u.draft
        keepLikeFlow()
        shownAt = millis()
    }

    // MARK: - opening a card, and coming back

    /// A card's photo, heart or `View profile`: the full profile. Leaving the list sends a decline
    /// waiting out its Undo ("when the user leaves the grid (another card ...)"), and a card still
    /// flying out finishes at once -- it is not on screen to finish its flight.
    func openCard(_ from: PremiumSurface, userId: String) {
        guard state.opened == nil, let card = state.list(from).card(userId), card.open, card.leaving == nil else { return }
        flushDecline()
        stopPlayback()
        updateList(from) { list in
            for i in list.cards.indices where list.cards[i].leaving != nil {
                list.cards[i].leaving = nil
                list.cards[i].gone = true
            }
        }
        state.opened = Opened(from: from, userId: card.userId)
        state.sheet = nil
        state.viewerAt = nil
        state.draft = ""
        state.refused = nil
        report(ProfileAnalytics.screenViewed(.searchProfile, referrer: listScreen(from)))
        photosSwiped = 0
        sectionsSeen = []
        // The coach: over the first profile opened from the grid each Search visit, never on a `Liked
        // you` one (its third button is Confirm date) -- it waits for the next with Like.
        if from == .grid && shouldShowCoach(hiddenForever: coachHidden, shownThisVisit: coachShownThisVisit,
                                            hasProfile: true, likedYou: card.likedYou) {
            coachShownThisVisit = true
            coachShownAt = millis()
            state.coach = true
            report(SearchAnalytics.coachShown())
        } else {
            shownAt = millis()
        }
    }

    /// The opened profile is left for somewhere else -- another tab, Filters, the likes chip -- not
    /// for its list: its view reported, playback stopped, the profile closed, and nothing fired for
    /// the list it is not returning to. The next screen fires its own `screen_viewed`.
    func closeProfileForElsewhere() {
        guard let opened = state.opened else { return }
        // The list under it is not coming back: whenever it shows again, that is an arrival.
        onScreen.remove(opened.from)
        stopPlayback()
        if !state.coach { reportCardViewed() }
        state.opened = nil
        state.sheet = nil
        state.viewerAt = nil
        state.coach = false
        state.refused = nil
        state.confirming = false
        keepLikeFlow()
    }

    /// The app came to the front: the plan and the open-likes count read again. The plan decides
    /// where the Likes tab goes ("read at app open"), on every tab bar, not only after a Search visit.
    func foregrounded() {
        let store = self.store
        Task { [weak self] in
            let session = await store.session()
            guard let self else { return }
            if case .success(let s) = session {
                self.coachHidden = self.coachHidden || s.coachHidden
                self.state.premium = s.premium
            }
            await self.refreshLikesOpenNow()
        }
    }

    /// `All profiles` / `All likes`, or the swipe back with nothing over the profile: the card
    /// unchanged.
    func backFromProfile() {
        guard state.opened != nil, !state.coach, state.sheet == nil, state.viewerAt == nil else { return }
        returnToList { _ in }
    }

    /// The profile closes and its list is on screen again: the profile's view reported, the card
    /// changed by what was decided (`change`), `screen_viewed` for the list (referrer
    /// `search_profile`), and every card's availability read again.
    private func returnToList(_ change: (inout PremiumCard) -> Void) {
        guard let opened = state.opened else { return }
        stopPlayback()
        reportCardViewed()
        updateList(opened.from) { list in
            if let i = list.cards.firstIndex(where: { $0.userId == opened.userId }) { change(&list.cards[i]) }
        }
        state.opened = nil
        state.sheet = nil
        state.viewerAt = nil
        state.coach = false
        state.refused = nil
        state.confirming = false
        keepLikeFlow()
        report(ProfileAnalytics.screenViewed(listScreen(opened.from), referrer: .searchProfile))
        recheck(opened.from)
    }

    /// The availability re-check (Search 02 19): every card still standing, in one call. `matched` /
    /// `no_overlap` closes a card in its slot, liked or not; a new meet time is shown. Nothing
    /// reorders, appears or disappears; a failed call leaves the list as it is, and nothing retries
    /// until the next return. It fires nothing: a card closing is state, not an act.
    private func recheck(_ from: PremiumSurface) {
        let ids = state.list(from).cards.filter { $0.open && $0.leaving == nil }.map(\.userId)
        guard !ids.isEmpty else { return }
        recheckTask?.cancel()
        let store = self.store
        let zone = self.zone
        recheckTask = Task { [weak self] in
            let read = await store.availability(userIds: ids, zone: zone)
            guard let self, !Task.isCancelled, case .success(let statuses) = read else { return }
            var closedAny = false
            self.updateList(from) { list in
                for i in list.cards.indices {
                    let c = list.cards[i]
                    // A card that flew out or is flying meanwhile is the user's answer: untouched.
                    guard c.open, c.leaving == nil, let status = statuses[c.userId] else { continue }
                    switch status {
                    case .closed(let reason):
                        list.cards[i].closed = reason
                        closedAny = true
                    case .available(let meetAt, let meetDay):
                        list.cards[i].profile?.meetAt = meetAt
                        list.cards[i].profile?.meetDay = meetDay
                    }
                }
            }
            // A closed liked-you card closed its like on the server: the badge drops with it.
            if closedAny { self.refreshLikesOpen() }
        }
    }

    /// A card's fly-out finished: it leaves the list, and the rest close the gap.
    func cardLeft(_ from: PremiumSurface, userId: String) {
        updateList(from) { list in
            if let i = list.cards.firstIndex(where: { $0.userId == userId && $0.leaving != nil }) {
                list.cards[i].leaving = nil
                list.cards[i].gone = true
            }
        }
    }

    private func listScreen(_ from: PremiumSurface) -> ProfileScreen { from == .grid ? .searchGrid : .likesReceived }

    private func updateList(_ from: PremiumSurface, _ change: (inout PremiumList) -> Void) {
        if from == .grid {
            change(&state.grid)
            reportEmptyIfNeeded()
        } else {
            change(&state.likes)
        }
    }

    /// `position` for the profile on screen: in the queue, or the card's place in its list.
    private func positionNow() -> Int {
        guard let o = state.opened else { return state.position }
        return state.list(o.from).positionOf(o.userId)
    }

    /// A profile is on screen: its dwell starts, unless the coach is over it (counted from the dismissal).
    private func present() {
        reportEmptyIfNeeded()
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
    /// reported now, while the dwell is known; `resume` starts a new one if it comes back. A list
    /// leaving the screen sends a decline waiting out its Undo.
    func leave() {
        stopPlayback()
        flushDecline()
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

    /// `Like`, or `Confirm date` on a profile that liked the viewer: Be sure opens -- mutual for a
    /// confirm. Inert under the coach -- no event either. A confirm is a like: `like_tapped` with
    /// `surface: profile`, and the Be sure events with `is_mutual: true`.
    func like() {
        guard let profile = state.current, !state.coach, state.sheet == nil else { return }
        stopPlayback()
        report(SearchAnalytics.likeTapped(position: positionNow()))
        state.sheet = .confirm
        keepLikeFlow()
        report(SearchAnalytics.likeConfirmShown(targetAttendedCount: profile.attendedCount, isMutual: state.likedYou))
    }

    /// `Cancel` or a scrim tap on Be sure: nothing is sent, the profile stays. While the confirm is
    /// with the server the sheet does not close: "the sheet does not close early".
    func cancelConfirm() {
        guard let profile = state.current, state.sheet == .confirm, !state.confirming else { return }
        state.sheet = nil
        keepLikeFlow()
        report(SearchAnalytics.likeConfirmAbandoned(.confirm, targetAttendedCount: profile.attendedCount,
                                                    isMutual: state.likedYou))
    }

    /// `Continue`: the message sheet, its draft pre-filled for this person and day. Never on a confirm.
    func continueToMessage() {
        guard let profile = state.current, state.sheet == .confirm, !state.likedYou else { return }
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
        guard state.sheet == .message else { return }
        let message = messageToSend(state.draft)
        if let message { report(SearchAnalytics.likeMessageAttached(charCount: graphemes(message))) }
        sendLike(message: message)
    }

    /// The like, sent in the background. From the queue the next profile slides in; from the grid the
    /// user is back on it and the card flies out -- "a plain Like is never refused", so whatever the
    /// server answers, the user sees success.
    private func sendLike(message: String?) {
        guard let profile = state.current, state.sheet == .message else { return }
        let position = positionNow()
        let fromList = state.opened != nil
        state.sheet = nil
        keepLikeFlow()
        decided.insert(profile.userId)
        answered.insert(profile.userId)
        let store = self.store
        let target = profile.userId
        sendInBackground { await store.like(targetUserId: target, message: message, position: position) }
        if fromList {
            returnToList { $0.leaving = .liked }
        } else {
            next()
        }
    }

    /// `Confirm date` in mutual Be sure: sent at once, and the button stays inert until the server
    /// answers. WON: the match (07a's from here) -- back on the grid the card flies out, on the list
    /// it leaves; REFUSED: Be sure becomes the sorry sheet for the server's reason, and
    /// `like_confirm_failed` fires, once. No answer at all (offline): the sheet stays, `Confirm date`
    /// live again, and nothing is reported -- nothing was decided.
    func confirmDate() {
        guard let opened = state.opened, state.sheet == .confirm, state.likedYou, !state.confirming else { return }
        let position = positionNow()
        state.confirming = true
        confirmTask?.cancel()
        let store = self.store
        confirmTask = Task { [weak self] in
            let answer = await store.confirm(targetUserId: opened.userId, position: position)
            guard let self, !Task.isCancelled else { return }
            switch answer {
            case .failure:
                self.state.confirming = false
            case .success(.won):
                self.decided.insert(opened.userId)
                self.answered.insert(opened.userId)
                if self.state.opened == opened {
                    self.returnToList { card in
                        if opened.from == .grid {
                            card.leaving = .liked
                        } else {
                            card.gone = true
                        }
                    }
                } else {
                    // The profile closed while the server decided: the match stands, and its card
                    // leaves the list all the same.
                    self.updateList(opened.from) { list in
                        if let i = list.cards.firstIndex(where: { $0.userId == opened.userId }) {
                            list.cards[i].leaving = nil
                            list.cards[i].gone = true
                        }
                    }
                }
                self.refreshLikesOpen()
            case .success(.refused(let reason)):
                self.state.confirming = false
                self.state.sheet = .refused
                self.state.refused = reason
                self.keepLikeFlow()
                self.report(SearchAnalytics.likeConfirmFailed(reason))
                self.refreshLikesOpen()
            }
        }
    }

    /// The sorry sheet closed -- its button, the scrim or a swipe down, all the same: the sheet and
    /// the profile close, and the card is closed in its slot with the reason. No event: the next
    /// `screen_viewed` of the list measures it.
    func refusedDone() {
        guard let reason = state.refused, state.sheet == .refused else { return }
        returnToList { $0.closed = reason }
    }

    // MARK: - passes

    /// `Not now` / `Not interested`: one tap, no confirmation. Inert under the coach.
    ///
    /// From the queue the next profile slides in, and there is no undo. From a list the user is back
    /// on it: `Not now` labels the card (and leaves a received like OPEN), `Not interested` flies it
    /// out -- and on a profile that liked the viewer it is a DECLINE, with a 6 s Undo, sent and
    /// reported only when the window ends.
    func pass(_ type: SearchAnalytics.PassType) {
        guard let profile = state.current, !state.coach, state.sheet == nil else { return }
        let position = positionNow()
        let opened = state.opened
        if let opened, type == .notInterested, state.likedYou {
            let name = profile.name
            returnToList { $0.leaving = .notInterested }
            startUndo(UndoToast(userId: opened.userId, name: name, from: opened.from, position: position))
            return
        }
        report(SearchAnalytics.passSent(position: position, passType: type))
        decided.insert(profile.userId)
        if type == .notInterested { answered.insert(profile.userId) }
        let store = self.store
        let target = profile.userId
        sendInBackground { await store.pass(targetUserId: target, type: type) }
        if opened == nil {
            next()
        } else if type == .notNow {
            returnToList { $0.notNow = true }
        } else {
            returnToList { $0.leaving = .notInterested }
        }
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
            position: positionNow(),
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

    // MARK: - the decline's Undo window

    /// A decline waits out its window: "a second decline inside the window sends the first and starts a
    /// new toast".
    private func startUndo(_ toast: UndoToast) {
        flushDecline()
        state.undo = toast
        undoLeftMs = Int((undoWindow * 1000).rounded())
        runUndoClock()
    }

    private func runUndoClock() {
        undoTask?.cancel()
        undoStartedAt = millis()
        let left = undoLeftMs
        undoTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(max(0, left)) * 1_000_000)
            guard let self, !Task.isCancelled else { return }
            self.flushDecline()
        }
    }

    /// VoiceOver focus on `Undo` holds the window ("while focus is on it, the 6 s does not run out");
    /// focus leaving it lets what was left run on.
    func undoFocus(_ focused: Bool) {
        guard state.undo != nil else { return }
        if focused {
            guard let running = undoTask else { return }
            running.cancel()
            undoTask = nil
            undoLeftMs = max(0, undoLeftMs - (millis() - undoStartedAt))
        } else if undoTask == nil {
            runUndoClock()
        }
    }

    /// `Undo`: the card back in its old slot -- during the fly-out it simply stops, at full opacity --
    /// and nothing reaches the server. `pass_undone`, and no `pass_sent`.
    func undo() {
        guard let toast = state.undo else { return }
        undoTask?.cancel()
        undoTask = nil
        updateList(toast.from) { list in
            if let i = list.cards.firstIndex(where: { $0.userId == toast.userId }) {
                list.cards[i].leaving = nil
                list.cards[i].gone = false
            }
        }
        state.undo = nil
        report(SearchAnalytics.passUndone(position: toast.position, surface: toast.surface))
    }

    /// The window is over -- it ran out, the user left the list, the app went to the background, or a
    /// second decline came: the decline is sent, once, and `pass_sent` fires with it. The card stays
    /// gone; the badge follows the server once the decline lands.
    private func flushDecline() {
        guard let toast = state.undo else { return }
        undoTask?.cancel()
        undoTask = nil
        state.undo = nil
        decided.insert(toast.userId)
        answered.insert(toast.userId)
        report(SearchAnalytics.passSent(position: toast.position, passType: .notInterested, surface: toast.surface))
        let store = self.store
        let delays = retryDelays
        let target = toast.userId
        Task { [weak self] in
            let landed = await Self.sendWithRetries(delays: delays) {
                await store.pass(targetUserId: target, type: .notInterested)
            }
            if landed { await self?.refreshLikesOpenNow() }
        }
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
        report(SearchAnalytics.promptPlayed(playing.kind == .voice ? .voice : .video, position: positionNow(), playedPct: pct))
    }

    // MARK: - the likes chip and the badge

    /// The open-likes count again, e.g. when the app comes back to the front.
    func refreshLikesOpen() {
        Task { [weak self] in await self?.refreshLikesOpenNow() }
    }

    private func refreshLikesOpenNow() async {
        if case .success(let n) = await store.likesOpenCount() { state.likesOpen = n }
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
    /// design ("If it fails, retry silently"): the user has already moved on.
    private func sendInBackground(_ call: @escaping @Sendable () async -> Result<Void, CallFailure>) {
        let delays = retryDelays
        Task { _ = await Self.sendWithRetries(delays: delays, call) }
    }

    /// `sendInBackground`'s loop: true once a call landed.
    private static func sendWithRetries(delays: [TimeInterval],
                                        _ call: @Sendable () async -> Result<Void, CallFailure>) async -> Bool {
        if case .success = await call() { return true }
        for wait in delays {
            try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
            if case .success = await call() { return true }
        }
        return false
    }

    /// The like flow as it stands, into scene storage: an open Be sure or message sheet kept, with
    /// where its profile was opened from; anything else forgotten. The sorry sheet is not kept -- its
    /// answer is the server's, and the next read of the list shows the card closed.
    private func keepLikeFlow() {
        guard let profile = state.current, let sheet = state.sheet, sheet != .refused else {
            persist("")
            return
        }
        let kept = UnfinishedLike(userId: profile.userId, sheet: sheet, draft: sheet == .message ? state.draft : "",
                                  from: state.opened?.from)
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
