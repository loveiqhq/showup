//
//  AVMediaCapture.swift
//  ShowUp · the two real recorders (SHOWUP-161)
//
//  ─────────────────────────────────────────────────────────────────────────────
//  OUR OWN SESSION, NOT UIImagePickerController
//  ─────────────────────────────────────────────────────────────────────────────
//
//  This file is the deliberate opposite of the photo step. Photos hands off to `PHPickerViewController`
//  and is better for it; media runs capture in process, and the ticket rules the alternative out in
//  as many words: `UIImagePickerController` "is NOT an acceptable substitute — it loses the prompt,
//  the 10-second cap and the review screen".
//
//  All three are the screen. The prompt has to sit under the lens for the whole take because the
//  user is answering a question; the cap has to be ours because 10 seconds is the product decision
//  the 16 Sep pass was about; and the review screen has to exist because stopping produces a take
//  and only accepting produces an artefact.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  NOTHING HERE DECIDES ANYTHING
//  ─────────────────────────────────────────────────────────────────────────────
//
//  No cap, no minimum length, no retake counting, no tracking. This file turns a microphone and a
//  camera into a file on disk and reports when that stopped happening. Every rule lives in
//  `MediaModel`, where it can be tested without hardware.
//
//  WHY A `UIViewRepresentable` IS NOT NEEDED FOR THE RECORDING ITSELF: only the PREVIEW is a UIKit
//  view (`CameraPreview`, below), and its written reason is that `AVCaptureVideoPreviewLayer` is a
//  `CALayer` with no SwiftUI equivalent — there is no way to show a viewfinder without it.
//

import AVFoundation
import SwiftUI

/// Where takes are written.
///
/// The caches directory, not documents. A take is a temporary artefact on its way to the server,
/// and the one that is NOT accepted must not survive: the user recorded their face, looked at it
/// and decided against it, and leaving that on disk is the wrong default in the one place it
/// matters most. The system is also free to reclaim the directory, which is correct for something
/// already uploaded.
private func takeURL(_ kind: MediaKind) -> URL {
    let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("media-takes", isDirectory: true)
    try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
    let ext = kind == .video ? "mov" : "m4a"
    return base.appendingPathComponent("\(kind.rawValue)-\(Int(Date().timeIntervalSince1970 * 1000)).\(ext)")
}

/// Creates the real sessions, and owns the camera they record from.
///
/// ONE OWNER. The viewfinder needs a session that is already running before it can show anything,
/// and the recorder needs the same one — so it lives here and the host reads it back through
/// `previewSession` rather than holding a second copy. See that property for what the second copy
/// would have cost.
@MainActor
final class AVMediaCaptureFactory: MediaCaptureMaking {
    private let camera = CameraSession()

    var previewSession: CameraSession? { camera }

    func makeSession(_ kind: MediaKind) -> any MediaCaptureSession {
        switch kind {
        case .voice: return VoiceCaptureSession(output: takeURL(.voice))
        case .video: return VideoCaptureSession(camera: { [camera] in camera },
                                                output: takeURL(.video))
        }
    }
}

// MARK: - voice

/// A voice take, through `AVAudioRecorder`.
///
/// MPEG-4 / AAC, which is what the server accepts for the voice slot. 96 kbps mono at 44.1 kHz:
/// fifteen seconds of speech is about 180 KB, comfortably inside the server's 8 MB ceiling with no
/// compression step of our own.
@MainActor
private final class VoiceCaptureSession: NSObject, MediaCaptureSession, AVAudioRecorderDelegate {
    private let output: URL
    private var recorder: AVAudioRecorder?
    private var finished = false
    private var onEnded: (@MainActor (CaptureFailure) -> Void)?
    private var interruptionObserver: NSObjectProtocol?

    /// Read just before `stop()`, because `currentTime` reports zero once stopped.
    private var lastKnownDurationMs = 0

    init(output: URL) {
        self.output = output
        super.init()
    }

