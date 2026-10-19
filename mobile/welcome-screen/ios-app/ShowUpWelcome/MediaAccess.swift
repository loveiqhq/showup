//
//  MediaAccess.swift
//  ShowUp · camera and microphone status for the media step (SHOWUP-161)
//
//  ─────────────────────────────────────────────────────────────────────────────
//  VIDEO NEEDS CAMERA *AND* MICROPHONE. VOICE NEEDS MICROPHONE ONLY.
//  ─────────────────────────────────────────────────────────────────────────────
//
//  That single sentence is the whole shape of this file, and it is why a blocked microphone is the
//  only status that blocks the entire step. A blocked camera blocks one card; the voice card stays
//  fully functional, "exactly as the library row stayed functional on screen 06".
//
//  So the treatment is SCALED TO WHAT IS ACTUALLY BLOCKED. Never a screen replacement, never a card
//  over the voice slot, and never a gate on Continue — which is optional in every state.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  `.canAsk` CANNOT HAPPEN ON iOS, AND IT IS NOT DELETED
//  ─────────────────────────────────────────────────────────────────────────────
//
//  The ticket's permission matrix labels one row "can still ask (Android, after one refusal)", and
//  the parenthesis is load-bearing. iOS shows each of these alerts ONCE: after a refusal
//  `AVCaptureDevice.authorizationStatus` reports `.denied` forever and there is no second prompt to
//  offer. Android's `shouldShowRequestPermissionRationale` is what makes the fourth state real
//  there, and it has no iOS counterpart.
//
//  The case stays in the enum anyway, exactly as `LibraryAccess.canAsk` stays in `PhotoAccess`:
//  the two platforms share this screen's state machine, its copy and its tests, and deleting a case
//  on one of them would fork the vocabulary to save one line. `AVMediaAccess` simply never returns
//  it, and that single function is the whole of the difference.
//

import AVFoundation
import Foundation

/// Whether a capture permission can be used, and if not, how to recover.
///
/// The app ALWAYS knows which of these it is in. It NEVER knows which toggle the user will land on
/// — neither platform deep-links to a single permission row — which is why `.canAsk` and `.blocked`
/// are two behaviours with two different buttons and never one label.
enum MediaPermission: Sendable {
    case granted

    /// Never asked. No row is shown; the OS alert fires on the commit CTA.
    ///
    /// The state every first-time user is in, and the reason this enum has four cases.
    case notDetermined

    /// Refused once, and the system will still show the prompt. ANDROID ONLY — see the header.
    ///
    /// The row RE-PROMPTS IN APP. No Settings trip, and the copy names no toggle.
    case canAsk

    /// Permanently denied. The system will not prompt again.
    ///
    /// The row opens our own app's settings page, and the copy names the platform's own label for
    /// the row to look for — because the user arrives on a list and has to find it.
    case blocked

    /// Whether a permission row is drawn at all. Nothing is drawn before the first refusal.
    var needsRow: Bool { self == .canAsk || self == .blocked }
}

/// Which device capability a row is about.
enum MediaCapability: Sendable, CaseIterable {
    case camera
    case microphone

    var mediaType: AVMediaType {
        switch self {
        case .camera: return .video
        case .microphone: return .audio
        }
    }
}

/// A permission row's subject and mode — everything its copy and its button need.
struct MediaBlocker: Equatable, Sendable {
    let capability: MediaCapability
    let status: MediaPermission
}

/// Both statuses, read together, because every decision on this screen needs both.
struct MediaAccess: Equatable, Sendable {
    var camera: MediaPermission = .notDetermined
    var microphone: MediaPermission = .notDetermined

