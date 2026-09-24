//
//  MediaState.swift
//  ShowUp · the media step's state, with no SwiftUI in it (SHOWUP-161)
//
//  Plain values and pure functions, so every rule on this screen is testable without a device, a
//  camera or a microphone — which matters more here than anywhere else in the flow, because the two
//  things this screen actually does cannot be exercised in a unit test at all.
//
//  The Kotlin twin is `MediaModel.kt`. Same types, same names, same reasons.
//

import Foundation

/// The hard caps. 10 seconds of video, 15 of voice — the point of the 16 Sep pass.
enum MediaLimits {
    static let videoMs = 10_000
    static let voiceMs = 15_000

    /// How much of a take survives an interruption.
    ///
    /// A call, an alarm or another app taking the microphone ends the take. At or above this, the
    /// review screen is shown; below it the take is discarded and we return to the card. Never a
    /// silent resume.
    ///
    /// THE ONLY NUMBER IN THE TICKET THAT IS A PROPOSAL rather than a decision, and it is named
    /// here so changing it is one line. There is deliberately NO minimum on a take the user stopped
    /// themselves: confirmed 17 September 2026 — any deliberate Stop lands on review and can be
    /// kept, and `stop_reason` is what will show whether a floor is needed.
    static let interruptionKeepMs = 2_000

    static func maxMs(_ kind: MediaKind) -> Int {
        switch kind {
        case .video: return videoMs
        case .voice: return voiceMs
        }
    }
}

/// Where a recording is on its way to the server.
///
/// FOUR CLIENT STATES AND ONE SERVER STATE. Queued, in flight and failed are things only the client
/// can know; the server's answer is "the bytes are stored" or nothing at all. That asymmetry is why
/// there is no upload-status column in the database and why this type lives here.
///
/// `.failed` NEVER BLOCKS THE FLOW. It surfaces on the card, not as a modal, and the user can leave
/// the screen — an upload still in flight when Continue is pressed keeps going.
enum MediaUploadStatus: Sendable { case queued, inFlight, confirmed, failed }

/// Why a take ended. §8 `stop_reason`, a closed set of two.
enum MediaStopReason: String, Sendable {
    /// The user pressed Stop.
    case userStop = "user_stop"

    /// The cap did. A cap that ends most takes is a cap that is too short — that is the measurement.
    case maxLength = "max_length"

    var trackingValue: String { rawValue }
}

/// Where a retake was invoked from. §8 `from`, a closed set of two.
enum MediaActedFrom: String, Sendable {
    /// A take not yet kept.
    case review

    /// An artefact already on the profile.
    case mediaCard = "media_card"

    var trackingValue: String { rawValue }
}

/// How the prompt list was reached. §21 `entry_point`.
enum MediaEntryPoint: String, Sendable {
    /// The CTA on an empty card — the intended first path.
    case seeThePrompts = "see_the_prompts"

    /// The list reopened over something already recorded.
    case retake

    var trackingValue: String { rawValue }
}

/// Where the previewed prompt came from. §22 `preview_source`.
enum MediaPreviewSource: String, Sendable {
    case ranked
    case fallback

    var trackingValue: String { rawValue }

    static func fromServer(_ value: String?) -> MediaPreviewSource {
        value == "ranked" ? .ranked : .fallback
    }
}

/// A recording that is on the profile, or on its way there.
///
/// `promptId` is a §20 id and never a display string. `localPath` outlives `remoteId` because the
/// card plays the local file while the upload is still in flight — the whole point of filling the
/// card optimistically.
struct MediaArtefact: Equatable, Sendable {
    let kind: MediaKind
    let promptId: String
    let durationMs: Int
    var localPath: String?
    var remoteId: String?
    var url: String?
    var status: MediaUploadStatus = .queued

    var prompt: MediaPrompt? { MediaPrompts.byId(promptId) }
    var isOwnPrompt: Bool { promptId == MediaPrompts.ownIdea }
}

/// Which half of a capture sub-screen is showing.
enum RecordingPhase: Sendable { case recording, review }

/// A take in progress, or one waiting to be kept.
///
/// NOT AN ARTEFACT UNTIL IT IS ACCEPTED. Stopping produces one of these; only `Use this clip` turns
/// it into a `MediaArtefact`. That distinction is the whole reason the review screen exists, and
/// collapsing the two types is how a build ends up creating the artefact on Stop.
struct MediaTake: Equatable, Sendable {
    let kind: MediaKind
    let promptId: String
    var phase: RecordingPhase = .recording
    var elapsedMs: Int = 0
    var path: String?
    /// Counts takes of the same prompt, from 1, so a retake loop is readable without timestamps.
    var attempt: Int = 1
    var stopReason: MediaStopReason?
    /// How many times the user has played it back on review.
    var playCount: Int = 0

    var prompt: MediaPrompt? { MediaPrompts.byId(promptId) }
    var maxMs: Int { MediaLimits.maxMs(kind) }

    /// 0...1 of the cap. Drives the progress bar and the live waveform's played portion.
    var progress: Double {
        guard maxMs > 0 else { return 0 }
        return min(1, Double(elapsedMs) / Double(maxMs))
    }
}