    func start(onEnded: @escaping @MainActor (CaptureFailure) -> Void) async -> Bool {
        self.onEnded = onEnded
        do {
            let session = AVAudioSession.sharedInstance()
            // `.record`, not `.playAndRecord`: nothing is played while recording, and the narrower
            // category is the one that tells the system to duck everything else.
            try session.setCategory(.record, mode: .spokenAudio)
            try session.setActive(true)

            let recorder = try AVAudioRecorder(url: output, settings: [
                AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
                AVSampleRateKey: 44_100,
                AVNumberOfChannelsKey: 1,
                AVEncoderBitRateKey: 96_000,
            ])
            recorder.delegate = self
            // NO `record(forDuration:)`. The cap is a product rule and lives in the model -- see
            // MediaCapture's header. A recorder that stopped itself would report that stop through
            // the same delegate callback as a failure.
            guard recorder.prepareToRecord(), recorder.record() else {
                cleanUp()
                return false
            }
            self.recorder = recorder
            observeInterruptions()
            return true
        } catch {
            cleanUp()
            return false
        }
    }

    /// An interruption is how iOS says "a call is arriving".
    ///
    /// Unlike Android there IS a first-class notification for this, and it fires for an incoming
    /// call, an alarm, Siri, and another app taking the microphone. `.began` is the only case that
    /// matters here: the take is over either way, and we never resume silently.
    private func observeInterruptions() {
        interruptionObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] note in
            guard
                let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                let type = AVAudioSession.InterruptionType(rawValue: raw),
                type == .began
            else { return }
            MainActor.assumeIsolated {
                guard let self, !self.finished else { return }
                self.lastKnownDurationMs = self.currentDurationMs()
                self.onEnded?(.interrupted)
            }
        }
    }

    private func currentDurationMs() -> Int {
        guard let recorder, recorder.isRecording else { return lastKnownDurationMs }
        return Int(recorder.currentTime * 1000)
    }

    func finish(elapsedMs: Int) async -> CaptureTake? {
        guard !finished else { return nil }
        finished = true
        // Read BEFORE stopping: `currentTime` is zero once the recorder is stopped, so asking
        // afterwards would report every take as instantaneous.
        let measured = currentDurationMs()
        recorder?.stop()
        cleanUp()

        guard
            FileManager.default.fileExists(atPath: output.path),
            let size = try? FileManager.default.attributesOfItem(atPath: output.path)[.size] as? Int,
            size > 0
        else {
            try? FileManager.default.removeItem(at: output)
            return nil
        }
        return CaptureTake(
            path: output.path,
            durationMs: measured > 0 ? measured : elapsedMs,
            mimeType: mimeType(for: .voice),
            fileName: fileName(for: .voice)
        )
    }

    func discard() async {
        if !finished {
            finished = true
            recorder?.stop()
        }
        cleanUp()
        try? FileManager.default.removeItem(at: output)
    }

    private func cleanUp() {
        recorder = nil
        if let interruptionObserver {
            NotificationCenter.default.removeObserver(interruptionObserver)
        }
        interruptionObserver = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    nonisolated func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder,
                                                      error: (any Error)?) {
        MainActor.assumeIsolated {
            guard !finished else { return }
            onEnded?(.noOutput)
        }
    }
}

// MARK: - video

/// The running capture session a viewfinder shows and a take is written from.
///
/// Held by the capture view rather than by a session, because the preview has to be live before the
/// user presses anything and `AVCaptureSession.startRunning` is slow enough to be visible.
@MainActor
final class CameraSession {
    let session = AVCaptureSession()
    private let movieOutput = AVCaptureMovieFileOutput()
    private var configured = false

