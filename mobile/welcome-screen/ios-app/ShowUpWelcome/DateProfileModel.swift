// DateProfileModel.swift
// ShowUp · "See {name}'s full profile" from the Date screen, read-only (SHOWUP-193 ⑫)
//
// The match's profile, built by the server exactly as Search builds one (`GET /me/date/profile`), drawn
// by Search's own profile view in its read-only mode. Its state IS Search's state type, so the view
// needs nothing new -- but it is owned here, not by Search's model: nothing on it can be liked or
// passed, and it fires `screen_viewed` `search_profile` from `date_screen` and no other Search event
// (spec §5). Photos open and page, clips play (ClipPlayback, shared with Search), and none of that is
// reported. Android's `DateProfileViewModel.kt`.

import Foundation

@MainActor
@Observable
final class DateProfileModel {
    /// The open profile; nil while none is open (the route is popped).
    private(set) var state: SearchUiState?

    private let store: any DateStoring
    private let analytics: (any AnalyticsTracking)?
    private let zone: () -> TimeZone
    private let clips: ClipPlayback
    private var readTask: Task<Void, Never>?

    init(
        store: any DateStoring,
        analytics: (any AnalyticsTracking)? = nil,
        players: (any MediaPlayerMaking)? = nil,
        zone: @escaping () -> TimeZone = { TimeZone.current }
    ) {
        self.store = store
        self.analytics = analytics
        self.zone = zone
        self.clips = ClipPlayback(players: players)
        clips.change = { [weak self] edit in
            guard let self, self.state != nil else { return }
            edit(&self.state!.playback)
        }
        clips.ended = { [weak self] in self?.stopPlayback() }
    }

    /// `See {name}'s full profile`: one visit, reported on the tap. A second tap while open is nothing.
    func open() {
        guard state == nil else { return }
        state = SearchUiState(loading: true)
        analytics?.report(ProfileAnalytics.screenViewed(.searchProfile, referrer: .dateScreen))
        read()
    }

    /// `Try again` on the failed state: the same visit, read again.
    func retry() {
        guard state != nil else { return }
        state?.loading = true
        state?.failure = nil
        read()
    }

    /// The back pill, the back gesture, or the date going away under it.
    func close() {
        readTask?.cancel()
        stopPlayback()
        state = nil
    }

    func openPhoto(_ index: Int) {
        stopPlayback()
        state?.viewerAt = index
    }

    func viewerPage(_ index: Int) { state?.viewerAt = index }

    func closeViewer() { state?.viewerAt = nil }

    /// Play or pause `kind`; starting one stops the other.
    func togglePlay(_ kind: MediaKind) {
        guard let profile = state?.current else { return }
        let playing = state?.playback
        stopPlayback()
        if playing?.kind == kind { return }
        guard let media = kind == .voice ? profile.voice : profile.video else { return }
        clips.start(kind: kind, media: media)
    }

    func scrolledOut(_ kind: MediaKind) {
        if state?.playback?.kind == kind { stopPlayback() }
    }

    private func stopPlayback() {
        clips.stop()
        state?.playback = nil
    }

    private func read() {
        readTask?.cancel()
        let store = self.store
        let zone = self.zone().identifier
        readTask = Task { [weak self] in
            let answer = await store.partnerProfile(zone: zone)
            guard let self, !Task.isCancelled, self.state != nil else { return }
            self.state?.loading = false
            switch answer {
            case .success(let profile):
                self.state?.failure = nil
                self.state?.queue = [profile]
            case .failure(let why):
                self.state?.failure = why
            }
        }
    }
}
