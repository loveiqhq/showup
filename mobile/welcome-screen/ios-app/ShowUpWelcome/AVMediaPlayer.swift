//
//  AVMediaPlayer.swift
//  ShowUpWelcome · the real player (SHOWUP-161)
//
//  ───────────────────────────────────────────────────────────────────────────
//  ONE PLAYER, NOT ONE PER CLIP
//  ───────────────────────────────────────────────────────────────────────────
//
//  `AVPlayer` holds a decoder and a route into the audio session. Two alive at once on a screen
//  with a video card and a voice card is two of both, and there is never a reason for it: the
//  ticket's rule that playback is on demand means exactly one thing is ever playing, and starting
//  a second play stops the first.
//
//  So this owns one instance and hands every session the same one. It is also what the video
//  surfaces draw from -- see `VideoSurface` in MediaCards.swift, which is the difference between
//  playing a clip and playing a clip's audio underneath a still picture.
//
//  ───────────────────────────────────────────────────────────────────────────
//  THE ENDING IS POLLED, NOT OBSERVED
//  ───────────────────────────────────────────────────────────────────────────
//
//  The obvious way to notice a clip ending is `AVPlayerItemDidPlayToEndTime`. It was written that
//  way first and replaced, for two reasons.
//
//  The first is concurrency. That notification hands back a `@Sendable` closure, and the only
//  useful thing to do inside it is touch this `@MainActor` class -- which means capturing a
//  non-Sendable value in a Sendable closure. That is a warning under Swift 5 and an error under
//  Swift 6, and the rule in ios-app/CLAUDE.md is to write as though 6 were already on. The three
//  escape hatches that would silence it are banned, with a checker that fails the build on them.
//
//  The second is that it bought nothing. The model already polls this session for its playhead on
//  every tick, so it is asking the question at exactly the moment the answer matters; the
//  notification delivered the same fact down a second path with its own observer lifetime to get
//  wrong.
//
//  ───────────────────────────────────────────────────────────────────────────
//  NOTHING HERE DECIDES ANYTHING
//  ───────────────────────────────────────────────────────────────────────────
//
//  Same rule as the recorders. No product decision lives in this file: not which source wins, not
//  what a second press does, not when the tracking event fires. It turns a path into sound and
//  pictures, and reports where the playhead is.
//

import AVFoundation
import Foundation
import Observation

/// The one player the app plays everything through.
///
/// `@MainActor` throughout, like `MediaPlaying` itself, so there is no isolation to cross and no
/// `@unchecked Sendable` anywhere -- an escape hatch this project bans and has a checker for.
@MainActor
@Observable
final class AVMediaPlayerMaker: MediaPlayerMaking {
    /// The one instance, created on first use. The video surfaces attach to it.
    ///
    /// OBSERVED, and that is not ceremony. The app reads this while building its body to hand to
    /// the video surface, and the player does not exist until the first play creates it -- so
    /// without observation it would go from nil to non-nil with nothing telling SwiftUI to look
    /// again, and the first video played would show its frozen frame with the sound running
    /// underneath. It happens to work without it only because `media.state.playback` changes in
    /// the same breath and redraws for an unrelated reason; that is accidental correctness, and it
    /// stops being true the moment the ordering changes.
    private(set) var surface: AVPlayer?

    /// Releases the player. The host calls this when the media flow goes away.
    func release() {
        surface?.pause()
        surface?.replaceCurrentItem(with: nil)
        surface = nil
    }

    fileprivate func player() -> AVPlayer {
        if let surface { return surface }
        let made = AVPlayer()
        surface = made
        return made
    }

    func makePlayer(kind: MediaKind) -> any MediaPlaying { AVSession(owner: self) }

    @MainActor
    final class AVSession: MediaPlaying {
        private unowned let owner: AVMediaPlayerMaker

        /// The item this session put on the player.
        ///
        /// Identity-compared in `hasFinished`, so a session that has been superseded cannot report
        /// the clip that replaced it as its own ending -- which the model would read as "this play
        /// ran to the end" and use to reset a playhead belonging to something else.
        private weak var item: AVPlayerItem?

