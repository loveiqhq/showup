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
//  NOTHING HERE DECIDES ANYTHING
//  ───────────────────────────────────────────────────────────────────────────
//
//  Same rule as the recorders. No product decision lives in this file: not which source wins, not
//  what a second press does, not when the tracking event fires. It turns a path into sound and
//  pictures, and reports where the playhead is.
//

import AVFoundation
import Foundation

/// The one player the app plays everything through.
///
/// `@MainActor` throughout, like `MediaPlaying` itself, so there is no isolation to cross and no
/// `@unchecked Sendable` anywhere -- an escape hatch this project bans and has a checker for.
@MainActor
final class AVMediaPlayerMaker: MediaPlayerMaking {
    /// The one instance, created on first use. The video surfaces attach to it.
    private(set) var surface: AVPlayer?

    /// Bumped by the end-of-item notification.
    ///
    /// A COUNT RATHER THAN A FLAG, so a session that was stopped and replaced cannot claim the
    /// clip that replaced it as its own ending -- which the model would read as "this play ran to
    /// the end" and use to reset a playhead belonging to something else.
    fileprivate var endedCount: Int = 0
    private var observer: NSObjectProtocol?

    /// Releases the player. The host calls this when the media flow goes away.
    func release() {
        surface?.pause()
        surface?.replaceCurrentItem(with: nil)
        surface = nil
    }

    fileprivate func player() -> AVPlayer {
        if let surface { return surface }
        let made = AVPlayer()
        observer = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.endedCount += 1 }
        }
        surface = made
        return made
    }

    func makePlayer(kind: MediaKind) -> any MediaPlaying { AVSession(owner: self) }

    @MainActor
    final class AVSession: MediaPlaying {
        private unowned let owner: AVMediaPlayerMaker
        /// The end count when this session started. See `endedCount`.
        private var endedAtStart = 0

        init(owner: AVMediaPlayerMaker) { self.owner = owner }

        func start(path: String) async -> Bool {
            let player = owner.player()
            endedAtStart = owner.endedCount
            // A local recording is a file path; an uploaded one is an https URL. Both arrive here
            // as a string, and only one of them is a valid URL on its own -- `URL(string:)` will
            // happily build a schemeless URL out of `/var/mobile/...` that then resolves to
            // nothing.
            let url = URL(string: path).flatMap { $0.scheme == nil ? nil : $0 }
                ?? URL(fileURLWithPath: path)
            player.replaceCurrentItem(with: AVPlayerItem(url: url))
            await player.seek(to: .zero)
            player.play()
            return player.currentItem != nil
        }

        func positionMs() -> Int {
            guard let player = owner.surface else { return 0 }
            let seconds = player.currentTime().seconds
            return seconds.isFinite ? Int(seconds * 1000) : 0
        }

        func hasFinished() -> Bool { owner.endedCount > endedAtStart }

        func stop() async {
            owner.surface?.pause()
            await owner.surface?.seek(to: .zero)
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
