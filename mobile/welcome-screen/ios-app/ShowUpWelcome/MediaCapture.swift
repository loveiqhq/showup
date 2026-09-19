//
//  MediaCapture.swift
//  ShowUp · the seam between "a take happened" and how it happened (SHOWUP-161)
//
//  ─────────────────────────────────────────────────────────────────────────────
//  WHY THIS IS A PROTOCOL
//  ─────────────────────────────────────────────────────────────────────────────
//
//  Neither real implementation can run in a unit test: `AVCaptureSession` needs a camera and
//  `AVAudioRecorder` needs a microphone. Everything ELSE on this screen — the cap, the attempt
//  counter, the retake loop, the interruption threshold, every one of the fourteen tracking events
//  — is decided by `MediaModel` and has nothing to do with either. Putting the recorders behind
//  this protocol is what lets all of that be tested with no device attached.
//
//  It is the same seam as `PhotoAccessReading`, for the same reason and with the same shape: one
//  small protocol, one real implementation, one fake.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  WHAT IT DELIBERATELY DOES NOT DO
//  ─────────────────────────────────────────────────────────────────────────────
//
//  IT DOES NOT ENFORCE THE CAP. The 10 and 15 second limits are product rules and they live in the
//  model with everything else that can be tested. A recorder that stopped itself would be a second
//  place the rule lives, and `AVCaptureFileOutput.maxRecordedDuration` in particular reports its
//  stop through the same delegate callback as a failure — so the one place that knows WHY a take
//  ended would be the one place unable to say.
//
//  IT DOES NOT COUNT TIME for the UI. The elapsed value the progress bar reads is the model's own
//  clock, so the bar advances identically in a test, in a preview and on a device.
//

import Foundation

/// A finished take: where the bytes are, and how long they ran.
struct CaptureTake: Equatable, Sendable {
    let path: String

    /// The recorder's own measurement where it has one, the caller's clock where it does not.
    ///
    /// `AVCaptureMovieFileOutput` reports `recordedDuration` on finish and that is the honest
    /// number — a wall clock also counts the moment between asking to stop and the file being
    /// closed. `AVAudioRecorder` gives `currentTime` only while running, so audio reads it just
    /// before stopping and falls back to the elapsed value it was given.
    let durationMs: Int
    let mimeType: String
    let fileName: String
}

/// Why a session ended without producing a usable take.
enum CaptureFailure: Equatable, Sendable {
    /// The recorder would not start: hardware in use, no encoder, permission revoked mid-flight.
    case couldNotStart

    /// A call, an alarm, or another app took the microphone.
    ///
    /// Reported separately from `.couldNotStart` because the product rule differs: an interruption
    /// that captured at least `MediaLimits.interruptionKeepMs` still shows the review screen.
    case interrupted

    /// The take ran but nothing usable came out of it.
    case noOutput
}

/// One take, from start to file.
///
/// Single use. A retake creates a new session, which is what makes the attempt counter meaningful
/// and keeps a half-finished recorder from being reused.
@MainActor
protocol MediaCaptureSession: AnyObject {

    /// Begins recording. False if it could not start at all.
    ///
    /// - Parameter onEnded: called only when the take ends WITHOUT `finish` being called — an
    ///   interruption or a hardware failure. The caller decides what that means; this only reports.
    func start(onEnded: @escaping @MainActor (CaptureFailure) -> Void) async -> Bool

    /// Ends the take and closes the file. Nil if nothing usable was written.
    func finish(elapsedMs: Int) async -> CaptureTake?

    /// Abandons the take and deletes whatever was written. Safe to call twice.
    func discard() async
}

/// Creates a session per take.
@MainActor
protocol MediaCaptureMaking {
    func makeSession(_ kind: MediaKind) -> any MediaCaptureSession

    /// The running camera a viewfinder shows, when this factory has one.
    ///
    /// ON THE FACTORY, so there is exactly one owner. The host tried holding the session in its own
    /// `@State` beside the model, and SwiftUI's `@State` initialisers cannot reference each other —
    /// so the model built a SECOND session and the result would have been a live preview over a
    /// black recording. A fake returns nil, which is honest: there is no camera in a test.
    var previewSession: CameraSession? { get }
}

/// What each medium is uploaded as.
///
/// The server accepts these exact spellings per slot and refuses a video in the voice slot, so
/// getting this wrong is a 415 rather than a silent mis-store. iOS writes QuickTime for video and
/// an MPEG-4 audio container for voice.
func mimeType(for kind: MediaKind) -> String {
    switch kind {
    case .video: return "video/quicktime"
    case .voice: return "audio/mp4"
    }
}

func fileName(for kind: MediaKind) -> String {
    switch kind {
    case .video: return "take.mov"
    case .voice: return "take.m4a"
    }
}

/// A capture that writes nothing, for tests and previews.
///
/// Returns a take whose length is exactly the elapsed value it was handed, so a test can assert the
/// cap, the attempt counter and the duration on every tracking payload without a camera.
@MainActor
final class FakeMediaCapture: MediaCaptureMaking {
    private let startSucceeds: Bool

    /// Every session this factory has made, in order, for a test to inspect.
    private(set) var sessions: [FakeSession] = []

    init(startSucceeds: Bool = true) {
        self.startSucceeds = startSucceeds
    }

    /// No camera, and saying so is the point — see `MediaCaptureMaking.previewSession`.
    var previewSession: CameraSession? { nil }

    func makeSession(_ kind: MediaKind) -> any MediaCaptureSession {
        let session = FakeSession(kind: kind, startSucceeds: startSucceeds)
        sessions.append(session)
        return session
    }

    @MainActor
    final class FakeSession: MediaCaptureSession {
        let kind: MediaKind
        private let startSucceeds: Bool
        private(set) var started = false
        private(set) var discarded = false
        private var onEnded: (@MainActor (CaptureFailure) -> Void)?

        init(kind: MediaKind, startSucceeds: Bool) {
            self.kind = kind
            self.startSucceeds = startSucceeds
        }

        func start(onEnded: @escaping @MainActor (CaptureFailure) -> Void) async -> Bool {
            self.onEnded = onEnded
            started = startSucceeds
            return startSucceeds
        }

        func finish(elapsedMs: Int) async -> CaptureTake? {
            guard started else { return nil }
            return CaptureTake(
                path: "/dev/null/\(kind.rawValue).take",
                durationMs: elapsedMs,
                mimeType: mimeType(for: kind),
                fileName: fileName(for: kind)
            )
        }

        func discard() async { discarded = true }

        /// Drives the interruption path from a test.
        func interrupt(_ reason: CaptureFailure = .interrupted) { onEnded?(reason) }
    }
}
