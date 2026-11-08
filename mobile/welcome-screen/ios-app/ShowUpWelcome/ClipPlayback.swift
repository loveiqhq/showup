// ClipPlayback.swift
// ShowUp · one profile clip at a time, for every screen that shows a Search profile
//
// Search's own profile and the match's profile opened read-only from the Date screen (SHOWUP-193)
// play voice notes and videos the same way: tap to play, one at a time, a readout that moves, an end
// that is noticed. It was Search's model's own code; the Date screen needed the same thing with none
// of Search's events, so the playing moved here and the REPORTING stayed with each owner -- this class
// knows nothing about analytics. Android's `ClipPlayback.kt`.

import Foundation

@MainActor
final class ClipPlayback {
    private let players: (any MediaPlayerMaking)?
    /// Applies a change to the playback the owner draws. Set by the owner once it exists.
    var change: ((inout SearchPlayback?) -> Void) -> Void = { _ in }
    /// The clip ran out (or past its length and the grace): the owner stops it, as it stops any other.
    var ended: () -> Void = {}

    private var session: (any MediaPlaying)?
    private var ticker: Task<Void, Never>?
    /// Every stop moves it on, so a play still opening when it moves knows it was superseded.
    private var generation = 0

    init(players: (any MediaPlayerMaking)?) {
        self.players = players
    }

    /// Opens `media` and, once it plays, draws it from 0 and keeps the readout moving.
    func start(kind: MediaKind, media: SearchMedia) {
        guard let url = media.url, players != nil else { return }
        let mine = generation
        Task { [weak self] in
            guard let self, let players = self.players else { return }
            let next = players.makePlayer(kind: kind)
            let started = await next.start(path: url.absoluteString)
            // SUPERSEDED while the clip was opening -- a pass, a like, a sheet, the app leaving. Without
            // this, a voice note tapped just before `Not now` played over the next profile.
            guard mine == self.generation else {
                await next.stop()
                return
            }
            guard started else { return }
            self.session = next
            self.change { $0 = SearchPlayback(kind: kind, positionMs: 0, durationMs: media.durationMs) }
            self.runClock(next, durationMs: media.durationMs)
        }
    }

    /// Stops the player. Clearing (and reporting) what was drawn is the owner's.
    func stop() {
        generation += 1
        ticker?.cancel()
        ticker = nil
        let ending = session
        session = nil
        if let ending { Task { await ending.stop() } }
    }

    /// Polls the playhead and notices the end. BOUNDED by the clip's own length and a grace, so a player
    /// that never reports an ending cannot keep a loop alive.
    private func runClock(_ playing: any MediaPlaying, durationMs: Int) {
        ticker = Task { [weak self] in
            var waited = 0
            let limit = durationMs + Self.graceMs
            while waited < limit {
                try? await Task.sleep(nanoseconds: UInt64(Self.tickMs) * 1_000_000)
                waited += Self.tickMs
                guard let self, !Task.isCancelled, self.session === playing else { return }
                if playing.hasFinished() {
                    self.change { $0?.positionMs = durationMs }
                    self.ended()
                    return
                }
                let at = playing.positionMs()
                self.change { $0?.positionMs = at }
            }
            self?.ended()
        }
    }

    /// How often a playing card's readout moves, in ms.
    private static let tickMs = 100
    /// How long the clock keeps polling past a clip's stated length: a backstop, not a timing rule.
    private static let graceMs = 1_000
}