    /// What stands between the user and a take of this kind, or nil when nothing does.
    ///
    /// MICROPHONE IS CHECKED FIRST FOR VIDEO, and the order is the point rather than a detail. A
    /// video needs both; if both are refused the user can only be told about one at a time, and the
    /// microphone is the one worth naming — it blocks the voice card too, so fixing it recovers
    /// more of the screen. Reporting the camera first would send someone to Settings, have them fix
    /// the camera, and return to a video card still blocked.
    func blocker(for kind: MediaKind) -> MediaBlocker? {
        if microphone.needsRow { return MediaBlocker(capability: .microphone, status: microphone) }
        if kind == .video, camera.needsRow {
            return MediaBlocker(capability: .camera, status: camera)
        }
        return nil
    }

    /// Whether a take of this kind can start without asking for anything.
    func isReady(for kind: MediaKind) -> Bool {
        switch kind {
        case .voice: return microphone == .granted
        case .video: return microphone == .granted && camera == .granted
        }
    }

    /// What still has to be requested before a take of this kind can start.
    func missing(for kind: MediaKind) -> [MediaCapability] {
        var out: [MediaCapability] = []
        if kind == .video, camera != .granted { out.append(.camera) }
        if microphone != .granted { out.append(.microphone) }
        return out
    }
}

/// Reads both statuses from the platform.
///
/// A protocol so the screen can be driven from a test and a preview with no device, and so the one
/// real decision lives in `AVMediaAccess` rather than scattered through a view.
protocol MediaAccessReading: Sendable {
    func read() -> MediaAccess

    /// The platform's own label for a permission row, for the blocked copy.
    ///
    /// The ticket forbids hard-coding `Camera` and `Microphone`: "Row labels in the blocked strings
    /// are replaced by the platform's own label — never hard-code Camera or Microphone if the OS
    /// calls it something else."
    func platformLabel(_ capability: MediaCapability) -> String

    /// Asks for one capability. The answer is whatever `read()` says afterwards.
    func request(_ capability: MediaCapability) async -> Bool
}

/// The real reader.
///
/// NOTHING IS CACHED. Every call asks the platform, so "re-read both statuses on every foreground"
/// is a call site rather than an invalidation problem. A user who granted access in Settings and
/// came back to a blocked row will not try twice.
struct AVMediaAccess: MediaAccessReading {

    func read() -> MediaAccess {
        MediaAccess(camera: status(.camera), microphone: status(.microphone))
    }

    private func status(_ capability: MediaCapability) -> MediaPermission {
        switch AVCaptureDevice.authorizationStatus(for: capability.mediaType) {
        case .authorized: return .granted
        case .notDetermined: return .notDetermined
        // `.denied` and `.restricted` both mean "no, and not from here". Restricted is a managed
        // device or a parental control, where even Settings may not offer the toggle -- but the
        // row we can honestly show is the same one, because we cannot tell the user anything more
        // useful than where to look.
        case .denied, .restricted: return .blocked
        @unknown default: return .blocked
        }
    }

    /// The label the platform itself uses for the row.
    ///
    /// iOS names these in the Settings list rather than exposing them through an API, so these two
    /// are the labels Apple ships. They are still resolved through this function rather than
    /// written into the copy, so a localised build has exactly one place to correct — and so the
    /// two platforms' call sites stay identical.
    func platformLabel(_ capability: MediaCapability) -> String {
        switch capability {
        case .camera: return "Camera"
        case .microphone: return "Microphone"
        }
    }

    func request(_ capability: MediaCapability) async -> Bool {
        await AVCaptureDevice.requestAccess(for: capability.mediaType)
    }
}

/// A reader that answers whatever a preview or a test needs.
struct FixedMediaAccess: MediaAccessReading {
    var access: MediaAccess = MediaAccess()
    var labels: [String: String] = [:]

    func read() -> MediaAccess { access }

    func platformLabel(_ capability: MediaCapability) -> String {
        switch capability {
        case .camera: return labels["camera"] ?? "Camera"
        case .microphone: return labels["microphone"] ?? "Microphone"
        }
    }

    func request(_ capability: MediaCapability) async -> Bool {
        access.isReady(for: capability == .camera ? .video : .voice)
    }
}