    /// Starts the camera. Safe to call repeatedly; only the first call configures.
    ///
    /// FRONT CAMERA BY DEFAULT — selfie is the obvious intent for a face clip, and no flip control
    /// is drawn. A flip is a one-button addition if takes ever show otherwise.
    func startIfNeeded() {
        if !configured {
            configured = true
            session.beginConfiguration()
            session.sessionPreset = .high
            if let camera = AVCaptureDevice.default(
                .builtInWideAngleCamera, for: .video, position: .front
            ), let input = try? AVCaptureDeviceInput(device: camera), session.canAddInput(input) {
                session.addInput(input)
            }
            if let mic = AVCaptureDevice.default(for: .audio),
               let micInput = try? AVCaptureDeviceInput(device: mic),
               session.canAddInput(micInput) {
                // Audio on: a ten-second clip of a silent face is not what was asked for, and it
                // is the single fact the whole permission matrix is derived from -- video needs
                // BOTH permissions and voice needs one.
                session.addInput(micInput)
            }
            if session.canAddOutput(movieOutput) { session.addOutput(movieOutput) }
            session.commitConfiguration()
        }
        guard !session.isRunning else { return }
        // `startRunning` blocks, and blocking the main actor here would stall the transition INTO
        // the viewfinder -- the one moment the user is watching for something to happen.
        let session = self.session
        Task.detached(priority: .userInitiated) { session.startRunning() }
    }

    func stop() {
        guard session.isRunning else { return }
        let session = self.session
        Task.detached(priority: .userInitiated) { session.stopRunning() }
    }

    var output: AVCaptureMovieFileOutput { movieOutput }
}