        init(owner: AVMediaPlayerMaker) { self.owner = owner }

        func start(path: String) async -> Bool {
            // THE CATEGORY, BEFORE ANYTHING ELSE.
            //
            // A device-only bug, and a silent one in both senses. `AVMediaCapture` sets the shared
            // session to `.record` -- deliberately, its comment says, because that is the narrower
            // category and the one that ducks everything else -- and a CATEGORY PERSISTS after the
            // session is deactivated. So a user who records a voice note and then presses play on
            // it gets no sound at all, because the session is still configured for input only.
            //
            // Nothing here could catch that: the fake plays no audio, the fit harness renders no
            // sound, and CI has no speaker. It was found by reading the recorder next door.
            //
            // `.spokenAudio` matches what was recorded and is what routes a voice note to the
            // speaker rather than the earpiece.
            do {
                let session = AVAudioSession.sharedInstance()
                try session.setCategory(.playback, mode: .spokenAudio)
                try session.setActive(true)
            } catch {
                // A session we cannot configure is one we cannot play through, and the honest
                // answer is the same as an unplayable file: nothing happens.
                return false
            }
            let player = owner.player()
            // A local recording is a file path; an uploaded one is an https URL. Both arrive here
            // as a string, and only one of them is a valid URL on its own -- `URL(string:)` will
            // happily build a schemeless URL out of `/var/mobile/...` that then resolves to
            // nothing.
            let url = URL(string: path).flatMap { $0.scheme == nil ? nil : $0 }
                ?? URL(fileURLWithPath: path)
            let made = AVPlayerItem(url: url)
            item = made
            player.replaceCurrentItem(with: made)
            // `await`, and it is not optional. `seek(to:)` has a synchronous form and an
            // `async -> Bool` one, and inside an async function Swift selects the async overload --
            // so dropping the `await` to "avoid depending on overload resolution" is an error, not
            // a simplification. It was tried; CI said so.
            await player.seek(to: .zero)
            player.play()
            return player.currentItem != nil
        }

        func positionMs() -> Int {
            guard let player = owner.surface else { return 0 }
            let seconds = player.currentTime().seconds
            return seconds.isFinite ? Int(seconds * 1000) : 0
        }

        func hasFinished() -> Bool {
            guard let player = owner.surface,
                  let current = player.currentItem,
                  current === item
            else { return false }
            let length = current.duration.seconds
            // `indefinite` until the container has been read, and zero on an asset that failed to
            // load. Neither of those is an ending.
            guard length.isFinite, length > 0 else { return false }
            let played = player.currentTime().seconds
            guard played.isFinite else { return false }
            // A frame's worth of tolerance: the playhead lands a hair short of the stated duration
            // on plenty of real files, and waiting for exact equality would be waiting forever.
            return played >= length - Self.endToleranceSeconds
        }

        /// How close to the stated duration counts as the end.
        private static let endToleranceSeconds = 0.05

        func stop() async {
            owner.surface?.pause()
            // See `start`: the async overload wins inside an async function.
            await owner.surface?.seek(to: .zero)
            // Hands the route back, so music the user had playing resumes rather than staying
            // ducked behind a screen that is no longer making a sound.
            try? AVAudioSession.sharedInstance()
                .setActive(false, options: .notifyOthersOnDeactivation)
        }
    }
}

/// The app's one player.
///
/// A global rather than an injected instance for one boring reason: the app's `MediaModel` lives in
/// a `@State` property initialiser, and a property initialiser cannot reference another stored
/// property of the same type. There is exactly one media flow, so one player is not a compromise.
///
/// A `let` binding to a `@MainActor` class, which is what the shared rules allow: no `var` at file
/// scope, and anything global and mutable is actor-isolated. Nothing in a test ever reaches it --
/// tests inject `FakeMediaPlayerMaker`.
@MainActor let sharedMediaPlayer = AVMediaPlayerMaker()
