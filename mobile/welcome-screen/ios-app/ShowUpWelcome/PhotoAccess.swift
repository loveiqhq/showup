//
//  PhotoAccess.swift
//  ShowUp · the two permission questions the photo step can actually ask (SHOWUP-156)
//
//  The Swift port of `profile/PhotoAccess.kt`. Read that file's header for the argument at length;
//  what follows is the part that differs because this is iOS, and it is the shorter half.
//
//  ─────────────────────────────────────────────────────────────────────────────
//  ON iOS THERE IS NO LIBRARY PERMISSION AT ALL, AND THAT IS THE DESIGN
//  ─────────────────────────────────────────────────────────────────────────────
//
//  SHOWUP-156 states it as the decision the rest of the ticket depends on: present the SYSTEM
//  PICKER — `PHPickerViewController` — and do not request a library permission. The picker runs
//  out of process, shows the user their whole library, and hands back only what they chose.
//  Therefore:
//
//    · iOS needs no library access card, in any status. `library()` returns `.notNeeded` and
//      cannot return anything else, so the card CANNOT APPEAR here. That is the acceptance
//      criterion, expressed as the only thing this function does.
//    · iOS "Limited access" CANNOT HAPPEN. There is no `limited` case in this file and no
//      partial-library banner anywhere in the build. An earlier draft had one; it was DELETED, not
//      redesigned, because it explained a state we cannot enter.
//    · There is no `PHPhotoLibrary.authorizationStatus` call in this app, and there must not be:
//      asking creates the status the picker exists to avoid.
//
//  Build an in-app library browser instead and every one of those states comes back. Don't.
//
//  THE TWO CASES ARE STILL DECLARED, and deliberately. They are reachable on Android ≤ 12, the
//  screen renders them, the fit harness sweeps them and the copy is asserted on both platforms —
//  a shared vocabulary that one platform cannot currently produce is not the same thing as dead
//  code, and deleting it here would break the parity the checkers compare.
//
//  WHAT SURVIVES ON THIS PLATFORM IS THE CAMERA, the one permission that always applies, answered
//  on the camera row of the source sheet at the moment the user asks for it.
//

import AVFoundation
import Foundation

/// Whether the photo library can be reached, and if not, how to recover.
///
/// TWO REFUSAL MODES, NOT ONE BOOLEAN. "Collapsing them is how a screen ends up telling a user to
/// open Settings when it could simply have asked."
enum LibraryAccess {
    /// No permission is involved. The grid works. The only value iOS ever produces.
    case notNeeded
    /// Not granted, and the system will still show the prompt. The card RE-PROMPTS IN APP.
    case canAsk
    /// Permanently denied. The card opens Settings and its copy NAMES THE ROW to look for.
    case blocked
}

/// Whether the camera can be used, and if not, how to recover.
///
/// THE ONE PERMISSION THAT ALWAYS APPLIES, on both platforms, and the reason the source sheet
/// exists at all: the library path needs no permission and the camera path always does, so the two
/// cannot share one control.
///
/// A DENIED CAMERA BLOCKS NOTHING. The library row still works and the step is still completable,
/// so `.blocked` is never a grid replacement and never a full-screen card — it is a quiet row in
/// the sheet the user just opened.
enum CameraAccess {
    case granted
    /// Never asked. Tapping the row fires the OS prompt — which iOS shows ONCE, ever.
    case canAsk
    /// Denied or restricted. The row goes quiet and grows a `Settings` pill.
    case blocked
}

/// Reads the two statuses from the platform.
///
/// A protocol so the screen can be driven from a test and a preview without a device, and so the
/// one decision in `SystemPhotoAccess` is a single replaceable thing rather than a condition
/// scattered through a view.
protocol PhotoAccessReading {
    func library() -> LibraryAccess
    func camera() -> CameraAccess
}

/// The real reader.
///
/// RE-READ ON EVERY FOREGROUND. "The most common bug on this screen is a user who granted access in
/// Settings returning to the blocked card." Nothing is cached here — every call asks the platform —
/// so the only thing the screen has to do is call it again when it becomes active, which it does.
struct SystemPhotoAccess: PhotoAccessReading {

    /// Always `.notNeeded`. See the file header, at length.
    ///
    /// This is the seam. It is a function rather than a constant so that the shape matches the
    /// Android reader and so a future change has one place to happen — not because it has a
    /// decision to make today.
    func library() -> LibraryAccess { .notNeeded }

    func camera() -> CameraAccess {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            return .granted
        // `.notDetermined` is the ONLY status iOS will still prompt for: the alert is shown once
        // per install, and after that the answer is whatever the user said. There is no rationale
        // flag to consult and none is needed, which is why this reader is half the size of the
        // Android one.
        case .notDetermined:
            return .canAsk
        // `.restricted` is not the same as `.denied` — a managed device or Screen Time made the
        // choice, not the user — but the recovery is identical and naming the difference on screen
        // would explain a distinction nobody can act on.
        case .denied, .restricted:
            return .blocked
        @unknown default:
            // A status Apple adds later. Blocked is the safe default: it offers Settings, which is
            // never wrong, where `.canAsk` would offer a prompt that may never appear.
            return .blocked
        }
    }
}

/// A reader that answers whatever a preview or a test needs.
struct FixedPhotoAccess: PhotoAccessReading {
    var libraryAccess: LibraryAccess = .notNeeded
    var cameraAccess: CameraAccess = .canAsk

    func library() -> LibraryAccess { libraryAccess }
    func camera() -> CameraAccess { cameraAccess }
}
