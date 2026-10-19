//
//  MediaPlayback.swift
//  ShowUpWelcome · the seam between "it played back" and how it played (SHOWUP-161)
//
//  ───────────────────────────────────────────────────────────────────────────
//  WHY THIS EXISTS AT ALL
//  ───────────────────────────────────────────────────────────────────────────
//
//  The ticket asks for two things that were drawn and never built. `Playback on review is on
//  demand, repeatable, and the CTA row does not move between plays` is an acceptance criterion,
//  and the filled voice card is specced with a `0:08 / 0:14` readout -- a played position out of a
//  total, which only means anything if the card plays.
//
//  What shipped instead was a flag. `playPressed` set `isPlaying = true`, fired
//  `media_preview_played` and played nothing; `playbackFinished` was called by nobody, so the flag
//  never came back down; the card's button reached the same function and returned early because
//  there is no take on that screen; and `playedMs` was the literal `0`.
//
//  The analytics half is the part worth naming. `media_preview_played` was firing for a play that
//  did not happen, into the same dataset the ticket says will decide whether 10 and 15 seconds are
//  the right caps. A silent feature is a gap; a feature that reports itself working is a lie in
//  the data.
//
//  ───────────────────────────────────────────────────────────────────────────
//  WHY IT IS A PROTOCOL, AND WHY IT IS @MainActor
//  ───────────────────────────────────────────────────────────────────────────
//
//  Same seam, same reason, and deliberately the same shape as `MediaCaptureMaking` next door:
//  AVFoundation needs a device, and everything ELSE here -- which source wins, what a second press
//  does, when the event fires, when the readout resets -- is a product rule that belongs in
//  `MediaModel` where it can be tested with nothing attached.
//
//  `@MainActor` and NOT `Sendable`, for the same reason the capture protocol is: it is held by a
//  `@MainActor` type and driven from the main actor, which is where AVFoundation wants to be driven
//  from anyway. Isolating it is what lets every implementation here be an ordinary mutable class
//  with no `@unchecked Sendable` anywhere -- an escape hatch this project bans and has a checker
//  for.
//

import Foundation

/// Where a playback request came from, which is also what it is allowed to interrupt.
enum PlaybackSource: Equatable, Sendable {
    /// The 56pt button on a filled video card, or the 44pt pip on a filled voice card.
    case card
    /// The 88pt glass button on video review, or the 64pt sunset pip on voice review.
    case review
}

/// What is playing, and how far in. Nil when nothing is -- the resting state of every screen here.
struct MediaPlayback: Equatable, Sendable {
    let kind: MediaKind
    let source: PlaybackSource
    /// The playhead, in ms. What the card's `0:08` half of `0:08 / 0:14` reads.
    var positionMs: Int = 0
    /// The clip's length. The `0:14` half.
    let durationMs: Int

    /// 0...1. Drives the played portion of the waveform.
    var progress: Double {
        guard durationMs > 0 else { return 0 }
        return min(1, max(0, Double(positionMs) / Double(durationMs)))
    }
}

/// One playing clip.
@MainActor
protocol MediaPlaying: AnyObject {
    /// Begins playing `path`. Returns false when there is nothing playable.
    ///
    /// A missing file, an unsupported container, a URL that will not open. False is a normal
    /// outcome and not an error: an artefact that has been uploaded and whose local copy has been
    /// cleaned up is exactly this case, and the honest response is to do nothing -- no playhead,
    /// no tracking event -- rather than to show a position that never moves.
    ///
    /// IT DOES NOT REPORT A DURATION, deliberately. The player does not know one until it has read
    /// the asset, so asking here would either block the press or return `indefinite`. The caller
    /// already knows: every artefact and every take carries its own `durationMs`, measured when it
    /// was recorded. That is the `0:14` in `0:08 / 0:14`.
    func start(path: String) async -> Bool

    /// The playhead now, in ms. Polled by the model's ticker rather than pushed.
    func positionMs() -> Int

    /// True once the clip has reached its own end, as opposed to being stopped.
    func hasFinished() -> Bool

    /// Stops and releases. Safe to call twice.
    func stop() async
}

/// Makes a player.
@MainActor
protocol MediaPlayerMaking {
    func makePlayer(kind: MediaKind) -> any MediaPlaying
}

/// A player with no player in it.
///
/// Used by previews and by every unit test: it reports success and then advances its playhead from
/// the clock it is given, so the model's ticker, the readout, the finish transition and the
/// tracking event are all exercised with no device and no file.
///
/// `failFor` is how a test reaches the false branch above without needing a broken file.
@MainActor
final class FakeMediaPlayerMaker: MediaPlayerMaking {
    private let now: () -> Int64
    private let durationMs: Int
    private let failFor: Set<String>

    /// Every player this maker has made, newest last. Tests assert against it.
    private(set) var players: [FakePlayer] = []

    init(
        now: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) },
        durationMs: Int = 9_400,
        failFor: Set<String> = []
    ) {
        self.now = now
        self.durationMs = durationMs
        self.failFor = failFor
    }

    func makePlayer(kind: MediaKind) -> any MediaPlaying {
        let made = FakePlayer(now: now, durationMs: durationMs, failFor: failFor)
        players.append(made)
        return made
    }

    @MainActor
    final class FakePlayer: MediaPlaying {
        private let now: () -> Int64
        private let durationMs: Int
        private let failFor: Set<String>
        private var startedAt: Int64?

        /// True between `start` and `stop`.
        var running: Bool { startedAt != nil }

        /// True once this player has played, and it stays true after it stops.
        ///
        /// How a test proves a press became a play now that the card fires no tracking event --
        /// see `MediaModel.cardPlayPressed` for why it does not.
        private(set) var didStart = false

        init(now: @escaping () -> Int64, durationMs: Int, failFor: Set<String>) {
            self.now = now
            self.durationMs = durationMs
            self.failFor = failFor
        }

        func start(path: String) async -> Bool {
            if failFor.contains(path) { return false }
            didStart = true
            startedAt = now()
            return true
        }

        func positionMs() -> Int {
            guard let began = startedAt else { return 0 }
            return min(durationMs, max(0, Int(now() - began)))
        }

        func hasFinished() -> Bool { startedAt != nil && positionMs() >= durationMs }

        func stop() async { startedAt = nil }
    }
}