/// A video take, through the running `CameraSession`.
@MainActor
private final class VideoCaptureSession: NSObject, MediaCaptureSession,
                                         AVCaptureFileOutputRecordingDelegate {
    private let camera: () -> CameraSession?
    private let output: URL
    private var finished = false
    private var onEnded: (@MainActor (CaptureFailure) -> Void)?
    private var recordedDurationMs: Int?
    private var finishedContinuation: CheckedContinuation<Void, Never>?

    /// Whether `didStartRecordingTo` has arrived, and whoever is waiting for it.
    ///
    /// THE DIFFERENCE BETWEEN ASKING AND RECORDING. `startRecording(to:recordingDelegate:)`
    /// returns immediately; the file is not open and no sample has been written until
    /// `fileOutput(_:didStartRecordingTo:from:)` fires. Android has the identical gap between
    /// `Recording.start()` and `VideoRecordEvent.Start`, where it was measured at three seconds on
    /// an emulator and produced a take with no frames in it at all.
    ///
    /// It is smaller here -- AVFoundation has the session already running -- but it is not zero,
    /// and the consequence is the same on both platforms: a Stop inside the gap ends a recording
    /// that never wrote anything, and every surviving take is short by the width of the gap
    /// because the ten-second clock began before the first frame.
    private var didStart = false

    /// Whether `startRecording` was ever issued, and whether this take was thrown away.
    ///
    /// `requested` is the ownership question: it is true from the instant we ask, which is
    /// earlier than `didStart` and earlier than `isRecording`. Anything that gives up on the take
    /// after that point is responsible for stopping it.
    ///
    /// `discarded` separates the two ways of giving up. `finish` wants the file; `discard` does
    /// not, and a file that lands after a discard has nobody waiting to delete it -- so the
    /// delegate deletes it instead. Without the flag they are indistinguishable, because both set
    /// `finished`.
    private var requested = false
    private var discarded = false

    /// EVERYONE waiting for the first frame, not just the last one to ask.
    ///
    /// An array rather than a single slot because `start` and `finish` can both be waiting at
    /// once: the user pressing Stop inside the gap leaves `start` suspended and sends `finish`
    /// to the same wait. A lone `CheckedContinuation` property would be overwritten by the
    /// second arrival and the first would never resume -- a deadlock in the exact path this
    /// change exists to fix. Kotlin's `CompletableDeferred` fans out for free; this is the
    /// Swift equivalent written out.
    private var startedContinuations: [CheckedContinuation<Bool, Never>] = []

    /// How long to wait for the first frame before calling the take a failure to start.
    ///
    /// A backstop against a delegate callback that never arrives, not a budget for a slow one --
    /// so it sits well clear of anything observed rather than close to it. Mirrors Android's
    /// `START_TIMEOUT_MS`, which carries the measurement this number is derived from.
    private static let startTimeoutNanoseconds: UInt64 = 8_000_000_000

    init(camera: @escaping () -> CameraSession?, output: URL) {
        self.camera = camera
        self.output = output
        super.init()
    }

    func start(onEnded: @escaping @MainActor (CaptureFailure) -> Void) async -> Bool {
        self.onEnded = onEnded
        guard let camera = camera(), camera.session.isRunning else { return false }
        camera.output.startRecording(to: output, recordingDelegate: self)
        requested = true

        // AND NOW WAIT FOR THE CAMERA TO ANSWER.
        //
        // Returning here rather than above is the whole fix: the recording clock in `MediaState`
        // is started by this function returning, so anchoring it to the first frame is what makes
        // the ten-second cap measure ten seconds of video.
        let rolling = await awaitRolling()

        // Stop arrived while we waited. `finish` set `finished`, owns the file, and has already
        // decided the outcome -- so this must neither clean up underneath it nor report a start
        // failure over the top of it.
        // NOT OURS TO STOP HERE. `finish` and `discard` each stop what they take over, and a
        // start that lands after either of them is stopped by `didStartRecordingTo`, which is
        // the only place a pending start can be caught.
        if finished { return false }
        if !rolling {
            stopRecordingIfOwned()
            try? FileManager.default.removeItem(at: output)
            return false
        }
        return true
    }

    /// Waits for the first frame, or gives up.
    ///
    /// The timeout is a sibling `Task` rather than a task group because there is nothing to race
    /// for a *value* -- one of the two resumes the continuation and the other is cancelled. The
    /// `didStart` check first covers the delegate firing before we get here, which is legal and
    /// would otherwise wait for a callback that had already happened.
    private func awaitRolling() async -> Bool {
        if didStart { return true }
        let timeout = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: Self.startTimeoutNanoseconds)
            guard !Task.isCancelled else { return }
            self?.resumeStart(false)
        }
        let rolling = await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            startedContinuations.append(continuation)
        }
        timeout.cancel()
        return rolling
    }

    /// Stops the recording this session asked for.
    ///
    /// THE TRAP IT EXISTS FOR: `stopRecording()` is a NO-OP while the output is not yet
    /// recording. An owner that gives up inside the start gap therefore cannot stop anything by
    /// calling it -- the call lands, does nothing, and AVFoundation goes on to start the
    /// recording it was already asked for and write to a file nobody is waiting for.
    ///
    /// So the stop is issued here when it can land, and re-issued from `didStartRecordingTo`
    /// when the owner has already gone. That callback is the only moment at which a pending
    /// start becomes stoppable.
    ///
    /// Android has no equivalent because `Recording.stop()` is honoured whether or not the Start
    /// event has arrived; this is the same guarantee, written out.
    private func stopRecordingIfOwned() {
        guard requested, let camera = camera(), camera.output.isRecording else { return }
        camera.output.stopRecording()
    }

    /// Answers every start wait exactly once, whoever gets there first.
    ///
    /// Drains the list before resuming any of it, so a continuation that synchronously starts
    /// another wait cannot be resumed twice -- which traps at runtime rather than misbehaving.
    private func resumeStart(_ rolling: Bool) {
        guard !startedContinuations.isEmpty else { return }
        let waiting = startedContinuations
        startedContinuations = []
        for continuation in waiting { continuation.resume(returning: rolling) }
    }

    func finish(elapsedMs: Int) async -> CaptureTake? {
        guard !finished else { return nil }
        finished = true
        // STOP ONLY WHAT HAS STARTED.
        //
        // Reachable when the user presses Stop inside the start gap, because the session exists
        // from the moment the take does. `isRecording` is still false there, so the old guard
        // below returned nil and the screen reported a take that had written nothing -- when in
        // fact it had not yet begun. Waiting for the first frame turns that into a real take.
        let rolling = await awaitRolling()
        // STOP WHAT WAS ASKED FOR, whether or not it ever rolled. The old guard returned early
        // when `isRecording` was still false, which is exactly the timed-out case -- and left a
        // requested recording with no owner and nothing to stop it.
        stopRecordingIfOwned()
        guard rolling else {
            try? FileManager.default.removeItem(at: output)
            return nil
        }
        // The file is not closed until the delegate fires, so the take is not readable until then.
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            finishedContinuation = continuation
        }

        guard
            FileManager.default.fileExists(atPath: output.path),
            let size = try? FileManager.default.attributesOfItem(atPath: output.path)[.size] as? Int,
            size > 0
        else {
            try? FileManager.default.removeItem(at: output)
            return nil
        }
        return CaptureTake(
            path: output.path,
            // The recorder's own measurement wins where it has one: a wall clock also counts the
            // moment between asking to stop and the file being closed.
            durationMs: recordedDurationMs.map { $0 > 0 ? $0 : elapsedMs } ?? elapsedMs,
            mimeType: mimeType(for: .video),
            fileName: fileName(for: .video)
        )
    }

    func discard() async {
        if !finished {
            finished = true
            discarded = true
            stopRecordingIfOwned()
            // WAITS ONLY IF THERE IS SOMETHING TO WAIT FOR. A discard is the user walking away,
            // so it must not block them for the length of the start gap on the chance that a
            // recording is coming. When one arrives anyway, `didStartRecordingTo` stops it and
            // `didFinishRecordingTo` deletes what it wrote -- which is why `discarded` exists.
            if didStart {
                await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                    finishedContinuation = continuation
                }
            }
        }
        try? FileManager.default.removeItem(at: output)
    }

    /// The first frame is on disk. This, not `startRecording`, is when recording begins.
    nonisolated func fileOutput(_ output: AVCaptureFileOutput,
                                didStartRecordingTo fileURL: URL,
                                from connections: [AVCaptureConnection]) {
        MainActor.assumeIsolated {
            didStart = true
            // THE OWNER MAY ALREADY HAVE GONE. `finish` or `discard` inside the start gap sets
            // `finished` before this arrives, and neither could stop a recording that had not
            // begun. This is the first moment it can be stopped, so it is stopped here.
            if finished { stopRecordingIfOwned() }
            resumeStart(true)
        }
    }

    nonisolated func fileOutput(_ output: AVCaptureFileOutput,
                                didFinishRecordingTo outputFileURL: URL,
                                from connections: [AVCaptureConnection],
                                error: (any Error)?) {
        let duration = CMTimeGetSeconds(output.recordedDuration)
        MainActor.assumeIsolated {
            // A finish with no start before it means the take never rolled. Answering the wait
            // here rather than letting it time out turns an eight-second stall into an immediate
            // and accurate "we could not start".
            resumeStart(didStart)
            recordedDurationMs = duration.isFinite ? Int(duration * 1000) : nil
            // Some errors still leave a playable prefix, which is why the duration travels either
            // way and the caller decides what to keep.
            if error != nil, !finished { onEnded?(.interrupted) }
            // Nothing is coming to collect this: the take was thrown away before the file closed,
            // and `discard` has already deleted a path that did not exist yet.
            if discarded { try? FileManager.default.removeItem(at: outputFileURL) }
            finishedContinuation?.resume()
            finishedContinuation = nil
        }
    }
}

/// The viewfinder.
///
/// A `UIViewRepresentable`, and the written reason the project requires: `AVCaptureVideoPreviewLayer`
/// is a `CALayer` and SwiftUI has no equivalent. There is no way to show a live camera without one,
/// and the alternative -- handing off to the system camera UI -- is what this whole ticket exists to
/// avoid. It holds no state of its own, which is what keeps it clear of the SwiftUI/UIKit seam that
/// produced this codebase's two worst state bugs.
struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        // `resizeAspectFill`, because the viewfinder is full-bleed and a letterbox would put black
        // bars around the user's face. The prompt sits over the lower third either way.
        view.previewLayer.videoGravity = .resizeAspectFill
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {
        uiView.previewLayer.session = session
    }

    final class PreviewView: UIView {
        override static var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer {
            // Safe by construction: `layerClass` above is what the system instantiates.
            layer as! AVCaptureVideoPreviewLayer
        }
    }
}