/// Which prompt list is docked, and what is picked in it.
struct MediaSheet: Equatable, Sendable {
    let kind: MediaKind
    let entryPoint: MediaEntryPoint
    /// Nothing is preselected when the sheet is opened from an empty card — not even the previewed
    /// prompt. The card previews it; the list does not pick it. Opening from a filled card's
    /// `Retake` DOES preselect the answered prompt, so keeping it is one tap.
    var selectedId: String?
    /// When it opened, for `time_on_sheet_s`. No default: the model always knows, and a default of
    /// zero would be a legal clock reading standing in for "unset".
    let openedAtMs: Int64
    /// How many rows the user has tapped before committing. `selections_before` on the commit.
    var selectionsBefore: Int = 0
}

/// The whole screen.
///
/// The four drawn states A-D are not a field: they are `video` and `voice` being present or absent,
/// which is the only representation that cannot disagree with itself.
struct MediaState: Equatable, Sendable {
    var video: MediaArtefact?
    var voice: MediaArtefact?
    var previewVideoId: String?
    var previewVoiceId: String?
    var previewSource: MediaPreviewSource = .fallback
    var access: MediaAccess = MediaAccess()
    var sheet: MediaSheet?
    var take: MediaTake?
    /// What is playing, if anything. Nil at rest -- playback here is never automatic.
    var playback: MediaPlayback?
    var loaded: Bool = false

    var hasVideo: Bool { video != nil }
    var hasVoice: Bool { voice != nil }

    /// How far into `kind`'s saved clip the user has listened, for the card's `0:08 / 0:14`.
    func playedMs(_ kind: MediaKind) -> Int {
        guard let playback, playback.source == .card, playback.kind == kind else { return 0 }
        return playback.positionMs
    }

    /// Whether `kind`'s card is the thing currently playing.
    func isPlaying(_ kind: MediaKind) -> Bool {
        guard let playback else { return false }
        return playback.source == .card && playback.kind == kind
    }

    func artefact(_ kind: MediaKind) -> MediaArtefact? {
        switch kind {
        case .video: return video
        case .voice: return voice
        }
    }

    /// What an empty card previews. Resolved, never ranked — see `MediaPrompts.preview`.
    func preview(_ kind: MediaKind) -> MediaPrompt {
        switch kind {
        case .video: return MediaPrompts.preview(.video, fromServer: previewVideoId)
        case .voice: return MediaPrompts.preview(.voice, fromServer: previewVoiceId)
        }
    }

    mutating func setArtefact(_ artefact: MediaArtefact?, for kind: MediaKind) {
        switch kind {
        case .video: video = artefact
        case .voice: voice = artefact
        }
    }
}

/// The two waveforms, ported from the reference file's formulas rather than approximated.
///
/// ─────────────────────────────────────────────────────────────────────────────
/// WHY THE EXACT ARITHMETIC MATTERS
/// ─────────────────────────────────────────────────────────────────────────────
///
/// The acceptance criterion is "48 deterministic bars whose heights do not change between renders".
/// A random waveform re-rolled on redraw makes a static recording appear to wobble while the user
/// is looking at it, and SwiftUI redraws for reasons that have nothing to do with audio.
///
/// Deterministic is the requirement; these particular curves are the design. Both are pure
/// functions of the bar index, so they are computed once and are identical on both platforms.
///
/// NEITHER IS REAL AUDIO. The filled card's waveform is decoration on a finished recording — the
/// reference calls it "purely decorative" — and the live one is the shape the design draws while
/// recording. Rendering a true amplitude envelope is a different, larger job and is not what the
/// ticket asks for; what it asks for is that the drawing is stable.
enum MediaWaveform {

    /// Both waveforms are 48 bars. The count is in the acceptance criteria.
    static let bars = 48

    /// The finished recording's strip on a filled voice card.
    ///
    /// `min(1, |sin(i * 0.7) * 0.55 + sin(i * 0.31) * 0.4| + 0.1)`, from `RecordedVoiceCard`.
    static let recorded: [Double] = (0..<bars).map { i in
        let v = abs(sin(Double(i) * 0.7) * 0.55 + sin(Double(i) * 0.31) * 0.4) + 0.1
        return min(1.0, v)
    }

    /// The live strip on the voice capture screen.
    ///
    /// Centre-anchored: heights peak in the middle and fade toward the edges, "so it reads as
    /// 'live mic' rather than 'scrubbing'". From `VoiceRecordingView`.
    static let live: [Double] = (0..<bars).map { i in
        let center = (Double(i) - 24.0) / 24.0
        let envelope = max(0.1, 1.0 - center * center * 0.85)
        let v = (abs(sin(Double(i) * 0.93) + cos(Double(i) * 0.5) * 0.7) / 1.7) * envelope
        return min(1.0, 0.15 + v * 0.85)
    }
}

/// `0:09` — a take's length, as every readout on this screen spells it.
///
/// Seconds are TRUNCATED, not rounded, and that is deliberate: a 9.6-second take displayed as
/// `0:10` on a 10-second cap reads as though it hit the cap when it did not. The same reasoning as
/// storing milliseconds on the server.
func formatTakeLength(_ ms: Int) -> String {
    let total = max(0, ms) / 1000
    return "\(total / 60):" + String(format: "%02d", total % 60)
}

/// `duration_s` for the tracking payloads.
///
/// Seconds with one decimal, because the caps are 10 and 15 and whole seconds cannot tell a take
/// that ran into the cap from one that stopped just short of it.
func durationSeconds(_ ms: Int) -> Double {
    Double(max(0, ms) / 100) / 10.0
}
