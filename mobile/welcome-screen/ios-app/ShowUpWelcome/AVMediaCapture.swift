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

    init(camera: @escaping () -> CameraSession?, output: URL) {
        self.camera = camera
        self.output = output
        super.init()
    }

    func start(onEnded: @escaping @MainActor (CaptureFailure) -> Void) async -> Bool {
        self.onEnded = onEnded
        guard let camera = camera(), camera.session.isRunning else { return false }
        camera.output.startRecording(to: output, recordingDelegate: self)
        return true
    }

    func finish(elapsedMs: Int) async -> CaptureTake? {
        guard !finished else { return nil }
        finished = true
        guard let camera = camera(), camera.output.isRecording else { return nil }
        // The file is not closed until the delegate fires, so the take is not readable until then.
        camera.output.stopRecording()
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
            if let camera = camera(), camera.output.isRecording {
                camera.output.stopRecording()
                await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
                    finishedContinuation = continuation
                }
            }
        }
        try? FileManager.default.removeItem(at: output)
    }

    nonisolated func fileOutput(_ output: AVCaptureFileOutput,
                                didFinishRecordingTo outputFileURL: URL,
                                from connections: [AVCaptureConnection],
                                error: (any Error)?) {
        let duration = CMTimeGetSeconds(output.recordedDuration)
        MainActor.assumeIsolated {
            recordedDurationMs = duration.isFinite ? Int(duration * 1000) : nil
            // Some errors still leave a playable prefix, which is why the duration travels either
            // way and the caller decides what to keep.
            if error != nil, !finished { onEnded?(.interrupted) }
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
